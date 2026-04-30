package net.harveywilliams.bluetoothbouncer.viewmodel

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
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
import net.harveywilliams.bluetoothbouncer.service.DeviceWatchManager
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
        val isDetected: Boolean,
        /** True when the device has an active CDM association for background presence monitoring. */
        val isWatched: Boolean = false,
    )

    data class UiState(
        val shizukuState: ShizukuHelper.State = ShizukuHelper.State.NotRunning,
        val btPermissionGranted: Boolean = false,
        val bluetoothEnabled: Boolean = false,
        val devices: List<DeviceUiModel> = emptyList(),
        val isLoading: Boolean = true,
        val toggleError: String? = null,
        /**
         * MAC address of the device whose Watch toggle is currently in-flight
         * (association dialog shown or request pending). Used to grey out the toggle.
         */
        val watchLoadingAddress: String? = null,
        /** Message to show in a Snackbar when a Watch operation fails or is cancelled. */
        val watchError: String? = null,
    )

    // ── State ─────────────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * Emits an [IntentSender] when the CDM association dialog needs to be launched by the UI.
     * The composable should observe this, launch via [startIntentSenderForResult], then call
     * [onWatchAssociationResult] with the result code.
     */
    private val _watchAssociationIntent = MutableStateFlow<IntentSender?>(null)
    val watchAssociationIntent: StateFlow<IntentSender?> = _watchAssociationIntent.asStateFlow()

    /** The device currently undergoing a Watch association flow. */
    private var pendingWatchDevice: DeviceUiModel? = null

    private val btAdapter: BluetoothAdapter? =
        application.getSystemService(BluetoothManager::class.java)?.adapter

    /**
     * Manages CompanionDeviceManager operations (associate, startObservingDevicePresence, etc.).
     * Null on API < 33 — all call sites must guard with the same API check.
     */
    private val deviceWatchManager: DeviceWatchManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            DeviceWatchManager(application, blockedDeviceDao)
        } else null

    /**
     * Dynamically-registered receiver for ACL connection and adapter state events.
     * Non-null only after [onBluetoothPermissionResult] has been called with granted = true.
     * Guarded against double-registration by the null check in [registerBluetoothEventReceiver].
     */
    private var bluetoothEventReceiver: BroadcastReceiver? = null

    /**
     * Profile proxies for A2DP and Headset — used to determine connection state at the
     * profile level rather than the ACL level. This avoids false "Connected" indicators
     * on devices where CONNECTION_POLICY_FORBIDDEN maintains the ACL link after all profiles
     * are disconnected.
     *
     * Initialised after BLUETOOTH_CONNECT permission is granted. May be null before that
     * or if the profile service is unavailable; [refreshDeviceList] falls back gracefully.
     */
    @Volatile private var a2dpProxy: BluetoothA2dp? = null
    @Volatile private var headsetProxy: BluetoothHeadset? = null

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
            initProfileProxies()
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
     *
     * When unblocking a watched device (non-null [cdmAssociationId]), this also runs
     * the disable-watch flow ([DeviceWatchManager.disableWatch]) before deleting the Room row.
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
                    // Unblocking: clean up CDM association if Watch was enabled
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val entity = blockedDeviceDao.getDeviceByMac(device.address)
                        val assocId = entity?.cdmAssociationId
                        if (assocId != null) {
                            deviceWatchManager?.disableWatch(device.address, assocId)
                        }
                    }
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

    /**
     * Toggles the Watch state for a blocked device (API 33+ only).
     *
     * Enabling: initiates a CDM association via [DeviceWatchManager.associate]. A pending
     * [IntentSender] is emitted via [watchAssociationIntent] for the UI to launch.
     * Disabling: stops presence observation and removes the CDM association.
     */
    fun toggleWatch(device: DeviceUiModel) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val manager = deviceWatchManager ?: return

        if (device.isWatched) {
            // Disable watch
            viewModelScope.launch {
                val entity = blockedDeviceDao.getDeviceByMac(device.address) ?: return@launch
                val assocId = entity.cdmAssociationId ?: return@launch
                manager.disableWatch(device.address, assocId)
            }
        } else {
            // Enable watch — begin association flow
            _uiState.update { it.copy(watchLoadingAddress = device.address) }
            pendingWatchDevice = device

            manager.associate(
                macAddress = device.address,
                onPendingIntent = { intentSender ->
                    _watchAssociationIntent.value = intentSender
                },
                onSuccess = { associationInfo ->
                    viewModelScope.launch {
                        try {
                            manager.enableWatch(device.address, associationInfo.id)
                        } catch (e: Exception) {
                            Log.e(TAG, "enableWatch failed for ${device.address}", e)
                            _uiState.update {
                                it.copy(watchError = "Failed to enable Watch: ${e.message}")
                            }
                        } finally {
                            _uiState.update { it.copy(watchLoadingAddress = null) }
                            pendingWatchDevice = null
                        }
                    }
                },
                onFailure = { _ ->
                    _uiState.update {
                        it.copy(
                            watchLoadingAddress = null,
                            watchError = "Device not found nearby. Try again when it's in Bluetooth range.",
                        )
                    }
                    pendingWatchDevice = null
                    _watchAssociationIntent.value = null
                },
            )
        }
    }

    /**
     * Called by the UI with the result from the CDM association dialog.
     * [Activity.RESULT_OK] means the user confirmed — [onSuccess] in [toggleWatch] handles it.
     * Any other result means the user cancelled or the dialog failed.
     */
    fun onWatchAssociationResult(resultCode: Int) {
        _watchAssociationIntent.value = null
        if (resultCode != Activity.RESULT_OK) {
            _uiState.update {
                it.copy(
                    watchLoadingAddress = null,
                    watchError = "Device not found nearby. Try again when it's in Bluetooth range.",
                )
            }
            pendingWatchDevice = null
        }
        // RESULT_OK: onAssociationCreated already fired and is handled by the onSuccess callback.
    }

    fun clearToggleError() {
        _uiState.update { it.copy(toggleError = null) }
    }

    fun clearWatchError() {
        _uiState.update { it.copy(watchError = null) }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        unregisterBluetoothEventReceiver()
        a2dpProxy?.let { btAdapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }
        headsetProxy?.let { btAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, it) }
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

    /**
     * Requests profile proxies for A2DP and Headset so [refreshDeviceList] can use
     * profile-level connection state instead of the ACL-level [BluetoothDevice.isConnected].
     * No-op if the adapter is unavailable. Safe to call more than once — the system
     * returns the existing proxy if one is already bound.
     */
    @SuppressLint("MissingPermission")
    private fun initProfileProxies() {
        val adapter = btAdapter ?: return
        adapter.getProfileProxy(getApplication(), object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                a2dpProxy = proxy as? BluetoothA2dp
                Log.d(TAG, "A2DP proxy connected")
            }
            override fun onServiceDisconnected(profile: Int) {
                a2dpProxy = null
                Log.d(TAG, "A2DP proxy disconnected")
            }
        }, BluetoothProfile.A2DP)

        adapter.getProfileProxy(getApplication(), object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                headsetProxy = proxy as? BluetoothHeadset
                Log.d(TAG, "Headset proxy connected")
            }
            override fun onServiceDisconnected(profile: Int) {
                headsetProxy = null
                Log.d(TAG, "Headset proxy disconnected")
            }
        }, BluetoothProfile.HEADSET)
    }

    @SuppressLint("MissingPermission")
    private fun refreshDeviceList(blockedDevices: List<BlockedDeviceEntity>) {
        val adapter = btAdapter
        if (adapter == null || !adapter.isEnabled) {
            _uiState.update { it.copy(bluetoothEnabled = false, devices = emptyList(), isLoading = false) }
            return
        }

        val blockedAddresses = blockedDevices.map { it.macAddress }.toSet()

        // Watched device addresses (have a non-null cdmAssociationId)
        val watchedAddresses = blockedDevices
            .filter { it.cdmAssociationId != null }
            .map { it.macAddress }
            .toSet()

        // Profile-level connected addresses — not affected by CONNECTION_POLICY_FORBIDDEN
        // maintaining an ACL link after all profiles are disconnected.
        val a2dpConnected = a2dpProxy?.connectedDevices.orEmpty().map { it.address }.toSet()
        val headsetConnected = headsetProxy?.connectedDevices.orEmpty().map { it.address }.toSet()
        val profileConnected = a2dpConnected + headsetConnected

        // ACL-level connected addresses — fallback for HID keyboards/mice and other profiles
        // not covered by the proxies above. Excluded for blocked devices to avoid false
        // positives from the Bluetooth stack maintaining an ACL link for policy enforcement.
        val aclConnected = getAclConnectedAddresses()

        val connectedAddresses = profileConnected + (aclConnected - blockedAddresses)

        // ACL-present but not profile-connected — covers blocked devices nearby and other
        // paired devices that have an ACL link without a profile connection.
        val detectedAddresses = aclConnected - connectedAddresses

        val devices = adapter.bondedDevices.orEmpty().map { device ->
            DeviceUiModel(
                address = device.address,
                name = device.name ?: device.address,
                isBlocked = device.address in blockedAddresses,
                isConnected = device.address in connectedAddresses,
                isDetected = device.address in detectedAddresses,
                isWatched = device.address in watchedAddresses,
            )
        }.sortedWith(compareByDescending<DeviceUiModel> { it.isConnected }.thenByDescending { it.isDetected }.thenBy { it.name })

        _uiState.update { it.copy(bluetoothEnabled = true, devices = devices, isLoading = false) }
    }

    /**
     * Returns addresses of bonded devices that have an active ACL connection, via the hidden
     * [BluetoothDevice.isConnected] method. Used as a fallback for profiles (e.g. HID) not
     * covered by the A2DP / Headset proxies.
     */
    @SuppressLint("MissingPermission")
    private fun getAclConnectedAddresses(): Set<String> {
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
