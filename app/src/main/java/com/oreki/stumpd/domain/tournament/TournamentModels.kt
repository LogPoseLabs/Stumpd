package com.oreki.stumpd.domain.tournament

/**
 * The vocabulary of a tournament, and nothing else.
 *
 * Deliberately free of Android, Room and the match model: fixture generation and the standings
 * table are arithmetic, and arithmetic is worth being able to test without a device. The repository
 * turns these into rows; nothing here knows that rows exist.
 */

enum class TournamentFormat {
    SINGLE_ROUND_ROBIN,
    DOUBLE_ROUND_ROBIN,
    GROUPS_KNOCKOUT,
    KNOCKOUT,
}

enum class FixtureStage { LEAGUE, POOL, KNOCKOUT }

enum class FixtureStatus {
    /** Both sides known, not yet played. */
    PENDING,

    /** A knockout slot whose occupant depends on a result that hasn't happened yet. */
    AWAITING_TEAMS,

    IN_PROGRESS,
    COMPLETED,

    /** Nobody to play: an odd team count in a round robin, or a bye in a bracket. */
    BYE,
}

/** A team, as fixture generation needs it: an identity, a seed, and a pool. */
data class TeamRef(
    val teamId: String,
    val seed: Int,
    val name: String,
    val poolOrdinal: Int = 0,
)

/**
 * Where a fixture's occupant comes from, when it isn't known yet.
 *
 * A knockout bracket has to exist before its semi-finalists do, so the empty slots carry a
 * reference instead of a team id, and are filled in as results arrive.
 */
sealed interface FixtureSource {
    /** The winner of an earlier fixture in this bracket. */
    data class WinnerOf(val round: Int, val slot: Int) : FixtureSource

    /** A position in a pool's table — resolved only once that pool has finished. */
    data class PoolPosition(val poolOrdinal: Int, val position: Int) : FixtureSource
}

/**
 * A fixture as planned, before it has an id or a row.
 *
 * `round` and `slot` place it in the schedule; `leg` distinguishes the two halves of a double round
 * robin. Together with the stage and pool they make a fixture's identity deterministic, which is
 * what lets the same tournament be uploaded repeatedly without duplicating anything.
 */
data class PlannedFixture(
    val stage: FixtureStage,
    val poolOrdinal: Int,
    val round: Int,
    val slot: Int,
    val leg: Int,
    val homeTeamId: String?,
    val awayTeamId: String?,
    val homeSource: FixtureSource? = null,
    val awaySource: FixtureSource? = null,
    val label: String,
    val isBye: Boolean = false,
) {
    val status: FixtureStatus
        get() = when {
            isBye -> FixtureStatus.BYE
            homeTeamId != null && awayTeamId != null -> FixtureStatus.PENDING
            else -> FixtureStatus.AWAITING_TEAMS
        }
}

/**
 * Encodes a [FixtureSource] as a short string, so a fixture row can hold one without a second
 * table.
 *
 * `"W:2:0"` is the winner of round 2, slot 0; `"P:1:2"` is second in pool 1. Decoding never throws
 * — an unreadable reference reads as "not known yet", which is also what it means.
 */
fun encodeFixtureSource(source: FixtureSource): String = when (source) {
    is FixtureSource.WinnerOf -> "W:${source.round}:${source.slot}"
    is FixtureSource.PoolPosition -> "P:${source.poolOrdinal}:${source.position}"
}

fun decodeFixtureSource(encoded: String?): FixtureSource? {
    val parts = encoded?.split(":") ?: return null
    if (parts.size != 3) return null
    val a = parts[1].toIntOrNull() ?: return null
    val b = parts[2].toIntOrNull() ?: return null
    return when (parts[0]) {
        "W" -> FixtureSource.WinnerOf(round = a, slot = b)
        "P" -> FixtureSource.PoolPosition(poolOrdinal = a, position = b)
        else -> null
    }
}

/** Pool 1 is "A". Pools are an integer plus a letter; they need no identity of their own. */
fun poolName(poolOrdinal: Int): String =
    if (poolOrdinal <= 0) "" else ('A' + (poolOrdinal - 1)).toString()
