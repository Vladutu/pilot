package com.vladutu.pilot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vladutu.pilot.catalog.CATALOG
import com.vladutu.pilot.catalog.CatalogEntry
import com.vladutu.pilot.net.NtfyPublisher
import kotlinx.coroutines.launch

@Composable
fun CatalogScreen(publisher: NtfyPublisher) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val busy = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            items(items = CATALOG, key = { it.ytListId }) { entry: CatalogEntry ->
                Tile(
                    label = entry.label,
                    busy = busy[entry.ytListId] == true,
                    onClick = {
                        busy[entry.ytListId] = true
                        scope.launch {
                            try {
                                publisher.publishYtMusicPlaylist(entry.ytListId)
                                snackbar.showSnackbar("Sent: ${entry.label}")
                            } catch (e: Exception) {
                                snackbar.showSnackbar("Failed — check connection")
                            } finally {
                                busy[entry.ytListId] = false
                            }
                        }
                    },
                )
            }
        }
    }
}
