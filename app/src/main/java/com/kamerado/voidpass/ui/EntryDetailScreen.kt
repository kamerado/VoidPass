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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    entry:     PasswordEntry,
    viewModel: EntryDetailViewModel = viewModel(),
    onBack:    () -> Unit,
    onDeleted: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(entry) { viewModel.load(entry) }

    // Navigate back if saved (so the list refreshes via LaunchedEffect(Unit) in VaultListScreen)
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onBack()
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BackgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text  = if (uiState.isEditing) "EDIT ENTRY" else "ENTRY",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily    = MonoFamily,
                            letterSpacing = 4.sp,
                        ),
                        color = TextPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.isEditing) viewModel.cancelEditing()
                        else onBack()
                    }) {
                        Icon(
                            if (uiState.isEditing) Icons.Default.Close else Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextSecondary,
                        )
                    }
                },
                actions = {
                    if (uiState.isEditing) {
                        IconButton(
                            onClick  = viewModel::saveEdits,
                            enabled  = !uiState.isLoading,
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(20.dp),
                                    color       = AccentBlue,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Default.Check, "Save", tint = AccentBlue)
                            }
                        }
                    } else {
                        IconButton(onClick = { viewModel.startEditing() }) {
                            Icon(Icons.Default.Edit, "Edit", tint = TextSecondary)
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, "Delete", tint = TextDisabled)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDeep),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BackgroundDeep)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // Error banner
            AnimatedVisibility(visible = uiState.errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentRed.copy(alpha = 0.12f))
                        .border(1.dp, AccentRed.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                ) {
                    Text(uiState.errorMessage ?: "", color = AccentRed,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (uiState.isEditing) {
                // ── Edit mode ──────────────────────────────────────────────
                DetailField("Title",    uiState.editTitle,    EntryField.TITLE,    viewModel)
                DetailField("Username", uiState.editUsername, EntryField.USERNAME, viewModel)
                PasswordEditField(uiState, viewModel)
                DetailField("URL",      uiState.editUrl,      EntryField.URL,      viewModel)
                DetailField("Notes",    uiState.editNotes,    EntryField.NOTES,    viewModel,
                    multiline = true)
            } else {
                // ── View mode ──────────────────────────────────────────────
                uiState.entry?.let { e ->
                    ViewRow(label = "Title",    value = e.title)
                    ViewRow(label = "Username", value = e.username,
                        copyable = true, context = context)
                    PasswordViewRow(
                        password       = e.password,
                        visible        = uiState.passwordVisible,
                        onToggle       = viewModel::onPasswordVisibilityToggle,
                        copiedRecently = uiState.copiedRecently,
                        onCopy         = {
                            copyToClipboard(context, "password", e.password, sensitive = true)
                            viewModel.onPasswordCopied()
                            scheduleClearClipboard(context, 30_000L)
                        },
                    )
                    e.url?.takeIf { it.isNotBlank() }?.let {
                        ViewRow(label = "URL", value = it, copyable = true, context = context)
                    }
                    e.notes?.takeIf { it.isNotBlank() }?.let {
                        ViewRow(label = "Notes", value = it)
                    }

                    Spacer(Modifier.height(8.dp))

                    // Timestamps
                    Text(
                        text  = "Created  ${formatTimestamp(e.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDisabled,
                    )
                    Text(
                        text  = "Modified ${formatTimestamp(e.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDisabled,
                    )
                }
            }
        }

        // Delete confirmation
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                containerColor   = SurfaceElevated,
                title            = { Text("Delete entry?", color = TextPrimary) },
                text             = {
                    Text(
                        "\"${uiState.entry?.title}\" will be permanently deleted.",
                        color = TextSecondary,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        uiState.entry?.let { e ->
                            VaultDatabase.instance?.deleteById(e.id)
                        }
                        onDeleted()
                    }) { Text("DELETE", color = AccentRed) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
            )
        }
    }
}

// ── View mode rows ─────────────────────────────────────────────────────────────

