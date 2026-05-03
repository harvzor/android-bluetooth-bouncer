package net.harveywilliams.bluetoothbouncer.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.harveywilliams.bluetoothbouncer.BluetoothBouncerApp
import net.harveywilliams.bluetoothbouncer.notification.WatchNotificationHelper
import net.harveywilliams.bluetoothbouncer.shizuku.ShizukuHelper
import androidx.core.app.NotificationManagerCompat

/**
 * Background presence-detection service for watched blocked devices.
 *
 * The OS wakes this service whenever a registered CDM-associated device appears or disappears,
 * even when the app process is not running.
 *
 * Requires API 33 (TIRAMISU) for [onDeviceAppeared] and [onDeviceDisappeared] with
 * [AssociationInfo] parameter. [CompanionDeviceService] itself was added in API 31, but
 * presence observation ([startObservingDevicePresence]) and these callbacks require API 33.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class DeviceWatcherService : CompanionDeviceService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Called when a CDM-associated device comes into Bluetooth range.
     *
     * Posts the "nearby" notification if the device is still blocked and not yet
     * temporarily allowed.
     */
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        val associationId = associationInfo.id
        val mac = associationInfo.deviceMacAddress?.toString()?.uppercase()
        Log.d(TAG, "onDeviceAppeared: associationId=$associationId mac=$mac")

        serviceScope.launch {
            val app = applicationContext as BluetoothBouncerApp
            val dao = app.database.blockedDeviceDao()
            val entity = resolveEntity(dao, associationId, mac) ?: run {
                Log.w(TAG, "onDeviceAppeared: no entity found for associationId=$associationId mac=$mac")
                return@launch
            }
            // Heal any stale association ID persisted in Room
            if (entity.cdmAssociationId != associationId) {
                Log.d(TAG, "Updating stale cdmAssociationId ${entity.cdmAssociationId} -> $associationId for ${entity.macAddress}")
                dao.updateCdmAssociationId(entity.macAddress, associationId)
            }
            if (!entity.isTemporarilyAllowed && entity.isAlertEnabled) {
                WatchNotificationHelper.postNearbyNotification(this@DeviceWatcherService, entity)
                Log.d(TAG, "Posted nearby notification for ${entity.deviceName}")
            } else if (entity.isTemporarilyAllowed) {
                Log.d(TAG, "Device ${entity.deviceName} already temporarily allowed — skipping notification")
            } else {
                Log.d(TAG, "Device ${entity.deviceName} has no Alert enabled — skipping notification")
            }
        }
    }

    /**
     * Called when a CDM-associated device leaves Bluetooth range.
     *
     * If the device was temporarily allowed and is not actively profile-connected (ACL),
     * re-applies [ShizukuHelper.POLICY_FORBIDDEN] and clears the temporary-allow flag.
     */
    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        val associationId = associationInfo.id
        val mac = associationInfo.deviceMacAddress?.toString()?.uppercase()
        Log.d(TAG, "onDeviceDisappeared: associationId=$associationId mac=$mac")

        serviceScope.launch {
            val app = applicationContext as BluetoothBouncerApp
            val dao = app.database.blockedDeviceDao()
            val entity = resolveEntity(dao, associationId, mac) ?: run {
                Log.w(TAG, "onDeviceDisappeared: no entity found for associationId=$associationId mac=$mac")
                return@launch
            }

            if (!entity.isTemporarilyAllowed) {
                Log.d(TAG, "Device ${entity.deviceName} is not temporarily allowed — no action needed")
                return@launch
            }

            if (isDeviceAclConnected(entity.macAddress)) {
                Log.d(TAG, "Device ${entity.deviceName} still ACL-connected — deferring re-block")
                return@launch
            }

            val result = app.shizukuHelper.setConnectionPolicy(
                entity.macAddress,
                ShizukuHelper.POLICY_FORBIDDEN,
            )
            if (result.isSuccess) {
                dao.updateIsTemporarilyAllowed(entity.macAddress, false)
                NotificationManagerCompat.from(this@DeviceWatcherService)
                    .cancel(WatchNotificationHelper.notificationId(entity.macAddress))
                Log.d(TAG, "Re-blocked ${entity.deviceName} after departure")
            } else {
                Log.e(TAG, "Failed to re-block ${entity.deviceName}: ${result.exceptionOrNull()}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    /**
     * Returns the [BlockedDeviceEntity] for the given [associationId], falling back to a
     * MAC-address lookup if no row matches the ID directly.
     *
     * The fallback handles stale association IDs that were created before the app crashed or
     * was reinstalled — the CDM may keep delivering callbacks for an old ID while Room only
     * knows about a newer one.
     */
    private suspend fun resolveEntity(
        dao: net.harveywilliams.bluetoothbouncer.data.BlockedDeviceDao,
        associationId: Int,
        mac: String?,
    ): net.harveywilliams.bluetoothbouncer.data.BlockedDeviceEntity? {
        dao.getDeviceByAssociationId(associationId)?.let { return it }
        if (mac != null) {
            val byMac = dao.getDeviceByMac(mac)
            // Only use the MAC fallback if this device is actually watched (non-null cdmAssociationId)
            if (byMac?.cdmAssociationId != null) return byMac
        }
        return null
    }

    /**
     * Returns true if a Bluetooth ACL link to the given [macAddress] is still active.
     *
     * Uses the hidden [BluetoothDevice.isConnected] API via reflection (same approach as the
     * existing codebase). A live ACL link means at least one profile is still connected.
     */
    @SuppressLint("MissingPermission")
    private fun isDeviceAclConnected(macAddress: String): Boolean {
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
            val device = adapter.getRemoteDevice(macAddress)
            val method = device.javaClass.getMethod("isConnected")
            method.invoke(device) as? Boolean ?: false
        } catch (e: Exception) {
            Log.w(TAG, "isDeviceAclConnected failed for $macAddress", e)
            false
        }
    }

    companion object {
        private const val TAG = "DeviceWatcherService"
    }
}
