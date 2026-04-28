package com.kamerado.voidpass.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kamerado.voidpass.db.PasswordEntry
import com.kamerado.voidpass.db.VaultDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VaultListUiState(
    val entries:      List<PasswordEntry> = emptyList(),
    val searchQuery:  String              = "",
    val isLoading:    Boolean             = false,
    val errorMessage: String?             = null,
)

val VaultListUiState.filteredEntries: List<PasswordEntry>
    get() = if (searchQuery.isBlank()) entries
    else entries.filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
                it.username.contains(searchQuery, ignoreCase = true) ||
                it.url?.contains(searchQuery, ignoreCase = true) == true
    }

class VaultListViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(VaultListUiState())
    val uiState: StateFlow<VaultListUiState> = _uiState.asStateFlow()

    /**
     * Loads entries from the unlocked vault. Should be called whenever
     * the screen comes into view — e.g. from a LaunchedEffect.
     * Safe to call repeatedly; quietly does nothing if vault is locked.
     */
    fun refresh() {
        val db = VaultDatabase.instance ?: return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entries = db.getAll()
                _uiState.update { it.copy(entries = entries, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading    = false,
                        errorMessage = "Failed to load entries: ${e.message}",
                    )
                }
            }
        }
    }

    fun onSearchChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onSearchCleared() {
        _uiState.update { it.copy(searchQuery = "") }
    }

    fun addEntry(
        title: String,
        username: String,
        password: String,
        url: String? = null,
        notes: String? = null,
    ) {
        val db = VaultDatabase.instance ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entry = PasswordEntry(
                    title    = title,
                    username = username,
                    password = password,
                    url      = url,
                    notes    = notes,
                    packageName = null,
                )
                db.insert(entry)
                refresh()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to save: ${e.message}")
                }
            }
        }
    }

    fun deleteEntry(id: String) {
        val db = VaultDatabase.instance ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.deleteById(id)
                _uiState.update { state ->
                    state.copy(entries = state.entries.filter { it.id != id })
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to delete: ${e.message}")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}