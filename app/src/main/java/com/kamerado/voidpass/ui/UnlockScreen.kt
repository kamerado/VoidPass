package com.kamerado.voidpass.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kamerado.voidpass.auth.BiometricAuth
import com.kamerado.voidpass.ui.theme.*
import androidx.biometric.BiometricManager

@Composable
fun UnlockScreen(
    viewModel:  UnlockViewModel = viewModel(),
    onUnlocked: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        val status = BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        viewModel.init(status)
    }

    LaunchedEffect(uiState.mode) {
        if (uiState.mode == UnlockMode.UNLOCKED) onUnlocked()
    }

    LaunchedEffect(uiState.mode) {
        if (uiState.mode == UnlockMode.BIOMETRIC) {
            val cipher = viewModel.getDecryptCipher() ?: return@LaunchedEffect
            BiometricAuth.authenticate(
                activity  = activity,
                cipher    = cipher,
                onSuccess = { viewModel.onBiometricSuccess(it) },
                onError   = { code, msg -> viewModel.onBiometricError(code, msg) },
                onFail    = { viewModel.onBiometricFailed() },
            )
        }
    }

    LaunchedEffect(uiState.showPasswordField, uiState.mode) {
        if (uiState.showPasswordField || uiState.mode == UnlockMode.SETUP) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
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
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val iconAlpha by animateFloatAsState(
                targetValue   = if (uiState.isLoading) 0.4f else 1f,
                animationSpec = tween(300),
                label         = "iconAlpha",
            )
            Icon(
                imageVector        = Icons.Default.Lock,
                contentDescription = null,
                tint               = AccentBlue.copy(alpha = iconAlpha),
                modifier           = Modifier.size(40.dp),
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text  = "VAULT",
                style = MaterialTheme.typography.displayLarge,
                color = TextPrimary,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text          = if (uiState.mode == UnlockMode.SETUP)
                    "FIRST-TIME SETUP"
                else
                    "SECURE CREDENTIAL STORE",
                style         = MaterialTheme.typography.labelSmall,
                color         = TextSecondary,
                letterSpacing = 3.sp,
            )

            Spacer(Modifier.height(48.dp))

            // ── Error banner ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = uiState.errorMessage != null,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically(),
            ) {
                uiState.errorMessage?.let { msg ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentRed.copy(alpha = 0.12f))
                            .border(1.dp, AccentRed.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                    ) {
                        Text(
                            text      = msg,
                            color     = AccentRed,
                            style     = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // ── SETUP MODE ────────────────────────────────────────────────
            if (uiState.mode == UnlockMode.SETUP) {
                SetupForm(
                    uiState         = uiState,
                    focusRequester  = focusRequester,
                    onPasswordChange = viewModel::onPasswordChanged,
                    onConfirmChange  = viewModel::onConfirmChanged,
                    onVisibilityToggle = viewModel::onPasswordVisibilityToggle,
                    onSubmit         = viewModel::onSetupSubmit,
                )
            } else {
                // ── UNLOCK MODE ───────────────────────────────────────────
                UnlockForm(
                    uiState         = uiState,
                    focusRequester  = focusRequester,
                    onPasswordChange = viewModel::onPasswordChanged,
                    onVisibilityToggle = viewModel::onPasswordVisibilityToggle,
                    onSubmit         = viewModel::onPasswordSubmit,
                )

                // Biometric button
                AnimatedVisibility(
                    visible = uiState.biometricAvailable && uiState.biometricEnabled,
                    enter   = fadeIn(),
                    exit    = fadeOut(),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (uiState.showPasswordField) {
                            Spacer(Modifier.height(24.dp))
                            HorizontalDividerWithLabel("OR")
                            Spacer(Modifier.height(24.dp))
                        }

                        OutlinedButton(
                            onClick  = {
                                val cipher = viewModel.getDecryptCipher() ?: return@OutlinedButton
                                BiometricAuth.authenticate(
                                    activity  = activity,
                                    cipher    = cipher,
                                    onSuccess = { viewModel.onBiometricSuccess(it) },
                                    onError   = { code, msg -> viewModel.onBiometricError(code, msg) },
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape    = RoundedCornerShape(8.dp),
                            colors   = ButtonDefaults.outlinedButtonColors(
                                contentColor = AccentBlue,
                            ),
                        ) {
                            Icon(
                                imageVector        = Icons.Default.Fingerprint,
                                contentDescription = null,
                                modifier           = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "USE BIOMETRIC",
                                style         = MaterialTheme.typography.labelSmall,
                                letterSpacing = 2.sp,
                                fontSize      = 13.sp,
                            )
                        }

                        if (!uiState.showPasswordField) {
                            Spacer(Modifier.height(16.dp))
                            TextButton(onClick = viewModel::switchToPassword) {
                                Text(
                                    "Use master password instead",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(64.dp))

            // ── Bottom status label ───────────────────────────────────────
            Text(
                text  = when (uiState.mode) {
                    UnlockMode.BIOMETRIC -> "Waiting for biometric…"
                    UnlockMode.PASSWORD  -> "Enter your master password"
                    UnlockMode.SETUP     -> "Create a master password (8+ chars)"
                    UnlockMode.IDLE      -> ""
                    UnlockMode.UNLOCKED  -> "Unlocked"
                    UnlockMode.ERROR     -> "Authentication failed"
                },
                style = MaterialTheme.typography.labelSmall,
                color = when (uiState.mode) {
                    UnlockMode.UNLOCKED -> AccentGreen
                    UnlockMode.ERROR    -> AccentRed
                    else                -> TextDisabled
                },
                letterSpacing = 1.5.sp,
            )
        }
    }
}

// ── Setup form ─────────────────────────────────────────────────────────────────

@Composable
private fun SetupForm(
    uiState:            UnlockUiState,
    focusRequester:     FocusRequester,
    onPasswordChange:   (String) -> Unit,
    onConfirmChange:    (String) -> Unit,
    onVisibilityToggle: () -> Unit,
    onSubmit:           () -> Unit,
) {
    Column {
        VaultPasswordField(
            value             = uiState.passwordInput,
            onValueChange     = onPasswordChange,
            label             = "New Master Password",
            visible           = uiState.passwordVisible,
            onVisibilityToggle = onVisibilityToggle,
            enabled           = !uiState.isLoading,
            imeAction         = ImeAction.Next,
            modifier          = Modifier.focusRequester(focusRequester),
        )
        Spacer(Modifier.height(12.dp))
        VaultPasswordField(
            value             = uiState.confirmInput,
            onValueChange     = onConfirmChange,
            label             = "Confirm Password",
            visible           = uiState.passwordVisible,
            onVisibilityToggle = onVisibilityToggle,
            enabled           = !uiState.isLoading,
            imeAction         = ImeAction.Done,
            onImeDone         = onSubmit,
        )
        Spacer(Modifier.height(20.dp))

        // Warning banner — first time setup is critical
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(AccentBlueDim.copy(alpha = 0.3f))
                .border(1.dp, AccentBlue.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(12.dp),
        ) {
            Text(
                text  = "This password cannot be recovered. " +
                        "If you forget it, your vault is unrecoverable.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick  = onSubmit,
            enabled  = !uiState.isLoading &&
                    uiState.passwordInput.isNotBlank() &&
                    uiState.confirmInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(8.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor         = AccentBlue,
                contentColor           = Color(0xFF001433),
                disabledContainerColor = AccentBlueDim,
                disabledContentColor   = TextDisabled,
            ),
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(20.dp),
                    color       = TextPrimary,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text("CREATING VAULT…",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.sp)
            } else {
                Text("CREATE VAULT",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.sp,
                    fontSize = 13.sp)
            }
        }
    }
}

// ── Unlock form ────────────────────────────────────────────────────────────────

@Composable
private fun UnlockForm(
    uiState:            UnlockUiState,
    focusRequester:     FocusRequester,
    onPasswordChange:   (String) -> Unit,
    onVisibilityToggle: () -> Unit,
    onSubmit:           () -> Unit,
) {
    AnimatedVisibility(
        visible = uiState.showPasswordField,
        enter   = fadeIn(tween(200)) + expandVertically(tween(200)),
        exit    = fadeOut(tween(150)) + shrinkVertically(tween(150)),
    ) {
        Column {
            VaultPasswordField(
                value             = uiState.passwordInput,
                onValueChange     = onPasswordChange,
                label             = "Master Password",
                visible           = uiState.passwordVisible,
                onVisibilityToggle = onVisibilityToggle,
                enabled           = !uiState.isLoading,
                imeAction         = ImeAction.Done,
                onImeDone         = onSubmit,
                modifier          = Modifier.focusRequester(focusRequester),
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick  = onSubmit,
                enabled  = !uiState.isLoading && uiState.passwordInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(8.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = AccentBlue,
                    contentColor           = Color(0xFF001433),
                    disabledContainerColor = AccentBlueDim,
                    disabledContentColor   = TextDisabled,
                ),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        color       = TextPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("DERIVING KEY…",
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 2.sp)
                } else {
                    Text("UNLOCK",
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 2.sp,
                        fontSize = 13.sp)
                }
            }
        }
    }
}

// ── Reusable password field ────────────────────────────────────────────────────

@Composable
private fun VaultPasswordField(
    value:              String,
    onValueChange:      (String) -> Unit,
    label:              String,
    visible:            Boolean,
    onVisibilityToggle: () -> Unit,
    enabled:            Boolean,
    imeAction:          ImeAction,
    onImeDone:          () -> Unit = {},
    modifier:           Modifier = Modifier,
) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onValueChange,
        label           = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        singleLine      = true,
        enabled         = enabled,
        visualTransformation = if (visible) VisualTransformation.None
        else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction    = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onImeDone() }),
        trailingIcon    = {
            IconButton(onClick = onVisibilityToggle) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff
                    else Icons.Default.Visibility,
                    contentDescription = null,
                    tint = TextSecondary,
                )
            }
        },
        colors          = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = AccentBlue,
            unfocusedBorderColor = Color(0xFF2A3340),
            focusedLabelColor    = AccentBlue,
            unfocusedLabelColor  = TextSecondary,
            cursorColor          = AccentBlue,
            focusedTextColor     = TextPrimary,
            unfocusedTextColor   = TextPrimary,
            disabledBorderColor  = Color(0xFF1E2730),
        ),
        textStyle       = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFamily),
        modifier        = modifier.fillMaxWidth(),
    )
}

// ── Helpers ────────────────────────────────────────────────────────────────────

@Composable
private fun HorizontalDividerWithLabel(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = Divider, thickness = 1.dp)
        Text(
            text     = label,
            modifier = Modifier.padding(horizontal = 12.dp),
            color    = TextDisabled,
            style    = MaterialTheme.typography.labelSmall,
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = Divider, thickness = 1.dp)
    }
}

@Composable
private fun GridOverlay() {
    androidx.compose.foundation.Canvas(
        modifier = Modifier.fillMaxSize().alpha(0.03f)
    ) {
        val dotSize = 1.dp.toPx()
        val spacing = 28.dp.toPx()
        var x = 0f
        while (x < size.width) {
            var y = 0f
            while (y < size.height) {
                drawCircle(
                    color = Color.White,
                    radius = dotSize,
                    center = androidx.compose.ui.geometry.Offset(x, y)
                )
                y += spacing
            }
            x += spacing
        }
    }
}