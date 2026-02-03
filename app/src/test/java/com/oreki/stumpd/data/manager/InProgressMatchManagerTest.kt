package com.oreki.stumpd.data.manager

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import com.oreki.stumpd.data.models.MatchInProgress
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class InProgressMatchManagerTest {

    private lateinit var context: Context
    private lateinit var manager: InProgressMatchManager

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        manager = InProgressMatchManager(context)
        // Clear any existing data
        manager.clearMatch()
    }

    @Test
    fun `saveMatch stores match data correctly`() {
        // Given
        val match = createSampleMatch()

        // When
        manager.saveMatch(match)

        // Then
        assertThat(manager.hasInProgressMatch()).isTrue()
    }

    @Test
    fun `loadMatch returns correct match data`() {
        // Given
        val match = createSampleMatch()
        manager.saveMatch(match)

        // When
        val loaded = manager.loadMatch()

        // Then
        assertThat(loaded).isNotNull()
        assertThat(loaded?.matchId).isEqualTo(match.matchId)
        assertThat(loaded?.team1Name).isEqualTo(match.team1Name)
        assertThat(loaded?.team2Name).isEqualTo(match.team2Name)
        assertThat(loaded?.currentInnings).isEqualTo(match.currentInnings)
        assertThat(loaded?.currentOver).isEqualTo(match.currentOver)
        assertThat(loaded?.ballsInOver).isEqualTo(match.ballsInOver)
    }

    @Test
    fun `loadMatch returns null when no match saved`() {
        // When
        val loaded = manager.loadMatch()

        // Then
        assertThat(loaded).isNull()
    }

    @Test
    fun `clearMatch removes saved data`() {
        // Given
        val match = createSampleMatch()
        manager.saveMatch(match)
        assertThat(manager.hasInProgressMatch()).isTrue()

        // When
        manager.clearMatch()

        // Then
        assertThat(manager.hasInProgressMatch()).isFalse()
        assertThat(manager.loadMatch()).isNull()
    }

    @Test
    fun `hasInProgressMatch returns false when no match saved`() {
        // When & Then
        assertThat(manager.hasInProgressMatch()).isFalse()
    }

    @Test
    fun `hasInProgressMatch returns true when match is saved`() {
        // Given
        val match = createSampleMatch()

        // When
        manager.saveMatch(match)

        // Then
        assertThat(manager.hasInProgressMatch()).isTrue()
    }

    @Test
    fun `loadMatch handles corrupted JSON gracefully`() {
        // Given
        val prefs = context.getSharedPreferences("in_progress_match", Context.MODE_PRIVATE)
        prefs.edit().putString("match_in_progress_json", "invalid json {{{").apply()

        // When
        val loaded = manager.loadMatch()

        // Then
        assertThat(loaded).isNull()
        assertThat(manager.hasInProgressMatch()).isFalse() // Should clear on error
    }

    @Test
    fun `saveMatch overwrites previous match`() {
        // Given
        val match1 = createSampleMatch()
        val match2 = createSampleMatch().copy(
            matchId = "match_2",
            team1Name = "Updated Team 1",
            currentOver = 5
        )

        // When
        manager.saveMatch(match1)
        manager.saveMatch(match2)

        // Then
        val loaded = manager.loadMatch()
        assertThat(loaded?.matchId).isEqualTo("match_2")
        assertThat(loaded?.team1Name).isEqualTo("Updated Team 1")
        assertThat(loaded?.currentOver).isEqualTo(5)
    }

    @Test
    fun `saveMatch preserves all match state fields`() {
        // Given
        val match = createSampleMatch().copy(
            currentInnings = 2,
            currentOver = 10,
            ballsInOver = 3,
            totalWickets = 5,
            firstInningsRuns = 150,
            firstInningsWickets = 8,
            totalExtras = 15,
            jokerOutInCurrentInnings = true,
            jokerBallsBowledInnings1 = 12
        )

        // When
        manager.saveMatch(match)
        val loaded = manager.loadMatch()

        // Then
        assertThat(loaded?.currentInnings).isEqualTo(2)
        assertThat(loaded?.currentOver).isEqualTo(10)
        assertThat(loaded?.ballsInOver).isEqualTo(3)
        assertThat(loaded?.totalWickets).isEqualTo(5)
        assertThat(loaded?.firstInningsRuns).isEqualTo(150)
        assertThat(loaded?.firstInningsWickets).isEqualTo(8)
        assertThat(loaded?.totalExtras).isEqualTo(15)
        assertThat(loaded?.jokerOutInCurrentInnings).isTrue()
        assertThat(loaded?.jokerBallsBowledInnings1).isEqualTo(12)
    }

    @Test
    fun `saveMatch preserves player JSON data`() {
        // Given
        val playersJson = """[{"id":"1","name":"Player 1","runs":50}]"""
        val match = createSampleMatch().copy(
            team1PlayersJson = playersJson,
            team2PlayersJson = playersJson
        )

        // When
        manager.saveMatch(match)
        val loaded = manager.loadMatch()

        // Then
        assertThat(loaded?.team1PlayersJson).isEqualTo(playersJson)
        assertThat(loaded?.team2PlayersJson).isEqualTo(playersJson)
    }

    private fun createSampleMatch() = MatchInProgress(
        matchId = "test_match_1",
        team1Name = "Team A",
        team2Name = "Team B",
        jokerName = "Joker",
        groupId = "group1",
        groupName = "Saturday",
        tossWinner = "Team A",
        tossChoice = "bat",
        matchSettingsJson = """{"totalOvers":20}""",
        team1PlayerIds = listOf("p1", "p2"),
        team2PlayerIds = listOf("p3", "p4"),
        team1PlayerNames = listOf("Player 1", "Player 2"),
        team2PlayerNames = listOf("Player 3", "Player 4"),
        currentInnings = 1,
        currentOver = 5,
        ballsInOver = 3,
        totalWickets = 0,
        team1PlayersJson = """[]""",
        team2PlayersJson = """[]""",
        strikerIndex = 0,
        nonStrikerIndex = 1,
        bowlerIndex = 0,
        firstInningsRuns = 0,
        firstInningsWickets = 0,
        firstInningsOvers = 0,
        firstInningsBalls = 0,
        totalExtras = 0,
        calculatedTotalRuns = 50,
        wides = 0,
        noBalls = 0,
        byes = 0,
        legByes = 0,
        completedBattersInnings1Json = null,
        completedBattersInnings2Json = null,
        completedBowlersInnings1Json = null,
        completedBowlersInnings2Json = null,
        firstInningsBattingPlayersJson = null,
        firstInningsBowlingPlayersJson = null,
        jokerOutInCurrentInnings = false,
        jokerBallsBowledInnings1 = 0,
        jokerBallsBowledInnings2 = 0,
        lastSavedAt = System.currentTimeMillis(),
        startedAt = System.currentTimeMillis()
    )
}

