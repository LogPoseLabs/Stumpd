package com.oreki.stumpd.ui.scoring

import com.oreki.stumpd.domain.model.Player
import com.oreki.stumpd.domain.model.PlayerId
import com.oreki.stumpd.domain.model.PlayerMatchStats
import com.oreki.stumpd.domain.model.WicketType
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Saved matches store a bowler's workload as "overs" in cricket notation — 1.3 means one over
 * and three balls, not one and a third. Live scoring stores the ball count instead, which is the
 * only form you can safely add up, so everything here works in balls and converts at the edge.
 */
fun ballsFromOversNotation(overs: Double): Int {
    if (overs <= 0.0) return 0
    // Player.oversBowled builds this figure with floating point, so 9 balls arrives as
    // 1.3000000000000003; nudge before truncating.
    val completedOvers = floor(overs + 1e-6).toInt()
    val balls = ((overs - completedOvers) * 10).roundToInt().coerceIn(0, 5)
    return completedOvers * 6 + balls
}

/** Renders a ball count the way a scoreboard does: 19 balls is "3.1", not "3.17". */
fun formatBallsAsOvers(balls: Int): String = "${balls / 6}.${balls % 6}"

/** Runs per over off the balls actually bowled. */
fun runRatePerOver(runs: Int, balls: Int): Double = if (balls > 0) runs * 6.0 / balls else 0.0

/**
 * A saved innings row in the form the scorecard renders. Only the fields the scorecard shows are
 * carried across; the id is preserved so rows stay distinguishable when two players share a name.
 */
fun PlayerMatchStats.toScorecardPlayer(): Player = Player(
    id = PlayerId(id),
    name = name,
    runs = runs,
    ballsFaced = ballsFaced,
    dots = dots,
    singles = singles,
    twos = twos,
    threes = threes,
    fours = fours,
    sixes = sixes,
    isOut = isOut,
    isRetired = isRetired,
    wickets = wickets,
    runsConceded = runsConceded,
    ballsBowled = ballsFromOversNotation(oversBowled),
    maidenOvers = maidenOvers,
    isJoker = isJoker,
    catches = catches,
    runOuts = runOuts,
    stumpings = stumpings,
    dismissalType = dismissalType?.let { type -> WicketType.entries.firstOrNull { it.name == type } },
    bowlerName = bowlerName,
    fielderName = fielderName,
)
