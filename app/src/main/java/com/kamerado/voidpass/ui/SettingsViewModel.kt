package com.kamerado.voidpass.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.kamerado.voidpass.crypto.KeystoreWrapper
import com.kamerado.voidpass.db.VaultStorage

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class SettingsUiState(
    val biometricEnabled: Boolean = false,
    val showDisableConfirm: Boolean = false,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = VaultStorage(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun refresh() {
        _uiState.update {
            it.copy(
                biometricEnabled = KeystoreWrapper.hasWrappingKey() && storage.wrappedKeyExists()
            )
        }
    }

    fun onDisableBiometricRequested() {
        _uiState.update { it.copy(showDisableConfirm = true) }
    }

    fun onDisableBiometricConfirmed() {
        KeystoreWrapper.deleteWrappingKey()
        storage.deleteWrappedKey()
        _uiState.update { it.copy(biometricEnabled = false, showDisableConfirm = false) }
    }

    fun onDisableBiometricCancelled() {
        _uiState.update { it.copy(showDisableConfirm = false) }
    }
}