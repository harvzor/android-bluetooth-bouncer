package net.harveywilliams.bluetoothbouncer.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.harveywilliams.bluetoothbouncer.BluetoothBouncerApp
import net.harveywilliams.bluetoothbouncer.notification.WatchNotificationHelper
import net.harveywilliams.bluetoothbouncer.shizuku.ShizukuHelper

/**
 * Handles the "Disconnect" notification action on the "temporarily allowed" notification.
 *
 * On receipt:
 * 1. Calls [ShizukuHelper.disconnectDevice] to force-disconnect all profiles.
 * 2. Calls [ShizukuHelper.setConnectionPolicy] with [ShizukuHelper.POLICY_FORBIDDEN] to re-block.
 * 3. Clears `isTemporarilyAllowed = false` in Room — the Application-scoped notification
 *    observer reacts to this write and posts the "Nearby" notification automatically.
 * 5. On failure, posts an error notification instead.
 *
 * Uses [goAsync] to keep the receiver alive long enough for the Shizuku calls to complete.
 */
class DisconnectReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WatchNotificationHelper.ACTION_DISCONNECT) return

        val macAddress = intent.getStringExtra(WatchNotificationHelper.EXTRA_MAC_ADDRESS)
            ?: return
        val deviceName = intent.getStringExtra(WatchNotificationHelper.EXTRA_DEVICE_NAME) ?: ""

        val pendingResult = goAsync()
        val app = context.applicationContext as BluetoothBouncerApp

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val disconnectResult = app.shizukuHelper.disconnectDevice(macAddress)
                if (disconnectResult.isFailure) {
                    Log.w(TAG, "disconnectDevice failed for $macAddress: ${disconnectResult.exceptionOrNull()}")
                    WatchNotificationHelper.postErrorNotification(context, macAddress, deviceName)
                    return@launch
                }

                val policyResult = app.shizukuHelper.setConnectionPolicy(
                    macAddress,
                    ShizukuHelper.POLICY_FORBIDDEN,
                )
                if (policyResult.isSuccess) {
                    app.database.blockedDeviceDao().updateIsTemporarilyAllowed(macAddress, false)
                    // Room write triggers the Application-scoped notification observer,
                    // which will post the "Nearby" notification automatically.
                    Log.d(TAG, "Disconnected and re-blocked $macAddress")
                } else {
                    Log.w(TAG, "setConnectionPolicy failed for $macAddress: ${policyResult.exceptionOrNull()}")
                    WatchNotificationHelper.postErrorNotification(context, macAddress, deviceName)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in DisconnectReceiver for $macAddress", e)
                WatchNotificationHelper.postErrorNotification(context, macAddress, deviceName)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "DisconnectReceiver"
    }
}
