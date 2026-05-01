package net.harveywilliams.bluetoothbouncer.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import net.harveywilliams.bluetoothbouncer.BuildConfig
import net.harveywilliams.bluetoothbouncer.IBluetoothBouncerUserService
import rikka.shizuku.Shizuku

/**
 * Manages all Shizuku integration: state detection, permission requests,
 * and UserService lifecycle.
 */
class ShizukuHelper(private val context: Context) {

    sealed class State {
        /** Shizuku app is not installed on the device. */
        object NotInstalled : State()

        /** Shizuku is installed but not currently running (needs ADB/Wireless Debug start). */
        object NotRunning : State()

        /** Shizuku is running but permission has not been granted to this app. */
        object PermissionDenied : State()

        /** Shizuku is running, permission granted, UserService is bound and ready. */
        object Ready : State()
    }

    private val _state = MutableStateFlow<State>(State.NotRunning)
    val state: StateFlow<State> = _state.asStateFlow()

    private var userService: IBluetoothBouncerUserService? = null

    // ── Shizuku listeners ────────────────────────────────────────────────────

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder received")
        refreshState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d(TAG, "Shizuku binder dead")
        userService = null
        _state.value = State.NotRunning
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == PERMISSION_REQUEST_CODE) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    refreshState()
                } else {
                    _state.value = State.PermissionDenied
                }
            }
        }

    // ── UserService connection ───────────────────────────────────────────────

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            Log.d(TAG, "UserService connected")
            userService = IBluetoothBouncerUserService.Stub.asInterface(binder)
            _state.value = State.Ready
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.d(TAG, "UserService disconnected")
            userService = null
            _state.value = if (isShizukuRunning()) State.PermissionDenied else State.NotRunning
        }
    }

    init {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        refreshState()
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /** Manually re-check and update the current Shizuku state. */
    fun refreshState() {
        when {
            !isShizukuInstalled() -> {
                _state.value = State.NotInstalled
            }
            !isShizukuRunning() -> {
                userService = null
                _state.value = State.NotRunning
            }
            !hasPermission() -> {
                _state.value = State.PermissionDenied
            }
            userService == null -> {
                // Permission granted — bind the UserService
                bindUserService()
            }
            else -> {
                _state.value = State.Ready
            }
        }
    }

    /** Request Shizuku permission from the user. */
    fun requestPermission() {
        if (isShizukuRunning()) {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        }
    }

    /**
     * Call setConnectionPolicy on the UserService.
     * Returns a [Result] wrapping the int[] from the service,
     * or a failure if the service is unavailable.
     *
     * If [userService] is null but Shizuku is running and permission is granted,
     * the UserService bind is likely still in progress (common on cold start).
     * In that case this function suspends and waits up to [BIND_TIMEOUT_MS] for
     * [State.Ready] before proceeding, rather than failing immediately.
     */
    suspend fun setConnectionPolicy(macAddress: String, policy: Int): Result<IntArray> {
        // If the UserService isn't ready yet but Shizuku is running and we have
        // permission, binding is in progress — wait for it rather than failing.
        if (userService == null && isShizukuRunning() && hasPermission()) {
            try {
                withTimeout(BIND_TIMEOUT_MS) {
                    _state.first { it is State.Ready }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Timed out or interrupted waiting for UserService to bind", e)
            }
        }

        val service = userService
            ?: return Result.failure(IllegalStateException("Shizuku UserService is not available"))
        return try {
            val results = service.setConnectionPolicy(macAddress, policy)
            // Each entry: 1 = success, 0 = call returned false, -1 = proxy unavailable.
            // If no profile reported success the OS policy was never changed — treat as failure.
            if (results.none { it == 1 }) {
                Result.failure(
                    IllegalStateException(
                        "setConnectionPolicy had no effect on any profile (results: ${results.toList()}). " +
                        "Check that Shizuku is running and has BLUETOOTH_PRIVILEGED."
                    )
                )
            } else {
                Result.success(results)
            }
        } catch (e: Exception) {
            Log.e(TAG, "setConnectionPolicy failed", e)
            Result.failure(e)
        }
    }

    /** Release all Shizuku listeners and unbind the service. Call from Application.onTerminate or similar. */
    fun cleanup() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        doUnbindUserService()
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    fun isShizukuInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun isShizukuRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Exception) {
        false
    }

    private fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }

    private fun buildUserServiceArgs() = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, BluetoothBouncerUserService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("user_service")
        .version(BuildConfig.VERSION_CODE)

    private fun bindUserService() {
        try {
            Shizuku.bindUserService(buildUserServiceArgs(), serviceConnection)
        } catch (e: Exception) {
            Log.e(TAG, "bindUserService failed", e)
        }
    }

    private fun doUnbindUserService() {
        if (userService != null) {
            try {
                Shizuku.unbindUserService(buildUserServiceArgs(), serviceConnection, true)
            } catch (e: Exception) {
                Log.w(TAG, "unbindUserService failed", e)
            }
            userService = null
        }
    }

    companion object {
        private const val TAG = "ShizukuHelper"
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val PERMISSION_REQUEST_CODE = 1001

        /** Milliseconds to wait for the UserService to bind on cold start before giving up. */
        private const val BIND_TIMEOUT_MS = 10_000L

        /** BluetoothProfile.CONNECTION_POLICY_FORBIDDEN */
        const val POLICY_FORBIDDEN = 0

        /** BluetoothProfile.CONNECTION_POLICY_ALLOWED */
        const val POLICY_ALLOWED = 100
    }
}
