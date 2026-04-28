package com.kamerado.voidpass.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kamerado.voidpass.db.PasswordEntry
import com.kamerado.voidpass.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListScreen(
    viewModel:    VaultListViewModel = viewModel(),
    onLock:       () -> Unit,
    onEntryClick: (PasswordEntry) -> Unit = {},
    onSettings:   () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val entries = uiState.filteredEntries
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        containerColor = BackgroundDeep,
        topBar = {
            VaultTopBar(
                onLock     = onLock,
                onSettings = onSettings,      // add this
                entryCount = uiState.entries.size,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = { showAddDialog = true },
                containerColor = AccentBlue,
                contentColor   = Color(0xFF001433),
                shape          = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add entry")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BackgroundDeep),
        ) {
            SearchBar(
                query     = uiState.searchQuery,
                onChanged = viewModel::onSearchChanged,
                onClear   = viewModel::onSearchCleared,
                modifier  = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            AnimatedVisibility(visible = uiState.searchQuery.isNotBlank()) {
                Text(
                    text     = "${entries.size} result${if (entries.size != 1) "s" else ""}",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = TextSecondary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = AccentBlue)
                }
            } else if (entries.isEmpty()) {
                EmptyState(hasSearch = uiState.searchQuery.isNotBlank())
            } else {
                LazyColumn(
                    contentPadding      = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        EntryCard(
                            entry    = entry,
                            onClick  = { onEntryClick(entry) },
                            onDelete = { viewModel.deleteEntry(entry.id) },
                            modifier = Modifier.animateItem(
                                fadeInSpec  = tween(200),
                                fadeOutSpec = tween(200),
                            ),
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            AddEntryDialog(
                onDismiss = { showAddDialog = false },
                onSave    = { title, username, password, url, notes ->
                    viewModel.addEntry(title, username, password, url, notes)
                    showAddDialog = false
                },
            )
        }

        // Error snack
        uiState.errorMessage?.let { msg ->
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(3000)
                viewModel.clearError()
            }
            Box(
                modifier         = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 100.dp, start = 16.dp, end = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentRed.copy(alpha = 0.95f))
                        .padding(12.dp),
                ) {
                    Text(msg, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ── TopBar ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultTopBar(
    onLock:     () -> Unit,
    onSettings: () -> Unit,
    entryCount: Int,
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text  = "VAULT",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily    = MonoFamily,
                        fontWeight    = FontWeight.W600,
                        letterSpacing = 4.sp,
                    ),
                    color = TextPrimary,
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AccentBlueDim)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text  = "$entryCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentBlue,
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onSettings) {     // ADD gear icon
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextSecondary,
                )
            }
            IconButton(onClick = onLock) {
                Icon(Icons.Default.Lock, contentDescription = "Lock vault", tint = TextSecondary)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor    = BackgroundDeep,
            titleContentColor = TextPrimary,
        ),
    )
}

// ── Search bar ─────────────────────────────────────────────────────────────────

@Composable
private fun SearchBar(
    query:     String,
    onChanged: (String) -> Unit,
    onClear:   () -> Unit,
    modifier:  Modifier = Modifier,
) {
    OutlinedTextField(
        value         = query,
        onValueChange = onChanged,
        placeholder   = {
            Text("Search entries…", style = MaterialTheme.typography.bodyMedium, color = TextDisabled)
        },
        leadingIcon   = {
            Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
        },
        trailingIcon  = {
            AnimatedVisibility(visible = query.isNotBlank()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                }
            }
        },
        singleLine = true,
        shape      = RoundedCornerShape(10.dp),
        colors     = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = AccentBlue,
            unfocusedBorderColor = Color(0xFF2A3340),
            focusedTextColor     = TextPrimary,
            unfocusedTextColor   = TextPrimary,
            cursorColor          = AccentBlue,
            focusedContainerColor   = BackgroundInput,
            unfocusedContainerColor = BackgroundInput,
        ),
        modifier   = modifier.fillMaxWidth(),
    )
}

