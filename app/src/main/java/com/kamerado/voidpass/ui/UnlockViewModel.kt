package com.kamerado.voidpass.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kamerado.voidpass.crypto.CryptoManager
import com.kamerado.voidpass.crypto.KeystoreWrapper
import com.kamerado.voidpass.db.VaultDatabase
import com.kamerado.voidpass.db.VaultStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.Cipher

// ── State ─────────────────────────────────────────────────────────────────────

data class UnlockUiState(
    val mode:               UnlockMode = UnlockMode.IDLE,
    val showPasswordField:  Boolean    = false,
    val passwordInput:      String     = "",
    val confirmInput:       String     = "",        // setup mode only
    val passwordVisible:    Boolean    = false,
    val errorMessage:       String?    = null,
    val isLoading:          Boolean    = false,
    val biometricAvailable: Boolean    = false,
    val biometricEnabled:   Boolean    = false,
)

enum class UnlockMode {
    IDLE,
    SETUP,         // first-time setup — create master password
    BIOMETRIC,     // returning user — biometric prompt active
    PASSWORD,      // returning user — entering master password
    UNLOCKED,      // success
    ERROR,
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class UnlockViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = VaultStorage(application)

    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    private var _vaultKey: ByteArray? = null
    val vaultKey: ByteArray? get() = _vaultKey

    // ── Initialisation ────────────────────────────────────────────────────────

    fun init(biometricStatus: Int) {
        val hasBiometric    = (biometricStatus == 0)
        val hasWrappingKey  = KeystoreWrapper.hasWrappingKey() &&
                storage.wrappedKeyExists()
        val vaultExists     = storage.saltExists()

        _uiState.update {
            it.copy(
                biometricAvailable = hasBiometric,
                biometricEnabled   = hasWrappingKey,
                mode = when {
                    !vaultExists                       -> UnlockMode.SETUP
                    hasBiometric && hasWrappingKey     -> UnlockMode.BIOMETRIC
                    else                               -> UnlockMode.PASSWORD
                },
                showPasswordField = vaultExists && !(hasBiometric && hasWrappingKey),
            )
        }
    }

    // ── Setup (first-time) ────────────────────────────────────────────────────

    fun onConfirmChanged(value: String) {
        _uiState.update { it.copy(confirmInput = value, errorMessage = null) }
    }

    fun onSetupSubmit() {
        val password = _uiState.value.passwordInput
        val confirm  = _uiState.value.confirmInput

        when {
            password.length < 8 -> {
                _uiState.update { it.copy(errorMessage = "Password must be at least 8 characters") }
                return
            }
            password != confirm -> {
                _uiState.update { it.copy(errorMessage = "Passwords don't match") }
                return
            }
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val salt = storage.generateAndSaveSalt()
                val derivedKey = CryptoManager.deriveKeyFromPassword(
                    password.toCharArray(),
                    salt,
                )
                // First open with this key encrypts the new database with it.
                VaultDatabase.open(getApplication(), derivedKey)

                _vaultKey = derivedKey
                _uiState.update {
                    it.copy(
                        isLoading     = false,
                        mode          = UnlockMode.UNLOCKED,
                        passwordInput = "",
                        confirmInput  = "",
                    )
                }
            } catch (e: Exception) {
                storage.deleteAll()
                _uiState.update {
                    it.copy(
                        isLoading    = false,
                        errorMessage = "Setup failed: ${e.message}",
                    )
                }
            }
        }
    }

    // ── Unlock with master password ───────────────────────────────────────────

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(passwordInput = value, errorMessage = null) }
    }

    fun onPasswordVisibilityToggle() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun onPasswordSubmit() {
        val password = _uiState.value.passwordInput
        if (password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Password cannot be empty") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val salt = storage.loadSalt()
                val derivedKey = CryptoManager.deriveKeyFromPassword(
                    password.toCharArray(),
                    salt,
                )
                // SQLCipher will throw if the key is wrong (page checksum fails).
                val db = VaultDatabase.open(getApplication(), derivedKey)
                db.getAll()  // force actual decryption — open() is lazy

                _vaultKey = derivedKey
                _uiState.update {
                    it.copy(
                        isLoading     = false,
                        mode          = UnlockMode.UNLOCKED,
                        passwordInput = "",
                    )
                }
            } catch (e: Exception) {
                VaultDatabase.close()
                _uiState.update {
                    it.copy(
                        isLoading    = false,
                        errorMessage = "Incorrect password",
                    )
                }
            }
        }
    }

    // ── Biometric unlock ──────────────────────────────────────────────────────

    fun getDecryptCipher(): Cipher? {
        val (iv, _) = storage.loadWrappedKey() ?: return null
        return try {
            KeystoreWrapper.decryptCipher(iv)
        } catch (e: Exception) {
            KeystoreWrapper.deleteWrappingKey()
            storage.deleteWrappedKey()
            _uiState.update {
                it.copy(
                    biometricEnabled  = false,
                    showPasswordField = true,
                    mode              = UnlockMode.PASSWORD,
                    errorMessage      = "Biometric was reset. Use master password.",
                )
            }
            null
        }
    }

    fun onBiometricSuccess(cipher: Cipher) {
        val (_, ct) = storage.loadWrappedKey() ?: run { onBiometricFailed(); return }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val derivedKey = KeystoreWrapper.unwrap(cipher, ct)
                val db = VaultDatabase.open(getApplication(), derivedKey)
                db.getAll()
                _vaultKey = derivedKey
                _uiState.update { it.copy(mode = UnlockMode.UNLOCKED, errorMessage = null) }
            } catch (e: Exception) {
                VaultDatabase.close()
                _uiState.update {
                    it.copy(
                        mode              = UnlockMode.PASSWORD,
                        showPasswordField = true,
                        errorMessage      = "Biometric unlock failed. Use master password.",
                    )
                }
            }
        }
    }

    fun onBiometricError(errorCode: Int, message: CharSequence) {
        _uiState.update {
            it.copy(
                mode              = UnlockMode.PASSWORD,
                showPasswordField = true,
                errorMessage      = if (errorCode == 10) null
                else "Biometric error: $message",
            )
        }
    }

    fun onBiometricFailed() {
        // System handles retry UI.
    }

    // ── Misc ──────────────────────────────────────────────────────────────────

    fun switchToPassword() {
        _uiState.update {
            it.copy(
                mode              = UnlockMode.PASSWORD,
                showPasswordField = true,
                errorMessage      = null,
            )
        }
    }

    fun lock() {
        _vaultKey?.fill(0)
        _vaultKey = null
        VaultDatabase.close()

        val hasWrapped = KeystoreWrapper.hasWrappingKey() && storage.wrappedKeyExists()
        _uiState.update {
            UnlockUiState(
                biometricAvailable = it.biometricAvailable,
                biometricEnabled   = hasWrapped,
                mode               = if (it.biometricAvailable && hasWrapped)
                    UnlockMode.BIOMETRIC
                else
                    UnlockMode.PASSWORD,
                showPasswordField  = !(it.biometricAvailable && hasWrapped),
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        _vaultKey?.fill(0)
        VaultDatabase.close()
    }
}