package com.oreki.stumpd.data.mappers

import com.oreki.stumpd.PlayerMatchStats
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
fun Double.oversToBalls(): Int = (this * Constants.BALLS_PER_OVER).toInt()

/**
 * Converts balls (int) to overs (double format: 2.3 means 2 overs and 3 balls)
 */
fun Int.ballsToOvers(): Double {
    val completeOvers = this / Constants.BALLS_PER_OVER
    val remainingBalls = this % Constants.BALLS_PER_OVER
    return completeOvers + (remainingBalls * 0.1)
}

