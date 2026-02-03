package com.oreki.stumpd.data.mappers

import com.google.gson.Gson
import com.oreki.stumpd.MatchSettings
import com.oreki.stumpd.TossChoice
import com.oreki.stumpd.data.local.entity.MatchEntity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MappersTest {

    private val gson = Gson()

    @Test
    fun `MatchEntity toDomain converts all basic fields correctly`() {
        // Given
        val entity = MatchEntity(
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
            matchSettingsJson = null
        )

        // When
        val domain = entity.toDomain()

        // Then
        assertEquals("match1", domain.id)
        assertEquals("Team A", domain.team1Name)
        assertEquals("Team B", domain.team2Name)
        assertEquals("Joker", domain.jokerPlayerName)
        assertEquals(120, domain.firstInningsRuns)
        assertEquals(7, domain.firstInningsWickets)
        assertEquals(115, domain.secondInningsRuns)
        assertEquals(10, domain.secondInningsWickets)
        assertEquals("Team A", domain.winnerTeam)
        assertEquals("5 runs", domain.winningMargin)
        assertEquals(1234567890L, domain.matchDate)
        assertEquals("group1", domain.groupId)
        assertEquals("Test Group", domain.groupName)
        assertTrue(domain.shortPitch)
        assertEquals("p1", domain.playerOfTheMatchId)
        assertEquals("Player 1", domain.playerOfTheMatchName)
        assertEquals("Team A", domain.playerOfTheMatchTeam)
        assertEquals(55.5, domain.playerOfTheMatchImpact ?: 0.0, 0.01)
        assertEquals("Great performance", domain.playerOfTheMatchSummary)
    }

    @Test
    fun `MatchEntity toDomain initializes empty lists for stats`() {
        // Given
        val entity = createTestMatchEntity()

        // When
        val domain = entity.toDomain()

        // Then
        assertTrue(domain.firstInningsBatting.isEmpty())
        assertTrue(domain.firstInningsBowling.isEmpty())
        assertTrue(domain.secondInningsBatting.isEmpty())
        assertTrue(domain.secondInningsBowling.isEmpty())
        assertTrue(domain.playerImpacts.isEmpty())
    }

    @Test
    fun `MatchEntity toDomain sets null for top performers`() {
        // Given
        val entity = createTestMatchEntity()

        // When
        val domain = entity.toDomain()

        // Then
        assertNull(domain.topBatsman)
        assertNull(domain.topBowler)
    }

    @Test
    fun `MatchEntity toDomain deserializes match settings from JSON`() {
        // Given
        val matchSettings = MatchSettings(
            totalOvers = 10,
            maxPlayersPerTeam = 11,
            noballRuns = 1,
            byeRuns = 1,
            legByeRuns = 1,
            legSideWideRuns = 1,
            offSideWideRuns = 1,
            powerplayOvers = 3,
            maxOversPerBowler = 2,
            enforceFollowOn = false,
            duckworthLewisMethod = false,
            jokerCanBatAndBowl = true,
            jokerMaxOvers = 1,
            tossWinnerChoice = TossChoice.BAT_FIRST,
            enableSuperOver = false,
            shortPitch = true
        )
        val settingsJson = gson.toJson(matchSettings)

        val entity = createTestMatchEntity(matchSettingsJson = settingsJson)

        // When
        val domain = entity.toDomain()

        // Then
        assertNotNull(domain.matchSettings)
        assertEquals(10, domain.matchSettings?.totalOvers)
        assertEquals(11, domain.matchSettings?.maxPlayersPerTeam)
        assertEquals(1, domain.matchSettings?.noballRuns)
        assertEquals(3, domain.matchSettings?.powerplayOvers)
        assertEquals(2, domain.matchSettings?.maxOversPerBowler)
        assertTrue(domain.matchSettings?.jokerCanBatAndBowl ?: false)
        assertEquals(1, domain.matchSettings?.jokerMaxOvers)
        assertEquals(TossChoice.BAT_FIRST, domain.matchSettings?.tossWinnerChoice)
        assertTrue(domain.matchSettings?.shortPitch ?: false)
    }

    @Test
    fun `MatchEntity toDomain handles null match settings JSON`() {
        // Given
        val entity = createTestMatchEntity(matchSettingsJson = null)

        // When
        val domain = entity.toDomain()

        // Then
        assertNull(domain.matchSettings)
    }

    @Test
    fun `MatchEntity toDomain handles invalid match settings JSON`() {
        // Given
        val entity = createTestMatchEntity(matchSettingsJson = "invalid json")

        // When/Then - should not throw exception
        val domain = entity.toDomain()
        // Invalid JSON should result in null
        assertNull(domain.matchSettings)
    }

    @Test
    fun `MatchEntity toDomain handles null joker player name`() {
        // Given
        val entity = createTestMatchEntity(jokerPlayerName = null)

        // When
        val domain = entity.toDomain()

        // Then
        assertNull(domain.jokerPlayerName)
    }

    @Test
    fun `MatchEntity toDomain handles null group information`() {
        // Given
        val entity = createTestMatchEntity(groupId = null, groupName = null)

        // When
        val domain = entity.toDomain()

        // Then
        assertNull(domain.groupId)
        assertNull(domain.groupName)
    }

    @Test
    fun `MatchEntity toDomain handles null player of the match information`() {
        // Given
        val entity = createTestMatchEntity(
            playerOfTheMatchId = null,
            playerOfTheMatchName = null,
            playerOfTheMatchTeam = null,
            playerOfTheMatchImpact = null,
            playerOfTheMatchSummary = null
        )

        // When
        val domain = entity.toDomain()

        // Then
        assertNull(domain.playerOfTheMatchId)
        assertNull(domain.playerOfTheMatchName)
        assertNull(domain.playerOfTheMatchTeam)
        assertNull(domain.playerOfTheMatchImpact)
        assertNull(domain.playerOfTheMatchSummary)
    }

    @Test
    fun `MatchEntity toDomain preserves shortPitch flag`() {
        // Given
        val entityShortPitch = createTestMatchEntity(shortPitch = true)
        val entityLongPitch = createTestMatchEntity(shortPitch = false)

        // When
        val domainShortPitch = entityShortPitch.toDomain()
        val domainLongPitch = entityLongPitch.toDomain()

        // Then
        assertTrue(domainShortPitch.shortPitch)
        assertFalse(domainLongPitch.shortPitch)
    }

    @Test
    fun `MatchEntity toDomain handles empty string fields`() {
        // Given
        val entity = MatchEntity(
            id = "",
            team1Name = "",
            team2Name = "",
            jokerPlayerName = "",
            firstInningsRuns = 0,
            firstInningsWickets = 0,
            secondInningsRuns = 0,
            secondInningsWickets = 0,
            winnerTeam = "",
            winningMargin = "",
            matchDate = 0L,
            groupId = "",
            groupName = "",
            shortPitch = false,
            playerOfTheMatchId = "",
            playerOfTheMatchName = "",
            playerOfTheMatchTeam = "",
            playerOfTheMatchImpact = 0.0,
            playerOfTheMatchSummary = "",
            matchSettingsJson = ""
        )

        // When
        val domain = entity.toDomain()

        // Then - should handle empty strings gracefully
        assertEquals("", domain.id)
        assertEquals("", domain.team1Name)
        assertEquals("", domain.team2Name)
        assertEquals("", domain.jokerPlayerName)
        assertEquals("", domain.winnerTeam)
        assertEquals("", domain.winningMargin)
    }

    @Test
    fun `MatchEntity toDomain handles zero values correctly`() {
        // Given
        val entity = MatchEntity(
            id = "match1",
            team1Name = "Team A",
            team2Name = "Team B",
            jokerPlayerName = null,
            firstInningsRuns = 0,
            firstInningsWickets = 0,
            secondInningsRuns = 0,
            secondInningsWickets = 0,
            winnerTeam = "Team A",
            winningMargin = "Forfeit",
            matchDate = 0L,
            groupId = null,
            groupName = null,
            shortPitch = false,
            playerOfTheMatchId = null,
            playerOfTheMatchName = null,
            playerOfTheMatchTeam = null,
            playerOfTheMatchImpact = 0.0,
            playerOfTheMatchSummary = null,
            matchSettingsJson = null
        )

        // When
        val domain = entity.toDomain()

        // Then
        assertEquals(0, domain.firstInningsRuns)
        assertEquals(0, domain.firstInningsWickets)
        assertEquals(0, domain.secondInningsRuns)
        assertEquals(0, domain.secondInningsWickets)
        assertEquals(0L, domain.matchDate)
    }

    @Test
    fun `MatchEntity toDomain handles all wickets out scenario`() {
        // Given
        val entity = createTestMatchEntity(
            firstInningsWickets = 10,
            secondInningsWickets = 10
        )

        // When
        val domain = entity.toDomain()

        // Then
        assertEquals(10, domain.firstInningsWickets)
        assertEquals(10, domain.secondInningsWickets)
    }

    @Test
    fun `MatchEntity toDomain handles match settings with all fields`() {
        // Given
        val matchSettings = MatchSettings(
            totalOvers = 20,
            maxPlayersPerTeam = 11,
            allowSingleSideBatting = true,
            noballRuns = 2,
            byeRuns = 1,
            legByeRuns = 1,
            legSideWideRuns = 2,
            offSideWideRuns = 1,
            powerplayOvers = 6,
            maxOversPerBowler = 4,
            enforceFollowOn = true,
            duckworthLewisMethod = false,
            jokerCanBatAndBowl = true,
            jokerMaxOvers = 2,
            tossWinnerChoice = TossChoice.BOWL_FIRST,
            enableSuperOver = true,
            shortPitch = false
        )
        val settingsJson = gson.toJson(matchSettings)

        val entity = createTestMatchEntity(matchSettingsJson = settingsJson)

        // When
        val domain = entity.toDomain()

        // Then
        val settings = domain.matchSettings
        assertNotNull(settings)
        assertEquals(20, settings?.totalOvers)
        assertEquals(11, settings?.maxPlayersPerTeam)
        assertTrue(settings?.allowSingleSideBatting ?: false)
        assertEquals(2, settings?.noballRuns)
        assertEquals(1, settings?.byeRuns)
        assertEquals(1, settings?.legByeRuns)
        assertEquals(2, settings?.legSideWideRuns)
        assertEquals(1, settings?.offSideWideRuns)
        assertEquals(6, settings?.powerplayOvers)
        assertEquals(4, settings?.maxOversPerBowler)
        assertTrue(settings?.enforceFollowOn ?: false)
        assertFalse(settings?.duckworthLewisMethod ?: true)
        assertTrue(settings?.jokerCanBatAndBowl ?: false)
        assertEquals(2, settings?.jokerMaxOvers)
        assertEquals(TossChoice.BOWL_FIRST, settings?.tossWinnerChoice)
        assertTrue(settings?.enableSuperOver ?: false)
        assertFalse(settings?.shortPitch ?: true)
    }

    // Helper function
    private fun createTestMatchEntity(
        id: String = "match1",
        team1Name: String = "Team A",
        team2Name: String = "Team B",
        jokerPlayerName: String? = null,
        firstInningsRuns: Int = 100,
        firstInningsWickets: Int = 5,
        secondInningsRuns: Int = 95,
        secondInningsWickets: Int = 10,
        winnerTeam: String = "Team A",
        winningMargin: String = "5 runs",
        matchDate: Long = 1234567890L,
        groupId: String? = null,
        groupName: String? = null,
        shortPitch: Boolean = false,
        playerOfTheMatchId: String? = null,
        playerOfTheMatchName: String? = null,
        playerOfTheMatchTeam: String? = null,
        playerOfTheMatchImpact: Double? = null,
        playerOfTheMatchSummary: String? = null,
        matchSettingsJson: String? = null
    ): MatchEntity {
        return MatchEntity(
            id = id,
            team1Name = team1Name,
            team2Name = team2Name,
            jokerPlayerName = jokerPlayerName,
            firstInningsRuns = firstInningsRuns,
            firstInningsWickets = firstInningsWickets,
            secondInningsRuns = secondInningsRuns,
            secondInningsWickets = secondInningsWickets,
            winnerTeam = winnerTeam,
            winningMargin = winningMargin,
            matchDate = matchDate,
            groupId = groupId,
            groupName = groupName,
            shortPitch = shortPitch,
            playerOfTheMatchId = playerOfTheMatchId,
            playerOfTheMatchName = playerOfTheMatchName,
            playerOfTheMatchTeam = playerOfTheMatchTeam,
            playerOfTheMatchImpact = playerOfTheMatchImpact,
            playerOfTheMatchSummary = playerOfTheMatchSummary,
            matchSettingsJson = matchSettingsJson
        )
    }
}

