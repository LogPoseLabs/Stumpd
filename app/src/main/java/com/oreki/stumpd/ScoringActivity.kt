package com.oreki.stumpd

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.google.gson.Gson
import com.oreki.stumpd.data.repository.MatchRepository
import com.oreki.stumpd.data.manager.InProgressMatchManager
import com.oreki.stumpd.data.models.createMatchInProgress
import com.oreki.stumpd.data.models.toPlayerList
import com.oreki.stumpd.ui.history.rememberMatchRepository
import com.oreki.stumpd.ui.history.rememberPlayerRepository
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.ui.theme.StumpdTopBar
import com.oreki.stumpd.ui.theme.sectionContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID
import com.oreki.stumpd.data.sync.sharing.MatchSharingManager
import android.content.ClipboardManager
import android.content.ClipData
import androidx.compose.ui.platform.ClipboardManager as ComposeClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString


class ScoringActivity : ComponentActivity() {
    
    private var persistedMatchId: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        
        // Restore matchId from saved state (survives configuration changes)
        persistedMatchId = savedInstanceState?.getString("MATCH_ID")
        
        val team1Name = intent.getStringExtra("team1_name") ?: "Team A"
        val team2Name = intent.getStringExtra("team2_name") ?: "Team B"
        val jokerName = intent.getStringExtra("joker_name") ?: ""
        val team1CaptainName = intent.getStringExtra("team1_captain")
        val team2CaptainName = intent.getStringExtra("team2_captain")
        val team1PlayerNames = intent.getStringArrayExtra("team1_players") ?: arrayOf("Player 1", "Player 2", "Player 3")
        val team2PlayerNames = intent.getStringArrayExtra("team2_players") ?: arrayOf("Player 4", "Player 5", "Player 6")
        val team1PlayerIds = intent.getStringArrayExtra("team1_player_ids") ?: emptyArray()
        val team2PlayerIds = intent.getStringArrayExtra("team2_player_ids") ?: emptyArray()
        val matchSettingsJson = intent.getStringExtra("match_settings") ?: ""
        val groupId = intent.getStringExtra("group_id")
        val groupName = intent.getStringExtra("group_name")
        val tossWinner = intent.getStringExtra("toss_winner")
        val tossChoice = intent.getStringExtra("toss_choice")
        val resumeMatchId = intent.getStringExtra("resume_match_id")
        
        // Priority: persistedMatchId (config change) > resumeMatchId (explicit resume) > new UUID
        val matchId = persistedMatchId ?: resumeMatchId ?: UUID.randomUUID().toString()
        
        // Save for future configuration changes
        persistedMatchId = matchId

        setContent {
            StumpdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ScoringScreen(
                        matchId = matchId,
                        team1Name = team1Name,
                        team2Name = team2Name,
                        jokerName = jokerName,
                        team1CaptainName = team1CaptainName,
                        team2CaptainName = team2CaptainName,
                        team1PlayerNames = team1PlayerNames,
                        team2PlayerNames = team2PlayerNames,
                        team1PlayerIds = team1PlayerIds,
                        team2PlayerIds = team2PlayerIds,
                        matchSettingsJson = matchSettingsJson,
                        groupId = groupId,
                        groupName = groupName,
                        tossChoice = tossChoice,
                        tossWinner = tossWinner,
                        // If we have a persistedMatchId (config change), treat it as a resume
                        resumeMatchId = if (persistedMatchId != null) matchId else resumeMatchId
                    )
                }
            }
        }
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save matchId to survive configuration changes (theme switch, rotation, etc.)
        persistedMatchId?.let {
            outState.putString("MATCH_ID", it)
        }
    }
}

