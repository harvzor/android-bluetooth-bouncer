package net.harveywilliams.bluetoothbouncer.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.harveywilliams.bluetoothbouncer.BluetoothBouncerApp
import net.harveywilliams.bluetoothbouncer.shizuku.ShizukuHelper

/**
 * Re-applies FORBIDDEN policies for all blocked devices on boot,
 * but only if Shizuku is running. Handles the edge case where a device
 * was re-paired while Shizuku wasn't available.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "Boot completed — re-applying blocked device policies")
        val app = context.applicationContext as? BluetoothBouncerApp ?: return
        val shizukuHelper = app.shizukuHelper

        // Use goAsync so we don't timeout in the main thread
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                shizukuHelper.refreshState()

                if (shizukuHelper.state.value !is ShizukuHelper.State.Ready) {
                    Log.i(TAG, "Shizuku not ready at boot — skipping policy re-application")
                    return@launch
                }

                val blockedDevices = app.database.blockedDeviceDao().getAllDevices().first()
                Log.d(TAG, "Re-applying FORBIDDEN for ${blockedDevices.size} blocked device(s)")

                for (device in blockedDevices) {
                    val result = shizukuHelper.setConnectionPolicy(
                        device.macAddress,
                        ShizukuHelper.POLICY_FORBIDDEN
                    )
                    if (result.isFailure) {
                        Log.w(TAG, "Failed to re-apply policy for ${device.macAddress}: ${result.exceptionOrNull()?.message}")
                    } else {
                        Log.d(TAG, "Re-applied FORBIDDEN for ${device.macAddress}")
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
