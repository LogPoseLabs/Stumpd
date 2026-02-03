package com.oreki.stumpd

import org.junit.Assert.*
import org.junit.Test

class DomainModelsTest {

    @Test
    fun `Player strikeRate calculation is correct`() {
        // Given
        val player = Player(
            name = "Test Player",
            runs = 50,
            ballsFaced = 40
        )

        // When
        val strikeRate = player.strikeRate

        // Then
        assertEquals(125.0, strikeRate, 0.01)
    }

    @Test
    fun `Player strikeRate returns zero when no balls faced`() {
        // Given
        val player = Player(
            name = "Test Player",
            runs = 50,
            ballsFaced = 0
        )

        // When
        val strikeRate = player.strikeRate

        // Then
        assertEquals(0.0, strikeRate, 0.01)
    }

    @Test
    fun `Player oversBowled calculation is correct for full overs`() {
        // Given
        val player = Player(
            name = "Test Player",
            ballsBowled = 12 // 2 full overs
        )

        // When
        val overs = player.oversBowled

        // Then
        assertEquals(2.0, overs, 0.01)
    }

    @Test
    fun `Player oversBowled calculation is correct for partial overs`() {
        // Given
        val player = Player(
            name = "Test Player",
            ballsBowled = 14 // 2 overs and 2 balls = 2.2
        )

        // When
        val overs = player.oversBowled

        // Then
        assertEquals(2.2, overs, 0.01)
    }

    @Test
    fun `Player oversBowled calculation handles 5 balls in over`() {
        // Given
        val player = Player(
            name = "Test Player",
            ballsBowled = 11 // 1 over and 5 balls = 1.5
        )

        // When
        val overs = player.oversBowled

        // Then
        assertEquals(1.5, overs, 0.01)
    }

    @Test
    fun `Player economy calculation is correct`() {
        // Given
        val player = Player(
            name = "Test Player",
            runsConceded = 30,
            ballsBowled = 12 // 2 overs
        )

        // When
        val economy = player.economy

        // Then
        assertEquals(15.0, economy, 0.01) // 30 runs in 2 overs = 15 per over
    }

    @Test
    fun `Player economy returns zero when no balls bowled`() {
        // Given
        val player = Player(
            name = "Test Player",
            runsConceded = 30,
            ballsBowled = 0
        )

        // When
        val economy = player.economy

        // Then
        assertEquals(0.0, economy, 0.01)
    }

    @Test
    fun `Player toMatchStats converts correctly`() {
        // Given
        val player = Player(
            id = PlayerId("p1"),
            name = "Test Player",
            runs = 50,
            ballsFaced = 30,
            fours = 4,
            sixes = 2,
            isOut = true,
            wickets = 2,
            runsConceded = 25,
            ballsBowled = 12,
            isJoker = false
        )
        val teamName = "Team A"

        // When
        val matchStats = player.toMatchStats(teamName)

        // Then
        assertEquals("p1", matchStats.id)
        assertEquals("Test Player", matchStats.name)
        assertEquals(50, matchStats.runs)
        assertEquals(30, matchStats.ballsFaced)
        assertEquals(4, matchStats.fours)
        assertEquals(2, matchStats.sixes)
        assertTrue(matchStats.isOut)
        assertEquals(2, matchStats.wickets)
        assertEquals(25, matchStats.runsConceded)
        assertEquals(2.0, matchStats.oversBowled, 0.01)
        assertFalse(matchStats.isJoker)
        assertEquals(teamName, matchStats.team)
    }

    @Test
    fun `PlayerMatchStats strikeRate calculation is correct`() {
        // Given
        val stats = PlayerMatchStats(
            id = "p1",
            name = "Player",
            runs = 75,
            ballsFaced = 50
        )

        // When
        val strikeRate = stats.strikeRate

        // Then
        assertEquals(150.0, strikeRate, 0.01)
    }

    @Test
    fun `PlayerMatchStats strikeRate returns zero when no balls faced`() {
        // Given
        val stats = PlayerMatchStats(
            id = "p1",
            name = "Player",
            runs = 50,
            ballsFaced = 0
        )

        // When
        val strikeRate = stats.strikeRate

        // Then
        assertEquals(0.0, strikeRate, 0.01)
    }

    @Test
    fun `PlayerMatchStats economy calculation is correct`() {
        // Given
        val stats = PlayerMatchStats(
            id = "p1",
            name = "Player",
            runsConceded = 42,
            oversBowled = 3.5 // 3.5 overs
        )

        // When
        val economy = stats.economy

        // Then
        assertEquals(12.0, economy, 0.01) // 42/3.5 = 12
    }

    @Test
    fun `PlayerMatchStats economy returns zero when no overs bowled`() {
        // Given
        val stats = PlayerMatchStats(
            id = "p1",
            name = "Player",
            runsConceded = 30,
            oversBowled = 0.0
        )

        // When
        val economy = stats.economy

        // Then
        assertEquals(0.0, economy, 0.01)
    }

