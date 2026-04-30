package net.harveywilliams.bluetoothbouncer

import android.app.Application
import android.companion.CompanionDeviceManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
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

    override fun onCreate() {
        super.onCreate()
        WatchNotificationHelper.createNotificationChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            cleanUpStaleCdmAssociations()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        shizukuHelper.cleanup()
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
