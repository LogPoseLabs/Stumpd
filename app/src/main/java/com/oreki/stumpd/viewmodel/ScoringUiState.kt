package com.oreki.stumpd.viewmodel

import com.oreki.stumpd.domain.model.*

/**
 * Immutable snapshot of all observable state in the scoring screen.
 *
 * The [ScoringViewModel] exposes individual [androidx.compose.runtime.mutableStateOf]
 * properties for efficient Compose recomposition (avoids 86-field `copy()` on every
 * state change).  This data class is kept as a **reference specification** of every
 * field and is used by the undo-snapshot system ([DeliverySnapshot] remains the
 * lightweight version actually stored per delivery).
 */
data class ScoringUiState(
    // ── Team / Player state ──────────────────────────────────────────
    val team1Players: List<Player> = emptyList(),
    val team2Players: List<Player> = emptyList(),
    val battingTeamPlayers: List<Player> = emptyList(),
    val bowlingTeamPlayers: List<Player> = emptyList(),
    val battingTeamName: String = "",
    val bowlingTeamName: String = "",
    val jokerPlayer: Player? = null,

    // ── Match progress ───────────────────────────────────────────────
    val currentInnings: Int = 1,
    val currentOver: Int = 0,
    val ballsInOver: Int = 0,
    val totalWickets: Int = 0,
    val totalExtras: Int = 0,
    val calculatedTotalRuns: Int = 0,
    val firstInningsRuns: Int = 0,
    val firstInningsWickets: Int = 0,
    val firstInningsOvers: Int = 0,
    val firstInningsBalls: Int = 0,

    // ── Player positions ─────────────────────────────────────────────
    val strikerIndex: Int? = null,
    val nonStrikerIndex: Int? = null,
    val bowlerIndex: Int? = null,
    val previousBowlerName: String? = null,
    val currentBowlerSpell: Int = 0,
    val runsConcededInCurrentOver: Int = 0,

    // ── Partnership ──────────────────────────────────────────────────
    val currentPartnershipRuns: Int = 0,
    val currentPartnershipBalls: Int = 0,
    val currentPartnershipBatsman1Name: String? = null,
    val currentPartnershipBatsman2Name: String? = null,
    val currentPartnershipBatsman1Runs: Int = 0,
    val currentPartnershipBatsman2Runs: Int = 0,
    val currentPartnershipBatsman1Balls: Int = 0,
    val currentPartnershipBatsman2Balls: Int = 0,
    val partnerships: List<Partnership> = emptyList(),
    val fallOfWickets: List<FallOfWicket> = emptyList(),
    val firstInningsPartnerships: List<Partnership> = emptyList(),
    val firstInningsFallOfWickets: List<FallOfWicket> = emptyList(),

    // ── Completed players ────────────────────────────────────────────
    val completedBattersInnings1: List<Player> = emptyList(),
    val completedBattersInnings2: List<Player> = emptyList(),
    val completedBowlersInnings1: List<Player> = emptyList(),
    val completedBowlersInnings2: List<Player> = emptyList(),
    val firstInningsBattingPlayersList: List<Player> = emptyList(),
    val firstInningsBowlingPlayersList: List<Player> = emptyList(),
    val secondInningsBattingPlayers: List<Player> = emptyList(),
    val secondInningsBowlingPlayers: List<Player> = emptyList(),

    // ── Joker state ──────────────────────────────────────────────────
    val jokerOutInCurrentInnings: Boolean = false,
    val jokerBallsBowledInnings1: Int = 0,
    val jokerBallsBowledInnings2: Int = 0,
    val midOverReplacementDueToJoker: Boolean = false,

    // ── Delivery history ─────────────────────────────────────────────
    val allDeliveries: List<DeliveryUI> = emptyList(),

    // ── Dialog visibility ────────────────────────────────────────────
    val showBatsmanDialog: Boolean = false,
    val showBowlerDialog: Boolean = false,
    val showWicketDialog: Boolean = false,
    val showInningsBreakDialog: Boolean = false,
    val showMatchCompleteDialog: Boolean = false,
    val showExitDialog: Boolean = false,
    val showLiveScorecardDialog: Boolean = false,
    val showExtrasDialog: Boolean = false,
    val showQuickWideDialog: Boolean = false,
    val showQuickNoBallDialog: Boolean = false,
    val showRetirementDialog: Boolean = false,
    val showRunOutDialog: Boolean = false,
    val showFielderSelectionDialog: Boolean = false,
    val selectingBatsman: Int = 1,
    val retiringPosition: Int? = null,

    // ── Pending action state ─────────────────────────────────────────
    val pendingWicketType: WicketType? = null,
    val pendingRunOutInput: RunOutInput? = null,
    val pendingWideExtraType: ExtraType? = null,
    val pendingWideRuns: Int = 0,
    val pickerOtherEndName: String? = null,
    val pendingSwapAfterBatsmanPick: Boolean = false,
    val pendingBowlerDialogAfterBatsmanPick: Boolean = false,
    val isNoBallRunOut: Boolean = false,

    // ── Powerplay ────────────────────────────────────────────────────
    val powerplayRunsInnings1: Int = 0,
    val powerplayRunsInnings2: Int = 0,
    val powerplayDoublingDoneInnings1: Boolean = false,
    val powerplayDoublingDoneInnings2: Boolean = false,

    // ── Undo ─────────────────────────────────────────────────────────
    val unlimitedUndoEnabled: Boolean = false,

    // ── Match metadata ───────────────────────────────────────────────
    val matchSettings: MatchSettings = MatchSettings(),
    val isResuming: Boolean = false,
    val resumedMatchLoaded: Boolean = false,
    val isMatchSaved: Boolean = false,
    val savedMatchId: String? = null,
)