// ── Entry card ──────────────────────────────────────────────────────────────────

@Composable
private fun EntryCard(
    entry:    PasswordEntry,
    onClick:  () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(BackgroundCard)
                .border(1.dp, Divider, RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(AccentBlueDim, Color(0xFF0D2137))
                        )
                    )
                    .border(1.dp, AccentBlue.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = entry.title.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = AccentBlue,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = MonoFamily,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = entry.title,
                    style    = MaterialTheme.typography.bodyLarge,
                    color    = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text     = entry.username,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                entry.url?.takeIf { it.isNotBlank() }?.let { url ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text     = url,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = AccentBlue.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            IconButton(
                onClick  = { showDeleteConfirm = true },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector        = Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint               = TextDisabled,
                    modifier           = Modifier.size(18.dp),
                )
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                containerColor   = SurfaceElevated,
                title            = { Text("Delete entry?", color = TextPrimary) },
                text             = {
                    Text(
                        "\"${entry.title}\" will be permanently deleted.",
                        color = TextSecondary,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        onDelete()
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

// ── Add entry dialog ────────────────────────────────────────────────────────────

@Composable
private fun AddEntryDialog(
    onDismiss: () -> Unit,
    onSave:    (title: String, username: String, password: String, url: String?, notes: String?) -> Unit,
) {
    var title    by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var url      by remember { mutableStateOf("") }
    var notes    by remember { mutableStateOf("") }
    var showPwd  by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SurfaceElevated,
        title            = { Text("New Entry", color = TextPrimary) },
        text = {
            Column {
                DialogField("Title *",       title,    { title = it })
                Spacer(Modifier.height(8.dp))
                DialogField("Username *",    username, { username = it })
                Spacer(Modifier.height(8.dp))
                DialogField(
                    label    = "Password *",
                    value    = password,
                    onChange = { password = it },
                    isPassword = true,
                    visible    = showPwd,
                    onToggleVisibility = { showPwd = !showPwd },
                )
                Spacer(Modifier.height(8.dp))
                DialogField("URL",  url,   { url = it })
                Spacer(Modifier.height(8.dp))
                DialogField("Notes", notes, { notes = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                onClick = {
                    onSave(
                        title.trim(),
                        username.trim(),
                        password,
                        url.trim().ifBlank { null },
                        notes.trim().ifBlank { null },
                    )
                },
            ) {
                Text("SAVE", color = AccentBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
    )
}

@Composable
private fun DialogField(
    label:              String,
    value:              String,
    onChange:           (String) -> Unit,
    isPassword:         Boolean = false,
    visible:            Boolean = true,
    onToggleVisibility: () -> Unit = {},
) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onChange,
        label           = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        singleLine      = !label.startsWith("Notes"),
        visualTransformation = if (isPassword && !visible) PasswordVisualTransformation()
        else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text
        ),
        trailingIcon    = if (isPassword) {{
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff
                    else Icons.Default.Visibility,
                    contentDescription = null,
                    tint = TextSecondary,
                )
            }
        }} else null,
        textStyle       = if (isPassword)
            MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFamily)
        else
            MaterialTheme.typography.bodyLarge,
        colors          = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = AccentBlue,
            unfocusedBorderColor = Color(0xFF2A3340),
            focusedLabelColor    = AccentBlue,
            unfocusedLabelColor  = TextSecondary,
            cursorColor          = AccentBlue,
            focusedTextColor     = TextPrimary,
            unfocusedTextColor   = TextPrimary,
        ),
        modifier        = Modifier.fillMaxWidth(),
    )
}

// ── Empty state ─────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(hasSearch: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector        = if (hasSearch) Icons.Default.SearchOff else Icons.Default.Lock,
                contentDescription = null,
                tint               = TextDisabled,
                modifier           = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text  = if (hasSearch) "No matching entries" else "Your vault is empty",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text  = if (hasSearch) "Try a different search term"
                else "Tap + to add your first entry",
                style = MaterialTheme.typography.bodyMedium,
                color = TextDisabled,
            )
        }
    }
}