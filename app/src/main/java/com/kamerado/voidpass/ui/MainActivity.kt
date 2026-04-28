package com.kamerado.voidpass.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import com.kamerado.voidpass.db.PasswordEntry
import com.kamerado.voidpass.ui.theme.VaultTheme

class MainActivity : FragmentActivity() {

    private val unlockViewModel:       UnlockViewModel       by viewModels()
    private val vaultListViewModel:    VaultListViewModel    by viewModels()
    private val biometricSetupViewModel: BiometricSetupViewModel by viewModels()
    private val settingsViewModel:     SettingsViewModel     by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )

        setContent {
            VaultTheme {
                VaultApp(
                    unlockViewModel        = unlockViewModel,
                    vaultListViewModel     = vaultListViewModel,
                    biometricSetupViewModel = biometricSetupViewModel,
                    settingsViewModel      = settingsViewModel,
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        unlockViewModel.lock()
    }
}

// ── Navigation state ──────────────────────────────────────────────────────────

sealed class Screen {
    object Unlock          : Screen()
    object BiometricSetup  : Screen()    // offered once after first setup
    object VaultList       : Screen()
    object Settings        : Screen()
    data class EntryDetail(val entry: PasswordEntry) : Screen()
}

// ── Root composable ───────────────────────────────────────────────────────────

@Composable
fun VaultApp(
    unlockViewModel:         UnlockViewModel,
    vaultListViewModel:      VaultListViewModel,
    biometricSetupViewModel: BiometricSetupViewModel,
    settingsViewModel:       SettingsViewModel,
) {
    val unlockState by unlockViewModel.uiState.collectAsState()

    // Navigation stack — simple list acting as a back stack.
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Unlock) }

    // When the vault locks, always snap back to unlock — no animation.
    LaunchedEffect(unlockState.mode) {
        if (unlockState.mode != UnlockMode.UNLOCKED && currentScreen != Screen.Unlock) {
            currentScreen = Screen.Unlock
        }
    }

    AnimatedContent(
        targetState   = currentScreen,
        transitionSpec = {
            when {
                // Unlock → anything: slide up
                initialState is Screen.Unlock ->
                    (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 10 })
                        .togetherWith(fadeOut(tween(200)))
                // Back to vault list: slide back down
                targetState is Screen.VaultList ->
                    (fadeIn(tween(200)))
                        .togetherWith(fadeOut(tween(150)))
                // Forward navigation: slide left
                else ->
                    (fadeIn(tween(250)) + slideInHorizontally(tween(250)) { it / 10 })
                        .togetherWith(fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { -it / 10 })
            }
        },
        label = "main_navigation",
    ) { screen ->
        when (screen) {

            is Screen.Unlock -> {
                UnlockScreen(
                    viewModel  = unlockViewModel,
                    onUnlocked = {
                        // After first-time setup, go to biometric setup prompt.
                        // After regular unlock, go straight to vault list.
                        val justCreated = biometricSetupViewModel.uiState.value.step ==
                                BiometricSetupStep.PROMPT &&
                                !com.kamerado.voidpass.crypto.KeystoreWrapper.hasWrappingKey()
                        // Simplest heuristic: if the vault list is empty (just created),
                        // offer biometric setup. Otherwise go straight in.
                        // We detect "just created" by checking if VaultListViewModel has
                        // no entries AND has never loaded — i.e. this is a fresh vault.
                        // A cleaner approach: UnlockViewModel exposes a `justCreated` flag.
                        if (unlockViewModel.justCreated) {
                            unlockViewModel.clearJustCreated()
                            biometricSetupViewModel.prepare(
                                unlockViewModel.vaultKey ?: return@UnlockScreen
                            )
                            currentScreen = Screen.BiometricSetup
                        } else {
                            currentScreen = Screen.VaultList
                        }
                    },
                )
            }

            is Screen.BiometricSetup -> {
                val vaultKey = unlockViewModel.vaultKey
                if (vaultKey != null) {
                    BiometricSetupScreen(
                        vaultKey  = vaultKey,
                        viewModel = biometricSetupViewModel,
                        onDone    = {
                            biometricSetupViewModel.reset()
                            currentScreen = Screen.VaultList
                        },
                    )
                } else {
                    // Vault locked while on this screen — go back to unlock.
                    currentScreen = Screen.Unlock
                }
            }

            is Screen.VaultList -> {
                VaultListScreen(
                    viewModel    = vaultListViewModel,
                    onLock       = { unlockViewModel.lock() },
                    onEntryClick = { entry -> currentScreen = Screen.EntryDetail(entry) },
                    onSettings   = { currentScreen = Screen.Settings },
                )
            }

            is Screen.Settings -> {
                SettingsScreen(
                    viewModel          = settingsViewModel,
                    onBack             = { currentScreen = Screen.VaultList },
                    onEnableBiometric  = {
                        val vaultKey = unlockViewModel.vaultKey ?: run {
                            currentScreen = Screen.VaultList
                            return@SettingsScreen
                        }
                        biometricSetupViewModel.prepare(vaultKey)
                        currentScreen = Screen.BiometricSetup
                    },
                )
            }

            is Screen.EntryDetail -> {
                EntryDetailScreen(
                    entry     = screen.entry,
                    onBack    = {
                        vaultListViewModel.refresh()
                        currentScreen = Screen.VaultList
                    },
                    onDeleted = {
                        vaultListViewModel.refresh()
                        currentScreen = Screen.VaultList
                    },
                )
            }
        }
    }
}