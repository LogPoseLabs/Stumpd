package com.oreki.stumpd.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.oreki.stumpd.data.local.db.StumpdDb
import com.oreki.stumpd.data.local.entity.MatchEntity
import com.oreki.stumpd.data.local.entity.PlayerImpactEntity
import com.oreki.stumpd.data.local.entity.PlayerMatchStatsEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchDaoTest {

    private lateinit var database: StumpdDb
    private lateinit var matchDao: MatchDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            StumpdDb::class.java
        ).build()
        matchDao = database.matchDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertMatch_insertsSuccessfully() = runTest {
        // Given
        val match = createTestMatch("match1")

        // When
        matchDao.insertMatch(match)

        // Then
        val result = matchDao.getById("match1")
        assertNotNull(result)
        assertEquals("Team A", result?.team1Name)
        assertEquals("Team B", result?.team2Name)
    }

    @Test
    fun insertMatch_replacesOnConflict() = runTest {
        // Given
        val match1 = createTestMatch("match1", team1 = "Team A", team2 = "Team B")
        matchDao.insertMatch(match1)

        // When
        val match2 = createTestMatch("match1", team1 = "Team X", team2 = "Team Y")
        matchDao.insertMatch(match2)

        // Then
        val result = matchDao.getById("match1")
        assertEquals("Team X", result?.team1Name)
        assertEquals("Team Y", result?.team2Name)
    }

    @Test
    fun insertFullMatch_insertsMatchWithStatsAndImpacts() = runTest {
        // Given
        val match = createTestMatch("match1")
        val stats = listOf(
            PlayerMatchStatsEntity(
                matchId = "match1",
                playerId = "p1",
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
        )
        val impacts = listOf(
            PlayerImpactEntity(
                matchId = "match1",
                playerId = "p1",
                name = "Player 1",
                team = "Team A",
                impact = 45.5,
                summary = "Great batting",
                isJoker = false,
                runs = 50,
                balls = 30,
                fours = 4,
                sixes = 2,
                wickets = 0,
                runsConceded = 0,
                oversBowled = 0.0
            )
        )

        // When
        matchDao.insertFullMatch(match, stats, impacts)

        // Then
        val savedMatch = matchDao.getById("match1")
        val savedStats = matchDao.statsForMatch("match1")
        val savedImpacts = matchDao.impactsForMatch("match1")

        assertNotNull(savedMatch)
        assertEquals(1, savedStats.size)
        assertEquals(1, savedImpacts.size)
        assertEquals(50, savedStats[0].runs)
        assertEquals(45.5, savedImpacts[0].impact, 0.01)
    }

    @Test
    fun insertFullMatch_handlesEmptyStatsAndImpacts() = runTest {
        // Given
        val match = createTestMatch("match1")

        // When
        matchDao.insertFullMatch(match, emptyList(), emptyList())

        // Then
        val savedMatch = matchDao.getById("match1")
        val savedStats = matchDao.statsForMatch("match1")
        val savedImpacts = matchDao.impactsForMatch("match1")

        assertNotNull(savedMatch)
        assertTrue(savedStats.isEmpty())
        assertTrue(savedImpacts.isEmpty())
    }

    @Test
    fun list_returnsAllMatchesSortedByDateDesc() = runTest {
        // Given
        val match1 = createTestMatch("match1", matchDate = 1000L)
        val match2 = createTestMatch("match2", matchDate = 2000L)
        val match3 = createTestMatch("match3", matchDate = 1500L)

        matchDao.insertMatch(match1)
        matchDao.insertMatch(match2)
        matchDao.insertMatch(match3)

        // When
        val result = matchDao.list(null, 500)

        // Then
        assertEquals(3, result.size)
        assertEquals("match2", result[0].id) // Most recent first
        assertEquals("match3", result[1].id)
        assertEquals("match1", result[2].id)
    }

    @Test
    fun list_respectsLimit() = runTest {
        // Given
        for (i in 1..10) {
            matchDao.insertMatch(createTestMatch("match$i"))
        }

        // When
        val result = matchDao.list(null, 5)

        // Then
        assertEquals(5, result.size)
    }

    @Test
    fun list_filtersBy GroupId() = runTest {
        // Given
        val match1 = createTestMatch("match1", groupId = "group1")
        val match2 = createTestMatch("match2", groupId = "group2")
        val match3 = createTestMatch("match3", groupId = "group1")

        matchDao.insertMatch(match1)
        matchDao.insertMatch(match2)
        matchDao.insertMatch(match3)

        // When
        val result = matchDao.list("group1", 500)

        // Then
        assertEquals(2, result.size)
        assertTrue(result.all { it.groupId == "group1" })
    }

    @Test
    fun list_returnsEmptyListWhenNoMatches() = runTest {
        // When
        val result = matchDao.list(null, 500)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun getById_returnsCorrectMatch() = runTest {
        // Given
        val match = createTestMatch("match1", team1 = "Team A", team2 = "Team B")
        matchDao.insertMatch(match)

        // When
        val result = matchDao.getById("match1")

        // Then
        assertNotNull(result)
        assertEquals("match1", result?.id)
        assertEquals("Team A", result?.team1Name)
        assertEquals("Team B", result?.team2Name)
    }

    @Test
    fun getById_returnsNullForNonexistentMatch() = runTest {
        // When
        val result = matchDao.getById("nonexistent")

        // Then
        assertNull(result)
    }

    @Test
    fun deleteMatch_removesMatch() = runTest {
        // Given
        val match = createTestMatch("match1")
        matchDao.insertMatch(match)

        // When
        matchDao.deleteMatch("match1")

        // Then
        val result = matchDao.getById("match1")
        assertNull(result)
    }

    @Test
    fun deleteMatch_doesNotAffectOtherMatches() = runTest {
        // Given
        matchDao.insertMatch(createTestMatch("match1"))
        matchDao.insertMatch(createTestMatch("match2"))

        // When
        matchDao.deleteMatch("match1")

        // Then
        assertNull(matchDao.getById("match1"))
        assertNotNull(matchDao.getById("match2"))
    }

    @Test
    fun statsForMatch_returnsStatsForCorrectMatch() = runTest {
        // Given
        val match1 = createTestMatch("match1")
        val match2 = createTestMatch("match2")
        matchDao.insertMatch(match1)
        matchDao.insertMatch(match2)

        val stats1 = listOf(
            createTestStats("match1", "p1", "Player 1"),
            createTestStats("match1", "p2", "Player 2")
        )
        val stats2 = listOf(
            createTestStats("match2", "p3", "Player 3")
        )

        matchDao.insertStats(stats1)
        matchDao.insertStats(stats2)

        // When
        val result = matchDao.statsForMatch("match1")

        // Then
        assertEquals(2, result.size)
        assertTrue(result.all { it.matchId == "match1" })
    }

    @Test
    fun statsForMatch_returnsEmptyListWhenNoStats() = runTest {
        // When
        val result = matchDao.statsForMatch("match1")

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun impactsForMatch_returnsImpactsSortedByImpactDesc() = runTest {
        // Given
        val match = createTestMatch("match1")
        matchDao.insertMatch(match)

        val impacts = listOf(
            createTestImpact("match1", "p1", impact = 30.0),
            createTestImpact("match1", "p2", impact = 50.0),
            createTestImpact("match1", "p3", impact = 40.0)
        )
        matchDao.insertImpacts(impacts)

        // When
        val result = matchDao.impactsForMatch("match1")

        // Then
        assertEquals(3, result.size)
        assertEquals(50.0, result[0].impact, 0.01)
        assertEquals(40.0, result[1].impact, 0.01)
        assertEquals(30.0, result[2].impact, 0.01)
    }

    @Test
    fun impactsForMatch_returnsEmptyListWhenNoImpacts() = runTest {
        // When
        val result = matchDao.impactsForMatch("match1")

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun insertStats_replacesStatsOnConflict() = runTest {
        // Given
        val stats1 = createTestStats("match1", "p1", "Player 1", runs = 50, team = "Team A")
        matchDao.insertStats(listOf(stats1))

        // When - same matchId, playerId, and team (composite key)
        val stats2 = createTestStats("match1", "p1", "Player 1", runs = 75, team = "Team A")
        matchDao.insertStats(listOf(stats2))

        // Then
        val result = matchDao.statsForMatch("match1")
        assertEquals(1, result.size)
        assertEquals(75, result[0].runs)
    }

    @Test
    fun insertStats_allowsSamePlayerInDifferentTeams() = runTest {
        // Given - Joker playing for both teams
        val statsTeamA = createTestStats("match1", "joker", "Joker", runs = 30, team = "Team A")
        val statsTeamB = createTestStats("match1", "joker", "Joker", runs = 25, team = "Team B")

        // When
        matchDao.insertStats(listOf(statsTeamA, statsTeamB))

        // Then
        val result = matchDao.statsForMatch("match1")
        assertEquals(2, result.size)
        assertTrue(result.any { it.team == "Team A" && it.runs == 30 })
        assertTrue(result.any { it.team == "Team B" && it.runs == 25 })
    }

    @Test
    fun insertImpacts_handlesAutoGeneratedPrimaryKey() = runTest {
        // Given
        val impact1 = createTestImpact("match1", "p1", impact = 45.5)
        val impact2 = createTestImpact("match1", "p2", impact = 38.2)

        // When
        matchDao.insertImpacts(listOf(impact1, impact2))

        // Then
        val result = matchDao.impactsForMatch("match1")
        assertEquals(2, result.size)
        // Auto-generated keys should be different
        assertNotEquals(result[0].pk, result[1].pk)
    }

    @Test
    fun match_storesPlayerOfTheMatchInfo() = runTest {
        // Given
        val match = createTestMatch(
            "match1",
            playerOfTheMatchId = "p1",
            playerOfTheMatchName = "Player 1",
            playerOfTheMatchTeam = "Team A",
            playerOfTheMatchImpact = 55.5,
            playerOfTheMatchSummary = "Outstanding performance"
        )

        // When
        matchDao.insertMatch(match)

        // Then
        val result = matchDao.getById("match1")
        assertEquals("p1", result?.playerOfTheMatchId)
        assertEquals("Player 1", result?.playerOfTheMatchName)
        assertEquals("Team A", result?.playerOfTheMatchTeam)
        assertEquals(55.5, result?.playerOfTheMatchImpact ?: 0.0, 0.01)
        assertEquals("Outstanding performance", result?.playerOfTheMatchSummary)
    }

    @Test
    fun match_storesMatchSettingsJson() = runTest {
        // Given
        val settingsJson = """{"totalOvers":10,"maxPlayersPerTeam":11}"""
        val match = createTestMatch("match1", matchSettingsJson = settingsJson)

        // When
        matchDao.insertMatch(match)

        // Then
        val result = matchDao.getById("match1")
        assertEquals(settingsJson, result?.matchSettingsJson)
    }

    // Helper functions
    private fun createTestMatch(
        id: String,
        team1: String = "Team A",
        team2: String = "Team B",
        matchDate: Long = System.currentTimeMillis(),
        groupId: String? = null,
        playerOfTheMatchId: String? = null,
        playerOfTheMatchName: String? = null,
        playerOfTheMatchTeam: String? = null,
        playerOfTheMatchImpact: Double? = null,
        playerOfTheMatchSummary: String? = null,
        matchSettingsJson: String? = null
    ): MatchEntity {
        return MatchEntity(
            id = id,
            team1Name = team1,
            team2Name = team2,
            jokerPlayerName = null,
            firstInningsRuns = 100,
            firstInningsWickets = 5,
            secondInningsRuns = 95,
            secondInningsWickets = 10,
            winnerTeam = team1,
            winningMargin = "5 runs",
            matchDate = matchDate,
            groupId = groupId,
            groupName = if (groupId != null) "Test Group" else null,
            shortPitch = false,
            playerOfTheMatchId = playerOfTheMatchId,
            playerOfTheMatchName = playerOfTheMatchName,
            playerOfTheMatchTeam = playerOfTheMatchTeam,
            playerOfTheMatchImpact = playerOfTheMatchImpact,
            playerOfTheMatchSummary = playerOfTheMatchSummary,
            matchSettingsJson = matchSettingsJson
        )
    }

    private fun createTestStats(
        matchId: String,
        playerId: String,
        playerName: String,
        runs: Int = 0,
        team: String = "Team A"
    ): PlayerMatchStatsEntity {
        return PlayerMatchStatsEntity(
            matchId = matchId,
            playerId = playerId,
            name = playerName,
            team = team,
            runs = runs,
            ballsFaced = 0,
            fours = 0,
            sixes = 0,
            wickets = 0,
            runsConceded = 0,
            oversBowled = 0.0,
            isOut = false,
            isJoker = false
        )
    }

    private fun createTestImpact(
        matchId: String,
        playerId: String,
        impact: Double
    ): PlayerImpactEntity {
        return PlayerImpactEntity(
            matchId = matchId,
            playerId = playerId,
            name = "Player",
            team = "Team A",
            impact = impact,
            summary = "Test summary",
            isJoker = false,
            runs = 0,
            balls = 0,
            fours = 0,
            sixes = 0,
            wickets = 0,
            runsConceded = 0,
            oversBowled = 0.0
        )
    }
}



