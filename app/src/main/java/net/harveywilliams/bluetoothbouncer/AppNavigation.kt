package net.harveywilliams.bluetoothbouncer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import net.harveywilliams.bluetoothbouncer.ui.devices.DeviceListScreen
import net.harveywilliams.bluetoothbouncer.ui.setup.ShizukuSetupScreen
import net.harveywilliams.bluetoothbouncer.viewmodel.DeviceListViewModel

/** Navigation route constants */
object Routes {
    const val DEVICE_LIST = "device_list"
    const val SHIZUKU_SETUP = "shizuku_setup"
}

/**
 * Root navigation composable.
 * Starts on the device list. Shizuku setup is navigated to from the status bar.
 */
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current
    val app = context.applicationContext as BluetoothBouncerApp

    val viewModel: DeviceListViewModel = viewModel(
        factory = DeviceListViewModel.factory(
            shizukuHelper = app.shizukuHelper,
            blockedDeviceDao = app.database.blockedDeviceDao()
        )
    )
    val uiState by viewModel.uiState.collectAsState()
    val watchAssociationIntent by viewModel.watchAssociationIntent.collectAsState()

    NavHost(navController = navController, startDestination = Routes.DEVICE_LIST) {

        composable(Routes.DEVICE_LIST) {
            DeviceListScreen(
                uiState = uiState,
                onToggleBlock = viewModel::toggleBlock,
                onToggleWatch = viewModel::toggleWatch,
                watchAssociationIntent = watchAssociationIntent,
                onWatchAssociationResult = viewModel::onWatchAssociationResult,
                onClearWatchError = viewModel::clearWatchError,
                onClearWatchSuccess = viewModel::clearWatchSuccess,
                onBluetoothPermissionResult = viewModel::onBluetoothPermissionResult,
                onNavigateToSetup = { navController.navigate(Routes.SHIZUKU_SETUP) },
                onClearToggleError = viewModel::clearToggleError,
                onRefresh = viewModel::refreshDevices,
            )
        }

        composable(Routes.SHIZUKU_SETUP) {
            ShizukuSetupScreen(
                shizukuState = uiState.shizukuState,
                onRequestPermission = { app.shizukuHelper.requestPermission() },
                onNavigateToDeviceList = {
                    navController.popBackStack(Routes.DEVICE_LIST, inclusive = false)
                },
            )
        }
    }
}
