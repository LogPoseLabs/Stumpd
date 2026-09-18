package com.oreki.stumpd.viewmodel

import android.app.Application
import com.oreki.stumpd.maxWicketsForSquad
import com.oreki.stumpd.domain.match.isSuperOverInnings
import com.oreki.stumpd.domain.match.inningsComplete
import com.oreki.stumpd.domain.match.SUPER_OVER_OVERS
import com.oreki.stumpd.domain.match.SUPER_OVER_MAX_WICKETS
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.oreki.stumpd.*
import com.oreki.stumpd.data.preferences.MatchSettingsManager
import com.oreki.stumpd.domain.model.*
import com.oreki.stumpd.data.local.db.StumpdDb
import com.oreki.stumpd.data.manager.InProgressMatchManager
import com.oreki.stumpd.data.repository.MatchRepository
import com.oreki.stumpd.data.repository.PlayerRepository
import com.oreki.stumpd.ui.scoring.ScoringEffect
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

sealed class ToastEvent {
    data class Short(val message: String) : ToastEvent()
    data class Long(val message: String) : ToastEvent()
}

data class ScoringInitParams(
    val matchId: String,
    val team1Name: String = "Team A",
    val team2Name: String = "Team B",
    val jokerName: String = "",
    val team1CaptainName: String? = null,
    val team2CaptainName: String? = null,
    val team1PlayerNames: List<String> = listOf("Player 1", "Player 2", "Player 3"),
    val team2PlayerNames: List<String> = listOf("Player 4", "Player 5", "Player 6"),
    val team1PlayerIds: List<String> = emptyList(),
    val team2PlayerIds: List<String> = emptyList(),
    val matchSettingsJson: String = "",
    val groupId: String? = null,
    val groupName: String? = null,
    val tossChoice: String? = null,
    val tossWinner: String? = null,
    val resumeMatchId: String? = null,
    /**
     * The tournament fixture being played, when this is one.
     *
     * [team1Id] and [team2Id] refer to [team1Name] and [team2Name] as set up — *not* to who bats
     * first, which the toss decides later.
     */
    val tournamentId: String? = null,
    val tournamentFixtureId: String? = null,
    val team1Id: String? = null,
    val team2Id: String? = null,
)

/**
 * ViewModel that owns **all** scoring state and delegates logic to:
 * - [ScoringEngine] for scoring, extras, wickets, partnerships, deliveries, undo, and innings
 * - [ScoringPersistence] for auto-save, resume, and Firebase auto-share
 * - [BatsmanBowlerSelection] for batsman/bowler selection, retirement, and joker helpers
 *
 * State is exposed as individual [mutableStateOf] properties so that Compose
 * recomposes only the composables that read the specific property that changed.
 */
