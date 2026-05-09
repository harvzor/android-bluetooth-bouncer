package net.harveywilliams.bluetoothbouncer.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Semantic color constants for Bluetooth Bouncer.
 *
 * These are domain-specific colors that sit outside the Material3 ColorScheme role system.
 * They represent fixed semantic meanings (Shizuku readiness, device connection states)
 * rather than theming roles, so they are defined here rather than as ColorScheme overrides.
 */
object AppColors {
    /** Green indicator: Shizuku is running and permission is granted. */
    val ShizukuReady = Color(0xFF4CAF50)

    /** Light blue indicator: device has an active profile-level connection. */
    val DeviceConnected = Color(0xFF87CEEB)

    /** Orange indicator: device is detected (ACL present) or was recently detected. */
    val DeviceDetected = Color(0xFFE8A06C)
}
