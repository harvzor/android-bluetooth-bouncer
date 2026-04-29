package net.harveywilliams.bluetoothbouncer.ui.setup

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.harveywilliams.bluetoothbouncer.shizuku.ShizukuHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShizukuSetupScreen(
    shizukuState: ShizukuHelper.State,
    onRequestPermission: () -> Unit,
    onNavigateToDeviceList: () -> Unit,
) {
    val context = LocalContext.current

    // 4.2 — Live status: when Shizuku becomes Ready, auto-navigate to device list
    LaunchedEffect(shizukuState) {
        if (shizukuState is ShizukuHelper.State.Ready) {
            onNavigateToDeviceList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Shizuku Setup") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Status card ──────────────────────────────────────────────────
            ShizukuStatusCard(state = shizukuState)

            // ── Action button ────────────────────────────────────────────────
            when (shizukuState) {
                is ShizukuHelper.State.NotInstalled -> {
                    Button(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Install Shizuku from Play Store")
                    }
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/RikkaApps/Shizuku/releases")
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Get Shizuku from GitHub")
                    }
                }

                is ShizukuHelper.State.PermissionDenied -> {
                    Button(
                        onClick = onRequestPermission,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant Shizuku Permission")
                    }
                }

                is ShizukuHelper.State.Ready -> {
                    Button(
                        onClick = onNavigateToDeviceList,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Continue to Device List")
                    }
                }

                else -> { /* NotRunning — show instructions only */ }
            }

            // ── Setup instructions ───────────────────────────────────────────
            if (shizukuState !is ShizukuHelper.State.Ready) {
                SetupInstructionsCard()
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ShizukuStatusCard(state: ShizukuHelper.State) {
    val (icon, color, title, description) = when (state) {
        is ShizukuHelper.State.NotInstalled -> StatusInfo(
            icon = Icons.Default.Warning,
            color = MaterialTheme.colorScheme.error,
            title = "Shizuku: Not Installed",
            description = "Shizuku is required to manage Bluetooth connection policies. Please install it using the links below."
        )
        is ShizukuHelper.State.NotRunning -> StatusInfo(
            icon = Icons.Default.Info,
            color = MaterialTheme.colorScheme.secondary,
            title = "Shizuku: Not Running",
            description = "Shizuku is installed but not started. Follow the setup instructions below to start it via ADB or Wireless Debugging."
        )
        is ShizukuHelper.State.PermissionDenied -> StatusInfo(
            icon = Icons.Default.Warning,
            color = MaterialTheme.colorScheme.tertiary,
            title = "Shizuku: Permission Required",
            description = "Bluetooth Bouncer needs permission to use Shizuku. Tap the button below to grant it."
        )
        is ShizukuHelper.State.Ready -> StatusInfo(
            icon = Icons.Default.CheckCircle,
            color = Color(0xFF4CAF50),
            title = "Shizuku: Ready",
            description = "Shizuku is running and permission is granted. All device blocking operations are available."
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SetupInstructionsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "How to start Shizuku",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))

            Text(
                text = "Option A — Wireless Debugging (Android 11+, no PC needed)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            InstructionStep("1", "Enable Developer Options: Settings → About Phone → tap Build Number 7×")
            InstructionStep("2", "Enable Wireless Debugging: Settings → Developer Options → Wireless Debugging")
            InstructionStep("3", "Open Shizuku and tap \"Start via Wireless Debugging\"")
            InstructionStep("4", "Follow the pairing prompt and tap Allow")

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Option B — ADB (requires a PC)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            InstructionStep("1", "Enable Developer Options and USB Debugging")
            InstructionStep("2", "Connect phone via USB")
            InstructionStep(
                number = "3",
                text = "Run in terminal:",
                code = "adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh"
            )
        }
    }
}

@Composable
private fun InstructionStep(number: String, text: String, code: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$number.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Column {
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
            if (code != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                )
            }
        }
    }
}

private data class StatusInfo(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val title: String,
    val description: String
)
