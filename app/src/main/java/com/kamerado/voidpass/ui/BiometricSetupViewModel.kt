package com.kamerado.voidpass.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kamerado.voidpass.crypto.KeystoreWrapper
import com.kamerado.voidpass.db.VaultStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.Cipher

data class BiometricSetupUiState(
    val step:         BiometricSetupStep = BiometricSetupStep.PROMPT,
    val errorMessage: String?            = null,
    val isLoading:    Boolean            = false,
)

enum class BiometricSetupStep {
    PROMPT,    // "Would you like to enable biometric unlock?"
    SCANNING,  // waiting for BiometricPrompt result
    SUCCESS,   // done
    SKIPPED,   // user said no
    ERROR,
}

class BiometricSetupViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = VaultStorage(application)

    private val _uiState = MutableStateFlow(BiometricSetupUiState())
    val uiState: StateFlow<BiometricSetupUiState> = _uiState.asStateFlow()

    /** The vault key must be in memory (vault unlocked) before calling this. */
    private var pendingVaultKey: ByteArray? = null

    fun prepare(vaultKey: ByteArray) {
        pendingVaultKey = vaultKey
    }

    fun onUserAgreed() {
        _uiState.update { it.copy(step = BiometricSetupStep.SCANNING) }
    }

    fun onUserSkipped() {
        _uiState.update { it.copy(step = BiometricSetupStep.SKIPPED) }
    }

    /**
     * Returns the Cipher that BiometricPrompt needs to initiate enrollment.
     * Creates the Keystore wrapping key if it doesn't exist yet.
     */
    fun getEncryptCipher(): Cipher? {
        return try {
            if (!KeystoreWrapper.hasWrappingKey()) {
                KeystoreWrapper.createWrappingKey()
            }
            KeystoreWrapper.encryptCipher()
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    step         = BiometricSetupStep.ERROR,
                    errorMessage = "Could not prepare biometric key: ${e.message}",
                )
            }
            null
        }
    }

    /** Called after BiometricPrompt returns the unlocked encryption cipher. */
    fun onBiometricSuccess(cipher: Cipher) {
        val vaultKey = pendingVaultKey ?: run {
            _uiState.update {
                it.copy(
                    step         = BiometricSetupStep.ERROR,
                    errorMessage = "Vault key unavailable. Please lock and unlock again.",
                )
            }
            return
        }

        viewModelScope.launch {
            try {
                // Wrap (encrypt) the vault key with the biometric-bound Keystore key.
                val wrapped = KeystoreWrapper.wrap(cipher, vaultKey)

                // Persist the wrapped key so future launches can use it.
                storage.saveWrappedKey(wrapped.iv, wrapped.ciphertext)

                _uiState.update { it.copy(step = BiometricSetupStep.SUCCESS, isLoading = false) }
            } catch (e: Exception) {
                KeystoreWrapper.deleteWrappingKey()
                _uiState.update {
                    it.copy(
                        step         = BiometricSetupStep.ERROR,
                        errorMessage = "Failed to save biometric key: ${e.message}",
                        isLoading    = false,
                    )
                }
            }
        }
    }

    fun onBiometricError(errorCode: Int, message: CharSequence) {
        if (errorCode == 10) {
            // User pressed cancel — treat as skip.
            _uiState.update { it.copy(step = BiometricSetupStep.SKIPPED) }
        } else {
            _uiState.update {
                it.copy(
                    step         = BiometricSetupStep.ERROR,
                    errorMessage = "Biometric error: $message",
                )
            }
        }
    }

    fun reset() {
        _uiState.update { BiometricSetupUiState() }
        pendingVaultKey = null
    }

    override fun onCleared() {
        super.onCleared()
        pendingVaultKey = null
    }
}