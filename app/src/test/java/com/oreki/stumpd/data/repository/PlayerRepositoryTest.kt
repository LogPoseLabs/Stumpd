package com.oreki.stumpd.data.repository

import com.oreki.stumpd.*
import com.oreki.stumpd.data.local.dao.MatchDao
import com.oreki.stumpd.data.local.dao.PlayerDao
import com.oreki.stumpd.data.local.db.StumpdDb
import com.oreki.stumpd.data.local.entity.MatchEntity
import com.oreki.stumpd.data.local.entity.PlayerEntity
import com.oreki.stumpd.data.local.entity.PlayerMatchStatsEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerRepositoryTest {

    private lateinit var db: StumpdDb
    private lateinit var playerDao: PlayerDao
    private lateinit var matchDao: MatchDao
    private lateinit var repository: PlayerRepository

    @Before
    fun setup() {
        db = mockk(relaxed = true)
        playerDao = mockk(relaxed = true)
        matchDao = mockk(relaxed = true)

        every { db.playerDao() } returns playerDao
        every { db.matchDao() } returns matchDao

        repository = PlayerRepository(db)
    }

    @Test
    fun `getAllPlayers returns all players from database`() = runTest {
        // Given
        val expectedPlayers = listOf(
            PlayerEntity(id = "1", name = "Player 1", isJoker = false),
            PlayerEntity(id = "2", name = "Player 2", isJoker = false),
            PlayerEntity(id = "3", name = "Joker", isJoker = true)
        )
        coEvery { playerDao.list() } returns expectedPlayers

        // When
        val result = repository.getAllPlayers()

        // Then
        assertEquals(expectedPlayers, result)
        coVerify { playerDao.list() }
    }

    @Test
    fun `getAllPlayers returns empty list when no players exist`() = runTest {
        // Given
        coEvery { playerDao.list() } returns emptyList()

        // When
        val result = repository.getAllPlayers()

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `addOrUpdatePlayer creates new player with trimmed name`() = runTest {
        // Given
        val playerName = "  John Doe  "
        val expectedTrimmedName = "John Doe"

        // When
        val result = repository.addOrUpdatePlayer(playerName)

        // Then
        assertEquals(expectedTrimmedName, result.name)
        assertFalse(result.isJoker)
        assertNotNull(result.id)
        coVerify { playerDao.upsert(match { it.size == 1 && it[0].name == expectedTrimmedName }) }
    }

    @Test
    fun `addOrUpdatePlayer generates unique ID for each player`() = runTest {
        // When
        val player1 = repository.addOrUpdatePlayer("Player 1")
        val player2 = repository.addOrUpdatePlayer("Player 2")

        // Then
        assertNotEquals(player1.id, player2.id)
    }

    @Test
    fun `getPlayerDetailedStats returns empty list when no matches provided`() = runTest {
        // When
        val result = repository.getPlayerDetailedStats(emptyList())

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getPlayerDetailedStats calculates correct totals for single match`() = runTest {
        // Given
        val playerId = "player1"
        val matchId = "match1"
        val matchDate = System.currentTimeMillis()

        val statsEntity = PlayerMatchStatsEntity(
            matchId = matchId,
            playerId = playerId,
            name = "John Doe",
            team = "Team A",
            runs = 50,
            ballsFaced = 30,
            fours = 4,
            sixes = 2,
            wickets = 2,
            runsConceded = 25,
            oversBowled = 2.0,
            isOut = true,
            isJoker = false
        )

        val match = MatchHistory(
            id = matchId,
            team1Name = "Team A",
            team2Name = "Team B",
            firstInningsRuns = 100,
            firstInningsWickets = 5,
            secondInningsRuns = 95,
            secondInningsWickets = 10,
            winnerTeam = "Team A",
            winningMargin = "5 runs",
            matchDate = matchDate,
            firstInningsBatting = listOf(statsEntity.toPlayerMatchStats())
        )

        coEvery { matchDao.statsForMatch(matchId) } returns listOf(statsEntity)

        // When
        val result = repository.getPlayerDetailedStats(listOf(match))

        // Then
        assertEquals(1, result.size)
        val playerStats = result[0]
        assertEquals("john doe", playerStats.playerId)
        assertEquals("John Doe", playerStats.name)
        assertEquals(50, playerStats.totalRuns)
        assertEquals(30, playerStats.totalBallsFaced)
        assertEquals(4, playerStats.totalFours)
        assertEquals(2, playerStats.totalSixes)
        assertEquals(2, playerStats.totalWickets)
        assertEquals(25, playerStats.totalRunsConceded)
        assertEquals(12, playerStats.totalBallsBowled)
        assertEquals(1, playerStats.timesOut)
        assertEquals(0, playerStats.notOuts)
        assertEquals(1, playerStats.totalMatches)
        assertEquals(1, playerStats.matchPerformances.size)
    }

    @Test
    fun `getPlayerDetailedStats aggregates multiple matches correctly`() = runTest {
        // Given
        val playerId = "player1"
        val playerName = "John Doe"

        val match1 = MatchHistory(
            id = "match1",
            team1Name = "Team A",
            team2Name = "Team B",
            firstInningsRuns = 100,
            firstInningsWickets = 5,
            secondInningsRuns = 95,
            secondInningsWickets = 10,
            winnerTeam = "Team A",
            winningMargin = "5 runs",
            matchDate = System.currentTimeMillis(),
            firstInningsBatting = listOf(
                PlayerMatchStats(
                    id = playerId,
                    name = playerName,
                    team = "Team A",
                    runs = 50,
                    ballsFaced = 30,
                    fours = 4,
                    sixes = 2,
                    wickets = 0,
                    runsConceded = 0,
                    oversBowled = 0.0,
                    isOut = true,
                    isJoker = false
                )
            )
        )

        val match2 = MatchHistory(
            id = "match2",
            team1Name = "Team A",
            team2Name = "Team C",
            firstInningsRuns = 120,
            firstInningsWickets = 3,
            secondInningsRuns = 115,
            secondInningsWickets = 10,
            winnerTeam = "Team A",
            winningMargin = "7 wickets",
            matchDate = System.currentTimeMillis() + 1000,
            firstInningsBatting = listOf(
                PlayerMatchStats(
                    id = playerId,
                    name = playerName,
                    team = "Team A",
                    runs = 75,
                    ballsFaced = 40,
                    fours = 6,
                    sixes = 3,
                    wickets = 0,
                    runsConceded = 0,
                    oversBowled = 0.0,
                    isOut = false,
                    isJoker = false
                )
            )
        )

        // When
        val result = repository.getPlayerDetailedStats(listOf(match1, match2))

        // Then
        assertEquals(1, result.size)
        val playerStats = result[0]
        assertEquals(125, playerStats.totalRuns) // 50 + 75
        assertEquals(70, playerStats.totalBallsFaced) // 30 + 40
        assertEquals(10, playerStats.totalFours) // 4 + 6
        assertEquals(5, playerStats.totalSixes) // 2 + 3
        assertEquals(1, playerStats.timesOut)
        assertEquals(1, playerStats.notOuts)
        assertEquals(2, playerStats.totalMatches)
        assertEquals(2, playerStats.matchPerformances.size)
    }

    @Test
    fun `getPlayerDetailedStats handles joker player correctly`() = runTest {
        // Given
        val playerId = "joker1"
        val matchId = "match1"

        val statsEntity1 = PlayerMatchStatsEntity(
            matchId = matchId,
            playerId = playerId,
            name = "Joker",
            team = "Team A",
            runs = 30,
            ballsFaced = 20,
            fours = 2,
            sixes = 1,
            wickets = 1,
            runsConceded = 15,
            oversBowled = 1.0,
            isOut = false,
            isJoker = true
        )

        val statsEntity2 = PlayerMatchStatsEntity(
            matchId = matchId,
            playerId = playerId,
            name = "Joker",
            team = "Team B",
            runs = 25,
            ballsFaced = 15,
            fours = 1,
            sixes = 2,
            wickets = 2,
            runsConceded = 20,
            oversBowled = 1.0,
            isOut = true,
            isJoker = true
        )

        val match = MatchHistory(
            id = matchId,
            team1Name = "Team A",
            team2Name = "Team B",
            firstInningsRuns = 100,
            firstInningsWickets = 5,
            secondInningsRuns = 95,
            secondInningsWickets = 10,
            winnerTeam = "Team A",
            winningMargin = "5 runs",
            matchDate = System.currentTimeMillis(),
            firstInningsBatting = listOf(statsEntity1.toPlayerMatchStats(), statsEntity2.toPlayerMatchStats())
        )

        coEvery { matchDao.statsForMatch(matchId) } returns listOf(statsEntity1, statsEntity2)

        // When
        val result = repository.getPlayerDetailedStats(listOf(match))

        // Then
        assertEquals(1, result.size)
        val jokerStats = result[0]
        assertEquals(55, jokerStats.totalRuns) // 30 + 25
        assertEquals(35, jokerStats.totalBallsFaced) // 20 + 15
        assertEquals(3, jokerStats.totalWickets) // 1 + 2
        // Should have 2 match performances (one for each team)
        assertEquals(2, jokerStats.matchPerformances.size)
    }

    @Test
    fun `getPlayerDetailedStats filters by playerId when provided`() = runTest {
        // Given
        val targetPlayerId = "player1"

        val match = MatchHistory(
            id = "match1",
            team1Name = "Team A",
            team2Name = "Team B",
            firstInningsRuns = 100,
            firstInningsWickets = 5,
            secondInningsRuns = 95,
            secondInningsWickets = 10,
            winnerTeam = "Team A",
            winningMargin = "5 runs",
            matchDate = System.currentTimeMillis(),
            firstInningsBatting = listOf(
                PlayerMatchStats(
                    id = targetPlayerId,
                    name = "Player 1",
                    team = "Team A",
                    runs = 50,
                    ballsFaced = 30,
                    isOut = false,
                    isJoker = false
                ),
                PlayerMatchStats(
                    id = "player2",
                    name = "Player 2",
                    team = "Team A",
                    runs = 40,
                    ballsFaced = 25,
                    isOut = true,
                    isJoker = false
                )
            )
        )

        // When
        // Note: getPlayerDetailedStats uses player name (lowercase) as ID, not the actual playerId
        val result = repository.getPlayerDetailedStats(listOf(match), "player 1")

        // Then
        assertEquals(1, result.size)
        assertEquals("player 1", result[0].playerId) // playerId is actually the lowercase name
    }

    @Test
    fun `getPlayerDetailedStats handles bowling statistics correctly`() = runTest {
        // Given
        val playerId = "bowler1"
        val matchId = "match1"

        val statsEntity = PlayerMatchStatsEntity(
            matchId = matchId,
            playerId = playerId,
            name = "Bowler",
            team = "Team A",
            runs = 0,
            ballsFaced = 0,
            fours = 0,
            sixes = 0,
            wickets = 3,
            runsConceded = 30,
            oversBowled = 3.2, // 3 overs and 2 balls
            isOut = false,
            isJoker = false
        )

        val match = MatchHistory(
            id = matchId,
            team1Name = "Team A",
            team2Name = "Team B",
            firstInningsRuns = 100,
            firstInningsWickets = 5,
            secondInningsRuns = 95,
            secondInningsWickets = 10,
            winnerTeam = "Team A",
            winningMargin = "5 runs",
            matchDate = System.currentTimeMillis(),
            firstInningsBatting = listOf(statsEntity.toPlayerMatchStats())
        )

        coEvery { matchDao.statsForMatch(matchId) } returns listOf(statsEntity)

        // When
        val result = repository.getPlayerDetailedStats(listOf(match))

        // Then
        assertEquals(1, result.size)
        val bowlerStats = result[0]
        assertEquals(3, bowlerStats.totalWickets)
        assertEquals(30, bowlerStats.totalRunsConceded)
        // Note: Implementation multiplies oversBowled by 6: 3.2 * 6 = 19.2, toInt() = 19
        assertEquals(19, bowlerStats.totalBallsBowled)
        assertEquals(0, bowlerStats.timesOut)
        assertEquals(1, bowlerStats.notOuts)
    }

    @Test
    fun `getPlayerDetailedStats uses database stats when firstInningsBatting is empty`() = runTest {
        // Given
        val matchId = "match1"
        val statsEntity = PlayerMatchStatsEntity(
            matchId = matchId,
            playerId = "player1",
            name = "Player 1",
            team = "Team A",
            runs = 50,
            ballsFaced = 30,
            fours = 4,
            sixes = 2,
            wickets = 0,
            runsConceded = 0,
            oversBowled = 0.0,
            isOut = true,
            isJoker = false
        )

        val match = MatchHistory(
            id = matchId,
            team1Name = "Team A",
            team2Name = "Team B",
            firstInningsRuns = 100,
            firstInningsWickets = 5,
            secondInningsRuns = 95,
            secondInningsWickets = 10,
            winnerTeam = "Team A",
            winningMargin = "5 runs",
            matchDate = System.currentTimeMillis(),
            firstInningsBatting = emptyList() // Empty, should fetch from DB
        )

        coEvery { matchDao.statsForMatch(matchId) } returns listOf(statsEntity)

        // When
        val result = repository.getPlayerDetailedStats(listOf(match))

        // Then
        assertEquals(1, result.size)
        coVerify { matchDao.statsForMatch(matchId) }
    }

    // Helper extension function for tests
    private fun PlayerMatchStatsEntity.toPlayerMatchStats(): PlayerMatchStats {
        return PlayerMatchStats(
            id = this.playerId,
            name = this.name,
            team = this.team,
            runs = this.runs,
            ballsFaced = this.ballsFaced,
            fours = this.fours,
            sixes = this.sixes,
            wickets = this.wickets,
            runsConceded = this.runsConceded,
            oversBowled = this.oversBowled,
            isOut = this.isOut,
            isJoker = this.isJoker
        )
    }
}

