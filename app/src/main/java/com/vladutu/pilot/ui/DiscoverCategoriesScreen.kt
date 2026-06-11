package com.vladutu.pilot.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vladutu.pilot.diagnostics.DiagnosticLog
import com.vladutu.pilot.discover.DiscoverCategoryStore
import com.vladutu.pilot.net.NtfyPublisher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Authoring screen for Discover keyword categories. Adding publishes immediately;
 * tapping an existing category re-sends it (the live-only delivery escape hatch
 * for when the car was off). Long-press → delete (local list only — the headunit
 * keeps its own copy until deleted there).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DiscoverCategoriesScreen(
    publisher: NtfyPublisher,
    store: DiscoverCategoryStore,
    publishStatus: PublishStatusHolder,
    onBack: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val busy = remember { mutableStateMapOf<String, Boolean>() }
    val categories by store.categories().collectAsState(initial = emptyList())
    var menuFor by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun send(keyword: String, alsoSave: Boolean) {
        busy[keyword] = true
        scope.launch {
            try {
                if (alsoSave) store.add(keyword)
                publisher.publishCategory(keyword)
                publishStatus.markOk()
                snackbar.showSnackbar("Sent: $keyword")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DiagnosticLog.e("Discover", "category publish failed (${e.javaClass.simpleName})", e)
                publishStatus.markFailed()
                snackbar.showSnackbar("Send failed — check connection")
            } finally {
                busy[keyword] = false
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Discover") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                actions = { StatusPill(statusFlow = publishStatus.state) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add category")
            }
        },
    ) { padding ->
        if (categories.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Tap + to add a music category (e.g. Workout, Petrecere, 80s rock).\nIt syncs to the car while Copilot is online.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                items(items = categories, key = { it }) { keyword ->
                    Box {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { if (busy[keyword] != true) send(keyword, alsoSave = false) },
                                    onLongClick = { menuFor = keyword },
                                ),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = keyword,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                if (busy[keyword] == true) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Re-send $keyword",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        DropdownMenu(expanded = menuFor == keyword, onDismissRequest = { menuFor = null }) {
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    scope.launch { store.delete(keyword) }
                                    menuFor = null
                                },
                            )
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AddCategoryDialog(
                onSubmit = { keyword ->
                    showAddDialog = false
                    send(keyword, alsoSave = true)
                },
                onDismiss = { showAddDialog = false },
            )
        }
    }
}

@Composable
private fun AddCategoryDialog(
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add category") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Keyword (e.g. Workout)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(text.trim()) },
                enabled = text.isNotBlank(),
            ) { Text("Add & send") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
