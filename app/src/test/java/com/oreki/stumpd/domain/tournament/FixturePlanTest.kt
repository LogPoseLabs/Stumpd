package com.oreki.stumpd.domain.tournament

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Fixture generation for the four formats.
 *
 * The properties worth holding are structural rather than cosmetic: everybody plays everybody the
 * right number of times, nobody plays twice in a round, byes go to the right teams, and the two
 * best seeds cannot meet before the final. Those are the things a hand-written schedule gets wrong.
 */
class FixturePlanTest {

    private fun teams(n: Int) = (1..n).map {
        TeamRef(teamId = "t$it", seed = it, name = "Team $it")
    }

    private fun pairsOf(fixtures: List<PlannedFixture>) =
        fixtures.filterNot { it.isBye }.map { setOfNotNull(it.homeTeamId, it.awayTeamId) }

    // ── Single round robin ──────────────────────────────────────────────────────────────

    @Test
    fun `an even field plays everyone once, in the right number of rounds`() {
        val fixtures = planSingleRoundRobin(teams(6))

        // Six teams: fifteen matches across five rounds of three.
        assertThat(fixtures).hasSize(15)
        assertThat(fixtures.map { it.round }.distinct()).hasSize(5)
        assertThat(pairsOf(fixtures).distinct()).hasSize(15)
        assertThat(fixtures.none { it.isBye }).isTrue()
    }

    @Test
    fun `nobody plays twice in the same round`() {
        val fixtures = planSingleRoundRobin(teams(6))

        fixtures.groupBy { it.round }.forEach { (_, round) ->
            val appearances = round.flatMap { listOfNotNull(it.homeTeamId, it.awayTeamId) }
            assertThat(appearances).containsNoDuplicates()
        }
    }

    @Test
    fun `an odd field gives each team exactly one bye`() {
        val fixtures = planSingleRoundRobin(teams(5))

        // Five teams: ten real matches, and five byes — one each.
        assertThat(fixtures.count { !it.isBye }).isEqualTo(10)
        val byesPerTeam = fixtures.filter { it.isBye }.groupingBy { it.homeTeamId }.eachCount()
        assertThat(byesPerTeam.values.distinct()).containsExactly(1)
        assertThat(byesPerTeam).hasSize(5)
    }

    @Test
    fun `two teams is a single match, and one team is no tournament`() {
        assertThat(planSingleRoundRobin(teams(2))).hasSize(1)
        assertThat(planSingleRoundRobin(teams(1))).isEmpty()
    }

    // ── Double round robin ──────────────────────────────────────────────────────────────

    @Test
    fun `a double round robin plays everyone twice, with the sides reversed`() {
        val fixtures = planDoubleRoundRobin(teams(4))

        assertThat(fixtures.count { !it.isBye }).isEqualTo(12)
        assertThat(fixtures.map { it.leg }.distinct()).containsExactly(1, 2)

        // Each pairing appears exactly twice, once each way round.
        val ordered = fixtures.filterNot { it.isBye }.map { it.homeTeamId to it.awayTeamId }
        ordered.forEach { (home, away) ->
            assertThat(ordered).contains(away to home)
        }
        assertThat(pairsOf(fixtures).distinct()).hasSize(6)
    }

    @Test
    fun `match numbers run on into the second leg rather than restarting`() {
        val labels = planDoubleRoundRobin(teams(4)).filterNot { it.isBye }.map { it.label }

        assertThat(labels).contains("Match 1")
        assertThat(labels).contains("Match 12")
        assertThat(labels.distinct()).hasSize(12)
    }

    // ── Knockout ────────────────────────────────────────────────────────────────────────

    @Test
    fun `a full bracket pairs best against worst and needs no byes`() {
        val fixtures = planKnockout(teams(4))

        val firstRound = fixtures.filter { it.round == 1 }
        assertThat(firstRound).hasSize(2)
        assertThat(firstRound[0].homeTeamId to firstRound[0].awayTeamId).isEqualTo("t1" to "t4")
        assertThat(firstRound[1].homeTeamId to firstRound[1].awayTeamId).isEqualTo("t2" to "t3")
        assertThat(fixtures.none { it.isBye }).isTrue()
        assertThat(fixtures.single { it.round == 2 }.label).isEqualTo("Final")
    }

    @Test
    fun `an awkward field gives the byes to the top seeds`() {
        // Five teams in an eight-slot bracket: three byes, and they go to seeds 1, 2 and 3.
        val fixtures = planKnockout(teams(5))

        val byes = fixtures.filter { it.isBye }.map { it.homeTeamId }
        assertThat(byes).containsExactly("t1", "t2", "t3")
        // Seed 4 plays seed 5; nobody else has an opponent in round one.
        val contested = fixtures.filter { it.round == 1 && !it.isBye }
        assertThat(contested).hasSize(1)
        assertThat(setOfNotNull(contested.single().homeTeamId, contested.single().awayTeamId))
            .containsExactly("t4", "t5")
    }

