package com.oreki.stumpd.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.oreki.stumpd.ui.theme.StumpdPaletteId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private val Context.themeDataStore by preferencesDataStore(name = "theme_settings_v1")

enum class DarkModeOption { SYSTEM, LIGHT, DARK }

data class ThemeSettings(
    val paletteId: String = StumpdPaletteId.PITCH_GREEN.name,
    val customSeedColorArgb: Int? = null,
    val darkMode: DarkModeOption = DarkModeOption.SYSTEM,
)

/**
 * Persists the user's chosen color palette / custom color / dark mode preference.
 *
 * All instances share one process-wide [settingsFlow] so that changing the setting on the
 * Settings screen re-themes every currently open Activity immediately (each Activity's
 * `StumpdTheme` composable collects the same flow), without needing to restart activities.
 */
class ThemePreferencesManager(private val context: Context) {
    private val keyPalette = stringPreferencesKey("palette_id")
    private val keySeed = intPreferencesKey("custom_seed_color")
    private val keyDarkMode = stringPreferencesKey("dark_mode")

    suspend fun getThemeSettings(): ThemeSettings = withContext(Dispatchers.IO) {
        try {
            val prefs = context.themeDataStore.data.first()
            ThemeSettings(
                paletteId = prefs[keyPalette] ?: ThemeSettings().paletteId,
                customSeedColorArgb = prefs[keySeed],
                darkMode = prefs[keyDarkMode]
                    ?.let { runCatching { DarkModeOption.valueOf(it) }.getOrNull() }
                    ?: DarkModeOption.SYSTEM,
            )
        } catch (_: Exception) {
            ThemeSettings()
        }
    }

    /** Persists [settings] and publishes it to every screen observing [settingsFlow]. */
    suspend fun saveThemeSettings(settings: ThemeSettings) = withContext(Dispatchers.IO) {
        // Marked before writing so a slow start-up load cannot land afterwards and republish
        // the pre-change values it read from disk.
        hasLocalWrite.set(true)
        context.themeDataStore.edit { prefs ->
            prefs[keyPalette] = settings.paletteId
            if (settings.customSeedColorArgb != null) {
                prefs[keySeed] = settings.customSeedColorArgb
            } else {
                prefs.remove(keySeed)
            }
            prefs[keyDarkMode] = settings.darkMode.name
        }
        state.value = settings
    }

    /**
     * Applies [transform] to the settings currently in effect.
     *
     * Prefer this over building a `copy()` from a value captured in a composable: a captured
     * snapshot can be one recomposition behind, and saving it silently reverts whatever changed
     * in between - e.g. picking a custom colour and then switching dark mode would write back
     * the old null seed and drop the colour.
     */
    suspend fun updateThemeSettings(transform: (ThemeSettings) -> ThemeSettings) {
        saveThemeSettings(transform(state.value))
    }

    companion object {
        private val state = MutableStateFlow(ThemeSettings())
        private val loadStarted = AtomicBoolean(false)
        private val hasLocalWrite = AtomicBoolean(false)
        private val holderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Live theme settings, shared across the whole app. */
        val settingsFlow: StateFlow<ThemeSettings> = state.asStateFlow()

        /** Kicks off a one-time load from disk into [settingsFlow]. Safe to call repeatedly. */
        fun ensureLoaded(appContext: Context) {
            if (loadStarted.compareAndSet(false, true)) {
                holderScope.launch {
                    val loaded = ThemePreferencesManager(appContext.applicationContext).getThemeSettings()
                    // If the user already changed the theme while this read was in flight, the
                    // on-disk snapshot is stale - publishing it would visibly revert their pick.
                    if (!hasLocalWrite.get()) {
                        state.value = loaded
                    }
                }
            }
        }
    }
}
