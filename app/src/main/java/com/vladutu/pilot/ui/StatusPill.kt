package com.vladutu.pilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vladutu.pilot.ui.theme.PilotOk
import kotlinx.coroutines.flow.StateFlow

@Composable
fun StatusPill(
    statusFlow: StateFlow<PublishStatus>,
    modifier: Modifier = Modifier,
) {
    val status by statusFlow.collectAsState()
    val color = when (status) {
        PublishStatus.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
        PublishStatus.Ok -> PilotOk
        PublishStatus.Failed -> MaterialTheme.colorScheme.error
    }
    Box(
        modifier = modifier
            .padding(end = 4.dp)
            .size(12.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f))
            .padding(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}