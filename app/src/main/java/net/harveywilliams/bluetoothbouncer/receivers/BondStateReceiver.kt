package net.harveywilliams.bluetoothbouncer.receivers

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.harveywilliams.bluetoothbouncer.BluetoothBouncerApp
import net.harveywilliams.bluetoothbouncer.shizuku.ShizukuHelper

/**
 * Listens for Bluetooth bond state changes. When a device transitions to BONDED
 * and was previously in the blocked list, re-applies CONNECTION_POLICY_FORBIDDEN.
 *
 * This handles the case where a user unpairs and re-pairs a blocked device —
 * re-pairing resets the OS-level policy to ALLOWED.
 */
class BondStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return

        val device: BluetoothDevice = intent.getParcelableExtra(
            BluetoothDevice.EXTRA_DEVICE
        ) ?: return

        val newState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
        if (newState != BluetoothDevice.BOND_BONDED) return

        Log.d(TAG, "Device bonded: ${device.address} — checking if previously blocked")

        val app = context.applicationContext as? BluetoothBouncerApp ?: return
        val shizukuHelper = app.shizukuHelper

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val blocked = app.database.blockedDeviceDao().getDeviceByMac(device.address)
                if (blocked == null) {
                    Log.d(TAG, "Device ${device.address} not in blocked list — nothing to do")
                    // Emit so the ViewModel picks up the newly bonded device in the list.
                    app.refreshSignal.tryEmit(Unit)
                    return@launch
                }

                Log.d(TAG, "Device ${device.address} was previously blocked — re-applying FORBIDDEN")

                if (shizukuHelper.state.value !is ShizukuHelper.State.Ready) {
                    Log.w(TAG, "Shizuku not ready — cannot re-apply policy for ${device.address}. " +
                        "Policy will be applied when the user next opens the app with Shizuku running.")
                    app.refreshSignal.tryEmit(Unit)
                    return@launch
                }

                val result = shizukuHelper.setConnectionPolicy(
                    device.address,
                    ShizukuHelper.POLICY_FORBIDDEN
                )
                if (result.isSuccess) {
                    Log.d(TAG, "Re-applied FORBIDDEN for re-paired device ${device.address}")
                } else {
                    Log.w(TAG, "Failed to re-apply FORBIDDEN for ${device.address}: ${result.exceptionOrNull()?.message}")
                }
                // Emit so the ViewModel reflects the bond/policy change immediately.
                app.refreshSignal.tryEmit(Unit)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BondStateReceiver"
    }
}
