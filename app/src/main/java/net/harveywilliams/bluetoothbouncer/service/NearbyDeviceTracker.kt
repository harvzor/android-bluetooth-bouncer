package net.harveywilliams.bluetoothbouncer.service

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.harveywilliams.bluetoothbouncer.BluetoothBouncerApp

/**
 * Tracks which blocked devices are currently nearby (within Bluetooth range as reported
 * by [DeviceWatcherService]) and manages the grace-period delay before a departed device
 * is removed from the nearby set.
 *
 * **Why in-memory (not Room)**:
 * CDM delivers presence *transitions*, not current state. Persisting to Room would risk a
 * stale `isNearby=true` after a missed [onDeviceDisappeared] callback, resulting in phantom
 * notifications with no self-healing path. The in-memory default of empty (no phantom) is
 * the safer failure mode.
 *
 * **Grace period**:
 * When a device disappears, [scheduleRemoval] starts a [DETECTION_GRACE_PERIOD_MS] countdown.
 * If the device reappears within that window, [cancelPendingRemoval] cancels the countdown.
 * Jobs run in the supplied [applicationScope] so they survive [DeviceWatcherService] destruction
 * — the service is short-lived and its own scope would be cancelled before any delay could fire.
 *
 * @param applicationScope Application-scoped scope; must outlive any individual service instance.
 */
class NearbyDeviceTracker(private val applicationScope: CoroutineScope) {

    private val _nearbyDevices = MutableStateFlow<Set<String>>(emptySet())

    /**
     * MAC addresses of CDM-associated devices that are currently within Bluetooth range.
     * Observed by [BluetoothBouncerApp.launchNotificationObserver] to drive notification state.
     */
    val nearbyDevices: StateFlow<Set<String>> = _nearbyDevices.asStateFlow()

    /**
     * Pending grace-period removal jobs keyed by MAC address.
     * At most one entry per MAC at any time. Jobs are self-evicting on completion.
     */
    private val pendingRemovals: MutableMap<String, Job> = mutableMapOf()

    /**
     * Adds [mac] to [nearbyDevices] and cancels any pending grace-period removal for it.
     *
     * Call from [DeviceWatcherService.onDeviceAppeared].
     */
    fun addDevice(mac: String) {
        cancelPendingRemoval(mac)
        _nearbyDevices.update { it + mac }
    }

    /**
     * Schedules removal of [mac] from [nearbyDevices] after [DETECTION_GRACE_PERIOD_MS].
     *
     * Any previously scheduled removal for the same MAC is cancelled first. Call from
     * [DeviceWatcherService.onDeviceDisappeared] for non-temporarily-allowed devices.
     */
    fun scheduleRemoval(mac: String, deviceName: String) {
        pendingRemovals[mac]?.cancel()
        pendingRemovals[mac] = applicationScope.launch {
            delay(DETECTION_GRACE_PERIOD_MS)
            _nearbyDevices.update { it - mac }
            pendingRemovals.remove(mac)
            Log.d(TAG, "Grace period expired — removed $deviceName from nearby set")
        }
        Log.d(TAG, "Grace period started for $deviceName ($mac)")
    }

    /**
     * Cancels any pending grace-period removal for [mac].
     *
     * Call from [DeviceWatcherService.onDeviceAppeared] so a device that reappears
     * within the grace window is not removed from [nearbyDevices].
     */
    fun cancelPendingRemoval(mac: String) {
        pendingRemovals.remove(mac)?.cancel()
    }

    /**
     * Removes [mac] from [nearbyDevices] immediately, without a grace period.
     *
     * Call when a temporarily-allowed device is explicitly disconnected or re-blocked.
     */
    fun removeDevice(mac: String) {
        _nearbyDevices.update { it - mac }
    }

    companion object {
        private const val TAG = "NearbyDeviceTracker"

        /**
         * How long a device remains in the nearby set after CDM fires
         * [onDeviceDisappeared] for a non-temporarily-allowed device.
         *
         * Must match [BluetoothBouncerApp.DETECTION_GRACE_PERIOD_MS] — kept as a
         * separate constant here so [NearbyDeviceTracker] has no dependency on the
         * Application class.
         */
        const val DETECTION_GRACE_PERIOD_MS = BluetoothBouncerApp.DETECTION_GRACE_PERIOD_MS
    }
}
