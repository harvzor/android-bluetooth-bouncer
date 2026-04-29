package net.harveywilliams.bluetoothbouncer

import android.app.Application
import net.harveywilliams.bluetoothbouncer.data.AppDatabase
import net.harveywilliams.bluetoothbouncer.shizuku.ShizukuHelper

/**
 * Application class — provides the Room database and ShizukuHelper singletons.
 */
class BluetoothBouncerApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    val shizukuHelper: ShizukuHelper by lazy { ShizukuHelper(this) }

    override fun onTerminate() {
        super.onTerminate()
        shizukuHelper.cleanup()
    }
}
