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
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    enum class DeviceSection { CONNECTED, DETECTED, BLOCKED, ALLOWED }

    data class DeviceUiModel(
        val address: String,
        val name: String,
        val isBlocked: Boolean,
        val isConnected: Boolean,
        val isDetected: Boolean,
        /** True when the device has an active CDM association for background presence monitoring. */
        val isWatched: Boolean = false,
        /** True when the device was allowed temporarily via the notification action. */
        val isTemporarilyAllowed: Boolean = false,
        /**
         * Seconds since the device was last detected (ACL dropped after being detected), or null
         * if there is no recent detection to show. Counts up from 0 to 29; null after 30 seconds.
         */
        val lastDetectedSecondsAgo: Int? = null,
        /** Which list section this device belongs to. Determined by most-active state. */
        val section: DeviceSection = DeviceSection.ALLOWED,
    )

    data class UiState(
        val shizukuState: ShizukuHelper.State = ShizukuHelper.State.NotRunning,
        val btPermissionGranted: Boolean = false,
        val bluetoothEnabled: Boolean = false,
        val devices: List<DeviceUiModel> = emptyList(),
        val isLoading: Boolean = true,
        val toggleError: String? = null,
        /**
         * MAC address of the device whose Alert toggle is currently in-flight
         * (association dialog shown or request pending). Used to grey out the toggle.
         */
        val watchLoadingAddress: String? = null,
        /** Message to show in a Snackbar when an Alert operation fails or is cancelled. */
        val watchError: String? = null,
        /** Message to show in a Snackbar when Alert is successfully enabled. */
        val watchSuccess: String? = null,
        /** MAC address of a device whose Connect action is in-flight. */
        val connectLoadingAddress: String? = null,
        /** MAC address of a device whose Disconnect action is in-flight. */
        val disconnectLoadingAddress: String? = null,
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

    /**
     * In-memory map from device address to the [SystemClock.elapsedRealtime] timestamp at which
     * the device last dropped out of the detected state. Entries are evicted after 30 seconds by
     * the decay ticker or when the device is detected again.
     */
    private val lastDetectedTimes: MutableMap<String, Long> = mutableMapOf()

    /** Snapshot of detected addresses from the previous [refreshDeviceList] call, used to
     *  detect ACL-drop transitions. */
    private var previousDetectedAddresses: Set<String> = emptySet()

    /** Running decay ticker coroutine, or null when no recent-detection timestamps are active. */
    private var decayTickerJob: Job? = null

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
                    // Unblocking: clean up CDM association if one exists (from Alert or Connect)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val entity = blockedDeviceDao.getDeviceByMac(device.address)
                        val assocId = entity?.cdmAssociationId
                        if (assocId != null) {
                            deviceWatchManager?.disableWatchAndDisassociate(device.address, assocId)
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
     * Toggles the Alert state for a blocked device (API 33+ only).
     *
     * Enabling: initiates a CDM association via [DeviceWatchManager.ensureAssociated]. If a CDM
     * association already exists (from a prior Connect action), the system dialog is skipped.
     * Disabling: stops presence observation and sets [isAlertEnabled] to false, but retains
     * the CDM association so future Connect taps don't need the dialog again.
     */
    fun toggleWatch(device: DeviceUiModel) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val manager = deviceWatchManager ?: return

        if (device.isWatched) {
            // Disable watch (Alert off) — keep CDM association, only stop notifications
            viewModelScope.launch {
                manager.disableWatch(device.address)
            }
        } else {
            // Enable watch (Alert on) — use ensureAssociated to skip dialog if already associated
            _uiState.update { it.copy(watchLoadingAddress = device.address) }
            pendingWatchDevice = device

            viewModelScope.launch {
                val entity = blockedDeviceDao.getDeviceByMac(device.address)
                val existingAssocId = entity?.cdmAssociationId

                manager.ensureAssociated(
                    macAddress = device.address,
                    existingAssociationId = existingAssocId,
                    onPendingIntent = { intentSender ->
                        _watchAssociationIntent.value = intentSender
                    },
                    onSuccess = { associationId ->
                        viewModelScope.launch {
                            try {
                                manager.enableWatch(device.address, associationId)
                                _uiState.update {
                                    it.copy(watchSuccess = "You'll get a notification when this device is nearby. Tap it to temporarily allow a connection.")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "enableWatch failed for ${device.address}", e)
                                _uiState.update {
                                    it.copy(watchError = "Failed to enable Alert: ${e.message}")
                                }
                            } finally {
                                _uiState.update { it.copy(watchLoadingAddress = null) }
                                pendingWatchDevice = null
                            }
                        }
                    },
                    onFailure = { _ ->
                        _uiState.update { it.copy(watchLoadingAddress = null) }
                        pendingWatchDevice = null
                        _watchAssociationIntent.value = null
                    },
                )
            }
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
            _uiState.update { it.copy(watchLoadingAddress = null) }
            pendingWatchDevice = null
        }
        // RESULT_OK: onAssociationCreated already fired and is handled by the onSuccess callback.
    }

    /**
     * Connects a device:
     *  - Blocked device: temporarily allows (CDM-backed, auto-reverts when device leaves range)
     *  - Allowed device: active profile connect, no policy change
     *
     * API 33+ only — callers must guard with the same check.
     */
    fun connectDevice(device: DeviceUiModel) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val manager = deviceWatchManager ?: return

        _uiState.update { it.copy(connectLoadingAddress = device.address) }

        if (device.isBlocked) {
            // Blocked device: ensure CDM association, then set POLICY_ALLOWED + mark temp-allowed
            viewModelScope.launch {
                val entity = blockedDeviceDao.getDeviceByMac(device.address)
                val existingAssocId = entity?.cdmAssociationId

                manager.ensureAssociated(
                    macAddress = device.address,
                    existingAssociationId = existingAssocId,
                    onPendingIntent = { intentSender ->
                        _watchAssociationIntent.value = intentSender
                    },
                    onSuccess = { associationId ->
                        viewModelScope.launch {
                            try {
                                // Start CDM observation so DeviceWatcherService can auto-revert
                                manager.startObservingForConnect(device.address, associationId)
                                // Set policy to allowed + mark as temporarily allowed
                                val result = shizukuHelper.setConnectionPolicy(device.address, ShizukuHelper.POLICY_ALLOWED)
                                if (result.isSuccess) {
                                    blockedDeviceDao.updateIsTemporarilyAllowed(device.address, true)
                                } else {
                                    val msg = "Failed to connect ${device.name}"
                                    Log.w(TAG, "$msg: ${result.exceptionOrNull()?.message}")
                                    _uiState.update { it.copy(toggleError = msg) }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "connectDevice blocked path failed for ${device.address}", e)
                                _uiState.update { it.copy(toggleError = "Failed to connect ${device.name}") }
                            } finally {
                                _uiState.update { it.copy(connectLoadingAddress = null) }
                            }
                        }
                    },
                    onFailure = { _ ->
                        _uiState.update { it.copy(connectLoadingAddress = null) }
                        _watchAssociationIntent.value = null
                    },
                )
            }
        } else {
            // Allowed device: direct connect, no policy change
            viewModelScope.launch {
                try {
                    val result = shizukuHelper.connectDevice(device.address)
                    if (result.isFailure) {
                        val msg = "Failed to connect ${device.name}"
                        Log.w(TAG, "$msg: ${result.exceptionOrNull()?.message}")
                        _uiState.update { it.copy(toggleError = msg) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "connectDevice allowed path failed for ${device.address}", e)
                    _uiState.update { it.copy(toggleError = "Failed to connect ${device.name}") }
                } finally {
                    _uiState.update { it.copy(connectLoadingAddress = null) }
                }
            }
        }
    }

    /**
     * Disconnects a device:
     *  - Blocked or temp-allowed device: re-applies POLICY_FORBIDDEN and clears temp-allowed flag
     *  - Allowed device: active profile disconnect only, no policy change (Android may auto-reconnect)
     *
     * API 33+ only — callers must guard with the same check.
     */
    fun disconnectDevice(device: DeviceUiModel) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        _uiState.update { it.copy(disconnectLoadingAddress = device.address) }

        viewModelScope.launch {
            try {
                val disconnectResult = shizukuHelper.disconnectDevice(device.address)
                if (disconnectResult.isFailure) {
                    val msg = "Failed to disconnect ${device.name}"
                    Log.w(TAG, "$msg: ${disconnectResult.exceptionOrNull()?.message}")
                    _uiState.update { it.copy(toggleError = msg) }
                    return@launch
                }
                // For blocked/temp-allowed devices, also re-apply POLICY_FORBIDDEN
                if (device.isBlocked || device.isTemporarilyAllowed) {
                    val policyResult = shizukuHelper.setConnectionPolicy(device.address, ShizukuHelper.POLICY_FORBIDDEN)
                    if (policyResult.isSuccess) {
                        if (device.isTemporarilyAllowed) {
                            blockedDeviceDao.updateIsTemporarilyAllowed(device.address, false)
                        }
                    } else {
                        Log.w(TAG, "disconnectDevice: re-block failed for ${device.address}: ${policyResult.exceptionOrNull()?.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "disconnectDevice failed for ${device.address}", e)
                _uiState.update { it.copy(toggleError = "Failed to disconnect ${device.name}") }
            } finally {
                _uiState.update { it.copy(disconnectLoadingAddress = null) }
            }
        }
    }

    fun clearToggleError() {
        _uiState.update { it.copy(toggleError = null) }
    }

    fun clearWatchError() {
        _uiState.update { it.copy(watchError = null) }
    }

    fun clearWatchSuccess() {
        _uiState.update { it.copy(watchSuccess = null) }
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
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
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
            lastDetectedTimes.clear()
            previousDetectedAddresses = emptySet()
            _uiState.update { it.copy(bluetoothEnabled = false, devices = emptyList(), isLoading = false) }
            return
        }

        val blockedAddresses = blockedDevices.map { it.macAddress }.toSet()

        // Watched device addresses (Alert enabled)
        val watchedAddresses = blockedDevices
            .filter { it.isAlertEnabled }
            .map { it.macAddress }
            .toSet()

        // Temporarily-allowed device addresses
        val temporarilyAllowedAddresses = blockedDevices
            .filter { it.isTemporarilyAllowed }
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

        // ── Detection-decay transition tracking ──────────────────────────────
        // Addresses that were detected last refresh but are no longer detected: stamp them.
        val justLost = previousDetectedAddresses - detectedAddresses
        val now = SystemClock.elapsedRealtime()
        for (addr in justLost) {
            lastDetectedTimes[addr] = now
        }
        // Addresses that are now detected again: clear any lingering "ago" timestamp.
        for (addr in detectedAddresses) {
            lastDetectedTimes.remove(addr)
        }
        previousDetectedAddresses = detectedAddresses

        val devices = adapter.bondedDevices.orEmpty().map { device ->
            val stamp = lastDetectedTimes[device.address]
            val secondsAgo = if (stamp != null) {
                ((SystemClock.elapsedRealtime() - stamp) / 1000L).toInt().coerceAtMost(29)
            } else null
            val isConn = device.address in connectedAddresses
            val isDet = device.address in detectedAddresses
            val isRecent = secondsAgo != null
            val section = when {
                isConn -> DeviceSection.CONNECTED
                isDet || isRecent -> DeviceSection.DETECTED
                device.address in blockedAddresses -> DeviceSection.BLOCKED
                else -> DeviceSection.ALLOWED
            }
            DeviceUiModel(
                address = device.address,
                name = device.name ?: device.address,
                isBlocked = device.address in blockedAddresses,
                isConnected = isConn,
                isDetected = isDet,
                isWatched = device.address in watchedAddresses,
                isTemporarilyAllowed = device.address in temporarilyAllowedAddresses,
                lastDetectedSecondsAgo = secondsAgo,
                section = section,
            )
        }.sortedWith(
            compareBy<DeviceUiModel> { it.section.ordinal }
                .thenByDescending { it.isDetected }
                .thenBy { it.name }
        )

        _uiState.update { it.copy(bluetoothEnabled = true, devices = devices, isLoading = false) }

        if (lastDetectedTimes.isNotEmpty()) startDecayTickerIfNeeded()
    }

    /**
     * Starts a 1-second decay ticker if one is not already running.
     *
     * Each tick:
     * 1. Evicts [lastDetectedTimes] entries that are 30+ seconds old.
     * 2. Remaps the current device list, recomputing [DeviceUiModel.lastDetectedSecondsAgo]
     *    from the remaining timestamps.
     * 3. Pushes the updated list to [_uiState].
     *
     * The loop exits naturally when [lastDetectedTimes] is empty — no Bluetooth API calls,
     * Room queries, or reflection are performed during ticking.
     */
    private fun startDecayTickerIfNeeded() {
        if (decayTickerJob?.isActive == true) return
        decayTickerJob = viewModelScope.launch {
            while (lastDetectedTimes.isNotEmpty()) {
                delay(1_000L)
                val now = SystemClock.elapsedRealtime()
                // Evict expired entries
                val expired = lastDetectedTimes.entries
                    .filter { (_, stamp) -> now - stamp >= 30_000L }
                    .map { it.key }
                for (addr in expired) lastDetectedTimes.remove(addr)
                // Remap device list in place — no Bluetooth calls
                _uiState.update { state ->
                    state.copy(devices = state.devices.map { device ->
                        val stamp = lastDetectedTimes[device.address]
                        val secondsAgo = if (stamp != null) {
                            ((now - stamp) / 1000L).toInt().coerceAtMost(29)
                        } else null
                        // Recompute section: a decaying device (secondsAgo != null) must stay
                        // in DETECTED for the full window, not drop to BLOCKED/ALLOWED.
                        val section = when {
                            device.isConnected -> DeviceSection.CONNECTED
                            device.isDetected || secondsAgo != null -> DeviceSection.DETECTED
                            device.isBlocked -> DeviceSection.BLOCKED
                            else -> DeviceSection.ALLOWED
                        }
                        device.copy(lastDetectedSecondsAgo = secondsAgo, section = section)
                    })
                }
            }
        }
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
