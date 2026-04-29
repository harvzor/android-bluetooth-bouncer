package net.harveywilliams.bluetoothbouncer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedDeviceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: BlockedDeviceEntity)

    @Query("DELETE FROM blocked_devices WHERE macAddress = :macAddress")
    suspend fun deleteDevice(macAddress: String)

    @Query("SELECT * FROM blocked_devices ORDER BY timestamp DESC")
    fun getAllDevices(): Flow<List<BlockedDeviceEntity>>

    @Query("SELECT * FROM blocked_devices WHERE macAddress = :macAddress LIMIT 1")
    suspend fun getDeviceByMac(macAddress: String): BlockedDeviceEntity?
}
