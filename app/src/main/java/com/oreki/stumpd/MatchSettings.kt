package com.oreki.stumpd

import android.content.Context
import com.google.gson.Gson

data class MatchSettings(
    // Basic match settings
    val totalOvers: Int = 2,
    val maxPlayersPerTeam: Int = 11,
    val allowSingleSideBatting: Boolean = false, // One batsman continues if others get out
    // Extras settings - runs awarded
    val noballRuns: Int = 0,
    val byeRuns: Int = 0,
    val legByeRuns: Int = 0,
    val legSideWideRuns: Int = 0,
    val offSideWideRuns: Int = 0,
    // Advanced rules
    val powerplayOvers: Int = 6, // First 6 overs are powerplay
    val maxOversPerBowler: Int = 4,
    val enforceFollowOn: Boolean = false,
    val duckworthLewisMethod: Boolean = false,
    // Joker player rules
    val jokerCanBatAndBowl: Boolean = true,
    val jokerMaxOvers: Int = 2,
    val jokerCountsForBothTeams: Boolean = true,
    // Match format
    val matchFormat: MatchFormat = MatchFormat.T20,
    val tossWinnerChoice: TossChoice = TossChoice.BAT_FIRST,
)

enum class MatchFormat(
    val displayName: String,
    val defaultOvers: Int,
) {
    T20("T20 (20 overs)", 20),
    T10("T10 (10 overs)", 10),
    ODI("One Day (50 overs)", 50),
    TEST("Test Match (Unlimited)", 0),
    CUSTOM("Custom Format", 0),
}

enum class TossChoice(
    val displayName: String,
) {
    BAT_FIRST("Bat First"),
    BOWL_FIRST("Bowl First"),
}

data class GlobalSettings(
    val defaultMatchSettings: MatchSettings = MatchSettings(),
    val autoSaveMatch: Boolean = true,
    val soundEffects: Boolean = true,
    val vibrationFeedback: Boolean = true,
    val darkMode: Boolean = false,
    val expertMode: Boolean = false, // Shows advanced statistics
)

class MatchSettingsManager(
    private val context: Context,
) {
    private val prefs = context.getSharedPreferences("match_settings", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getGlobalSettings(): GlobalSettings =
        try {
            val settingsJson = prefs.getString("global_settings", null)
            if (settingsJson != null) {
                gson.fromJson(settingsJson, GlobalSettings::class.java)
            } else {
                GlobalSettings()
            }
        } catch (e: Exception) {
            GlobalSettings()
        }

    fun saveGlobalSettings(settings: GlobalSettings) {
        val settingsJson = gson.toJson(settings)
        prefs.edit().putString("global_settings", settingsJson).apply()
    }

    fun getDefaultMatchSettings(): MatchSettings = getGlobalSettings().defaultMatchSettings

    fun saveDefaultMatchSettings(settings: MatchSettings) {
        val globalSettings = getGlobalSettings()
        val updatedSettings = globalSettings.copy(defaultMatchSettings = settings)
        saveGlobalSettings(updatedSettings)
    }
}
