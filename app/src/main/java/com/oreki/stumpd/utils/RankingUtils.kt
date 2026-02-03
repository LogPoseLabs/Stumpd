package com.oreki.stumpd.utils

import com.oreki.stumpd.PlayerDetailedStats

/**
 * Utility functions for calculating player rankings
 * Based on 40% Batting • 40% Bowling • 20% Fielding
 */
object RankingUtils {
    
    /**
     * Calculate batting score (0-100 scale)
     * Considers: Total runs, batting average, strike rate
     */
    fun calculateBattingScore(player: PlayerDetailedStats, allPlayers: List<PlayerDetailedStats>): Double {
        if (player.totalRuns == 0) return 0.0
        
        val maxRuns = allPlayers.maxOfOrNull { it.totalRuns } ?: 1
        val maxAvg = allPlayers.filter { it.timesOut > 0 }.maxOfOrNull { it.battingAverage } ?: 1.0
        val maxSR = allPlayers.filter { it.totalBallsFaced > 0 }.maxOfOrNull { it.strikeRate } ?: 1.0
        
        val runsScore = (player.totalRuns.toDouble() / maxRuns) * 40 // 40% weight
        val avgScore = if (player.timesOut > 0) (player.battingAverage / maxAvg) * 30 else 0.0 // 30% weight
        val srScore = if (player.totalBallsFaced > 0) (player.strikeRate / maxSR) * 30 else 0.0 // 30% weight
        
        return runsScore + avgScore + srScore
    }
    
    /**
     * Calculate bowling score (0-100 scale)
     * Considers: Total wickets, economy rate, bowling average
     */
    fun calculateBowlingScore(player: PlayerDetailedStats, allPlayers: List<PlayerDetailedStats>): Double {
        if (player.totalWickets == 0) return 0.0
        
        val maxWickets = allPlayers.maxOfOrNull { it.totalWickets } ?: 1
        val minEconomy = allPlayers.filter { it.totalBallsBowled > 0 && it.economyRate > 0 }
            .minOfOrNull { it.economyRate } ?: 1.0
        val minBowlAvg = allPlayers.filter { it.totalWickets > 0 && it.bowlingAverage > 0 }
            .minOfOrNull { it.bowlingAverage } ?: 1.0
        
        val wicketsScore = (player.totalWickets.toDouble() / maxWickets) * 40 // 40% weight
        // For economy and bowling avg, lower is better, so invert the ratio
        val econScore = if (player.economyRate > 0) (minEconomy / player.economyRate) * 30 else 0.0 // 30% weight
        val bowlAvgScore = if (player.bowlingAverage > 0) (minBowlAvg / player.bowlingAverage) * 30 else 0.0 // 30% weight
        
        return wicketsScore + econScore + bowlAvgScore
    }
    
    /**
     * Calculate fielding score (0-100 scale)
     * Considers: Catches, run outs, stumpings
     */
    fun calculateFieldingScore(player: PlayerDetailedStats, allPlayers: List<PlayerDetailedStats>): Double {
        val totalFielding = player.totalCatches + player.totalRunOuts + player.totalStumpings
        if (totalFielding == 0) return 0.0
        
        val maxFielding = allPlayers.maxOfOrNull { 
            it.totalCatches + it.totalRunOuts + it.totalStumpings 
        } ?: 1
        
        return (totalFielding.toDouble() / maxFielding) * 100
    }
    
    /**
     * Calculate overall player score
     * 40% Batting + 40% Bowling + 20% Fielding
     */
    fun calculateOverallScore(player: PlayerDetailedStats, allPlayers: List<PlayerDetailedStats>): Double {
        val battingScore = calculateBattingScore(player, allPlayers)
        val bowlingScore = calculateBowlingScore(player, allPlayers)
        val fieldingScore = calculateFieldingScore(player, allPlayers)
        return (battingScore * 0.4) + (bowlingScore * 0.4) + (fieldingScore * 0.2)
    }
}



