package com.oreki.stumpd.data.repository

import com.google.common.truth.Truth.assertThat
import com.oreki.stumpd.data.local.dao.MatchDao
import com.oreki.stumpd.data.local.db.StumpdDb
import com.oreki.stumpd.data.local.entity.PlayerMatchStatsEntity
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.model.PlayerMatchStats
import androidx.room.withTransaction
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * A bowler taken off an over must stay off it.
 *
 * This is a regression test for a real corruption. Reassigning an over removes the previous bowler
 * from the innings' bowling list — and the write path used to read that absence as *missing* data
 * and helpfully rebuild the row from the player's batting entry, which still carried a copy of
 * their pre-correction bowling figures. So the removal was undone on save, and the next correction
 * added to the resurrected figures: a bowler who had actually sent down three balls finished with
 * nine (1.3 overs), and his team-mate the same.
 *
 * The asymmetry is what made it hard to see. Only the side that bowled the *first* innings was
 * affected, because their batting rows are snapshotted at the end of the match — by which time
 * they have bowled — whereas the side batting first has batting rows snapshotted at the innings
 * break, before they bowl at all.
 */
@RunWith(RobolectricTestRunner::class)
class ReassignedBowlerPersistenceTest {

    private lateinit var db: StumpdDb
    private lateinit var matchDao: MatchDao
    private lateinit var repository: MatchRepository

    @Before
    fun setup() {
        db = mockk(relaxed = true)
        matchDao = mockk(relaxed = true)
        every { db.matchDao() } returns matchDao
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { db.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            runBlocking { secondArg<suspend () -> Any?>()() }
        }
        repository = MatchRepository(db, RuntimeEnvironment.getApplication(), UnconfinedTestDispatcher())
    }

    /**
     * The shape the live scorer produces: a player who bowled in the first innings and batted in
     * the second has *both* sets of figures on their batting row, because the row is a snapshot of
     * the whole player.
     */
    private fun match(bowling: List<PlayerMatchStats>) = MatchHistory(
        id = "m1",
        team1Name = "Prasad's Team",
        team2Name = "Prashanth's Team",
        firstInningsRuns = 21,
        firstInningsWickets = 3,
        secondInningsRuns = 18,
        secondInningsWickets = 4,
        winnerTeam = "Prasad's Team",
        winningMargin = "3 runs",
        firstInningsBatting = listOf(
            PlayerMatchStats(id = "kushal", name = "Kushal", team = "Prasad's Team", role = "BAT", runs = 10, ballsFaced = 12),
        ),
        firstInningsBowling = bowling,
        secondInningsBatting = listOf(
            // Shashi batted here *and* bowled in the first innings, so his batting row carries a
            // copy of his bowling figures. That copy is what used to resurrect him.
            PlayerMatchStats(
                id = "shashi", name = "Shashi", team = "Prashanth's Team", role = "BAT",
                runs = 2, ballsFaced = 4, oversBowled = 1.0, runsConceded = 3, wickets = 2,
            ),
            PlayerMatchStats(
                id = "bharath", name = "Bharath", team = "Prashanth's Team", role = "BAT",
                runs = 1, ballsFaced = 1,
            ),
        ),
        secondInningsBowling = listOf(
            PlayerMatchStats(id = "kushal", name = "Kushal", team = "Prasad's Team", role = "BOWL", oversBowled = 1.0, runsConceded = 1),
        ),
    )

    private suspend fun savedStats(match: MatchHistory): List<PlayerMatchStatsEntity> {
        val captured = slot<List<PlayerMatchStatsEntity>>()
        coEvery { matchDao.replaceFullMatch(any(), capture(captured), any()) } returns Unit
        repository.replaceMatchGraph(match)
        return captured.captured
    }

    @Test
    fun `a bowler removed by a correction is not rebuilt from their batting row`() = runTest {
        // The state after reassigning Shashi's over to Bharath: Shashi is gone from the bowling
        // list on purpose, and his batting row still carries the old figures.
        val afterCorrection = match(
            bowling = listOf(
                PlayerMatchStats(
                    id = "bharath", name = "Bharath", team = "Prashanth's Team", role = "BOWL",
                    oversBowled = 1.0, runsConceded = 3, wickets = 2,
                ),
            ),
        )

        val stats = savedStats(afterCorrection)

        val shashiBowling = stats.filter { it.name == "Shashi" && it.role == "BOWL" }
        assertThat(shashiBowling).isEmpty()
        assertThat(stats.single { it.name == "Bharath" && it.role == "BOWL" }.oversBowled)
            .isWithin(0.001).of(1.0)
    }

    @Test
    fun `the innings' bowling figures are exactly the ones the correction left behind`() = runTest {
        // The specific failure: three balls became nine, because a resurrected 1.0 was added to.
        val afterCorrection = match(
            bowling = listOf(
                PlayerMatchStats(
                    id = "shashi", name = "Shashi", team = "Prashanth's Team", role = "BOWL",
                    oversBowled = 0.3, runsConceded = 1, wickets = 1,
                ),
            ),
        )

        val stats = savedStats(afterCorrection)

        val bowlRows = stats.filter { it.role == "BOWL" && it.team == "Prashanth's Team" }
        assertThat(bowlRows.map { it.name }).containsExactly("Shashi")
        assertThat(bowlRows.single().oversBowled).isWithin(0.001).of(0.3)
    }

    @Test
    fun `a legacy match with no bowling lists at all still has its figures rescued`() = runTest {
        // The case the rescue exists for: an old backup where the two roles were merged, so there
        // is no bowling list to read absence from.
        val legacy = match(bowling = emptyList()).copy(secondInningsBowling = emptyList())

        val stats = savedStats(legacy)

        val rescued = stats.single { it.name == "Shashi" && it.role == "BOWL" }
        assertThat(rescued.oversBowled).isWithin(0.001).of(1.0)
        assertThat(rescued.runsConceded).isEqualTo(3)
    }

    @Test
    fun `nobody is invented for a batting row that carries no bowling figures`() = runTest {
        val legacy = match(bowling = emptyList()).copy(secondInningsBowling = emptyList())

        val stats = savedStats(legacy)

        assertThat(stats.none { it.name == "Bharath" && it.role == "BOWL" }).isTrue()
    }

    @Test
    fun `an ordinary saved match keeps one row per player per role`() = runTest {
        val ordinary = match(
            bowling = listOf(
                PlayerMatchStats(id = "shashi", name = "Shashi", team = "Prashanth's Team", role = "BOWL", oversBowled = 1.0, runsConceded = 3, wickets = 2),
            ),
        )

        val stats = savedStats(ordinary)

        val keys = stats.map { Triple(it.playerId, it.team, it.role) }
        assertThat(keys).containsNoDuplicates()
    }
}