    @Test
    fun `Team regularPlayersCount excludes jokers`() {
        // Given
        val team = Team(
            name = "Test Team",
            players = mutableListOf(
                Player(name = "Player 1", isJoker = false),
                Player(name = "Player 2", isJoker = false),
                Player(name = "Joker", isJoker = true),
                Player(name = "Player 3", isJoker = false)
            )
        )

        // When
        val count = team.regularPlayersCount

        // Then
        assertEquals(3, count)
    }

    @Test
    fun `Team regularPlayersCount returns zero when only jokers`() {
        // Given
        val team = Team(
            name = "Test Team",
            players = mutableListOf(
                Player(name = "Joker 1", isJoker = true),
                Player(name = "Joker 2", isJoker = true)
            )
        )

        // When
        val count = team.regularPlayersCount

        // Then
        assertEquals(0, count)
    }

    @Test
    fun `Team regularPlayersCount returns correct count when no players`() {
        // Given
        val team = Team(name = "Test Team", players = mutableListOf())

        // When
        val count = team.regularPlayersCount

        // Then
        assertEquals(0, count)
    }

    @Test
    fun `PlayerId generates unique IDs`() {
        // When
        val id1 = PlayerId()
        val id2 = PlayerId()

        // Then
        assertNotEquals(id1.value, id2.value)
    }

    @Test
    fun `PlayerId can be created with specific value`() {
        // Given
        val value = "custom-id-123"

        // When
        val id = PlayerId(value)

        // Then
        assertEquals(value, id.value)
    }

    @Test
    fun `ExtraType has correct display names`() {
        // Then
        assertEquals("No Ball", ExtraType.NO_BALL.displayName)
        assertEquals("Off Side Wide", ExtraType.OFF_SIDE_WIDE.displayName)
        assertEquals("Leg Side Wide", ExtraType.LEG_SIDE_WIDE.displayName)
        assertEquals("Bye", ExtraType.BYE.displayName)
        assertEquals("Leg Bye", ExtraType.LEG_BYE.displayName)
    }

    @Test
    fun `WicketType has all standard types`() {
        // Then - verify all wicket types exist
        val types = WicketType.values()
        assertEquals(7, types.size)
        assertTrue(types.contains(WicketType.BOWLED))
        assertTrue(types.contains(WicketType.CAUGHT))
        assertTrue(types.contains(WicketType.LBW))
        assertTrue(types.contains(WicketType.RUN_OUT))
        assertTrue(types.contains(WicketType.STUMPED))
        assertTrue(types.contains(WicketType.HIT_WICKET))
        assertTrue(types.contains(WicketType.BOUNDARY_OUT))
    }

    @Test
    fun `TossChoice has correct display names`() {
        // Then
        assertEquals("Bat First", TossChoice.BAT_FIRST.displayName)
        assertEquals("Bowl First", TossChoice.BOWL_FIRST.displayName)
    }

    @Test
    fun `BallFormat has both types`() {
        // Then
        val formats = BallFormat.values()
        assertEquals(2, formats.size)
        assertTrue(formats.contains(BallFormat.WHITE_BALL))
        assertTrue(formats.contains(BallFormat.RED_BALL))
    }

    @Test
    fun `MatchSettings default values are correct`() {
        // When
        val settings = MatchSettings()

        // Then
        assertEquals(5, settings.totalOvers)
        assertEquals(11, settings.maxPlayersPerTeam)
        assertTrue(settings.allowSingleSideBatting)
        assertEquals(0, settings.noballRuns)
        assertEquals(0, settings.byeRuns)
        assertEquals(0, settings.legByeRuns)
        assertEquals(1, settings.legSideWideRuns)
        assertEquals(0, settings.offSideWideRuns)
        assertEquals(0, settings.powerplayOvers)
        assertEquals(2, settings.maxOversPerBowler)
        assertFalse(settings.enforceFollowOn)
        assertFalse(settings.duckworthLewisMethod)
        assertTrue(settings.jokerCanBatAndBowl)
        assertEquals(1, settings.jokerMaxOvers)
        assertEquals(TossChoice.BAT_FIRST, settings.tossWinnerChoice)
        assertFalse(settings.enableSuperOver)
        assertTrue(settings.shortPitch)
    }

    @Test
    fun `GlobalSettings default values are correct`() {
        // When
        val settings = GlobalSettings()

        // Then
        assertEquals(MatchSettings(), settings.defaultMatchSettings)
        assertTrue(settings.autoSaveMatch)
        assertTrue(settings.soundEffects)
        assertTrue(settings.vibrationFeedback)
        assertFalse(settings.darkMode)
        assertFalse(settings.expertMode)
    }

