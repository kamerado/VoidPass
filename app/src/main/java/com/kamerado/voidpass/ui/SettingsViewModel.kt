package com.kamerado.voidpass.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.kamerado.voidpass.crypto.KeystoreWrapper
import com.kamerado.voidpass.db.VaultStorage
import androidx.core.content.edit

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class SettingsUiState(
    val biometricEnabled: Boolean = false,
    val showDisableConfirm: Boolean = false,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {



    private val prefs = getApplication<Application>()
        .getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)

    private val _defaultUsername = MutableStateFlow(getDefaultUsername())
    val defaultUsername: StateFlow<String> = _defaultUsername.asStateFlow()

    private val _defaultEmail = MutableStateFlow(getDefaultEmail())
    public val defaultEmail: StateFlow<String> = _defaultEmail.asStateFlow();

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

    fun setDefaultUsername(username: String) {
        prefs.edit {
            putString("default_username", username)
        }
        _defaultUsername.update { username }
    }

    fun setDefaultEmail(email: String) {
        prefs.edit {
            putString("default_email", email)
        }
        _defaultEmail.update { email }
    }

    fun getDefaultUsername(): String = prefs.getString("default_username", "") ?: ""

    fun getDefaultEmail(): String    = prefs.getString("default_email", "") ?: ""

}