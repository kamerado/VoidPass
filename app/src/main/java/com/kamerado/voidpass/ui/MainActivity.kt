package com.kamerado.voidpass.ui
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import com.kamerado.voidpass.ui.theme.VaultTheme

/**
 * The single activity. We use FragmentActivity (not AppCompatActivity) because
 * BiometricPrompt requires it. If you later add AppCompat features, swap to
 * AppCompatActivity — it extends FragmentActivity so nothing breaks.
 *
 * Navigation is intentionally hand-rolled (no NavController dependency)
 * since there are only two screens for now. Add Jetpack Navigation later
 * if the app grows.
 */
class MainActivity : FragmentActivity() {

    // ViewModels survive configuration changes (rotation etc.)
    private val unlockViewModel: UnlockViewModel by viewModels()
    private val vaultListViewModel: VaultListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Security flags ─────────────────────────────────────────────────
        // FLAG_SECURE: prevents screenshots, screen recording, and recents
        // thumbnail from capturing vault contents.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )

        setContent {
            VaultTheme {
                VaultApp(
                    unlockViewModel   = unlockViewModel,
                    vaultListViewModel = vaultListViewModel,
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Wipe vault key from RAM whenever the app goes to background.
        // The user will need to authenticate again on return.
        unlockViewModel.lock()
    }
}

// ── App-level composable ───────────────────────────────────────────────────────

/**
 * Manages which screen is visible. Two states:
 *   - LOCKED   → UnlockScreen
 *   - UNLOCKED → VaultListScreen
 *
 * We use a simple Boolean flag rather than a NavController because the
 * transition is a single toggle and we want the lock() call in onPause()
 * to also flip the screen back.
 */
@Composable
fun VaultApp(
    unlockViewModel:    UnlockViewModel,
    vaultListViewModel: VaultListViewModel,
) {
    val unlockState by unlockViewModel.uiState.collectAsState()
    val isUnlocked = unlockState.mode == UnlockMode.UNLOCKED

    AnimatedContent(
        targetState   = isUnlocked,
        transitionSpec = {
            if (targetState) {
                // Unlocking: new screen fades + slides up
                (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 10 })
                    .togetherWith(fadeOut(tween(200)))
            } else {
                // Locking: instant snap back to lock screen (no animation —
                // feels more secure, less like a normal "back" navigation)
                fadeIn(tween(100)).togetherWith(fadeOut(tween(100)))
            }
        },
        label = "vault_screen_transition",
    ) { unlocked ->
        if (unlocked) {
            VaultListScreen(
                viewModel    = vaultListViewModel,
                onLock       = { unlockViewModel.lock() },
                onEntryClick = { /* TODO: navigate to detail */ },
//                onAddEntry   = { /* TODO: navigate to add entry */ },
            )
        } else {
            UnlockScreen(
                viewModel  = unlockViewModel,
                onUnlocked = { /* state change drives the transition above */ },
            )
        }
    }
}