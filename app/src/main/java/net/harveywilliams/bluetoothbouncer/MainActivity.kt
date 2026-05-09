package net.harveywilliams.bluetoothbouncer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import net.harveywilliams.bluetoothbouncer.ui.theme.BluetoothBouncerTheme

/**
 * Single Activity entry point. Hosts the Compose navigation graph.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BluetoothBouncerTheme {
                AppNavigation()
            }
        }
    }
}
