package com.oreki.stumpd.data.local.entity

import androidx.room.Entity

/**
 * Per-record upload watermark, so an interrupted sync resumes instead of restarting.
 *
 * A single global "last synced at" timestamp cannot express partial progress: if a run uploads
 * 30 of 80 matches and then stops (quota, network, process death), the watermark either advances
 * - stranding the 50 that never went up - or stays put, and the next run re-uploads all 80.
 * One row per record makes progress exact and monotonic.
 *
 * [syncedUpdatedAt] is the record's own `updatedAt` at the moment it was accepted by the cloud,
 * so a later local edit (which bumps `updatedAt`) automatically marks it pending again.
 */
@Entity(tableName = "sync_progress", primaryKeys = ["collection", "recordId"])
data class SyncProgressEntity(
    val collection: String,
    val recordId: String,
    val syncedUpdatedAt: Long,
) {
    companion object {
        const val COLLECTION_MATCHES = "matches"

        /**
         * Tournaments upload whole, one document each, so a per-record watermark is exactly
         * right: an interrupted run resumes at the next tournament rather than re-sending them
         * all, and a later edit bumps `updatedAt` and marks that one pending again.
         */
        const val COLLECTION_TOURNAMENTS = "tournaments"
    }
}
