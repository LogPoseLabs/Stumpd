package com.oreki.stumpd.data.preferences

import android.content.Context
import com.google.gson.Gson
import com.oreki.stumpd.domain.model.GlobalSettings
import com.oreki.stumpd.domain.model.MatchSettings

/**
 * The app-wide settings that aren't per-match: the default match settings a new match or group
 * starts from, plus the handful of global toggles in [GlobalSettings] (vibration, sound, expert
 * mode).
 *
 * There used to be two classes with this name and the same API — this one, backed by DataStore
 * under `match_settings_v4`, and a synchronous SharedPreferences one in `domain.model` under
 * `match_settings_v3`. Every caller used the synchronous one, so the DataStore store had never
 * held a byte; this is that surviving implementation, moved here beside
 * [ThemePreferencesManager] and [PasscodeManager] so the next person looking for a preference
 * store finds one answer instead of two.
 *
 * Kept synchronous deliberately. All five callers need a value while building their first frame —
 * two inside `remember {}` in a composable, three in a ViewModel's field initialisers — and a
 * suspend read would hand them a default first and the real value a frame later, which in
 * `EditGroupActivity` means text fields that visibly rewrite themselves. It's one small JSON blob
 * from a warm SharedPreferences file, and the prefs name is unchanged so anything already written
 * by an older build still reads.
 */
class MatchSettingsManager(
    private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getGlobalSettings(): GlobalSettings =
        try {
            val settingsJson = prefs.getString(KEY_GLOBAL_SETTINGS, null)
            if (settingsJson != null) {
                gson.fromJson(settingsJson, GlobalSettings::class.java)
            } else {
                GlobalSettings()
            }
        } catch (_: Exception) {
            GlobalSettings()
        }

    fun saveGlobalSettings(settings: GlobalSettings) {
        prefs.edit().putString(KEY_GLOBAL_SETTINGS, gson.toJson(settings)).apply()
    }

    fun getDefaultMatchSettings(): MatchSettings = getGlobalSettings().defaultMatchSettings

    fun saveDefaultMatchSettings(settings: MatchSettings) {
        saveGlobalSettings(getGlobalSettings().copy(defaultMatchSettings = settings))
    }

    private companion object {
        const val PREFS_NAME = "match_settings_v3"
        const val KEY_GLOBAL_SETTINGS = "global_settings"
    }
}
