package com.oreki.stumpd.data.sync

/**
 * Represents the current sync state
 */
sealed class SyncState {
    object Idle : SyncState()
    data class Syncing(
        val progress: Int = 0,
        val total: Int = 0,
        val message: String = "Syncing...",
        val subProgress: Int = 0,   // Progress within the current step (e.g., match 3 of 15)
        val subTotal: Int = 0       // Total items in the current step
    ) : SyncState()
    data class Success(val itemsSynced: Int) : SyncState()
    data class Error(val message: String, val error: Throwable? = null) : SyncState()
    object Offline : SyncState()
}

/**
 * Result of a sync operation
 */
sealed class SyncResult {
    data class Success(val itemsSynced: Int) : SyncResult()
    data class PartialSuccess(val synced: Int, val failed: Int, val errors: List<String>) : SyncResult()
    data class Failure(val message: String, val error: Throwable? = null) : SyncResult()
    object NoDataToSync : SyncResult()
    object Offline : SyncResult()
}

/**
 * Metadata for tracking sync state
 */
data class SyncMetadata(
    val lastSyncTimestamp: Long = 0L,
    val lastSyncSuccess: Boolean = false,
    val pendingUploads: Int = 0,
    val pendingDownloads: Int = 0,
    val deviceId: String = "",
    val userId: String? = null
)
