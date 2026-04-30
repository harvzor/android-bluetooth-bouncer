package net.harveywilliams.bluetoothbouncer.ui.devices

import android.Manifest
import android.content.IntentSender
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.harveywilliams.bluetoothbouncer.shizuku.ShizukuHelper
import net.harveywilliams.bluetoothbouncer.viewmodel.DeviceListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    uiState: DeviceListViewModel.UiState,
    onToggleBlock: (DeviceListViewModel.DeviceUiModel) -> Unit,
    onToggleWatch: (DeviceListViewModel.DeviceUiModel) -> Unit,
    watchAssociationIntent: IntentSender?,
    onWatchAssociationResult: (Int) -> Unit,
    onClearWatchError: () -> Unit,
    onBluetoothPermissionResult: (Boolean) -> Unit,
    onNavigateToSetup: () -> Unit,
    onClearToggleError: () -> Unit,
    onRefresh: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showPermissionRationale by remember { mutableStateOf(false) }

    // ── Permission launcher ───────────────────────────────────────────────────
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onBluetoothPermissionResult(granted)
        if (!granted) showPermissionRationale = true
    }

    // ── CDM association launcher (Watch toggle, API 33+) ──────────────────────
    val watchAssociationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        onWatchAssociationResult(result.resultCode)
    }

    // Launch the CDM association dialog whenever a new IntentSender arrives.
    LaunchedEffect(watchAssociationIntent) {
        watchAssociationIntent?.let { intentSender ->
            watchAssociationLauncher.launch(
                IntentSenderRequest.Builder(intentSender).build()
            )
        }
    }

    // Request BT permission on first load if not granted
    LaunchedEffect(uiState.btPermissionGranted) {
        if (!uiState.btPermissionGranted) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    // Show block/unblock toggle errors as a snackbar
    LaunchedEffect(uiState.toggleError) {
        uiState.toggleError?.let {
            snackbarHostState.showSnackbar(it)
            onClearToggleError()
        }
    }

    // Show Watch operation errors / cancellations as a snackbar
    LaunchedEffect(uiState.watchError) {
        uiState.watchError?.let {
            snackbarHostState.showSnackbar(it)
            onClearWatchError()
        }
    }

    if (showPermissionRationale) {
        BluetoothPermissionRationaleDialog(
            onDismiss = { showPermissionRationale = false },
            onRetry = {
                showPermissionRationale = false
                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Bluetooth Bouncer") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Shizuku status bar ───────────────────────────────────────────
            ShizukuStatusBar(
                state = uiState.shizukuState,
                onSetupClick = onNavigateToSetup
            )

            HorizontalDivider()

            // ── Main content ─────────────────────────────────────────────────
            when {
                uiState.isLoading -> {
                    LoadingContent()
                }
                !uiState.btPermissionGranted -> {
                    // Permission not granted — shown after rationale dialog
                    PermissionDeniedContent(
                        onRetry = { permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) }
                    )
                }
                !uiState.bluetoothEnabled -> {
                    BluetoothDisabledContent()
                }
                uiState.devices.isEmpty() -> {
                    NoPairedDevicesContent()
                }
                else -> {
                    DeviceList(
                        devices = uiState.devices,
                        shizukuReady = uiState.shizukuState is ShizukuHelper.State.Ready,
                        watchLoadingAddress = uiState.watchLoadingAddress,
                        onToggleBlock = onToggleBlock,
                        onToggleWatch = onToggleWatch,
                    )
                }
            }
        }
    }
}

// ── Shizuku status bar ────────────────────────────────────────────────────────

@Composable
private fun ShizukuStatusBar(
    state: ShizukuHelper.State,
    onSetupClick: () -> Unit,
) {
    val (color, text) = when (state) {
        is ShizukuHelper.State.Ready ->
            Color(0xFF4CAF50) to "Shizuku: Ready"
        is ShizukuHelper.State.PermissionDenied ->
            MaterialTheme.colorScheme.error to "Shizuku: Permission denied"
        is ShizukuHelper.State.NotRunning ->
            MaterialTheme.colorScheme.tertiary to "Shizuku: Not running"
        is ShizukuHelper.State.NotInstalled ->
            MaterialTheme.colorScheme.error to "Shizuku: Not installed"
    }

    Surface(
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (state is ShizukuHelper.State.Ready)
                        Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (state !is ShizukuHelper.State.Ready) {
                TextButton(onClick = onSetupClick) {
                    Text("Setup", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// ── Device list ───────────────────────────────────────────────────────────────

@Composable
private fun DeviceList(
    devices: List<DeviceListViewModel.DeviceUiModel>,
    shizukuReady: Boolean,
    watchLoadingAddress: String?,
    onToggleBlock: (DeviceListViewModel.DeviceUiModel) -> Unit,
    onToggleWatch: (DeviceListViewModel.DeviceUiModel) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(devices, key = { it.address }) { device ->
            DeviceRow(
                device = device,
                shizukuReady = shizukuReady,
                isWatchLoading = watchLoadingAddress == device.address,
                onToggle = { onToggleBlock(device) },
                onToggleWatch = { onToggleWatch(device) },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun DeviceRow(
    device: DeviceListViewModel.DeviceUiModel,
    shizukuReady: Boolean,
    isWatchLoading: Boolean,
    onToggle: () -> Unit,
    onToggleWatch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Bluetooth icon with connected indicator
            Box {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = if (device.isConnected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
                if (device.isConnected) {
                    Badge(
                        modifier = Modifier.align(Alignment.TopEnd),
                        containerColor = Color(0xFF4CAF50)
                    ) { }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (device.isConnected) {
                    Text(
                        text = "Connected",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50)
                    )
                } else if (device.isDetected) {
                    Text(
                        text = "Detected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Toggles column (right side)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Watch toggle — visible only for blocked devices on API 33+
            if (device.isBlocked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Switch(
                        checked = device.isWatched,
                        onCheckedChange = { onToggleWatch() },
                        // Disabled while an association request is in-flight for this device
                        enabled = shizukuReady && !isWatchLoading,
                    )
                    Text(
                        text = "Watch",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (device.isWatched)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Block/allow toggle — disabled when Shizuku is not ready
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(
                    checked = device.isBlocked,
                    onCheckedChange = { onToggle() },
                    enabled = shizukuReady,
                )
                Text(
                    text = if (device.isBlocked) "Blocked" else "Allowed",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (device.isBlocked)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Empty states ──────────────────────────────────────────────────────────────

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun BluetoothDisabledContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.BluetoothDisabled,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Bluetooth is disabled",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Please enable Bluetooth to manage your paired devices.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NoPairedDevicesContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Bluetooth,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No paired devices",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Pair a Bluetooth device in Android Settings to manage it here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermissionDeniedContent(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Bluetooth permission required",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Bluetooth Bouncer needs the BLUETOOTH_CONNECT permission to list and manage your paired devices.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onRetry) { Text("Grant Permission") }
    }
}

@Composable
private fun BluetoothPermissionRationaleDialog(
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) },
        title = { Text("Bluetooth Permission Needed") },
        text = {
            Text(
                "Bluetooth Bouncer requires the BLUETOOTH_CONNECT permission to read your paired " +
                    "device list. Without it, the app cannot function. Please grant the permission."
            )
        },
        confirmButton = {
            TextButton(onClick = onRetry) { Text("Grant") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