class ScoringViewModel(
    application: Application,
    private val params: ScoringInitParams,
) : AndroidViewModel(application) {

    // ── Dependencies ────────────────────────────────────────────────
    private val context get() = getApplication<Application>()
    private val gson = Gson()
    private val db = StumpdDb.get(context)
    val repo = MatchRepository(db, context)
    val playerRepo = PlayerRepository(db, repo)
    val inProgressManager = InProgressMatchManager(context)

    // ── Delegates ────────────────────────────────────────────────────
    val engine = ScoringEngine(this)
    val persistence = ScoringPersistence(this, gson)
    val selection = BatsmanBowlerSelection(this)
    val corrections = LiveCorrections(this, engine)

    // ── UI events ───────────────────────────────────────────────────
    private val _toastEvent = MutableSharedFlow<ToastEvent>(extraBufferCapacity = 10)
    val toastEvent = _toastEvent.asSharedFlow()

    /**
     * Moments worth marking on screen: a boundary, a wicket, a milestone.
     *
     * Deliberately an event stream rather than state. Derived state would replay the celebration
     * on every recomposition and fire it again when a mid-match process death is restored.
     */
    private val _scoringEffect = MutableSharedFlow<ScoringEffect>(extraBufferCapacity = 4)
    val scoringEffect = _scoringEffect.asSharedFlow()

    internal fun emitEffect(effect: ScoringEffect) {
        _scoringEffect.tryEmit(effect)
    }
    internal fun toast(msg: String) { _toastEvent.tryEmit(ToastEvent.Short(msg)) }
    internal fun toastLong(msg: String) { _toastEvent.tryEmit(ToastEvent.Long(msg)) }

    // ── Params exposed for composables ──────────────────────────────
    val matchId: String = params.matchId
    val team1Name: String = params.team1Name
    val team2Name: String = params.team2Name
    val jokerName: String = params.jokerName
    val team1CaptainName: String? = params.team1CaptainName
    val team2CaptainName: String? = params.team2CaptainName
    val team1PlayerNames: List<String> = params.team1PlayerNames
    val team2PlayerNames: List<String> = params.team2PlayerNames
    val team1PlayerIds: List<String> = params.team1PlayerIds
    val team2PlayerIds: List<String> = params.team2PlayerIds
    val matchSettingsJson: String = params.matchSettingsJson
    val groupId: String? = params.groupId
    val groupName: String? = params.groupName
    val tossWinner: String? = params.tossWinner
    val tossChoice: String? = params.tossChoice

    /**
     * The tournament fixture being settled, keyed to [team1Name] / [team2Name].
     *
     * Vars rather than vals because a resumed match takes them from the in-progress row: the
     * intent that resumes carries them, but a match resumed after an update might not.
     */
    var tournamentId: String? = params.tournamentId
    var tournamentFixtureId: String? = params.tournamentFixtureId
    var team1Id: String? = params.team1Id
    var team2Id: String? = params.team2Id

    val matchSettings: MatchSettings = try {
        if (params.matchSettingsJson.isNotEmpty()) {
            gson.fromJson(params.matchSettingsJson, MatchSettings::class.java)
        } else {
            MatchSettingsManager(context).getDefaultMatchSettings()
        }
    } catch (_: Exception) { MatchSettings() }

    // ── ID → Name map ────────────────────────────────────────────────
    var idToName by mutableStateOf<Map<String, String>>(emptyMap()); private set
    var idToNameLoaded by mutableStateOf(false); private set

    // ── Team players ────────────────────────────────────────────────
    var team1Players by mutableStateOf<MutableList<Player>>(mutableListOf())
    var team2Players by mutableStateOf<MutableList<Player>>(mutableListOf())
    var battingTeamPlayers by mutableStateOf<MutableList<Player>>(mutableListOf())
    var bowlingTeamPlayers by mutableStateOf<MutableList<Player>>(mutableListOf())
    var battingTeamName by mutableStateOf("")
    var bowlingTeamName by mutableStateOf("")
    val jokerPlayer: Player? = if (params.jokerName.isNotEmpty()) Player(name = params.jokerName, isJoker = true) else null
    var initialBattingTeamName by mutableStateOf(params.team1Name); private set
    var initialBowlingTeamName by mutableStateOf(params.team2Name); private set

    // ── Match progress ──────────────────────────────────────────────
    var currentInnings by mutableStateOf(1)
    var currentOver by mutableStateOf(0)
    var ballsInOver by mutableStateOf(0)
    var totalWickets by mutableStateOf(0)
    var totalExtras by mutableStateOf(0)
    var firstInningsRuns by mutableStateOf(0)
    var firstInningsWickets by mutableStateOf(0)
    var firstInningsOvers by mutableStateOf(0)
    var firstInningsBalls by mutableStateOf(0)

    /**
     * The second innings' figures, snapshotted when it ends.
     *
     * The match save used to read these live off [calculatedTotalRuns] and [totalWickets], which
     * worked only because nothing ever came after innings 2. A super over resets exactly those, so
     * without a snapshot a match that went to an eliminator would save with the *super over's*
     * score as its second-innings total.
     */
    var secondInningsRuns by mutableStateOf(0)
    var secondInningsWickets by mutableStateOf(0)

    // ── Super over ──────────────────────────────────────────────────
    /**
     * The runs the side in progress has to beat, or null while it is setting the target.
     *
     * Replaces the old hard-coded "innings 2 chases [firstInningsRuns]" test so the same rule
     * covers a super over, where innings 4 chases innings 3.
     */
    var runsToChase by mutableStateOf<Int?>(null)

    /** One record per completed super-over innings, in order. Empty in an ordinary match. */
    var superOvers by mutableStateOf<List<SuperOverInnings>>(emptyList())

    /**
     * Who won the eliminator — a team name, or `"TIE"` if the scorer chose to leave it level.
     *
     * Null until a super over has been decided, which is also what tells the completion path the
     * result is settled and the match may finally be written.
     */
    var superOverWinner by mutableStateOf<String?>(null)

    /** Finished batters and bowlers per super-over innings, keyed 3, 4, 5 … */
    var completedBattersSuperOver by mutableStateOf<Map<Int, List<Player>>>(emptyMap())
    var completedBowlersSuperOver by mutableStateOf<Map<Int, List<Player>>>(emptyMap())

    val isSuperOver: Boolean get() = isSuperOverInnings(currentInnings)

    /** A super over is one over; everything else gets the match's allotment. */
    val oversForCurrentInnings: Int
        get() = if (isSuperOver) SUPER_OVER_OVERS else matchSettings.totalOvers

    /**
     * Two wickets end a super-over innings — clamped to the squad, since a small side may have
     * fewer than two to lose. Null outside a super over, where only the overs and the batters left
     * end an innings.
     */
    val wicketCapForCurrentInnings: Int?
        get() = if (!isSuperOver) {
            null
        } else {
            minOf(
                SUPER_OVER_MAX_WICKETS,
                maxWicketsForSquad(battingTeamPlayers.size, matchSettings.allowSingleSideBatting),
            ).coerceAtLeast(1)
        }

    // ── Player positions ────────────────────────────────────────────
    var strikerIndex by mutableStateOf<Int?>(null)
    var nonStrikerIndex by mutableStateOf<Int?>(null)
    var bowlerIndex by mutableStateOf<Int?>(null)
    var previousBowlerName by mutableStateOf<String?>(null)
    var currentBowlerSpell by mutableStateOf(0)
    var runsConcededInCurrentOver by mutableStateOf(0)
    val striker: Player? get() = strikerIndex?.let { battingTeamPlayers.getOrNull(it) }
    val nonStriker: Player? get() = nonStrikerIndex?.let { battingTeamPlayers.getOrNull(it) }
    val bowler: Player? get() = bowlerIndex?.let { bowlingTeamPlayers.getOrNull(it) }
    val calculatedTotalRuns: Int get() = battingTeamPlayers.sumOf { it.runs } + totalExtras

    // ── Partnership ─────────────────────────────────────────────────
    var currentPartnershipRuns by mutableStateOf(0)
    var currentPartnershipBalls by mutableStateOf(0)
    var currentPartnershipBatsman1Runs by mutableStateOf(0)
    var currentPartnershipBatsman2Runs by mutableStateOf(0)
    var currentPartnershipBatsman1Balls by mutableStateOf(0)
    var currentPartnershipBatsman2Balls by mutableStateOf(0)
    var currentPartnershipBatsman1Name by mutableStateOf<String?>(null)
    var currentPartnershipBatsman2Name by mutableStateOf<String?>(null)
    var partnerships by mutableStateOf<List<Partnership>>(emptyList())
    var fallOfWickets by mutableStateOf<List<FallOfWicket>>(emptyList())
    var firstInningsPartnerships by mutableStateOf<List<Partnership>>(emptyList())
    var firstInningsFallOfWickets by mutableStateOf<List<FallOfWicket>>(emptyList())

    /**
     * The second innings' stands and wickets, snapshotted when it ends.
     *
     * Previously the save read the *live* lists for innings 2, which a super-over wicket would
     * then append to — landing an eliminator dismissal in the second innings' fall of wickets.
     */
    var secondInningsPartnerships by mutableStateOf<List<Partnership>>(emptyList())
    var secondInningsFallOfWickets by mutableStateOf<List<FallOfWicket>>(emptyList())

    // ── Completed players ───────────────────────────────────────────
    var completedBattersInnings1 by mutableStateOf(mutableListOf<Player>())
    var completedBattersInnings2 by mutableStateOf(mutableListOf<Player>())
    var completedBowlersInnings1 by mutableStateOf(mutableListOf<Player>())
    var completedBowlersInnings2 by mutableStateOf(mutableListOf<Player>())
    var firstInningsBattingPlayersList by mutableStateOf<List<Player>>(emptyList())
    var firstInningsBowlingPlayersList by mutableStateOf<List<Player>>(emptyList())
    var secondInningsBattingPlayers by mutableStateOf<List<Player>>(emptyList())
    var secondInningsBowlingPlayers by mutableStateOf<List<Player>>(emptyList())

    // ── Joker state ─────────────────────────────────────────────────
    var jokerOutInCurrentInnings by mutableStateOf(false)
    var jokerBallsBowledInnings1 by mutableStateOf(0)
    var jokerBallsBowledInnings2 by mutableStateOf(0)
    val midOverReplacementDueToJoker = mutableStateOf(false)

    // ── Delivery history ────────────────────────────────────────────
    val deliveryHistory = mutableStateListOf<DeliverySnapshot>()
    val allDeliveries = mutableStateListOf<DeliveryUI>()
    val currentOverNumber: Int get() = currentOver + 1
    val currentOverDeliveries: List<DeliveryUI>
        get() = allDeliveries.filter { it.inning == currentInnings && it.over == currentOverNumber }

    // ── Powerplay ───────────────────────────────────────────────────
    var powerplayRunsInnings1 by mutableStateOf(0)
    var powerplayRunsInnings2 by mutableStateOf(0)
    var powerplayDoublingDoneInnings1 by mutableStateOf(false)
    var powerplayDoublingDoneInnings2 by mutableStateOf(false)
    val isPowerplayActive: Boolean
        get() = !isSuperOver &&
            matchSettings.powerplayOvers > 0 &&
            currentOver < matchSettings.powerplayOvers

    // ── Dialog visibility ───────────────────────────────────────────
    var showBatsmanDialog by mutableStateOf(false)
    var showBowlerDialog by mutableStateOf(false)
    var showWicketDialog by mutableStateOf(false)
    var showInningsBreakDialog by mutableStateOf(false)
    var showMatchCompleteDialog by mutableStateOf(false)
    var showExitDialog by mutableStateOf(false)
    var showLiveScorecardDialog by mutableStateOf(false)
    var showExtrasDialog by mutableStateOf(false)
    var showQuickWideDialog by mutableStateOf(false)
    var showQuickNoBallDialog by mutableStateOf(false)
    var showRetirementDialog by mutableStateOf(false)
    var showRunOutDialog by mutableStateOf(false)
    var showFielderSelectionDialog by mutableStateOf(false)

    /**
     * The "fix a mistake" surfaces: the menu, and one dialog per correction.
     *
     * Separate flags rather than one enum because every other dialog on this screen works that
     * way, and because the wicket celebration's "is anything covering the screen?" test is an OR
     * over all of them — a shape that only stays right if new dialogs are added to the same list.
     */
    var showFixMenu by mutableStateOf(false)
    var showFixWicketDialog by mutableStateOf(false)
    var showFixBowlerDialog by mutableStateOf(false)
    var showFixLastBallDialog by mutableStateOf(false)

    /**
     * The super-over surfaces.
     *
     * [showSuperOverOfferDialog] asks whether to play one — at the end of a tied match, and again
     * after a tied super over. [showSuperOverIntervalDialog] is the break between its two halves.
     * Both must be in `ScoringActivity`'s `surfaceObscured` list, or a wicket celebration fires
     * behind the scrim.
     */
    var showSuperOverOfferDialog by mutableStateOf(false)
    var showSuperOverIntervalDialog by mutableStateOf(false)
    var selectingBatsman by mutableStateOf(1)
    var retiringPosition by mutableStateOf<Int?>(null)

    // ── Pending action state ────────────────────────────────────────
    var pendingWicketType by mutableStateOf<WicketType?>(null)
    var pendingRunOutInput by mutableStateOf<RunOutInput?>(null)
    var pendingWideExtraType by mutableStateOf<ExtraType?>(null)
    var pendingWideRuns by mutableStateOf(0)
    var pickerOtherEndName by mutableStateOf<String?>(null)
    var pendingSwapAfterBatsmanPick by mutableStateOf(false)
    var pendingBowlerDialogAfterBatsmanPick by mutableStateOf(false)
    var isNoBallRunOut by mutableStateOf(false)

    // ── Match metadata ──────────────────────────────────────────────
    var isResuming by mutableStateOf(params.resumeMatchId != null)
    var resumedMatchLoaded by mutableStateOf(false)

    // ── Derived state ───────────────────────────────────────────────
    val jokerAvailableForBatting: Boolean
        get() = jokerPlayer != null && !battingTeamPlayers.any { it.isJoker } && !jokerOutInCurrentInnings
    val availableBatsmen: Int
        get() = battingTeamPlayers.count { !it.isOut } + if (jokerAvailableForBatting) 1 else 0
    val showSingleSideLayout: Boolean
        get() = matchSettings.allowSingleSideBatting && availableBatsmen == 1
    /**
     * The scalar state the live tab renders, in one object.
     *
     * A read-model: every field is derived from the properties above, so reading it in composition
     * subscribes to exactly the same state the hand-unpacked parameters did. Nothing here is
     * persisted — the saved match keeps its own shape.
     */
    val liveScoreUiState: com.oreki.stumpd.ui.scoring.LiveScoreUiState
        get() = com.oreki.stumpd.ui.scoring.LiveScoreUiState(
            battingTeamName = battingTeamName,
            currentInnings = currentInnings,
            runsToChase = runsToChase,
            matchSettings = matchSettings,
            totalRuns = calculatedTotalRuns,
            totalWickets = totalWickets,
            currentOver = currentOver,
            ballsInOver = ballsInOver,
            totalExtras = totalExtras,
            firstInningsRuns = firstInningsRuns,
            showSingleSideLayout = showSingleSideLayout,
            availableBatsmen = availableBatsmen,
            currentBowlerSpell = currentBowlerSpell,
            isInningsComplete = isInningsComplete,
            isPowerplayActive = isPowerplayActive,
            partnership = com.oreki.stumpd.ui.scoring.PartnershipLine(
                runs = currentPartnershipRuns,
                balls = currentPartnershipBalls,
                batsman1Name = currentPartnershipBatsman1Name,
                batsman2Name = currentPartnershipBatsman2Name,
                batsman1Runs = currentPartnershipBatsman1Runs,
                batsman2Runs = currentPartnershipBatsman2Runs,
                batsman1Balls = currentPartnershipBatsman1Balls,
                batsman2Balls = currentPartnershipBatsman2Balls,
            ),
        )

    /**
     * Whether the innings in progress is over.
     *
     * Delegates to the pure [inningsComplete] so a super over can share the rule. For innings 1
     * ([runsToChase] null, no wicket cap) and innings 2 ([runsToChase] = the first-innings total)
     * this is the same expression it has always been — an equivalence pinned by `SuperOverTest`.
     */
    val isInningsComplete: Boolean
        get() = inningsComplete(
            currentOver = currentOver,
            oversAllotted = oversForCurrentInnings,
            totalWickets = totalWickets,
            wicketCap = wicketCapForCurrentInnings,
            runs = calculatedTotalRuns,
            runsToChase = runsToChase,
            availableBatsmen = availableBatsmen,
            allowSingleSideBatting = matchSettings.allowSingleSideBatting,
        )

    // ═════════════════════════════════════════════════════════════════
    //  Initialization
    // ═════════════════════════════════════════════════════════════════

    init {
        viewModelScope.launch {
            val players = playerRepo.getAllPlayers()
            val map = players.associate { it.id to it.name }
            idToName = map
            idToNameLoaded = true
            rebuildTeams(map)
        }
    }

    private fun rebuildTeams(map: Map<String, String>) {
        team1Players = if (params.team1PlayerIds.isNotEmpty()) {
            params.team1PlayerIds.map { id -> Player(id = PlayerId(id), name = map[id] ?: id) }.toMutableList()
        } else {
            params.team1PlayerNames.map { Player(name = it) }.toMutableList()
        }
        team2Players = if (params.team2PlayerIds.isNotEmpty()) {
            params.team2PlayerIds.map { id -> Player(id = PlayerId(id), name = map[id] ?: id) }.toMutableList()
        } else {
            params.team2PlayerNames.map { Player(name = it) }.toMutableList()
        }
        applyTossAndSetTeams()
    }

    private fun applyTossAndSetTeams() {
        val tw = params.tossWinner; val tc = params.tossChoice
        if (tw != null && tc != null) {
            val team1Won = tw.equals(params.team1Name, ignoreCase = true)
            val battingChosen = tc.contains("Batting", ignoreCase = true)
            val team1BatsFirst = (team1Won && battingChosen) || (!team1Won && !battingChosen)
            if (team1BatsFirst) {
                battingTeamPlayers = team1Players; bowlingTeamPlayers = team2Players
                battingTeamName = params.team1Name; bowlingTeamName = params.team2Name
            } else {
                battingTeamPlayers = team2Players; bowlingTeamPlayers = team1Players
                battingTeamName = params.team2Name; bowlingTeamName = params.team1Name
            }
        } else {
            battingTeamPlayers = team1Players; bowlingTeamPlayers = team2Players
            battingTeamName = params.team1Name; bowlingTeamName = params.team2Name
        }
        initialBattingTeamName = battingTeamName; initialBowlingTeamName = bowlingTeamName
    }

    internal fun isTeam1BatsFirst(): Boolean {
        val tw = params.tossWinner; val tc = params.tossChoice
        return if (tw != null && tc != null) {
            val team1Won = tw.equals(params.team1Name, ignoreCase = true)
            val battingChosen = tc.contains("Batting", ignoreCase = true)
            (team1Won && battingChosen) || (!team1Won && !battingChosen)
        } else true
    }

    // ═════════════════════════════════════════════════════════════════
    //  Delegated public API (keeps ScoringActivity unchanged)
    // ═════════════════════════════════════════════════════════════════

    fun startFirebaseAutoShare() = persistence.startFirebaseAutoShare()
    fun resumeMatch() = persistence.resumeMatch()
    fun autoSaveMatch() = persistence.autoSaveMatch()

    fun addDelivery(outcome: String, highlight: Boolean = false, runs: Int = 0) = engine.addDelivery(outcome, highlight, runs)
    fun pushSnapshot() = engine.pushSnapshot()
    fun removeLastDeliveryIfAny() = engine.removeLastDeliveryIfAny()
    fun undoLastDelivery() { engine.undoLastDelivery() }

    fun updateStrikerAndTotals(updateFunction: (Player) -> Player) = engine.updateStrikerAndTotals(updateFunction)
    fun updateBowlerStats(updateFunction: (Player) -> Player) = engine.updateBowlerStats(updateFunction)

    fun initializePartnership() = engine.initializePartnership()
    fun updatePartnershipOnRuns(runs: Int, isLegalDelivery: Boolean = true, creditStriker: Boolean = true) =
        engine.updatePartnershipOnRuns(runs, isLegalDelivery, creditStriker)
    fun endPartnershipAndRecordWicket(outPlayer: Player, isRunOut: Boolean = false) =
        engine.endPartnershipAndRecordWicket(outPlayer, isRunOut)
    fun saveInningsPartnershipsAndWickets() = engine.saveInningsPartnershipsAndWickets()
    fun closeCurrentPartnershipIfAny() = engine.closeCurrentPartnershipIfAny()
    fun swapStrike() = engine.swapStrike()
    fun initPartnershipIfNeeded() = engine.initPartnershipIfNeeded()
    fun handleOverCompletionIfNeeded() = engine.handleOverCompletionIfNeeded()

    fun onRunScored(runs: Int) = engine.onRunScored(runs)
    fun onQuickWide() = engine.onQuickWide()
    fun onExtraSelected(extraType: ExtraType, totalRuns: Int) = engine.onExtraSelected(extraType, totalRuns)
    fun onQuickWideDialogConfirmed(totalRuns: Int) = engine.onQuickWideDialogConfirmed(totalRuns)
    fun onStumpingOnWide(runs: Int) = engine.onStumpingOnWide(runs)
    fun onQuickNoBallConfirmed(totalRuns: Int) = engine.onQuickNoBallConfirmed(totalRuns)
    fun onWicketTypeSelected(wicketType: WicketType) = engine.onWicketTypeSelected(wicketType)
    fun onFielderSelectedForCaughtStumped(fielder: Player?) = engine.onFielderSelectedForCaughtStumped(fielder)
    fun onFielderSelectedForRunOut(fielder: Player?) = engine.onFielderSelectedForRunOut(fielder)
    fun onWideWithStumping(extraType: ExtraType, baseRuns: Int) = engine.onWideWithStumping(extraType, baseRuns)

    fun onInningsComplete() = engine.onInningsComplete()
    fun onStartSecondInnings() = engine.onStartSecondInnings()
    fun startSuperOver() = engine.startSuperOver()
    fun declineSuperOver() = engine.declineSuperOver()
    fun checkPowerplayDoubling() = engine.checkPowerplayDoubling()

    fun recordCurrentBowlerIfAny() = selection.recordCurrentBowlerIfAny()
    fun jokerBallsBowledThisInningsRaw(): Int = selection.jokerBallsBowledThisInningsRaw()
    fun jokerOversBowledThisInnings(): Double = selection.jokerOversBowledThisInnings()
    fun incJokerBallIfBowledThisDelivery() = selection.incJokerBallIfBowledThisDelivery()
    fun ensureJokerStatsAppliedOnAdd() = selection.ensureJokerStatsAppliedOnAdd()

    fun onBatsmanSelected(player: Player) = selection.onBatsmanSelected(player)
    fun onBowlerSelected(player: Player, overrideToCompleteOverAllowed: Boolean) =
        selection.onBowlerSelected(player, overrideToCompleteOverAllowed)
    fun onRetireBatsman(position: Int) = selection.onRetireBatsman(position)
}
