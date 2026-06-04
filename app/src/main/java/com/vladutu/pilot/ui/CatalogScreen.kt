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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.platform.LocalContext
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
import com.vladutu.pilot.diagnostics.DiagnosticsActivity
import com.vladutu.pilot.meta.MetadataFetcher
import com.vladutu.pilot.net.NtfyPublisher
import com.vladutu.pilot.share.ClassifiedShare
import kotlinx.coroutines.launch

private val TABS = listOf(
    Form.PLAYLIST to "Playlists",
    Form.SONG to "Songs",
    Form.DESTINATION to "Destinations",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    publisher: NtfyPublisher,
    store: CatalogStore,
    metadataFetcher: MetadataFetcher,
    pipeline: DestinationPipeline,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val busy = remember { mutableStateMapOf<String, Boolean>() }
    val entries by store.entries.collectAsState(initial = emptyList())

    var selectedTab by remember { mutableStateOf(0) }
    var menuFor by remember { mutableStateOf<CatalogEntry?>(null) }
    var renameFor by remember { mutableStateOf<CatalogEntry?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()
    var refreshing by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    val currentForm = TABS[selectedTab].first
    val context = LocalContext.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Pilot") },
                actions = {
                    IconButton(onClick = {
                        context.startActivity(Intent(context, DiagnosticsActivity::class.java))
                    }) {
                        Icon(Icons.Default.Info, contentDescription = "Diagnostics")
                    }
                },
            )
        },
        floatingActionButton = {
            val cd = when (currentForm) {
                Form.PLAYLIST -> "Add playlist"
                Form.SONG -> "Add song"
                Form.DESTINATION -> "Add destination"
            }
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = cd)
            }
        },
    ) { padding ->
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

            val visible = entries.filter { it.form == currentForm }

            if (visible.isEmpty()) {
                EmptyState(form = currentForm)
            } else {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    state = pullState,
                    onRefresh = {
                        refreshing = true
                        scope.launch {
                            try {
                                visible.filter { it.imagePath == null }.forEach { e ->
                                    val share: ClassifiedShare.YtMusic? = when (e.form) {
                                        Form.PLAYLIST -> ClassifiedShare.Playlist(e.id, e.title)
                                        Form.SONG -> ClassifiedShare.Song(e.id, e.title)
                                        Form.DESTINATION -> null  // no metadata refresh for destinations
                                    }
                                    if (share != null) metadataFetcher.refresh(share, store)
                                }
                            } finally {
                                refreshing = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
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
                                    title = entry.title,
                                    imagePath = entry.imagePath,
                                    busy = busy[key] == true,
                                    onClick = {
                                        busy[key] = true
                                        DiagnosticLog.i("Tap", "tap ${entry.form}:${entry.id} '${entry.title}'")
                                        scope.launch {
                                            try {
                                                if (entry.form == Form.DESTINATION) {
                                                    publisher.publishWaze(entry.id, title = entry.title)
                                                } else {
                                                    publisher.publishYtMusic(entry.form, entry.id, title = entry.title, imageUrl = entry.imageUrl)
                                                }
                                                DiagnosticLog.i("Tap", "tap publish ok ${entry.form}:${entry.id}")
                                                // Promote most-recently-used item to the top, then
                                                // scroll the grid so the user lands back on it.
                                                store.touch(entry.form, entry.id)
                                                gridState.animateScrollToItem(0)
                                                snackbar.showSnackbar("Sent: ${entry.title}")
                                            } catch (e: Exception) {
                                                DiagnosticLog.e("Tap", "tap publish failed (${e.javaClass.simpleName})", e)
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
                                    if (entry.form != Form.DESTINATION) {
                                        DropdownMenuItem(
                                            text = { Text("Rename") },
                                            onClick = {
                                                renameFor = entry
                                                menuFor = null
                                            },
                                        )
                                    }
                                    if (entry.form == Form.DESTINATION && entry.googleMapsUrl != null) {
                                        DropdownMenuItem(
                                            text = { Text("Send as Maps") },
                                            onClick = {
                                                val target = entry
                                                val mapsUrl = target.googleMapsUrl
                                                menuFor = null
                                                if (mapsUrl == null) return@DropdownMenuItem
                                                busy[key] = true
                                                DiagnosticLog.i("Tap", "send-as-maps ${target.form}:${target.id} '${target.title}'")
                                                scope.launch {
                                                    try {
                                                        publisher.publishMaps(mapsUrl, title = target.title)
                                                        DiagnosticLog.i("Tap", "send-as-maps publish ok ${target.form}:${target.id}")
                                                        store.touch(target.form, target.id)
                                                        gridState.animateScrollToItem(0)
                                                        snackbar.showSnackbar("Sent as Maps: ${target.title}")
                                                    } catch (e: Exception) {
                                                        DiagnosticLog.e("Tap", "send-as-maps publish failed (${e.javaClass.simpleName})", e)
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
                activeForm = currentForm,
                onSubmit = { urlText, title ->
                    showAddDialog = false
                    DiagnosticLog.i("AddUrl", "manual add urlText=$urlText title=$title")
                    scope.launch {
                        try {
                            val result = pipeline.ingest(urlText = urlText, manualTitle = title, subject = null)
                            DiagnosticLog.i("AddUrl", "manual add result=${result::class.simpleName}")
                            snackbar.showSnackbar(result.toastText)
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
private fun EmptyState(form: Form) {
    val text = when (form) {
        Form.PLAYLIST -> "Share a playlist from YT Music or tap + to paste a URL"
        Form.SONG -> "Share a song from YT Music or tap + to paste a URL"
        Form.DESTINATION -> "Share a Google Maps link or tap + to add a destination"
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
