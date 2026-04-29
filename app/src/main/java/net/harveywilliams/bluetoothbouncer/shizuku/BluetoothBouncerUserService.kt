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
import android.os.IBinder
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

    // Mutable so reinitializeIfNeeded() can replace dead futures and retry.
    @Volatile private var bluetoothAdapter: BluetoothAdapter? = null

    @Volatile private var a2dpFuture = CompletableFuture<BluetoothA2dp?>()
    @Volatile private var headsetFuture = CompletableFuture<BluetoothHeadset?>()
    @Volatile private var hidFuture = CompletableFuture<Any?>()

    init {
        val ctx = getAppContext()
        bluetoothAdapter = resolveBluetoothAdapter(ctx)

        if (ctx == null || bluetoothAdapter == null) {
            Log.w(TAG, "No context or adapter at init — will retry on first call")
            // Complete futures with null so any early callers don't block forever.
            a2dpFuture.complete(null)
            headsetFuture.complete(null)
            hidFuture.complete(null)
        } else {
            initProxies(ctx, bluetoothAdapter!!)
        }
    }

    /**
     * If the adapter was unavailable at construction (common in Shizuku's app_process
     * environment where ActivityThread isn't fully set up yet), try again now.
     * Replaces the dead CompletableFutures so the new proxy callbacks land correctly.
     */
    @Synchronized
    private fun reinitializeIfNeeded() {
        if (bluetoothAdapter != null) return

        Log.d(TAG, "Retrying initialization on first setConnectionPolicy call")
        val ctx = getAppContext() ?: run {
            Log.e(TAG, "Still no context after retry — blocking will not work")
            return
        }
        val adapter = resolveBluetoothAdapter(ctx) ?: run {
            Log.e(TAG, "Still no BluetoothAdapter after retry")
            return
        }

        bluetoothAdapter = adapter
        // Replace futures (the old ones are already completed with null).
        a2dpFuture = CompletableFuture()
        headsetFuture = CompletableFuture()
        hidFuture = CompletableFuture()
        initProxies(ctx, adapter)
    }

    private fun initProxies(ctx: Context, adapter: BluetoothAdapter) {
        adapter.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                a2dpFuture.complete(proxy as? BluetoothA2dp)
            }
            override fun onServiceDisconnected(profile: Int) {}
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
        // Attempt lazy initialization if init-time context acquisition failed.
        reinitializeIfNeeded()

        val device: BluetoothDevice? = try {
            bluetoothAdapter?.getRemoteDevice(macAddress)
        } catch (e: Exception) {
            Log.e(TAG, "Invalid MAC address: $macAddress", e)
            null
        }
        if (device == null) {
            Log.e(TAG, "setConnectionPolicy: device is null (adapter=${bluetoothAdapter})")
            return intArrayOf(-1, -1, -1)
        }

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
         * Resolves a BluetoothAdapter using three strategies in order:
         *
         *  1. context.getSystemService(BLUETOOTH_SERVICE) — works in normal app processes.
         *  2. BluetoothAdapter.getDefaultAdapter() — deprecated but still functional on some paths.
         *  3. ServiceManager.getService("bluetooth_manager") + hidden BluetoothAdapter constructor —
         *     works in Shizuku's app_process environment where the system service registry is not
         *     wired into the Context but the binder is still accessible as shell UID.
         */
        @SuppressLint("PrivateApi")
        private fun resolveBluetoothAdapter(ctx: Context?): BluetoothAdapter? {
            // Strategy 1: normal system service lookup
            ctx?.let {
                val adapter = (it.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                if (adapter != null) {
                    Log.d(TAG, "BluetoothAdapter via getSystemService()")
                    return adapter
                }
            }

            // Strategy 2: deprecated static getter (still works on some API 31+ paths)
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()?.let {
                Log.d(TAG, "BluetoothAdapter via getDefaultAdapter()")
                return it
            }

            // Strategy 3: get bluetooth_manager binder via ServiceManager, then construct adapter directly.
            // ServiceManager is accessible as shell UID. BluetoothAdapter has a hidden constructor that
            // takes an IBluetoothManager — same path getDefaultAdapter() uses internally but called explicitly.
            return try {
                val binder = Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String::class.java)
                    .invoke(null, "bluetooth_manager") as? IBinder
                    ?: return null.also { Log.e(TAG, "ServiceManager.getService(bluetooth_manager) returned null") }

                val iBluetoothManagerClass = Class.forName("android.bluetooth.IBluetoothManager\$Stub")
                val managerService = iBluetoothManagerClass
                    .getMethod("asInterface", IBinder::class.java)
                    .invoke(null, binder)

                val iBluetoothManagerInterface = Class.forName("android.bluetooth.IBluetoothManager")

                // Try one-arg constructor first (it builds AttributionSource internally).
                // Fall back to two-arg (API 31+) with a real AttributionSource if one-arg is absent.
                val adapter: BluetoothAdapter? = try {
                    BluetoothAdapter::class.java
                        .getDeclaredConstructor(iBluetoothManagerInterface)
                        .also { it.isAccessible = true }
                        .newInstance(managerService) as? BluetoothAdapter
                } catch (e: NoSuchMethodException) {
                    val attributionSourceClass = Class.forName("android.content.AttributionSource")
                    val myAttributionSource = attributionSourceClass
                        .getMethod("myAttributionSource")
                        .invoke(null)
                    BluetoothAdapter::class.java
                        .getDeclaredConstructor(iBluetoothManagerInterface, attributionSourceClass)
                        .also { it.isAccessible = true }
                        .newInstance(managerService, myAttributionSource) as? BluetoothAdapter
                }

                if (adapter == null) {
                    Log.e(TAG, "BluetoothAdapter constructor returned null")
                    return null
                }

                Log.d(TAG, "BluetoothAdapter via ServiceManager reflection")

                // BluetoothProfileConnector has a field initialized as:
                //   private final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                // This runs BEFORE the constructor body, so it uses the static singleton.
                // If getDefaultAdapter() returns null (as it does in this process), every
                // BluetoothProfileConnector.connect() call NPEs. Fix: populate the static
                // sAdapter singleton BEFORE getProfileProxy() creates any BluetoothA2dp/etc.
                try {
                    BluetoothAdapter::class.java
                        .getDeclaredField("sAdapter")
                        .also { it.isAccessible = true }
                        .set(null, adapter)
                    Log.d(TAG, "Set BluetoothAdapter.sAdapter")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set sAdapter — getProfileProxy may still fail: ${e.message}")
                }

                adapter
            } catch (e: Exception) {
                Log.e(TAG, "BluetoothAdapter via ServiceManager failed: ${e.message}", e)
                null
            }
        }

        /**
         * Gets a usable Context for the Shizuku UserService process.
         *
         * Strategy:
         *  1. Try ActivityThread.currentApplication() — works if Shizuku has wired up a real app.
         *  2. Fall back to ActivityThread.currentActivityThread().getSystemContext() — always
         *     available in app_process environments (which is how Shizuku starts UserServices),
         *     even without a real Application object.
         */
        @SuppressLint("PrivateApi")
        private fun getAppContext(): Context? {
            // Attempt 1: real Application context
            try {
                val app = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication")
                    .invoke(null) as? Application
                if (app != null) {
                    Log.d(TAG, "Got context via currentApplication()")
                    return app
                }
            } catch (e: Exception) {
                Log.w(TAG, "currentApplication() failed: ${e.message}")
            }

            // Attempt 2: system context from the ActivityThread (always present in app_process)
            try {
                val atClass = Class.forName("android.app.ActivityThread")
                val thread = atClass.getDeclaredMethod("currentActivityThread")
                    .also { it.isAccessible = true }
                    .invoke(null)
                if (thread != null) {
                    val ctx = atClass.getDeclaredMethod("getSystemContext")
                        .also { it.isAccessible = true }
                        .invoke(thread) as? Context
                    if (ctx != null) {
                        Log.d(TAG, "Got context via getSystemContext()")
                        return ctx
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "getSystemContext() failed: ${e.message}")
            }

            Log.e(TAG, "Could not obtain any Context — Bluetooth proxy init will fail")
            return null
        }
    }
}
