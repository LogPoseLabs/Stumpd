package com.oreki.stumpd

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.oreki.stumpd.data.manager.MatchPerformance
import com.oreki.stumpd.data.manager.PlayerDetailedStats
import org.junit.Test

/**
 * The all-players list leads with whichever metric it's sorted by. That used to be a 370-line
 * `when` of near-identical layouts inside a composable, so none of it could be checked; this
 * pins the figure each sort puts in the headline.
 */
class PlayerStatLineTest {

    private val player = PlayerDetailedStats(
        playerId = "p1",
        name = "Samhith",
        totalMatches = 10,
        totalRuns = 250,
        totalBallsFaced = 200,
        totalDots = 60,
        totalFours = 20,
        totalSixes = 10,
        totalWickets = 12,
        totalRunsConceded = 180,
        totalBallsBowled = 120,
        totalMaidenOvers = 2,
        timesOut = 8,
        notOuts = 2,
        totalCatches = 5,
        totalRunOuts = 1,
        totalStumpings = 0,
        totalWides = 7,
        totalNoBalls = 3,
        highestScore = 62,
        bestBowlingWickets = 4,
        bestBowlingRuns = 12,
    )

    @Test
    fun `the default sort leads with runs`() {
        val line = statLineFor("Runs", player)

        assertThat(line.label).isEqualTo("Runs")
        assertThat(line.value).isEqualTo("250")
        assertThat(line.detail).contains("HS 62")
    }

    @Test
    fun `an unrecognised sort falls back to runs rather than showing nothing`() {
        assertThat(statLineFor("Something New", player).value).isEqualTo("250")
    }

    @Test
    fun `each sort leads with its own metric`() {
        val expected = mapOf(
            "Highest Score" to "62",
            "Not Outs" to "2",
            "Balls Faced" to "200",
            "Boundaries" to "30",
            "Fours" to "20",
            "Sixes" to "10",
            "Wickets" to "12",
            "Best Bowling" to "4/12",
            "Maidens" to "2",
            "Extras" to "10",
            "Catches" to "5",
            "Run Outs" to "1",
            "Stumpings" to "0",
            "Matches" to "10",
        )

        expected.forEach { (sort, value) ->
            assertWithMessage("sorting by $sort").that(statLineFor(sort, player).value).isEqualTo(value)
        }
    }

    @Test
    fun `player of the match count comes from the caller, not the player record`() {
        // The award isn't stored on the player; it's counted from the filtered matches.
        assertThat(statLineFor("Player of the Match", player, potmAwards = 3).value).isEqualTo("3")
        assertThat(statLineFor("Player of the Match", player).value).isEqualTo("0")
    }

    @Test
    fun `ducks are shown as a count with the golden and diamond breakdown`() {
        val line = statLineFor("Ducks", player)

        assertThat(line.label).isEqualTo("Ducks")
        assertThat(line.tint).isEqualTo(StatTint.Error)
        assertThat(line.detail).contains("golden")
        assertThat(line.detail).contains("diamond")
    }

    @Test
    fun `an unrecorded dot ball percentage reads as unknown rather than zero`() {
        // Dots were only recorded from a later version. Showing 0.0% for those matches claimed
        // the batter never played out a dot.
        val noDots = player.copy(totalDots = 0, totalBallsFaced = 200)

        val line = statLineFor("Dot Ball %", noDots)

        assertThat(line.value).isEqualTo("—")
        assertThat(line.detail).contains("Not recorded")
    }

    @Test
    fun `a recorded dot ball percentage is shown as a percentage`() {
        assertThat(statLineFor("Dot Ball %", player).value).isEqualTo("30.0%")
    }

    @Test
    fun `bowling figures that need a wicket to mean anything show a dash without one`() {
        val noWickets = player.copy(totalWickets = 0)

        assertThat(statLineFor("Bowling Avg", noWickets).value).isEqualTo("—")
        assertThat(statLineFor("Bowling SR", noWickets).value).isEqualTo("—")
        // Economy is defined without wickets, so it still shows a figure.
        assertThat(statLineFor("Economy", noWickets).value).isEqualTo("9.0")
    }

