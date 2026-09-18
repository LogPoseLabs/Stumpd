package com.oreki.stumpd.domain.model


data class MatchSettings(
    // Basic match settings
    val totalOvers: Int = 5,
    val maxPlayersPerTeam: Int = 11,
    /**
     * The score worth calling a milestone in this group's stats. Fifty is meaningless in a
     * five-over game — the record high score across 75 matches here is 27 — so this defaults to
     * twenty and each group can set its own.
     */
    val battingMilestone: Int = 20,
    val allowSingleSideBatting: Boolean = true, // One batsman continues if others get out
    // Extras settings - runs awarded
    val noballRuns: Int = 0,
    val byeRuns: Int = 0,
    val legByeRuns: Int = 0,
    val wideRuns: Int = 1, // Combined wide runs (replaces legSideWideRuns and offSideWideRuns)
    @Deprecated("Use wideRuns instead") val legSideWideRuns: Int = 1, // Kept for backward compatibility
    @Deprecated("Use wideRuns instead") val offSideWideRuns: Int = 0, // Kept for backward compatibility
    // Advanced rules
    val powerplayOvers: Int = 0, // First N overs are powerplay
    val doubleRunsInPowerplay: Boolean = false, // Double all runs scored once powerplay ends
    val maxOversPerBowler: Int = 2,
    val enforceFollowOn: Boolean = false,
    val duckworthLewisMethod: Boolean = false,
    // Joker rules
    /** Master switch: whether this match has a joker at all. */
    val jokerCanBatAndBowl: Boolean = true,
    /**
     * Whether the joker is allowed to bowl. A joker can always bat; bowling is optional because
     * the stand-in is often a batter only. Defaults to true so existing matches and group
     * defaults keep behaving as before.
     */
    val jokerCanBowl: Boolean = true,
    val jokerMaxOvers: Int = 1,
    // Match format
    val tossWinnerChoice: TossChoice = TossChoice.BAT_FIRST,
    val enableSuperOver: Boolean = false,
    val shortPitch: Boolean = true
)

/**
 * Why a match cannot legally start with these settings, or null when it can.
 *
 * Each fielding side has to cover every over of an innings without any bowler exceeding
 * [MatchSettings.maxOversPerBowler]. If it cannot, the bowler picker runs out of legal choices
 * partway through and the scorer is forced to break the cap to continue - so the cap silently
 * stops meaning anything. Better to refuse the settings up front than to discover it at over 4.
 *
 * The joker counts toward capacity only when he is enabled and allowed to bowl.
 */
fun MatchSettings.bowlingCapacityProblem(
    team1Name: String,
    team1Size: Int,
    team2Name: String,
    team2Size: Int,
): String? {
    if (totalOvers <= 0) return null

    val jokerOvers = if (jokerCanBatAndBowl && jokerCanBowl) jokerMaxOvers else 0
    val jokerBowlers = if (jokerCanBatAndBowl && jokerCanBowl) 1 else 0

    // A bowler may not bowl consecutive overs, so one bowler can never finish a 2+ over innings.
    if (totalOvers >= 2) {
        listOf(team1Name to team1Size, team2Name to team2Size).forEach { (name, size) ->
            if (size + jokerBowlers < 2) {
                return "$name needs at least 2 bowlers - the same bowler cannot bowl " +
                    "consecutive overs."
            }
        }
    }

    listOf(team1Name to team1Size, team2Name to team2Size).forEach { (name, size) ->
        if (size <= 0) return@forEach
        val capacity = size * maxOversPerBowler + jokerOvers
        if (capacity < totalOvers) {
            val neededCap = kotlin.math.ceil(totalOvers.toDouble() / size).toInt()
            val players = if (size == 1) "1 player" else "$size players"
            val perBowler = if (maxOversPerBowler == 1) "1 over" else "$maxOversPerBowler overs"
            val jokerPart = if (jokerOvers > 0) " + $jokerOvers joker" else ""
            return buildString {
                append("$name can only bowl $capacity of $totalOvers overs ")
                append("($players x $perBowler each$jokerPart). ")
                append("Raise max overs per bowler to $neededCap, add players, or reduce total overs.")
            }
        }
    }
    return null
}

enum class TossChoice(
    val displayName: String,
) {
    BAT_FIRST("Bat First"),
    BOWL_FIRST("Bowl First"),
}

data class GlobalSettings(
    val defaultMatchSettings: MatchSettings = MatchSettings(),
    val autoSaveMatch: Boolean = true,
    val soundEffects: Boolean = true,
    val vibrationFeedback: Boolean = true,
    val darkMode: Boolean = false,
    val expertMode: Boolean = false, // Shows advanced statistics
)