object NoBallOutcomeHolders {
    // Set by ExtrasDialog for NO_BALL flow; consumed in NO_BALL handler and cleared
    val noBallSubOutcome = mutableStateOf(NoBallSubOutcome.NONE)
    val noBallRunOutInput = mutableStateOf<RunOutInput?>(null)
    val noBallBoundaryOutInput = mutableStateOf<NoBallBoundaryOutInput?>(null)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScoringScreen(
    matchId: String = UUID.randomUUID().toString(),
    team1Name: String = "Team A",
    team2Name: String = "Team B",
    jokerName: String = "",
    team1CaptainName: String? = null,
    team2CaptainName: String? = null,
    team1PlayerNames: Array<String> = arrayOf("Player 1", "Player 2", "Player 3"),
    team2PlayerNames: Array<String> = arrayOf("Player 4", "Player 5", "Player 6"),
    team1PlayerIds: Array<String> = arrayOf("1","2"),
    team2PlayerIds: Array<String> = arrayOf("1","2"),
    matchSettingsJson: String = "",
    groupId: String?,
    groupName: String?,
    tossChoice: String?,
    tossWinner: String?,
    resumeMatchId: String? = null,
) {
    val context = LocalContext.current
    val repo = rememberMatchRepository()
    val scope = rememberCoroutineScope()
    val playerRepo = rememberPlayerRepository()
    var idToName by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val gson = Gson()
    val inProgressManager = remember { InProgressMatchManager(context) }
    var isResuming by remember { mutableStateOf(resumeMatchId != null) }
    var resumedMatchLoaded by remember { mutableStateOf(false) }

    // Unlimited undo state
    var unlimitedUndoEnabled by remember {
        mutableStateOf(com.oreki.stumpd.utils.FeatureFlags.isUnlimitedUndoEnabled(context))
    }
    var showUnlimitedUndoDialog by remember { mutableStateOf(false) }
    var pendingUnlimitedUndoValue by remember { mutableStateOf(false) }
    
    // Auto-share match when it starts (runs once)
    LaunchedEffect(matchId) {
        scope.launch {
            try {
                // Wait a bit for match screen to fully load
                kotlinx.coroutines.delay(1000)
                
                // Authenticate if needed
                val authHelper = com.oreki.stumpd.data.sync.firebase.EnhancedFirebaseAuthHelper(context)
                var userId = authHelper.currentUserId
                
                if (userId == null) {
                    android.util.Log.d("ScoringActivity", "No userId, signing in anonymously...")
                    val user = authHelper.signInAnonymously()
                    userId = user?.uid
                    
                    if (userId == null) {
                        android.util.Log.e("ScoringActivity", "Auto-share: Authentication failed")
                        return@launch
                    }
                    
                    android.util.Log.d("ScoringActivity", "Auto-share: Signed in with userId: $userId")
                    // Give Firebase MORE time to propagate (increased from 500ms)
                    kotlinx.coroutines.delay(2000)
                } else {
                    android.util.Log.d("ScoringActivity", "Auto-share: Already authenticated with userId: $userId")
                    // Even if already authenticated, wait a bit for Firebase to be ready
                    kotlinx.coroutines.delay(1000)
                }
                
                // Auto-share match silently
                android.util.Log.d("ScoringActivity", "Auto-share: Attempting to share matchId: $matchId")
                val sharingManager = com.oreki.stumpd.data.sync.sharing.MatchSharingManager()
                val result = sharingManager.shareMatch(
                    ownerId = userId,
                    matchId = matchId,
                    ownerName = null,
                    expiryHours = 48
                )
                
                result.onSuccess { code ->
                    android.util.Log.d("ScoringActivity", "Auto-share: SUCCESS! Code: $code for matchId: $matchId")
                }.onFailure { error ->
                    android.util.Log.e("ScoringActivity", "Auto-share: FAILED for matchId: $matchId - ${error.message}", error)
                }
            } catch (e: Exception) {
                // Silent fail - match will still work locally
                android.util.Log.e("ScoringActivity", "Auto-share: Exception for matchId: $matchId", e)
            }
        }
    }
    
    LaunchedEffect(Unit) {
        val players = playerRepo.getAllPlayers()
        idToName = players.associate { it.id to it.name }
    }
    // If incoming extras use IDs, wait until Room name map is ready
    val needsMap = team1PlayerIds.isNotEmpty() || team2PlayerIds.isNotEmpty()
    if (needsMap && idToName.isEmpty()) {
        // minimal placeholder; prevents UUIDs flashing in dialogs
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }


    val matchSettings = remember {
        try {
            if (matchSettingsJson.isNotEmpty()) {
                gson.fromJson(matchSettingsJson, MatchSettings::class.java)
            } else {
                MatchSettingsManager(context).getDefaultMatchSettings()
            }
        } catch (e: Exception) {
            MatchSettings()
        }
    }
    var team1Players by remember(idToName) {
        mutableStateOf(
            if (team1PlayerIds.isNotEmpty()) {
                team1PlayerIds.map { id ->
                    Player(id = PlayerId(id), name = idToName[id] ?: id)
                }.toMutableList()
            } else {
                team1PlayerNames.map { Player(name = it) }.toMutableList()
            }
        )
    }

    var team2Players by remember(idToName) {
        mutableStateOf(
            if (team2PlayerIds.isNotEmpty()) {
                team2PlayerIds.map { id ->
                    Player(id = PlayerId(id), name = idToName[id] ?: id)
                }.toMutableList()
            } else {
                team2PlayerNames.map { Player(name = it) }.toMutableList()
            }
        )
    }

    val jokerPlayer = remember {
        if (jokerName.isNotEmpty()) {
            Player(name = jokerName, isJoker = true)
        } else null
    }

    var currentInnings by remember { mutableStateOf(1) }
    // read toss extras once
//    val tossWinnerExtra = remember { (LocalContext.current as? ComponentActivity)?.intent?.getStringExtra("toss_winner") ?: "" }
//    val tossChoiceExtra = remember { (LocalContext.current as? ComponentActivity)?.intent?.getStringExtra("toss_choice") ?: "" }

    val initialBattingTeamPlayers: MutableList<Player>
    val initialBowlingTeamPlayers: MutableList<Player>
    val initialBattingTeamName: String
    val initialBowlingTeamName: String

    if (tossWinner != null && tossChoice != null) {
        val team1Won = tossWinner.equals(team1Name, ignoreCase = true)
        val battingChosen = tossChoice.contains("Batting", ignoreCase = true)

        // Decide who bats first
        val team1BatsFirst = (team1Won && battingChosen) || (!team1Won && !battingChosen)

        if (team1BatsFirst) {
            initialBattingTeamPlayers = team1Players
            initialBowlingTeamPlayers = team2Players
            initialBattingTeamName = team1Name
            initialBowlingTeamName = team2Name
        } else {
            initialBattingTeamPlayers = team2Players
            initialBowlingTeamPlayers = team1Players
            initialBattingTeamName = team2Name
            initialBowlingTeamName = team1Name
        }
    } else {
        // default
        initialBattingTeamPlayers = team1Players
        initialBowlingTeamPlayers = team2Players
        initialBattingTeamName = team1Name
        initialBowlingTeamName = team2Name
    }

    var battingTeamPlayers by remember { mutableStateOf(initialBattingTeamPlayers) }
    var bowlingTeamPlayers by remember { mutableStateOf(initialBowlingTeamPlayers) }
    var battingTeamName by remember { mutableStateOf(initialBattingTeamName) }
    var bowlingTeamName by remember { mutableStateOf(initialBowlingTeamName) }


    var firstInningsRuns by remember { mutableStateOf(0) }
    var firstInningsWickets by remember { mutableStateOf(0) }
    var firstInningsOvers by remember { mutableStateOf(0) }
    var firstInningsBalls by remember { mutableStateOf(0) }
    var firstInningsBattingPlayersList by remember { mutableStateOf<List<Player>>(emptyList()) }
    var firstInningsBowlingPlayersList by remember { mutableStateOf<List<Player>>(emptyList()) }
    var secondInningsBattingPlayers by remember { mutableStateOf<List<Player>>(emptyList()) }
    var secondInningsBowlingPlayers by remember { mutableStateOf<List<Player>>(emptyList()) }

    var totalWickets by remember { mutableStateOf(0) }
    var currentOver by remember { mutableStateOf(0) }
    var ballsInOver by remember { mutableStateOf(0) }
    var totalExtras by remember { mutableStateOf(0) }

    // Partnership and Fall of Wickets tracking
    var currentPartnershipRuns by remember { mutableStateOf(0) }
    var currentPartnershipBalls by remember { mutableStateOf(0) }
    var currentPartnershipBatsman1Runs by remember { mutableStateOf(0) }
    var currentPartnershipBatsman2Runs by remember { mutableStateOf(0) }
    var currentPartnershipBatsman1Balls by remember { mutableStateOf(0) }
    var currentPartnershipBatsman2Balls by remember { mutableStateOf(0) }
    var currentPartnershipBatsman1Name by remember { mutableStateOf<String?>(null) }
    var currentPartnershipBatsman2Name by remember { mutableStateOf<String?>(null) }
    var partnerships by remember { mutableStateOf<List<Partnership>>(emptyList()) }
    var fallOfWickets by remember { mutableStateOf<List<FallOfWicket>>(emptyList()) }
    var firstInningsPartnerships by remember { mutableStateOf<List<Partnership>>(emptyList()) }
    var firstInningsFallOfWickets by remember { mutableStateOf<List<FallOfWicket>>(emptyList()) }

    val calculatedTotalRuns = remember(battingTeamPlayers, totalExtras) {
        battingTeamPlayers.sumOf { it.runs } + totalExtras
    }

    var currentBowlerSpell by remember { mutableStateOf(0) }
    var runsConcededInCurrentOver by remember { mutableStateOf(0) }
    var strikerIndex by remember { mutableStateOf<Int?>(null) }
    var nonStrikerIndex by remember { mutableStateOf<Int?>(null) }
    var bowlerIndex by remember { mutableStateOf<Int?>(null) }

    val striker = strikerIndex?.let { battingTeamPlayers.getOrNull(it) }
    val nonStriker = nonStrikerIndex?.let { battingTeamPlayers.getOrNull(it) }
    val bowler = bowlerIndex?.let { bowlingTeamPlayers.getOrNull(it) }

    // Initialize partnership when both batsmen are set
    LaunchedEffect(striker, nonStriker) {
        if (striker != null && nonStriker != null && currentPartnershipBatsman1Name == null) {
            currentPartnershipRuns = 0
            currentPartnershipBalls = 0
            currentPartnershipBatsman1Runs = 0
            currentPartnershipBatsman2Runs = 0
            currentPartnershipBatsman1Balls = 0
            currentPartnershipBatsman2Balls = 0
            currentPartnershipBatsman1Name = striker.name
            currentPartnershipBatsman2Name = nonStriker.name
        }
    }

    var showBatsmanDialog by remember { mutableStateOf(false) }
    var showBowlerDialog by remember { mutableStateOf(false) }
    var showWicketDialog by remember { mutableStateOf(false) }
    var showInningsBreakDialog by remember { mutableStateOf(false) }
    var showMatchCompleteDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showLiveScorecardDialog by remember { mutableStateOf(false) }
    var showExtrasDialog by remember { mutableStateOf(false) }
    var showQuickWideDialog by remember { mutableStateOf(false) }
    var showQuickNoBallDialog by remember { mutableStateOf(false) }
    var showRetirementDialog by remember { mutableStateOf(false) }
    var selectingBatsman by remember { mutableStateOf(1) }
    var retiringPosition by remember { mutableStateOf<Int?>(null) } // 1 for striker, 2 for non-striker

    var previousBowlerName by remember { mutableStateOf<String?>(null) }

    var completedBattersInnings1 by remember { mutableStateOf(mutableListOf<Player>()) }
    var completedBattersInnings2 by remember { mutableStateOf(mutableListOf<Player>()) }

    var completedBowlersInnings1 by remember { mutableStateOf(mutableListOf<Player>()) }
    var completedBowlersInnings2 by remember { mutableStateOf(mutableListOf<Player>()) }

    var jokerOutInCurrentInnings by remember { mutableStateOf(false) }
    // Joker per-innings ball counters and helpers (ADD)
    var jokerBallsBowledInnings1 by remember { mutableStateOf(0) }
    var jokerBallsBowledInnings2 by remember { mutableStateOf(0) }
    val midOverReplacementDueToJoker = remember { mutableStateOf(false) }

    val deliveryHistory = remember { mutableStateListOf<DeliverySnapshot>() }
    val allDeliveries = remember { mutableStateListOf<DeliveryUI>() }
    // Derived state lists
    val currentOverNumber by remember { derivedStateOf { currentOver + 1 } }   // 1‑based
    val currentOverDeliveries by remember(allDeliveries, currentOver, currentInnings) {
        derivedStateOf {
            allDeliveries.filter { it.inning == currentInnings && it.over == currentOverNumber }
        }
    }
    
    // Powerplay state
    var powerplayRunsInnings1 by remember { mutableStateOf(0) }
    var powerplayRunsInnings2 by remember { mutableStateOf(0) }
    var powerplayDoublingDoneInnings1 by remember { mutableStateOf(false) }
    var powerplayDoublingDoneInnings2 by remember { mutableStateOf(false) }
    
    // Check if currently in powerplay
    val isPowerplayActive = matchSettings.powerplayOvers > 0 && currentOver < matchSettings.powerplayOvers

    var showRunOutDialog by remember { mutableStateOf(false) }
    var isNoBallRunOut by remember { mutableStateOf(false) }
    
    // Fielding contribution tracking
    var showFielderSelectionDialog by remember { mutableStateOf(false) }
    var pendingWicketType by remember { mutableStateOf<WicketType?>(null) }
    var pendingRunOutInput by remember { mutableStateOf<RunOutInput?>(null) }
    var pendingWideExtraType by remember { mutableStateOf<ExtraType?>(null) }
    var pendingWideRuns by remember { mutableStateOf(0) }
    var pickerOtherEndName by remember { mutableStateOf<String?>(null) }
    var pendingSwapAfterBatsmanPick by remember { mutableStateOf(false) }
    var pendingBowlerDialogAfterBatsmanPick by remember { mutableStateOf(false) }

    fun addDelivery(outcome: String, highlight: Boolean = false, runs: Int = 0) {
        // ball number shown 1..6 based on ballsInOver AFTER increment (so compute from current)
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
        // Only limit to 2 balls if unlimited undo is disabled
        if (!com.oreki.stumpd.utils.FeatureFlags.isUnlimitedUndoEnabled(context)) {
        if (deliveryHistory.size > 2) deliveryHistory.removeAt(0)
        }
    }

    fun removeLastDeliveryIfAny() {
        if (allDeliveries.isNotEmpty()) {
            val last = allDeliveries.last()
            // Only remove if it is from the current over; prevents crossing to previous over
            if (last.over == currentOver + 1) {
                allDeliveries.removeAt(allDeliveries.lastIndex)
            }
        }
    }
    
    // Auto-save match state after each scoring action
    fun autoSaveMatch() {
        try {
            // Determine which team bats first based on toss
            val team1BatsFirst = if (tossWinner != null && tossChoice != null) {
                val team1Won = tossWinner.equals(team1Name, ignoreCase = true)
                val battingChosen = tossChoice.contains("Batting", ignoreCase = true)
                (team1Won && battingChosen) || (!team1Won && !battingChosen)
            } else {
                true // default: team1 bats first
            }
            
            // Figure out which team is currently batting
            // In innings 1: if team1 bats first, battingTeamPlayers = team1
            // In innings 2: teams swap
            val battingIsTeam1 = if (currentInnings == 1) team1BatsFirst else !team1BatsFirst
            
            // Assign current players to team1Players and team2Players based on who's batting
            val currentTeam1Players = if (battingIsTeam1) battingTeamPlayers else bowlingTeamPlayers
            val currentTeam2Players = if (battingIsTeam1) bowlingTeamPlayers else battingTeamPlayers
            
            val matchInProgress = createMatchInProgress(
                matchId = matchId,
                team1Name = team1Name,
                team2Name = team2Name,
                jokerName = jokerName,
                team1PlayerIds = team1PlayerIds.toList(),
                team2PlayerIds = team2PlayerIds.toList(),
                team1PlayerNames = team1PlayerNames.toList(),
                team2PlayerNames = team2PlayerNames.toList(),
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
                wides = 0, // Extras are tracked in totalExtras
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
            scope.launch {
                android.util.Log.d("ScoringActivity", "=== Starting auto-save ===")
                
                // Save to Room database (local persistence)
                inProgressManager.saveMatch(matchInProgress)
                android.util.Log.d("ScoringActivity", "Saved to Room DB")
                
                // Sync to Firestore (for live spectators)
                try {
                    val authHelper = com.oreki.stumpd.data.sync.firebase.EnhancedFirebaseAuthHelper(context)
                    val userId = authHelper.currentUserId
                    android.util.Log.d("ScoringActivity", "Firestore sync - userId: $userId")
                    
                    if (userId != null) {
                        // Get the entity we just saved
                        val entity = com.oreki.stumpd.data.local.db.StumpdDb.get(context)
                            .inProgressMatchDao()
                            .getLatest()
                        
                        android.util.Log.d("ScoringActivity", "Entity retrieved: matchId=${entity?.matchId}, overs=${entity?.currentOver}.${entity?.ballsInOver}, wickets=${entity?.totalWickets}")
                        
                        if (entity != null) {
                            val firestoreDao = com.oreki.stumpd.data.sync.firebase.FirestoreInProgressMatchDao()
                            firestoreDao.uploadInProgressMatch(userId, entity)
                            android.util.Log.d("ScoringActivity", "✅ Live match synced to Firestore at /users/$userId/in_progress_matches/${entity.matchId}")
                        } else {
                            android.util.Log.w("ScoringActivity", "❌ Entity is null, cannot sync")
                        }
                    } else {
                        android.util.Log.w("ScoringActivity", "❌ No userId, cannot sync to Firestore")
                    }
                } catch (e: Exception) {
                    // Don't crash on sync failures - match still saved locally
                    android.util.Log.e("ScoringActivity", "❌ Failed to sync match to Firestore", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ScoringActivity", "Failed to auto-save match", e)
        }
    }

    fun undoLastDelivery() {
        if (deliveryHistory.isEmpty()) {
            Toast.makeText(context, "Nothing to undo", Toast.LENGTH_SHORT).show()
            return
        }
        val peek = deliveryHistory.last()
        val atStartOfSecond = (currentInnings == 2 && currentOver == 0 && ballsInOver == 0)
        val wouldGoBeforeStartOfSecond = (currentInnings == 2 && peek.currentOver == 0 && peek.ballsInOver == 0)
        if (atStartOfSecond || wouldGoBeforeStartOfSecond) {
            Toast.makeText(context, "Cannot undo beyond 0.0 overs in 2nd innings", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(context, "Last delivery undone", Toast.LENGTH_SHORT).show()
    }

    fun recordCurrentBowlerIfAny() {
        val idx = bowlerIndex
        if (idx != null) {
            // Check if this was a maiden over (only if 6 legal balls were bowled)
            if (runsConcededInCurrentOver == 0 && ballsInOver == 6) {
                bowlingTeamPlayers = bowlingTeamPlayers.mapIndexed { i, player ->
                    if (i == idx) player.copy(maidenOvers = player.maidenOvers + 1)
                    else player
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
        
        // Reset runs conceded counter for next over
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

    // Partnership tracking helpers
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
                // Add ball to striker's count
                if (striker!!.name == currentPartnershipBatsman1Name) {
                    currentPartnershipBatsman1Balls++
                } else if (striker!!.name == currentPartnershipBatsman2Name) {
                    currentPartnershipBatsman2Balls++
                }
            }
            currentPartnershipRuns += runs

            // Add runs to the striker's partnership contribution
            if (striker!!.name == currentPartnershipBatsman1Name) {
                currentPartnershipBatsman1Runs += runs
            } else if (striker!!.name == currentPartnershipBatsman2Name) {
                currentPartnershipBatsman2Runs += runs
            }
        }
    }

    fun endPartnershipAndRecordWicket(outPlayer: Player, isRunOut: Boolean = false) {
        // Save completed partnership
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

        // Record fall of wicket
        val teamScore = calculatedTotalRuns
        // Add 1 to ballsInOver to account for the current ball (not yet incremented)
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

        // Reset partnership counters for next partnership
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
        // Reset for next innings
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
        if (si == null || nsi == null) return  // only swap when both ends are occupied
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

    // At the top level - replace existing calculation
    val jokerAvailableForBatting = jokerPlayer != null &&
            !battingTeamPlayers.any { it.isJoker } &&
            !jokerOutInCurrentInnings

    val availableBatsmen = battingTeamPlayers.count { !it.isOut } +
            if (jokerAvailableForBatting) 1 else 0

    val showSingleSideLayout = matchSettings.allowSingleSideBatting && availableBatsmen == 1

    // Resume match from saved state if resuming
    LaunchedEffect(isResuming) {
        if (isResuming && !resumedMatchLoaded) {
            val savedMatch = inProgressManager.loadMatch()
            if (savedMatch != null && savedMatch.matchId == matchId) {
                try {
                    // Restore state
                    currentInnings = savedMatch.currentInnings
                    currentOver = savedMatch.currentOver
                    ballsInOver = savedMatch.ballsInOver
                    totalWickets = savedMatch.totalWickets
                    team1Players = savedMatch.team1PlayersJson.toPlayerList(gson).toMutableList()
                    team2Players = savedMatch.team2PlayersJson.toPlayerList(gson).toMutableList()
                    
                    // Determine which team bats first based on toss
                    val team1BatsFirst = if (tossWinner != null && tossChoice != null) {
                        val team1Won = tossWinner.equals(team1Name, ignoreCase = true)
                        val battingChosen = tossChoice.contains("Batting", ignoreCase = true)
                        (team1Won && battingChosen) || (!team1Won && !battingChosen)
                    } else {
                        true // default: team1 bats first
                    }
                    
                    // Assign batting/bowling teams based on current innings
                    if (savedMatch.currentInnings == 1) {
                        battingTeamPlayers = if (team1BatsFirst) team1Players else team2Players
                        bowlingTeamPlayers = if (team1BatsFirst) team2Players else team1Players
                        battingTeamName = if (team1BatsFirst) team1Name else team2Name
                        bowlingTeamName = if (team1BatsFirst) team2Name else team1Name
                    } else {
                        // Innings 2: swap teams
                        battingTeamPlayers = if (team1BatsFirst) team2Players else team1Players
                        bowlingTeamPlayers = if (team1BatsFirst) team1Players else team2Players
                        battingTeamName = if (team1BatsFirst) team2Name else team1Name
                        bowlingTeamName = if (team1BatsFirst) team1Name else team2Name
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
                    
                    // Restore powerplay tracking
                    powerplayRunsInnings1 = savedMatch.powerplayRunsInnings1
                    powerplayRunsInnings2 = savedMatch.powerplayRunsInnings2
                    powerplayDoublingDoneInnings1 = savedMatch.powerplayDoublingDoneInnings1
                    powerplayDoublingDoneInnings2 = savedMatch.powerplayDoublingDoneInnings2
                    
                    // Restore completed players lists
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
                    
                    // Restore first innings stats if available
                    savedMatch.firstInningsBattingPlayersJson?.let {
                        firstInningsBattingPlayersList = it.toPlayerList(gson)
                    }
                    savedMatch.firstInningsBowlingPlayersJson?.let {
                        firstInningsBowlingPlayersList = it.toPlayerList(gson)
                    }
                    
                    // Restore deliveries (ball-by-ball data)
                    savedMatch.allDeliveriesJson?.let { json ->
                        try {
                            val deliveries = gson.fromJson(json, Array<DeliveryUI>::class.java).toList()
                            allDeliveries.clear()
                            allDeliveries.addAll(deliveries)
                        } catch (e: Exception) {
                            android.util.Log.e("ScoringActivity", "Failed to restore deliveries", e)
                        }
                    }
                    
                    resumedMatchLoaded = true
                    Toast.makeText(context, "Match resumed from Over ${currentOver}.${ballsInOver}", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    android.util.Log.e("ScoringActivity", "Failed to resume match", e)
                    Toast.makeText(context, "Failed to resume match. Starting fresh.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Auto-save match state whenever key values change
    LaunchedEffect(currentOver, ballsInOver, totalWickets, calculatedTotalRuns, currentInnings) {
        if (currentOver > 0 || ballsInOver > 0) { // Don't save initial state
            autoSaveMatch()
        }
    }
    
    // Handle powerplay ending - double runs if enabled
    LaunchedEffect(currentOver, currentInnings) {
        if (matchSettings.powerplayOvers > 0 && matchSettings.doubleRunsInPowerplay) {
            // Check if powerplay just ended (currentOver reached powerplayOvers)
            if (currentOver == matchSettings.powerplayOvers) {
                if (currentInnings == 1 && !powerplayDoublingDoneInnings1) {
                    val runsToDouble = calculatedTotalRuns - powerplayRunsInnings1
                    if (runsToDouble > 0) {
                        // Double the runs scored during powerplay by adding them to extras
                        totalExtras += runsToDouble
                        powerplayDoublingDoneInnings1 = true
                        Toast.makeText(
                            context,
                            "🚀 Powerplay ended! ${runsToDouble} runs doubled! New total: ${calculatedTotalRuns}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else if (currentInnings == 2 && !powerplayDoublingDoneInnings2) {
                    val runsToDouble = calculatedTotalRuns - powerplayRunsInnings2
                    if (runsToDouble > 0) {
                        // Double the runs scored during powerplay by adding them to extras
                        totalExtras += runsToDouble
                        powerplayDoublingDoneInnings2 = true
                        Toast.makeText(
                            context,
                            "🚀 Powerplay ended! ${runsToDouble} runs doubled! New total: ${calculatedTotalRuns}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    val isInningsComplete = currentOver >= matchSettings.totalOvers
            || (currentInnings == 2 && calculatedTotalRuns > firstInningsRuns)
            || if (matchSettings.allowSingleSideBatting) (availableBatsmen == 0) else (totalWickets >= battingTeamPlayers.size - 1)

    LaunchedEffect(isInningsComplete) {
        if (isInningsComplete) {
            // Close any open dialogs immediately to prevent them from flashing
            showBatsmanDialog = false
            showBowlerDialog = false
            showWicketDialog = false
            showExtrasDialog = false
            showQuickWideDialog = false
            showQuickNoBallDialog = false
            
            if (currentInnings == 1) {
                firstInningsRuns = calculatedTotalRuns
                firstInningsWickets = totalWickets
                firstInningsOvers = currentOver
                firstInningsBalls = ballsInOver
                // Merge batting players: prioritize completedBattersInnings1 for players with dismissal info
                // Include striker and non-striker even if they have 0 balls (they came to bat)
                val activeBatters = battingTeamPlayers.filter { player ->
                    player.ballsFaced > 0 || player.runs > 0 || 
                    strikerIndex?.let { battingTeamPlayers[it].name == player.name } == true ||
                    nonStrikerIndex?.let { battingTeamPlayers[it].name == player.name } == true
                }
                val completedNames = completedBattersInnings1.map { it.name }.toSet()
                firstInningsBattingPlayersList =
                    (completedBattersInnings1 + activeBatters.filterNot { it.name in completedNames })
                        .distinctBy { it.name }
                firstInningsBowlingPlayersList =
                    (bowlingTeamPlayers.filter { it.ballsBowled > 0 || it.wickets > 0 || it.runsConceded > 0 } +
                            completedBowlersInnings1)
                        .distinctBy { it.name }
                previousBowlerName = null
                // Save partnerships and fall of wickets for first innings
                saveInningsPartnershipsAndWickets()
                showInningsBreakDialog = true
            } else {
                // Merge batting players: prioritize completedBattersInnings2 for players with dismissal info
                // Include striker and non-striker even if they have 0 balls (they came to bat)
                val activeBatters = battingTeamPlayers.filter { player ->
                    player.ballsFaced > 0 || player.runs > 0 || 
                    strikerIndex?.let { battingTeamPlayers[it].name == player.name } == true ||
                    nonStrikerIndex?.let { battingTeamPlayers[it].name == player.name } == true
                }
                val completedNames = completedBattersInnings2.map { it.name }.toSet()
                val secondInningsBattingPlayersList =
                    (completedBattersInnings2 + activeBatters.filterNot { it.name in completedNames })
                        .distinctBy { it.name }
                secondInningsBattingPlayers = secondInningsBattingPlayersList
                val secondInningsBowlingPlayersList =
                    (bowlingTeamPlayers.filter { it.ballsBowled > 0 || it.wickets > 0 || it.runsConceded > 0 } +
                            completedBowlersInnings2)
                        .distinctBy { it.name }
                secondInningsBattingPlayers = secondInningsBattingPlayersList
                secondInningsBowlingPlayers = secondInningsBowlingPlayersList
                showMatchCompleteDialog = true
            }
        }
    }

    val tabs = listOf("Live", "Scorecard", "Overs", "Squad")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()
    
    // Sync pager with tab selection
    LaunchedEffect(pagerState.currentPage) {
        // When pager changes (swipe), update nothing - just observe
    }

    // Responsive tab text size based on screen width
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val tabFontSize = when {
        screenWidthDp >= 900 -> 11.sp  // Extra large screens
        screenWidthDp >= 600 -> 12.sp  // Large screens (tablets, S24 Ultra landscape)
        else -> 14.sp                  // Normal screens
    }
    
    Scaffold(
        topBar = {
            StumpdTopBar(
                title = "Live Scoring",
                subtitle = "$battingTeamName vs $bowlingTeamName • ${matchSettings.totalOvers} overs",
                onBack = { showExitDialog = true },
                actions = {
                    IconButton(
                        onClick = {
                            val intent = Intent(context, MatchHistoryActivity::class.java)
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = "Match History"
                        )
                    }
                }
            )
        }
    ) { padding ->
    Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
    ) {
        // Tab Row
        TabRow(
            selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { 
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = { 
                        Text(
                            text = title,
                            fontSize = tabFontSize,
                                fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        ) 
                    }
                )
            }
        }
        
        // Tab Content with Swipe Support
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
            0 -> LiveScoreTab(
                modifier = Modifier.padding(16.dp),
            battingTeamName = battingTeamName,
            currentInnings = currentInnings,
            matchSettings = matchSettings,
            calculatedTotalRuns = calculatedTotalRuns,
            totalWickets = totalWickets,
            currentOver = currentOver,
            ballsInOver = ballsInOver,
            totalExtras = totalExtras,
            battingTeamPlayers = battingTeamPlayers,
                firstInningsRuns = firstInningsRuns,
            showSingleSideLayout = showSingleSideLayout,
            striker = striker,
            nonStriker = nonStriker,
            bowler = bowler,
            availableBatsmen = availableBatsmen,
                currentBowlerSpell = currentBowlerSpell,
                jokerPlayer = jokerPlayer,
                currentOverDeliveries = currentOverDeliveries,
                isInningsComplete = isInningsComplete,
                isPowerplayActive = isPowerplayActive,
                context = context,
            onSelectStriker = {
                selectingBatsman = 1
                showBatsmanDialog = true
            },
            onSelectNonStriker = {
                selectingBatsman = 2
                showBatsmanDialog = true
            },
            onSelectBowler = { showBowlerDialog = true },
            onSwapStrike = {
                swapStrike()
                Toast.makeText(context, "Strike swapped! ${nonStriker?.name} now on strike", Toast.LENGTH_SHORT).show()
            },
            onRetire = { showRetirementDialog = true },
            onScoreRuns = { runs ->
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
                            // Update partnership
                            updatePartnershipOnRuns(runs, isLegalDelivery = true)
                incJokerBallIfBowledThisDelivery()

                addDelivery(
                    outcome = runs.toString(),
                    highlight = (runs == 4 || runs == 6),
                    runs = runs
                )
                if (runs % 2 == 1 && !showSingleSideLayout) {
                    swapStrike()
                }
                ballsInOver += 1
                if (ballsInOver == 6) {
                    currentOver += 1
                    ballsInOver = 0
                    recordCurrentBowlerIfAny()
                    previousBowlerName = bowler?.name
                    bowlerIndex = null
                    currentBowlerSpell = 0
                    midOverReplacementDueToJoker.value = false
                    if (!showSingleSideLayout) {
                        swapStrike()
                    }
                    showBowlerDialog = true
                    Toast.makeText(context, "Over complete! Select new bowler", Toast.LENGTH_LONG).show()
                }
            },
            onShowExtras = { showExtrasDialog = true },
            onShowWicket = { showWicketDialog = true },
            onUndo = { undoLastDelivery() },
                        onWide = {
                            // Quick wide: just adds base wide penalty + 0 runs (no dialog)
                            pushSnapshot()
                            val totalRuns = matchSettings.wideRuns
                            updateBowlerStats { player ->
                                player.copy(runsConceded = player.runsConceded + totalRuns)
                            }
                            runsConcededInCurrentOver += totalRuns
                            totalExtras += totalRuns
                            addDelivery("Wd", runs = totalRuns)
                            val totalAfter = battingTeamPlayers.sumOf { it.runs } + totalExtras
                            Toast.makeText(context, "Wide! +$totalRuns runs. Total: $totalAfter", Toast.LENGTH_SHORT).show()
                        },
                        unlimitedUndoEnabled = unlimitedUndoEnabled,
                        onToggleUnlimitedUndo = { newValue ->
                            pendingUnlimitedUndoValue = newValue
                            showUnlimitedUndoDialog = true
                        }
        )
            1 -> ScorecardTab(
                modifier = Modifier.padding(16.dp),
                currentInnings = currentInnings,
                battingTeamName = battingTeamName,
                bowlingTeamName = bowlingTeamName,
                battingTeamPlayers = battingTeamPlayers,
                bowlingTeamPlayers = bowlingTeamPlayers,
                completedBattersInnings1 = completedBattersInnings1,
                completedBattersInnings2 = completedBattersInnings2,
                completedBowlersInnings1 = completedBowlersInnings1,
                completedBowlersInnings2 = completedBowlersInnings2,
                firstInningsBattingPlayersList = firstInningsBattingPlayersList,
                        firstInningsBowlingPlayersList = firstInningsBowlingPlayersList,
                        currentPartnerships = partnerships,
                        firstInningsPartnerships = firstInningsPartnerships,
                        currentFallOfWickets = fallOfWickets,
                        firstInningsFallOfWickets = firstInningsFallOfWickets,
                        striker = striker,
                        nonStriker = nonStriker,
                        currentPartnershipRuns = currentPartnershipRuns,
                        currentPartnershipBalls = currentPartnershipBalls,
                        currentPartnershipBatsman1Runs = currentPartnershipBatsman1Runs,
                        currentPartnershipBatsman2Runs = currentPartnershipBatsman2Runs,
                        currentPartnershipBatsman1Balls = currentPartnershipBatsman1Balls,
                        currentPartnershipBatsman2Balls = currentPartnershipBatsman2Balls,
                        currentPartnershipBatsman1Name = currentPartnershipBatsman1Name,
                        currentPartnershipBatsman2Name = currentPartnershipBatsman2Name
            )
            2 -> OversTab(
                modifier = Modifier.padding(16.dp),
                allDeliveries = allDeliveries
            )
            3 -> SquadTab(
                modifier = Modifier.padding(16.dp),
                team1Name = team1Name,
                team2Name = team2Name,
                team1Players = team1Players,
                team2Players = team2Players,
                jokerPlayer = jokerPlayer
            )
                }
            }
        }
    }

    // Dialogs
    if (showLiveScorecardDialog) {
        LiveScorecardDialog(
            currentInnings = currentInnings,
            battingTeamName = battingTeamName,
            battingTeamPlayers = battingTeamPlayers,
            bowlingTeamPlayers = bowlingTeamPlayers,
            firstInningsBattingPlayers = firstInningsBattingPlayersList,
            firstInningsBowlingPlayers = firstInningsBowlingPlayersList,
            firstInningsRuns = firstInningsRuns,
            firstInningsWickets = firstInningsWickets,
            currentRuns = calculatedTotalRuns,
            currentWickets = totalWickets,
            currentOvers = currentOver,
            currentBalls = ballsInOver,
            totalOvers = matchSettings.totalOvers,
            jokerPlayerName = jokerName,
            striker = striker,
            nonStriker = nonStriker,
            bowler = bowler,
            onDismiss = { showLiveScorecardDialog = false },
        )
    }

    if (showExtrasDialog) {
        ExtrasDialog(
            matchSettings = matchSettings,
            onExtraSelected = { extraType, totalRuns ->
                pushSnapshot()
                when (extraType) {
                    ExtraType.OFF_SIDE_WIDE, ExtraType.LEG_SIDE_WIDE -> {
                        updateBowlerStats { player ->
                            player.copy(runsConceded = player.runsConceded + totalRuns)
                        }
                        runsConcededInCurrentOver += totalRuns
                        totalExtras += totalRuns
                        val baseWideRuns = matchSettings.wideRuns
                        val additionalRuns = totalRuns - baseWideRuns
                        if (additionalRuns % 2 == 1 && !showSingleSideLayout) {
                            swapStrike()
                        }
                        addDelivery("Wd+${totalRuns}", runs = totalRuns)
                        Toast.makeText(context, "${extraType.displayName}! +$totalRuns runs", Toast.LENGTH_SHORT).show()
                    }
                    ExtraType.NO_BALL -> {
                        // DO NOT increment ballsInOver or bowler.ballsBowled on No ball
                        val baseNoBallRuns = matchSettings.noballRuns
                        val additionalRuns = totalRuns - baseNoBallRuns
                        updateBowlerStats { player ->
                            player.copy(runsConceded = player.runsConceded + totalRuns)
                        }
                        runsConcededInCurrentOver += totalRuns
                        totalExtras += baseNoBallRuns

                        // Additional runs go to STRIKER
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
                            // Update partnership with additional runs from no-ball
                            updatePartnershipOnRuns(additionalRuns, isLegalDelivery = true)
                        } else {
                            // Just increment balls faced even with 0 additional runs
                            updateStrikerAndTotals { player ->
                                player.copy(ballsFaced = player.ballsFaced + 1)
                            }
                        }

                        val sub = NoBallOutcomeHolders.noBallSubOutcome.value
                        val ro = NoBallOutcomeHolders.noBallRunOutInput.value
                        val bo = NoBallOutcomeHolders.noBallBoundaryOutInput.value

                        when (sub) {
                            NoBallSubOutcome.BOUNDARY_OUT -> {
                                // Custom gully rule: BoundaryOut behaves like a regular wicket (no run increment), allowed on No ball.
                                // Identify out batter: given name or default striker
                                val outName = bo?.outBatterName?.trim()?.takeIf { it.isNotEmpty() } ?: striker?.name ?: "Batsman"
                                val outIndex = battingTeamPlayers.indexOfFirst { it.name.equals(outName, ignoreCase = true) }
                                val validIndex = if (outIndex != -1) outIndex else strikerIndex

                                if (validIndex == null) {
                                    Toast.makeText(context, "Boundary out: could not resolve batter. Aborting wicket.", Toast.LENGTH_LONG).show()
                                    addDelivery("Nb+${totalRuns}", runs = totalRuns)
                                } else {
                                    pushSnapshot()
                                    totalWickets += 1

                                    // Mark out but DO NOT add any runs; do NOT increment balls/over or bowler balls
                                    val newList = battingTeamPlayers.toMutableList()
                                    val dismissedPlayer = newList[validIndex]
                                    newList[validIndex] = dismissedPlayer.copy(
                                        isOut = true,
                                        ballsFaced = dismissedPlayer.ballsFaced + 1,
                                        dismissalType = WicketType.BOUNDARY_OUT,
                                        bowlerName = bowler?.name
                                    )
                                    val outSnapshot = newList[validIndex].copy()
                                    battingTeamPlayers = newList

                                    // Record completed batter snapshot
                                    if (currentInnings == 1) {
                                        if (completedBattersInnings1.none { it.name.equals(outSnapshot.name, true) }) {
                                            completedBattersInnings1 = (completedBattersInnings1 + outSnapshot).toMutableList()
                                        } else {
                                            completedBattersInnings1 = completedBattersInnings1.map {
                                                if (it.name.equals(outSnapshot.name, true)) outSnapshot else it
                                            }.toMutableList()
                                        }
                                    } else {
                                        if (completedBattersInnings2.none { it.name.equals(outSnapshot.name, true) }) {
                                            completedBattersInnings2 = (completedBattersInnings2 + outSnapshot).toMutableList()
                                        } else {
                                            completedBattersInnings2 = completedBattersInnings2.map {
                                                if (it.name.equals(outSnapshot.name, true)) outSnapshot else it
                                            }.toMutableList()
                                        }
                                    }

                                    // Clear the correct end slot; do not advance over or ballsInOver
                                    if (strikerIndex == validIndex) {
                                        strikerIndex = null
                                        selectingBatsman = 1
                                        pickerOtherEndName = nonStriker?.name
                                    } else if (nonStrikerIndex == validIndex) {
                                        nonStrikerIndex = null
                                        selectingBatsman = 2
                                        pickerOtherEndName = striker?.name
                                    } else {
                                        // Didn't match the two active ends; default to striker replacement
                                        strikerIndex = null
                                        selectingBatsman = 1
                                        pickerOtherEndName = nonStriker?.name
                                    }
                                    showBatsmanDialog = true

                                    // Log and toast
                                    addDelivery("Nb+${totalRuns}; BO(${outSnapshot.name})", highlight = true, runs = totalRuns)
                                    val totalAfter = battingTeamPlayers.sumOf { it.runs } + totalExtras
                                    Toast.makeText(context, "No ball + Boundary out! Total: $totalAfter", Toast.LENGTH_SHORT).show()
                                }
                            }
                            NoBallSubOutcome.RUN_OUT -> {
                                isNoBallRunOut = true
                                val input = ro
                                if (input == null) {
                                    addDelivery("Nb+${totalRuns}", runs = totalRuns)
                                    Toast.makeText(context, "No ball! +$totalRuns runs", Toast.LENGTH_SHORT).show()
                                } else {
                                    pushSnapshot()

                                    // Resolve outIndex robustly
                                    val name = input.whoOut.trim()
                                    val byName = battingTeamPlayers.indexOfFirst { it.name.equals(name, ignoreCase = true) }
                                    val byStriker = if (striker?.name.equals(name, true)) strikerIndex else null
                                    val byNonStriker = if (nonStriker?.name.equals(name, true)) nonStrikerIndex else null
                                    val resolvedIndex = when {
                                        byName != -1 -> byName
                                        byStriker != null -> byStriker
                                        byNonStriker != null -> byNonStriker
                                        else -> strikerIndex // last-resort: treat as striker
                                    }

                                    if (resolvedIndex == null) {
                                        Toast.makeText(context, "Run out on No ball: player \"$name\" not found", Toast.LENGTH_LONG).show()
                                        addDelivery("Nb+${totalRuns}", runs = totalRuns)
                                    } else {
                                        // Mark out; do NOT change ballsInOver or bowler.ballsBowled on No ball
                                        val updated = battingTeamPlayers.toMutableList()
                                        updated[resolvedIndex] = updated[resolvedIndex].copy(
                                            isOut = true,
                                            dismissalType = WicketType.RUN_OUT
                                            // Note: fielderName not available in this flow (no fielder selection dialog for no-ball run-outs)
                                        )
                                        val outSnapshot = updated[resolvedIndex].copy()
                                        battingTeamPlayers = updated
                                        totalWickets += 1

                                        // Ledger
                                        if (currentInnings == 1) {
                                            if (completedBattersInnings1.none { it.name.equals(outSnapshot.name, true) }) {
                                                completedBattersInnings1 = (completedBattersInnings1 + outSnapshot).toMutableList()
                                            } else {
                                                completedBattersInnings1 = completedBattersInnings1.map {
                                                    if (it.name.equals(outSnapshot.name, true)) outSnapshot else it
                                                }.toMutableList()
                                            }
                                        } else {
                                            if (completedBattersInnings2.none { it.name.equals(outSnapshot.name, true) }) {
                                                completedBattersInnings2 = (completedBattersInnings2 + outSnapshot).toMutableList()
                                            } else {
                                                completedBattersInnings2 = completedBattersInnings2.map {
                                                    if (it.name.equals(outSnapshot.name, true)) outSnapshot else it
                                                }.toMutableList()
                                            }
                                        }

                                        // Only clear the end where wicket occurred; keep the other end intact.
                                        if (input.end == RunOutEnd.STRIKER_END) {
                                            if (strikerIndex == resolvedIndex) {
                                                strikerIndex = null
                                            }
                                            // if mismatch (non-striker dismissed but scorer said S), still free striker by intent ONLY if indexes matched
                                            selectingBatsman = 1
                                            pickerOtherEndName = nonStriker?.name
                                        } else { // NON_STRIKER_END
                                            if (nonStrikerIndex == resolvedIndex) {
                                                nonStrikerIndex = null
                                            }
                                            selectingBatsman = 2
                                            pickerOtherEndName = striker?.name
                                        }
                                        showBatsmanDialog = true

                                        addDelivery("Nb+${totalRuns}; RO(${outSnapshot.name} @ ${if (input.end == RunOutEnd.STRIKER_END) "S" else "NS"})", highlight = true, runs = totalRuns)
                                        val totalAfter = battingTeamPlayers.sumOf { it.runs } + totalExtras
                                        Toast.makeText(context, "No ball + Run out! Total: $totalAfter", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            else -> {
                                // None: just Nb + runs; do not increment balls/over here either.
                                if (additionalRuns % 2 == 1 && !showSingleSideLayout) {
                                    swapStrike()
                                }
                                addDelivery("Nb+${totalRuns}", runs = totalRuns)
                                Toast.makeText(context, "No ball! +$totalRuns runs", Toast.LENGTH_SHORT).show()
                            }
                        }

                        // Clear holders after consumption
                        NoBallOutcomeHolders.noBallSubOutcome.value = NoBallSubOutcome.NONE
                        NoBallOutcomeHolders.noBallRunOutInput.value = null
                        NoBallOutcomeHolders.noBallBoundaryOutInput.value = null
                    }
                    ExtraType.BYE, ExtraType.LEG_BYE -> {
                        updateStrikerAndTotals { player ->
                            player.copy(ballsFaced = player.ballsFaced + 1)
                        }
                        updateBowlerStats { player ->
                            player.copy(ballsBowled = player.ballsBowled + 1)
                        }
                        incJokerBallIfBowledThisDelivery()
                        totalExtras += totalRuns
                        ballsInOver += 1
                        addDelivery(if (extraType == ExtraType.BYE) "B+$totalRuns" else "Lb+$totalRuns", runs = totalRuns)
                        if (ballsInOver == 6) {
                            currentOver += 1
                            ballsInOver = 0
                            recordCurrentBowlerIfAny()
                            previousBowlerName = bowler?.name
                            bowlerIndex = null
                            currentBowlerSpell = 0
                            midOverReplacementDueToJoker.value = false
                            if (!showSingleSideLayout) {
                                swapStrike()
                            }
                            showBowlerDialog = true
                            Toast.makeText(context, "Over complete! Select new bowler", Toast.LENGTH_LONG).show()
                        } else {
                            val baseByeRuns = if (extraType == ExtraType.BYE) matchSettings.byeRuns else matchSettings.legByeRuns
                            val additionalRuns = totalRuns - baseByeRuns
                            if (additionalRuns % 2 == 1 && !showSingleSideLayout) {
                                swapStrike()
                            }
                        }
                        Toast.makeText(context, "${extraType.displayName}! +$totalRuns runs", Toast.LENGTH_SHORT).show()
                    }
                }
                val totalAfter = battingTeamPlayers.sumOf { it.runs } + totalExtras
                Toast.makeText(context, "${extraType.displayName}: +$totalRuns. Total: $totalAfter", Toast.LENGTH_SHORT).show()
                showExtrasDialog = false
            },
            onDismiss = { showExtrasDialog = false },
            striker = striker,
            nonStriker = nonStriker,
            onWideWithStumping = { extraType, baseRuns ->
                // Wide ball with stumping
                showExtrasDialog = false
                
                // Set up for fielder selection (keeper)
                pendingWicketType = WicketType.STUMPED
                pendingWideExtraType = extraType
                pendingWideRuns = baseRuns
                showFielderSelectionDialog = true
            }
        )
    }
    
    // Quick Wide Dialog
    if (showQuickWideDialog) {
        QuickWideDialog(
            matchSettings = matchSettings,
            onWideConfirmed = { totalRuns ->
                pushSnapshot()
                updateBowlerStats { player ->
                    player.copy(runsConceded = player.runsConceded + totalRuns)
                }
                runsConcededInCurrentOver += totalRuns
                totalExtras += totalRuns
                val baseWideRuns = matchSettings.wideRuns
                val additionalRuns = totalRuns - baseWideRuns
                if (additionalRuns % 2 == 1 && !showSingleSideLayout) {
                    swapStrike()
                }
                addDelivery("Wd+${totalRuns}", runs = totalRuns)
                val totalAfter = battingTeamPlayers.sumOf { it.runs } + totalExtras
                Toast.makeText(context, "Wide! +$totalRuns runs. Total: $totalAfter", Toast.LENGTH_SHORT).show()
                showQuickWideDialog = false
            },
            onStumpingOnWide = { runs ->
                showQuickWideDialog = false
                pendingWicketType = WicketType.STUMPED
                pendingWideExtraType = ExtraType.LEG_SIDE_WIDE // Default, doesn't matter for display
                pendingWideRuns = runs
                showFielderSelectionDialog = true
            },
            onDismiss = { showQuickWideDialog = false }
        )
    }
    
    // Quick No-ball Dialog
    if (showQuickNoBallDialog) {
        QuickNoBallDialog(
            matchSettings = matchSettings,
            striker = striker,
            nonStriker = nonStriker,
            onNoBallConfirmed = { totalRuns ->
                pushSnapshot()
                val baseNoBallRuns = matchSettings.noballRuns
                val additionalRuns = totalRuns - baseNoBallRuns
                updateBowlerStats { player ->
                    player.copy(runsConceded = player.runsConceded + totalRuns)
                }
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
                    // Update partnership with additional runs from no-ball
                    updatePartnershipOnRuns(additionalRuns, isLegalDelivery = true)
                } else {
                    updateStrikerAndTotals { player ->
                        player.copy(ballsFaced = player.ballsFaced + 1)
                    }
                }

                if (additionalRuns % 2 == 1 && !showSingleSideLayout) {
                    swapStrike()
                }
                addDelivery("Nb+${totalRuns}", runs = totalRuns)
                val totalAfter = battingTeamPlayers.sumOf { it.runs } + totalExtras
                Toast.makeText(context, "No-ball! +$totalRuns runs. Total: $totalAfter", Toast.LENGTH_SHORT).show()
                showQuickNoBallDialog = false
            },
            onDismiss = { showQuickNoBallDialog = false }
        )
    }

    if (showBatsmanDialog && !isInningsComplete) {
        val availableBatsmenCount = battingTeamPlayers.count { !it.isOut || it.isRetired }
        val jokerAvailableForBattingInsideBatsmanDialog = jokerPlayer != null &&
                !battingTeamPlayers.any { it.isJoker } &&
                !jokerPlayer.isOut


        EnhancedPlayerSelectionDialog(
            title = when {
                selectingBatsman == 1 -> "Select Striker"
                else -> "Select Non-Striker"
            },
            players = battingTeamPlayers,
            // FIXED: Only show joker if wickets have fallen (not for opening)
            jokerPlayer = if (totalWickets == 0 || jokerOutInCurrentInnings) null else jokerPlayer,
            currentStrikerIndex = strikerIndex,
            currentNonStrikerIndex = nonStrikerIndex,
            allowSingleSide = matchSettings.allowSingleSideBatting,
            totalWickets = totalWickets,
            battingTeamPlayers = battingTeamPlayers,
            bowlingTeamPlayers = bowlingTeamPlayers,
            jokerOversThisInnings = jokerOversBowledThisInnings(),
            onPlayerSelected = { player ->
                if (selectingBatsman == 1) {
                    if (player.isJoker) {
                        // Remove joker from bowling team if they were bowling
                        val jokerBowlingIndex = bowlingTeamPlayers.indexOfFirst { it.isJoker }
                        if (jokerBowlingIndex != -1) {
                            // Reset bowler if joker was bowling
                            if (bowlerIndex == jokerBowlingIndex) {
                                recordCurrentBowlerIfAny()
                                bowlerIndex = null
                                currentBowlerSpell = 0
                                if (ballsInOver > 0) {
                                    midOverReplacementDueToJoker.value = true
                                    Toast.makeText(context, "🃏 Joker switched to bat. Select a new bowler to complete the over.", Toast.LENGTH_LONG).show()
                                    showBowlerDialog = true
                                }
                            }

                            val newBowlingList = bowlingTeamPlayers.toMutableList()
                            newBowlingList.removeAt(jokerBowlingIndex)
                            bowlingTeamPlayers = newBowlingList

                            // Update previous bowler index
                            if (previousBowlerName == jokerName) {
                                previousBowlerName = null
                            }
                        }

                        // Add joker to batting team
                        if (!battingTeamPlayers.any { it.isJoker }) {
                            val newList = battingTeamPlayers.toMutableList()
                            newList.add(jokerPlayer!!.copy())
                            battingTeamPlayers = newList
                            strikerIndex = battingTeamPlayers.size - 1
                        } else {
                            strikerIndex = battingTeamPlayers.indexOfFirst { it.isJoker }
                        }
                        jokerOutInCurrentInnings = false
                    } else {
                        val playerIndex = battingTeamPlayers.indexOfFirst { it.name.trim().equals(player.name.trim(), ignoreCase = true) }
                        strikerIndex = playerIndex
                        
                        // If player was retired, un-retire them
                        if (playerIndex >= 0 && battingTeamPlayers[playerIndex].isRetired) {
                            val unretiredPlayer = battingTeamPlayers[playerIndex].copy(isRetired = false)
                            battingTeamPlayers = battingTeamPlayers.toMutableList().apply {
                                this[playerIndex] = unretiredPlayer
                            }
                            Toast.makeText(context, "${unretiredPlayer.name} returns to bat", Toast.LENGTH_SHORT).show()
                        }
                    }

                    if (!matchSettings.allowSingleSideBatting && (availableBatsmenCount + if (jokerAvailableForBattingInsideBatsmanDialog) 1 else 0) > 1) {
                        selectingBatsman = 2
                        // Keep dialog open for second selection
                    } else {
                        showBatsmanDialog = false

                        if (pendingSwapAfterBatsmanPick) {
                            if (!showSingleSideLayout) swapStrike()
                            pendingSwapAfterBatsmanPick = false
                        }
                        if (pendingBowlerDialogAfterBatsmanPick) {
                            showBowlerDialog = true
                            pendingBowlerDialogAfterBatsmanPick = false
                        }

                    }
                } else {
                    // Second batsman selection - same logic for joker
                    if (player.isJoker) {
                        // Remove joker from bowling team if they were bowling
                        val jokerBowlingIndex = bowlingTeamPlayers.indexOfFirst { it.isJoker }
                        if (jokerBowlingIndex != -1) {
                            // Reset bowler if joker was bowling
                            if (bowlerIndex == jokerBowlingIndex) {
                                recordCurrentBowlerIfAny()
                                bowlerIndex = null
                                currentBowlerSpell = 0
                                if (ballsInOver > 0) {
                                    midOverReplacementDueToJoker.value = true
                                    Toast.makeText(context, "🃏 Joker switched to bat. Select a new bowler to complete the over.", Toast.LENGTH_LONG).show()
                                    showBowlerDialog = true
                                }
                            }
                            val newBowlingList = bowlingTeamPlayers.toMutableList()
                            newBowlingList.removeAt(jokerBowlingIndex)
                            bowlingTeamPlayers = newBowlingList

                            // Update previous bowler index
                            if (previousBowlerName == jokerName) {
                                previousBowlerName = null
                            }
                        }

                        if (!battingTeamPlayers.any { it.isJoker }) {
                            val newList = battingTeamPlayers.toMutableList()
                            newList.add(jokerPlayer!!.copy())
                            battingTeamPlayers = newList
                            nonStrikerIndex = battingTeamPlayers.size - 1
                        } else {
                            nonStrikerIndex = battingTeamPlayers.indexOfFirst { it.isJoker }
                        }
                    } else {
                        val playerIndex = battingTeamPlayers.indexOfFirst { it.name.trim().equals(player.name.trim(), ignoreCase = true) }
                        nonStrikerIndex = playerIndex
                        
                        // If player was retired, un-retire them
                        if (playerIndex >= 0 && battingTeamPlayers[playerIndex].isRetired) {
                            val unretiredPlayer = battingTeamPlayers[playerIndex].copy(isRetired = false)
                            battingTeamPlayers = battingTeamPlayers.toMutableList().apply {
                                this[playerIndex] = unretiredPlayer
                            }
                            Toast.makeText(context, "${unretiredPlayer.name} returns to bat", Toast.LENGTH_SHORT).show()
                        }
                    }
                    jokerOutInCurrentInnings = false
                    showBatsmanDialog = false

                    if (pendingSwapAfterBatsmanPick) {
                        if (!showSingleSideLayout) swapStrike()
                        pendingSwapAfterBatsmanPick = false
                    }
                    if (pendingBowlerDialogAfterBatsmanPick) {
                        showBowlerDialog = true
                        pendingBowlerDialogAfterBatsmanPick = false
                    }

                }
            },
            jokerOutInCurrentInnings = jokerOutInCurrentInnings,
            onDismiss = { showBatsmanDialog = false },
            matchSettings = matchSettings,
            otherEndName = pickerOtherEndName
        )
    }

    if (showBowlerDialog && !isInningsComplete) {
        val bowlerPool = if (ballsInOver == 0) {
            val prev = previousBowlerName?.trim()
            bowlingTeamPlayers.filter { !it.name.trim().equals(prev, ignoreCase = true) }
        } else {
            bowlingTeamPlayers
        }
        // Allow cap override only at the start of an over if nobody can legally bowl 6 balls
        val overrideToCompleteOverAllowed = (ballsInOver == 0) && run {
            val prev = previousBowlerName?.trim()
            val startOverPool = if (prev != null) {
                bowlingTeamPlayers.filter { !it.name.trim().equals(prev, ignoreCase = true) }
            } else {
                bowlingTeamPlayers
            }

            val anyoneHasSix = startOverPool.any { p ->
                val isJoker = p.isJoker
                val ballsSoFar = if (isJoker) {
                    // Use the innings-ledger for Joker
                    jokerBallsBowledThisInningsRaw()
                } else {
                    p.ballsBowled
                }
                val capOvers = if (isJoker) matchSettings.jokerMaxOvers else matchSettings.maxOversPerBowler
                val capBalls = capOvers * 6
                val remainingBalls = (capBalls - ballsSoFar).coerceAtLeast(0)
                remainingBalls >= 6
            }
            !anyoneHasSix
        }
        EnhancedPlayerSelectionDialog(
            title = when {
                ballsInOver == 0 && previousBowlerName != null -> "Select New Bowler (Same bowler cannot bowl consecutive overs)"
                bowlerIndex == null && ballsInOver > 0 -> "Select Bowler to Complete Over"
                else -> "Select Bowler"
            },
            players = bowlerPool,
            // FIXED: Only show joker if they're not currently batting
            jokerPlayer = if (battingTeamPlayers.any { it.isJoker } && (!jokerOutInCurrentInnings)) null else jokerPlayer,
            totalWickets = totalWickets,
            battingTeamPlayers = battingTeamPlayers,
            bowlingTeamPlayers = bowlingTeamPlayers,
            jokerOversThisInnings = jokerOversBowledThisInnings(),
            jokerOutInCurrentInnings = jokerOutInCurrentInnings,
            onPlayerSelected = { player ->
                // Normalize lookup for current stats
                fun norm(s: String): String = s.trim().replace(Regex("\\s+"), " ")
                val ballsSoFar = if (player.isJoker) {
                    // Always trust the innings ledger for Joker
                    jokerBallsBowledThisInningsRaw()
                } else {
                    val idxInBowling = bowlingTeamPlayers.indexOfFirst {
                        norm(it.name).equals(norm(player.name), ignoreCase = true)
                    }
                    bowlingTeamPlayers.getOrNull(idxInBowling)?.ballsBowled ?: 0
                }

                // Determine per-player cap and remaining legal balls
                val capOvers = if (player.isJoker) matchSettings.jokerMaxOvers else matchSettings.maxOversPerBowler
                val capBalls = capOvers * 6
                val remainingBalls = (capBalls - ballsSoFar).coerceAtLeast(0)

                // If selecting the same bowler mid-over, just close the dialog silently
                run {
                    val currentIdx = bowlerIndex
                    if (ballsInOver > 0 && currentIdx != null) {
                        val current = bowlingTeamPlayers.getOrNull(currentIdx)
                        if (current != null) {
                            fun norm(s: String): String = s.trim().replace(Regex("\\s+"), " ")
                            val same = (!player.isJoker && norm(current.name).equals(norm(player.name), ignoreCase = true)) ||
                                    (player.isJoker && current.isJoker)
                            if (same) {
                                showBowlerDialog = false
                                return@EnhancedPlayerSelectionDialog
                            }
                        }
                    }
                }

// 1) New-over requires 6 legal balls (with one-over override if nobody has 6)
                if (ballsInOver == 0 && remainingBalls < 6) {
                    val canOverrideThisOver =
                        overrideToCompleteOverAllowed && !player.isJoker
                    if (!canOverrideThisOver) {
                        val who = if (player.isJoker) "Joker" else player.name
                        Toast.makeText(
                            context,
                            "$who cannot start a new over (only $remainingBalls legal ball${if (remainingBalls != 1) "s" else ""} remaining; needs 6).",
                            Toast.LENGTH_LONG
                        ).show()
                        return@EnhancedPlayerSelectionDialog
                    } else {
                        Toast.makeText(
                            context,
                            "${player.name} will exceed max-over cap only to complete this over.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }


// 2) Mid-over replacement (only allowed after Joker left mid-over)
                if (ballsInOver > 0) {
                    if (!midOverReplacementDueToJoker.value) {
                        Toast.makeText(context, "Mid-over bowler change is only allowed when replacing the Joker.", Toast.LENGTH_LONG).show()
                        return@EnhancedPlayerSelectionDialog
                    }
                    val need = 6 - ballsInOver
                    if (remainingBalls < need) {
                        val who = if (player.isJoker) "Joker" else player.name
                        Toast.makeText(context, "$who cannot replace mid-over (needs $need balls, only $remainingBalls remaining).", Toast.LENGTH_LONG).show()
                        return@EnhancedPlayerSelectionDialog
                    }
                }

// Proceed with selection
                if (player.isJoker) {
                    if (!bowlingTeamPlayers.any { it.isJoker } && jokerPlayer != null) {
                        val newList = bowlingTeamPlayers.toMutableList()
                        newList.add(jokerPlayer.copy())
                        bowlingTeamPlayers = newList
                    }
// Apply the innings-ledger balls to the live Joker entry
                    ensureJokerStatsAppliedOnAdd()
                    bowlerIndex = bowlingTeamPlayers.indexOfFirst { it.isJoker }
                } else {
                    bowlerIndex = bowlingTeamPlayers.indexOfFirst { norm(it.name).equals(norm(player.name), ignoreCase = true) }
                }

                currentBowlerSpell = 1
                showBowlerDialog = false
                midOverReplacementDueToJoker.value = false
            },
            onDismiss = {
                if (ballsInOver > 0 && bowlerIndex == null) {
                    Toast.makeText(context, "Please select a bowler to continue", Toast.LENGTH_SHORT).show()
                } else {
                    showBowlerDialog = false
                    // Defensive: ensure flag is not stuck
                    midOverReplacementDueToJoker.value = false
                }
            },
            matchSettings = matchSettings,
            otherEndName = pickerOtherEndName
        )
    }

    if (showInningsBreakDialog) {
        jokerOutInCurrentInnings = false
        EnhancedInningsBreakDialog(
            runs = firstInningsRuns,
            wickets = firstInningsWickets,
            overs = firstInningsOvers,
            balls = firstInningsBalls,
            battingTeam = battingTeamName,
            battingPlayers = firstInningsBattingPlayersList,
            bowlingPlayers = firstInningsBowlingPlayersList,
            totalOvers = matchSettings.totalOvers,
            onStartSecondInnings = {
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
                // Make sure Joker is not already in either side at innings start
                battingTeamPlayers = battingTeamPlayers.filter { !it.isJoker }.toMutableList()
                bowlingTeamPlayers = bowlingTeamPlayers.filter { !it.isJoker }.toMutableList()
                // Joker flags/ball counters reset for new innings context
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
                
                // Initialize powerplay tracking for innings 2
                powerplayRunsInnings2 = 0
                powerplayDoublingDoneInnings2 = false

                // Reset partnership tracking for second innings
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
            },
        )
    }

    if (showMatchCompleteDialog) {
        EnhancedMatchCompleteDialog(
            matchId = matchId,
            firstInningsRuns = firstInningsRuns,
            firstInningsWickets = firstInningsWickets,
            secondInningsRuns = calculatedTotalRuns,
            secondInningsWickets = totalWickets,
            team1Name = initialBattingTeamName,
            team2Name = initialBowlingTeamName,
            jokerPlayerName = jokerName.takeIf { it.isNotEmpty() },
            team1CaptainName = if (initialBattingTeamName == team1Name) team1CaptainName else team2CaptainName,
            team2CaptainName = if (initialBattingTeamName == team1Name) team2CaptainName else team1CaptainName,
            firstInningsBattingPlayers = firstInningsBattingPlayersList,
            firstInningsBowlingPlayers = firstInningsBowlingPlayersList,
            secondInningsBattingPlayers = secondInningsBattingPlayers,
            secondInningsBowlingPlayers = secondInningsBowlingPlayers,
            onNewMatch = {
                val intent = android.content.Intent(context, MainActivity::class.java)
                intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                context.startActivity(intent)
                (context as androidx.activity.ComponentActivity).finish()
            },
            onDismiss = {
                showMatchCompleteDialog = false

                val intent = android.content.Intent(context, FullScorecardActivity::class.java)
                intent.putExtra("match_id", matchId)
                context.startActivity(intent)
                (context as androidx.activity.ComponentActivity).finish()
            },
            matchSettings = matchSettings,
            groupId = groupId,
            groupName = groupName,
            scope = scope,
            repo = repo,
            inProgressManager = inProgressManager,
            allDeliveries = allDeliveries.toList()
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "Exit Match?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Are you sure you want to exit?",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Match progress will be saved and you can resume later from Match History.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val intent = android.content.Intent(context, MainActivity::class.java)
                        context.startActivity(intent)
                        (context as androidx.activity.ComponentActivity).finish()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Exit", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Continue Match")
                }
            },
        )
    }

    // Unlimited Undo Password Dialog
    if (showUnlimitedUndoDialog) {
        var password by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                showUnlimitedUndoDialog = false
                password = ""
                errorMessage = null
            },
            title = {
                Text(
                    "🔒 Password Required",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        if (pendingUnlimitedUndoValue) {
                            "Enable unlimited undo to undo all the way back to match start. This is useful for correcting errors but should only be used by the match master."
                        } else {
                            "Lock undo back to 2 balls only."
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        label = { Text("Password") },
                        singleLine = true,
                        isError = errorMessage != null,
                        supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Check password (using the same password from player deletion)
                        val prefs = context.getSharedPreferences("stumpd_prefs", android.content.Context.MODE_PRIVATE)
                        val savedPassword = prefs.getString("deletion_password", null)

                        if (savedPassword == null) {
                            // No password set, save this one
                            prefs.edit().putString("deletion_password", password).apply()
                            com.oreki.stumpd.utils.FeatureFlags.setUnlimitedUndoEnabled(context, pendingUnlimitedUndoValue)
                            unlimitedUndoEnabled = pendingUnlimitedUndoValue
                            showUnlimitedUndoDialog = false
                            password = ""
                            errorMessage = null
                            Toast.makeText(
                                context,
                                if (pendingUnlimitedUndoValue) "✅ Unlimited undo enabled" else "✅ Undo locked to 2 balls",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else if (password == savedPassword) {
                            // Correct password
                            com.oreki.stumpd.utils.FeatureFlags.setUnlimitedUndoEnabled(context, pendingUnlimitedUndoValue)
                            unlimitedUndoEnabled = pendingUnlimitedUndoValue
                            showUnlimitedUndoDialog = false
                            password = ""
                            errorMessage = null
                            Toast.makeText(
                                context,
                                if (pendingUnlimitedUndoValue) "✅ Unlimited undo enabled" else "✅ Undo locked to 2 balls",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            // Wrong password
                            errorMessage = "❌ Incorrect password"
                        }
                    },
                    enabled = password.isNotBlank()
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUnlimitedUndoDialog = false
                        password = ""
                        errorMessage = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRetirementDialog) {
        RetirementDialog(
            striker = striker,
            nonStriker = nonStriker,
            onRetireBatsman = { position ->
                retiringPosition = position
                showRetirementDialog = false
                
                // Mark the batsman as retired
                val batsmanIndex = if (position == 1) strikerIndex else nonStrikerIndex
                batsmanIndex?.let { idx ->
                    val updatedPlayer = battingTeamPlayers[idx].copy(isRetired = true)
                    battingTeamPlayers = battingTeamPlayers.toMutableList().apply {
                        this[idx] = updatedPlayer
                    }
                    
                    // Clear the position
                    if (position == 1) {
                        strikerIndex = null
                    } else {
                        nonStrikerIndex = null
                    }
                    
                    // Open batsman dialog for replacement
                    selectingBatsman = position
                    showBatsmanDialog = true
                    
                    Toast.makeText(context, "${updatedPlayer.name} retired", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showRetirementDialog = false }
        )
    }

    if (showWicketDialog) {
        WicketTypeDialog(
            onWicketSelected = { wicketType ->
                val dismissedIndex = strikerIndex
                val jokerWasOut = striker?.isJoker == true

                if (wicketType == WicketType.RUN_OUT) {
                    showWicketDialog = false
                    showRunOutDialog = true
                    return@WicketTypeDialog
                }
                
                // For CAUGHT and STUMPED, show fielder selection dialog
                if (wicketType == WicketType.CAUGHT || wicketType == WicketType.STUMPED) {
                    pendingWicketType = wicketType
                    showWicketDialog = false
                    showFielderSelectionDialog = true
                    return@WicketTypeDialog
                }

                // --- begin replacement logic ---
                pushSnapshot()
                totalWickets += 1

                // mark striker as out in the striker's record
                updateStrikerAndTotals { p ->
                    p.copy(
                        isOut = true,
                        ballsFaced = p.ballsFaced + 1,
                        dismissalType = wicketType,
                        bowlerName = bowler?.name
                    )
                }

                val outSnapshot = dismissedIndex?.let { idx ->
                    battingTeamPlayers.getOrNull(idx)?.copy()
                }

                // Record fall of wicket and end partnership
                outSnapshot?.let { endPartnershipAndRecordWicket(it, isRunOut = false) }

                // ledger update - always add if out (regardless of runs/balls)
                if (outSnapshot != null && outSnapshot.isOut) {
                    if (currentInnings == 1) {
                        if (completedBattersInnings1.none { it.name.equals(outSnapshot.name, true) }) {
                            completedBattersInnings1 =
                                (completedBattersInnings1 + outSnapshot).toMutableList()
                        } else {
                            completedBattersInnings1 = completedBattersInnings1
                                .map { if (it.name.equals(outSnapshot.name, true)) outSnapshot else it }
                                .toMutableList()
                        }
                    } else {
                        if (completedBattersInnings2.none { it.name.equals(outSnapshot.name, true) }) {
                            completedBattersInnings2 =
                                (completedBattersInnings2 + outSnapshot).toMutableList()
                        } else {
                            completedBattersInnings2 = completedBattersInnings2
                                .map { if (it.name.equals(outSnapshot.name, true)) outSnapshot else it }
                                .toMutableList()
                        }
                    }
                }

                // update bowler (unchanged)
                updateBowlerStats { player ->
                    player.copy(
                        wickets = player.wickets + 1,
                        ballsBowled = player.ballsBowled + 1
                    )
                }

                // cache current end names/indexes BEFORE any index changes
                val curStrikerIndex = strikerIndex
                val curNonStrikerIndex = nonStrikerIndex
                val curStrikerName = striker?.name
                val curNonStrikerName = nonStriker?.name

                val jokerWasBowling = bowler?.isJoker == true

                Toast.makeText(
                    context,
                    "Wicket! ${striker?.name} is ${wicketType.name.lowercase().replace("_", " ")}",
                    Toast.LENGTH_LONG
                ).show()

                // Handle joker-out removal (unchanged)
                if (jokerWasOut) {
                    jokerOutInCurrentInnings = true
                    val jokerBattingIndex = battingTeamPlayers.indexOfFirst { it.isJoker }
                    if (jokerBattingIndex != -1) {
//                        val newBattingList = battingTeamPlayers.toMutableList()
//                        newBattingList.removeAt(jokerBattingIndex)
//                        battingTeamPlayers = newBattingList

                        if (strikerIndex == jokerBattingIndex) strikerIndex = null
                        if (nonStrikerIndex == jokerBattingIndex) nonStrikerIndex = null
                        else if (nonStrikerIndex != null && nonStrikerIndex!! > jokerBattingIndex) {
                            nonStrikerIndex = nonStrikerIndex!! - 1
                        }
                    }
                    Toast.makeText(
                        context,
                        "🃏 Joker is now available for bowling!",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                // Recompute availability AFTER any joker removal
                val availableBatsmenAfterWicket = battingTeamPlayers.count { !it.isOut }
                val jokerAvailableForBattingInWicketDialog = jokerPlayer != null &&
                        !battingTeamPlayers.any { it.isJoker } &&
                        !jokerOutInCurrentInnings

                val totalAvailableBatsmen = availableBatsmenAfterWicket + if (jokerAvailableForBattingInWicketDialog) 1 else 0

                // Check if innings will be complete after this wicket
                val inningsWillEnd = if (matchSettings.allowSingleSideBatting) {
                    totalAvailableBatsmen == 0
                } else {
                    totalWickets >= battingTeamPlayers.size - 1
                }

                when {
                    inningsWillEnd -> {
                        // Innings ending - clear both ends
                        strikerIndex = null
                        nonStrikerIndex = null
                    }

                    // single-side batting: be end-aware (place last batter into the end that is empty)
                    matchSettings.allowSingleSideBatting && totalAvailableBatsmen == 1 -> {
                        if (jokerAvailableForBattingInWicketDialog && availableBatsmenAfterWicket == 0) {
                            // Joker is only available — open picker at the end that was just freed
                            selectingBatsman = if (dismissedIndex == curStrikerIndex) 1 else 2
                            pickerOtherEndName = if (dismissedIndex == curStrikerIndex) curNonStrikerName else curStrikerName
                            showBatsmanDialog = true
                            Toast.makeText(context, "🃏 Only joker available to bat!", Toast.LENGTH_LONG).show()
                        } else {
                            // keep the remaining batter in the slot that is NOT dismissed
                            val lastBatsman = battingTeamPlayers.indexOfFirst { !it.isOut }
                            if (curStrikerIndex == dismissedIndex) {
                                // striker was dismissed earlier → place last batsman in striker slot
                                strikerIndex = lastBatsman
                            } else {
                                // non-striker slot was freed earlier → place last batsman in non-striker slot
                                nonStrikerIndex = lastBatsman
                            }
                            Toast.makeText(
                                context,
                                "Single side batting: ${battingTeamPlayers[lastBatsman].name} continues alone",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    !matchSettings.allowSingleSideBatting && totalAvailableBatsmen == 1 -> {
                        strikerIndex = null
                        nonStrikerIndex = null
                    }

                    else -> {
                        // Normal case: striker wicket (this dialog is for striker) -> ONLY clear striker slot
                        // Do NOT promote non-striker to striker; keep non-striker untouched.
                        Toast.makeText(context, "Normal Out flow!", Toast.LENGTH_LONG).show()
                        strikerIndex = null
                        selectingBatsman = 1
                        pickerOtherEndName = curNonStrikerName
                        showBatsmanDialog = true
                        Toast.makeText(context, "End of Normal Out flow", Toast.LENGTH_LONG).show()
                        if (jokerWasBowling && !jokerWasOut) {
                            Toast.makeText(context, "🃏 Joker can now bat!", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                // --- now handle ball/over progression AFTER replacement decision ---
                ballsInOver += 1
                incJokerBallIfBowledThisDelivery()
                addDelivery("W", highlight = true, runs = 0)
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
                }

                showWicketDialog = false
                val bowlerName = bowler?.name ?: "Bowler"
                val outName = outSnapshot?.name ?: "Batsman"
                val totalAfter = battingTeamPlayers.sumOf { it.runs } + totalExtras
                Toast.makeText(
                    context,
                    "Wicket! $outName ${wicketType.name.lowercase().replace('_', ' ')}. Total: $totalAfter. $bowlerName to continue.",
                    Toast.LENGTH_LONG
                ).show()
            },
            onDismiss = { showWicketDialog = false },
        )
    }

    if (showRunOutDialog) {
        RunOutDialog(
            striker = striker,
            nonStriker = nonStriker,
            onConfirm = { input ->
                // Store the run-out input and show fielder selection
                pendingRunOutInput = input
                showRunOutDialog = false
                showFielderSelectionDialog = true
                pendingWicketType = WicketType.RUN_OUT
            },
            onDismiss = { 
                showRunOutDialog = false
            }
        )
    }

    // Handle run-out fielder selection
    if (showFielderSelectionDialog && pendingWicketType == WicketType.RUN_OUT && pendingRunOutInput != null) {
        // Joker can field if they exist and are not currently batting (striker/non-striker) or bowling
        val jokerAvailableForFielding = jokerPlayer != null && 
            striker?.isJoker != true && 
            nonStriker?.isJoker != true && 
            bowler?.isJoker != true
        
        FielderSelectionDialog(
            wicketType = WicketType.RUN_OUT,
            bowlingTeamPlayers = bowlingTeamPlayers,
            jokerPlayer = jokerPlayer,
            jokerAvailableForFielding = jokerAvailableForFielding,
            currentBowler = bowler,
            onFielderSelected = { fielder ->
                val input = pendingRunOutInput!!
                pushSnapshot()

                val runsCompleted = input.runsCompleted
                val outPlayerName = input.whoOut
                val outEnd = input.end

                // Snapshot current refs to avoid race with later index changes
                val curStrikerIndex = strikerIndex
                val curNonStrikerIndex = nonStrikerIndex
                val curStriker = striker
                val curNonStriker = nonStriker
                val curStrikerName = curStriker?.name
                val curNonStrikerName = curNonStriker?.name

                // 1) Update striker and bowler stats exactly from scorer input
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

                // Credit the fielder for run-out
                fielder?.let { fieldingPlayer ->
                    // Check if fielder is already in the bowling team
                    val fielderIndex = bowlingTeamPlayers.indexOfFirst { it.name == fieldingPlayer.name }
                    if (fielderIndex != -1) {
                        // Fielder is in the team, update their stats
                        val updated = bowlingTeamPlayers.toMutableList()
                        updated[fielderIndex] = updated[fielderIndex].copy(runOuts = updated[fielderIndex].runOuts + 1)
                        bowlingTeamPlayers = updated
                    } else if (fieldingPlayer.isJoker) {
                        // Joker fielded but isn't in the bowling team yet - add them with the run-out
                        val jokerWithRunOut = fieldingPlayer.copy(runOuts = 1)
                        bowlingTeamPlayers = (bowlingTeamPlayers + jokerWithRunOut).toMutableList()
                        Toast.makeText(context, "🃏 Joker credited with run-out!", Toast.LENGTH_SHORT).show()
                    }
                }

                // 2) Find the dismissed player by name
                val outIndex = battingTeamPlayers.indexOfFirst { it.name.equals(outPlayerName, ignoreCase = true) }
                if (outIndex == -1) {
                    Toast.makeText(context, "Could not find player \"$outPlayerName\" in batting team", Toast.LENGTH_LONG).show()
                    showFielderSelectionDialog = false
                    pendingRunOutInput = null
                    pendingWicketType = null
                    return@FielderSelectionDialog
                }

                // 3) Mark the player out
                val newBatting = battingTeamPlayers.toMutableList()
                val wasJoker = newBatting[outIndex].isJoker
                val jokerWasBowling = bowler?.isJoker == true
                val jokerWasOut = wasJoker
                newBatting[outIndex] = newBatting[outIndex].copy(
                    isOut = true,
                    dismissalType = WicketType.RUN_OUT,
                    fielderName = fielder?.name
                )
                battingTeamPlayers = newBatting
                // 3b) Joker-specific handling (mirror of normal wicket code)
                if (wasJoker) {
                    jokerOutInCurrentInnings = true
                    // If joker representation should be removed from the batting list, do it here:
                    // val idx = outIndex
                    // val tmp = battingTeamPlayers.toMutableList()
                    // tmp.removeAt(idx)
                    // battingTeamPlayers = tmp

                    // Clear/adjust ends like in the normal wicket flow
                    if (strikerIndex == outIndex) strikerIndex = null
                    if (nonStrikerIndex == outIndex) nonStrikerIndex = null
                    else if (nonStrikerIndex != null && nonStrikerIndex!! > outIndex) {
                        nonStrikerIndex = nonStrikerIndex!! - 1
                    }

                    // Optional UX cue
                    Toast.makeText(context, "🃏 Joker is now available for Bowling (run out).", Toast.LENGTH_SHORT).show()
                }

                // 4) Record completed batter snapshot
                val outSnapshot = battingTeamPlayers[outIndex].copy()
                if (currentInnings == 1) {
                    completedBattersInnings1 = completedBattersInnings1
                        .filterNot { it.name.equals(outSnapshot.name, true) }
                        .toMutableList()
                        .apply { add(outSnapshot) }
                } else {
                    completedBattersInnings2 = completedBattersInnings2
                        .filterNot { it.name.equals(outSnapshot.name, true) }
                        .toMutableList()
                        .apply { add(outSnapshot) }
                }

                // 5) Increment wickets
                totalWickets += 1

                // 6) Defensive / helpful warning if scorer input looks inconsistent
                if ((outIndex == curStrikerIndex && outEnd == RunOutEnd.NON_STRIKER_END) ||
                    (outIndex == curNonStrikerIndex && outEnd == RunOutEnd.STRIKER_END)
                ) {
                    // Inform scorer but continue to honor their input
                    Toast.makeText(
                        context,
                        "Note: $outPlayerName was listed at ${if (outIndex == curStrikerIndex) "striker" else "non-striker"}, " +
                                "but you selected ${if (outEnd == RunOutEnd.STRIKER_END) "Striker's End" else "Non-Striker's End"}. " +
                                "Honouring scorer input.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                // 7) Remove the out player from whichever slot they occupied
                if (strikerIndex == outIndex) strikerIndex = null
                if (nonStrikerIndex == outIndex) nonStrikerIndex = null

                // 7b) If scorer's "end" does not match the dismissed player's slot, adjust indexes
                if (outIndex == curNonStrikerIndex && outEnd == RunOutEnd.STRIKER_END) {
                    // Scorer says wicket was at striker end but non-striker is actually out
                    // → shift striker into non-striker slot
                    nonStrikerIndex = curStrikerIndex
                    strikerIndex = null
                } else if (outIndex == curStrikerIndex && outEnd == RunOutEnd.NON_STRIKER_END) {
                    // Scorer says wicket was at non-striker end but striker is actually out
                    // → shift non-striker into striker slot
                    strikerIndex = curNonStrikerIndex
                    nonStrikerIndex = null
                }

                // 8) Handle replacement based on available batsmen and single-side batting
                val availableBatsmenAfterRunOut = battingTeamPlayers.count { !it.isOut }
                val jokerAvailableForBattingInRunOut = jokerPlayer != null &&
                        !battingTeamPlayers.any { it.isJoker } &&
                        !jokerOutInCurrentInnings
                val totalAvailableBatsmen = availableBatsmenAfterRunOut + if (jokerAvailableForBattingInRunOut) 1 else 0

                when {
                    totalAvailableBatsmen == 0 -> {
                        // No batsmen left - innings will end
                        strikerIndex = null
                        nonStrikerIndex = null
                    }

                    // single-side batting: be end-aware (place last batter into the end that is empty)
                    matchSettings.allowSingleSideBatting && totalAvailableBatsmen == 1 -> {
                        if (jokerAvailableForBattingInRunOut && availableBatsmenAfterRunOut == 0) {
                            // Joker is only available — open picker at the end that was just freed
                            selectingBatsman = if (outEnd == RunOutEnd.STRIKER_END) 1 else 2
                            pickerOtherEndName = if (outEnd == RunOutEnd.STRIKER_END) curNonStrikerName else curStrikerName
                            showBatsmanDialog = true
                            Toast.makeText(context, "🃏 Only joker available to bat!", Toast.LENGTH_LONG).show()
                        } else {
                            // keep the remaining batter in the slot that is NOT dismissed
                            val lastBatsman = battingTeamPlayers.indexOfFirst { !it.isOut }
                            if (outEnd == RunOutEnd.STRIKER_END) {
                                // striker end was dismissed → place last batsman in striker slot
                                strikerIndex = lastBatsman
                            } else {
                                // non-striker end was dismissed → place last batsman in non-striker slot
                                nonStrikerIndex = lastBatsman
                            }
                            Toast.makeText(
                                context,
                                "Single side batting: ${battingTeamPlayers[lastBatsman].name} continues alone",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    !matchSettings.allowSingleSideBatting && totalAvailableBatsmen == 1 -> {
                        // Single-side not enabled, only 1 left - innings ends
                        strikerIndex = null
                        nonStrikerIndex = null
                    }

                    else -> {
                        // Normal case: multiple batsmen available, show selection dialog
                if (outEnd == RunOutEnd.STRIKER_END) {
                    selectingBatsman = 1
                    pickerOtherEndName = curNonStrikerName // keep other batter as is
                    showBatsmanDialog = true
                } else {
                    selectingBatsman = 2
                    pickerOtherEndName = curStrikerName
                    showBatsmanDialog = true
                        }
                        if (jokerWasBowling && !jokerWasOut) {
                            Toast.makeText(context, "🃏 Joker can now bat!", Toast.LENGTH_LONG).show()
                        }
                    }
                }


                // 9) Ball & over progression (as before)
                if (!isNoBallRunOut) {
                    ballsInOver += 1
                    incJokerBallIfBowledThisDelivery()
                }
                // 10) Delivery log + feedback
                val label = "${runsCompleted} + RO (${outPlayerName} @ ${if (outEnd == RunOutEnd.STRIKER_END) "S" else "NS"})"
                addDelivery(label, highlight = true, runs = runsCompleted)
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
                }


                val fielderCredit = if (fielder != null) " (${fielder.name})" else ""
                Toast.makeText(
                    context,
                    "Run out! $outPlayerName dismissed$fielderCredit. $runsCompleted run(s) recorded.",
                    Toast.LENGTH_LONG
                ).show()

                showFielderSelectionDialog = false
                pendingRunOutInput = null
                pendingWicketType = null
                isNoBallRunOut = false
            },
            onDismiss = {
                showFielderSelectionDialog = false
                pendingRunOutInput = null
                pendingWicketType = null
            }
        )
    }
    
    // Fielder selection dialog for CAUGHT and STUMPED (not RUN_OUT - that's handled above)
    if (showFielderSelectionDialog && pendingWicketType != null && pendingWicketType != WicketType.RUN_OUT) {
        // Joker can field if they exist and are not currently batting (striker/non-striker) or bowling
        val jokerAvailableForFielding = jokerPlayer != null && 
            striker?.isJoker != true && 
            nonStriker?.isJoker != true && 
            bowler?.isJoker != true
        
        FielderSelectionDialog(
            wicketType = pendingWicketType!!,
            bowlingTeamPlayers = bowlingTeamPlayers,
            jokerPlayer = jokerPlayer,
            jokerAvailableForFielding = jokerAvailableForFielding,
            currentBowler = bowler,
            onFielderSelected = { fielder ->
                // Credit the fielder
                fielder?.let { fieldingPlayer ->
                    val fielderIndex = bowlingTeamPlayers.indexOfFirst { it.name == fieldingPlayer.name }
                    if (fielderIndex != -1) {
                        // Fielder is in the team, update their stats
                        val updated = bowlingTeamPlayers.toMutableList()
                        updated[fielderIndex] = when (pendingWicketType) {
                            WicketType.CAUGHT -> updated[fielderIndex].copy(catches = updated[fielderIndex].catches + 1)
                            WicketType.STUMPED -> updated[fielderIndex].copy(stumpings = updated[fielderIndex].stumpings + 1)
                            else -> updated[fielderIndex]
                        }
                        bowlingTeamPlayers = updated
                    } else if (fieldingPlayer.isJoker) {
                        // Joker fielded but isn't in the bowling team yet - add them with the fielding stat
                        val jokerWithFielding = when (pendingWicketType) {
                            WicketType.CAUGHT -> fieldingPlayer.copy(catches = 1)
                            WicketType.STUMPED -> fieldingPlayer.copy(stumpings = 1)
                            else -> fieldingPlayer
                        }
                        bowlingTeamPlayers = (bowlingTeamPlayers + jokerWithFielding).toMutableList()
                        val actionText = when (pendingWicketType) {
                            WicketType.CAUGHT -> "catch"
                            WicketType.STUMPED -> "stumping"
                            else -> "fielding"
                        }
                        Toast.makeText(context, "🃏 Joker credited with $actionText!", Toast.LENGTH_SHORT).show()
                    }
                }
                
                // Check if this is a stumping on a wide
                val isStumpingOnWide = pendingWideExtraType != null
                
                // Now process the wicket with the selected type
                val wicketType = pendingWicketType!!
                val dismissedIndex = strikerIndex
                val jokerWasOut = striker?.isJoker == true

                pushSnapshot()
                totalWickets += 1

                // mark striker as out in the striker's record
                // Note: on wide, we don't increment ballsFaced (it's not a legal delivery)
                updateStrikerAndTotals { p ->
                    p.copy(
                        isOut = true,
                        ballsFaced = if (isStumpingOnWide) p.ballsFaced else p.ballsFaced + 1,
                        dismissalType = wicketType,
                        bowlerName = bowler?.name,
                        fielderName = fielder?.name
                    )
                }
                
                // If stumping on wide, add the wide runs and update bowler
                if (isStumpingOnWide) {
                    updateBowlerStats { player ->
                        player.copy(runsConceded = player.runsConceded + pendingWideRuns)
                    }
                    runsConcededInCurrentOver += pendingWideRuns
                    totalExtras += pendingWideRuns
                    addDelivery("Wd+${pendingWideRuns} W", highlight = true, runs = pendingWideRuns)
                }

                val outSnapshot = dismissedIndex?.let { idx ->
                    battingTeamPlayers.getOrNull(idx)?.copy()
                }

                // Record fall of wicket and end partnership
                outSnapshot?.let { endPartnershipAndRecordWicket(it, isRunOut = false) }

                // ledger update - always add if out (regardless of runs/balls)
                if (outSnapshot != null && outSnapshot.isOut) {
                    if (currentInnings == 1) {
                        if (completedBattersInnings1.none { it.name.equals(outSnapshot.name, true) }) {
                            completedBattersInnings1 = (completedBattersInnings1 + outSnapshot).toMutableList()
                        } else {
                            completedBattersInnings1 = completedBattersInnings1
                                .map { if (it.name.equals(outSnapshot.name, true)) outSnapshot else it }
                                .toMutableList()
                        }
                    } else {
                        if (completedBattersInnings2.none { it.name.equals(outSnapshot.name, true) }) {
                            completedBattersInnings2 = (completedBattersInnings2 + outSnapshot).toMutableList()
                        } else {
                            completedBattersInnings2 = completedBattersInnings2
                                .map { if (it.name.equals(outSnapshot.name, true)) outSnapshot else it }
                                .toMutableList()
                        }
                    }
                }

                // update bowler
                updateBowlerStats { player ->
                    player.copy(
                        wickets = player.wickets + 1,
                        ballsBowled = player.ballsBowled + 1
                    )
                }

                // cache current end names/indexes BEFORE any index changes
                val curStrikerIndex = strikerIndex
                val curNonStrikerIndex = nonStrikerIndex
                val curStrikerName = striker?.name
                val curNonStrikerName = nonStriker?.name

                val jokerWasBowling = bowler?.isJoker == true

                val fielderCredit = if (fielder != null) " (${fielder.name})" else ""
                Toast.makeText(
                    context,
                    "Wicket! ${striker?.name} is ${wicketType.name.lowercase().replace("_", " ")}$fielderCredit",
                    Toast.LENGTH_LONG
                ).show()

                // Handle joker-out removal
                if (jokerWasOut) {
                    jokerOutInCurrentInnings = true
                    val jokerBattingIndex = battingTeamPlayers.indexOfFirst { it.isJoker }
                    if (jokerBattingIndex != -1) {
                        if (strikerIndex == jokerBattingIndex) strikerIndex = null
                        if (nonStrikerIndex == jokerBattingIndex) nonStrikerIndex = null
                        else if (nonStrikerIndex != null && nonStrikerIndex!! > jokerBattingIndex) {
                            nonStrikerIndex = nonStrikerIndex!! - 1
                        }
                    }
                    Toast.makeText(context, "🃏 Joker is now available for bowling!", Toast.LENGTH_SHORT).show()
                }

                // Recompute availability AFTER any joker removal
                val availableBatsmenAfterWicket = battingTeamPlayers.count { !it.isOut }
                val jokerAvailableForBattingInWicketDialog = jokerPlayer != null &&
                        !battingTeamPlayers.any { it.isJoker } &&
                        !jokerOutInCurrentInnings

                val totalAvailableBatsmen = availableBatsmenAfterWicket + if (jokerAvailableForBattingInWicketDialog) 1 else 0

                when {
                    totalAvailableBatsmen == 0 -> {
                        strikerIndex = null
                        nonStrikerIndex = null
                    }

                    matchSettings.allowSingleSideBatting && totalAvailableBatsmen == 1 -> {
                        if (jokerAvailableForBattingInWicketDialog && availableBatsmenAfterWicket == 0) {
                            selectingBatsman = if (dismissedIndex == curStrikerIndex) 1 else 2
                            pickerOtherEndName = if (dismissedIndex == curStrikerIndex) curNonStrikerName else curStrikerName
                            showBatsmanDialog = true
                            Toast.makeText(context, "🃏 Only joker available to bat!", Toast.LENGTH_LONG).show()
                        } else {
                            val lastBatsman = battingTeamPlayers.indexOfFirst { !it.isOut }
                            if (curStrikerIndex == dismissedIndex) {
                                strikerIndex = lastBatsman
                            } else {
                                nonStrikerIndex = lastBatsman
                            }
                            Toast.makeText(
                                context,
                                "Single side batting: ${battingTeamPlayers[lastBatsman].name} continues alone",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    !matchSettings.allowSingleSideBatting && totalAvailableBatsmen == 1 -> {
                        strikerIndex = null
                        nonStrikerIndex = null
                    }

                    else -> {
                        strikerIndex = null
                        selectingBatsman = 1
                        pickerOtherEndName = curNonStrikerName
                        showBatsmanDialog = true
                        if (jokerWasBowling && !jokerWasOut) {
                            Toast.makeText(context, "🃏 Joker can now bat!", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                // now handle ball/over progression AFTER replacement decision
                // NOTE: For stumping on wide, we don't increment balls (it's not a legal delivery)
                if (!isStumpingOnWide) {
                    ballsInOver += 1
                    incJokerBallIfBowledThisDelivery()
                    addDelivery("W", highlight = true, runs = 0)
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
                    }
                }

                showFielderSelectionDialog = false
                pendingWicketType = null
                pendingWideExtraType = null
                pendingWideRuns = 0
                val bowlerName = bowler?.name ?: "Bowler"
                val outName = outSnapshot?.name ?: "Batsman"
                val totalAfter = battingTeamPlayers.sumOf { it.runs } + totalExtras
                Toast.makeText(
                    context,
                    "Wicket! $outName ${wicketType.name.lowercase().replace('_', ' ')}. Total: $totalAfter. $bowlerName to continue.",
                    Toast.LENGTH_LONG
                ).show()
            },
            onDismiss = {
                showFielderSelectionDialog = false
                pendingWicketType = null
            }
        )
    }

}

@Composable
fun ScoreHeaderCard(
    battingTeamName: String,
    currentInnings: Int,
    matchSettings: MatchSettings,
    calculatedTotalRuns: Int,
    totalWickets: Int,
    currentOver: Int,
    ballsInOver: Int,
    totalExtras: Int,
    battingTeamPlayers: List<Player>,
    firstInningsRuns: Int,
    isPowerplayActive: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Team name and innings info
            Text(
                text = "$battingTeamName • Innings $currentInnings",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            
            // Powerplay indicator
            if (isPowerplayActive) {
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondary,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "⚡ PP (${currentOver + 1}/${matchSettings.powerplayOvers})",
                        fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Main score - large and prominent
            Text(
                text = "$calculatedTotalRuns/$totalWickets",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                letterSpacing = (-1).sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            // Stats row with modern styling
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Overs
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                        text = "$currentOver.$ballsInOver",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "of ${matchSettings.totalOvers} ov",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
                
                // Run Rate
                val runRate = if (currentOver == 0 && ballsInOver == 0) 0.0
                else calculatedTotalRuns.toDouble() / ((currentOver * 6 + ballsInOver) / 6.0)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                        text = "${"%.2f".format(runRate)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "Run Rate",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
                
                // Extras if present
            if (totalExtras > 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                            text = "$totalExtras",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Text(
                            text = "Extras",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
            }
            
            // Second innings chase info
            if (currentInnings == 2) {
                Spacer(modifier = Modifier.height(4.dp))
                val target = firstInningsRuns + 1
                val required = target - calculatedTotalRuns
                val ballsLeft = (matchSettings.totalOvers - currentOver) * 6 - ballsInOver
                val requiredRunRate = if (ballsLeft > 0) (required.toDouble() / ballsLeft) * 6 else 0.0
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (required > 0) 
                            MaterialTheme.colorScheme.tertiaryContainer 
                        else 
                            MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                Text(
                    text = if (required > 0) {
                            "Need $required in $ballsLeft balls • RRR: ${"%.2f".format(requiredRunRate)}"
                    } else {
                        "🎉 Target achieved!"
                    },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (required > 0) 
                            MaterialTheme.colorScheme.onTertiaryContainer 
                        else 
                            MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(6.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun PlayersCard(
    showSingleSideLayout: Boolean,
    striker: Player?,
    nonStriker: Player?,
    bowler: Player?,
    availableBatsmen: Int,
    onSelectStriker: () -> Unit,
    onSelectNonStriker: () -> Unit,
    onSelectBowler: () -> Unit,
    onSwapStrike: () -> Unit,
    currentBowlerSpell: Int,
    jokerPlayer: Player?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Current Players Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Current Players",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Swap strike button (compact icon version)
                if (striker != null && nonStriker != null && !showSingleSideLayout && availableBatsmen > 1) {
                    SwapStrikeButton(onSwap = onSwapStrike)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            BattingSection(
                showSingleSideLayout = showSingleSideLayout,
                striker = striker,
                nonStriker = nonStriker,
                onSelectStriker = onSelectStriker,
                onSelectNonStriker = onSelectNonStriker
            )

            if (showSingleSideLayout && striker != null) {
                SingleSideBattingStatus(striker.name)
            }

            Spacer(modifier = Modifier.height(12.dp))

            BowlerSection(
                bowler = bowler,
                currentBowlerSpell = currentBowlerSpell,
                onSelectBowler = onSelectBowler
            )

            jokerPlayer?.let { joker ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "🃏 Joker Available: ${joker.name}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun BattingSection(
    showSingleSideLayout: Boolean,
    striker: Player?,
    nonStriker: Player?,
    onSelectStriker: () -> Unit,
    onSelectNonStriker: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (showSingleSideLayout) Arrangement.Center else Arrangement.SpaceBetween,
    ) {
        // Striker Column (Always shown)
        BatsmanColumn(
            player = striker,
            onClick = onSelectStriker,
            center = showSingleSideLayout,
            isStriker = true,
            isLastBatsman = (showSingleSideLayout && striker != null),
            modifier = if (showSingleSideLayout) Modifier else Modifier.weight(1f)
        )

        // Non-Striker Column - Show when NOT in single-side layout
        if (!showSingleSideLayout) {
            BatsmanColumn(
                player = nonStriker,
                onClick = onSelectNonStriker,
                center = false,
                isStriker = false,
                isLastBatsman = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun BatsmanColumn(
    player: Player?,
    onClick: () -> Unit,
    center: Boolean = false,
    isStriker: Boolean,
    isLastBatsman: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = if (center) Alignment.CenterHorizontally else if (isStriker) Alignment.Start else Alignment.End
    ) {
        Text(
            text = "🏏 ${player?.name ?: if (isStriker) "Select Striker" else "Select Non-Striker"}",
            fontWeight = if (isStriker) FontWeight.Bold else FontWeight.Normal,
            color = if (player == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        player?.let {
            Text(
                text = "${it.runs}${if (!it.isOut && it.ballsFaced > 0) "*" else ""} (${it.ballsFaced}) - 4s: ${it.fours}, 6s: ${it.sixes}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "SR: ${"%.1f".format(it.strikeRate)}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
        if (isLastBatsman) {
            Text(
                text = "⚡ Last Batsman",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic
            )
        }
    }
}

@Composable
fun SingleSideBattingStatus(strikerName: String) {
    Spacer(modifier = Modifier.height(8.dp))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Text(
            text = "⚡ Single Side Batting: $strikerName continues alone",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
fun SwapStrikeButton(onSwap: () -> Unit) {
    FilledTonalIconButton(
        onClick = onSwap,
        modifier = Modifier.size(40.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Swap Strike",
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun BowlerSection(
    bowler: Player?,
    currentBowlerSpell: Int,
    onSelectBowler: () -> Unit
) {
    Column(modifier = Modifier.clickable { onSelectBowler() }) {
        Text(
            text = "⚾ Bowler: ${bowler?.name ?: "Select Bowler"}",
            fontWeight = FontWeight.Medium,
            color = if (bowler == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        bowler?.let { currentBowler ->
            Text(
                text = "${"%.1f".format(currentBowler.oversBowled)} overs, ${currentBowler.runsConceded} runs, ${currentBowler.wickets} wickets",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Economy: ${"%.1f".format(currentBowler.economy)} | Spell: $currentBowlerSpell over${if (currentBowlerSpell != 1) "s" else ""}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun ScoringButtons(
    striker: Player?,
    nonStriker: Player?,
    bowler: Player?,
    isInningsComplete: Boolean,
    matchSettings: MatchSettings,
    availableBatsmen: Int,
    calculatedTotalRuns: Int,
    onScoreRuns: (Int) -> Unit,
    onShowExtras: () -> Unit,
    onShowWicket: () -> Unit,
    onUndo: () -> Unit,
    onWide: () -> Unit,
    onRetire: () -> Unit,
    unlimitedUndoEnabled: Boolean,
    onToggleUnlimitedUndo: (Boolean) -> Unit
) {
    val canStartScoring = striker != null && bowler != null &&
            (nonStriker != null || (matchSettings.allowSingleSideBatting && availableBatsmen == 1))

    Column(modifier = Modifier.fillMaxWidth()) {
    when {
        canStartScoring && !isInningsComplete -> {
                Text(
                    text = "Runs",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))

                if (matchSettings.shortPitch) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (0..4).forEach { RunButton(it, onScoreRuns) }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (0..6).forEach { RunButton(it, onScoreRuns) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                
                // Quick Wide and Retire buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Quick Wide button (adds Wide + 0 runs directly)
                    FilledTonalButton(
                        onClick = onWide,
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    ) {
                        Text("Wide", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    
                    // Retire button
                    FilledTonalButton(
                        onClick = onRetire,
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text("Retire", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionTonalButton(
                        label = "More Extras",
                        modifier = Modifier.weight(1f),
                        onClick = onShowExtras
                    )
                    // Prominent Wicket
                    Button(
                        onClick = onShowWicket,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text("Wicket", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        isInningsComplete -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                ),
            ) {
                Text(
                    text = "Innings Complete! Total: $calculatedTotalRuns runs",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }

        else -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "⚠️ Please select players to start scoring",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center,
                    )
                    if (matchSettings.allowSingleSideBatting) {
                        Text(
                            text = "Single side batting enabled - only one batsman required",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }
            }
        }
        }
        
        // Undo button - always visible
        Spacer(Modifier.height(4.dp))
        ActionTonalButton(
            label = "Undo Last Delivery",
            modifier = Modifier.fillMaxWidth(),
            onClick = onUndo
        )

        // Unlimited Undo Toggle - compact indicator
        Spacer(Modifier.height(4.dp))
        UnlimitedUndoToggle(
            isEnabled = unlimitedUndoEnabled,
            onToggle = onToggleUnlimitedUndo
        )
    }
}

@Composable
private fun RowScope.RunButton(
    value: Int,
    onClick: (Int) -> Unit,
) {
    val isSpecial = value == 4 || value == 6
    Button(
        onClick = { onClick(value) },
        modifier = Modifier
            .weight(1f)
            .height(56.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isSpecial)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isSpecial)
                MaterialTheme.colorScheme.onSecondaryContainer
            else
                MaterialTheme.colorScheme.onSurface // Better contrast
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 6.dp
        )
    ) {
        Text(
            text = value.toString(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ActionTonalButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.filledTonalButtonColors()
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private data class Agg(
    val id: String,
    val name: String,
    val team: String,
    var runs: Int = 0,
    var balls: Int = 0,
    var fours: Int = 0,
    var sixes: Int = 0,
    var notOut: Boolean = false,
    var wkts: Int = 0,
    var rcv: Int = 0,
    var ballsBowled: Int = 0,
    var isJoker: Boolean = false,
    var catches: Int = 0,
    var runOuts: Int = 0,
    var stumpings: Int = 0
)

private fun summarizeAgg(a: Agg): String {
    val bat = if (a.balls > 0) "${a.runs}${if (a.notOut) "*" else ""}(${a.balls})" else ""
    val bowl = if (a.ballsBowled > 0) "${a.wkts}/${a.rcv}" else ""
    val field = buildList {
        if (a.catches > 0) add("${a.catches} ct")
        if (a.runOuts > 0) add("${a.runOuts} ro")
        if (a.stumpings > 0) add("${a.stumpings} st")
    }.joinToString(", ")
    return listOf(bat, bowl, field).filter { it.isNotBlank() }.joinToString(" and ")
}

private fun computeMatchEconomy(
    firstBat: List<PlayerMatchStats>,
    secondBat: List<PlayerMatchStats>,
    totalOvers: Int
): Double {
    val totalRuns = firstBat.sumOf { it.runs } + secondBat.sumOf { it.runs }
    val oversApprox = totalOvers * 2.0
    return if (oversApprox > 0) totalRuns / oversApprox else 0.0
}

fun saveMatchToHistory(
    team1Name: String,
    team2Name: String,
    jokerPlayerName: String?,
    team1CaptainName: String? = null,
    team2CaptainName: String? = null,
    firstInningsRuns: Int,
    firstInningsWickets: Int,
    secondInningsRuns: Int,
    secondInningsWickets: Int,
    winnerTeam: String,
    winningMargin: String,
    firstInningsBattingStats: List<PlayerMatchStats> = emptyList(),
    firstInningsBowlingStats: List<PlayerMatchStats> = emptyList(),
    secondInningsBattingStats: List<PlayerMatchStats> = emptyList(),
    secondInningsBowlingStats: List<PlayerMatchStats> = emptyList(),
    context: android.content.Context,
    matchSettings: MatchSettings,
    groupId: String,
    groupName: String,
    allDeliveries: List<DeliveryUI> = emptyList()
): MatchHistory {
    android.util.Log.d("SaveMatch", "Attempting to save match: $team1Name vs $team2Name")
    // Simple leaders for display
    val allBattingStats = firstInningsBattingStats + secondInningsBattingStats
    val allBowlingStats = firstInningsBowlingStats + secondInningsBowlingStats
    val topBatsman = allBattingStats.maxByOrNull { it.runs }
    val topBowler = allBowlingStats
        .filter { it.oversBowled > 0 }
        .maxWithOrNull { a, b ->
            when {
                a.wickets != b.wickets -> a.wickets.compareTo(b.wickets)
                else -> {
                    val economyA = a.runsConceded.toDouble() / a.oversBowled
                    val economyB = b.runsConceded.toDouble() / b.oversBowled
                    economyB.compareTo(economyA)
                }
            }
        }

    val wasChaseWin = winnerTeam == team2Name
    val matchEconomy = computeMatchEconomy(firstInningsBattingStats, secondInningsBattingStats, matchSettings.totalOvers)

// SINGLE aggregation for impacts
    val aggMap: LinkedHashMap<String, Agg> = linkedMapOf()

    fun keyOf(p: PlayerMatchStats) = "${p.team}::${p.name.trim().lowercase()}"

    fun mergeInto(map: LinkedHashMap<String, Agg>, incoming: Agg) {
        val k = "${incoming.team}::${incoming.name.trim().lowercase()}"
        val a = map[k]
        if (a == null) {
            map[k] = incoming
        } else {
            a.runs += incoming.runs
            a.balls += incoming.balls
            a.fours += incoming.fours
            a.sixes += incoming.sixes
            a.notOut = a.notOut || incoming.notOut
            a.wkts += incoming.wkts
            a.rcv += incoming.rcv
            a.ballsBowled += incoming.ballsBowled
            a.isJoker = a.isJoker || incoming.isJoker
            a.catches += incoming.catches
            a.runOuts += incoming.runOuts
            a.stumpings += incoming.stumpings
        }
    }

    fun addBatting(ps: List<PlayerMatchStats>) {
        ps.forEach { p ->
            mergeInto(
                aggMap,
                Agg(
                    id = p.id,
                    name = p.name,
                    team = p.team,
                    runs = p.runs,
                    balls = p.ballsFaced,
                    fours = p.fours,
                    sixes = p.sixes,
                    notOut = (!p.isOut && p.ballsFaced > 0),
                    isJoker = p.isJoker
                )
            )
        }
    }

    fun addBowling(ps: List<PlayerMatchStats>) {
        ps.forEach { p ->
            mergeInto(
                aggMap,
                Agg(
                    id = p.id,
                    name = p.name,
                    team = p.team,
                    wkts = p.wickets,
                    rcv = p.runsConceded,
                    ballsBowled = (p.oversBowled * 6).toInt(),
                    isJoker = p.isJoker
                )
            )
        }
    }

    fun addFielding(ps: List<PlayerMatchStats>) {
        ps.forEach { p ->
            if (p.catches > 0 || p.runOuts > 0 || p.stumpings > 0) {
                mergeInto(
                    aggMap,
                    Agg(
                        id = p.id,
                        name = p.name,
                        team = p.team,
                        catches = p.catches,
                        runOuts = p.runOuts,
                        stumpings = p.stumpings,
                        isJoker = p.isJoker
                    )
                )
            }
        }
    }

    addBatting(firstInningsBattingStats)
    addBatting(secondInningsBattingStats)
    addBowling(firstInningsBowlingStats)
    addBowling(secondInningsBowlingStats)
    addFielding(firstInningsBattingStats)  // Fielding from first innings
    addFielding(firstInningsBowlingStats)
    addFielding(secondInningsBattingStats)  // Fielding from second innings
    addFielding(secondInningsBowlingStats)

    // Single scoring function closes over matchEconomy/wasChaseWin/winnerTeam
    fun scoreAgg(a: Agg): Double {
        if (a.isJoker) return 0.0
        val overs = matchSettings.totalOvers.toDouble()

        // Match context
        val totalMatchRuns = firstInningsRuns + secondInningsRuns
        val actualMatchEconomy = totalMatchRuns / (overs * 2)

        val economyPar = when {
            overs <= 10 -> kotlin.math.max(8.0, actualMatchEconomy - 1.0)
            overs <= 20 -> kotlin.math.max(7.0, actualMatchEconomy - 0.5)
            else -> kotlin.math.max(5.0, actualMatchEconomy - 0.5)
        }

        // BALANCED weights - reduced batting emphasis
        val runsWeight = 15.0 / overs      // Reduced from 20.0
        val fourBonus = 0.8 * (15.0 / overs)  // Reduced multiplier from 1.0
        val sixBonus = 1.2 * (15.0 / overs)   // Reduced multiplier from 1.5
        val wicketWeight = 60.0 / overs       // Increased for bowling from 50.0

        // Batting calculation
        val sr = if (a.balls > 0) a.runs * 100.0 / a.balls else 0.0
        var bat = a.runs * runsWeight + a.fours * fourBonus + a.sixes * sixBonus

        val srBonus = if (a.balls >= 10)
            kotlin.math.max(0.0, kotlin.math.min(8.0, (sr - 100.0) / 6.0))  // Reduced cap & steeper
        else 0.0
        val chaseBonus = if (wasChaseWin) kotlin.math.min(10.0, a.runs / 6.0) else 0.0  // Reduced
        bat += srBonus + chaseBonus

        // Enhanced bowling with matching bonuses
        val eco = if (a.ballsBowled > 0) a.rcv * 6.0 / a.ballsBowled else 0.0
        val runPenalty = if (overs <= 20) 0.03 else 0.08  // Further reduced

        // Expanded economy impact range to match batting bonuses
        val economyImpact = when {
            eco <= economyPar - 2.0 -> 15.0    // Exceptional
            eco <= economyPar - 1.5 -> 12.0    // Brilliant
            eco <= economyPar - 1.0 -> 8.0     // Very good
            eco <= economyPar - 0.5 -> 4.0     // Good
            eco <= economyPar -> 0.0           // Par
            eco <= economyPar + 1.0 -> -3.0    // Expensive
            else -> -8.0                       // Very expensive
        }

        // Bowling "strike rate" bonus - reward quick wickets
        val bowlingStrikeRate = if (a.wkts > 0) a.ballsBowled.toDouble() / a.wkts else 999.0
        val strikeRateBonus = if (a.ballsBowled >= 12 && a.wkts > 0) {  // Min 2 overs
            val parSR = when {
                overs <= 10 -> 9.0   // Very aggressive formats
                overs <= 20 -> 12.0  // T20 standard
                else -> 18.0         // ODI standard
            }
            kotlin.math.max(0.0, kotlin.math.min(8.0, (parSR - bowlingStrikeRate) / 2.0))
        } else 0.0

        val bowlBase = a.wkts * wicketWeight - runPenalty * a.rcv + economyImpact + strikeRateBonus
        val fiveW = if (a.wkts >= 5) 15.0 else if (a.wkts >= 4) 8.0 else 0.0  // Adjusted
        var bowl = if (a.ballsBowled > 0) bowlBase + fiveW else 0.0

        // Fielding contributions
        val catchPoints = a.catches * 5.0  // 5 points per catch
        val runOutPoints = a.runOuts * 8.0  // 8 points per run-out (more impactful)
        val stumpingPoints = a.stumpings * 8.0  // 8 points per stumping (keeper skill)
        val fieldingScore = catchPoints + runOutPoints + stumpingPoints

        // True balance - equal weighting including fielding
        val base = when {
            a.balls > 0 && a.ballsBowled > 0 -> 0.5 * bat + 0.5 * bowl + fieldingScore  // Perfect balance + fielding
            a.balls > 0 -> bat + fieldingScore
            else -> bowl + fieldingScore
        }

        return if (a.team == winnerTeam) base * 1.10 else base
    }


    // Build impacts once
    val impactsUnsorted: List<PlayerImpact> = aggMap.values.map { a ->
        val impact = scoreAgg(a)
        PlayerImpact(
            id = a.id, name = a.name, team = a.team,
            impact = "%.1f".format(impact).toDouble(),
            summary = summarizeAgg(a),
            isJoker = a.isJoker,
            runs = a.runs, balls = a.balls, fours = a.fours, sixes = a.sixes,
            wickets = a.wkts, runsConceded = a.rcv,
            oversBowled = (a.ballsBowled / 6) + (a.ballsBowled % 6) * 0.1 // Convert to overs.balls format
        )
    }

    val playerImpactsListWithoutJoker: List<PlayerImpact> =
        impactsUnsorted
            .filterNot { it.isJoker }
            .sortedByDescending { it.impact }


    // POTM = top of impact list
    val potm = playerImpactsListWithoutJoker.firstOrNull()

    val matchHistory = MatchHistory(
        team1Name = team1Name,
        team2Name = team2Name,
        jokerPlayerName = jokerPlayerName,
        team1CaptainName = team1CaptainName,
        team2CaptainName = team2CaptainName,
        firstInningsRuns = firstInningsRuns,
        firstInningsWickets = firstInningsWickets,
        secondInningsRuns = secondInningsRuns,
        secondInningsWickets = secondInningsWickets,
        winnerTeam = winnerTeam,
        winningMargin = winningMargin,
        firstInningsBatting = firstInningsBattingStats,
        firstInningsBowling = firstInningsBowlingStats,
        secondInningsBatting = secondInningsBattingStats,
        secondInningsBowling = secondInningsBowlingStats,
        team1Players = firstInningsBattingStats + secondInningsBowlingStats,
        team2Players = firstInningsBowlingStats + secondInningsBattingStats,
        topBatsman = topBatsman,
        topBowler = topBowler,
        matchDate = System.currentTimeMillis(),
        matchSettings = matchSettings,
        groupId = groupId,
        groupName = groupName,
        shortPitch = matchSettings.shortPitch,
        // POTM from impacts
        playerOfTheMatchId = potm?.id,
        playerOfTheMatchName = potm?.name,
        playerOfTheMatchTeam = potm?.team,
        playerOfTheMatchImpact = potm?.impact,
        playerOfTheMatchSummary = potm?.summary,
        playerImpacts = playerImpactsListWithoutJoker,
        allDeliveries = allDeliveries
    )
    return matchHistory
}

@Composable
fun LiveScorecardDialog(
    currentInnings: Int,
    battingTeamName: String,
    battingTeamPlayers: List<Player>,
    bowlingTeamPlayers: List<Player>,
    firstInningsBattingPlayers: List<Player>,
    firstInningsBowlingPlayers: List<Player>,
    firstInningsRuns: Int,
    firstInningsWickets: Int,
    currentRuns: Int,
    currentWickets: Int,
    currentOvers: Int,
    currentBalls: Int,
    totalOvers: Int,
    jokerPlayerName: String,
    striker: Player?,
    nonStriker: Player?,
    bowler: Player?,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val titleColor = cs.onSurface
    val headerContainer = cs.primaryContainer
    val headerOn = cs.onPrimaryContainer
    val sectionTitleColor = cs.primary
    val battingSectionColor = cs.tertiary
    val bowlingSectionColor = cs.tertiary
    val infoContainer = cs.surfaceVariant
    val infoOn = cs.onSurfaceVariant
    val accentContainer = cs.secondaryContainer
    val accentOn = cs.onSecondaryContainer
    val successColor = cs.tertiary
    val warnColor = cs.secondary

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
            Text(
                        "🏏",
                        fontSize = 18.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Column {
                    Text(
                        text = "Live Scorecard",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = titleColor
            )
                    Text(
                        text = "Innings $currentInnings",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.height(500.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Header score panel
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = headerContainer)) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "$battingTeamName - Innings $currentInnings",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = headerOn
                            )
                            Text(
                                text = "$currentRuns/$currentWickets ($currentOvers.$currentBalls/$totalOvers overs)",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = headerOn
                            )
                            if (currentInnings == 2) {
                                val target = firstInningsRuns + 1
                                val required = target - currentRuns
                                val reqText = if (required > 0) "Need $required runs" else "Target achieved!"
                                val reqColor = if (required > 0) warnColor else successColor
                                Text(
                                    text = reqText,
                                    fontSize = 12.sp,
                                    color = reqColor
                                )
                            }
                        }
                    }
                }

                // First innings summary
                if (currentInnings == 2) {
                    item {
                        Text(
                            text = "First Innings Summary",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = sectionTitleColor
                        )
                    }
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = infoContainer)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "${if (battingTeamName == "Team A") "Team B" else "Team A"}: $firstInningsRuns/$firstInningsWickets",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (firstInningsBattingPlayers.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Top Performers:", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = infoOn)

                                    val topBat = firstInningsBattingPlayers.maxByOrNull { it.runs }
                                    val topBowl = firstInningsBowlingPlayers
                                        .filter { it.ballsBowled > 0 }
                                        .maxWithOrNull(
                                            compareBy<Player> { it.wickets }
                                                .thenBy { -(it.runsConceded.toDouble() * 6.0 / it.ballsBowled) } // Economy rate
                                                .thenByDescending { it.ballsBowled }
                                        )

                                    topBat?.let {
                                        Text(
                                            "🏏 ${it.name}: ${it.runs} runs",
                                            fontSize = 11.sp,
                                            color = infoOn
                                        )
                                    }
                                    topBowl?.let {
                                        if (it.wickets > 0) {
                                            Text(
                                                "⚾ ${it.name}: ${it.wickets} wickets",
                                                fontSize = 11.sp,
                                                color = infoOn
                                            )
                                        } else {
                                            val economy = if (it.ballsBowled > 0) (it.runsConceded.toDouble() * 6.0) / it.ballsBowled else 0.0
                                            Text(
                                                "⚾ ${it.name}: Best economy ${"%.1f".format(economy)}",
                                                fontSize = 11.sp,
                                                color = infoOn
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Batting section
                item {
                    Text(
                        text = "Current Innings - Batting",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = battingSectionColor
                    )
                }

                val activeBatsmen = battingTeamPlayers.filter { player ->
                    // Include if they have batting stats
                    val hasStats = player.ballsFaced > 0 || player.runs > 0

                    // OR if they're currently batting (striker/non-striker)
                    val isCurrentlyBatting = player.name == striker?.name || player.name == nonStriker?.name

                    // OR if they're retired (should always be shown)
                    val isRetired = player.isRetired

                    hasStats || (isCurrentlyBatting && !player.isOut) || isRetired
                }.sortedWith(
                    compareBy<Player> { !(it.name == striker?.name || it.name == nonStriker?.name) }
                        .thenByDescending { it.runs }
                )
                if (activeBatsmen.isNotEmpty()) {
                    items(activeBatsmen.sortedByDescending { it.runs }) { player ->
                        LivePlayerStatCard(player, "batting")
                    }
                } else {
                    item {
                        Text(
                            text = "No batting data yet",
                            fontSize = 12.sp,
                            color = infoOn,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }

                val yetToBat = battingTeamPlayers.filter { player ->
                    val hasStats = player.ballsFaced == 0 && player.runs == 0
                    val isCurrentlyBatting = player.name == striker?.name || player.name == nonStriker?.name
                    hasStats && !isCurrentlyBatting && !player.isOut && !player.isRetired
                }
                if (yetToBat.isNotEmpty()) {
                    item {
                        Text(
                            text = "Yet to bat: ${yetToBat.joinToString(", ") { it.name }}",
                            fontSize = 11.sp,
                            color = infoOn,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                // Bowling section
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Current Innings - Bowling",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = bowlingSectionColor
                    )
                }

                val activeBowlers = bowlingTeamPlayers.filter { player ->
                    val hasStats = player.ballsBowled > 0 || player.wickets > 0 || player.runsConceded > 0
                    val isCurrentlyBowling = player.name == bowler?.name
                    hasStats || (isCurrentlyBowling && !player.isOut)
                }.sortedWith(
                    compareBy<Player> { !(it.name == bowler?.name) }
                        .thenByDescending { it.wickets }
                )

                if (activeBowlers.isNotEmpty()) {
                    items(activeBowlers) { player ->
                        LivePlayerStatCard(player, "bowling")
                    }
                } else {
                    item {
                        Text(
                            text = "No bowling data yet",
                            fontSize = 12.sp,
                            color = infoOn,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }

                val didNotBowl = bowlingTeamPlayers.filter { player ->
                    val hasStats = player.ballsBowled == 0 && player.wickets == 0 && player.runsConceded == 0
                    val isCurrentlyBowling = player.name == bowler?.name
                    hasStats && !isCurrentlyBowling && !player.isOut
                }

                if (didNotBowl.isNotEmpty()) {
                    item {
                        Text(
                            text = "Yet to bowl: ${didNotBowl.joinToString(", ") { it.name }}",
                            fontSize = 11.sp,
                            color = infoOn,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                // Joker panel
                if (jokerPlayerName.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = accentContainer)) {
                            val jokerInBatting = battingTeamPlayers.find { it.name == jokerPlayerName && it.isJoker }
                            val jokerInBowling = bowlingTeamPlayers.find { it.name == jokerPlayerName && it.isJoker }
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "🃏 Joker: $jokerPlayerName",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentOn
                                )
                                when {
                                    jokerInBatting != null -> {
                                        Text(
                                            text = "Currently batting: ${jokerInBatting.runs} runs (${jokerInBatting.ballsFaced} balls)",
                                            fontSize = 10.sp,
                                            color = accentOn
                                        )
                                    }
                                    jokerInBowling != null -> {
                                        Text(
                                            text = "Currently bowling: ${jokerInBowling.wickets}/${jokerInBowling.runsConceded} (${"%.1f".format(jokerInBowling.oversBowled)} overs)",
                                            fontSize = 10.sp,
                                            color = accentOn
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = "Available for both teams",
                                            fontSize = 10.sp,
                                            color = accentOn
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}


@Composable
fun LivePlayerStatCard(
    player: Player,
    type: String,
) {
    val cs = MaterialTheme.colorScheme
    val primaryTextColor = cs.onSurface
    val jokerColor = cs.secondary
    val secondaryTextColor = cs.onSurfaceVariant
    val bowlingStatsColor = cs.tertiary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (player.isJoker) "🃏 ${player.name}" else player.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            color = if (player.isJoker) jokerColor else primaryTextColor,
        )
        when (type) {
            "batting" -> {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${player.runs}${if (!player.isOut && player.ballsFaced > 0) "*" else ""} (${player.ballsFaced})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = primaryTextColor,
                    )
                    if (player.fours > 0 || player.sixes > 0) {
                        Text(
                            text = "4s:${player.fours} 6s:${player.sixes}",
                            fontSize = 10.sp,
                            color = secondaryTextColor,
                        )
                    }
                }
            }
            "bowling" -> {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${player.wickets}/${player.runsConceded}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = bowlingStatsColor,
                    )
                    Text(
                        text = "${"%.1f".format(player.oversBowled)} ov, Eco: ${"%.1f".format(player.economy)}",
                        fontSize = 10.sp,
                        color = secondaryTextColor,
                    )
                }
            }
        }
    }
}


@Composable
fun EnhancedInningsBreakDialog(
    runs: Int, wickets: Int, overs: Int, balls: Int,
    battingTeam: String,
    battingPlayers: List<Player>, bowlingPlayers: List<Player>,
    totalOvers: Int,
    onStartSecondInnings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { },
        confirmButton = {
            Button(
                onClick = onStartSecondInnings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start 2nd Innings", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        "🏏",
                        fontSize = 32.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "First Innings Complete",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
// Summary banner
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(battingTeam, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                            Text("$runs/$wickets ($overs.$balls/$totalOvers ov)", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
// Batting top 3
                item { Text("Top Batting", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary) }
                items(battingPlayers.sortedByDescending { it.runs }.take(3)) { p ->
                    StatRowCompact(name = p.name, left = "${p.runs}${if (!p.isOut && p.ballsFaced > 0) "*" else ""} (${p.ballsFaced})", right = "4s:${p.fours} 6s:${p.sixes}")
                }
// Bowling top 3
                item { Text("Top Bowling", style = MaterialTheme.typography.titleSmall, color = Color(0xFFFF5722)) }
                items(bowlingPlayers.sortedByDescending { it.wickets }.take(3)) { p ->
                    StatRowCompact(name = p.name, left = "${p.wickets}/${p.runsConceded}", right = "${"%.1f".format(p.oversBowled)} ov - Eco ${"%.1f".format(p.economy)}")
                }
            }
        }
    )
}

@Composable
private fun StatRowCompact(name: String, left: String, right: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(left, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(right, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExtrasDialog(
    matchSettings: MatchSettings,
    onExtraSelected: (ExtraType, Int) -> Unit,
    onDismiss: () -> Unit,
    striker: Player?,
    nonStriker: Player?,
    onWideWithStumping: ((ExtraType, Int) -> Unit)? = null
) {
    var selectedExtraType by remember { mutableStateOf<ExtraType?>(null) }

    // No-ball subflow state
    var showNoBallOutcomeStep by remember { mutableStateOf(false) }
    var showRunOutOnNoBall by remember { mutableStateOf(false) }
    var showBoundaryOutOnNoBall by remember { mutableStateOf(false) }
    
    // Wide subflow state
    var showWideOutcomeStep by remember { mutableStateOf(false) }

    fun resetNoBallHolders() {
        NoBallOutcomeHolders.noBallSubOutcome.value = NoBallSubOutcome.NONE
        NoBallOutcomeHolders.noBallRunOutInput.value = null
        NoBallOutcomeHolders.noBallBoundaryOutInput.value = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "🏏",
                        fontSize = 20.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Text("Extras", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            when {
                // STEP 1 — Type
                selectedExtraType == null -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Select extra type",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )

                        ExtraType.entries.forEach { extraType ->
                            val baseRuns = when (extraType) {
                                ExtraType.OFF_SIDE_WIDE -> matchSettings.wideRuns
                                ExtraType.LEG_SIDE_WIDE  -> matchSettings.wideRuns
                                ExtraType.NO_BALL        -> matchSettings.noballRuns
                                ExtraType.BYE            -> matchSettings.byeRuns
                                ExtraType.LEG_BYE        -> matchSettings.legByeRuns
                            }
                            // Emphasis: filled for most common, tonal for others
                            val isPrimary = extraType == ExtraType.NO_BALL || extraType == ExtraType.OFF_SIDE_WIDE || extraType == ExtraType.LEG_SIDE_WIDE

                            if (isPrimary) {
                            Button(
                                onClick = {
                                    selectedExtraType = extraType
                                    showNoBallOutcomeStep = (extraType == ExtraType.NO_BALL)
                                    showWideOutcomeStep = (extraType == ExtraType.OFF_SIDE_WIDE || extraType == ExtraType.LEG_SIDE_WIDE)
                                    if (!showNoBallOutcomeStep) resetNoBallHolders()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(extraType.displayName, fontWeight = FontWeight.SemiBold)
                                        Surface(
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                "+$baseRuns",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = {
                                        selectedExtraType = extraType
                                        showNoBallOutcomeStep = (extraType == ExtraType.NO_BALL)
                                        showWideOutcomeStep = (extraType == ExtraType.OFF_SIDE_WIDE || extraType == ExtraType.LEG_SIDE_WIDE)
                                        if (!showNoBallOutcomeStep) resetNoBallHolders()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(extraType.displayName, fontWeight = FontWeight.Medium)
                                        Text("+$baseRuns", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // STEP 2 — No-ball outcome
                selectedExtraType == ExtraType.NO_BALL && showNoBallOutcomeStep -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("No-ball outcome", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        // None
                        FilledTonalButton(
                            onClick = {
                                NoBallOutcomeHolders.noBallSubOutcome.value = NoBallSubOutcome.NONE
                                showNoBallOutcomeStep = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Just additional runs") }

                        // Run out
                        Button(
                            onClick = {
                                NoBallOutcomeHolders.noBallSubOutcome.value = NoBallSubOutcome.RUN_OUT
                                showRunOutOnNoBall = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Run out on No ball") }

                        // Boundary out
                        FilledTonalButton(
                            onClick = {
                                NoBallOutcomeHolders.noBallSubOutcome.value = NoBallSubOutcome.BOUNDARY_OUT
                                NoBallOutcomeHolders.noBallBoundaryOutInput.value = NoBallBoundaryOutInput(outBatterName = null)
                                // Immediately finalize: Boundary Out has no extra runs UI
                                val base = matchSettings.noballRuns
                                onExtraSelected(ExtraType.NO_BALL, base)   // <-- fire the scoring callback now
                                showBoundaryOutOnNoBall = false
                                showNoBallOutcomeStep = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) { Text("Boundary out on No ball") }

                        if (showRunOutOnNoBall) {
                            RunOutDialog(
                                striker = striker,
                                nonStriker = nonStriker,
                                onConfirm = { input ->
                                    NoBallOutcomeHolders.noBallRunOutInput.value = input
                                    showRunOutOnNoBall = false
                                    showNoBallOutcomeStep = false
                                },
                                onDismiss = { showRunOutOnNoBall = false }
                            )
                        }
                    }
                }
                
                // STEP 2.5 — Wide outcome
                (selectedExtraType == ExtraType.OFF_SIDE_WIDE || selectedExtraType == ExtraType.LEG_SIDE_WIDE) && showWideOutcomeStep -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Wide ball outcome", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        
                        // Clean wide (no wicket)
                        FilledTonalButton(
                            onClick = {
                                showWideOutcomeStep = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Clean wide (runs only)") }
                        
                        // Stumped on wide
                        Button(
                            onClick = {
                                // Will be handled in the calling code
                                val baseRuns = matchSettings.wideRuns
                                onWideWithStumping?.invoke(selectedExtraType!!, baseRuns)
                                showWideOutcomeStep = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text("Stumped on Wide") }
                    }
                }
                
                // STEP 3 — Runs
                else -> {
                    val baseRuns = when (selectedExtraType!!) {
                        ExtraType.OFF_SIDE_WIDE -> matchSettings.wideRuns
                        ExtraType.LEG_SIDE_WIDE -> matchSettings.wideRuns
                        ExtraType.NO_BALL       -> matchSettings.noballRuns
                        ExtraType.BYE           -> matchSettings.byeRuns
                        ExtraType.LEG_BYE       -> matchSettings.legByeRuns
                    }
                    val isBoundaryOut =
                        selectedExtraType == ExtraType.NO_BALL &&
                                NoBallOutcomeHolders.noBallSubOutcome.value == NoBallSubOutcome.BOUNDARY_OUT

                    if (isBoundaryOut) {
                        // Do nothing here; selection will be finalized in onExtraSelected handler that already logs Nb+base
                        Text("No ball + Boundary out recorded", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        return@AlertDialog
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${selectedExtraType!!.displayName} · pick additional runs",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Horizontal wrap of compact buttons per M3
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = 5
                        ) {
                            // Before building buttons:
                            val additionalOptions =
                                if (selectedExtraType == ExtraType.NO_BALL) (0..6).toList()
                                else (0..4).toList() // cap at 4 for Wd, B, Lb

                            // Then use additionalOptions instead of (0..6)
                            additionalOptions.forEach { add ->
                                val total = baseRuns + add
                                val colors =
                                    when (add) {
                                        0 -> ButtonDefaults.filledTonalButtonColors()
                                        4 -> ButtonDefaults.buttonColors()
                                        6 -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                        else -> ButtonDefaults.outlinedButtonColors()
                                    }
                                val shape = RoundedCornerShape(12.dp)
                                val btn: @Composable (@Composable () -> Unit) -> Unit =
                                    if (add in listOf(1,2,3,5)) {
                                        { content -> OutlinedButton(
                                            onClick = { onExtraSelected(selectedExtraType!!, total) },
                                            shape = shape) { content() } }
                                    } else {
                                        { content -> Button(
                                            onClick = { onExtraSelected(selectedExtraType!!, total) },
                                            colors = colors,
                                            shape = shape) { content() } }
                                    }
                                btn { Text("+$add = $total") }
                            }
                        }
                    }
                }
            }
        },
        // Buttons per M3: primary action right, secondary left
        confirmButton = {
            // Primary: Continue or Apply depending on step
            val label = when {
                selectedExtraType == null -> "Cancel"
                selectedExtraType == ExtraType.NO_BALL && showNoBallOutcomeStep -> "Back"
                else -> "Back"
            }
            TextButton(onClick = {
                when {
                    selectedExtraType == null -> onDismiss()
                    selectedExtraType == ExtraType.NO_BALL && showNoBallOutcomeStep -> {
                        showNoBallOutcomeStep = false
                        resetNoBallHolders()
                        selectedExtraType = null
                    }
                    else -> {
                        // Back from runs to types
                        resetNoBallHolders()
                        selectedExtraType = null
                    }
                }
            }) { Text(label) }
        },
        dismissButton = if (selectedExtraType != null) {
            // Only show Cancel when user is in the middle of a workflow
            {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        } else {
            // No dismiss button on the initial screen since "Close" handles it
            null
        }
    )
}

@Composable
fun QuickWideDialog(
    matchSettings: MatchSettings,
    onWideConfirmed: (Int) -> Unit,
    onStumpingOnWide: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var showOutcomeStep by remember { mutableStateOf(true) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        "🏏",
                        fontSize = 18.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Text("Wide Ball", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            if (showOutcomeStep) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select outcome", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    FilledTonalButton(
                        onClick = { showOutcomeStep = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Just runs") }
                    
                    Button(
                        onClick = {
                            onStumpingOnWide(matchSettings.wideRuns)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Stumped on Wide") }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Runs by batsmen (wide penalty added automatically)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(0, 1, 2, 3, 4).forEach { batsmenRuns ->
                            val totalRuns = matchSettings.wideRuns + batsmenRuns
                            Button(
                                onClick = { onWideConfirmed(totalRuns) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) { 
                                Text(
                                    text = "$batsmenRuns",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun QuickNoBallDialog(
    matchSettings: MatchSettings,
    striker: Player?,
    nonStriker: Player?,
    onNoBallConfirmed: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var showOutcomeStep by remember { mutableStateOf(true) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("No-ball", style = MaterialTheme.typography.titleLarge) },
        text = {
            if (showOutcomeStep) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select outcome", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    FilledTonalButton(
                        onClick = { showOutcomeStep = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Just additional runs") }
                    
                    Text(
                        "Note: For run-out or boundary-out on no-ball, use 'More Extras' button",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Runs by batsmen (no-ball penalty added automatically)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val maxBatsmenRuns = if (matchSettings.shortPitch) 4 else 6
                        listOf(0, 1, 2, 3, 4, maxBatsmenRuns).distinct().forEach { batsmenRuns ->
                            val totalRuns = matchSettings.noballRuns + batsmenRuns
                            Button(
                                onClick = { onNoBallConfirmed(totalRuns) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) { 
                                Text(
                                    text = "$batsmenRuns",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun WicketTypeDialog(
    onWicketSelected: (WicketType) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp).size(20.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            Text(
                    text = "How was the batter out?",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(WicketType.values()) { wicketType ->
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onWicketSelected(wicketType) },
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                        )
                    ) {
                        Row(
                        modifier = Modifier
                            .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                                text = wicketType.name.lowercase().replace("_", " ")
                                    .replaceFirstChar { it.uppercase() },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontWeight = FontWeight.Medium)
            }
        },
    )
}

/**
 * Merge Joker batting + bowling rows into a single Player row for this innings.
 * Ensures only one Joker row exists per team.
 */
private fun mergeJokerForInnings(
    batting: List<Player>,
    bowling: List<Player>,
    jokerName: String?
): Pair<List<Player>, List<Player>> {
    if (jokerName.isNullOrBlank()) return batting to bowling

    val batJoker = batting.find { it.isJoker && it.name == jokerName }
    val bowlJoker = bowling.find { it.isJoker && it.name == jokerName }

    // If Joker not present in either list, return as-is
    if (batJoker == null && bowlJoker == null) return batting to bowling

    // Base row: prefer batting row so runs/balls/isOut come from there
    val base = batJoker ?: bowlJoker!!

    // Merge: keep batting stats from batJoker, bowling stats from bowlJoker
    val merged = base.copy(
        runs = batJoker?.runs ?: 0,
        ballsFaced = batJoker?.ballsFaced ?: 0,
        fours = batJoker?.fours ?: 0,
        sixes = batJoker?.sixes ?: 0,

        wickets = bowlJoker?.wickets ?: 0,
        runsConceded = bowlJoker?.runsConceded ?: 0,
        // or ballsBowled depending on your Player model
        ballsBowled = bowlJoker?.ballsBowled ?: 0,

        isJoker = true,
        isOut = batJoker?.isOut ?: base.isOut,
        
        // Preserve dismissal information from batting record
        dismissalType = batJoker?.dismissalType,
        bowlerName = batJoker?.bowlerName,
        fielderName = batJoker?.fielderName,
        
        // Preserve fielding stats
        catches = (batJoker?.catches ?: 0) + (bowlJoker?.catches ?: 0),
        runOuts = (batJoker?.runOuts ?: 0) + (bowlJoker?.runOuts ?: 0),
        stumpings = (batJoker?.stumpings ?: 0) + (bowlJoker?.stumpings ?: 0)
    )

    // Build new lists: remove any old Joker rows, then add merged to batting list
    val newBatting = batting.filterNot { it.isJoker && it.name == jokerName }.toMutableList()
    val newBowling = bowling.filterNot { it.isJoker && it.name == jokerName }.toMutableList()

    // Add merged Joker row only once to batting list
    newBatting.add(merged)

    return newBatting to newBowling
}

@Composable
fun EnhancedMatchCompleteDialog(
    matchId: String,
    firstInningsRuns: Int,
    firstInningsWickets: Int,
    secondInningsRuns: Int,
    secondInningsWickets: Int,
    team1Name: String,
    team2Name: String,
    jokerPlayerName: String?,
    team1CaptainName: String? = null,
    team2CaptainName: String? = null,
    firstInningsBattingPlayers: List<Player> = emptyList(),
    firstInningsBowlingPlayers: List<Player> = emptyList(),
    secondInningsBattingPlayers: List<Player> = emptyList(),
    secondInningsBowlingPlayers: List<Player> = emptyList(),
    onNewMatch: () -> Unit,
    onDismiss: () -> Unit,
    matchSettings: MatchSettings,
    groupId: String?,
    groupName: String?,
    scope: CoroutineScope,
    repo : MatchRepository,
    inProgressManager: InProgressMatchManager,
    allDeliveries: List<DeliveryUI> = emptyList()
) {
    val context = LocalContext.current

    // Result computation (tie-safe)
    val isTie = secondInningsRuns == firstInningsRuns
    val chasingWon = secondInningsRuns > firstInningsRuns
    val winner: String? = when {
        isTie -> null
        chasingWon -> team2Name
        else -> team1Name
    }
    val margin: String? = when {
        isTie -> null
        chasingWon -> "${calculateWicketMargin(secondInningsWickets)} wickets"
        else -> "${firstInningsRuns - secondInningsRuns} runs"
    }

    val (teamABatMerged, teamABowlClean) = mergeJokerForInnings(
        batting = firstInningsBattingPlayers,
        bowling = secondInningsBowlingPlayers,
        jokerName = jokerPlayerName
    )

    val (teamBBatMerged, teamBBowlClean) = mergeJokerForInnings(
        batting = secondInningsBattingPlayers,
        bowling = firstInningsBowlingPlayers,
        jokerName = jokerPlayerName
    )

    // build stats for DB
    val firstInningsBattingStats = teamABatMerged.map { it.toMatchStats(team1Name) }
    val firstInningsBowlingStats = teamBBowlClean.map { it.toMatchStats(team2Name) }

    val secondInningsBattingStats = teamBBatMerged.map { it.toMatchStats(team2Name) }
    val secondInningsBowlingStats = teamABowlClean.map { it.toMatchStats(team1Name) }


    val finalGroupId = groupId ?: "1"
    val finalGroupName = groupName ?: "Default"

    var isMatchSaved by remember { mutableStateOf(false) }

    // Save history (tie-friendly placeholders)
    LaunchedEffect(Unit) {
        val match = saveMatchToHistory(
            team1Name = team1Name,
            team2Name = team2Name,
            jokerPlayerName = jokerPlayerName,
            team1CaptainName = team1CaptainName,
            team2CaptainName = team2CaptainName,
            firstInningsRuns = firstInningsRuns,
            firstInningsWickets = firstInningsWickets,
            secondInningsRuns = secondInningsRuns,
            secondInningsWickets = secondInningsWickets,
            winnerTeam = winner ?: "TIE",
            winningMargin = margin ?: "Scores level",
            firstInningsBattingStats = firstInningsBattingStats,
            firstInningsBowlingStats = firstInningsBowlingStats,
            secondInningsBattingStats = secondInningsBattingStats,
            secondInningsBowlingStats = secondInningsBowlingStats,
            context = context,
            matchSettings = matchSettings,
            groupId = finalGroupId,
            groupName = finalGroupName,
            allDeliveries = allDeliveries.toList()
        )
        scope.launch {
            repo.saveMatch(match)
            inProgressManager.clearMatch() // Clear saved match since it's now completed
            
            // Clean up shared match from live matches
            try {
                val sharingManager = com.oreki.stumpd.data.sync.sharing.MatchSharingManager()
                sharingManager.revokeShare(match.id)
                android.util.Log.d("MatchComplete", "Removed match from shared_matches")
            } catch (e: Exception) {
                android.util.Log.e("MatchComplete", "Failed to cleanup share (non-critical)", e)
            }
            
            isMatchSaved = true
            val total = repo.getAllMatches().size
            Toast.makeText(
                context,
                "Match saved! Total: $total matches 🏏",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "🏆 Match Complete!",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        text = {
            LazyColumn {
                // Result banner
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E8))) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = if (isTie) "Match Tied" else "${winner} won by ${margin}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isTie) Color(0xFF6A1B9A) else MaterialTheme.colorScheme.primary,
                            )

                            if (isTie && matchSettings.enableSuperOver) {
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { /* trigger super over flow */ },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
                                ) {
                                    Text("Start Super Over")
                                }
                            }
                        }
                    }
                }

                // Team 1 summary
                item {
                    Text(
                        text = "$team1Name - 1st Innings: $firstInningsRuns/$firstInningsWickets",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                item {
                    Text(
                        text = "Batting",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                items(firstInningsBattingPlayers.sortedByDescending { it.runs }) { player ->
                    PlayerStatCard(player, "batting")
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Bowling",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFFF5722),
                    )
                }
                items(firstInningsBowlingPlayers.sortedByDescending { it.wickets }) { player ->
                    PlayerStatCard(player, "bowling")
                }

                // Team 2 summary
                item { Spacer(modifier = Modifier.height(16.dp)) }
                item {
                    Text(
                        text = "$team2Name - 2nd Innings: $secondInningsRuns/$secondInningsWickets",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                item {
                    Text(
                        text = "Batting",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                items(secondInningsBattingPlayers.sortedByDescending { it.runs }) { player ->
                    PlayerStatCard(player, "batting")
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Bowling",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFFF5722),
                    )
                }
                items(secondInningsBowlingPlayers.sortedByDescending { it.wickets }) { player ->
                    PlayerStatCard(player, "bowling")
                }

                // Joker performance (if present)
                jokerPlayerName?.let { jokerName ->
                    val jokerFirstInningsBat = firstInningsBattingPlayers.find { it.name == jokerName }
                    val jokerFirstInningsBowl = firstInningsBowlingPlayers.find { it.name == jokerName }
                    val jokerSecondInningsBat = secondInningsBattingPlayers.find { it.name == jokerName }
                    val jokerSecondInningsBowl = secondInningsBowlingPlayers.find { it.name == jokerName }

                    if (jokerFirstInningsBat != null || jokerFirstInningsBowl != null ||
                        jokerSecondInningsBat != null || jokerSecondInningsBowl != null
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "🃏 Joker Performance: $jokerName",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                    val totalRuns = (jokerFirstInningsBat?.runs ?: 0) + (jokerSecondInningsBat?.runs ?: 0)
                                    val totalWickets = (jokerFirstInningsBowl?.wickets ?: 0) + (jokerSecondInningsBowl?.wickets ?: 0)
                                    Text(
                                        text = "Total: $totalRuns runs, $totalWickets wickets",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onNewMatch,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Text("New Match") }
        },
        dismissButton = {
            Button(
                onClick = {
                    if (isMatchSaved) {
                    val intent = Intent(context, FullScorecardActivity::class.java)
                    intent.putExtra("match_id", matchId)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    context.startActivity(intent)
                    (context as ComponentActivity).finish()
                    } else {
                        Toast.makeText(context, "Please wait, saving match...", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = isMatchSaved,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isMatchSaved) {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                Text("View Details")
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Saving...")
                }
            }
        },
    )
}

@Composable
fun PlayerStatCard(
    player: Player,
    type: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = if (player.isJoker) "🃏 ${player.name}" else player.name,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        when (type) {
            "batting" -> {
                Text(
                    text = "${player.runs}${if (!player.isOut && player.ballsFaced > 0) "*" else ""} (${player.ballsFaced}) - 4s:${player.fours} 6s:${player.sixes}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            "bowling" -> {
                Text(
                    text = "${player.wickets}/${player.runsConceded} (${"%.1f".format(player.oversBowled)} ov) Eco: ${"%.1f".format(player.economy)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

fun calculateWicketMargin(wicketsLost: Int): Int {
    return 10 - wicketsLost
}

@Composable
fun EnhancedPlayerSelectionDialog(
    title: String,
    players: List<Player>,
    jokerPlayer: Player? = null,
    currentStrikerIndex: Int? = null,
    currentNonStrikerIndex: Int? = null,
    allowSingleSide: Boolean = false,
    totalWickets: Int = 0, // Add this parameter
    battingTeamPlayers: List<Player> = emptyList(), // Add this parameter
    bowlingTeamPlayers: List<Player> = emptyList(), // Add this parameter
    jokerOversThisInnings: Double = 0.0,
    jokerOutInCurrentInnings: Boolean = false,
    onPlayerSelected: (Player) -> Unit,
    onDismiss: () -> Unit,
    matchSettings: MatchSettings,
    otherEndName: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp).size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.height(300.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val availablePlayers = players.filterIndexed { index, player ->
                    // Exclude jokers from the main list since they're handled separately
                    if (player.isJoker) return@filterIndexed false
                    // Allow retired players to be shown (they can return), but not out players
                    if (player.isOut && !player.isRetired) return@filterIndexed false
                    val pickingStriker = title.contains("Striker", ignoreCase = true) && !title.contains("Non", ignoreCase = true)
                    val pickingNonStriker = title.contains("Non-Striker", ignoreCase = true)

                    fun sameName(a: String?, b: String?) =
                        a != null && b != null && a.trim().equals(b.trim(), ignoreCase = true)

                    if (pickingStriker) {
                        val excludeByIndex = (index == currentNonStrikerIndex)
                        val excludeByName = sameName(player.name, otherEndName)
                        !(excludeByIndex || excludeByName)
                    } else if (pickingNonStriker) {
                        val excludeByIndex = (index == currentStrikerIndex)
                        val excludeByName = sameName(player.name, otherEndName)
                        !(excludeByIndex || excludeByName)
                    } else {
                        true
                    }
                }


                items(availablePlayers) { player ->
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onPlayerSelected(player) },
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (player.isJoker)
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                            else
                                MaterialTheme.colorScheme.surface
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (player.isJoker)
                                            MaterialTheme.colorScheme.tertiary
                                        else
                                            MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = player.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (player.isJoker) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = MaterialTheme.colorScheme.tertiaryContainer
                                        ) {
                                            Text(
                                                "🃏",
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                            if (title.contains("Striker", ignoreCase = true)) {
                                if (player.ballsFaced > 0 || player.runs > 0) {
                                    Text(
                                            text = "${player.runs}${if (!player.isOut && player.ballsFaced > 0) "*" else ""} (${player.ballsFaced}) • SR: ${"%.1f".format(player.strikeRate)}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (player.fours > 0 || player.sixes > 0) {
                                        Text(
                                                text = "4s: ${player.fours} • 6s: ${player.sixes}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else {
                                    Text(
                                            text = if (player.isJoker) "Available for both teams" else "Yet to bat",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontStyle = FontStyle.Italic,
                                    )
                                }
                            }
                            if (title.contains("Bowler", ignoreCase = true)) {
                                if (player.ballsBowled > 0 || player.wickets > 0 || player.runsConceded > 0) {
                                    Text(
                                            text = "${player.wickets}/${player.runsConceded} (${"%.1f".format(player.oversBowled)} ov) • Eco: ${"%.1f".format(player.economy)}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                            text = if (player.isJoker) "Available for both teams" else "Yet to bowl",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontStyle = FontStyle.Italic,
                                    )
                                }
                            }
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // FIXED: Proper joker availability logic based on rules
                jokerPlayer?.let { joker ->
                    val showJoker = when {
                        title.contains("Striker", ignoreCase = true) -> {
                            // Joker can bat only if he’s not already in the batting side or is marked out
                            val jokerInBatting = battingTeamPlayers.any { it.isJoker && !it.isOut }
                            val wicketsFallen = totalWickets > 0
                            !jokerInBatting && !jokerOutInCurrentInnings && wicketsFallen
                        }
                        title.contains("Bowler", ignoreCase = true) -> {
                            // Joker can bowl only if he’s not currently batting
                            val jokerCurrentlyBatting = battingTeamPlayers.any { it.isJoker && !it.isOut }
                            val withinCap = jokerOversThisInnings < matchSettings.jokerMaxOvers
                            !jokerCurrentlyBatting && withinCap
                        }
                        else -> false
                    }

                    if (showJoker) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onPlayerSelected(joker) },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                ),
                                elevation = CardDefaults.cardElevation(4.dp)
                            ) {
                                Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.tertiary
                                        ) {
                                    Text(
                                                "🃏",
                                                fontSize = 18.sp,
                                                modifier = Modifier.padding(6.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = joker.name,
                                        fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        text = if (title.contains("Striker", ignoreCase = true))
                                                    "Available to bat"
                                        else
                                                    "Available to bowl",
                                        fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                                                fontStyle = FontStyle.Italic
                                            )
                                        }
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                // Show empty state only if no regular players and no joker available
                if (availablePlayers.isEmpty() &&
                    (jokerPlayer == null ||
                            (title.contains("Striker", ignoreCase = true) &&
                                    (!jokerPlayer.let { joker ->
                                        val notInBattingTeam = !battingTeamPlayers.any { it.isJoker }
                                        val notOut = !joker.isOut
                                        val wicketsFallen = totalWickets > 0
                                        val notOpeningPair = !(totalWickets == 0 && title.contains("First", ignoreCase = true))
                                        notInBattingTeam && notOut && (wicketsFallen || notOpeningPair)
                                    })))) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        ) {
                            Text(
                                text = if (title.contains("Striker", ignoreCase = true)) {
                                    if (allowSingleSide) "All batsmen are out" else "No available batsmen"
                                } else {
                                    "No available bowlers"
                                },
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun RunOutDialog(
    striker: Player?,
    nonStriker: Player?,
    onConfirm: (RunOutInput) -> Unit,
    onDismiss: () -> Unit
) {
    var runsCompleted by remember { mutableStateOf(0) }
    var selectedWho by remember { mutableStateOf<Player?>(null) }
    var selectedEnd by remember { mutableStateOf<RunOutEnd?>(null) }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp).size(20.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Text(
                    "Run Out",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Runs Completed (0–3)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Runs completed before wicket:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        (0..3).forEach { run ->
                            OutlinedCard(
                                modifier = Modifier.weight(1f),
                                onClick = { runsCompleted = run },
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = if (runsCompleted == run)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else
                                        MaterialTheme.colorScheme.surface
                                ),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    width = if (runsCompleted == run) 2.dp else 1.dp
                                )
                            ) {
                                Text(
                                    text = "$run",
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    fontWeight = if (runsCompleted == run) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Who got out (actual player names)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Who got out?",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    striker?.let {
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { selectedWho = striker },
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = if (selectedWho == striker)
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                    else
                                        MaterialTheme.colorScheme.surface
                                ),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    width = if (selectedWho == striker) 2.dp else 1.dp
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            it.name,
                                            fontWeight = if (selectedWho == striker) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }
                                    if (selectedWho == striker) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                        }
                    }
                    nonStriker?.let {
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { selectedWho = nonStriker },
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = if (selectedWho == nonStriker)
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                    else
                                        MaterialTheme.colorScheme.surface
                                ),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    width = if (selectedWho == nonStriker) 2.dp else 1.dp
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            it.name,
                                            fontWeight = if (selectedWho == nonStriker) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }
                                    if (selectedWho == nonStriker) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // End of wicket
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "At which end?",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedCard(
                            modifier = Modifier.weight(1f),
                            onClick = { selectedEnd = RunOutEnd.STRIKER_END },
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (selectedEnd == RunOutEnd.STRIKER_END)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else
                                    MaterialTheme.colorScheme.surface
                            ),
                            border = CardDefaults.outlinedCardBorder().copy(
                                width = if (selectedEnd == RunOutEnd.STRIKER_END) 2.dp else 1.dp
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Striker's",
                                    fontWeight = if (selectedEnd == RunOutEnd.STRIKER_END) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "End",
                                    fontWeight = if (selectedEnd == RunOutEnd.STRIKER_END) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        OutlinedCard(
                            modifier = Modifier.weight(1f),
                            onClick = { selectedEnd = RunOutEnd.NON_STRIKER_END },
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (selectedEnd == RunOutEnd.NON_STRIKER_END)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else
                                    MaterialTheme.colorScheme.surface
                            ),
                            border = CardDefaults.outlinedCardBorder().copy(
                                width = if (selectedEnd == RunOutEnd.NON_STRIKER_END) 2.dp else 1.dp
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Non-Striker's",
                                    fontWeight = if (selectedEnd == RunOutEnd.NON_STRIKER_END) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "End",
                                    fontWeight = if (selectedEnd == RunOutEnd.NON_STRIKER_END) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val input = RunOutInput(
                        runsCompleted = runsCompleted,
                        whoOut = selectedWho?.name ?: "",
                        end = selectedEnd ?: RunOutEnd.STRIKER_END
                    )
                    onConfirm(input)
                },
                enabled = selectedWho != null && selectedEnd != null,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Confirm", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onDismiss() },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel", fontWeight = FontWeight.Medium)
            }
        }
    )
}

@Composable
fun LiveScoreTab(
    modifier: Modifier = Modifier,
    battingTeamName: String,
    currentInnings: Int,
    matchSettings: MatchSettings,
    calculatedTotalRuns: Int,
    totalWickets: Int,
    currentOver: Int,
    ballsInOver: Int,
    totalExtras: Int,
    battingTeamPlayers: List<Player>,
    firstInningsRuns: Int,
    showSingleSideLayout: Boolean,
    striker: Player?,
    nonStriker: Player?,
    bowler: Player?,
    availableBatsmen: Int,
    currentBowlerSpell: Int,
    jokerPlayer: Player?,
    currentOverDeliveries: List<DeliveryUI>,
    isInningsComplete: Boolean,
    isPowerplayActive: Boolean,
    context: android.content.Context,
    onSelectStriker: () -> Unit,
    onSelectNonStriker: () -> Unit,
    onSelectBowler: () -> Unit,
    onSwapStrike: () -> Unit,
    onScoreRuns: (Int) -> Unit,
    onShowExtras: () -> Unit,
    onShowWicket: () -> Unit,
    onUndo: () -> Unit,
    onWide: () -> Unit,
    onRetire: () -> Unit,
    unlimitedUndoEnabled: Boolean,
    onToggleUnlimitedUndo: (Boolean) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(2.dp))
        ScoreHeaderCard(
            battingTeamName = battingTeamName,
            currentInnings = currentInnings,
            matchSettings = matchSettings,
            calculatedTotalRuns = calculatedTotalRuns,
            totalWickets = totalWickets,
            currentOver = currentOver,
            ballsInOver = ballsInOver,
            totalExtras = totalExtras,
            battingTeamPlayers = battingTeamPlayers,
            firstInningsRuns = firstInningsRuns,
            isPowerplayActive = isPowerplayActive
        )

        Spacer(modifier = Modifier.height(4.dp))

        PlayersCard(
            showSingleSideLayout = showSingleSideLayout,
            striker = striker,
            nonStriker = nonStriker,
            bowler = bowler,
            availableBatsmen = availableBatsmen,
            onSelectStriker = onSelectStriker,
            onSelectNonStriker = onSelectNonStriker,
            onSelectBowler = onSelectBowler,
            onSwapStrike = onSwapStrike,
            currentBowlerSpell = currentBowlerSpell,
            jokerPlayer = jokerPlayer,
        )

        Spacer(modifier = Modifier.height(4.dp))

        ScoringButtons(
            striker = striker,
            nonStriker = nonStriker,
            bowler = bowler,
            isInningsComplete = isInningsComplete,
            matchSettings = matchSettings,
            availableBatsmen = availableBatsmen,
            calculatedTotalRuns = calculatedTotalRuns,
            onScoreRuns = onScoreRuns,
            onShowExtras = onShowExtras,
            onShowWicket = onShowWicket,
            onUndo = onUndo,
            onWide = onWide,
            onRetire = onRetire,
            unlimitedUndoEnabled = unlimitedUndoEnabled,
            onToggleUnlimitedUndo = onToggleUnlimitedUndo
        )

        if (currentOverDeliveries.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    Text(
                        "Over ${currentOver + 1}:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(2.dp))
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(currentOverDeliveries.size) { index ->
                            val d = currentOverDeliveries[index]
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = when {
                                    d.outcome == "W" -> MaterialTheme.colorScheme.errorContainer
                                    d.highlight -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                modifier = Modifier.defaultMinSize(minWidth = 32.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        d.outcome,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = when {
                                            d.outcome == "W" -> MaterialTheme.colorScheme.onErrorContainer
                                            d.highlight -> MaterialTheme.colorScheme.onTertiaryContainer
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScorecardTab(
    modifier: Modifier = Modifier,
    currentInnings: Int,
    battingTeamName: String,
    bowlingTeamName: String,
    battingTeamPlayers: List<Player>,
    bowlingTeamPlayers: List<Player>,
    completedBattersInnings1: List<Player>,
    completedBattersInnings2: List<Player>,
    completedBowlersInnings1: List<Player>,
    completedBowlersInnings2: List<Player>,
    firstInningsBattingPlayersList: List<Player>,
    firstInningsBowlingPlayersList: List<Player>,
    currentPartnerships: List<Partnership> = emptyList(),
    firstInningsPartnerships: List<Partnership> = emptyList(),
    currentFallOfWickets: List<FallOfWicket> = emptyList(),
    firstInningsFallOfWickets: List<FallOfWicket> = emptyList(),
    striker: Player? = null,
    nonStriker: Player? = null,
    currentPartnershipRuns: Int = 0,
    currentPartnershipBalls: Int = 0,
    currentPartnershipBatsman1Runs: Int = 0,
    currentPartnershipBatsman2Runs: Int = 0,
    currentPartnershipBatsman1Balls: Int = 0,
    currentPartnershipBatsman2Balls: Int = 0,
    currentPartnershipBatsman1Name: String? = null,
    currentPartnershipBatsman2Name: String? = null
) {
    var currentInningsExpanded by remember { mutableStateOf(true) }
    var firstInningsExpanded by remember { mutableStateOf(false) }
    
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Current Innings - Collapsible
        item {
            InningsScorecardCard(
                title = "Current Innings • $battingTeamName batting",
                isExpanded = currentInningsExpanded,
                onToggleExpand = { currentInningsExpanded = !currentInningsExpanded },
                battingTeam = battingTeamName,
                bowlingTeam = bowlingTeamName,
                batters = if (currentInnings == 1) {
                    (battingTeamPlayers.filter { player ->
                        player.ballsFaced > 0 || player.runs > 0 || player.isRetired ||
                        player.name == striker?.name || player.name == nonStriker?.name
                    } + completedBattersInnings1).distinctBy { it.name }
                } else {
                    (battingTeamPlayers.filter { player ->
                        player.ballsFaced > 0 || player.runs > 0 || player.isRetired ||
                        player.name == striker?.name || player.name == nonStriker?.name
                    } + completedBattersInnings2).distinctBy { it.name }
                },
                bowlers = if (currentInnings == 1) {
                    (bowlingTeamPlayers.filter { it.ballsBowled > 0 || it.wickets > 0 || it.runsConceded > 0 } + completedBowlersInnings1).distinctBy { it.name }
                } else {
                    (bowlingTeamPlayers.filter { it.ballsBowled > 0 || it.wickets > 0 || it.runsConceded > 0 } + completedBowlersInnings2).distinctBy { it.name }
                },
                partnerships = currentPartnerships,
                fallOfWickets = currentFallOfWickets,
                striker = striker,
                nonStriker = nonStriker,
                currentPartnershipRuns = currentPartnershipRuns,
                currentPartnershipBalls = currentPartnershipBalls,
                currentPartnershipBatsman1Runs = currentPartnershipBatsman1Runs,
                currentPartnershipBatsman2Runs = currentPartnershipBatsman2Runs,
                currentPartnershipBatsman1Balls = currentPartnershipBatsman1Balls,
                currentPartnershipBatsman2Balls = currentPartnershipBatsman2Balls,
                currentPartnershipBatsman1Name = currentPartnershipBatsman1Name,
                currentPartnershipBatsman2Name = currentPartnershipBatsman2Name
            )
        }

        
        // First Innings (if in 2nd innings) - Collapsible
        if (currentInnings == 2 && firstInningsBattingPlayersList.isNotEmpty()) {
            item {
                InningsScorecardCard(
                    title = "First Innings",
                    isExpanded = firstInningsExpanded,
                    onToggleExpand = { firstInningsExpanded = !firstInningsExpanded },
                    battingTeam = "", // Team names are swapped in 2nd innings
                    bowlingTeam = "",
                    batters = firstInningsBattingPlayersList,
                    bowlers = firstInningsBowlingPlayersList,
                    partnerships = firstInningsPartnerships,
                    fallOfWickets = firstInningsFallOfWickets,
                    striker = null,
                    nonStriker = null,
                    currentPartnershipRuns = 0,
                    currentPartnershipBalls = 0,
                    currentPartnershipBatsman1Runs = 0,
                    currentPartnershipBatsman2Runs = 0
                )
            }
        }
    }
}

@Composable
fun InningsScorecardCard(
    title: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    battingTeam: String,
    bowlingTeam: String,
    batters: List<Player>,
    bowlers: List<Player>,
    partnerships: List<Partnership> = emptyList(),
    fallOfWickets: List<FallOfWicket> = emptyList(),
    striker: Player? = null,
    nonStriker: Player? = null,
    currentPartnershipRuns: Int = 0,
    currentPartnershipBalls: Int = 0,
    currentPartnershipBatsman1Runs: Int = 0,
    currentPartnershipBatsman2Runs: Int = 0,
    currentPartnershipBatsman1Balls: Int = 0,
    currentPartnershipBatsman2Balls: Int = 0,
    currentPartnershipBatsman1Name: String? = null,
    currentPartnershipBatsman2Name: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column {
            // Header - Always visible
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (isExpanded) 
                        Icons.Default.KeyboardArrowUp 
                    else 
                        Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            // Expandable content
            if (isExpanded) {
                HorizontalDivider()
                
                // Batting Section
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "BATTING",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    
                    // Batting Header Row
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("Batter", modifier = Modifier.weight(2f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("R", modifier = Modifier.weight(0.7f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("B", modifier = Modifier.weight(0.7f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("4s", modifier = Modifier.weight(0.7f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("6s", modifier = Modifier.weight(0.7f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("SR", modifier = Modifier.weight(0.9f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(8.dp))
                    
                    // Batters
                    batters.forEach { player ->
                        val sr = if (player.ballsFaced > 0) 
                            String.format("%.1f", (player.runs.toFloat() / player.ballsFaced) * 100)
                        else "0.0"
                        
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    player.name,
                                    modifier = Modifier.weight(2f),
                                    fontSize = 13.sp,
                                    fontWeight = if (!player.isOut) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(player.runs.toString(), modifier = Modifier.weight(0.7f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(player.ballsFaced.toString(), modifier = Modifier.weight(0.7f), fontSize = 13.sp)
                                Text(player.fours.toString(), modifier = Modifier.weight(0.7f), fontSize = 13.sp)
                                Text(player.sixes.toString(), modifier = Modifier.weight(0.7f), fontSize = 13.sp)
                                Text(sr, modifier = Modifier.weight(0.9f), fontSize = 13.sp)
                            }
                            if (player.isOut || player.isRetired) {
                                Text(
                                    text = player.getDismissalText(),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontStyle = FontStyle.Italic,
                                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                                )
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                
                // Bowling Section
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "BOWLING",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    
                    // Bowling Header Row
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("Bowler", modifier = Modifier.weight(2f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("O", modifier = Modifier.weight(0.7f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("R", modifier = Modifier.weight(0.7f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("W", modifier = Modifier.weight(0.7f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Econ", modifier = Modifier.weight(0.9f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(8.dp))
                    
                    // Bowlers
                    bowlers.forEach { player ->
                        val overs = player.ballsBowled / 6
                        val balls = player.ballsBowled % 6
                        val oversStr = "$overs.$balls"
                        val econ = if (overs > 0 || balls > 0) {
                            val totalOvers = overs + (balls / 6.0)
                            String.format("%.1f", player.runsConceded / totalOvers)
                        } else "0.0"
                        
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(player.name, modifier = Modifier.weight(2f), fontSize = 13.sp)
                            Text(oversStr, modifier = Modifier.weight(0.7f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(player.runsConceded.toString(), modifier = Modifier.weight(0.7f), fontSize = 13.sp)
                            Text(player.wickets.toString(), modifier = Modifier.weight(0.7f), fontSize = 13.sp)
                            Text(econ, modifier = Modifier.weight(0.9f), fontSize = 13.sp)
                        }
                    }
                }

                // Partnerships Section - Collapsible (includes current partnership)
                if (partnerships.isNotEmpty() || (striker != null && nonStriker != null && currentPartnershipRuns > 0)) {
                    var partnershipsExpanded by remember { mutableStateOf(false) }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        // Header with expand/collapse
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { partnershipsExpanded = !partnershipsExpanded }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "🤝 PARTNERSHIPS",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = if (partnershipsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (partnershipsExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (partnershipsExpanded) {
                            Spacer(Modifier.height(12.dp))
                            
                            // Current active partnership first (if exists)
                            if (striker != null && nonStriker != null && currentPartnershipRuns > 0) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Current Partnership *",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                "$currentPartnershipRuns runs ($currentPartnershipBalls balls)",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                "${currentPartnershipBatsman1Name ?: ""}: $currentPartnershipBatsman1Runs* ($currentPartnershipBatsman1Balls)",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                "${currentPartnershipBatsman2Name ?: ""}: $currentPartnershipBatsman2Runs* ($currentPartnershipBatsman2Balls)",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            
                            // Completed partnerships
                            partnerships.forEachIndexed { index, partnership ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "${index + 1}${when(index + 1) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }} wkt",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                "${partnership.runs} runs (${partnership.balls} balls)",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                "${partnership.batsman1Name}: ${partnership.batsman1Runs}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                "${partnership.batsman2Name}: ${partnership.batsman2Runs}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                if (index < partnerships.size - 1) {
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Fall of Wickets Section - Collapsible
                if (fallOfWickets.isNotEmpty()) {
                    var fallOfWicketsExpanded by remember { mutableStateOf(false) }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                        // Header with expand/collapse
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { fallOfWicketsExpanded = !fallOfWicketsExpanded }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "📉 FALL OF WICKETS",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = if (fallOfWicketsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (fallOfWicketsExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (fallOfWicketsExpanded) {
                            Spacer(Modifier.height(12.dp))
                            fallOfWickets.forEach { fow ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${fow.wicketNumber}-${fow.runs}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        "(${String.format("%.1f", fow.overs)} ov)",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    fow.batsmanName,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                                if (fow != fallOfWickets.last()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OversTab(
    modifier: Modifier = Modifier,
    allDeliveries: List<DeliveryUI>
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                text = "Over-by-Over Commentary",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        if (allDeliveries.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        "No deliveries yet. Start scoring to see over-by-over details.",
                        modifier = Modifier.padding(16.dp),
                        fontSize = 14.sp,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        } else {
            // Group deliveries by innings first, then by over
            val deliveriesByInnings = allDeliveries.groupBy { it.inning }
            
            // Display in reverse innings order (2nd innings first if exists, then 1st)
            deliveriesByInnings.keys.sortedDescending().forEach { inningsNumber ->
                val inningsDeliveries = deliveriesByInnings[inningsNumber] ?: emptyList()
                
                // Innings header
                item {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (inningsNumber == 2) 
                                MaterialTheme.colorScheme.secondaryContainer 
                            else 
                                MaterialTheme.colorScheme.primaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Text(
                            text = when (inningsNumber) {
                                1 -> "First Innings"
                                2 -> "Second Innings"
                                else -> "Innings $inningsNumber"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(10.dp),
                            color = if (inningsNumber == 2) 
                                MaterialTheme.colorScheme.onSecondaryContainer 
                            else 
                                MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                // Group by over within this innings
                val deliveriesByOver = inningsDeliveries.groupBy { it.over }
                
                // Display overs in reverse order (latest first)
                deliveriesByOver.keys.sortedDescending().forEach { overNumber ->
                    val overDeliveries = deliveriesByOver[overNumber] ?: emptyList()
                    
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                // Over header with bowler info on same line
                                val overTotalRuns = overDeliveries.sumOf { it.runs }
                                val firstDelivery = overDeliveries.firstOrNull()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            "Over $overNumber",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        // Bowler info inline
                                        if (firstDelivery != null && firstDelivery.bowlerName.isNotEmpty()) {
                                            Text(
                                                "• ${firstDelivery.bowlerName}",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        "$overTotalRuns runs",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                
                                // Batsmen info (compact, single line)
                                if (firstDelivery != null) {
                                    val allBatsmen = overDeliveries.flatMap { 
                                        listOf(it.strikerName, it.nonStrikerName) 
                                    }.filter { it.isNotEmpty() }.distinct()
                                    
                                    if (allBatsmen.isNotEmpty()) {
                                        Text(
                                            "Batters: ${allBatsmen.joinToString(", ")}",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                                
                                Spacer(Modifier.height(6.dp))
                                
                                // Deliveries - Smooth horizontal scrollable row with LazyRow
                                androidx.compose.foundation.lazy.LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp)
                                ) {
                                    items(overDeliveries.size) { index ->
                                        val delivery = overDeliveries[index]
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = when {
                                                delivery.outcome == "W" -> MaterialTheme.colorScheme.errorContainer
                                                delivery.highlight -> MaterialTheme.colorScheme.tertiaryContainer
                                                else -> MaterialTheme.colorScheme.surface
                                            },
                                            modifier = Modifier.defaultMinSize(minWidth = 32.dp)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    delivery.outcome,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = when {
                                                        delivery.outcome == "W" -> MaterialTheme.colorScheme.onErrorContainer
                                                        delivery.highlight -> MaterialTheme.colorScheme.onTertiaryContainer
                                                        else -> MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                // Calculate over summary
                                val runsInOver = overDeliveries.sumOf { delivery ->
                                    when {
                                        delivery.outcome == "W" -> 0
                                        delivery.outcome.toIntOrNull() != null -> delivery.outcome.toInt()
                                        delivery.outcome.contains("WD") -> delivery.outcome.filter { it.isDigit() }.toIntOrNull() ?: 1
                                        delivery.outcome.contains("NB") -> delivery.outcome.filter { it.isDigit() }.toIntOrNull()?.plus(1) ?: 1
                                        delivery.outcome.contains("B") || delivery.outcome.contains("LB") -> 
                                            delivery.outcome.filter { it.isDigit() }.toIntOrNull() ?: 0
                                        else -> 0
                                    }
                                }
                                val wicketsInOver = overDeliveries.count { it.outcome == "W" }
                                
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Summary: $runsInOver runs${if (wicketsInOver > 0) ", $wicketsInOver wicket(s)" else ""}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SquadTab(
    modifier: Modifier = Modifier,
    team1Name: String,
    team2Name: String,
    team1Players: List<Player>,
    team2Players: List<Player>,
    jokerPlayer: Player?
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Text(
                text = "Match Squad",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        // Teams Side by Side
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Team 1
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            team1Name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(12.dp))
                        
                        team1Players.forEachIndexed { index, player ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${index + 1}. ${player.name}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (player.isJoker) {
                                    Text(
                                        "🃏",
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Team 2
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            team2Name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(12.dp))
                        
                        team2Players.forEachIndexed { index, player ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${index + 1}. ${player.name}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                if (player.isJoker) {
                                    Text(
                                        "🃏",
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Joker Player (if exists)
        jokerPlayer?.let { joker ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "🃏 Joker Player",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            joker.name,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Can bat for either team or bowl when not batting",
                            fontSize = 12.sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FielderSelectionDialog(
    wicketType: WicketType,
    bowlingTeamPlayers: List<Player>,
    jokerPlayer: Player?,
    jokerAvailableForFielding: Boolean,
    currentBowler: Player?,
    onFielderSelected: (Player?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFielder by remember { mutableStateOf<Player?>(null) }
    
    // Build the list of available fielders (bowling team + joker if available)
    // For stumping, exclude the bowler (bowler cannot stump)
    val availableFielders = buildList {
        addAll(bowlingTeamPlayers)
        // Only add joker if they're not already in the bowling team (prevents duplicates)
        if (jokerAvailableForFielding && jokerPlayer != null &&
            !bowlingTeamPlayers.any { it.name == jokerPlayer.name }) {
            add(jokerPlayer)
        }
    }.filter { player ->
        // Bowler cannot stump the batsman
        if (wicketType == WicketType.STUMPED) {
            player.name != currentBowler?.name
        } else {
            true
        }
    }.distinctBy { it.name } // Extra safety to prevent any duplicates
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        when (wicketType) {
                            WicketType.CAUGHT -> Icons.Default.Person
                            WicketType.STUMPED -> Icons.Default.Star
                            else -> Icons.Default.Person
                        },
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp).size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            Column {
                Text(
                    text = when (wicketType) {
                        WicketType.CAUGHT -> "Who took the catch?"
                        WicketType.STUMPED -> "Who stumped?"
                            WicketType.RUN_OUT -> "Who ran them out?"
                        else -> "Select Fielder"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (wicketType == WicketType.STUMPED) {
                    Text(
                            text = "Bowler cannot stump",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic
                    )
                    }
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Option to skip fielder selection
                item {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { selectedFielder = null },
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (selectedFielder == null) 
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else
                                MaterialTheme.colorScheme.surface
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(
                            width = if (selectedFielder == null) 2.dp else 1.dp
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Skip (No fielder credit)",
                                fontWeight = if (selectedFielder == null) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 14.sp
                        )
                        }
                    }
                }
                
                // Fielding team players (including joker if available, no duplicates)
                items(availableFielders) { player ->
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { selectedFielder = player },
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (selectedFielder == player) 
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else 
                                MaterialTheme.colorScheme.surface
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(
                            width = if (selectedFielder == player) 2.dp else 1.dp
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (player.isJoker)
                                        MaterialTheme.colorScheme.tertiary
                                    else
                                        MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                            Text(
                                text = player.name,
                                    fontWeight = if (selectedFielder == player) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 14.sp
                            )
                            }
                            if (player.isJoker) {
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        "🃏",
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (selectedFielder == player) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onFielderSelected(selectedFielder) }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun UnlimitedUndoToggle(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isEnabled) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (isEnabled) "🔓" else "🔒",
                    fontSize = 16.sp
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (isEnabled) "Unlimited Undo Active" else "Undo Limited to 2 Balls",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isEnabled) "Can undo to match start" else "Tap to enable unlimited undo",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
fun RetirementDialog(
    striker: Player?,
    nonStriker: Player?,
    onRetireBatsman: (Int) -> Unit, // 1 for striker, 2 for non-striker
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp).size(20.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Text(
                    "Retire Batsman",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Select which batsman to retire:",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Striker option
                striker?.let { s ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRetireBatsman(1) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        s.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Striker",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    "${s.runs}(${s.ballsFaced})",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
                
                // Non-striker option
                nonStriker?.let { ns ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRetireBatsman(2) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        ns.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Non-striker",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    "${ns.runs}(${ns.ballsFaced})",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

