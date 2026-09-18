package com.oreki.stumpd.data.repository

import android.content.Context
import com.google.gson.Gson
import com.oreki.stumpd.*
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.model.MatchSettings
import com.oreki.stumpd.domain.model.PlayerImpact
import com.oreki.stumpd.domain.model.PlayerMatchStats
import com.oreki.stumpd.data.local.dao.MatchDao
import com.oreki.stumpd.data.local.db.StumpdDb
import com.oreki.stumpd.data.local.entity.MatchEntity
import com.oreki.stumpd.data.local.entity.PlayerImpactEntity
import com.oreki.stumpd.data.local.entity.PlayerMatchStatsEntity
import androidx.room.withTransaction
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MatchRepositoryTest {

    private lateinit var db: StumpdDb
    private lateinit var matchDao: MatchDao
    private lateinit var context: Context
    private lateinit var repository: MatchRepository
    private val gson = Gson()

    @Before
    fun setup() {
        db = mockk(relaxed = true)
        matchDao = mockk(relaxed = true)
        context = RuntimeEnvironment.getApplication()

        every { db.matchDao() } returns matchDao
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { db.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            // withTransaction is an extension function, so the recorded args are
            // (receiver, block) - firstArg() is the database itself, not the lambda.
            val body = secondArg<suspend () -> Any?>()
            runBlocking { body() }
        }

        repository = MatchRepository(db, context, UnconfinedTestDispatcher())
    }

    @Test
    fun `saveMatch converts domain to entity and saves to database`() = runTest {
        // Setup mock
        coEvery { matchDao.insertFullMatch(any(), any(), any()) } returns Unit
        
        // Given
        val matchSettings = MatchSettings(totalOvers = 5, maxPlayersPerTeam = 6)
        val match = MatchHistory(
            id = "match1",
            team1Name = "Team A",
            team2Name = "Team B",
            jokerPlayerName = "Joker",
            firstInningsRuns = 100,
            firstInningsWickets = 5,
            secondInningsRuns = 95,
            secondInningsWickets = 10,
            winnerTeam = "Team A",
            winningMargin = "5 runs",
            matchDate = 1234567890L,
            groupId = "group1",
            groupName = "Test Group",
            shortPitch = true,
            matchSettings = matchSettings,
            firstInningsBatting = listOf(
                PlayerMatchStats(
                    id = "p1",
                    name = "Player 1",
                    team = "Team A",
                    runs = 50,
                    ballsFaced = 30,
                    wickets = 0,
                    isOut = true,
                    isJoker = false
                )
            ),
            firstInningsBowling = listOf(
                PlayerMatchStats(
                    id = "p2",
                    name = "Player 2",
                    team = "Team B",
                    runs = 0,
                    wickets = 2,
                    runsConceded = 25,
                    oversBowled = 2.0,
                    isJoker = false
                )
            ),
            playerImpacts = listOf(
                PlayerImpact(
                    id = "p1",
                    name = "Player 1",
                    team = "Team A",
                    impact = 45.5,
                    summary = "Great batting",
                    runs = 50,
                    balls = 30
                )
            )
        )

        // When
        repository.saveMatch(match)

        // Then
        coVerify {
            matchDao.insertFullMatch(
                m = withArg {
                    assertEquals("match1", it.id)
                    assertEquals("Team A", it.team1Name)
                    assertEquals("Team B", it.team2Name)
                    assertEquals("Joker", it.jokerPlayerName)
                    assertEquals(100, it.firstInningsRuns)
                    assertEquals(5, it.firstInningsWickets)
                    assertEquals(95, it.secondInningsRuns)
                    assertEquals(10, it.secondInningsWickets)
                    assertEquals("Team A", it.winnerTeam)
                    assertEquals("5 runs", it.winningMargin)
                    assertEquals(1234567890L, it.matchDate)
                    assertEquals("group1", it.groupId)
                    assertEquals("Test Group", it.groupName)
                    assertTrue(it.shortPitch)
                    assertNotNull(it.matchSettingsJson)
                },
                stats = withArg { stats ->
                    // Should have 2 stats entries (1 batting + 1 bowling)
                    assertEquals(2, stats.size)
                },
                impacts = withArg { impacts ->
                    assertEquals(1, impacts.size)
                    assertEquals("p1", impacts[0].playerId)
                    assertEquals(45.5, impacts[0].impact, 0.01)
                }
            )
        }
    }

    @Test
    fun `saveMatch handles match without joker`() = runTest {
        // Setup mock
        coEvery { matchDao.insertFullMatch(any(), any(), any()) } returns Unit
        
        // Given
        val match = MatchHistory(
            id = "match1",
            team1Name = "Team A",
            team2Name = "Team B",
            jokerPlayerName = null,
            team1CaptainName = null,
            team2CaptainName = null,
            firstInningsRuns = 100,
            firstInningsWickets = 5,
            secondInningsRuns = 95,
            secondInningsWickets = 10,
            winnerTeam = "Team A",
            winningMargin = "5 runs"
        )

        // When
        repository.saveMatch(match)

        // Then
        coVerify {
            matchDao.insertFullMatch(
                m = withArg { assertNull(it.jokerPlayerName) },
                stats = any(),
                impacts = any()
            )
        }
    }

    @Test
    fun `getAllMatches returns all matches from database`() = runTest {
        // Given
        val matchEntities = listOf(
            MatchEntity(
                id = "match1",
                team1Name = "Team A",
                team2Name = "Team B",
                jokerPlayerName = null,
                team1CaptainName = null,
                team2CaptainName = null,
                firstInningsRuns = 100,
                firstInningsWickets = 5,
                secondInningsRuns = 95,
                secondInningsWickets = 10,
                winnerTeam = "Team A",
                winningMargin = "5 runs",
                matchDate = 1234567890L,
                groupId = null,
                groupName = null,
                shortPitch = false,
                playerOfTheMatchId = null,
                playerOfTheMatchName = null,
                playerOfTheMatchTeam = null,
                playerOfTheMatchImpact = null,
                playerOfTheMatchSummary = null,
                matchSettingsJson = null,
                allDeliveriesJson = null
            )
        )

        coEvery { matchDao.list(null, 500) } returns matchEntities

        // When
        val result = repository.getAllMatches()

        // Then
        assertEquals(1, result.size)
        assertEquals("match1", result[0].id)
        assertEquals("Team A", result[0].team1Name)
        assertEquals("Team B", result[0].team2Name)
    }

    @Test
    fun `getAllMatches filters by groupId when provided`() = runTest {
        // Given
        val groupId = "group1"
        val matchEntities = listOf(
            MatchEntity(
                id = "match1",
                team1Name = "Team A",
                team2Name = "Team B",
                jokerPlayerName = null,
                team1CaptainName = null,
                team2CaptainName = null,
                firstInningsRuns = 100,
                firstInningsWickets = 5,
                secondInningsRuns = 95,
                secondInningsWickets = 10,
                winnerTeam = "Team A",
                winningMargin = "5 runs",
                matchDate = 1234567890L,
                groupId = groupId,
                groupName = "Test Group",
                shortPitch = false,
                playerOfTheMatchId = null,
                playerOfTheMatchName = null,
                playerOfTheMatchTeam = null,
                playerOfTheMatchImpact = null,
                playerOfTheMatchSummary = null,
                matchSettingsJson = null,
                allDeliveriesJson = null
            )
        )

        coEvery { matchDao.list(groupId, 500) } returns matchEntities

        // When
        val result = repository.getAllMatches(groupId)

        // Then
        coVerify { matchDao.list(groupId, 500) }
        assertEquals(1, result.size)
        assertEquals(groupId, result[0].groupId)
    }

    @Test
    fun `getAllMatches respects limit parameter`() = runTest {
        // Given
        val limit = 10
        coEvery { matchDao.list(null, limit) } returns emptyList()

        // When
        repository.getAllMatches(limit = limit)

        // Then
        coVerify { matchDao.list(null, limit) }
    }

    @Test
    fun `deleteMatch removes match from database`() = runTest {
        // Given
        val matchId = "match1"

        // When
        repository.deleteMatch(matchId)

        // Then
        coVerify { matchDao.deleteMatch(matchId) }
    }

    @Test
    fun `getMatchById returns match when exists`() = runTest {
        // Given
        val matchId = "match1"
        val matchEntity = MatchEntity(
            id = matchId,
            team1Name = "Team A",
            team2Name = "Team B",
            jokerPlayerName = null,
            team1CaptainName = null,
            team2CaptainName = null,
            firstInningsRuns = 100,
            firstInningsWickets = 5,
            secondInningsRuns = 95,
            secondInningsWickets = 10,
            winnerTeam = "Team A",
            winningMargin = "5 runs",
            matchDate = 1234567890L,
            groupId = null,
            groupName = null,
            shortPitch = false,
            playerOfTheMatchId = null,
            playerOfTheMatchName = null,
            playerOfTheMatchTeam = null,
            playerOfTheMatchImpact = null,
            playerOfTheMatchSummary = null,
            matchSettingsJson = null,
            allDeliveriesJson = null
        )

        coEvery { matchDao.getById(matchId) } returns matchEntity

        // When
        val result = repository.getMatchById(matchId)

        // Then
        assertNotNull(result)
        assertEquals(matchId, result?.id)
    }

    @Test
    fun `getMatchById returns null when match does not exist`() = runTest {
        // Given
        val matchId = "nonexistent"
        coEvery { matchDao.getById(matchId) } returns null

        // When
        val result = repository.getMatchById(matchId)

        // Then
        assertNull(result)
    }

    @Test
    fun `getMatchWithStats returns complete match with stats and impacts`() = runTest {
        // Given
        val matchId = "match1"
        val matchEntity = MatchEntity(
            id = matchId,
            team1Name = "Team A",
            team2Name = "Team B",
            jokerPlayerName = null,
            team1CaptainName = null,
            team2CaptainName = null,
            firstInningsRuns = 100,
            firstInningsWickets = 5,
            secondInningsRuns = 95,
            secondInningsWickets = 10,
            winnerTeam = "Team A",
            winningMargin = "5 runs",
            matchDate = 1234567890L,
            groupId = null,
            groupName = null,
            shortPitch = false,
            playerOfTheMatchId = null,
            playerOfTheMatchName = null,
            playerOfTheMatchTeam = null,
            playerOfTheMatchImpact = null,
            playerOfTheMatchSummary = null,
            matchSettingsJson = null,
            allDeliveriesJson = null
        )

        val stats = listOf(
            PlayerMatchStatsEntity(
                matchId = matchId,
                playerId = "p1",
                name = "Player 1",
                team = "Team A",
                role = "BAT",
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
                matchId = matchId,
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

        coEvery { matchDao.getById(matchId) } returns matchEntity
        coEvery { matchDao.statsForMatch(matchId) } returns stats
        coEvery { matchDao.impactsForMatch(matchId) } returns impacts

        // When
        val result = repository.getMatchWithStats(matchId)

        // Then
        assertNotNull(result)
        assertEquals(matchId, result?.id)
        assertEquals(1, result?.firstInningsBatting?.size)
        assertEquals(1, result?.playerImpacts?.size)
        assertEquals(45.5, result?.playerImpacts?.get(0)?.impact ?: 0.0, 0.01)
    }

    @Test
    fun `getMatchWithStats returns null when match does not exist`() = runTest {
        // Given
        val matchId = "nonexistent"
        coEvery { matchDao.getById(matchId) } returns null

        // When
        val result = repository.getMatchWithStats(matchId)

        // Then
        assertNull(result)
        coVerify(exactly = 0) { matchDao.statsForMatch(any()) }
        coVerify(exactly = 0) { matchDao.impactsForMatch(any()) }
    }

    @Test
    fun `getAllMatchesWithStats returns matches with stats`() = runTest {
        // Given
        val matchEntity = MatchEntity(
            id = "match1",
            team1Name = "Team A",
            team2Name = "Team B",
            jokerPlayerName = null,
            team1CaptainName = null,
            team2CaptainName = null,
            firstInningsRuns = 100,
            firstInningsWickets = 5,
            secondInningsRuns = 95,
            secondInningsWickets = 10,
            winnerTeam = "Team A",
            winningMargin = "5 runs",
            matchDate = 1234567890L,
            groupId = null,
            groupName = null,
            shortPitch = false,
            playerOfTheMatchId = null,
            playerOfTheMatchName = null,
            playerOfTheMatchTeam = null,
            playerOfTheMatchImpact = null,
            playerOfTheMatchSummary = null,
            matchSettingsJson = null,
            allDeliveriesJson = null
        )

        val stats = listOf(
            PlayerMatchStatsEntity(
                matchId = "match1",
                playerId = "p1",
                name = "Player 1",
                team = "Team A",
                role = "BAT",
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

        coEvery { matchDao.list(null, 500) } returns listOf(matchEntity)
        coEvery { matchDao.statsForMatch("match1") } returns stats

        // When
        val result = repository.getAllMatchesWithStats()

        // Then
        assertEquals(1, result.size)
        assertEquals(1, result[0].firstInningsBatting.size)
    }

    @Test
    fun `exportMatches creates JSON file with matches`() = runTest {
        // Given
        val matchEntity = MatchEntity(
            id = "match1",
            team1Name = "Team A",
            team2Name = "Team B",
            jokerPlayerName = null,
            team1CaptainName = null,
            team2CaptainName = null,
            firstInningsRuns = 100,
            firstInningsWickets = 5,
            secondInningsRuns = 95,
            secondInningsWickets = 10,
            winnerTeam = "Team A",
            winningMargin = "5 runs",
            matchDate = 1234567890L,
            groupId = null,
            groupName = null,
            shortPitch = false,
            playerOfTheMatchId = null,
            playerOfTheMatchName = null,
            playerOfTheMatchTeam = null,
            playerOfTheMatchImpact = null,
            playerOfTheMatchSummary = null,
            matchSettingsJson = null,
            allDeliveriesJson = null
        )

        coEvery { matchDao.list(null, 10_000) } returns listOf(matchEntity)

        // When
        val result = repository.exportMatches()

        // Then
        assertNotNull(result)
        assertTrue(result!!.endsWith(".json"))
        val file = File(result)
        assertTrue(file.exists())
        val content = file.readText()
        assertTrue(content.contains("match1"))
        file.delete() // Cleanup
    }

    @Test
    fun `importMatches reads and saves matches from JSON file`() = runTest {
        coEvery { matchDao.insertMatch(any()) } returns Unit

        val matchEntity = MatchEntity(
            id = "match1",
            team1Name = "Team A",
            team2Name = "Team B",
            jokerPlayerName = null,
            team1CaptainName = null,
            team2CaptainName = null,
            firstInningsRuns = 100,
            firstInningsWickets = 5,
            secondInningsRuns = 95,
            secondInningsWickets = 10,
            winnerTeam = "Team A",
            winningMargin = "5 runs",
            matchDate = 1234567890L,
            groupId = null,
            groupName = null,
            shortPitch = false,
            playerOfTheMatchId = null,
            playerOfTheMatchName = null,
            playerOfTheMatchTeam = null,
            playerOfTheMatchImpact = null,
            playerOfTheMatchSummary = null,
            matchSettingsJson = null,
            allDeliveriesJson = null
        )

        val backup = MatchRepository.CompleteBackup(
            matches = listOf(matchEntity),
            matchStats = emptyList(),
            playerImpacts = emptyList(),
            players = emptyList(),
            groups = emptyList(),
            groupDefaults = emptyList(),
            groupMembers = emptyList(),
            groupLastTeams = emptyList(),
            groupUnavailablePlayers = emptyList(),
            userPreferences = emptyList(),
            partnerships = emptyList(),
            fallOfWickets = emptyList()
        )
        val tempFile = File.createTempFile("test_import", ".json")
        tempFile.writeText(gson.toJson(backup))

        val result = repository.importMatches(tempFile.absolutePath)

        assertTrue(result)
        coVerify { matchDao.insertMatch(any()) }
        tempFile.delete()
    }

    @Test
    fun `importMatches returns false for nonexistent file`() = runTest {
        // When
        val result = repository.importMatches("/nonexistent/file.json")

        // Then
        assertFalse(result)
        coVerify(exactly = 0) { matchDao.insertMatch(any()) }
    }

    @Test
    fun `importMatches returns false for invalid JSON`() = runTest {
        // Given
        val tempFile = File.createTempFile("test_invalid", ".json")
        tempFile.writeText("invalid json content")

        // When
        val result = repository.importMatches(tempFile.absolutePath)

        // Then
        assertFalse(result)
        tempFile.delete() // Cleanup
    }

    @Test
    fun `saveMatch serializes match settings correctly`() = runTest {
        // Setup mock
        coEvery { matchDao.insertFullMatch(any(), any(), any()) } returns Unit
        
        // Given
        val matchSettings = MatchSettings(
            totalOvers = 10,
            maxPlayersPerTeam = 11,
            noballRuns = 1,
            powerplayOvers = 3,
            shortPitch = false
        )

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
            matchSettings = matchSettings
        )

        // When
        repository.saveMatch(match)

        // Then
        coVerify {
            matchDao.insertFullMatch(
                m = withArg {
                    assertNotNull(it.matchSettingsJson)
                    val deserializedSettings = gson.fromJson(it.matchSettingsJson, MatchSettings::class.java)
                    assertEquals(10, deserializedSettings.totalOvers)
                    assertEquals(11, deserializedSettings.maxPlayersPerTeam)
                    assertEquals(1, deserializedSettings.noballRuns)
                    assertEquals(3, deserializedSettings.powerplayOvers)
                },
                stats = any(),
                impacts = any()
            )
        }
    }

    // ── Changing an existing match ──────────────────────────────────────────────────────
    //
    // Until now nothing tested saving over a match that already exists, which is what adopting,
    // merging, a cloud download and (soon) a correction all do. The two shapes of that write are
    // deliberately different: merging must not delete, replacing must.

    private fun matchFor(
        id: String,
        batting: List<PlayerMatchStats> = emptyList(),
        bowling: List<PlayerMatchStats> = emptyList(),
        impacts: List<PlayerImpact> = emptyList(),
    ) = MatchHistory(
        id = id,
        team1Name = "Team A",
        team2Name = "Team B",
        firstInningsRuns = 40,
        firstInningsWickets = 2,
        secondInningsRuns = 38,
        secondInningsWickets = 4,
        winnerTeam = "Team A",
        winningMargin = "2 runs",
        firstInningsBatting = batting,
        secondInningsBowling = bowling,
        playerImpacts = impacts,
    )

    @Test
    fun `replaceMatchGraph clears the old child rows before writing the new ones`() = runTest {
        val match = matchFor(
            id = "m1",
            batting = listOf(PlayerMatchStats(id = "p1", name = "Kushal", runs = 20, team = "Team A", role = "BAT")),
        )

        repository.replaceMatchGraph(match)

        // Order matters: inserting first and deleting after would wipe what we just wrote.
        coVerifyOrder {
            db.partnershipDao().deleteForMatch("m1")
            db.fallOfWicketDao().deleteForMatch("m1")
            matchDao.replaceFullMatch(any(), any(), any())
        }
        coVerify(exactly = 0) { matchDao.insertFullMatch(any(), any(), any()) }
    }

    @Test
    fun `replaceMatchGraph stamps a fresh updatedAt so the change is picked up for upload`() = runTest {
        val before = System.currentTimeMillis()
        val saved = slot<MatchEntity>()
        coEvery { matchDao.replaceFullMatch(capture(saved), any(), any()) } returns Unit

        repository.replaceMatchGraph(matchFor("m1"))

        assertTrue(saved.captured.updatedAt >= before)
    }

    @Test
    fun `saveMatch still merges by default, so a partial cloud copy cannot delete local rows`() = runTest {
        repository.saveMatch(matchFor("m1"), persistedUpdatedAt = 1234L)

        coVerify { matchDao.insertFullMatch(any(), any(), any()) }
        coVerify(exactly = 0) { matchDao.replaceFullMatch(any(), any(), any()) }
        coVerify(exactly = 0) { matchDao.deleteStatsForMatch(any()) }
        coVerify(exactly = 0) { db.partnershipDao().deleteForMatch(any()) }
    }

    @Test
    fun `deleteMatch clears the four child tables that have no foreign keys`() = runTest {
        repository.deleteMatch("m1")

        coVerify { matchDao.deleteStatsForMatch("m1") }
        coVerify { matchDao.deleteImpactsForMatch("m1") }
        coVerify { db.partnershipDao().deleteForMatch("m1") }
        coVerify { db.fallOfWicketDao().deleteForMatch("m1") }
        coVerify { matchDao.deleteMatch("m1") }
    }

    @Test
    fun `renaming a player rewrites every place their name appears in a match`() = runTest {
        // The defect this covers: renaming used to update `player_match_stats.name` alone, so the
        // scorecard showed a batter dismissed by a bowler who, by name, no longer existed, and
        // head-to-head and partnership records quietly split one player in two.
        val matchId = "m-rename"
        coEvery { matchDao.matchIdsNaming("p-kushal", "Kushal Kumar") } returns listOf(matchId)
        coEvery { matchDao.getById(matchId) } returns MatchEntity(
            id = matchId, team1Name = "Team A", team2Name = "Team B",
            jokerPlayerName = "Kushal", team1CaptainName = "Kushal", team2CaptainName = null,
            firstInningsRuns = 20, firstInningsWickets = 1,
            secondInningsRuns = 10, secondInningsWickets = 0,
            winnerTeam = "Team A", winningMargin = "10 runs", matchDate = 1L,
            groupId = null, groupName = null, shortPitch = false,
            playerOfTheMatchId = "p-kushal", playerOfTheMatchName = "Kushal",
            playerOfTheMatchTeam = "Team A", playerOfTheMatchImpact = 40.0,
            playerOfTheMatchSummary = "8 (7)",
            matchSettingsJson = null,
            allDeliveriesJson = gson.toJson(
                listOf(
                    mapOf(
                        "inning" to 1, "over" to 1, "ballInOver" to 1,
                        "outcome" to "1 + RO (Kushal @ NS)", "runs" to 1,
                        "strikerName" to "Ajith", "nonStrikerName" to "Kushal",
                        "bowlerName" to "Muttu", "highlight" to false,
                    )
                )
            ),
        )
        coEvery { matchDao.statsForMatch(matchId) } returns listOf(
            PlayerMatchStatsEntity(
                matchId = matchId, playerId = "p-kushal", name = "Kushal", team = "Team A",
                role = "BAT", runs = 8, ballsFaced = 7, fours = 0, sixes = 0,
                wickets = 0, runsConceded = 0, oversBowled = 0.0, isOut = true, isJoker = false,
            ),
            PlayerMatchStatsEntity(
                matchId = matchId, playerId = "p-ajith", name = "Ajith", team = "Team A",
                role = "BAT", runs = 12, ballsFaced = 9, fours = 0, sixes = 0,
                wickets = 0, runsConceded = 0, oversBowled = 0.0, isOut = true, isJoker = false,
                dismissalType = "RUN_OUT", bowlerName = "Muttu", fielderName = "Kushal",
            ),
        )
        coEvery { matchDao.impactsForMatch(matchId) } returns listOf(
            PlayerImpactEntity(
                matchId = matchId, playerId = "p-kushal", name = "Kushal", team = "Team A",
                impact = 40.0, summary = "8 (7)", isJoker = false,
                runs = 8, balls = 7, fours = 0, sixes = 0,
                wickets = 0, runsConceded = 0, oversBowled = 0.0,
            )
        )
        val saved = slot<MatchEntity>()
        val savedStats = slot<List<PlayerMatchStatsEntity>>()
        val savedImpacts = slot<List<PlayerImpactEntity>>()
        coEvery {
            matchDao.replaceFullMatch(capture(saved), capture(savedStats), capture(savedImpacts))
        } returns Unit

        val changed = repository.renamePlayerAcrossMatches("p-kushal", "Kushal Kumar")

        assertEquals(1, changed)
        // The stats row, the fielder credited on someone else's run-out, the impact row, the
        // joker, the captain, Player of the Match — and the name buried in the outcome string,
        // which is the one every earlier pass at this missed.
        assertEquals("Kushal Kumar", savedStats.captured.single { it.playerId == "p-kushal" }.name)
        assertEquals("Kushal Kumar", savedStats.captured.single { it.playerId == "p-ajith" }.fielderName)
        assertEquals("Kushal Kumar", savedImpacts.captured.single().name)
        assertEquals("Kushal Kumar", saved.captured.jokerPlayerName)
        assertEquals("Kushal Kumar", saved.captured.team1CaptainName)
        assertEquals("Kushal Kumar", saved.captured.playerOfTheMatchName)
        assertTrue(saved.captured.allDeliveriesJson!!.contains("Kushal Kumar @ NS"))
        assertFalse(saved.captured.allDeliveriesJson!!.contains("(Kushal @"))
    }

    @Test
    fun `a match that already uses the new name is left alone`() = runTest {
        // Saving a player without renaming them must not stamp a fresh updatedAt on every match
        // they ever played in and re-upload the lot.
        coEvery { matchDao.matchIdsNaming("p-kushal", "Kushal") } returns emptyList()

        assertEquals(0, repository.renamePlayerAcrossMatches("p-kushal", "Kushal"))

        coVerify(exactly = 0) { matchDao.replaceFullMatch(any(), any(), any()) }
    }

}

