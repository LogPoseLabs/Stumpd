package com.oreki.stumpd.data.mappers

import com.oreki.stumpd.domain.model.PlayerMatchStats
import com.oreki.stumpd.data.local.entity.PlayerMatchStatsEntity
import com.oreki.stumpd.data.util.Constants

/**
 * Consolidated extension functions for entity-to-domain conversions
 */

/**
 * Converts PlayerMatchStatsEntity to domain PlayerMatchStats
 */
fun PlayerMatchStatsEntity.toDomain(): PlayerMatchStats {
    return PlayerMatchStats(
        id = this.playerId,
        name = this.name,
        team = this.team,
        role = this.role,
        runs = this.runs,
        ballsFaced = this.ballsFaced,
        dots = this.dots,
        singles = this.singles,
        twos = this.twos,
        threes = this.threes,
        fours = this.fours,
        sixes = this.sixes,
        wickets = this.wickets,
        runsConceded = this.runsConceded,
        oversBowled = this.oversBowled,
        maidenOvers = this.maidenOvers,
        isOut = this.isOut,
        isRetired = this.isRetired,
        isJoker = this.isJoker,
        catches = this.catches,
        runOuts = this.runOuts,
        stumpings = this.stumpings,
        dismissalType = this.dismissalType,
        bowlerName = this.bowlerName,
        fielderName = this.fielderName,
        battingPosition = this.battingPosition,
        bowlingPosition = this.bowlingPosition
    )
}

/**
 * Converts overs (double) to total balls (int)
 */
fun Double.oversToBalls(): Int {
    if (this <= 0.0) return 0
    // Cricket notation, not a decimal: 1.3 overs is nine balls, so it cannot be `overs * 6`
    // (which gives seven). The fractional digit counts balls out of six.
    val completedOvers = kotlin.math.floor(this + 1e-6).toInt()
    val balls = ((this - completedOvers) * 10).let { Math.round(it).toInt() }.coerceIn(0, 5)
    return completedOvers * Constants.BALLS_PER_OVER + balls
}

/**
 * Converts balls (int) to overs (double format: 2.3 means 2 overs and 3 balls)
 */
fun Int.ballsToOvers(): Double {
    val completeOvers = this / Constants.BALLS_PER_OVER
    val remainingBalls = this % Constants.BALLS_PER_OVER
    return completeOvers + (remainingBalls * 0.1)
}

