package net.harveywilliams.bluetoothbouncer.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import net.harveywilliams.bluetoothbouncer.R
import net.harveywilliams.bluetoothbouncer.data.BlockedDeviceEntity
import net.harveywilliams.bluetoothbouncer.receivers.TemporaryAllowReceiver

/**
 * Helpers for building and posting device-watch notifications.
 *
 * Notification channel [CHANNEL_ID] must be created before any notification is posted.
 * Call [createNotificationChannel] from [BluetoothBouncerApp.onCreate].
 */
object WatchNotificationHelper {

    const val CHANNEL_ID = "device_watch"
    const val ACTION_ALLOW_TEMPORARILY =
        "net.harveywilliams.bluetoothbouncer.ACTION_ALLOW_TEMPORARILY"

    const val EXTRA_MAC_ADDRESS = "extra_mac_address"
    const val EXTRA_DEVICE_NAME = "extra_device_name"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

    /** Deterministic notification ID per device, derived from MAC address. */
    fun notificationId(macAddress: String): Int = macAddress.hashCode()

    /** Error notification ID is offset by 1 to not collide with the main nearby notification. */
    fun errorNotificationId(macAddress: String): Int = macAddress.hashCode() + 1

    /**
     * Creates the [CHANNEL_ID] notification channel (importance HIGH).
     * Safe to call multiple times — the OS ignores duplicate registrations.
     */
    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Device Watch",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alerts when a blocked device with Alert enabled comes into Bluetooth range"
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    /**
     * Posts the "device nearby" notification for [entity].
     *
     * The notification includes an "Allow temporarily" action that fires [TemporaryAllowReceiver].
     */
    fun postNearbyNotification(context: Context, entity: BlockedDeviceEntity) {
        val notifId = notificationId(entity.macAddress)

        val allowIntent = Intent(context, TemporaryAllowReceiver::class.java).apply {
            action = ACTION_ALLOW_TEMPORARILY
            putExtra(EXTRA_MAC_ADDRESS, entity.macAddress)
            putExtra(EXTRA_DEVICE_NAME, entity.deviceName)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
        }
        val allowPendingIntent = PendingIntent.getBroadcast(
            context,
            notifId,
            allowIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_bluetooth)
            .setContentTitle(entity.deviceName)
            .setContentText("Nearby — tap to allow for this session")
            .addAction(0, "Allow temporarily", allowPendingIntent)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(notifId, notification)
    }

    /**
     * Posts an error notification informing the user that Shizuku was not available
     * when the "Allow temporarily" action was tapped.
     */
    fun postErrorNotification(context: Context, macAddress: String, deviceName: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_bluetooth)
            .setContentTitle(deviceName)
            .setContentText("Could not allow — Shizuku unavailable")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(errorNotificationId(macAddress), notification)
    }
}
