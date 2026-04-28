package com.kamerado.voidpass.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kamerado.voidpass.auth.BiometricAuth
import com.kamerado.voidpass.ui.theme.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.alpha

/**
 * Shown once after first-time vault creation.
 * Asks the user if they want to enable biometric unlock.
 * onDone is called regardless of whether they enable it or not.
 */
@Composable
fun BiometricSetupScreen(
    vaultKey:  ByteArray,
    viewModel: BiometricSetupViewModel = viewModel(),
    onDone:    () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context  = LocalContext.current
    val activity = context as FragmentActivity

    // Hand the vault key to the ViewModel.
    LaunchedEffect(Unit) { viewModel.prepare(vaultKey) }

    // When scanning is requested, fire the biometric prompt.
    LaunchedEffect(uiState.step) {
        if (uiState.step == BiometricSetupStep.SCANNING) {
            val cipher = viewModel.getEncryptCipher() ?: return@LaunchedEffect
            BiometricAuth.authenticate(
                activity  = activity,
                cipher    = cipher,
                title     = "Enable biometric unlock",
                subtitle  = "Scan your fingerprint to save your vault key",
                onSuccess = { viewModel.onBiometricSuccess(it) },
                onError   = { code, msg -> viewModel.onBiometricError(code, msg) },
            )
        }
    }

    // Navigate away once finished or skipped.
    LaunchedEffect(uiState.step) {
        if (uiState.step == BiometricSetupStep.SUCCESS ||
            uiState.step == BiometricSetupStep.SKIPPED) {
            onDone()
        }
    }

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(BackgroundDeep, Color(0xFF0D1117))
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        GridOverlay()

        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(AccentBlueDim)
                    .border(1.dp, AccentBlue.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint               = AccentBlue,
                    modifier           = Modifier.size(40.dp),
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text      = "Enable Biometric Unlock?",
                style     = MaterialTheme.typography.headlineMedium,
                color     = TextPrimary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text      = "Instead of typing your master password each time, " +
                        "you can unlock the vault with your fingerprint.\n\n" +
                        "Your master password remains the only way to unlock " +
                        "if biometrics are unavailable.",
                style     = MaterialTheme.typography.bodyMedium,
                color     = TextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            // Security note
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentBlueDim.copy(alpha = 0.3f))
                    .border(1.dp, AccentBlue.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(12.dp),
            ) {
                Text(
                    text      = "If you add or remove fingerprints later, " +
                            "biometric unlock will be disabled automatically " +
                            "and you'll need to re-enable it here.",
                    style     = MaterialTheme.typography.labelSmall,
                    color     = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(36.dp))

            // Error
            AnimatedVisibility(visible = uiState.errorMessage != null) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentRed.copy(alpha = 0.12f))
                            .border(1.dp, AccentRed.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint     = AccentRed,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text  = uiState.errorMessage ?: "",
                                color = AccentRed,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // Buttons
            Button(
                onClick  = { viewModel.onUserAgreed() },
                enabled  = uiState.step != BiometricSetupStep.SCANNING,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(8.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = AccentBlue,
                    contentColor   = Color(0xFF001433),
                ),
            ) {
                if (uiState.step == BiometricSetupStep.SCANNING) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        color       = TextPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "SCANNING…",
                        style         = MaterialTheme.typography.labelSmall,
                        letterSpacing = 2.sp,
                    )
                } else {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "ENABLE BIOMETRIC",
                        style         = MaterialTheme.typography.labelSmall,
                        letterSpacing = 2.sp,
                        fontSize      = 13.sp,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(
                onClick  = { viewModel.onUserSkipped() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Skip for now",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun GridOverlay() {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .alpha(0.03f)
    ) {
        val dotSize = 1.dp.toPx()
        val spacing = 28.dp.toPx()
        var x = 0f
        while (x < size.width) {
            var y = 0f
            while (y < size.height) {
                drawCircle(
                    color  = Color.White,
                    radius = dotSize,
                    center = androidx.compose.ui.geometry.Offset(x, y),
                )
                y += spacing
            }
            x += spacing
        }
    }
}