package com.vladutu.pilot.ui.theme

import androidx.compose.ui.graphics.Color

// Dark automotive-cockpit palette. Dark-only — Pilot reads the same at noon and 11pm,
// so we don't follow the system light/dark setting.

val PilotBackground = Color(0xFF0E1116)       // near-black with a touch of blue
val PilotSurface = Color(0xFF161B22)          // cards / tiles sit on this
val PilotSurfaceVariant = Color(0xFF1E2530)   // segmented control track, status pill bg
val PilotOutline = Color(0xFF2A323D)          // 1dp tile borders, dividers

val PilotPrimary = Color(0xFFFFB020)          // warm amber — accent, FAB, selection
val PilotOnPrimary = Color(0xFF0E1116)

val PilotOnSurface = Color(0xFFE6EAF0)        // primary text
val PilotOnSurfaceVariant = Color(0xFF9AA4B2) // secondary text, badges

val PilotError = Color(0xFFE5484D)            // failed-send pill, error snackbar
val PilotOk = Color(0xFF4FCB66)               // successful-send pill
