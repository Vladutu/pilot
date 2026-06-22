package com.vladutu.pilot.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vladutu.pilot.catalog.CatalogStore
import com.vladutu.pilot.catalog.Form
import com.vladutu.pilot.diagnostics.DiagnosticLog
import com.vladutu.pilot.meta.MetadataFetcher
import com.vladutu.pilot.radio.RadioBrowserClient
import com.vladutu.pilot.radio.RadioCatalog
import com.vladutu.pilot.radio.RadioStation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface SearchState {
    data object NoCountry : SearchState
    data object Loading : SearchState
    data class Loaded(val stations: List<RadioStation>) : SearchState
    data class Error(val message: String) : SearchState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioSearchScreen(
    client: RadioBrowserClient,
    countryCode: String?,
    store: CatalogStore,
    metadataFetcher: MetadataFetcher,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<SearchState>(SearchState.Loading) }
    val entries by store.entries.collectAsState(initial = emptyList())
    val inCatalog = RadioCatalog.inCatalogUuids(entries)

    suspend fun runSearch(q: String?) {
        val cc = countryCode ?: return
        state = SearchState.Loading
        state = try {
            // lastCheckOk pre-filter (spec §8): hidebroken already drops dead streams; keep verified ones.
            SearchState.Loaded(client.search(cc, q).filter { it.lastCheckOk })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagnosticLog.w("RadioSearch", "search failed", e)
            SearchState.Error("Couldn't load stations. Check your connection and retry.")
        }
    }

    // Re-runs when the country changes (e.g. user just set one in Settings).
    LaunchedEffect(countryCode) {
        state = if (countryCode == null) SearchState.NoCountry else SearchState.Loading
        if (countryCode != null) runSearch(null)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Find stations") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (state !is SearchState.NoCountry) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search stations") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboard?.hide()
                        scope.launch { runSearch(query) }
                    }),
                    trailingIcon = {
                        TextButton(onClick = { scope.launch { runSearch(query) } }) { Text("Go") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            when (val s = state) {
                is SearchState.NoCountry -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        "Set a radio country in Settings to find stations",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                is SearchState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                is SearchState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.message, style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = { scope.launch { runSearch(query.takeIf { it.isNotBlank() }) } }) {
                            Text("Retry")
                        }
                    }
                }
                is SearchState.Loaded -> {
                    if (s.stations.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No stations found") }
                    } else {
                        LazyColumn {
                            items(items = s.stations, key = { it.stationUuid }) { station ->
                                val already = station.stationUuid in inCatalog
                                ListItem(
                                    headlineContent = { Text(station.name) },
                                    supportingContent = {
                                        Text(
                                            listOfNotNull(
                                                station.codec,
                                                station.bitrate.takeIf { it > 0 }?.let { "$it kbps" },
                                            ).joinToString(" · ")
                                        )
                                    },
                                    leadingContent = {
                                        if (station.faviconUrl != null) {
                                            AsyncImage(
                                                model = station.faviconUrl,
                                                contentDescription = null,
                                                modifier = Modifier.size(40.dp),
                                            )
                                        } else {
                                            Icon(Icons.Filled.Radio, contentDescription = null, modifier = Modifier.size(40.dp))
                                        }
                                    },
                                    trailingContent = {
                                        if (already) Icon(Icons.Filled.Check, contentDescription = "Already added")
                                    },
                                    modifier = Modifier.fillMaxWidth().clickable(enabled = !already) {
                                        scope.launch {
                                            val imagePath = station.faviconUrl?.let {
                                                runCatching {
                                                    metadataFetcher.downloadImage(it, Form.RADIO, station.stationUuid)?.absolutePath
                                                }.getOrNull()
                                            }
                                            RadioCatalog.addStation(store, station, imagePath = imagePath)
                                            snackbar.showSnackbar("Added: ${station.name}")
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
