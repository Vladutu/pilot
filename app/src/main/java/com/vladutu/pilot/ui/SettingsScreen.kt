package com.vladutu.pilot.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.vladutu.pilot.settings.SettingsStore
import com.vladutu.pilot.settings.TopicPairing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentTopic by settingsStore.topicFlow.collectAsState(initial = null)

    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun savePaired(topic: String) {
        scope.launch {
            settingsStore.setTopic(topic)
            error = null
            input = ""
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents == null) {
            // user cancelled — leave state unchanged
            return@rememberLauncherForActivityResult
        }
        val topic = TopicPairing.parsePairUri(contents) ?: TopicPairing.validate(contents)
        if (topic != null) savePaired(topic)
        else error = "That QR code isn't a Copilot pairing code."
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Pairing", style = MaterialTheme.typography.titleMedium)

            Text(
                text = currentTopic?.let { "Paired: $it" } ?: "Not paired",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = {
                    error = null
                    scanLauncher.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("Scan the Copilot pairing QR")
                            .setBeepEnabled(false)
                            .setOrientationLocked(false),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Text("  Scan QR")
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it; error = null },
                label = { Text("Or paste topic / pairing link") },
                isError = error != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            OutlinedButton(
                onClick = {
                    val topic = TopicPairing.parsePairUri(input) ?: TopicPairing.validate(input)
                    if (topic != null) savePaired(topic)
                    else error = "Invalid topic. Expected a copilot-… code or a pilot://pair link."
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }

            Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
            ListItem(
                headlineContent = { Text("Open diagnostics") },
                leadingContent = { Icon(Icons.Filled.BugReport, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenDiagnostics),
            )
        }
    }
}
