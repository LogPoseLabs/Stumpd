package com.oreki.stumpd.data.util

/**
 * Application-wide constants for database, storage, and preferences
 */
object Constants {
    // Storage keys
    const val PREFS_MATCHES = "cricket_matches_v3"
    const val PREFS_PLAYERS = "cricket_players_v3"
    const val PREFS_PLAYERS_DETAILED = "cricket_players_detailed_v3"
    const val PREFS_GROUPS = "player_groups_v3"
    const val PREFS_IN_PROGRESS_MATCH = "in_progress_match"
    
    const val KEY_MATCHES_JSON = "matches_json"
    const val KEY_PLAYERS_JSON = "players_json"
    const val KEY_DETAILED_PLAYERS = "detailed_players"
    const val KEY_GROUPS = "groups"
    const val KEY_LAST_UPDATED = "last_updated"
    const val KEY_IN_PROGRESS_MATCH = "match_in_progress_json"
    
    // Storage limits
    const val MAX_MATCHES_STORED = 500
    const val MAX_MATCHES_EXPORT = 10_000
    
    // Default values
    const val DEFAULT_TEAM_NAME_1 = "Team A"
    const val DEFAULT_TEAM_NAME_2 = "Team B"
    const val DEFAULT_GROUND_NAME = "Default Ground"
    
    // Database
    const val DB_NAME = "stumpd.db"
    const val DB_VERSION = 2
    
    // Conversion constants
    const val BALLS_PER_OVER = 6
    const val RUNS_PER_OVER_DIVISOR = 100.0
    
    // Time constants (milliseconds)
    const val MILLIS_PER_MINUTE = 60_000L
    const val MILLIS_PER_HOUR = 3_600_000L
    const val MILLIS_PER_DAY = 86_400_000L
    const val MILLIS_PER_WEEK = 604_800_000L
    
    // File export
    const val EXPORT_FILE_PREFIX = "stumpd_backup_"
    const val EXPORT_FILE_EXTENSION = ".json"
    
    // Logging tags
    const val LOG_TAG_MATCH_STORAGE = "MatchStorage"
    const val LOG_TAG_PLAYER_STORAGE = "PlayerStorage"
    const val LOG_TAG_PLAYER_SYNC = "PlayerSync"
    const val LOG_TAG_IMPORT = "Import"
    const val LOG_TAG_EXPORT = "Export"
}

