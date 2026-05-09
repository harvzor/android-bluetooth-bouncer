package net.harveywilliams.bluetoothbouncer.receivers

import android.content.BroadcastReceiver
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.harveywilliams.bluetoothbouncer.BluetoothBouncerApp

/**
 * Launches an async coroutine from a [BroadcastReceiver], keeping the receiver
 * alive via [BroadcastReceiver.goAsync] until the coroutine completes.
 *
 * Handles the [BroadcastReceiver.goAsync] + `CoroutineScope(Dispatchers.IO).launch` +
 * `pendingResult.finish()` boilerplate shared by all receivers in this package.
 *
 * The [CoroutineScope] is intentionally not cancelled — the [goAsync] pending result
 * provides the effective lifecycle bound. The coroutine will complete (or be killed by
 * the system's 10-second broadcast timeout) regardless.
 *
 * @param block Suspending lambda receiving the [BluetoothBouncerApp] instance. Each
 *   caller is responsible for its own try/catch — this helper only guarantees
 *   [PendingResult.finish] is called in a finally block.
 */
fun BroadcastReceiver.launchAsync(
    context: Context,
    block: suspend (app: BluetoothBouncerApp) -> Unit,
) {
    val pendingResult = goAsync()
    val app = context.applicationContext as BluetoothBouncerApp
    CoroutineScope(Dispatchers.IO).launch {
        try {
            block(app)
        } finally {
            pendingResult.finish()
        }
    }
}
