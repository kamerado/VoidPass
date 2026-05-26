package com.kamerado.voidpass.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kamerado.voidpass.db.PasswordEntry
import com.kamerado.voidpass.db.VaultDatabase
import com.kamerado.voidpass.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class EntryDetailUiState(
    val entry:           PasswordEntry? = null,
    val isEditing:       Boolean        = false,
    val isLoading:       Boolean        = false,
    val isSaved:         Boolean        = false,
    val errorMessage:    String?        = null,
    // Editable field values
    val editTitle:       String         = "",
    val editUsername:    String         = "",
    val editEmail:       String         = "",
    val editPassword:    String         = "",
    val editUrl:         String         = "",
    val editNotes:       String         = "",
    // UI state
    val passwordVisible: Boolean        = false,
    val copiedRecently:  Boolean        = false,  // drives "Copied!" feedback
)

class EntryDetailViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(EntryDetailUiState())
    val uiState: StateFlow<EntryDetailUiState> = _uiState.asStateFlow()

    fun load(entry: PasswordEntry) {
        _uiState.update {
            it.copy(
                entry        = entry,
                editTitle    = entry.title,
                editUsername = entry.username,
                editEmail    = entry.email,
                editPassword = entry.password,
                editUrl      = entry.url ?: "",
                editNotes    = entry.notes ?: "",
            )
        }
    }

    fun startEditing() {
        _uiState.update { it.copy(isEditing = true) }
    }

    fun cancelEditing() {
        val entry = _uiState.value.entry ?: return
        _uiState.update {
            it.copy(
                isEditing    = false,
                editTitle    = entry.title,
                editUsername = entry.username,
                editEmail    = entry.email,
                editPassword = entry.password,
                editUrl      = entry.url ?: "",
                editNotes    = entry.notes ?: "",
                errorMessage = null,
            )
        }
    }

    fun onFieldChange(field: EntryField, value: String) {
        _uiState.update {
            when (field) {
                EntryField.TITLE    -> it.copy(editTitle    = value, errorMessage = null)
                EntryField.USERNAME -> it.copy(editUsername = value, errorMessage = null)
                EntryField.EMAIL    -> it.copy(editEmail = value, errorMessage = null)
                EntryField.PASSWORD -> it.copy(editPassword = value, errorMessage = null)
                EntryField.URL      -> it.copy(editUrl      = value, errorMessage = null)
                EntryField.NOTES    -> it.copy(editNotes    = value, errorMessage = null)
            }
        }
    }

    fun onPasswordVisibilityToggle() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun saveEdits() {
        val state = _uiState.value
        val original = state.entry ?: return

        if (state.editTitle.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Title cannot be empty") }
            return
        }
        if (state.editPassword.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Password cannot be empty") }
            return
        }

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                val db = VaultDatabase.instance
                    ?: throw IllegalStateException("Vault is locked")

                val updated = original.copy(
                    title       = state.editTitle.trim(),
                    username    = state.editUsername.trim(),
                    email       = state.editEmail.trim(),
                    password    = state.editPassword,
                    url         = state.editUrl.trim().ifBlank { null },
                    notes       = state.editNotes.trim().ifBlank { null },
                    updatedAt   = System.currentTimeMillis(),
                )
                db.update(updated)

                _uiState.update {
                    it.copy(
                        entry     = updated,
                        isEditing = false,
                        isLoading = false,
                        isSaved   = true,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading    = false,
                        errorMessage = "Save failed: ${e.message}",
                    )
                }
            }
        }
    }

    /** Signals the clipboard was just populated — clears feedback after 2s. */
    fun onPasswordCopied() {
        _uiState.update { it.copy(copiedRecently = true) }
        viewModelScope.launch {
            delay(2000)
            _uiState.update { it.copy(copiedRecently = false) }
        }
    }
}

enum class EntryField { TITLE, USERNAME, EMAIL, PASSWORD, URL, NOTES }