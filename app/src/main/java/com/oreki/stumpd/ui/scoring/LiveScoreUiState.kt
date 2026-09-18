package com.oreki.stumpd.ui.scoring

import androidx.compose.runtime.Immutable
import com.oreki.stumpd.domain.model.MatchSettings

/**
 * The scalar state the live scoring tab renders: the score, where the innings is up to, and the
 * flags that decide which controls make sense.
 *
 * `LiveScoreTab` used to take 42 parameters, hand-unpacked from the view model at the call site —
 * including a `Context` it never used. Grouping the scalars here leaves the tab's signature
 * readable and gives one place to look when adding a figure to the scoreboard.
 *
 * Deliberately holds no `Player` or delivery lists: those are mutable objects behind the scenes
 * (`ScoringViewModel` keeps `MutableList<Player>` so undo and resume work), and claiming they're
 * immutable here would let Compose skip a recomposition it needs to do. They stay as their own
 * parameters until they can be mapped to real snapshots.
 */
@Immutable
data class LiveScoreUiState(
    val battingTeamName: String,
    val currentInnings: Int,
    /** What the side in progress must beat, or null while it is setting the target. */
    val runsToChase: Int? = null,
    val matchSettings: MatchSettings,
    val totalRuns: Int,
    val totalWickets: Int,
    val currentOver: Int,
    val ballsInOver: Int,
    val totalExtras: Int,
    val firstInningsRuns: Int,
    val showSingleSideLayout: Boolean,
    val availableBatsmen: Int,
    val currentBowlerSpell: Int,
    val isInningsComplete: Boolean,
    val isPowerplayActive: Boolean,
    val partnership: PartnershipLine,
)

/** The stand in progress, as the scoreboard shows it. */
@Immutable
data class PartnershipLine(
    val runs: Int = 0,
    val balls: Int = 0,
    val batsman1Name: String? = null,
    val batsman2Name: String? = null,
    val batsman1Runs: Int = 0,
    val batsman2Runs: Int = 0,
    val batsman1Balls: Int = 0,
    val batsman2Balls: Int = 0,
)

/**
 * What the scorer can do from the live tab. Grouped so the tab takes one parameter rather than
 * ten, and so the set is visible in one place when adding a control.
 *
 * Remember the instance at the call site: rebuilding the lambdas every recomposition would defeat
 * the point.
 */
@Immutable
data class LiveScoreActions(
    val onSelectStriker: () -> Unit,
    val onSelectNonStriker: () -> Unit,
    val onSelectBowler: () -> Unit,
    val onSwapStrike: () -> Unit,
    val onScoreRuns: (Int) -> Unit,
    val onShowExtras: () -> Unit,
    val onShowWicket: () -> Unit,
    val onUndo: () -> Unit,
    val onWide: () -> Unit,
    val onRetire: () -> Unit,
    /** Opens the mid-match "fix a mistake" menu. Null where corrections don't apply. */
    val onFix: (() -> Unit)? = null,
)
