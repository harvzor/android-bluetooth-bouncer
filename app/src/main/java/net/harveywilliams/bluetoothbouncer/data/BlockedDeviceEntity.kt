package net.harveywilliams.bluetoothbouncer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_devices")
data class BlockedDeviceEntity(
    @PrimaryKey
    val macAddress: String,
    val deviceName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val cdmAssociationId: Int? = null,
    val isTemporarilyAllowed: Boolean = false,
    val isAlertEnabled: Boolean = false,
)
