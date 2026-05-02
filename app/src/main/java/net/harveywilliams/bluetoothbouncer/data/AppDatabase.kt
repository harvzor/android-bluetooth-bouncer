package net.harveywilliams.bluetoothbouncer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [BlockedDeviceEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun blockedDeviceDao(): BlockedDeviceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bluetooth_bouncer_database"
                )
                    .fallbackToDestructiveMigration() // dev-only: remove before public distribution
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
