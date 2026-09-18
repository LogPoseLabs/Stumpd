package com.oreki.stumpd.data.local.dao

/** How many players one side fielded in one match. */
data class MatchSquadSize(
    val matchId: String,
    val team: String,
    val squadSize: Int,
)
