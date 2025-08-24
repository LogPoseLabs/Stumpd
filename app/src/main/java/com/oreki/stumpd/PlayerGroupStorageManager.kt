package com.oreki.stumpd

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PlayerGroupStorageManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("player_groups_v1", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val key = "groups"

    fun getAllGroups(): List<PlayerGroup> {
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<PlayerGroup>>() {}.type
            gson.fromJson<List<PlayerGroup>>(json, type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun saveAll(groups: List<PlayerGroup>) {
        prefs.edit().putString(key, gson.toJson(groups)).apply()
    }

    fun createGroup(name: String): PlayerGroup {
        val groups = getAllGroups().toMutableList()
        val g = PlayerGroup(name = name)
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

    fun addPlayers(id: String, refs: List<PlayerRef>) {
        val groups = getAllGroups().map { g ->
            if (g.id == id) {
                val existing = g.players.associateBy { it.id }.toMutableMap()
                refs.forEach { existing[it.id] = it }
                g.copy(players = existing.values.toList())
            } else g
        }
        saveAll(groups)
    }

    fun removePlayer(id: String, playerId: String) {
        saveAll(
            getAllGroups().map { g ->
                if (g.id == id) g.copy(players = g.players.filterNot { it.id == playerId }) else g
            }
        )
    }

    fun replaceMembers(id: String, members: List<PlayerRef>) {
        val groups = getAllGroups().map { g ->
            if (g.id == id) g.copy(players = members) else g
        }
        saveAll(groups)
    }
}
