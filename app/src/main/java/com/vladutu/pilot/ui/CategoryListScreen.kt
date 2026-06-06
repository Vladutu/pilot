package com.vladutu.pilot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import com.vladutu.pilot.destination.DestinationPipeline
import com.vladutu.pilot.diagnostics.DiagnosticLog
import com.vladutu.pilot.net.NtfyPublisher
import com.vladutu.pilot.radio.RadioCatalog
import com.vladutu.pilot.share.MapsNavUrlBuilder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryListScreen(
    form: Form,
    publisher: NtfyPublisher,
    store: CatalogStore,
    pipeline: DestinationPipeline,
    publishStatus: PublishStatusHolder,
    onBack: () -> Unit,
    onOpenRadioSearch: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val busy = remember { mutableStateMapOf<String, Boolean>() }
    val entries by store.entries.collectAsState(initial = emptyList())
    val gridState = rememberLazyGridState()
    var menuFor by remember { mutableStateOf<CatalogEntry?>(null) }
    var renameFor by remember { mutableStateOf<CatalogEntry?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()
    var refreshing by remember { mutableStateOf(false) }

    val title = when (form) {
        Form.PLAYLIST -> "Playlists"
        Form.SONG -> "Songs"
        Form.DESTINATION -> "Places"
        Form.RADIO -> "Radio"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
            if (form == Form.RADIO) {
                FloatingActionButton(onClick = onOpenRadioSearch) {
                    Icon(Icons.Default.Search, contentDescription = "Find stations")
                }
            } else {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        },
    ) { padding ->
        val visible = entries.filter { it.form == form }
        if (visible.isEmpty()) {
            EmptyCategoryState(form = form, modifier = Modifier.fillMaxSize().padding(padding))
        } else {
            PullToRefreshBox(
                isRefreshing = refreshing,
                state = pullState,
                onRefresh = { refreshing = false }, // radio/destinations have no metadata refresh
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                LazyVerticalGrid(
                    state = gridState,
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
                                form = entry.form,
                                title = entry.title,
                                imagePath = entry.imagePath,
                                busy = busy[key] == true,
                                onClick = {
                                    busy[key] = true
                                    DiagnosticLog.i("Tap", "tap ${entry.form}:${entry.id} '${entry.title}'")
                                    scope.launch {
                                        try {
                                            when (entry.form) {
                                                Form.DESTINATION -> publisher.publishWaze(entry.id, title = entry.title)
                                                Form.RADIO -> publisher.publishRadio(
                                                    streamUrl = entry.url ?: error("radio entry has no url"),
                                                    title = entry.title,
                                                    imageUrl = entry.imageUrl,
                                                )
                                                Form.PLAYLIST, Form.SONG -> publisher.publishYtMusic(
                                                    entry.form, entry.id, title = entry.title, imageUrl = entry.imageUrl,
                                                )
                                            }
                                            publishStatus.markOk()
                                            store.touch(entry.form, entry.id)
                                            gridState.animateScrollToItem(0)
                                            snackbar.showSnackbar("Sent: ${entry.title}")
                                        } catch (e: Exception) {
                                            DiagnosticLog.e("Tap", "tap publish failed (${e.javaClass.simpleName})", e)
                                            publishStatus.markFailed()
                                            snackbar.showSnackbar("Send failed — check connection")
                                        } finally {
                                            busy[key] = false
                                        }
                                    }
                                },
                                onLongClick = { menuFor = entry },
                            )
                            DropdownMenu(expanded = menuFor == entry, onDismissRequest = { menuFor = null }) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = { renameFor = entry; menuFor = null },
                                )
                                if (entry.form == Form.DESTINATION && entry.googleMapsUrl != null) {
                                    DropdownMenuItem(
                                        text = { Text("Send as Maps") },
                                        onClick = {
                                            val target = entry
                                            val navUrl = MapsNavUrlBuilder.fromWazeUrl(target.id)
                                            menuFor = null
                                            if (navUrl == null) {
                                                scope.launch { snackbar.showSnackbar("No coordinates for this destination") }
                                                return@DropdownMenuItem
                                            }
                                            busy[key] = true
                                            scope.launch {
                                                try {
                                                    publisher.publishMaps(navUrl, title = target.title)
                                                    publishStatus.markOk()
                                                    store.touch(target.form, target.id)
                                                    gridState.animateScrollToItem(0)
                                                    snackbar.showSnackbar("Sent as Maps: ${target.title}")
                                                } catch (e: Exception) {
                                                    publishStatus.markFailed()
                                                    snackbar.showSnackbar("Send failed — check connection")
                                                } finally {
                                                    busy[key] = false
                                                }
                                            }
                                        },
                                    )
                                }
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

        if (showAddDialog) {
            AddUrlDialog(
                activeForm = form,
                onSubmit = { urlText, manualTitle ->
                    showAddDialog = false
                    scope.launch {
                        try {
                            if (form == Form.RADIO) {
                                // Manual stream-URL fallback (spec §5.6): accept any http(s) URL.
                                if (!urlText.startsWith("http://") && !urlText.startsWith("https://")) {
                                    snackbar.showSnackbar("Enter an http(s) stream URL")
                                    return@launch
                                }
                                RadioCatalog.addManual(store, streamUrl = urlText, title = manualTitle)
                                snackbar.showSnackbar("Added: ${manualTitle ?: urlText}")
                            } else {
                                val result = pipeline.ingest(urlText = urlText, manualTitle = manualTitle, subject = null)
                                snackbar.showSnackbar(result.toastText)
                            }
                        } catch (t: Throwable) {
                            DiagnosticLog.e("AddUrl", "manual add threw ${t.javaClass.simpleName}", t)
                            snackbar.showSnackbar("Add failed — check log")
                        }
                    }
                },
                onDismiss = { showAddDialog = false },
            )
        }
    }
}

@Composable
private fun EmptyCategoryState(form: Form, modifier: Modifier = Modifier) {
    val text = when (form) {
        Form.PLAYLIST -> "Share a playlist from YT Music or tap + to paste a URL"
        Form.SONG -> "Share a song from YT Music or tap + to paste a URL"
        Form.DESTINATION -> "Share a Google Maps link or tap + to add a destination"
        Form.RADIO -> "Tap search to find Romanian stations, or add a stream URL"
    }
    Box(modifier = modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
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
