package com.vladutu.pilot.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PilotColorScheme = darkColorScheme(
    primary = PilotPrimary,
    onPrimary = PilotOnPrimary,
    secondary = PilotPrimary,
    onSecondary = PilotOnPrimary,
    tertiary = PilotPrimary,
    onTertiary = PilotOnPrimary,
    background = PilotBackground,
    onBackground = PilotOnSurface,
    surface = PilotSurface,
    onSurface = PilotOnSurface,
    surfaceVariant = PilotSurfaceVariant,
    onSurfaceVariant = PilotOnSurfaceVariant,
    outline = PilotOutline,
    outlineVariant = PilotOutline,
    error = PilotError,
    onError = PilotOnPrimary,
)

@Composable
fun PilotTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // statusBarColor/navigationBarColor are deprecated in favour of edge-to-edge, which
            // has no like-for-like replacement and would change layout insets. targetSdk is pinned
            // at 34 on purpose, so we keep the solid bars that match the app background.
            @Suppress("DEPRECATION")
            window.statusBarColor = PilotBackground.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = PilotBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }
    MaterialTheme(
        colorScheme = PilotColorScheme,
        typography = PilotTypography,
        shapes = PilotShapes,
        content = content,
    )
}
