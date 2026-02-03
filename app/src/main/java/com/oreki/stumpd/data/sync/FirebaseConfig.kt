package com.oreki.stumpd.data.sync

/**
 * Firebase Firestore Configuration
 * 
 * Collections structure (GLOBAL - shared with all users):
 * - matches/{matchId} - Match data (includes ownerId for edit permissions)
 * - players/{playerId} - Player profiles  
 * - groups/{groupId} - Player groups
 * - in_progress_matches/{matchId} - Active matches (for live spectating)
 * 
 * User-specific collections (private):
 * - users/{userId}/preferences/ - User preferences
 * - users/{userId}/group_last_teams/ - Last used teams per group
 * 
 * Sync strategy:
 * - Offline-first: Always read from Room, sync in background
 * - Conflict resolution: Last-write-wins with timestamp
 * - Auto-sync: On app start, after match save, when network reconnects
 * - All match/player/group data is GLOBALLY visible to all app users
 */
object FirebaseConfig {
    // Collection names (GLOBAL - shared with all users)
    const val COLLECTION_MATCHES = "matches"
    const val COLLECTION_PLAYERS = "players"
    const val COLLECTION_GROUPS = "groups"
    const val COLLECTION_MATCH_STATS = "match_stats"
    const val COLLECTION_PARTNERSHIPS = "partnerships"
    const val COLLECTION_FALL_OF_WICKETS = "fall_of_wickets"
    const val COLLECTION_IN_PROGRESS_MATCHES = "in_progress_matches"
    
    // User-specific collection (for preferences only)
    const val COLLECTION_USERS = "users"
    
    // Sync settings
    const val SYNC_TIMEOUT_MS = 30_000L // 30 seconds
    const val AUTO_SYNC_ENABLED = true
    const val SYNC_ON_APP_START = true
    const val SYNC_ON_NETWORK_RECONNECT = true
    
    // Field names for metadata
    const val FIELD_UPDATED_AT = "updatedAt"
    const val FIELD_CREATED_AT = "createdAt"
    const val FIELD_SYNCED_AT = "syncedAt"
    const val FIELD_DEVICE_ID = "deviceId"
    const val FIELD_OWNER_ID = "ownerId" // Track who created the data
}
