package com.vladutu.pilot.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The share activity's only UI. Renders nothing on the [ConversionUiState.Working] fast path (the
 * activity is translucent, so the previous app shows through), and a centered "Converting… [Stop]"
 * card over a dim scrim once the papko retry loop is running ([ConversionUiState.Retrying]).
 */
@Composable
fun ConversionOverlay(state: ConversionUiState, onStop: () -> Unit) {
    if (state !is ConversionUiState.Retrying) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.padding(32.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator()
                Text(
                    text = "Converting “${state.label}”…",
                    textAlign = TextAlign.Center,
                )
                // attempt = papko tries already made; show the one about to run.
                Text(text = "attempt ${state.attempt + 1}")
                if (state.attempt >= LONG_RUN_HINT_AFTER) {
                    Text(
                        text = "papko may be down — you can stop and try again later",
                        textAlign = TextAlign.Center,
                    )
                }
                Button(onClick = onStop) {
                    Text(text = "Stop")
                }
            }
        }
    }
}

private const val LONG_RUN_HINT_AFTER = 15
