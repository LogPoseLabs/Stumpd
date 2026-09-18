package com.oreki.stumpd.domain.tournament

/**
 * Filling in a bracket as results arrive.
 *
 * Both functions are idempotent and take the whole schedule, so they can be run on every read
 * rather than hooked onto the moment a match finishes. That matters more than it sounds: a
 * correction weeks later can change who won a semi-final, and a bracket derived on read simply
 * follows, where one advanced by a callback would have to remember.
 */

/** A fixture as the schedule holds it: a plan, plus whatever has happened to it. */
data class TournamentFixture(
    val fixtureId: String,
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
    val status: FixtureStatus,
    val matchId: String? = null,
    val winnerTeamId: String? = null,
)

/**
 * Resolves slots whose occupant is now known.
 *
 * A [FixtureSource.WinnerOf] resolves as soon as the feeding fixture has a winner. A
 * [FixtureSource.PoolPosition] waits for its *whole* pool — seeding a semi-final off a table that
 * is still moving would be worse than leaving it blank.
 *
 * Returns the full schedule with whatever could be filled in filled in.
 */
fun resolveFixtureSlots(
    fixtures: List<TournamentFixture>,
    standingsByPool: Map<Int, List<StandingsRow>>,
    completedPools: Set<Int>,
): List<TournamentFixture> {
    val winners = fixtures
        .filter { it.winnerTeamId != null }
        .associate { (it.round to it.slot) to it.winnerTeamId }

    // A bye's occupant carries through as its winner, so the next round can see it.
    val byeWinners = fixtures
        .filter { it.status == FixtureStatus.BYE && it.homeTeamId != null }
        .associate { (it.round to it.slot) to it.homeTeamId }

    fun occupantOf(source: FixtureSource?): String? = when (source) {
        null -> null
        is FixtureSource.WinnerOf ->
            winners[source.round to source.slot] ?: byeWinners[source.round to source.slot]

        is FixtureSource.PoolPosition ->
            if (source.poolOrdinal !in completedPools) {
                null
            } else {
                standingsByPool[source.poolOrdinal]?.getOrNull(source.position - 1)?.teamId
            }
    }

    return fixtures.map { fixture ->
        if (fixture.homeTeamId != null && fixture.awayTeamId != null) return@map fixture
        val home = fixture.homeTeamId ?: occupantOf(fixture.homeSource)
        val away = fixture.awayTeamId ?: occupantOf(fixture.awaySource)
        val status = when {
            fixture.status == FixtureStatus.COMPLETED || fixture.status == FixtureStatus.BYE ->
                fixture.status

            home != null && away != null -> FixtureStatus.PENDING
            else -> FixtureStatus.AWAITING_TEAMS
        }
        fixture.copy(homeTeamId = home, awayTeamId = away, status = status)
    }
}

/**
 * Completes any fixture that has nobody to play.
 *
 * A bye isn't a match, so it can't be "played" — but the next round still needs its occupant, so it
 * is marked complete with its lone team as the winner.
 */
fun autoAdvanceByes(fixtures: List<TournamentFixture>): List<TournamentFixture> =
    fixtures.map { fixture ->
        val lone = when {
            fixture.homeTeamId != null && fixture.awayTeamId == null -> fixture.homeTeamId
            fixture.awayTeamId != null && fixture.homeTeamId == null -> fixture.awayTeamId
            else -> null
        }
        // Only in a knockout: a round-robin bye is just a round off, with nothing to advance to.
        if (lone != null && fixture.stage == FixtureStage.KNOCKOUT &&
            fixture.status != FixtureStatus.COMPLETED && fixture.homeSource == null &&
            fixture.awaySource == null
        ) {
            fixture.copy(status = FixtureStatus.BYE, winnerTeamId = lone)
        } else {
            fixture
        }
    }

/** True once every fixture that can be played has been. */
fun tournamentIsComplete(fixtures: List<TournamentFixture>): Boolean =
    fixtures.isNotEmpty() && fixtures.all {
        it.status == FixtureStatus.COMPLETED || it.status == FixtureStatus.BYE
    }
