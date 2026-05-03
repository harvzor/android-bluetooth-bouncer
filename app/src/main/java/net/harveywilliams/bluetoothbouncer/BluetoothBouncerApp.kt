package net.harveywilliams.bluetoothbouncer

import android.app.Application
import android.companion.CompanionDeviceManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.harveywilliams.bluetoothbouncer.data.AppDatabase
import net.harveywilliams.bluetoothbouncer.notification.WatchNotificationHelper
import net.harveywilliams.bluetoothbouncer.shizuku.ShizukuHelper

/**
 * Application class — provides the Room database, ShizukuHelper, and the app-level
 * Bluetooth refresh signal singletons.
 */
class BluetoothBouncerApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    val shizukuHelper: ShizukuHelper by lazy { ShizukuHelper(this) }

    /**
     * App-level event bus for Bluetooth state changes (ACL connect/disconnect, bond state,
     * adapter on/off). Any component (ViewModel receiver, BondStateReceiver) can emit Unit
     * into this flow; [DeviceListViewModel] collects it with a debounce to trigger a refresh.
     */
    val refreshSignal: MutableSharedFlow<Unit> = MutableSharedFlow(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * In-memory set of MAC addresses whose CDM-associated devices are currently nearby
     * (i.e., within Bluetooth range as reported by [DeviceWatcherService]).
     *
     * This is intentionally NOT persisted to Room. CDM delivers presence transitions, not
     * current state — using Room would risk a stale isNearby=true after a missed
     * onDeviceDisappeared callback, resulting in a phantom notification with no self-healing
     * path. The in-memory default of empty (no phantom) is the safer failure mode.
     */
    val nearbyDevices: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())

    /**
     * Application-scoped coroutine scope for long-lived observers that must outlive any
     * individual Activity, ViewModel, or Service.
     */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        WatchNotificationHelper.createNotificationChannel(this)
        // Trigger lazy init immediately so Shizuku binding starts at process launch.
        // This narrows the race window where setConnectionPolicy() is called before
        // the UserService binder is delivered (e.g. notification action on cold start).
        shizukuHelper
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            cleanUpStaleCdmAssociations()
        }
        launchNotificationObserver()
    }

    override fun onTerminate() {
        super.onTerminate()
        shizukuHelper.cleanup()
    }

    /**
     * Launches an Application-scoped observer that reactively posts or cancels the correct
     * device-watch notification for each blocked device based on its current presence and
     * temporary-allow state.
     *
     * Logic:
     *  - MAC in nearbyDevices + isTemporarilyAllowed  → postAllowedNotification
     *  - MAC in nearbyDevices + isAlertEnabled         → postNearbyNotification
     *  - MAC in nearbyDevices only (no alert, no temp) → cancel (CDM-only device, no alert)
     *  - MAC not in nearbyDevices                      → cancel
     *
     * [WatchNotificationHelper] uses setOnlyAlertOnce(true) so re-posting an identical
     * notification does not re-alert the user with sound or vibration.
     */
    private fun launchNotificationObserver() {
        applicationScope.launch {
            combine(nearbyDevices, database.blockedDeviceDao().getAllDevices()) { nearby, devices ->
                nearby to devices
            }.collect { (nearby, devices) ->
                for (device in devices) {
                    val mac = device.macAddress
                    val notifId = WatchNotificationHelper.notificationId(mac)
                    when {
                        mac in nearby && device.isTemporarilyAllowed ->
                            WatchNotificationHelper.postAllowedNotification(this@BluetoothBouncerApp, mac, device.deviceName)
                        mac in nearby && device.isAlertEnabled ->
                            WatchNotificationHelper.postNearbyNotification(this@BluetoothBouncerApp, mac, device.deviceName)
                        else ->
                            androidx.core.app.NotificationManagerCompat.from(this@BluetoothBouncerApp).cancel(notifId)
                    }
                }
            }
        }
    }

    /**
     * Removes any CDM associations that exist at the OS level but are no longer tracked in Room.
     *
     * This can happen when the app crashes mid-association (before [cdmAssociationId] is
     * persisted), or when the database is cleared. Orphaned associations cause the CDM to keep
     * delivering [onDeviceAppeared]/[onDeviceDisappeared] callbacks with stale IDs that no
     * longer match any Room row.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun cleanUpStaleCdmAssociations() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cdm = getSystemService(CompanionDeviceManager::class.java)
                val osAssociations = cdm.myAssociations
                if (osAssociations.isEmpty()) return@launch

                val dao = database.blockedDeviceDao()
                val knownIds = dao.getWatchedDevices().mapNotNull { it.cdmAssociationId }.toSet()

                for (assoc in osAssociations) {
                    if (assoc.id !in knownIds) {
                        Log.d(TAG, "Removing stale CDM association id=${assoc.id} mac=${assoc.deviceMacAddress}")
                        try {
                            cdm.disassociate(assoc.id)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to disassociate stale id=${assoc.id}", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "cleanUpStaleCdmAssociations failed", e)
            }
        }
    }

    companion object {
        private const val TAG = "BluetoothBouncerApp"
    }
}
