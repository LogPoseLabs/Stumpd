package com.oreki.stumpd.domain.tournament

import com.oreki.stumpd.domain.model.MatchHistory

/** A player's tally of Player of the Match awards within one tournament. */
data class PotmEntry(val playerId: String, val name: String, val count: Int)

/**
 * Who has won Player of the Match, and how often, across a tournament's own matches.
 *
 * A match without a Player of the Match — the super-over match-completion path can save before
 * one is picked, or a very old match predates the field — is simply skipped rather than counted
 * against "Unknown", since that would turn a data gap into a leaderboard entry.
 */
fun potmTally(matches: List<MatchHistory>): List<PotmEntry> = matches
    .mapNotNull { match ->
        match.playerOfTheMatchId?.let { id -> id to match.playerOfTheMatchName.orEmpty() }
    }
    .groupBy({ it.first }, { it.second })
    .map { (id, names) -> PotmEntry(id, names.firstOrNull { it.isNotBlank() } ?: "Unknown", names.size) }
    .sortedByDescending { it.count }