    @Test
    fun `later rounds reference the winners of earlier ones`() {
        val fixtures = planKnockout(teams(4))
        val final = fixtures.single { it.round == 2 }

        assertThat(final.homeTeamId).isNull()
        assertThat(final.homeSource).isEqualTo(FixtureSource.WinnerOf(1, 0))
        assertThat(final.awaySource).isEqualTo(FixtureSource.WinnerOf(1, 1))
        assertThat(final.status).isEqualTo(FixtureStatus.AWAITING_TEAMS)
    }

    @Test
    fun `rounds are named from the final backwards`() {
        val labels = planKnockout(teams(8)).map { it.label }

        assertThat(labels).contains("Final")
        assertThat(labels).contains("Semi-final 1")
        assertThat(labels).contains("Quarter-final 1")
    }

    // ── Pools, then a knockout ──────────────────────────────────────────────────────────

    @Test
    fun `pools are snaked so they are of comparable strength`() {
        val pools = allocatePools(teams(6), poolCount = 2)

        // 1→A, 2→B, 3→B, 4→A, 5→A, 6→B: each pool gets one of the top two, not both.
        assertThat(pools.getValue(1).map { it.teamId }).containsExactly("t1", "t4", "t5")
        assertThat(pools.getValue(2).map { it.teamId }).containsExactly("t2", "t3", "t6")
        assertThat(pools.getValue(1).map { it.poolOrdinal }.distinct()).containsExactly(1)
    }

    @Test
    fun `a groups-and-knockout tournament plays its pools then seeds the semis across them`() {
        val fixtures = planGroupsAndKnockout(teams(6), poolCount = 2, advancePerPool = 2)

        // Two pools of three: three real matches each, and — an odd pool — one bye per team.
        val pool = fixtures.filter { it.stage == FixtureStage.POOL }
        assertThat(pool.count { !it.isBye }).isEqualTo(6)
        assertThat(pool.count { it.isBye }).isEqualTo(6)
        assertThat(pool.map { it.poolOrdinal }.distinct()).containsExactly(1, 2)

        val knockout = fixtures.filter { it.stage == FixtureStage.KNOCKOUT }
        assertThat(knockout).hasSize(3) // two semi-finals and a final
        val semis = knockout.filter { it.round == 1 }
        // A1 v B2 and B1 v A2 — the pool winners are kept apart.
        assertThat(semis[0].homeSource).isEqualTo(FixtureSource.PoolPosition(1, 1))
        assertThat(semis[0].awaySource).isEqualTo(FixtureSource.PoolPosition(2, 2))
        assertThat(semis[1].homeSource).isEqualTo(FixtureSource.PoolPosition(2, 1))
        assertThat(semis[1].awaySource).isEqualTo(FixtureSource.PoolPosition(1, 2))
        assertThat(knockout.single { it.round == 2 }.label).isEqualTo("Final")
    }

    @Test
    fun `every fixture in a plan is uniquely addressable`() {
        // Identity has to be deterministic, because it becomes the cloud document id.
        TournamentFormat.entries.forEach { format ->
            val fixtures = planFixtures(format, teams(6), poolCount = 2, advancePerPool = 2)
            val keys = fixtures.map { listOf(it.stage, it.poolOrdinal, it.round, it.slot, it.leg) }
            assertThat(keys).containsNoDuplicates()
        }
    }

    // ── Source references ───────────────────────────────────────────────────────────────

    @Test
    fun `fixture sources survive a round trip, and rubbish decodes to nothing`() {
        val winner: FixtureSource = FixtureSource.WinnerOf(2, 3)
        val pool: FixtureSource = FixtureSource.PoolPosition(1, 2)

        assertThat(decodeFixtureSource(encodeFixtureSource(winner))).isEqualTo(winner)
        assertThat(decodeFixtureSource(encodeFixtureSource(pool))).isEqualTo(pool)
        assertThat(decodeFixtureSource(null)).isNull()
        assertThat(decodeFixtureSource("")).isNull()
        assertThat(decodeFixtureSource("X:1:2")).isNull()
        assertThat(decodeFixtureSource("W:one:2")).isNull()
    }

    @Test
    fun `pools are named with letters`() {
        assertThat(poolName(1)).isEqualTo("A")
        assertThat(poolName(2)).isEqualTo("B")
        assertThat(poolName(0)).isEmpty()
    }
}