    @Test
    fun `milestone scores count innings past the group's threshold`() {
        // Fifty is meaningless in a five-over game, so each group sets its own milestone.
        val withInnings = player.copy(
            matchPerformances = mutableListOf(
                performance(runs = 27),
                performance(runs = 21),
                performance(runs = 19),
                performance(runs = 4),
            ),
        )

        assertThat(statLineFor(SORT_MILESTONES, withInnings, milestone = 20).value).isEqualTo("2")
        assertThat(statLineFor(SORT_MILESTONES, withInnings, milestone = 25).value).isEqualTo("1")
        assertThat(statLineFor(SORT_MILESTONES, withInnings, milestone = 50).value).isEqualTo("0")
    }

    @Test
    fun `the milestone stat is labelled with the threshold`() {
        assertThat(statLineFor(SORT_MILESTONES, player, milestone = 20).label).isEqualTo("20+ scores")
        assertThat(sortLabel(SORT_MILESTONES, battingMilestone = 20)).isEqualTo("20s")
        assertThat(sortLabel(SORT_MILESTONES, battingMilestone = 30)).isEqualTo("30s")
    }

    @Test
    fun `the old fifties sort key still resolves`() {
        // A caller passing the pre-milestone key gets the milestone stat rather than nothing.
        assertThat(statLineFor("50s", player, milestone = 20).label).isEqualTo("20+ scores")
        assertThat(sortLabel("50s", battingMilestone = 20)).isEqualTo("20s")
    }

    @Test
    fun `sort keys that are not the milestone read as themselves`() {
        assertThat(sortLabel("Runs", battingMilestone = 20)).isEqualTo("Runs")
        assertThat(sortLabel("Best Bowling", battingMilestone = 20)).isEqualTo("Best Bowling")
    }

    private fun performance(runs: Int) = MatchPerformance(
        matchId = "m$runs",
        matchDate = 0L,
        opposingTeam = "B",
        myTeam = "A",
        runs = runs,
        ballsFaced = runs,
        isOut = true,
    )

    @Test
    fun `the ranking sorts label their figure as a rating and leave the value to the caller`() {
        listOf("Batting Ranking", "Bowling Ranking", "All-Rounder Ranking").forEach { sort ->
            val line = statLineFor(sort, player)
            assertWithMessage(sort).that(line.label).isEqualTo("rating")
            assertWithMessage(sort).that(line.value).isEmpty()
        }
    }

    @Test
    fun `bowling metrics are tinted apart from batting ones`() {
        assertThat(statLineFor("Wickets", player).tint).isEqualTo(StatTint.Tertiary)
        assertThat(statLineFor("Economy", player).tint).isEqualTo(StatTint.Tertiary)
        assertThat(statLineFor("Runs", player).tint).isEqualTo(StatTint.Primary)
        assertThat(statLineFor("Catches", player).tint).isEqualTo(StatTint.Secondary)
    }

    @Test
    fun `every sort the list offers produces a headline and a detail line`() {
        val allSorts = listOf(
            "Runs", "Highest Score", "Batting Avg", "Strike Rate", SORT_MILESTONES, "Not Outs",
            "Balls Faced", "Boundaries", "Fours", "Sixes", "Ducks", "Golden Ducks",
            "Diamond Ducks", "Boundary %", "Dot Ball %", "Consistency",
            "Wickets", "Best Bowling", "Bowling Avg", "Bowling SR", "Economy", "Maidens",
            "Extras", "Pressure Index",
            "Catches", "Run Outs", "Stumpings",
            "Matches", "Player of the Match",
            "Batting Ranking", "Bowling Ranking", "All-Rounder Ranking",
        )

        allSorts.forEach { sort ->
            val line = statLineFor(sort, player, potmAwards = 1)
            assertWithMessage("sorting by $sort").that(line.label).isNotEmpty()
            assertWithMessage("sorting by $sort").that(line.detail).isNotEmpty()
        }
    }
}
