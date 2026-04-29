package net.harveywilliams.bluetoothbouncer.shizuku

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import net.harveywilliams.bluetoothbouncer.IBluetoothBouncerUserService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Shizuku UserService — runs as shell UID (which holds BLUETOOTH_PRIVILEGED).
 * Instantiated by Shizuku in a separate process. Uses reflection to get context
 * and to call the hidden setConnectionPolicy method on each profile proxy.
 */
class BluetoothBouncerUserService : IBluetoothBouncerUserService.Stub() {

    private val context: Context? = getAppContext()

    private val bluetoothAdapter: BluetoothAdapter? =
        (context?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()

    private val a2dpFuture = CompletableFuture<BluetoothA2dp?>()
    private val headsetFuture = CompletableFuture<BluetoothHeadset?>()
    private val hidFuture = CompletableFuture<Any?>()

    init {
        initProxies()
    }

    private fun initProxies() {
        val ctx = context
        val adapter = bluetoothAdapter
        if (ctx == null || adapter == null) {
            Log.w(TAG, "No context or adapter — skipping proxy init")
            a2dpFuture.complete(null)
            headsetFuture.complete(null)
            hidFuture.complete(null)
            return
        }

        adapter.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                a2dpFuture.complete(proxy as? BluetoothA2dp)
            }
            override fun onServiceDisconnected(profile: Int) {
                // Not re-connected here; caller will use cached result
            }
        }, BluetoothProfile.A2DP)

        adapter.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                headsetFuture.complete(proxy as? BluetoothHeadset)
            }
            override fun onServiceDisconnected(profile: Int) {}
        }, BluetoothProfile.HEADSET)

        // HID Host — BluetoothProfile.HID_HOST = 4 (constant not in compile-time SDK; use literal)
        adapter.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                hidFuture.complete(proxy)
            }
            override fun onServiceDisconnected(profile: Int) {}
        }, 4 /* HID_HOST */)
    }

    override fun setConnectionPolicy(macAddress: String, policy: Int): IntArray {
        val device: BluetoothDevice? = try {
            bluetoothAdapter?.getRemoteDevice(macAddress)
        } catch (e: Exception) {
            Log.e(TAG, "Invalid MAC address: $macAddress", e)
            null
        }
        if (device == null) return intArrayOf(-1, -1, -1)

        val results = IntArray(3) { -1 }
        val timeoutSec = 8L

        try {
            val a2dp = a2dpFuture.get(timeoutSec, TimeUnit.SECONDS)
            results[0] = if (a2dp != null) callSetConnectionPolicy(a2dp, device, policy) else -1
        } catch (e: Exception) {
            Log.w(TAG, "A2DP proxy timeout/error", e)
        }

        try {
            val headset = headsetFuture.get(timeoutSec, TimeUnit.SECONDS)
            results[1] = if (headset != null) callSetConnectionPolicy(headset, device, policy) else -1
        } catch (e: Exception) {
            Log.w(TAG, "Headset proxy timeout/error", e)
        }

        try {
            val hid = hidFuture.get(timeoutSec, TimeUnit.SECONDS)
            results[2] = if (hid != null) callSetConnectionPolicy(hid, device, policy) else -1
        } catch (e: Exception) {
            Log.w(TAG, "HID proxy timeout/error", e)
        }

        Log.d(TAG, "setConnectionPolicy($macAddress, $policy) → ${results.toList()}")
        return results
    }

    /**
     * Calls proxy.setConnectionPolicy(device, policy) via reflection.
     * The method is hidden API but accessible when running as shell UID.
     */
    private fun callSetConnectionPolicy(proxy: Any, device: BluetoothDevice, policy: Int): Int {
        return try {
            val method = proxy.javaClass.getMethod(
                "setConnectionPolicy",
                BluetoothDevice::class.java,
                Int::class.java
            )
            val result = method.invoke(proxy, device, policy) as? Boolean ?: false
            if (result) 1 else 0
        } catch (e: Exception) {
            Log.e(TAG, "setConnectionPolicy reflection failed on ${proxy.javaClass.simpleName}", e)
            0
        }
    }

    override fun isAlive(): Boolean = true

    companion object {
        private const val TAG = "BBUserService"

        /**
         * Gets the application context via ActivityThread reflection.
         * Works in Shizuku UserService processes because Shizuku uses app_process to start them.
         */
        @SuppressLint("PrivateApi")
        private fun getAppContext(): Context? {
            return try {
                Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication")
                    .invoke(null) as? Application
            } catch (e: Exception) {
                Log.w("BBUserService", "Could not get app context via ActivityThread", e)
                null
            }
        }
    }
}
