package com.oreki.stumpd.data.preferences

import com.oreki.stumpd.domain.model.*
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

val Context.settingsDataStore by preferencesDataStore(name = "match_settings_v4")

class MatchSettingsManager(private val context: Context) {
    private val gson = Gson()
    private val KEY_GLOBAL_JSON = stringPreferencesKey("global_settings_json")

    suspend fun getGlobalSettings(): GlobalSettings = withContext(Dispatchers.IO) {
        val json = context.settingsDataStore.data
            .map { it[KEY_GLOBAL_JSON] }
            .first()
        return@withContext try {
            if (json != null) gson.fromJson(json, GlobalSettings::class.java) else GlobalSettings()
        } catch (_: Exception) {
            GlobalSettings()
        }
    }

    suspend fun saveGlobalSettings(settings: GlobalSettings) = withContext(Dispatchers.IO) {
        val json = gson.toJson(settings)
        context.settingsDataStore.edit { it[KEY_GLOBAL_JSON] = json }
    }

    suspend fun getDefaultMatchSettings(): MatchSettings = getGlobalSettings().defaultMatchSettings

    suspend fun saveDefaultMatchSettings(settings: MatchSettings) {
        val current = getGlobalSettings()
        saveGlobalSettings(current.copy(defaultMatchSettings = settings))
    }
}
