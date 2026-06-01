package com.vladutu.pilot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import com.vladutu.pilot.catalog.CatalogEntry
import com.vladutu.pilot.catalog.CatalogStore
import com.vladutu.pilot.catalog.Form
import com.vladutu.pilot.meta.MetadataFetcher
import com.vladutu.pilot.net.NtfyPublisher
import com.vladutu.pilot.share.ClassifiedShare
import kotlinx.coroutines.launch

private val TABS = listOf(Form.PLAYLIST to "Playlists", Form.SONG to "Songs")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    publisher: NtfyPublisher,
    store: CatalogStore,
    metadataFetcher: MetadataFetcher,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val busy = remember { mutableStateMapOf<String, Boolean>() }
    val entries by store.entries.collectAsState(initial = emptyList())

    var selectedTab by remember { mutableStateOf(0) }
    var menuFor by remember { mutableStateOf<CatalogEntry?>(null) }
    var renameFor by remember { mutableStateOf<CatalogEntry?>(null) }
    val pullState = rememberPullToRefreshState()
    var refreshing by remember { mutableStateOf(false) }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                TABS.forEachIndexed { index, (_, title) ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            val form = TABS[selectedTab].first
            val visible = entries.filter { it.form == form }

            if (visible.isEmpty()) {
                EmptyState(form = form)
            } else {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    state = pullState,
                    onRefresh = {
                        refreshing = true
                        scope.launch {
                            try {
                                visible.filter { it.imagePath == null }.forEach { e ->
                                    val share: ClassifiedShare = when (e.form) {
                                        Form.PLAYLIST -> ClassifiedShare.Playlist(e.id, e.title)
                                        Form.SONG -> ClassifiedShare.Song(e.id, e.title)
                                    }
                                    metadataFetcher.refresh(share, store)
                                }
                            } finally {
                                refreshing = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items = visible, key = { "${it.form}-${it.id}" }) { entry ->
                            val key = "${entry.form}-${entry.id}"
                            Box {
                                Tile(
                                    title = entry.title,
                                    imagePath = entry.imagePath,
                                    busy = busy[key] == true,
                                    onClick = {
                                        busy[key] = true
                                        scope.launch {
                                            try {
                                                publisher.publishYtMusic(entry.form, entry.id)
                                                snackbar.showSnackbar("Sent: ${entry.title}")
                                            } catch (e: Exception) {
                                                snackbar.showSnackbar("Send failed — check connection")
                                            } finally {
                                                busy[key] = false
                                            }
                                        }
                                    },
                                    onLongClick = { menuFor = entry },
                                )
                                DropdownMenu(
                                    expanded = menuFor == entry,
                                    onDismissRequest = { menuFor = null },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        onClick = {
                                            renameFor = entry
                                            menuFor = null
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = {
                                            val target = entry
                                            scope.launch { store.delete(target.form, target.id) }
                                            menuFor = null
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        renameFor?.let { target ->
            RenameDialog(
                initialTitle = target.title,
                onDismiss = { renameFor = null },
                onConfirm = { newTitle ->
                    scope.launch { store.rename(target.form, target.id, newTitle) }
                    renameFor = null
                },
            )
        }
    }
}

@Composable
private fun EmptyState(form: Form) {
    val text = when (form) {
        Form.PLAYLIST -> "Share a playlist from YT Music to get started"
        Form.SONG -> "Share a song from YT Music to get started"
    }
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RenameDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