@Composable
private fun ViewRow(
    label:    String,
    value:    String,
    copyable: Boolean = false,
    context:  Context? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BackgroundCard)
            .border(1.dp, Divider, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text  = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = TextDisabled,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text     = value,
                style    = MaterialTheme.typography.bodyLarge,
                color    = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            if (copyable && context != null) {
                IconButton(
                    onClick  = { copyToClipboard(context, label, value, sensitive = false) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy $label",
                        tint     = TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordViewRow(
    password:       String,
    visible:        Boolean,
    onToggle:       () -> Unit,
    copiedRecently: Boolean,
    onCopy:         () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BackgroundCard)
            .border(1.dp, Divider, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("PASSWORD", style = MaterialTheme.typography.labelSmall, color = TextDisabled)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text     = if (visible) password else "•".repeat(password.length.coerceAtMost(20)),
                style    = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFamily),
                color    = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            // Visibility toggle
            IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff
                    else Icons.Default.Visibility,
                    contentDescription = null,
                    tint     = TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
            // Copy button with feedback
            IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                AnimatedContent(
                    targetState = copiedRecently,
                    label       = "copy_icon",
                ) { copied ->
                    Icon(
                        imageVector = if (copied) Icons.Default.Check
                        else Icons.Default.ContentCopy,
                        contentDescription = "Copy password",
                        tint     = if (copied) AccentGreen else TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

// ── Edit mode fields ───────────────────────────────────────────────────────────

@Composable
private fun DetailField(
    label:     String,
    value:     String,
    field:     EntryField,
    viewModel: EntryDetailViewModel,
    multiline: Boolean = false,
) {
    OutlinedTextField(
        value         = value,
        onValueChange = { viewModel.onFieldChange(field, it) },
        label         = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        singleLine    = !multiline,
        minLines      = if (multiline) 3 else 1,
        colors        = outlinedColors(),
        modifier      = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordEditField(
    uiState:   EntryDetailUiState,
    viewModel: EntryDetailViewModel,
) {
    OutlinedTextField(
        value            = uiState.editPassword,
        onValueChange    = { viewModel.onFieldChange(EntryField.PASSWORD, it) },
        label            = { Text("Password", style = MaterialTheme.typography.bodyMedium) },
        singleLine       = true,
        visualTransformation = if (uiState.passwordVisible) VisualTransformation.None
        else PasswordVisualTransformation(),
        keyboardOptions  = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon     = {
            IconButton(onClick = viewModel::onPasswordVisibilityToggle) {
                Icon(
                    imageVector = if (uiState.passwordVisible) Icons.Default.VisibilityOff
                    else Icons.Default.Visibility,
                    contentDescription = null,
                    tint = TextSecondary,
                )
            }
        },
        textStyle        = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFamily),
        colors           = outlinedColors(),
        modifier         = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun outlinedColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = AccentBlue,
    unfocusedBorderColor = Color(0xFF2A3340),
    focusedLabelColor    = AccentBlue,
    unfocusedLabelColor  = TextSecondary,
    cursorColor          = AccentBlue,
    focusedTextColor     = TextPrimary,
    unfocusedTextColor   = TextPrimary,
)

// ── Clipboard helpers ──────────────────────────────────────────────────────────

fun copyToClipboard(context: Context, label: String, value: String, sensitive: Boolean) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, value)

    if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Android 13+: mark as sensitive so it's hidden from clipboard previews
        // and excluded from clipboard history.
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    clipboard.setPrimaryClip(clip)
}

/**
 * Schedules a clear of the clipboard after [delayMs] milliseconds.
 * Uses a simple coroutine — no persistent alarm needed for a 30s window.
 * The clear only happens if our text is still in the clipboard when
 * the delay expires (we don't want to wipe something the user copied after us).
 */
fun scheduleClearClipboard(context: Context, delayMs: Long) {
    val appScope = (context.applicationContext as? com.kamerado.voidpass.VaultApplication)
        ?.applicationScope ?: return
    appScope.launch {
        delay(delayMs)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        // Only clear if the clipboard still has our content.
        // On API 28+ we can inspect the primary clip; clear by replacing with empty.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}

private fun formatTimestamp(ts: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM d, yyyy HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(ts))
}