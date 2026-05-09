package net.harveywilliams.bluetoothbouncer.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.util.Log

/**
 * Utility for checking Bluetooth ACL connection state via the hidden
 * [BluetoothDevice.isConnected] API.
 *
 * The public SDK only exposes profile-level connection state (A2DP, Headset, etc.).
 * Calling the hidden method via reflection is the only way to determine whether a
 * raw ACL link is active — needed for HID keyboards/mice and other profiles not
 * exposed through public proxies.
 *
 * Both [DeviceListViewModel] and [DeviceWatcherService] require this check;
 * centralising it here ensures the reflection approach is maintained in one place.
 */
@SuppressLint("MissingPermission")
object BluetoothAclHelper {

    private const val TAG = "BluetoothAclHelper"

    /**
     * Returns true if [device] has an active ACL connection, using the hidden
     * `BluetoothDevice.isConnected()` method via reflection.
     *
     * Returns false on any reflection failure so callers degrade gracefully.
     */
    fun isConnected(device: BluetoothDevice): Boolean {
        return try {
            val method = BluetoothDevice::class.java.getDeclaredMethod("isConnected")
            method.invoke(device) as? Boolean ?: false
        } catch (e: Exception) {
            Log.w(TAG, "isConnected reflection failed for ${device.address}", e)
            false
        }
    }

    /**
     * Returns the MAC addresses of all bonded devices that have an active ACL link.
     *
     * Iterates [adapter]'s bonded device set and delegates to [isConnected] for each.
     */
    fun getConnectedAddresses(adapter: BluetoothAdapter): Set<String> {
        return adapter.bondedDevices.orEmpty()
            .filter { isConnected(it) }
            .map { it.address }
            .toSet()
    }
}
