package com.kamerado.voidpass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachEmail
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GppBad
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

            Spacer(Modifier.height(8.dp))

            SettingsSectionLabel("Defaults")

            Spacer(Modifier.height(8.dp))

            var showUsernameDialog by remember { mutableStateOf(false) }
            var draftUsername by remember { mutableStateOf("") }
            val defaultUsername by viewModel.defaultUsername.collectAsState()

            SettingsRow(
                icon     = Icons.Default.AccountCircle,
                title    = "Default Username",
                subtitle = defaultUsername.ifBlank { "Not set" },
                onClick  = {
                    draftUsername = defaultUsername  // pre-fill with current value
                    showUsernameDialog = true
                },
            )

            if (showUsernameDialog) {
                AlertDialog(
                    onDismissRequest = { showUsernameDialog = false },
                    containerColor   = SurfaceElevated,
                    title            = { Text("Default Username", color = TextPrimary) },
                    text = {
                        OutlinedTextField(
                            value         = draftUsername,
                            onValueChange = { draftUsername = it },
                            singleLine    = true,
                            label         = { Text("Username") },
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentBlue,
                                unfocusedBorderColor = Color(0xFF2A3340),
                                focusedTextColor   = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedLabelColor  = AccentBlue,
                                unfocusedLabelColor = TextSecondary,
                                cursorColor        = AccentBlue,
                            ),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.setDefaultUsername(draftUsername)
                            showUsernameDialog = false
                        }) {
                            Text("SAVE", color = AccentBlue)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUsernameDialog = false }) {
                            Text("Cancel", color = TextSecondary)
                        }
                    },
                )
            }

            Spacer(Modifier.height(8.dp))

            var showEmailDialog by remember { mutableStateOf(false) }
            var draftEmail by remember { mutableStateOf("") }
            val defaultEmail by viewModel.defaultEmail.collectAsState()

            SettingsRow(
                icon     = Icons.Default.AttachEmail,
                title    = "Default Email",
                subtitle = defaultEmail.ifBlank { "Not set" },
                onClick  = {
                    draftEmail = defaultEmail  // pre-fill with current value
                    showEmailDialog = true
                },
            )

            if (showEmailDialog) {
                AlertDialog(
                    onDismissRequest = { showEmailDialog = false },
                    containerColor   = SurfaceElevated,
                    title            = { Text("Default Email", color = TextPrimary) },
                    text = {
                        OutlinedTextField(
                            value         = draftEmail,
                            onValueChange = { draftEmail = it },
                            singleLine    = true,
                            label         = { Text("Email") },
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentBlue,
                                unfocusedBorderColor = Color(0xFF2A3340),
                                focusedTextColor   = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedLabelColor  = AccentBlue,
                                unfocusedLabelColor = TextSecondary,
                                cursorColor        = AccentBlue,
                            ),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.setDefaultEmail(draftEmail)
                            showEmailDialog = false
                        }) {
                            Text("SAVE", color = AccentBlue)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEmailDialog = false }) {
                            Text("Cancel", color = TextSecondary)
                        }
                    },
                )
            }

            Spacer(Modifier.height(8.dp))

            val passwordLength by viewModel.defaultPasswordLength.collectAsState()
            var showLengthDialog by remember { mutableStateOf(false) }
            var draftLength by remember { mutableStateOf(passwordLength.toFloat()) }

            SettingsRow(
                icon     = Icons.Default.GppBad,
                title    = "Default Password Length",
                subtitle = "$passwordLength characters",
                onClick  = {
                    draftLength = passwordLength.toFloat()
                    showLengthDialog = true
                },
            )

            if (showLengthDialog) {
                AlertDialog(
                    onDismissRequest = { showLengthDialog = false },
                    containerColor   = SurfaceElevated,
                    title            = { Text("Password Length", color = TextPrimary) },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Big number display
                            Text(
                                text  = draftLength.toInt().toString(),
                                style = MaterialTheme.typography.displayLarge,
                                color = AccentBlue,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text  = "characters",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                            Spacer(Modifier.height(24.dp))
                            Slider(
                                value         = draftLength,
                                onValueChange = { draftLength = it },
                                valueRange    = 12f..50f,
                                steps         = 37,
                                colors        = SliderDefaults.colors(
                                    thumbColor         = AccentBlue,
                                    activeTrackColor   = AccentBlue,
                                    inactiveTrackColor = Color(0xFF2A3340),
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("12", style = MaterialTheme.typography.labelSmall, color = TextDisabled)
                                Text("50", style = MaterialTheme.typography.labelSmall, color = TextDisabled)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.setDefaultPasswordLength(draftLength.toInt())
                            showLengthDialog = false
                        }) {
                            Text("SAVE", color = AccentBlue)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLengthDialog = false }) {
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