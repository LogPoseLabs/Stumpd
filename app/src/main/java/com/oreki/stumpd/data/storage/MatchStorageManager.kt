package com.oreki.stumpd.data.storage

import android.content.Context
import android.util.Log
import com.google.gson.reflect.TypeToken
import com.oreki.stumpd.MatchHistory
import com.oreki.stumpd.data.util.Constants
import com.oreki.stumpd.data.util.GsonProvider

/**
 * Enhanced MatchStorageManager with better persistence
 * Manages match history storage using SharedPreferences
 */
class MatchStorageManager(private val context: Context) {
    
    private val prefs = context.getSharedPreferences(Constants.PREFS_MATCHES, Context.MODE_PRIVATE)
    private val gson = GsonProvider.get()

    /**
     * Saves a match to storage
     * @param match The match to save
     */
    fun saveMatch(match: MatchHistory) {
        try {
            val matches = getAllMatches().toMutableList()
            matches.add(0, match) // Add to beginning

            // Keep only configured max matches to prevent storage bloat
            if (matches.size > Constants.MAX_MATCHES_STORED) {
                matches.subList(Constants.MAX_MATCHES_STORED, matches.size).clear()
            }

            // Convert to JSON string
            val matchesJson = gson.toJson(matches)

            prefs.edit()
                .putString(Constants.KEY_MATCHES_JSON, matchesJson)
                .putLong(Constants.KEY_LAST_UPDATED, System.currentTimeMillis())
                .apply()

            Log.d(Constants.LOG_TAG_MATCH_STORAGE, "Saved match: ${match.team1Name} vs ${match.team2Name}")
            Log.d(Constants.LOG_TAG_MATCH_STORAGE, "Total matches saved: ${matches.size}")
        } catch (e: Exception) {
            Log.e(Constants.LOG_TAG_MATCH_STORAGE, "Error saving match", e)
        }
    }

    /**
     * Returns the ID of the most recently added match
     */
    fun getLatestMatchID(): String? {
        val all = getAllMatches()
        return all.lastOrNull()?.id
    }

    /**
     * Retrieves all matches from storage
     * @return List of all stored matches
     */
    fun getAllMatches(): List<MatchHistory> {
        return try {
            val matchesJson = prefs.getString(Constants.KEY_MATCHES_JSON, null)
            if (matchesJson.isNullOrEmpty()) {
                Log.d(Constants.LOG_TAG_MATCH_STORAGE, "No matches found in storage")
                emptyList()
            } else {
                val matches = gson.fromJson(matchesJson, Array<MatchHistory>::class.java).toList()
                Log.d(Constants.LOG_TAG_MATCH_STORAGE, "Loaded ${matches.size} matches from storage")
                matches
            }
        } catch (e: Exception) {
            Log.e(Constants.LOG_TAG_MATCH_STORAGE, "Error loading matches", e)
            emptyList()
        }
    }

    /**
     * Deletes a specific match by ID
     * @param matchId The ID of the match to delete
     */
    fun deleteMatch(matchId: String) {
        try {
            val matches = getAllMatches().filter { it.id != matchId }
            val matchesJson = gson.toJson(matches)

            prefs.edit()
                .putString(Constants.KEY_MATCHES_JSON, matchesJson)
                .putLong(Constants.KEY_LAST_UPDATED, System.currentTimeMillis())
                .apply()

            Log.d(Constants.LOG_TAG_MATCH_STORAGE, "Deleted match with ID: $matchId")
            Log.d(Constants.LOG_TAG_MATCH_STORAGE, "Remaining matches: ${matches.size}")
        } catch (e: Exception) {
            Log.e(Constants.LOG_TAG_MATCH_STORAGE, "Error deleting match", e)
        }
    }

    /**
     * Clears all matches from storage
     */
    fun clearAllMatches() {
        prefs.edit().clear().apply()
        Log.d(Constants.LOG_TAG_MATCH_STORAGE, "Cleared all matches")
    }

    /**
     * Debug function to check what's stored
     * @return Debug information as string
     */
    fun debugStorage(): String {
        val matchesJson = prefs.getString(Constants.KEY_MATCHES_JSON, "No data")
        val lastUpdated = prefs.getLong(Constants.KEY_LAST_UPDATED, 0)
        return "Storage Debug:\nJSON: $matchesJson\nLast Updated: $lastUpdated"
    }

    /**
     * Exports matches to a JSON file
     * @param fileName The name of the export file (default includes timestamp)
     * @return The absolute path of the exported file, or null if export failed
     */
    fun exportMatches(fileName: String = "${Constants.EXPORT_FILE_PREFIX}${System.currentTimeMillis()}${Constants.EXPORT_FILE_EXTENSION}"): String? {
        return try {
            val matches = getAllMatches()
            val json = gson.toJson(matches)

            val path = context.getExternalFilesDir(null)
            if (path == null) {
                Log.e(Constants.LOG_TAG_EXPORT, "External storage not available")
                return null
            }

            val file = java.io.File(path, fileName)
            file.writeText(json)

            Log.d(Constants.LOG_TAG_EXPORT, "Exported ${matches.size} matches to ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(Constants.LOG_TAG_EXPORT, "Failed to export matches", e)
            null
        }
    }

    /**
     * Imports matches from a JSON file
     * @param filePath The path to the file to import from
     * @return true if import was successful, false otherwise
     */
    fun importMatches(filePath: String): Boolean {
        return try {
            val file = java.io.File(filePath)
            if (!file.exists()) {
                Log.e(Constants.LOG_TAG_IMPORT, "File does not exist: $filePath")
                return false
            }

            val json = file.readText()
            val type = TypeToken.getParameterized(
                List::class.java,
                MatchHistory::class.java
            ).type

            val matches: List<MatchHistory> = gson.fromJson(json, type)

            // Import all matches
            matches.forEach { match ->
                saveMatch(match)
            }

            Log.d(Constants.LOG_TAG_IMPORT, "Imported ${matches.size} matches")
            true
        } catch (e: Exception) {
            Log.e(Constants.LOG_TAG_IMPORT, "Failed to import matches", e)
            false
        }
    }

    /**
     * Creates a backup file and returns its path
     * @return The path to the backup file, or null if backup failed
     */
    fun shareBackup(): String? {
        val backupPath = exportMatches()
        if (backupPath != null) {
            // You can add sharing functionality here
            return backupPath
        }
        return null
    }
}