    @Test
    fun `Player calculates multiple statistics correctly`() {
        // Given
        val player = Player(
            name = "All-rounder",
            runs = 60,
            ballsFaced = 40,
            fours = 5,
            sixes = 3,
            wickets = 3,
            runsConceded = 35,
            ballsBowled = 18, // 3 overs
            isOut = true
        )

        // When & Then
        assertEquals(150.0, player.strikeRate, 0.01)
        assertEquals(3.0, player.oversBowled, 0.01)
        assertEquals(11.67, player.economy, 0.01) // 35 runs in 3 overs
        assertEquals(60, player.runs)
        assertEquals(3, player.wickets)
        assertTrue(player.isOut)
    }

    @Test
    fun `PlayerMatchStats handles both batting and bowling stats`() {
        // Given
        val stats = PlayerMatchStats(
            id = "p1",
            name = "All-rounder",
            runs = 45,
            ballsFaced = 30,
            fours = 4,
            sixes = 2,
            wickets = 2,
            runsConceded = 28,
            oversBowled = 2.5,
            isOut = false,
            team = "Team A"
        )

        // When & Then
        assertEquals(150.0, stats.strikeRate, 0.01)
        assertEquals(11.2, stats.economy, 0.01)
        assertEquals(45, stats.runs)
        assertEquals(2, stats.wickets)
        assertFalse(stats.isOut)
    }

    @Test
    fun `RunOutEnd has both options`() {
        // Then
        val ends = RunOutEnd.values()
        assertEquals(2, ends.size)
        assertTrue(ends.contains(RunOutEnd.STRIKER_END))
        assertTrue(ends.contains(RunOutEnd.NON_STRIKER_END))
    }

    @Test
    fun `NoBallSubOutcome has all options`() {
        // Then
        val outcomes = NoBallSubOutcome.values()
        assertEquals(3, outcomes.size)
        assertTrue(outcomes.contains(NoBallSubOutcome.NONE))
        assertTrue(outcomes.contains(NoBallSubOutcome.RUN_OUT))
        assertTrue(outcomes.contains(NoBallSubOutcome.BOUNDARY_OUT))
    }

    @Test
    fun `Player economy handles fractional overs correctly`() {
        // Given
        val player = Player(
            name = "Bowler",
            runsConceded = 25,
            ballsBowled = 15 // 2.3 overs
        )

        // When
        val economy = player.economy

        // Then
        // 2.3 overs = 15 balls, 25 runs / 15 balls * 6 = 10.0
        assertEquals(10.0, economy, 0.01)
    }

    @Test
    fun `Player with zero stats returns zero for calculations`() {
        // Given
        val player = Player(name = "New Player")

        // When & Then
        assertEquals(0.0, player.strikeRate, 0.01)
        assertEquals(0.0, player.oversBowled, 0.01)
        assertEquals(0.0, player.economy, 0.01)
        assertEquals(0, player.runs)
        assertEquals(0, player.ballsFaced)
        assertEquals(0, player.wickets)
        assertEquals(0, player.ballsBowled)
    }

    @Test
    fun `PlayerMatchStats with high values calculates correctly`() {
        // Given
        val stats = PlayerMatchStats(
            id = "p1",
            name = "Star Player",
            runs = 150,
            ballsFaced = 80,
            fours = 15,
            sixes = 5,
            wickets = 5,
            runsConceded = 60,
            oversBowled = 5.0
        )

        // When & Then
        assertEquals(187.5, stats.strikeRate, 0.01)
        assertEquals(12.0, stats.economy, 0.01)
    }

    @Test
    fun `DeliveryUI contains required fields`() {
        // Given
        val delivery = DeliveryUI(
            inning = 1,
            over = 3,
            ballInOver = 4,
            outcome = "4",
            highlight = true
        )

        // Then
        assertEquals(1, delivery.inning)
        assertEquals(3, delivery.over)
        assertEquals(4, delivery.ballInOver)
        assertEquals("4", delivery.outcome)
        assertTrue(delivery.highlight)
    }

    @Test
    fun `PlayerImpact contains all required fields`() {
        // Given
        val impact = PlayerImpact(
            id = "p1",
            name = "Player",
            team = "Team A",
            impact = 55.5,
            summary = "Great performance",
            isJoker = false,
            runs = 50,
            balls = 30,
            fours = 4,
            sixes = 2,
            wickets = 2,
            runsConceded = 25,
            oversBowled = 2.0
        )

        // Then
        assertEquals("p1", impact.id)
        assertEquals("Player", impact.name)
        assertEquals("Team A", impact.team)
        assertEquals(55.5, impact.impact, 0.01)
        assertEquals("Great performance", impact.summary)
        assertFalse(impact.isJoker)
        assertEquals(50, impact.runs)
        assertEquals(30, impact.balls)
        assertEquals(4, impact.fours)
        assertEquals(2, impact.sixes)
        assertEquals(2, impact.wickets)
        assertEquals(25, impact.runsConceded)
        assertEquals(2.0, impact.oversBowled, 0.01)
    }
}



