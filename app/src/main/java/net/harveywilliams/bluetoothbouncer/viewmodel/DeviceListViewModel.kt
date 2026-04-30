package net.harveywilliams.bluetoothbouncer.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.harveywilliams.bluetoothbouncer.BluetoothBouncerApp
import net.harveywilliams.bluetoothbouncer.data.BlockedDeviceDao
import net.harveywilliams.bluetoothbouncer.data.BlockedDeviceEntity
import net.harveywilliams.bluetoothbouncer.shizuku.ShizukuHelper

class DeviceListViewModel(
    application: Application,
    private val shizukuHelper: ShizukuHelper,
    private val blockedDeviceDao: BlockedDeviceDao,
) : AndroidViewModel(application) {

    // ── UI model ─────────────────────────────────────────────────────────────

    data class DeviceUiModel(
        val address: String,
        val name: String,
        val isBlocked: Boolean,
        val isConnected: Boolean,
    )

    data class UiState(
        val shizukuState: ShizukuHelper.State = ShizukuHelper.State.NotRunning,
        val btPermissionGranted: Boolean = false,
        val bluetoothEnabled: Boolean = false,
        val devices: List<DeviceUiModel> = emptyList(),
        val isLoading: Boolean = true,
        val toggleError: String? = null,
    )

    // ── State ─────────────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val btAdapter: BluetoothAdapter? =
        application.getSystemService(BluetoothManager::class.java)?.adapter

    /**
     * Dynamically-registered receiver for ACL connection and adapter state events.
     * Non-null only after [onBluetoothPermissionResult] has been called with granted = true.
     * Guarded against double-registration by the null check in [onBluetoothPermissionResult].
     */
    private var bluetoothEventReceiver: BroadcastReceiver? = null

    init {
        // Sole long-lived collector on the Room Flow — observes Shizuku state and the
        // blocked-device table together so any Room change triggers a UI refresh.
        viewModelScope.launch {
            combine(
                shizukuHelper.state,
                blockedDeviceDao.getAllDevices()
            ) { shizukuState, blockedDevices ->
                shizukuState to blockedDevices
            }.collect { (shizukuState, blockedDevices) ->
                val currentPermission = _uiState.value.btPermissionGranted
                _uiState.update { state ->
                    state.copy(
                        shizukuState = shizukuState,
                        isLoading = false,
                    )
                }
                if (currentPermission) {
                    refreshDeviceList(blockedDevices)
                }
            }
        }

        // Collect app-level Bluetooth refresh signals with a debounce so rapid bursts
        // (e.g., several ACL events when Bluetooth is toggled off) coalesce into one refresh.
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            getApplication<BluetoothBouncerApp>().refreshSignal
                .debounce(300L)
                .collect {
                    val blockedDevices = blockedDeviceDao.getAllDevices().first()
                    refreshDeviceList(blockedDevices)
                }
        }
    }

    // ── Public actions ────────────────────────────────────────────────────────

    /** Called by the screen after the BLUETOOTH_CONNECT permission result. */
    fun onBluetoothPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(btPermissionGranted = granted, isLoading = !granted) }
        if (granted) {
            registerBluetoothEventReceiver()
            refreshDevices()
        }
    }

    /** Re-read paired devices and re-merge with Room data (one-shot, no coroutine leak). */
    fun refreshDevices() {
        viewModelScope.launch {
            val blockedDevices = blockedDeviceDao.getAllDevices().first()
            refreshDeviceList(blockedDevices)
        }
    }

    /**
     * Toggle block/unblock for a device.
     * Applies an optimistic update, calls Shizuku, updates Room on success,
     * or reverts on failure.
     */
    fun toggleBlock(device: DeviceUiModel) {
        val newBlocked = !device.isBlocked

        // Optimistic update
        _uiState.update { state ->
            state.copy(devices = state.devices.map { d ->
                if (d.address == device.address) d.copy(isBlocked = newBlocked) else d
            })
        }

        viewModelScope.launch {
            val policy = if (newBlocked) ShizukuHelper.POLICY_FORBIDDEN else ShizukuHelper.POLICY_ALLOWED
            val result = shizukuHelper.setConnectionPolicy(device.address, policy)

            if (result.isSuccess) {
                if (newBlocked) {
                    blockedDeviceDao.insertDevice(
                        BlockedDeviceEntity(
                            macAddress = device.address,
                            deviceName = device.name,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                } else {
                    blockedDeviceDao.deleteDevice(device.address)
                }
            } else {
                // Revert toggle and show error
                val errorMsg = if (newBlocked) "Failed to block ${device.name}" else "Failed to unblock ${device.name}"
                Log.w(TAG, "$errorMsg: ${result.exceptionOrNull()?.message}")
                _uiState.update { state ->
                    state.copy(
                        devices = state.devices.map { d ->
                            if (d.address == device.address) d.copy(isBlocked = device.isBlocked) else d
                        },
                        toggleError = errorMsg
                    )
                }
            }
        }
    }

    fun clearToggleError() {
        _uiState.update { it.copy(toggleError = null) }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        unregisterBluetoothEventReceiver()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Registers the Bluetooth ACL / adapter-state receiver.
     * No-op if already registered (guards against double-registration when
     * [onBluetoothPermissionResult] is called more than once with granted = true).
     */
    private fun registerBluetoothEventReceiver() {
        if (bluetoothEventReceiver != null) return  // already registered

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d(TAG, "Bluetooth event received: ${intent.action}")
                getApplication<BluetoothBouncerApp>().refreshSignal.tryEmit(Unit)
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }

        // RECEIVER_EXPORTED is required so the Bluetooth system service (a different package)
        // can deliver ACL and adapter-state broadcasts to this receiver.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplication<Application>().registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            getApplication<Application>().registerReceiver(receiver, filter)
        }

        bluetoothEventReceiver = receiver
        Log.d(TAG, "Bluetooth event receiver registered")
    }

    private fun unregisterBluetoothEventReceiver() {
        bluetoothEventReceiver?.let { receiver ->
            getApplication<Application>().unregisterReceiver(receiver)
            bluetoothEventReceiver = null
            Log.d(TAG, "Bluetooth event receiver unregistered")
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshDeviceList(blockedDevices: List<BlockedDeviceEntity>) {
        val adapter = btAdapter
        if (adapter == null || !adapter.isEnabled) {
            _uiState.update { it.copy(bluetoothEnabled = false, devices = emptyList(), isLoading = false) }
            return
        }

        val blockedAddresses = blockedDevices.map { it.macAddress }.toSet()
        val connectedAddresses = getConnectedDeviceAddresses()

        val devices = adapter.bondedDevices.orEmpty().map { device ->
            DeviceUiModel(
                address = device.address,
                name = device.name ?: device.address,
                isBlocked = device.address in blockedAddresses,
                isConnected = device.address in connectedAddresses,
            )
        }.sortedWith(compareByDescending<DeviceUiModel> { it.isConnected }.thenBy { it.name })

        _uiState.update { it.copy(bluetoothEnabled = true, devices = devices, isLoading = false) }
    }

    /**
     * Checks which bonded devices are currently connected using the hidden
     * BluetoothDevice.isConnected() method via reflection.
     * Available as an @hide API since API 28; safe to call via reflection at runtime.
     */
    @SuppressLint("MissingPermission")
    private fun getConnectedDeviceAddresses(): Set<String> {
        val adapter = btAdapter ?: return emptySet()
        return adapter.bondedDevices.orEmpty().mapNotNull { device ->
            try {
                val method = BluetoothDevice::class.java.getDeclaredMethod("isConnected")
                val connected = method.invoke(device) as? Boolean ?: false
                if (connected) device.address else null
            } catch (e: Exception) {
                null
            }
        }.toSet()
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "DeviceListViewModel"

        fun factory(
            shizukuHelper: ShizukuHelper,
            blockedDeviceDao: BlockedDeviceDao,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                DeviceListViewModel(app, shizukuHelper, blockedDeviceDao)
            }
        }
    }
}
