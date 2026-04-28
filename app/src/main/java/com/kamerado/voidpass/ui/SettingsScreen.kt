package com.kamerado.voidpass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kamerado.voidpass.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel:             SettingsViewModel = viewModel(),
    onBack:                () -> Unit,
    onEnableBiometric:     () -> Unit,   // navigates to BiometricSetupScreen
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        containerColor = BackgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "SETTINGS",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily    = MonoFamily,
                            letterSpacing = androidx.compose.ui.unit.TextUnit(4f,
                                androidx.compose.ui.unit.TextUnitType.Sp),
                        ),
                        color = TextPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextSecondary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDeep,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BackgroundDeep)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {

            SettingsSectionLabel("SECURITY")

            Spacer(Modifier.height(8.dp))

            // Biometric toggle row
            SettingsRow(
                icon        = Icons.Default.Fingerprint,
                title       = "Biometric Unlock",
                subtitle    = if (uiState.biometricEnabled)
                    "Tap to disable fingerprint unlock"
                else
                    "Tap to enable fingerprint unlock",
                tint        = if (uiState.biometricEnabled) AccentGreen else TextSecondary,
                trailing    = {
                    Switch(
                        checked         = uiState.biometricEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) onEnableBiometric()
                            else viewModel.onDisableBiometricRequested()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor       = AccentGreen,
                            checkedTrackColor       = AccentGreen.copy(alpha = 0.3f),
                            uncheckedThumbColor     = TextDisabled,
                            uncheckedTrackColor     = BackgroundInput,
                        ),
                    )
                },
                onClick = {
                    if (uiState.biometricEnabled) viewModel.onDisableBiometricRequested()
                    else onEnableBiometric()
                },
            )

            // Disable confirmation dialog
            if (uiState.showDisableConfirm) {
                AlertDialog(
                    onDismissRequest = { viewModel.onDisableBiometricCancelled() },
                    containerColor   = SurfaceElevated,
                    title            = { Text("Disable biometric unlock?", color = TextPrimary) },
                    text             = {
                        Text(
                            "You will need to use your master password to unlock the vault.",
                            color = TextSecondary,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.onDisableBiometricConfirmed() }) {
                            Text("DISABLE", color = AccentRed)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.onDisableBiometricCancelled() }) {
                            Text("Cancel", color = TextSecondary)
                        }
                    },
                )
            }
        }
    }
}

// ── Reusable settings components ──────────────────────────────────────────────

@Composable
fun SettingsSectionLabel(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.labelSmall,
        color    = TextDisabled,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
    )
}

@Composable
fun SettingsRow(
    icon:     ImageVector,
    title:    String,
    subtitle: String,
    tint:     Color = TextSecondary,
    onClick:  () -> Unit,
    trailing: @Composable () -> Unit = {
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextDisabled)
    },
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BackgroundCard)
            .border(1.dp, Divider, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = tint,
            modifier           = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
            )
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}