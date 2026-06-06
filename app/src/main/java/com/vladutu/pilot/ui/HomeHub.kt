package com.vladutu.pilot.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vladutu.pilot.catalog.Form
import com.vladutu.pilot.diagnostics.DiagnosticsActivity

private data class CategorySpec(val form: Form, val label: String, val icon: ImageVector)

private val CATEGORIES = listOf(
    CategorySpec(Form.PLAYLIST, "Playlists", Icons.Filled.PlaylistPlay),
    CategorySpec(Form.SONG, "Songs", Icons.Filled.MusicNote),
    CategorySpec(Form.DESTINATION, "Places", Icons.Filled.Place),
    CategorySpec(Form.RADIO, "Radio", Icons.Filled.Radio),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeHub(
    publishStatus: PublishStatusHolder,
    onOpenCategory: (Form) -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Pilot") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                actions = {
                    StatusPill(statusFlow = publishStatus.state)
                    IconButton(onClick = {
                        context.startActivity(Intent(context, DiagnosticsActivity::class.java))
                    }) {
                        Icon(Icons.Default.Info, contentDescription = "Diagnostics")
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            items(items = CATEGORIES, key = { it.form.name }) { spec ->
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clickable { onOpenCategory(spec.form) },
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = spec.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp).padding(bottom = 12.dp),
                        )
                        Text(
                            text = spec.label,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
