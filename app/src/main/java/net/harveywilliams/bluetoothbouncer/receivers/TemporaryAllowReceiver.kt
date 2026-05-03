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
 * Handles the "Allow temporarily" notification action posted by [DeviceWatcherService].
 *
 * On receipt:
 * 1. Calls [ShizukuHelper.setConnectionPolicy] with [ShizukuHelper.POLICY_ALLOWED].
 * 2. Sets `isTemporarilyAllowed = true` in Room on success — the Application-scoped
 *    notification observer reacts to this write and posts the "temporarily allowed"
 *    notification automatically.
 * 3. On failure, posts an error notification instead.
 *
 * Uses [goAsync] to keep the receiver alive long enough for the Shizuku call to complete.
 */
class TemporaryAllowReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WatchNotificationHelper.ACTION_ALLOW_TEMPORARILY) return

        val macAddress = intent.getStringExtra(WatchNotificationHelper.EXTRA_MAC_ADDRESS)
            ?: return
        val deviceName = intent.getStringExtra(WatchNotificationHelper.EXTRA_DEVICE_NAME) ?: ""

        val pendingResult = goAsync()
        val app = context.applicationContext as BluetoothBouncerApp

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = app.shizukuHelper.setConnectionPolicy(
                    macAddress,
                    ShizukuHelper.POLICY_ALLOWED,
                )
                if (result.isSuccess) {
                    app.database.blockedDeviceDao().updateIsTemporarilyAllowed(macAddress, true)
                    // Room write triggers the Application-scoped notification observer,
                    // which will post the "Temporarily allowed" notification automatically.
                    Log.d(TAG, "Temporarily allowed $macAddress")
                } else {
                    Log.w(TAG, "setConnectionPolicy failed for $macAddress: ${result.exceptionOrNull()}")
                    WatchNotificationHelper.postErrorNotification(context, macAddress, deviceName)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in TemporaryAllowReceiver for $macAddress", e)
                WatchNotificationHelper.postErrorNotification(context, macAddress, deviceName)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "TemporaryAllowReceiver"
    }
}
