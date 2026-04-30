package net.harveywilliams.bluetoothbouncer

import android.app.Application
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import net.harveywilliams.bluetoothbouncer.data.AppDatabase
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

    override fun onTerminate() {
        super.onTerminate()
        shizukuHelper.cleanup()
    }
}
