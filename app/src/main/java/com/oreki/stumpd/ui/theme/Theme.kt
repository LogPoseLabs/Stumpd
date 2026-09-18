package com.oreki.stumpd.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import com.oreki.stumpd.data.preferences.DarkModeOption
import com.oreki.stumpd.data.preferences.ThemePreferencesManager

/**
 * App-wide theme. Reads the user's palette / dark-mode choice from [ThemePreferencesManager]
 * so every screen re-themes live the moment the user changes it in Settings.
 */
@Composable
fun StumpdTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        ThemePreferencesManager.ensureLoaded(context)
    }

    val settings by ThemePreferencesManager.settingsFlow.collectAsState()

    val darkTheme = when (settings.darkMode) {
        DarkModeOption.SYSTEM -> isSystemInDarkTheme()
        DarkModeOption.LIGHT -> false
        DarkModeOption.DARK -> true
    }

    val paletteId = StumpdPaletteId.fromStorageKey(settings.paletteId)
    val colorScheme = colorSchemeFor(paletteId, darkTheme, settings.customSeedColorArgb, context)

    // Keep the window itself in step with the scheme. The launch window is painted from
    // themes.xml before anything composes, so without this the frame behind a screen can be a
    // different colour to the screen — most visibly when the user's dark-mode choice disagrees
    // with the system's.
    val view = LocalView.current
    if (!view.isInEditMode) {
        val background = colorScheme.background.toArgb()
        val systemBars = colorScheme.surface.toArgb()
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.setBackgroundDrawable(ColorDrawable(background))
            // The system bars were left at the platform default, which meant a grey status bar
            // sitting above a near-white app in light mode. They follow `surface` because that's
            // what's directly underneath them on every screen — and the icon appearance has to
            // come from the app's own dark-mode choice, not the system's, or the icons go
            // invisible whenever the two disagree.
            @Suppress("DEPRECATION")
            window.statusBarColor = systemBars
            @Suppress("DEPRECATION")
            window.navigationBarColor = systemBars
            WindowInsetsControllerCompat(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalStumpdColors provides stumpdColorsFor(darkTheme)) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = StumpdTypography,
            shapes = StumpdShapes,
            content = content
        )
    }
}
