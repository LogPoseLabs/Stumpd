package com.oreki.stumpd.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.oreki.stumpd.*
import com.oreki.stumpd.domain.model.*
import com.oreki.stumpd.ui.scoring.NoBallOutcomeHolders
import com.oreki.stumpd.data.local.db.StumpdDb
import com.oreki.stumpd.data.manager.InProgressMatchManager
import com.oreki.stumpd.data.models.createMatchInProgress
import com.oreki.stumpd.data.models.toPlayerList
import com.oreki.stumpd.data.repository.MatchRepository
import com.oreki.stumpd.data.repository.PlayerRepository
import com.oreki.stumpd.data.sync.firebase.EnhancedFirebaseAuthHelper
import com.oreki.stumpd.data.sync.firebase.FirestoreInProgressMatchDao
import com.oreki.stumpd.data.sync.sharing.MatchSharingManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────
// Toast events sent to the UI layer
// ─────────────────────────────────────────────────────────────────────
sealed class ToastEvent {
    data class Short(val message: String) : ToastEvent()
    data class Long(val message: String) : ToastEvent()
}

// ─────────────────────────────────────────────────────────────────────
// Initialization parameters (everything from the Intent extras)
// ─────────────────────────────────────────────────────────────────────
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
)

/**
 * ViewModel that owns **all** scoring state and business logic.
 *
 * State is exposed as individual [mutableStateOf] properties so that Compose
 * recomposes only the composables that read the specific property that changed
 * (far more efficient than a single 86-field `StateFlow<ScoringUiState>`).
 *
 * UI events (toasts) are delivered through [toastEvent].
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
    val playerRepo = PlayerRepository(db)
    val inProgressManager = InProgressMatchManager(context)

    // ── UI events ───────────────────────────────────────────────────
    private val _toastEvent = MutableSharedFlow<ToastEvent>(extraBufferCapacity = 10)
    val toastEvent = _toastEvent.asSharedFlow()

    private fun toast(msg: String) { _toastEvent.tryEmit(ToastEvent.Short(msg)) }
    private fun toastLong(msg: String) { _toastEvent.tryEmit(ToastEvent.Long(msg)) }

    // ── Params exposed for composables that need them ───────────────
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

    // ── Match settings (parsed once) ────────────────────────────────
    val matchSettings: MatchSettings = try {
        if (params.matchSettingsJson.isNotEmpty()) {
            gson.fromJson(params.matchSettingsJson, MatchSettings::class.java)
        } else {
            MatchSettingsManager(context).getDefaultMatchSettings()
        }
    } catch (_: Exception) { MatchSettings() }

    // ── ID → Name map (loaded async) ────────────────────────────────
    var idToName by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var idToNameLoaded by mutableStateOf(false)
        private set

    // ── Team players ────────────────────────────────────────────────
    var team1Players by mutableStateOf<MutableList<Player>>(mutableListOf())
    var team2Players by mutableStateOf<MutableList<Player>>(mutableListOf())
    var battingTeamPlayers by mutableStateOf<MutableList<Player>>(mutableListOf())
    var bowlingTeamPlayers by mutableStateOf<MutableList<Player>>(mutableListOf())
    var battingTeamName by mutableStateOf("")
    var bowlingTeamName by mutableStateOf("")

    val jokerPlayer: Player? = if (params.jokerName.isNotEmpty()) {
        Player(name = params.jokerName, isJoker = true)
    } else null

    // Names captured at start for match-complete dialog
    var initialBattingTeamName by mutableStateOf(params.team1Name)
        private set
    var initialBowlingTeamName by mutableStateOf(params.team2Name)
        private set

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

    // ── Player positions ────────────────────────────────────────────
    var strikerIndex by mutableStateOf<Int?>(null)
    var nonStrikerIndex by mutableStateOf<Int?>(null)
    var bowlerIndex by mutableStateOf<Int?>(null)
    var previousBowlerName by mutableStateOf<String?>(null)
    var currentBowlerSpell by mutableStateOf(0)
    var runsConcededInCurrentOver by mutableStateOf(0)

    // Derived
    val striker: Player? get() = strikerIndex?.let { battingTeamPlayers.getOrNull(it) }
    val nonStriker: Player? get() = nonStrikerIndex?.let { battingTeamPlayers.getOrNull(it) }
    val bowler: Player? get() = bowlerIndex?.let { bowlingTeamPlayers.getOrNull(it) }

    // ── Calculated total runs (derived) ─────────────────────────────
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
    val isPowerplayActive: Boolean get() = matchSettings.powerplayOvers > 0 && currentOver < matchSettings.powerplayOvers

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

    // ── Unlimited undo ──────────────────────────────────────────────
    var unlimitedUndoEnabled by mutableStateOf(
        com.oreki.stumpd.utils.FeatureFlags.isUnlimitedUndoEnabled(context)
    )
    var showUnlimitedUndoDialog by mutableStateOf(false)
    var pendingUnlimitedUndoValue by mutableStateOf(false)

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

    val isInningsComplete: Boolean
        get() = currentOver >= matchSettings.totalOvers
                || (currentInnings == 2 && calculatedTotalRuns > firstInningsRuns)
                || if (matchSettings.allowSingleSideBatting) (availableBatsmen == 0) else (availableBatsmen < 2)

    // ═════════════════════════════════════════════════════════════════
    //  Initialization
    // ═════════════════════════════════════════════════════════════════

    init {
        // Load player ID→name map
        viewModelScope.launch {
            val players = playerRepo.getAllPlayers()
            val map = players.associate { it.id to it.name }
            idToName = map
            idToNameLoaded = true
            rebuildTeams(map)
        }
    }

    /** Build team lists once the ID→Name map is ready. */
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

    /** Determine batting/bowling order from toss and assign initial team state. */
    private fun applyTossAndSetTeams() {
        val tw = params.tossWinner
        val tc = params.tossChoice
        if (tw != null && tc != null) {
            val team1Won = tw.equals(params.team1Name, ignoreCase = true)
            val battingChosen = tc.contains("Batting", ignoreCase = true)
            val team1BatsFirst = (team1Won && battingChosen) || (!team1Won && !battingChosen)
            if (team1BatsFirst) {
                battingTeamPlayers = team1Players
                bowlingTeamPlayers = team2Players
                battingTeamName = params.team1Name
                bowlingTeamName = params.team2Name
            } else {
                battingTeamPlayers = team2Players
                bowlingTeamPlayers = team1Players
                battingTeamName = params.team2Name
                bowlingTeamName = params.team1Name
            }
        } else {
            battingTeamPlayers = team1Players
            bowlingTeamPlayers = team2Players
            battingTeamName = params.team1Name
            bowlingTeamName = params.team2Name
        }
        initialBattingTeamName = battingTeamName
        initialBowlingTeamName = bowlingTeamName
    }

    /** Whether team1 bats first according to toss. */
    private fun isTeam1BatsFirst(): Boolean {
        val tw = params.tossWinner
        val tc = params.tossChoice
        return if (tw != null && tc != null) {
            val team1Won = tw.equals(params.team1Name, ignoreCase = true)
            val battingChosen = tc.contains("Batting", ignoreCase = true)
            (team1Won && battingChosen) || (!team1Won && !battingChosen)
        } else true
    }

    // ═════════════════════════════════════════════════════════════════
    //  Firebase auto-share (fire once)
    // ═════════════════════════════════════════════════════════════════

    fun startFirebaseAutoShare() {
        viewModelScope.launch {
            try {
                kotlinx.coroutines.delay(1000)
                val authHelper = EnhancedFirebaseAuthHelper(context)
                var userId = authHelper.currentUserId
                if (userId == null) {
                    Log.d("ScoringVM", "No userId, signing in anonymously...")
                    val user = authHelper.signInAnonymously()
                    userId = user?.uid ?: run {
                        Log.e("ScoringVM", "Auto-share: Authentication failed"); return@launch
                    }
                    Log.d("ScoringVM", "Auto-share: Signed in with userId: $userId")
                    kotlinx.coroutines.delay(2000)
                } else {
                    Log.d("ScoringVM", "Auto-share: Already authenticated with userId: $userId")
                    kotlinx.coroutines.delay(1000)
                }
                Log.d("ScoringVM", "Auto-share: Attempting to share matchId: $matchId")
                val result = MatchSharingManager().shareMatch(
                    ownerId = userId, matchId = matchId, ownerName = null, expiryHours = 48
                )
                result.onSuccess { code -> Log.d("ScoringVM", "Auto-share: SUCCESS! Code: $code") }
                    .onFailure { e -> Log.e("ScoringVM", "Auto-share: FAILED - ${e.message}", e) }
            } catch (e: Exception) {
                Log.e("ScoringVM", "Auto-share: Exception", e)
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Resume from saved in-progress match
    // ═════════════════════════════════════════════════════════════════

    fun resumeMatch() {
        if (!isResuming || resumedMatchLoaded) return
        viewModelScope.launch {
            val savedMatch = inProgressManager.loadMatch()
            if (savedMatch != null && savedMatch.matchId == matchId) {
                try {
                    currentInnings = savedMatch.currentInnings
                    currentOver = savedMatch.currentOver
                    ballsInOver = savedMatch.ballsInOver
                    totalWickets = savedMatch.totalWickets
                    team1Players = savedMatch.team1PlayersJson.toPlayerList(gson).toMutableList()
                    team2Players = savedMatch.team2PlayersJson.toPlayerList(gson).toMutableList()

                    val t1First = isTeam1BatsFirst()
                    if (savedMatch.currentInnings == 1) {
                        battingTeamPlayers = if (t1First) team1Players else team2Players
                        bowlingTeamPlayers = if (t1First) team2Players else team1Players
                        battingTeamName = if (t1First) team1Name else team2Name
                        bowlingTeamName = if (t1First) team2Name else team1Name
                    } else {
                        battingTeamPlayers = if (t1First) team2Players else team1Players
                        bowlingTeamPlayers = if (t1First) team1Players else team2Players
                        battingTeamName = if (t1First) team2Name else team1Name
                        bowlingTeamName = if (t1First) team1Name else team2Name
                    }

                    strikerIndex = savedMatch.strikerIndex
                    nonStrikerIndex = savedMatch.nonStrikerIndex
                    bowlerIndex = savedMatch.bowlerIndex
                    firstInningsRuns = savedMatch.firstInningsRuns
                    firstInningsWickets = savedMatch.firstInningsWickets
                    firstInningsOvers = savedMatch.firstInningsOvers
                    firstInningsBalls = savedMatch.firstInningsBalls
                    totalExtras = savedMatch.totalExtras
                    jokerOutInCurrentInnings = savedMatch.jokerOutInCurrentInnings
                    jokerBallsBowledInnings1 = savedMatch.jokerBallsBowledInnings1
                    jokerBallsBowledInnings2 = savedMatch.jokerBallsBowledInnings2

                    powerplayRunsInnings1 = savedMatch.powerplayRunsInnings1
                    powerplayRunsInnings2 = savedMatch.powerplayRunsInnings2
                    powerplayDoublingDoneInnings1 = savedMatch.powerplayDoublingDoneInnings1
                    powerplayDoublingDoneInnings2 = savedMatch.powerplayDoublingDoneInnings2

                    savedMatch.completedBattersInnings1Json?.let {
                        completedBattersInnings1 = it.toPlayerList(gson).toMutableList()
                    }
                    savedMatch.completedBattersInnings2Json?.let {
                        completedBattersInnings2 = it.toPlayerList(gson).toMutableList()
                    }
                    savedMatch.completedBowlersInnings1Json?.let {
                        completedBowlersInnings1 = it.toPlayerList(gson).toMutableList()
                    }
                    savedMatch.completedBowlersInnings2Json?.let {
                        completedBowlersInnings2 = it.toPlayerList(gson).toMutableList()
                    }
                    savedMatch.firstInningsBattingPlayersJson?.let {
                        firstInningsBattingPlayersList = it.toPlayerList(gson)
                    }
                    savedMatch.firstInningsBowlingPlayersJson?.let {
                        firstInningsBowlingPlayersList = it.toPlayerList(gson)
                    }
                    savedMatch.allDeliveriesJson?.let { json ->
                        try {
                            val deliveries = gson.fromJson(json, Array<DeliveryUI>::class.java).toList()
                            allDeliveries.clear()
                            allDeliveries.addAll(deliveries)
                        } catch (e: Exception) {
                            Log.e("ScoringVM", "Failed to restore deliveries", e)
                        }
                    }

                    resumedMatchLoaded = true
                    toastLong("Match resumed from Over ${currentOver}.${ballsInOver}")
                } catch (e: Exception) {
                    Log.e("ScoringVM", "Failed to resume match", e)
                    toastLong("Failed to resume match. Starting fresh.")
                }
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Auto-save
    // ═════════════════════════════════════════════════════════════════

    fun autoSaveMatch() {
        try {
            val t1First = isTeam1BatsFirst()
            val battingIsTeam1 = if (currentInnings == 1) t1First else !t1First
            val currentTeam1Players = if (battingIsTeam1) battingTeamPlayers else bowlingTeamPlayers
            val currentTeam2Players = if (battingIsTeam1) bowlingTeamPlayers else battingTeamPlayers

            val matchInProgress = createMatchInProgress(
                matchId = matchId,
                team1Name = team1Name,
                team2Name = team2Name,
                jokerName = jokerName,
                team1PlayerIds = team1PlayerIds,
                team2PlayerIds = team2PlayerIds,
                team1PlayerNames = team1PlayerNames,
                team2PlayerNames = team2PlayerNames,
                matchSettingsJson = matchSettingsJson,
                groupId = groupId,
                groupName = groupName,
                tossWinner = tossWinner,
                tossChoice = tossChoice,
                currentInnings = currentInnings,
                currentOver = currentOver,
                ballsInOver = ballsInOver,
                team1Players = currentTeam1Players,
                team2Players = currentTeam2Players,
                strikerIndex = strikerIndex,
                nonStrikerIndex = nonStrikerIndex,
                bowlerIndex = bowlerIndex,
                firstInningsRuns = firstInningsRuns,
                firstInningsWickets = firstInningsWickets,
                firstInningsOvers = firstInningsOvers,
                firstInningsBalls = firstInningsBalls,
                bowlingTeamPlayers = bowlingTeamPlayers,
                totalExtras = totalExtras,
                wides = 0,
                noBalls = 0,
                byes = 0,
                legByes = 0,
                completedBattersInnings1 = completedBattersInnings1,
                completedBattersInnings2 = completedBattersInnings2,
                completedBowlersInnings1 = completedBowlersInnings1,
                completedBowlersInnings2 = completedBowlersInnings2,
                firstInningsBattingPlayers = firstInningsBattingPlayersList,
                firstInningsBowlingPlayers = firstInningsBowlingPlayersList,
                jokerOutInCurrentInnings = jokerOutInCurrentInnings,
                jokerBallsBowledInnings1 = jokerBallsBowledInnings1,
                jokerBallsBowledInnings2 = jokerBallsBowledInnings2,
                powerplayRunsInnings1 = powerplayRunsInnings1,
                powerplayRunsInnings2 = powerplayRunsInnings2,
                powerplayDoublingDoneInnings1 = powerplayDoublingDoneInnings1,
                powerplayDoublingDoneInnings2 = powerplayDoublingDoneInnings2,
                allDeliveries = allDeliveries.toList(),
                gson = gson
            )
            viewModelScope.launch {
                Log.d("ScoringVM", "=== Starting auto-save ===")
                inProgressManager.saveMatch(matchInProgress)
                Log.d("ScoringVM", "Saved to Room DB")
                try {
                    val authHelper = EnhancedFirebaseAuthHelper(context)
                    val userId = authHelper.currentUserId
                    Log.d("ScoringVM", "Firestore sync - userId: $userId")
                    if (userId != null) {
                        val entity = StumpdDb.get(context).inProgressMatchDao().getLatest()
                        Log.d("ScoringVM", "Entity retrieved: matchId=${entity?.matchId}")
                        if (entity != null) {
                            FirestoreInProgressMatchDao().uploadInProgressMatch(userId, entity)
                            Log.d("ScoringVM", "✅ Live match synced to Firestore")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ScoringVM", "❌ Failed to sync match to Firestore", e)
                }
            }
        } catch (e: Exception) {
            Log.e("ScoringVM", "Failed to auto-save match", e)
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Delivery helpers
    // ═════════════════════════════════════════════════════════════════

    fun addDelivery(outcome: String, highlight: Boolean = false, runs: Int = 0) {
        val ballNumber = (ballsInOver % 6) + 1
        val entry = DeliveryUI(
            inning = currentInnings,
            over = currentOver + 1,
            ballInOver = ballNumber,
            outcome = outcome,
            highlight = highlight,
            strikerName = striker?.name ?: "",
            nonStrikerName = nonStriker?.name ?: "",
            bowlerName = bowler?.name ?: "",
            runs = runs
        )
        allDeliveries.add(entry)
    }

    fun pushSnapshot() {
        deliveryHistory.add(
            DeliverySnapshot(
                strikerIndex = strikerIndex,
                nonStrikerIndex = nonStrikerIndex,
                bowlerIndex = bowlerIndex,
                battingTeamPlayers = battingTeamPlayers.map { it.copy() },
                bowlingTeamPlayers = bowlingTeamPlayers.map { it.copy() },
                totalWickets = totalWickets,
                currentOver = currentOver,
                ballsInOver = ballsInOver,
                totalExtras = totalExtras,
                calculatedTotalRuns = calculatedTotalRuns,
                previousBowlerName = previousBowlerName,
                midOverReplacementDueToJoker = midOverReplacementDueToJoker.value,
                jokerBallsBowledInnings1 = jokerBallsBowledInnings1,
                jokerBallsBowledInnings2 = jokerBallsBowledInnings2,
                completedBattersInnings1 = completedBattersInnings1.map { it.copy() },
                completedBattersInnings2 = completedBattersInnings2.map { it.copy() },
                completedBowlersInnings1 = completedBowlersInnings1.map { it.copy() },
                completedBowlersInnings2 = completedBowlersInnings2.map { it.copy() },
            )
        )
        if (!com.oreki.stumpd.utils.FeatureFlags.isUnlimitedUndoEnabled(context)) {
            if (deliveryHistory.size > 2) deliveryHistory.removeAt(0)
        }
    }

    fun removeLastDeliveryIfAny() {
        if (allDeliveries.isNotEmpty()) {
            val last = allDeliveries.last()
            if (last.over == currentOver + 1) {
                allDeliveries.removeAt(allDeliveries.lastIndex)
            }
        }
    }

    fun undoLastDelivery() {
        if (deliveryHistory.isEmpty()) {
            toast("Nothing to undo")
            return
        }
        val peek = deliveryHistory.last()
        val atStartOfSecond = (currentInnings == 2 && currentOver == 0 && ballsInOver == 0)
        val wouldGoBeforeStartOfSecond = (currentInnings == 2 && peek.currentOver == 0 && peek.ballsInOver == 0)
        if (atStartOfSecond || wouldGoBeforeStartOfSecond) {
            toast("Cannot undo beyond 0.0 overs in 2nd innings")
            return
        }
        val snap = deliveryHistory.removeAt(deliveryHistory.lastIndex)
        strikerIndex = snap.strikerIndex
        nonStrikerIndex = snap.nonStrikerIndex
        bowlerIndex = snap.bowlerIndex
        battingTeamPlayers = snap.battingTeamPlayers.toMutableList()
        bowlingTeamPlayers = snap.bowlingTeamPlayers.toMutableList()
        totalWickets = snap.totalWickets
        currentOver = snap.currentOver
        ballsInOver = snap.ballsInOver
        totalExtras = snap.totalExtras
        previousBowlerName = snap.previousBowlerName
        midOverReplacementDueToJoker.value = snap.midOverReplacementDueToJoker
        jokerBallsBowledInnings1 = snap.jokerBallsBowledInnings1
        jokerBallsBowledInnings2 = snap.jokerBallsBowledInnings2
        completedBattersInnings1 = snap.completedBattersInnings1.toMutableList()
        completedBattersInnings2 = snap.completedBattersInnings2.toMutableList()
        completedBowlersInnings1 = snap.completedBowlersInnings1.toMutableList()
        completedBowlersInnings2 = snap.completedBowlersInnings2.toMutableList()
        showBowlerDialog = false
        showBatsmanDialog = false
        showExtrasDialog = false
        showWicketDialog = false
        removeLastDeliveryIfAny()
        toast("Last delivery undone")
    }

    // ═════════════════════════════════════════════════════════════════
    //  Bowler recording & joker helpers
    // ═════════════════════════════════════════════════════════════════

    fun recordCurrentBowlerIfAny() {
        val idx = bowlerIndex
        if (idx != null) {
            if (runsConcededInCurrentOver == 0 && ballsInOver == 6) {
                bowlingTeamPlayers = bowlingTeamPlayers.mapIndexed { i, player ->
                    if (i == idx) player.copy(maidenOvers = player.maidenOvers + 1) else player
                }.toMutableList()
            }
            val p = bowlingTeamPlayers.getOrNull(idx)
            if (p != null && (p.ballsBowled > 0 || p.wickets > 0 || p.runsConceded > 0)) {
                if (currentInnings == 1) {
                    val exists = completedBowlersInnings1.indexOfFirst { it.name.equals(p.name, true) }
                    completedBowlersInnings1 =
                        if (exists == -1) (completedBowlersInnings1 + p.copy()).toMutableList()
                        else completedBowlersInnings1.mapIndexed { i, old -> if (i == exists) p.copy() else old }.toMutableList()
                } else {
                    val exists = completedBowlersInnings2.indexOfFirst { it.name.equals(p.name, true) }
                    completedBowlersInnings2 =
                        if (exists == -1) (completedBowlersInnings2 + p.copy()).toMutableList()
                        else completedBowlersInnings2.mapIndexed { i, old -> if (i == exists) p.copy() else old }.toMutableList()
                }
            }
        }
        runsConcededInCurrentOver = 0
    }

    fun jokerBallsBowledThisInningsRaw(): Int =
        if (currentInnings == 1) jokerBallsBowledInnings1 else jokerBallsBowledInnings2

    fun jokerOversBowledThisInnings(): Double {
        val b = jokerBallsBowledThisInningsRaw()
        return (b / 6) + (b % 6) * 0.1
    }

    fun incJokerBallIfBowledThisDelivery() {
        val currentBowler = bowler
        if (currentBowler?.isJoker == true) {
            if (currentInnings == 1) jokerBallsBowledInnings1++ else jokerBallsBowledInnings2++
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Striker / Bowler stat updates
    // ═════════════════════════════════════════════════════════════════

    fun updateStrikerAndTotals(updateFunction: (Player) -> Player) {
        strikerIndex?.let { index ->
            val newPlayersList = battingTeamPlayers.toMutableList()
            newPlayersList[index] = updateFunction(newPlayersList[index])
            battingTeamPlayers = newPlayersList
        }
    }

    fun updateBowlerStats(updateFunction: (Player) -> Player) {
        bowlerIndex?.let { index ->
            val newPlayersList = bowlingTeamPlayers.toMutableList()
            newPlayersList[index] = updateFunction(newPlayersList[index])
            bowlingTeamPlayers = newPlayersList
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Partnership tracking
    // ═════════════════════════════════════════════════════════════════

    fun initializePartnership() {
        if (striker != null && nonStriker != null) {
            currentPartnershipRuns = 0
            currentPartnershipBalls = 0
            currentPartnershipBatsman1Runs = 0
            currentPartnershipBatsman2Runs = 0
            currentPartnershipBatsman1Balls = 0
            currentPartnershipBatsman2Balls = 0
            currentPartnershipBatsman1Name = striker!!.name
            currentPartnershipBatsman2Name = nonStriker!!.name
        }
    }

    fun updatePartnershipOnRuns(runs: Int, isLegalDelivery: Boolean = true) {
        if (striker != null && nonStriker != null) {
            if (isLegalDelivery) {
                currentPartnershipBalls++
                if (striker!!.name == currentPartnershipBatsman1Name) currentPartnershipBatsman1Balls++
                else if (striker!!.name == currentPartnershipBatsman2Name) currentPartnershipBatsman2Balls++
            }
            currentPartnershipRuns += runs
            if (striker!!.name == currentPartnershipBatsman1Name) currentPartnershipBatsman1Runs += runs
            else if (striker!!.name == currentPartnershipBatsman2Name) currentPartnershipBatsman2Runs += runs
        }
    }

    fun endPartnershipAndRecordWicket(outPlayer: Player, isRunOut: Boolean = false) {
        if (currentPartnershipBatsman1Name != null && currentPartnershipBatsman2Name != null && currentPartnershipRuns > 0) {
            val partnership = Partnership(
                batsman1Name = currentPartnershipBatsman1Name!!,
                batsman2Name = currentPartnershipBatsman2Name!!,
                runs = currentPartnershipRuns,
                balls = currentPartnershipBalls,
                batsman1Runs = currentPartnershipBatsman1Runs,
                batsman2Runs = currentPartnershipBatsman2Runs,
                isActive = false
            )
            partnerships = partnerships + partnership
        }
        val teamScore = calculatedTotalRuns
        val totalBalls = currentOver * 6 + ballsInOver + 1
        val oversCompleted = (totalBalls / 6).toDouble() + ((totalBalls % 6) / 10.0)
        val wicketNum = totalWickets

        val fow = FallOfWicket(
            batsmanName = outPlayer.name,
            runs = teamScore,
            overs = oversCompleted,
            wicketNumber = wicketNum,
            dismissalType = outPlayer.dismissalType?.name,
            bowlerName = outPlayer.bowlerName,
            fielderName = outPlayer.fielderName
        )
        fallOfWickets = fallOfWickets + fow

        currentPartnershipRuns = 0
        currentPartnershipBalls = 0
        currentPartnershipBatsman1Runs = 0
        currentPartnershipBatsman2Runs = 0
        currentPartnershipBatsman1Balls = 0
        currentPartnershipBatsman2Balls = 0
        currentPartnershipBatsman1Name = null
        currentPartnershipBatsman2Name = null
    }

    fun saveInningsPartnershipsAndWickets() {
        if (currentInnings == 1) {
            firstInningsPartnerships = partnerships
            firstInningsFallOfWickets = fallOfWickets
        }
        partnerships = emptyList()
        fallOfWickets = emptyList()
        currentPartnershipRuns = 0
        currentPartnershipBalls = 0
        currentPartnershipBatsman1Runs = 0
        currentPartnershipBatsman2Runs = 0
    }

    fun swapStrike() {
        val si = strikerIndex
        val nsi = nonStrikerIndex
        if (si == null || nsi == null) return
        strikerIndex = nsi
        nonStrikerIndex = si
    }

    fun ensureJokerStatsAppliedOnAdd() {
        if (jokerPlayer == null) return
        val exists = bowlingTeamPlayers.indexOfFirst { it.isJoker }
        if (exists == -1) return
        val balls = if (currentInnings == 1) jokerBallsBowledInnings1 else jokerBallsBowledInnings2
        if (balls <= 0) return
        val list = bowlingTeamPlayers.toMutableList()
        val j = list[exists]
        list[exists] = j.copy(ballsBowled = balls)
        bowlingTeamPlayers = list
    }

    // ═════════════════════════════════════════════════════════════════
    //  Over completion helper (reused by run scoring, extras, wickets)
    // ═════════════════════════════════════════════════════════════════

    /** Call AFTER incrementing ballsInOver. Handles over completion. */
    fun handleOverCompletionIfNeeded() {
        if (ballsInOver == 6) {
            currentOver += 1
            ballsInOver = 0
            recordCurrentBowlerIfAny()
            previousBowlerName = bowler?.name
            bowlerIndex = null
            currentBowlerSpell = 0
            midOverReplacementDueToJoker.value = false
            if (showBatsmanDialog) {
                pendingSwapAfterBatsmanPick = !showSingleSideLayout
                pendingBowlerDialogAfterBatsmanPick = true
            } else {
                if (!showSingleSideLayout) swapStrike()
                showBowlerDialog = true
            }
            toastLong("Over complete! Select new bowler")
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Scoring: Runs
    // ═════════════════════════════════════════════════════════════════

    fun onRunScored(runs: Int) {
        pushSnapshot()
        updateStrikerAndTotals { player ->
            player.copy(
                runs = player.runs + runs,
                ballsFaced = player.ballsFaced + 1,
                dots = if (runs == 0) player.dots + 1 else player.dots,
                singles = if (runs == 1) player.singles + 1 else player.singles,
                twos = if (runs == 2) player.twos + 1 else player.twos,
                threes = if (runs == 3) player.threes + 1 else player.threes,
                fours = if (runs == 4) player.fours + 1 else player.fours,
                sixes = if (runs == 6) player.sixes + 1 else player.sixes,
            )
        }
        updateBowlerStats { player ->
            player.copy(
                runsConceded = player.runsConceded + runs,
                ballsBowled = player.ballsBowled + 1,
            )
        }
        runsConcededInCurrentOver += runs
        updatePartnershipOnRuns(runs, isLegalDelivery = true)
        incJokerBallIfBowledThisDelivery()
        addDelivery(outcome = runs.toString(), highlight = (runs == 4 || runs == 6), runs = runs)
        if (runs % 2 == 1 && !showSingleSideLayout) swapStrike()
        ballsInOver += 1
        handleOverCompletionIfNeeded()
    }

    // ═════════════════════════════════════════════════════════════════
    //  Scoring: Quick Wide (just base penalty)
    // ═════════════════════════════════════════════════════════════════

    fun onQuickWide() {
        pushSnapshot()
        val totalRuns = matchSettings.wideRuns
        updateBowlerStats { player -> player.copy(runsConceded = player.runsConceded + totalRuns) }
        runsConcededInCurrentOver += totalRuns
        totalExtras += totalRuns
        addDelivery("Wd", runs = totalRuns)
        val totalAfter = battingTeamPlayers.sumOf { it.runs } + totalExtras
        toast("Wide! +$totalRuns runs. Total: $totalAfter")
    }

    // ═════════════════════════════════════════════════════════════════
    //  Scoring: Extras (dialog callback)
    // ═════════════════════════════════════════════════════════════════

    fun onExtraSelected(extraType: ExtraType, totalRuns: Int) {
        pushSnapshot()
        when (extraType) {
            ExtraType.OFF_SIDE_WIDE, ExtraType.LEG_SIDE_WIDE -> {
                updateBowlerStats { player -> player.copy(runsConceded = player.runsConceded + totalRuns) }
                runsConcededInCurrentOver += totalRuns
                totalExtras += totalRuns
                val baseWideRuns = matchSettings.wideRuns
                val additionalRuns = totalRuns - baseWideRuns
                if (additionalRuns % 2 == 1 && !showSingleSideLayout) swapStrike()
                addDelivery("Wd+${totalRuns}", runs = totalRuns)
                toast("${extraType.displayName}! +$totalRuns runs")
            }
            ExtraType.NO_BALL -> {
                val baseNoBallRuns = matchSettings.noballRuns
                val additionalRuns = totalRuns - baseNoBallRuns
                updateBowlerStats { player -> player.copy(runsConceded = player.runsConceded + totalRuns) }
                runsConcededInCurrentOver += totalRuns
                totalExtras += baseNoBallRuns

                if (additionalRuns > 0) {
                    updateStrikerAndTotals { player ->
                        player.copy(
                            runs = player.runs + additionalRuns,
                            ballsFaced = player.ballsFaced + 1,
                            dots = if (additionalRuns == 0) player.dots + 1 else player.dots,
                            singles = if (additionalRuns == 1) player.singles + 1 else player.singles,
                            twos = if (additionalRuns == 2) player.twos + 1 else player.twos,
                            threes = if (additionalRuns == 3) player.threes + 1 else player.threes,
                            fours = if (additionalRuns == 4) player.fours + 1 else player.fours,
                            sixes = if (additionalRuns == 6) player.sixes + 1 else player.sixes
                        )
                    }
                    updatePartnershipOnRuns(additionalRuns, isLegalDelivery = true)
                } else {
                    updateStrikerAndTotals { player -> player.copy(ballsFaced = player.ballsFaced + 1) }
                }

                val sub = NoBallOutcomeHolders.noBallSubOutcome.value
                val ro = NoBallOutcomeHolders.noBallRunOutInput.value
                val bo = NoBallOutcomeHolders.noBallBoundaryOutInput.value

                when (sub) {
                    NoBallSubOutcome.BOUNDARY_OUT -> handleNoBallBoundaryOut(totalRuns, bo)
                    NoBallSubOutcome.RUN_OUT -> handleNoBallRunOut(totalRuns, ro)
                    else -> {
                        if (additionalRuns % 2 == 1 && !showSingleSideLayout) swapStrike()
                        addDelivery("Nb+${totalRuns}", runs = totalRuns)
                        toast("No ball! +$totalRuns runs")
                    }
                }

                NoBallOutcomeHolders.noBallSubOutcome.value = NoBallSubOutcome.NONE
                NoBallOutcomeHolders.noBallRunOutInput.value = null
                NoBallOutcomeHolders.noBallBoundaryOutInput.value = null
            }
            ExtraType.BYE, ExtraType.LEG_BYE -> {
                updateStrikerAndTotals { player -> player.copy(ballsFaced = player.ballsFaced + 1) }
                updateBowlerStats { player -> player.copy(ballsBowled = player.ballsBowled + 1) }
                incJokerBallIfBowledThisDelivery()
                totalExtras += totalRuns
                addDelivery(if (extraType == ExtraType.BYE) "B+$totalRuns" else "Lb+$totalRuns", runs = totalRuns)
                ballsInOver += 1
                if (ballsInOver == 6) {
                    currentOver += 1
                    ballsInOver = 0
                    recordCurrentBowlerIfAny()
                    previousBowlerName = bowler?.name
                    bowlerIndex = null
                    currentBowlerSpell = 0
                    midOverReplacementDueToJoker.value = false
                    if (!showSingleSideLayout) swapStrike()
                    showBowlerDialog = true
                    toastLong("Over complete! Select new bowler")
                } else {
                    val baseByeRuns = if (extraType == ExtraType.BYE) matchSettings.byeRuns else matchSettings.legByeRuns
                    val additionalRuns2 = totalRuns - baseByeRuns
                    if (additionalRuns2 % 2 == 1 && !showSingleSideLayout) swapStrike()
                }
                toast("${extraType.displayName}! +$totalRuns runs")
            }
        }
        val totalAfter = battingTeamPlayers.sumOf { it.runs } + totalExtras
        toast("${extraType.displayName}: +$totalRuns. Total: $totalAfter")
        showExtrasDialog = false
    }

    private fun handleNoBallBoundaryOut(totalRuns: Int, bo: NoBallBoundaryOutInput?) {
        val outName = bo?.outBatterName?.trim()?.takeIf { it.isNotEmpty() } ?: striker?.name ?: "Batsman"
        val outIndex = battingTeamPlayers.indexOfFirst { it.name.equals(outName, ignoreCase = true) }
        val validIndex = if (outIndex != -1) outIndex else strikerIndex

        if (validIndex == null) {
            toastLong("Boundary out: could not resolve batter. Aborting wicket.")
            addDelivery("Nb+${totalRuns}", runs = totalRuns)
            return
        }

        pushSnapshot()
        totalWickets += 1
        val newList = battingTeamPlayers.toMutableList()
        val dismissedPlayer = newList[validIndex]
        newList[validIndex] = dismissedPlayer.copy(
            isOut = true, ballsFaced = dismissedPlayer.ballsFaced + 1,
            dismissalType = WicketType.BOUNDARY_OUT, bowlerName = bowler?.name
        )
        val outSnapshot = newList[validIndex].copy()
        battingTeamPlayers = newList
        recordCompletedBatter(outSnapshot)

        if (strikerIndex == validIndex) {
            strikerIndex = null; selectingBatsman = 1; pickerOtherEndName = nonStriker?.name
        } else if (nonStrikerIndex == validIndex) {
            nonStrikerIndex = null; selectingBatsman = 2; pickerOtherEndName = striker?.name
        } else {
            strikerIndex = null; selectingBatsman = 1; pickerOtherEndName = nonStriker?.name
        }
        showBatsmanDialog = true
        addDelivery("Nb+${totalRuns}; BO(${outSnapshot.name})", highlight = true, runs = totalRuns)
        toast("No ball + Boundary out! Total: ${battingTeamPlayers.sumOf { it.runs } + totalExtras}")
    }

    private fun handleNoBallRunOut(totalRuns: Int, ro: RunOutInput?) {
        isNoBallRunOut = true
        if (ro == null) {
            addDelivery("Nb+${totalRuns}", runs = totalRuns)
            toast("No ball! +$totalRuns runs")
            return
        }
        pushSnapshot()
        val name = ro.whoOut.trim()
        val byName = battingTeamPlayers.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        val byStriker = if (striker?.name.equals(name, true)) strikerIndex else null
        val byNonStriker = if (nonStriker?.name.equals(name, true)) nonStrikerIndex else null
        val resolvedIndex = when {
            byName != -1 -> byName
            byStriker != null -> byStriker
            byNonStriker != null -> byNonStriker
            else -> strikerIndex
        }
        if (resolvedIndex == null) {
            toastLong("Run out on No ball: player \"$name\" not found")
            addDelivery("Nb+${totalRuns}", runs = totalRuns)
            return
        }
        val updated = battingTeamPlayers.toMutableList()
        updated[resolvedIndex] = updated[resolvedIndex].copy(isOut = true, dismissalType = WicketType.RUN_OUT)
        val outSnapshot = updated[resolvedIndex].copy()
        battingTeamPlayers = updated
        totalWickets += 1
        recordCompletedBatter(outSnapshot)

        if (ro.end == RunOutEnd.STRIKER_END) {
            if (strikerIndex == resolvedIndex) strikerIndex = null
            selectingBatsman = 1; pickerOtherEndName = nonStriker?.name
        } else {
            if (nonStrikerIndex == resolvedIndex) nonStrikerIndex = null
            selectingBatsman = 2; pickerOtherEndName = striker?.name
        }
        showBatsmanDialog = true
        addDelivery("Nb+${totalRuns}; RO(${outSnapshot.name} @ ${if (ro.end == RunOutEnd.STRIKER_END) "S" else "NS"})", highlight = true, runs = totalRuns)
        toast("No ball + Run out! Total: ${battingTeamPlayers.sumOf { it.runs } + totalExtras}")
    }

    // ═════════════════════════════════════════════════════════════════
    //  Scoring: Quick Wide Dialog confirmed
    // ═════════════════════════════════════════════════════════════════

    fun onQuickWideDialogConfirmed(totalRuns: Int) {
        pushSnapshot()
        updateBowlerStats { player -> player.copy(runsConceded = player.runsConceded + totalRuns) }
        runsConcededInCurrentOver += totalRuns
        totalExtras += totalRuns
        val baseWideRuns = matchSettings.wideRuns
        val additionalRuns = totalRuns - baseWideRuns
        if (additionalRuns % 2 == 1 && !showSingleSideLayout) swapStrike()
        addDelivery("Wd+${totalRuns}", runs = totalRuns)
        toast("Wide! +$totalRuns runs. Total: ${battingTeamPlayers.sumOf { it.runs } + totalExtras}")
        showQuickWideDialog = false
    }

    fun onStumpingOnWide(runs: Int) {
        showQuickWideDialog = false
        pendingWicketType = WicketType.STUMPED
        pendingWideExtraType = ExtraType.LEG_SIDE_WIDE
        pendingWideRuns = runs
        showFielderSelectionDialog = true
    }

    // ═════════════════════════════════════════════════════════════════
    //  Scoring: Quick No-Ball Dialog confirmed
    // ═════════════════════════════════════════════════════════════════

    fun onQuickNoBallConfirmed(totalRuns: Int) {
        pushSnapshot()
        val baseNoBallRuns = matchSettings.noballRuns
        val additionalRuns = totalRuns - baseNoBallRuns
        updateBowlerStats { player -> player.copy(runsConceded = player.runsConceded + totalRuns) }
        totalExtras += baseNoBallRuns

        if (additionalRuns > 0) {
            updateStrikerAndTotals { player ->
                player.copy(
                    runs = player.runs + additionalRuns,
                    ballsFaced = player.ballsFaced + 1,
                    dots = if (additionalRuns == 0) player.dots + 1 else player.dots,
                    singles = if (additionalRuns == 1) player.singles + 1 else player.singles,
                    twos = if (additionalRuns == 2) player.twos + 1 else player.twos,
                    threes = if (additionalRuns == 3) player.threes + 1 else player.threes,
                    fours = if (additionalRuns == 4) player.fours + 1 else player.fours,
                    sixes = if (additionalRuns == 6) player.sixes + 1 else player.sixes
                )
            }
            updatePartnershipOnRuns(additionalRuns, isLegalDelivery = true)
        } else {
            updateStrikerAndTotals { player -> player.copy(ballsFaced = player.ballsFaced + 1) }
        }

        if (additionalRuns % 2 == 1 && !showSingleSideLayout) swapStrike()
        addDelivery("Nb+${totalRuns}", runs = totalRuns)
        toast("No-ball! +$totalRuns runs. Total: ${battingTeamPlayers.sumOf { it.runs } + totalExtras}")
        showQuickNoBallDialog = false
    }

    // ═════════════════════════════════════════════════════════════════
    //  Wicket handling
    // ═════════════════════════════════════════════════════════════════

    fun onWicketTypeSelected(wicketType: WicketType) {
        if (wicketType == WicketType.RUN_OUT) {
            showWicketDialog = false
            showRunOutDialog = true
            return
        }
        if (wicketType == WicketType.CAUGHT || wicketType == WicketType.STUMPED) {
            pendingWicketType = wicketType
            showWicketDialog = false
            showFielderSelectionDialog = true
            return
        }
        // Direct wicket types (BOWLED, LBW, HIT_WICKET, BOUNDARY_OUT etc.)
        processDirectWicket(wicketType)
    }

    private fun processDirectWicket(wicketType: WicketType) {
        val dismissedIndex = strikerIndex
        val jokerWasOut = striker?.isJoker == true

        pushSnapshot()
        totalWickets += 1
        updateStrikerAndTotals { p ->
            p.copy(isOut = true, ballsFaced = p.ballsFaced + 1, dismissalType = wicketType, bowlerName = bowler?.name)
        }
        val outSnapshot = dismissedIndex?.let { battingTeamPlayers.getOrNull(it)?.copy() }
        outSnapshot?.let { endPartnershipAndRecordWicket(it, isRunOut = false) }
        if (outSnapshot != null && outSnapshot.isOut) recordCompletedBatter(outSnapshot)
        updateBowlerStats { player -> player.copy(wickets = player.wickets + 1, ballsBowled = player.ballsBowled + 1) }

        val curStrikerIndex = strikerIndex
        val curNonStrikerIndex = nonStrikerIndex
        val curStrikerName = striker?.name
        val curNonStrikerName = nonStriker?.name
        val jokerWasBowling = bowler?.isJoker == true

        toastLong("Wicket! ${striker?.name} is ${wicketType.name.lowercase().replace("_", " ")}")
        handleJokerOutRemoval(jokerWasOut)
        handlePostWicketReplacement(dismissedIndex, curStrikerIndex, curNonStrikerIndex, curStrikerName, curNonStrikerName, jokerWasBowling, jokerWasOut)

        addDelivery("W", highlight = true, runs = 0)
        ballsInOver += 1
        incJokerBallIfBowledThisDelivery()
        handleOverCompletionIfNeeded()
        showWicketDialog = false
        val totalAfter = battingTeamPlayers.sumOf { it.runs } + totalExtras
        toastLong("Wicket! ${outSnapshot?.name ?: "Batsman"} ${wicketType.name.lowercase().replace('_', ' ')}. Total: $totalAfter.")
    }

    // ═════════════════════════════════════════════════════════════════
    //  Fielder selection: CAUGHT / STUMPED (non-run-out)
    // ═════════════════════════════════════════════════════════════════

    fun onFielderSelectedForCaughtStumped(fielder: Player?) {
        // Credit the fielder
        fielder?.let { fieldingPlayer ->
            val fielderIndex = bowlingTeamPlayers.indexOfFirst { it.name == fieldingPlayer.name }
            if (fielderIndex != -1) {
                val updated = bowlingTeamPlayers.toMutableList()
                updated[fielderIndex] = when (pendingWicketType) {
                    WicketType.CAUGHT -> updated[fielderIndex].copy(catches = updated[fielderIndex].catches + 1)
                    WicketType.STUMPED -> updated[fielderIndex].copy(stumpings = updated[fielderIndex].stumpings + 1)
                    else -> updated[fielderIndex]
                }
                bowlingTeamPlayers = updated
            } else if (fieldingPlayer.isJoker) {
                val jokerWithFielding = when (pendingWicketType) {
                    WicketType.CAUGHT -> fieldingPlayer.copy(catches = 1)
                    WicketType.STUMPED -> fieldingPlayer.copy(stumpings = 1)
                    else -> fieldingPlayer
                }
                bowlingTeamPlayers = (bowlingTeamPlayers + jokerWithFielding).toMutableList()
                toast("🃏 Joker credited with ${if (pendingWicketType == WicketType.CAUGHT) "catch" else "stumping"}!")
            }
        }

        val isStumpingOnWide = pendingWideExtraType != null
        val wicketType = pendingWicketType!!
        val dismissedIndex = strikerIndex
        val jokerWasOut = striker?.isJoker == true

        pushSnapshot()
        totalWickets += 1
        updateStrikerAndTotals { p ->
            p.copy(
                isOut = true,
                ballsFaced = if (isStumpingOnWide) p.ballsFaced else p.ballsFaced + 1,
                dismissalType = wicketType,
                bowlerName = bowler?.name,
                fielderName = fielder?.name
            )
        }

        if (isStumpingOnWide) {
            updateBowlerStats { player -> player.copy(runsConceded = player.runsConceded + pendingWideRuns) }
            runsConcededInCurrentOver += pendingWideRuns
            totalExtras += pendingWideRuns
            addDelivery("Wd+${pendingWideRuns} W", highlight = true, runs = pendingWideRuns)
        }

        val outSnapshot = dismissedIndex?.let { battingTeamPlayers.getOrNull(it)?.copy() }
        outSnapshot?.let { endPartnershipAndRecordWicket(it, isRunOut = false) }
        if (outSnapshot != null && outSnapshot.isOut) recordCompletedBatter(outSnapshot)
        updateBowlerStats { player -> player.copy(wickets = player.wickets + 1, ballsBowled = player.ballsBowled + 1) }

        val curStrikerIndex = strikerIndex
        val curNonStrikerIndex = nonStrikerIndex
        val curStrikerName = striker?.name
        val curNonStrikerName = nonStriker?.name
        val jokerWasBowling = bowler?.isJoker == true
        val fielderCredit = if (fielder != null) " (${fielder.name})" else ""
        toastLong("Wicket! ${striker?.name} is ${wicketType.name.lowercase().replace("_", " ")}$fielderCredit")
        handleJokerOutRemoval(jokerWasOut)
        handlePostWicketReplacement(dismissedIndex, curStrikerIndex, curNonStrikerIndex, curStrikerName, curNonStrikerName, jokerWasBowling, jokerWasOut)

        if (!isStumpingOnWide) {
            addDelivery("W", highlight = true, runs = 0)
            ballsInOver += 1
            incJokerBallIfBowledThisDelivery()
            handleOverCompletionIfNeeded()
        }

        showFielderSelectionDialog = false
        pendingWicketType = null
        pendingWideExtraType = null
        pendingWideRuns = 0
        val totalAfter = battingTeamPlayers.sumOf { it.runs } + totalExtras
        toastLong("Wicket! ${outSnapshot?.name ?: "Batsman"} ${wicketType.name.lowercase().replace('_', ' ')}. Total: $totalAfter.")
    }

    // ═════════════════════════════════════════════════════════════════
    //  Run-out handling (fielder selected)
    // ═════════════════════════════════════════════════════════════════

    fun onFielderSelectedForRunOut(fielder: Player?) {
        val input = pendingRunOutInput!!
        pushSnapshot()

        val runsCompleted = input.runsCompleted
        val outPlayerName = input.whoOut
        val outEnd = input.end
        val curStrikerIndex = strikerIndex
        val curNonStrikerIndex = nonStrikerIndex
        val curStrikerName = striker?.name
        val curNonStrikerName = nonStriker?.name

        updateStrikerAndTotals { p ->
            p.copy(
                runs = p.runs + runsCompleted,
                ballsFaced = p.ballsFaced + 1,
                fours = p.fours + if (runsCompleted == 4) 1 else 0,
                sixes = p.sixes + if (runsCompleted == 6) 1 else 0
            )
        }
        updateBowlerStats { b ->
            b.copy(
                runsConceded = b.runsConceded + runsCompleted,
                ballsBowled = if (isNoBallRunOut) b.ballsBowled else b.ballsBowled + 1
            )
        }

        // Credit the fielder
        fielder?.let { fieldingPlayer ->
            val fidx = bowlingTeamPlayers.indexOfFirst { it.name == fieldingPlayer.name }
            if (fidx != -1) {
                val upd = bowlingTeamPlayers.toMutableList()
                upd[fidx] = upd[fidx].copy(runOuts = upd[fidx].runOuts + 1)
                bowlingTeamPlayers = upd
            } else if (fieldingPlayer.isJoker) {
                bowlingTeamPlayers = (bowlingTeamPlayers + fieldingPlayer.copy(runOuts = 1)).toMutableList()
                toast("🃏 Joker credited with run-out!")
            }
        }

        val outIndex = battingTeamPlayers.indexOfFirst { it.name.equals(outPlayerName, ignoreCase = true) }
        if (outIndex == -1) {
            toastLong("Could not find player \"$outPlayerName\" in batting team")
            showFielderSelectionDialog = false; pendingRunOutInput = null; pendingWicketType = null
            return
        }

        val newBatting = battingTeamPlayers.toMutableList()
        val wasJoker = newBatting[outIndex].isJoker
        val jokerWasBowling = bowler?.isJoker == true
        val jokerWasOut = wasJoker
        newBatting[outIndex] = newBatting[outIndex].copy(isOut = true, dismissalType = WicketType.RUN_OUT, fielderName = fielder?.name)
        battingTeamPlayers = newBatting

        if (wasJoker) {
            jokerOutInCurrentInnings = true
            if (strikerIndex == outIndex) strikerIndex = null
            if (nonStrikerIndex == outIndex) nonStrikerIndex = null
            else if (nonStrikerIndex != null && nonStrikerIndex!! > outIndex) nonStrikerIndex = nonStrikerIndex!! - 1
            toast("🃏 Joker is now available for Bowling (run out).")
        }

        val outSnapshot = battingTeamPlayers[outIndex].copy()
        endPartnershipAndRecordWicket(outSnapshot, isRunOut = true)
        recordCompletedBatter(outSnapshot)
        totalWickets += 1

        // Defensive warning
        if ((outIndex == curStrikerIndex && outEnd == RunOutEnd.NON_STRIKER_END) ||
            (outIndex == curNonStrikerIndex && outEnd == RunOutEnd.STRIKER_END)) {
            toastLong("Note: $outPlayerName position mismatch with selected end. Honouring scorer input.")
        }

        if (strikerIndex == outIndex) strikerIndex = null
        if (nonStrikerIndex == outIndex) nonStrikerIndex = null

        if (outIndex == curNonStrikerIndex && outEnd == RunOutEnd.STRIKER_END) {
            nonStrikerIndex = curStrikerIndex; strikerIndex = null
        } else if (outIndex == curStrikerIndex && outEnd == RunOutEnd.NON_STRIKER_END) {
            strikerIndex = curNonStrikerIndex; nonStrikerIndex = null
        }

        // Replacement
        val availableBatsmenAfterRunOut = battingTeamPlayers.count { !it.isOut }
        val jokerAvailForBat = jokerPlayer != null && !battingTeamPlayers.any { it.isJoker } && !jokerOutInCurrentInnings
        val totalAvail = availableBatsmenAfterRunOut + if (jokerAvailForBat) 1 else 0

        when {
            totalAvail == 0 -> { strikerIndex = null; nonStrikerIndex = null }
            matchSettings.allowSingleSideBatting && totalAvail == 1 -> {
                if (jokerAvailForBat && availableBatsmenAfterRunOut == 0) {
                    selectingBatsman = if (outEnd == RunOutEnd.STRIKER_END) 1 else 2
                    pickerOtherEndName = if (outEnd == RunOutEnd.STRIKER_END) curNonStrikerName else curStrikerName
                    showBatsmanDialog = true; toastLong("🃏 Only joker available to bat!")
                } else {
                    val lastBatsman = battingTeamPlayers.indexOfFirst { !it.isOut }
                    if (outEnd == RunOutEnd.STRIKER_END) strikerIndex = lastBatsman else nonStrikerIndex = lastBatsman
                    toastLong("Single side batting: ${battingTeamPlayers[lastBatsman].name} continues alone")
                }
            }
            !matchSettings.allowSingleSideBatting && totalAvail == 1 -> { strikerIndex = null; nonStrikerIndex = null }
            else -> {
                if (outEnd == RunOutEnd.STRIKER_END) {
                    selectingBatsman = 1; pickerOtherEndName = curNonStrikerName; showBatsmanDialog = true
                } else {
                    selectingBatsman = 2; pickerOtherEndName = curStrikerName; showBatsmanDialog = true
                }
                if (jokerWasBowling && !jokerWasOut) toastLong("🃏 Joker can now bat!")
            }
        }

        val label = "${runsCompleted} + RO (${outPlayerName} @ ${if (outEnd == RunOutEnd.STRIKER_END) "S" else "NS"})"
        addDelivery(label, highlight = true, runs = runsCompleted)
        if (!isNoBallRunOut) {
            ballsInOver += 1
            incJokerBallIfBowledThisDelivery()
        }
        handleOverCompletionIfNeeded()

        val fielderCredit = if (fielder != null) " (${fielder.name})" else ""
        toastLong("Run out! $outPlayerName dismissed$fielderCredit. $runsCompleted run(s) recorded.")
        showFielderSelectionDialog = false
        pendingRunOutInput = null
        pendingWicketType = null
        isNoBallRunOut = false
    }

    // ═════════════════════════════════════════════════════════════════
    //  Shared wicket helpers
    // ═════════════════════════════════════════════════════════════════

    private fun recordCompletedBatter(outSnapshot: Player) {
        if (currentInnings == 1) {
            if (completedBattersInnings1.none { it.name.equals(outSnapshot.name, true) }) {
                completedBattersInnings1 = (completedBattersInnings1 + outSnapshot).toMutableList()
            } else {
                completedBattersInnings1 = completedBattersInnings1
                    .map { if (it.name.equals(outSnapshot.name, true)) outSnapshot else it }.toMutableList()
            }
        } else {
            if (completedBattersInnings2.none { it.name.equals(outSnapshot.name, true) }) {
                completedBattersInnings2 = (completedBattersInnings2 + outSnapshot).toMutableList()
            } else {
                completedBattersInnings2 = completedBattersInnings2
                    .map { if (it.name.equals(outSnapshot.name, true)) outSnapshot else it }.toMutableList()
            }
        }
    }

    private fun handleJokerOutRemoval(jokerWasOut: Boolean) {
        if (!jokerWasOut) return
        jokerOutInCurrentInnings = true
        val jokerBattingIndex = battingTeamPlayers.indexOfFirst { it.isJoker }
        if (jokerBattingIndex != -1) {
            if (strikerIndex == jokerBattingIndex) strikerIndex = null
            if (nonStrikerIndex == jokerBattingIndex) nonStrikerIndex = null
            else if (nonStrikerIndex != null && nonStrikerIndex!! > jokerBattingIndex) {
                nonStrikerIndex = nonStrikerIndex!! - 1
            }
        }
        toast("🃏 Joker is now available for bowling!")
    }

    private fun handlePostWicketReplacement(
        dismissedIndex: Int?,
        curStrikerIndex: Int?,
        curNonStrikerIndex: Int?,
        curStrikerName: String?,
        curNonStrikerName: String?,
        jokerWasBowling: Boolean,
        jokerWasOut: Boolean,
    ) {
        val availableBatsmenAfterWicket = battingTeamPlayers.count { !it.isOut }
        val jokerAvailForBat = jokerPlayer != null && !battingTeamPlayers.any { it.isJoker } && !jokerOutInCurrentInnings
        val totalAvail = availableBatsmenAfterWicket + if (jokerAvailForBat) 1 else 0

        val inningsWillEnd = if (matchSettings.allowSingleSideBatting) totalAvail == 0
        else totalAvail < 2

        when {
            inningsWillEnd -> { strikerIndex = null; nonStrikerIndex = null }
            matchSettings.allowSingleSideBatting && totalAvail == 1 -> {
                if (jokerAvailForBat && availableBatsmenAfterWicket == 0) {
                    selectingBatsman = if (dismissedIndex == curStrikerIndex) 1 else 2
                    pickerOtherEndName = if (dismissedIndex == curStrikerIndex) curNonStrikerName else curStrikerName
                    showBatsmanDialog = true; toastLong("🃏 Only joker available to bat!")
                } else {
                    val lastBatsman = battingTeamPlayers.indexOfFirst { !it.isOut }
                    if (curStrikerIndex == dismissedIndex) strikerIndex = lastBatsman
                    else nonStrikerIndex = lastBatsman
                    toastLong("Single side batting: ${battingTeamPlayers[lastBatsman].name} continues alone")
                }
            }
            !matchSettings.allowSingleSideBatting && totalAvail == 1 -> { strikerIndex = null; nonStrikerIndex = null }
            else -> {
                if (dismissedIndex == curStrikerIndex) {
                    strikerIndex = null; selectingBatsman = 1; pickerOtherEndName = curNonStrikerName
                } else {
                    nonStrikerIndex = null; selectingBatsman = 2; pickerOtherEndName = curStrikerName
                }
                showBatsmanDialog = true
                if (jokerWasBowling && !jokerWasOut) toastLong("🃏 Joker can now bat!")
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Innings transition
    // ═════════════════════════════════════════════════════════════════

    fun onInningsComplete() {
        showBatsmanDialog = false; showBowlerDialog = false; showWicketDialog = false
        showExtrasDialog = false; showQuickWideDialog = false; showQuickNoBallDialog = false

        if (currentInnings == 1) {
            firstInningsRuns = calculatedTotalRuns
            firstInningsWickets = totalWickets
            firstInningsOvers = currentOver
            firstInningsBalls = ballsInOver
            val activeBatters = battingTeamPlayers.filter { player ->
                player.ballsFaced > 0 || player.runs > 0 ||
                        strikerIndex?.let { battingTeamPlayers[it].name == player.name } == true ||
                        nonStrikerIndex?.let { battingTeamPlayers[it].name == player.name } == true
            }
            val completedNames = completedBattersInnings1.map { it.name }.toSet()
            firstInningsBattingPlayersList =
                (completedBattersInnings1 + activeBatters.filterNot { it.name in completedNames }.map { it.copy() }).distinctBy { it.name }
            firstInningsBowlingPlayersList =
                (bowlingTeamPlayers.filter { it.ballsBowled > 0 || it.wickets > 0 || it.runsConceded > 0 }.map { it.copy() } + completedBowlersInnings1).distinctBy { it.name }
            previousBowlerName = null
            saveInningsPartnershipsAndWickets()
            showInningsBreakDialog = true
        } else {
            val activeBatters = battingTeamPlayers.filter { player ->
                player.ballsFaced > 0 || player.runs > 0 ||
                        strikerIndex?.let { battingTeamPlayers[it].name == player.name } == true ||
                        nonStrikerIndex?.let { battingTeamPlayers[it].name == player.name } == true
            }
            val completedNames = completedBattersInnings2.map { it.name }.toSet()
            val secondBattingList = (completedBattersInnings2 + activeBatters.filterNot { it.name in completedNames }.map { it.copy() }).distinctBy { it.name }
            secondInningsBattingPlayers = secondBattingList
            val secondBowlingList = (bowlingTeamPlayers.filter { it.ballsBowled > 0 || it.wickets > 0 || it.runsConceded > 0 }.map { it.copy() } + completedBowlersInnings2).distinctBy { it.name }
            secondInningsBattingPlayers = secondBattingList
            secondInningsBowlingPlayers = secondBowlingList
            showMatchCompleteDialog = true
        }
    }

    fun onStartSecondInnings() {
        currentInnings = 2
        val tempPlayers = battingTeamPlayers
        val tempName = battingTeamName
        battingTeamPlayers = bowlingTeamPlayers
        bowlingTeamPlayers = tempPlayers
        battingTeamName = bowlingTeamName
        bowlingTeamName = tempName
        battingTeamPlayers = battingTeamPlayers.map { player ->
            player.copy(runs = 0, ballsFaced = 0, fours = 0, sixes = 0, isOut = false)
        }.toMutableList()
        bowlingTeamPlayers = bowlingTeamPlayers.map { player ->
            player.copy(wickets = 0, runsConceded = 0, ballsBowled = 0, isOut = false)
        }.toMutableList()
        battingTeamPlayers = battingTeamPlayers.filter { !it.isJoker }.toMutableList()
        bowlingTeamPlayers = bowlingTeamPlayers.filter { !it.isJoker }.toMutableList()
        jokerOutInCurrentInnings = false

        totalWickets = 0
        currentOver = 0
        ballsInOver = 0
        totalExtras = 0
        previousBowlerName = null
        completedBowlersInnings2 = mutableListOf()
        currentBowlerSpell = 0
        strikerIndex = null
        nonStrikerIndex = null
        bowlerIndex = null

        powerplayRunsInnings2 = 0
        powerplayDoublingDoneInnings2 = false

        currentPartnershipRuns = 0
        currentPartnershipBalls = 0
        currentPartnershipBatsman1Runs = 0
        currentPartnershipBatsman2Runs = 0
        currentPartnershipBatsman1Balls = 0
        currentPartnershipBatsman2Balls = 0
        currentPartnershipBatsman1Name = null
        currentPartnershipBatsman2Name = null

        showInningsBreakDialog = false
        showBatsmanDialog = true
        selectingBatsman = 1
    }

    // ═════════════════════════════════════════════════════════════════
    //  Powerplay doubling check
    // ═════════════════════════════════════════════════════════════════

    fun checkPowerplayDoubling() {
        if (matchSettings.powerplayOvers > 0 && matchSettings.doubleRunsInPowerplay) {
            if (currentOver == matchSettings.powerplayOvers) {
                if (currentInnings == 1 && !powerplayDoublingDoneInnings1) {
                    val runsToDouble = calculatedTotalRuns - powerplayRunsInnings1
                    if (runsToDouble > 0) {
                        totalExtras += runsToDouble
                        powerplayDoublingDoneInnings1 = true
                        toastLong("🚀 Powerplay ended! $runsToDouble runs doubled! New total: $calculatedTotalRuns")
                    }
                } else if (currentInnings == 2 && !powerplayDoublingDoneInnings2) {
                    val runsToDouble = calculatedTotalRuns - powerplayRunsInnings2
                    if (runsToDouble > 0) {
                        totalExtras += runsToDouble
                        powerplayDoublingDoneInnings2 = true
                        toastLong("🚀 Powerplay ended! $runsToDouble runs doubled! New total: $calculatedTotalRuns")
                    }
                }
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Batsman selection (from dialog)
    // ═════════════════════════════════════════════════════════════════

    fun onBatsmanSelected(player: Player) {
        val jokerAvailInsideDialog = jokerPlayer != null &&
                !battingTeamPlayers.any { it.isJoker } && !jokerPlayer.isOut
        val availableBatsmenCount = battingTeamPlayers.count { !it.isOut || it.isRetired }

        if (selectingBatsman == 1) {
            handleBatsmanSelection(player, isStriker = true)
            if (!matchSettings.allowSingleSideBatting && nonStrikerIndex == null &&
                (availableBatsmenCount + if (jokerAvailInsideDialog) 1 else 0) > 1) {
                selectingBatsman = 2
            } else {
                showBatsmanDialog = false
                handlePendingAfterBatsmanPick()
            }
        } else {
            handleBatsmanSelection(player, isStriker = false)
            showBatsmanDialog = false
            handlePendingAfterBatsmanPick()
        }
    }

    private fun handleBatsmanSelection(player: Player, isStriker: Boolean) {
        if (player.isJoker) {
            val jokerBowlingIndex = bowlingTeamPlayers.indexOfFirst { it.isJoker }
            if (jokerBowlingIndex != -1) {
                if (bowlerIndex == jokerBowlingIndex) {
                    recordCurrentBowlerIfAny()
                    bowlerIndex = null
                    currentBowlerSpell = 0
                    if (ballsInOver > 0) {
                        midOverReplacementDueToJoker.value = true
                        toastLong("🃏 Joker switched to bat. Select a new bowler to complete the over.")
                        showBowlerDialog = true
                    }
                }
                val newBowlingList = bowlingTeamPlayers.toMutableList()
                newBowlingList.removeAt(jokerBowlingIndex)
                bowlingTeamPlayers = newBowlingList
                if (previousBowlerName == jokerName) previousBowlerName = null
            }
            if (!battingTeamPlayers.any { it.isJoker }) {
                val newList = battingTeamPlayers.toMutableList()
                newList.add(jokerPlayer!!.copy())
                battingTeamPlayers = newList
                if (isStriker) strikerIndex = battingTeamPlayers.size - 1 else nonStrikerIndex = battingTeamPlayers.size - 1
            } else {
                val idx = battingTeamPlayers.indexOfFirst { it.isJoker }
                if (isStriker) strikerIndex = idx else nonStrikerIndex = idx
            }
            jokerOutInCurrentInnings = false
        } else {
            val playerIndex = battingTeamPlayers.indexOfFirst { it.name.trim().equals(player.name.trim(), ignoreCase = true) }
            if (isStriker) strikerIndex = playerIndex else nonStrikerIndex = playerIndex
            if (playerIndex >= 0 && battingTeamPlayers[playerIndex].isRetired) {
                val unretiredPlayer = battingTeamPlayers[playerIndex].copy(isRetired = false)
                battingTeamPlayers = battingTeamPlayers.toMutableList().apply { this[playerIndex] = unretiredPlayer }
                toast("${unretiredPlayer.name} returns to bat")
            }
        }
    }

    private fun handlePendingAfterBatsmanPick() {
        if (pendingSwapAfterBatsmanPick) {
            if (!showSingleSideLayout) swapStrike()
            pendingSwapAfterBatsmanPick = false
        }
        if (pendingBowlerDialogAfterBatsmanPick) {
            showBowlerDialog = true
            pendingBowlerDialogAfterBatsmanPick = false
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Bowler selection (from dialog)
    // ═════════════════════════════════════════════════════════════════

    fun onBowlerSelected(player: Player, overrideToCompleteOverAllowed: Boolean) {
        fun norm(s: String): String = s.trim().replace(Regex("\\s+"), " ")
        val ballsSoFar = if (player.isJoker) jokerBallsBowledThisInningsRaw()
        else {
            val idxInBowling = bowlingTeamPlayers.indexOfFirst { norm(it.name).equals(norm(player.name), ignoreCase = true) }
            bowlingTeamPlayers.getOrNull(idxInBowling)?.ballsBowled ?: 0
        }
        val capOvers = if (player.isJoker) matchSettings.jokerMaxOvers else matchSettings.maxOversPerBowler
        val capBalls = capOvers * 6
        val remainingBalls = (capBalls - ballsSoFar).coerceAtLeast(0)

        // If selecting same bowler mid-over, just close
        run {
            val currentIdx = bowlerIndex
            if (ballsInOver > 0 && currentIdx != null) {
                val current = bowlingTeamPlayers.getOrNull(currentIdx)
                if (current != null) {
                    val same = (!player.isJoker && norm(current.name).equals(norm(player.name), ignoreCase = true)) ||
                            (player.isJoker && current.isJoker)
                    if (same) { showBowlerDialog = false; return }
                }
            }
        }

        // New-over requires 6 legal balls
        if (ballsInOver == 0 && remainingBalls < 6) {
            val canOverride = overrideToCompleteOverAllowed && !player.isJoker
            if (!canOverride) {
                val who = if (player.isJoker) "Joker" else player.name
                toastLong("$who cannot start a new over (only $remainingBalls legal ball${if (remainingBalls != 1) "s" else ""} remaining; needs 6).")
                return
            } else {
                toast("${player.name} will exceed max-over cap only to complete this over.")
            }
        }

        // Mid-over replacement only allowed after Joker left
        if (ballsInOver > 0) {
            if (!midOverReplacementDueToJoker.value) {
                toastLong("Mid-over bowler change is only allowed when replacing the Joker.")
                return
            }
            val need = 6 - ballsInOver
            if (remainingBalls < need) {
                val who = if (player.isJoker) "Joker" else player.name
                toastLong("$who cannot replace mid-over (needs $need balls, only $remainingBalls remaining).")
                return
            }
        }

        // Proceed
        if (player.isJoker) {
            if (!bowlingTeamPlayers.any { it.isJoker } && jokerPlayer != null) {
                bowlingTeamPlayers = (bowlingTeamPlayers + jokerPlayer.copy()).toMutableList()
            }
            ensureJokerStatsAppliedOnAdd()
            bowlerIndex = bowlingTeamPlayers.indexOfFirst { it.isJoker }
        } else {
            bowlerIndex = bowlingTeamPlayers.indexOfFirst { norm(it.name).equals(norm(player.name), ignoreCase = true) }
        }
        currentBowlerSpell = 1
        showBowlerDialog = false
        midOverReplacementDueToJoker.value = false
    }

    // ═════════════════════════════════════════════════════════════════
    //  Retirement
    // ═════════════════════════════════════════════════════════════════

    fun onRetireBatsman(position: Int) {
        retiringPosition = position
        showRetirementDialog = false
        val batsmanIndex = if (position == 1) strikerIndex else nonStrikerIndex
        batsmanIndex?.let { idx ->
            val updatedPlayer = battingTeamPlayers[idx].copy(isRetired = true)
            battingTeamPlayers = battingTeamPlayers.toMutableList().apply { this[idx] = updatedPlayer }
            if (position == 1) strikerIndex = null else nonStrikerIndex = null
            selectingBatsman = position
            showBatsmanDialog = true
            toast("${updatedPlayer.name} retired")
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Wide with stumping (from extras dialog)
    // ═════════════════════════════════════════════════════════════════

    fun onWideWithStumping(extraType: ExtraType, baseRuns: Int) {
        showExtrasDialog = false
        pendingWicketType = WicketType.STUMPED
        pendingWideExtraType = extraType
        pendingWideRuns = baseRuns
        showFielderSelectionDialog = true
    }

    // ═════════════════════════════════════════════════════════════════
    //  Partnership initialization (called from LaunchedEffect)
    // ═════════════════════════════════════════════════════════════════

    fun initPartnershipIfNeeded() {
        if (striker != null && nonStriker != null && currentPartnershipBatsman1Name == null) {
            currentPartnershipRuns = 0
            currentPartnershipBalls = 0
            currentPartnershipBatsman1Runs = 0
            currentPartnershipBatsman2Runs = 0
            currentPartnershipBatsman1Balls = 0
            currentPartnershipBatsman2Balls = 0
            currentPartnershipBatsman1Name = striker!!.name
            currentPartnershipBatsman2Name = nonStriker!!.name
        }
    }
}
