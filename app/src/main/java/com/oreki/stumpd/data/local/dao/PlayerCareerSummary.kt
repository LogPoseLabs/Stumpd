package com.oreki.stumpd.data.local.dao

/** Aggregated career totals for a single player, produced by [MatchDao.playerCareerSummaries]. */
data class PlayerCareerSummary(
    val playerId: String,
    val matches: Int,
    val runs: Int,
    val wickets: Int,
)
