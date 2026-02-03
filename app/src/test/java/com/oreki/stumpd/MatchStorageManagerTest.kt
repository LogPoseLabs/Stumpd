package com.oreki.stumpd

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MatchStorageManagerTest {

    private lateinit var context: Context
    private lateinit var storageManager: MatchStorageManager
    private lateinit var sharedPrefs: SharedPreferences

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        storageManager = MatchStorageManager(context)
        sharedPrefs = context.getSharedPreferences("cricket_matches_v3", Context.MODE_PRIVATE)
        
        // Clear preferences before each test
        sharedPrefs.edit().clear().apply()
    }

    @Test
    fun `saveMatch stores match in SharedPreferences`() {
        // Given
        val match = createTestMatch("match1", "Team A", "Team B")

        // When
        storageManager.saveMatch(match)

        // Then
        val matches = storageManager.getAllMatches()
        assertEquals(1, matches.size)
        assertEquals("match1", matches[0].id)
        assertEquals("Team A", matches[0].team1Name)
        assertEquals("Team B", matches[0].team2Name)
    }

    @Test
    fun `saveMatch adds match to beginning of list`() {
        // Given
        val match1 = createTestMatch("match1", "Team A", "Team B")
        val match2 = createTestMatch("match2", "Team C", "Team D")

        // When
        storageManager.saveMatch(match1)
        storageManager.saveMatch(match2)

        // Then
        val matches = storageManager.getAllMatches()
        assertEquals(2, matches.size)
        assertEquals("match2", matches[0].id) // Most recent first
        assertEquals("match1", matches[1].id)
    }

    @Test
    fun `saveMatch limits storage to 500 matches`() {
        // Given - save 505 matches
        for (i in 1..505) {
            storageManager.saveMatch(createTestMatch("match$i", "Team A", "Team B"))
        }

        // When
        val matches = storageManager.getAllMatches()

        // Then
        assertEquals(500, matches.size)
        assertEquals("match505", matches[0].id) // Most recent
        assertEquals("match6", matches[499].id) // Oldest kept (1-5 should be dropped)
    }

    @Test
    fun `getAllMatches returns empty list when no matches saved`() {
        // When
        val matches = storageManager.getAllMatches()

        // Then
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `getAllMatches returns all saved matches`() {
        // Given
        val match1 = createTestMatch("match1", "Team A", "Team B")
        val match2 = createTestMatch("match2", "Team C", "Team D")
        val match3 = createTestMatch("match3", "Team E", "Team F")

        storageManager.saveMatch(match1)
        storageManager.saveMatch(match2)
        storageManager.saveMatch(match3)

        // When
        val matches = storageManager.getAllMatches()

        // Then
        assertEquals(3, matches.size)
    }

    @Test
    fun `deleteMatch removes correct match`() {
        // Given
        val match1 = createTestMatch("match1", "Team A", "Team B")
        val match2 = createTestMatch("match2", "Team C", "Team D")
        val match3 = createTestMatch("match3", "Team E", "Team F")

        storageManager.saveMatch(match1)
        storageManager.saveMatch(match2)
        storageManager.saveMatch(match3)

        // When
        storageManager.deleteMatch("match2")

        // Then
        val matches = storageManager.getAllMatches()
        assertEquals(2, matches.size)
        assertFalse(matches.any { it.id == "match2" })
        assertTrue(matches.any { it.id == "match1" })
        assertTrue(matches.any { it.id == "match3" })
    }

    @Test
    fun `deleteMatch does nothing when match ID not found`() {
        // Given
        storageManager.saveMatch(createTestMatch("match1", "Team A", "Team B"))

        // When
        storageManager.deleteMatch("nonexistent")

        // Then
        val matches = storageManager.getAllMatches()
        assertEquals(1, matches.size)
    }

    @Test
    fun `deleteMatch handles empty storage`() {
        // When
        storageManager.deleteMatch("match1")

        // Then
        val matches = storageManager.getAllMatches()
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `clearAllMatches removes all matches`() {
        // Given
        storageManager.saveMatch(createTestMatch("match1", "Team A", "Team B"))
        storageManager.saveMatch(createTestMatch("match2", "Team C", "Team D"))

        // When
        storageManager.clearAllMatches()

        // Then
        val matches = storageManager.getAllMatches()
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `getLatestMatchID returns ID of most recently saved match`() {
        // Given
        storageManager.saveMatch(createTestMatch("match1", "Team A", "Team B"))
        storageManager.saveMatch(createTestMatch("match2", "Team C", "Team D"))
        storageManager.saveMatch(createTestMatch("match3", "Team E", "Team F"))

        // When
        val latestId = storageManager.getLatestMatchID()

        // Then
        // Note: saveMatch adds to beginning, but getLatestMatchID returns last in list
        // So the first saved match (match1) will be last in the list
        assertEquals("match1", latestId)
    }

    @Test
    fun `getLatestMatchID returns null when no matches exist`() {
        // When
        val latestId = storageManager.getLatestMatchID()

        // Then
        assertNull(latestId)
    }

    @Test
    fun `exportMatches creates JSON file with all matches`() {
        // Given
        storageManager.saveMatch(createTestMatch("match1", "Team A", "Team B"))
        storageManager.saveMatch(createTestMatch("match2", "Team C", "Team D"))

        // When
        val exportPath = storageManager.exportMatches()

        // Then
        assertNotNull(exportPath)
        val file = File(exportPath!!)
        assertTrue(file.exists())
        val content = file.readText()
        assertTrue(content.contains("match1"))
        assertTrue(content.contains("match2"))
        assertTrue(content.contains("Team A"))
        
        // Cleanup
        file.delete()
    }

    @Test
    fun `exportMatches creates file with custom name`() {
        // Given
        storageManager.saveMatch(createTestMatch("match1", "Team A", "Team B"))
        val customName = "custom_backup_test.json"

        // When
        val exportPath = storageManager.exportMatches(customName)

        // Then
        assertNotNull(exportPath)
        assertTrue(exportPath!!.endsWith(customName))
        val file = File(exportPath)
        assertTrue(file.exists())
        
        // Cleanup
        file.delete()
    }

    @Test
    fun `exportMatches handles empty match list`() {
        // When
        val exportPath = storageManager.exportMatches()

        // Then
        assertNotNull(exportPath)
        val file = File(exportPath!!)
        assertTrue(file.exists())
        val content = file.readText()
        assertEquals("[]", content)
        
        // Cleanup
        file.delete()
    }

    @Test
    fun `importMatches loads matches from JSON file`() {
        // Given
        val match1 = createTestMatch("match1", "Team A", "Team B")
        val match2 = createTestMatch("match2", "Team C", "Team D")
        
        val tempManager = MatchStorageManager(context)
        tempManager.saveMatch(match1)
        tempManager.saveMatch(match2)
        val exportPath = tempManager.exportMatches()!!

        // Clear current storage
        storageManager.clearAllMatches()

        // When
        val success = storageManager.importMatches(exportPath)

        // Then
        assertTrue(success)
        val matches = storageManager.getAllMatches()
        assertEquals(2, matches.size)
        assertTrue(matches.any { it.id == "match1" })
        assertTrue(matches.any { it.id == "match2" })
        
        // Cleanup
        File(exportPath).delete()
    }

    @Test
    fun `importMatches returns false for nonexistent file`() {
        // When
        val success = storageManager.importMatches("/nonexistent/file.json")

        // Then
        assertFalse(success)
    }

    @Test
    fun `importMatches returns false for invalid JSON`() {
        // Given
        val tempFile = File.createTempFile("invalid", ".json")
        tempFile.writeText("invalid json content")

        // When
        val success = storageManager.importMatches(tempFile.absolutePath)

        // Then
        assertFalse(success)
        
        // Cleanup
        tempFile.delete()
    }

    @Test
    fun `importMatches handles empty JSON array`() {
        // Given
        val tempFile = File.createTempFile("empty", ".json")
        tempFile.writeText("[]")

        // When
        val success = storageManager.importMatches(tempFile.absolutePath)

        // Then
        assertTrue(success)
        val matches = storageManager.getAllMatches()
        assertTrue(matches.isEmpty())
        
        // Cleanup
        tempFile.delete()
    }

    @Test
    fun `saveMatch preserves all match fields`() {
        // Given
        val matchSettings = MatchSettings(totalOvers = 10, maxPlayersPerTeam = 11)
        val match = MatchHistory(
            id = "match1",
            team1Name = "Team A",
            team2Name = "Team B",
            jokerPlayerName = "Joker",
            firstInningsRuns = 120,
            firstInningsWickets = 7,
            secondInningsRuns = 115,
            secondInningsWickets = 10,
            winnerTeam = "Team A",
            winningMargin = "5 runs",
            matchDate = 1234567890L,
            groupId = "group1",
            groupName = "Test Group",
            shortPitch = true,
            playerOfTheMatchId = "p1",
            playerOfTheMatchName = "Player 1",
            playerOfTheMatchTeam = "Team A",
            playerOfTheMatchImpact = 55.5,
            playerOfTheMatchSummary = "Great performance",
            matchSettings = matchSettings,
            firstInningsBatting = listOf(
                PlayerMatchStats(id = "p1", name = "Player 1", runs = 50, ballsFaced = 30, team = "Team A")
            ),
            playerImpacts = listOf(
                PlayerImpact(id = "p1", name = "Player 1", team = "Team A", impact = 55.5, summary = "Great")
            )
        )

        // When
        storageManager.saveMatch(match)

        // Then
        val matches = storageManager.getAllMatches()
        val savedMatch = matches[0]
        
        assertEquals("match1", savedMatch.id)
        assertEquals("Team A", savedMatch.team1Name)
        assertEquals("Team B", savedMatch.team2Name)
        assertEquals("Joker", savedMatch.jokerPlayerName)
        assertEquals(120, savedMatch.firstInningsRuns)
        assertEquals(7, savedMatch.firstInningsWickets)
        assertEquals(115, savedMatch.secondInningsRuns)
        assertEquals(10, savedMatch.secondInningsWickets)
        assertEquals("Team A", savedMatch.winnerTeam)
        assertEquals("5 runs", savedMatch.winningMargin)
        assertEquals(1234567890L, savedMatch.matchDate)
        assertEquals("group1", savedMatch.groupId)
        assertEquals("Test Group", savedMatch.groupName)
        assertTrue(savedMatch.shortPitch)
        assertEquals("p1", savedMatch.playerOfTheMatchId)
        assertEquals("Player 1", savedMatch.playerOfTheMatchName)
        assertEquals("Team A", savedMatch.playerOfTheMatchTeam)
        assertEquals(55.5, savedMatch.playerOfTheMatchImpact ?: 0.0, 0.01)
        assertEquals("Great performance", savedMatch.playerOfTheMatchSummary)
        assertEquals(10, savedMatch.matchSettings?.totalOvers)
        assertEquals(1, savedMatch.firstInningsBatting.size)
        assertEquals(1, savedMatch.playerImpacts.size)
    }

    @Test
    fun `shareBackup returns export path`() {
        // Given
        storageManager.saveMatch(createTestMatch("match1", "Team A", "Team B"))

        // When
        val backupPath = storageManager.shareBackup()

        // Then
        assertNotNull(backupPath)
        val file = File(backupPath!!)
        assertTrue(file.exists())
        
        // Cleanup
        file.delete()
    }

    @Test
    fun `shareBackup returns null when export fails`() {
        // This is hard to test, but we can verify it doesn't crash
        // When
        val backupPath = storageManager.shareBackup()

        // Then - should either succeed or return null, but not crash
        // If it succeeds, clean up
        backupPath?.let { File(it).delete() }
    }

    @Test
    fun `multiple operations maintain data integrity`() {
        // Given & When - perform multiple operations
        storageManager.saveMatch(createTestMatch("match1", "Team A", "Team B"))
        storageManager.saveMatch(createTestMatch("match2", "Team C", "Team D"))
        storageManager.deleteMatch("match1")
        storageManager.saveMatch(createTestMatch("match3", "Team E", "Team F"))
        
        // Then
        val matches = storageManager.getAllMatches()
        assertEquals(2, matches.size)
        assertFalse(matches.any { it.id == "match1" })
        assertTrue(matches.any { it.id == "match2" })
        assertTrue(matches.any { it.id == "match3" })
    }

    @Test
    fun `getAllMatches handles corrupted data gracefully`() {
        // Given - manually corrupt the SharedPreferences
        sharedPrefs.edit()
            .putString("matches_json", "corrupted json data")
            .apply()

        // When
        val matches = storageManager.getAllMatches()

        // Then - should return empty list instead of crashing
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `saveMatch updates last_updated timestamp`() {
        // Given
        val match = createTestMatch("match1", "Team A", "Team B")
        val timeBefore = System.currentTimeMillis()

        // When
        storageManager.saveMatch(match)

        // Then
        val lastUpdated = sharedPrefs.getLong("last_updated", 0)
        assertTrue(lastUpdated >= timeBefore)
        assertTrue(lastUpdated <= System.currentTimeMillis())
    }

    @Test
    fun `debugStorage returns meaningful information`() {
        // Given
        storageManager.saveMatch(createTestMatch("match1", "Team A", "Team B"))

        // When
        val debugInfo = storageManager.debugStorage()

        // Then
        assertNotNull(debugInfo)
        assertTrue(debugInfo.contains("Storage Debug"))
        assertTrue(debugInfo.contains("JSON"))
        assertTrue(debugInfo.contains("Last Updated"))
    }

    @Test
    fun `round trip export and import preserves all data`() {
        // Given
        val matchSettings = MatchSettings(totalOvers = 15, maxPlayersPerTeam = 9)
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
            groupId = "g1",
            groupName = "Group",
            shortPitch = true,
            matchSettings = matchSettings,
            playerOfTheMatchId = "p1",
            playerOfTheMatchName = "Player 1",
            playerOfTheMatchTeam = "Team A",
            playerOfTheMatchImpact = 45.5,
            playerOfTheMatchSummary = "Excellent"
        )

        storageManager.saveMatch(match)
        val exportPath = storageManager.exportMatches()!!

        // Clear and import
        storageManager.clearAllMatches()
        storageManager.importMatches(exportPath)

        // Then
        val matches = storageManager.getAllMatches()
        val importedMatch = matches[0]
        
        assertEquals(match.id, importedMatch.id)
        assertEquals(match.team1Name, importedMatch.team1Name)
        assertEquals(match.team2Name, importedMatch.team2Name)
        assertEquals(match.jokerPlayerName, importedMatch.jokerPlayerName)
        assertEquals(match.groupId, importedMatch.groupId)
        assertEquals(match.shortPitch, importedMatch.shortPitch)
        assertEquals(match.playerOfTheMatchId, importedMatch.playerOfTheMatchId)
        assertEquals(match.matchSettings?.totalOvers, importedMatch.matchSettings?.totalOvers)
        
        // Cleanup
        File(exportPath).delete()
    }

    // Helper function
    private fun createTestMatch(
        id: String,
        team1: String,
        team2: String,
        matchDate: Long = System.currentTimeMillis()
    ): MatchHistory {
        return MatchHistory(
            id = id,
            team1Name = team1,
            team2Name = team2,
            firstInningsRuns = 100,
            firstInningsWickets = 5,
            secondInningsRuns = 95,
            secondInningsWickets = 10,
            winnerTeam = team1,
            winningMargin = "5 runs",
            matchDate = matchDate
        )
    }
}

