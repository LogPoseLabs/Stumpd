package com.oreki.stumpd.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * One correction that was applied to a match.
 *
 * A corrected match syncs to everyone else in the group without a word, so a group member can
 * open a scorecard and find a number different from the one they remember. This is the record
 * that answers "why" — what changed, when, and from which device.
 *
 * [summary] holds the same lines the person confirmed in the preview, so the log reads as an
 * explanation rather than an audit dump. [beforeJson] holds the match as it was, which makes a
 * correction reversible: re-saving that graph puts it back.
 */
@Entity(
    tableName = "match_corrections",
    primaryKeys = ["matchId", "appliedAt"],
    indices = [Index("matchId")],
)
data class MatchCorrectionLogEntity(
    val matchId: String,
    val appliedAt: Long,
    val deviceLabel: String,
    val summary: String,
    val beforeJson: String?,
)
