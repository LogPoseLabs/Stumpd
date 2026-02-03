package com.oreki.stumpd

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScoringActivityHelpersTest {

    private lateinit var gson: Gson

    @Before
    fun setup() {
        gson = Gson()
    }

    @Test
    fun `toJsonString converts player list to JSON correctly`() {
        // Given
        val players = listOf(
            Player(id = PlayerId("1"), name = "Player 1", runs = 50, ballsFaced = 30),
            Player(id = PlayerId("2"), name = "Player 2", runs = 25, ballsFaced = 20)
        )

        // When
        val json = players.toJsonString(gson)

        // Then
        assertThat(json).isNotEmpty()
        assertThat(json).contains("Player 1")
        assertThat(json).contains("Player 2")
    }

    @Test
    fun `toPlayerList converts JSON to player list correctly`() {
        // Given
        val json = """[{"id":{"value":"1"},"name":"Player 1","runs":50,"ballsFaced":30}]"""

        // When
        val players = json.toPlayerList(gson)

        // Then
        assertThat(players).hasSize(1)
        assertThat(players[0].name).isEqualTo("Player 1")
        assertThat(players[0].runs).isEqualTo(50)
        assertThat(players[0].ballsFaced).isEqualTo(30)
    }

    @Test
    fun `toPlayerList returns empty list for invalid JSON`() {
        // Given
        val invalidJson = "invalid json {{"

        // When
        val players = invalidJson.toPlayerList(gson)

        // Then
        assertThat(players).isEmpty()
    }

    @Test
    fun `toPlayerList returns empty list for empty JSON array`() {
        // Given
        val emptyJson = "[]"

        // When
        val players = emptyJson.toPlayerList(gson)

        // Then
        assertThat(players).isEmpty()
    }

    @Test
    fun `createMatchInProgress creates match with correct basic info`() {
        // Given
        val team1Players = listOf(
            Player(id = PlayerId("1"), name = "Player 1", runs = 50)
        )
        val team2Players = listOf(
            Player(id = PlayerId("2"), name = "Player 2", runs = 0)
        )

        // When
        val match = createMatchInProgress(
            matchId = "match1",
            team1Name = "Team A",
            team2Name = "Team B",
            jokerName = "Joker",
            team1PlayerIds = listOf("1"),
            team2PlayerIds = listOf("2"),
            team1PlayerNames = listOf("Player 1"),
            team2PlayerNames = listOf("Player 2"),
            matchSettingsJson = """{"totalOvers":20}""",
            groupId = "group1",
            groupName = "Saturday",
            tossWinner = "Team A",
            tossChoice = "bat",
            currentInnings = 1,
            currentOver = 5,
            ballsInOver = 3,
            totalWickets = 0,
            team1Players = team1Players,
            team2Players = team2Players,
            strikerIndex = 0,
            nonStrikerIndex = null,
            bowlerIndex = 0,
            firstInningsRuns = 0,
            firstInningsWickets = 0,
            firstInningsOvers = 0,
            firstInningsBalls = 0,
            bowlingTeamPlayers = team2Players,
            totalExtras = 5,
            wides = 3,
            noBalls = 2,
            byes = 0,
            legByes = 0,
            completedBattersInnings1 = emptyList(),
            completedBattersInnings2 = emptyList(),
            completedBowlersInnings1 = emptyList(),
            completedBowlersInnings2 = emptyList(),
            firstInningsBattingPlayers = emptyList(),
            firstInningsBowlingPlayers = emptyList(),
            jokerOutInCurrentInnings = false,
            jokerBallsBowledInnings1 = 0,
            jokerBallsBowledInnings2 = 0,
            gson = gson
        )

        // Then
        assertThat(match.matchId).isEqualTo("match1")
        assertThat(match.team1Name).isEqualTo("Team A")
        assertThat(match.team2Name).isEqualTo("Team B")
        assertThat(match.jokerName).isEqualTo("Joker")
        assertThat(match.currentInnings).isEqualTo(1)
        assertThat(match.currentOver).isEqualTo(5)
        assertThat(match.ballsInOver).isEqualTo(3)
    }

    @Test
    fun `createMatchInProgress calculates total runs correctly for innings 1`() {
        // Given
        val team1Players = listOf(
            Player(id = PlayerId("1"), name = "Player 1", runs = 50),
            Player(id = PlayerId("2"), name = "Player 2", runs = 25)
        )
        val team2Players = listOf(
            Player(id = PlayerId("3"), name = "Player 3", runs = 0)
        )

        // When
        val match = createMatchInProgress(
            matchId = "match1",
            team1Name = "Team A",
            team2Name = "Team B",
            jokerName = "Joker",
            team1PlayerIds = listOf("1", "2"),
            team2PlayerIds = listOf("3"),
            team1PlayerNames = listOf("Player 1", "Player 2"),
            team2PlayerNames = listOf("Player 3"),
            matchSettingsJson = """{"totalOvers":20}""",
            groupId = null,
            groupName = null,
            tossWinner = null,
            tossChoice = null,
            currentInnings = 1,
            currentOver = 5,
            ballsInOver = 3,
            totalWickets = 0,
            team1Players = team1Players,
            team2Players = team2Players,
            strikerIndex = 0,
            nonStrikerIndex = 1,
            bowlerIndex = 0,
            firstInningsRuns = 0,
            firstInningsWickets = 0,
            firstInningsOvers = 0,
            firstInningsBalls = 0,
            bowlingTeamPlayers = team2Players,
            totalExtras = 10,
            wides = 0,
            noBalls = 0,
            byes = 0,
            legByes = 0,
            completedBattersInnings1 = emptyList(),
            completedBattersInnings2 = emptyList(),
            completedBowlersInnings1 = emptyList(),
            completedBowlersInnings2 = emptyList(),
            firstInningsBattingPlayers = emptyList(),
            firstInningsBowlingPlayers = emptyList(),
            jokerOutInCurrentInnings = false,
            jokerBallsBowledInnings1 = 0,
            jokerBallsBowledInnings2 = 0,
            gson = gson
        )

        // Then
        // Calculated total = 50 + 25 + 10 = 85
        assertThat(match.calculatedTotalRuns).isEqualTo(85)
    }

    @Test
    fun `createMatchInProgress calculates total runs correctly for innings 2`() {
        // Given
        val team1Players = listOf(
            Player(id = PlayerId("1"), name = "Player 1", runs = 0)
        )
        val team2Players = listOf(
            Player(id = PlayerId("2"), name = "Player 2", runs = 30),
            Player(id = PlayerId("3"), name = "Player 3", runs = 20)
        )

        // When
        val match = createMatchInProgress(
            matchId = "match1",
            team1Name = "Team A",
            team2Name = "Team B",
            jokerName = "Joker",
            team1PlayerIds = listOf("1"),
            team2PlayerIds = listOf("2", "3"),
            team1PlayerNames = listOf("Player 1"),
            team2PlayerNames = listOf("Player 2", "Player 3"),
            matchSettingsJson = """{"totalOvers":20}""",
            groupId = null,
            groupName = null,
            tossWinner = null,
            tossChoice = null,
            currentInnings = 2,
            currentOver = 3,
            ballsInOver = 2,
            totalWickets = 0,
            team1Players = team1Players,
            team2Players = team2Players,
            strikerIndex = 0,
            nonStrikerIndex = 1,
            bowlerIndex = 0,
            firstInningsRuns = 100,
            firstInningsWickets = 5,
            firstInningsOvers = 20,
            firstInningsBalls = 0,
            bowlingTeamPlayers = team1Players,
            totalExtras = 5,
            wides = 0,
            noBalls = 0,
            byes = 0,
            legByes = 0,
            completedBattersInnings1 = emptyList(),
            completedBattersInnings2 = emptyList(),
            completedBowlersInnings1 = emptyList(),
            completedBowlersInnings2 = emptyList(),
            firstInningsBattingPlayers = emptyList(),
            firstInningsBowlingPlayers = emptyList(),
            jokerOutInCurrentInnings = false,
            jokerBallsBowledInnings1 = 0,
            jokerBallsBowledInnings2 = 0,
            gson = gson
        )

        // Then
        // Calculated total = 30 + 20 + 5 = 55
        assertThat(match.calculatedTotalRuns).isEqualTo(55)
    }

    @Test
    fun `createMatchInProgress serializes player lists to JSON`() {
        // Given
        val team1Players = listOf(
            Player(id = PlayerId("1"), name = "Player 1", runs = 50)
        )
        val team2Players = listOf(
            Player(id = PlayerId("2"), name = "Player 2", runs = 25)
        )
        val completedBatters = listOf(
            Player(id = PlayerId("3"), name = "Player 3", runs = 30, isOut = true)
        )

        // When
        val match = createMatchInProgress(
            matchId = "match1",
            team1Name = "Team A",
            team2Name = "Team B",
            jokerName = "Joker",
            team1PlayerIds = listOf("1"),
            team2PlayerIds = listOf("2"),
            team1PlayerNames = listOf("Player 1"),
            team2PlayerNames = listOf("Player 2"),
            matchSettingsJson = """{"totalOvers":20}""",
            groupId = null,
            groupName = null,
            tossWinner = null,
            tossChoice = null,
            currentInnings = 1,
            currentOver = 5,
            ballsInOver = 3,
            totalWickets = 0,
            team1Players = team1Players,
            team2Players = team2Players,
            strikerIndex = 0,
            nonStrikerIndex = null,
            bowlerIndex = 0,
            firstInningsRuns = 0,
            firstInningsWickets = 0,
            firstInningsOvers = 0,
            firstInningsBalls = 0,
            bowlingTeamPlayers = team2Players,
            totalExtras = 0,
            wides = 0,
            noBalls = 0,
            byes = 0,
            legByes = 0,
            completedBattersInnings1 = completedBatters,
            completedBattersInnings2 = emptyList(),
            completedBowlersInnings1 = emptyList(),
            completedBowlersInnings2 = emptyList(),
            firstInningsBattingPlayers = emptyList(),
            firstInningsBowlingPlayers = emptyList(),
            jokerOutInCurrentInnings = false,
            jokerBallsBowledInnings1 = 0,
            jokerBallsBowledInnings2 = 0,
            gson = gson
        )

        // Then
        assertThat(match.team1PlayersJson).isNotEmpty()
        assertThat(match.team2PlayersJson).isNotEmpty()
        assertThat(match.completedBattersInnings1Json).isNotEmpty()
        assertThat(match.completedBattersInnings1Json).contains("Player 3")
    }

    @Test
    fun `createMatchInProgress preserves all state fields`() {
        // Given
        val team1Players = listOf(Player(id = PlayerId("1"), name = "Player 1"))
        val team2Players = listOf(Player(id = PlayerId("2"), name = "Player 2"))

        // When
        val match = createMatchInProgress(
            matchId = "match1",
            team1Name = "Team A",
            team2Name = "Team B",
            jokerName = "Joker",
            team1PlayerIds = listOf("1"),
            team2PlayerIds = listOf("2"),
            team1PlayerNames = listOf("Player 1"),
            team2PlayerNames = listOf("Player 2"),
            matchSettingsJson = """{"totalOvers":20}""",
            groupId = "group1",
            groupName = "Saturday",
            tossWinner = "Team A",
            tossChoice = "bowl",
            currentInnings = 2,
            currentOver = 15,
            ballsInOver = 4,
            totalWickets = 5,
            team1Players = team1Players,
            team2Players = team2Players,
            strikerIndex = 1,
            nonStrikerIndex = 0,
            bowlerIndex = 2,
            firstInningsRuns = 150,
            firstInningsWickets = 7,
            firstInningsOvers = 20,
            firstInningsBalls = 0,
            bowlingTeamPlayers = team1Players,
            totalExtras = 12,
            wides = 5,
            noBalls = 3,
            byes = 2,
            legByes = 2,
            completedBattersInnings1 = emptyList(),
            completedBattersInnings2 = emptyList(),
            completedBowlersInnings1 = emptyList(),
            completedBowlersInnings2 = emptyList(),
            firstInningsBattingPlayers = emptyList(),
            firstInningsBowlingPlayers = emptyList(),
            jokerOutInCurrentInnings = true,
            jokerBallsBowledInnings1 = 6,
            jokerBallsBowledInnings2 = 12,
            gson = gson
        )

        // Then
        assertThat(match.groupId).isEqualTo("group1")
        assertThat(match.groupName).isEqualTo("Saturday")
        assertThat(match.tossWinner).isEqualTo("Team A")
        assertThat(match.tossChoice).isEqualTo("bowl")
        assertThat(match.strikerIndex).isEqualTo(1)
        assertThat(match.nonStrikerIndex).isEqualTo(0)
        assertThat(match.bowlerIndex).isEqualTo(2)
        assertThat(match.firstInningsRuns).isEqualTo(150)
        assertThat(match.firstInningsWickets).isEqualTo(7)
        assertThat(match.totalWickets).isEqualTo(5)
        assertThat(match.wides).isEqualTo(5)
        assertThat(match.noBalls).isEqualTo(3)
        assertThat(match.byes).isEqualTo(2)
        assertThat(match.legByes).isEqualTo(2)
        assertThat(match.jokerOutInCurrentInnings).isTrue()
        assertThat(match.jokerBallsBowledInnings1).isEqualTo(6)
        assertThat(match.jokerBallsBowledInnings2).isEqualTo(12)
    }
}

