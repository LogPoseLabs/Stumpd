package com.oreki.stumpd

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PlayerGroupStorageManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("player_groups_v3", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val key = "groups"

    fun getAllGroups(): List<PlayerGroup> {
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            val type = TypeToken.getParameterized(List::class.java, PlayerGroup::class.java).type
            gson.fromJson<List<PlayerGroup>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAll(groups: List<PlayerGroup>) {
        prefs.edit().putString(key, gson.toJson(groups)).apply()
    }

    fun createGroup(
        name: String,
        defaults: GroupDefaultSettings = GroupDefaultSettings(
            matchSettings = MatchSettingsManager(context).getDefaultMatchSettings()
        )
    ): PlayerGroup {
        val groups = getAllGroups().toMutableList()
        val g = PlayerGroup(name = name, playerIds = emptyList(), defaults = defaults)
        groups.add(g)
        saveAll(groups)
        return g
    }

    fun renameGroup(id: String, newName: String) {
        saveAll(getAllGroups().map { if (it.id == id) it.copy(name = newName) else it })
    }

    fun deleteGroup(id: String) {
        saveAll(getAllGroups().filterNot { it.id == id })
    }

    fun addPlayers(id: String, playerIds: List<String>) {
        val groups = getAllGroups().map { g ->
            if (g.id == id) g.copy(playerIds = (g.playerIds + playerIds).distinct()) else g
        }
        saveAll(groups)
    }

    fun removePlayer(id: String, playerId: String) {
        saveAll(getAllGroups().map { g ->
            if (g.id == id) g.copy(playerIds = g.playerIds.filterNot { it == playerId }) else g
        })
    }

    fun replaceMembers(id: String, members: List<String>) {
        val groups = getAllGroups().map { g ->
            if (g.id == id) g.copy(playerIds = members.distinct()) else g
        }
        saveAll(groups)
    }

    fun updateDefaults(id: String, newDefaults: GroupDefaultSettings) {
        saveAll(getAllGroups().map { g ->
            if (g.id == id) g.copy(defaults = newDefaults) else g
        })
    }
}
