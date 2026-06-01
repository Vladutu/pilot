package com.vladutu.pilot.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vladutu.pilot.catalog.Form

@Composable
fun AddUrlDialog(
    activeForm: Form,
    onSubmit: (urlText: String, title: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var urlText by remember { mutableStateOf("") }
    var titleText by remember { mutableStateOf("") }

    val urlLabel = when (activeForm) {
        Form.PLAYLIST, Form.SONG -> "YouTube Music URL"
        Form.DESTINATION -> "Google Maps or Waze URL"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val title = when (activeForm) {
                Form.PLAYLIST -> "Add playlist"
                Form.SONG -> "Add song"
                Form.DESTINATION -> "Add destination"
            }
            Text(title)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text(urlLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(urlText.trim(), titleText.trim().takeIf { it.isNotBlank() }) },
                enabled = urlText.isNotBlank(),
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
