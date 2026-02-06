package com.oreki.stumpd.ui.scoring

import com.oreki.stumpd.*

import com.oreki.stumpd.domain.model.*
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.ui.theme.StumpdTopBar
import java.util.UUID
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oreki.stumpd.viewmodel.ScoringViewModel
import com.oreki.stumpd.viewmodel.ScoringViewModelFactory
import com.oreki.stumpd.viewmodel.ScoringInitParams
import com.oreki.stumpd.viewmodel.ToastEvent


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

        val initParams = ScoringInitParams(
            matchId = matchId,
            team1Name = team1Name,
            team2Name = team2Name,
            jokerName = jokerName,
            team1CaptainName = team1CaptainName,
            team2CaptainName = team2CaptainName,
            team1PlayerNames = team1PlayerNames.toList(),
            team2PlayerNames = team2PlayerNames.toList(),
            team1PlayerIds = team1PlayerIds.toList(),
            team2PlayerIds = team2PlayerIds.toList(),
            matchSettingsJson = matchSettingsJson,
            groupId = groupId,
            groupName = groupName,
            tossChoice = tossChoice,
            tossWinner = tossWinner,
            resumeMatchId = if (persistedMatchId != null) matchId else resumeMatchId
        )
        val factory = ScoringViewModelFactory(application, initParams)

        setContent {
            StumpdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val vm: ScoringViewModel = viewModel(factory = factory)
                    ScoringScreen(viewModel = vm)
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
fun ScoringScreen(viewModel: ScoringViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Convenience aliases so the rest of the composable reads cleanly
    val vm = viewModel
    val matchSettings = vm.matchSettings
    val battingTeamName = vm.battingTeamName
    val bowlingTeamName = vm.bowlingTeamName
    val battingTeamPlayers = vm.battingTeamPlayers
    val bowlingTeamPlayers = vm.bowlingTeamPlayers
    val currentInnings = vm.currentInnings
    val currentOver = vm.currentOver
    val ballsInOver = vm.ballsInOver
    val totalWickets = vm.totalWickets
    val totalExtras = vm.totalExtras
    val calculatedTotalRuns = vm.calculatedTotalRuns
    val firstInningsRuns = vm.firstInningsRuns
    val striker = vm.striker
    val nonStriker = vm.nonStriker
    val bowler = vm.bowler
    val jokerPlayer = vm.jokerPlayer
    val showSingleSideLayout = vm.showSingleSideLayout
    val availableBatsmen = vm.availableBatsmen
    val isInningsComplete = vm.isInningsComplete
    val isPowerplayActive = vm.isPowerplayActive

    // ── Collect toast events from ViewModel ─────────────────────────
    LaunchedEffect(Unit) {
        vm.toastEvent.collect { event ->
            when (event) {
                is ToastEvent.Short -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is ToastEvent.Long -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── One-shot: Firebase auto-share ───────────────────────────────
    LaunchedEffect(vm.matchId) { vm.startFirebaseAutoShare() }

    // Wait for player ID→name map before rendering
    if ((vm.team1PlayerIds.isNotEmpty() || vm.team2PlayerIds.isNotEmpty()) && !vm.idToNameLoaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    // ── Resume from saved state ─────────────────────────────────────
    LaunchedEffect(vm.isResuming) { vm.resumeMatch() }

    // ── Partnership init ────────────────────────────────────────────
    LaunchedEffect(striker, nonStriker) { vm.initPartnershipIfNeeded() }

    // ── Auto-save when key state changes ────────────────────────────
    LaunchedEffect(currentOver, ballsInOver, totalWickets, calculatedTotalRuns, currentInnings) {
        if (currentOver > 0 || ballsInOver > 0) vm.autoSaveMatch()
    }

    // ── Powerplay doubling ──────────────────────────────────────────
    LaunchedEffect(currentOver, currentInnings) { vm.checkPowerplayDoubling() }

    // ── Innings completion ──────────────────────────────────────────
    LaunchedEffect(isInningsComplete) { if (isInningsComplete) vm.onInningsComplete() }

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
                onBack = { vm.showExitDialog = true },
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
                currentBowlerSpell = vm.currentBowlerSpell,
                jokerPlayer = jokerPlayer,
                currentOverDeliveries = vm.currentOverDeliveries,
                isInningsComplete = isInningsComplete,
                isPowerplayActive = isPowerplayActive,
                context = context,
            onSelectStriker = { vm.selectingBatsman = 1; vm.showBatsmanDialog = true },
            onSelectNonStriker = { vm.selectingBatsman = 2; vm.showBatsmanDialog = true },
            onSelectBowler = { vm.showBowlerDialog = true },
            onSwapStrike = {
                vm.swapStrike()
                Toast.makeText(context, "Strike swapped! ${nonStriker?.name} now on strike", Toast.LENGTH_SHORT).show()
            },
            onRetire = { vm.showRetirementDialog = true },
            onScoreRuns = { runs -> vm.onRunScored(runs) },
            onShowExtras = { vm.showExtrasDialog = true },
            onShowWicket = { vm.showWicketDialog = true },
            onUndo = { vm.undoLastDelivery() },
                        onWide = { vm.onQuickWide() },
                        unlimitedUndoEnabled = vm.unlimitedUndoEnabled,
                        onToggleUnlimitedUndo = { newValue ->
                            vm.pendingUnlimitedUndoValue = newValue
                            vm.showUnlimitedUndoDialog = true
                        }
        )
            1 -> ScorecardTab(
                modifier = Modifier.padding(16.dp),
                currentInnings = currentInnings,
                battingTeamName = battingTeamName,
                bowlingTeamName = bowlingTeamName,
                battingTeamPlayers = battingTeamPlayers,
                bowlingTeamPlayers = bowlingTeamPlayers,
                completedBattersInnings1 = vm.completedBattersInnings1,
                completedBattersInnings2 = vm.completedBattersInnings2,
                completedBowlersInnings1 = vm.completedBowlersInnings1,
                completedBowlersInnings2 = vm.completedBowlersInnings2,
                firstInningsBattingPlayersList = vm.firstInningsBattingPlayersList,
                        firstInningsBowlingPlayersList = vm.firstInningsBowlingPlayersList,
                        currentPartnerships = vm.partnerships,
                        firstInningsPartnerships = vm.firstInningsPartnerships,
                        currentFallOfWickets = vm.fallOfWickets,
                        firstInningsFallOfWickets = vm.firstInningsFallOfWickets,
                        striker = striker,
                        nonStriker = nonStriker,
                        currentPartnershipRuns = vm.currentPartnershipRuns,
                        currentPartnershipBalls = vm.currentPartnershipBalls,
                        currentPartnershipBatsman1Runs = vm.currentPartnershipBatsman1Runs,
                        currentPartnershipBatsman2Runs = vm.currentPartnershipBatsman2Runs,
                        currentPartnershipBatsman1Balls = vm.currentPartnershipBatsman1Balls,
                        currentPartnershipBatsman2Balls = vm.currentPartnershipBatsman2Balls,
                        currentPartnershipBatsman1Name = vm.currentPartnershipBatsman1Name,
                        currentPartnershipBatsman2Name = vm.currentPartnershipBatsman2Name,
                        shortPitch = matchSettings.shortPitch
            )
            2 -> OversTab(
                modifier = Modifier.padding(16.dp),
                allDeliveries = vm.allDeliveries
            )
            3 -> SquadTab(
                modifier = Modifier.padding(16.dp),
                team1Name = vm.team1Name,
                team2Name = vm.team2Name,
                team1Players = vm.team1Players,
                team2Players = vm.team2Players,
                jokerPlayer = jokerPlayer
            )
                }
            }
        }
    }

    // Dialogs
    if (vm.showLiveScorecardDialog) {
        LiveScorecardDialog(
            currentInnings = currentInnings,
            battingTeamName = battingTeamName,
            battingTeamPlayers = battingTeamPlayers,
            bowlingTeamPlayers = bowlingTeamPlayers,
            firstInningsBattingPlayers = vm.firstInningsBattingPlayersList,
            firstInningsBowlingPlayers = vm.firstInningsBowlingPlayersList,
            firstInningsRuns = firstInningsRuns,
            firstInningsWickets = vm.firstInningsWickets,
            currentRuns = calculatedTotalRuns,
            currentWickets = totalWickets,
            currentOvers = currentOver,
            currentBalls = ballsInOver,
            totalOvers = matchSettings.totalOvers,
            jokerPlayerName = vm.jokerName,
            striker = striker,
            nonStriker = nonStriker,
            bowler = bowler,
            onDismiss = { vm.showLiveScorecardDialog = false },
            shortPitch = matchSettings.shortPitch,
        )
    }

    if (vm.showExtrasDialog) {
        ExtrasDialog(
            matchSettings = matchSettings,
            onExtraSelected = { extraType, totalRuns ->
                vm.onExtraSelected(extraType, totalRuns)
            },
            onDismiss = { vm.showExtrasDialog = false },
            striker = striker,
            nonStriker = nonStriker,
            onWideWithStumping = { extraType, baseRuns -> vm.onWideWithStumping(extraType, baseRuns) }
        )
    }
    
    // Quick Wide Dialog
    if (vm.showQuickWideDialog) {
        QuickWideDialog(
            matchSettings = matchSettings,
            onWideConfirmed = { totalRuns -> vm.onQuickWideDialogConfirmed(totalRuns) },
            onStumpingOnWide = { runs -> vm.onStumpingOnWide(runs) },
            onDismiss = { vm.showQuickWideDialog = false }
        )
    }
    
    // Quick No-ball Dialog
    if (vm.showQuickNoBallDialog) {
        QuickNoBallDialog(
            matchSettings = matchSettings,
            striker = striker,
            nonStriker = nonStriker,
            onNoBallConfirmed = { totalRuns -> vm.onQuickNoBallConfirmed(totalRuns) },
            onDismiss = { vm.showQuickNoBallDialog = false }
        )
    }

    if (vm.showBatsmanDialog && !isInningsComplete) {
        EnhancedPlayerSelectionDialog(
            title = when {
                vm.selectingBatsman == 1 -> "Select Striker"
                else -> "Select Non-Striker"
            },
            players = battingTeamPlayers,
            jokerPlayer = if (totalWickets == 0 || vm.jokerOutInCurrentInnings) null else jokerPlayer,
            currentStrikerIndex = vm.strikerIndex,
            currentNonStrikerIndex = vm.nonStrikerIndex,
            allowSingleSide = matchSettings.allowSingleSideBatting,
            totalWickets = totalWickets,
            battingTeamPlayers = battingTeamPlayers,
            bowlingTeamPlayers = bowlingTeamPlayers,
            jokerOversThisInnings = vm.jokerOversBowledThisInnings(),
            onPlayerSelected = { player -> vm.onBatsmanSelected(player) },
            jokerOutInCurrentInnings = vm.jokerOutInCurrentInnings,
            onDismiss = { vm.showBatsmanDialog = false },
            matchSettings = matchSettings,
            otherEndName = vm.pickerOtherEndName
        )
    }

    if (vm.showBowlerDialog && !isInningsComplete) {
        val bowlerPool = if (ballsInOver == 0) {
            val prev = vm.previousBowlerName?.trim()
            bowlingTeamPlayers.filter { !it.name.trim().equals(prev, ignoreCase = true) }
        } else { bowlingTeamPlayers }
        val overrideToCompleteOverAllowed = (ballsInOver == 0) && run {
            val prev = vm.previousBowlerName?.trim()
            val startOverPool = if (prev != null) bowlingTeamPlayers.filter { !it.name.trim().equals(prev, ignoreCase = true) } else bowlingTeamPlayers
            !startOverPool.any { p ->
                val bsf = if (p.isJoker) vm.jokerBallsBowledThisInningsRaw() else p.ballsBowled
                val cap = (if (p.isJoker) matchSettings.jokerMaxOvers else matchSettings.maxOversPerBowler) * 6
                (cap - bsf).coerceAtLeast(0) >= 6
            }
        }
        EnhancedPlayerSelectionDialog(
            title = when {
                ballsInOver == 0 && vm.previousBowlerName != null -> "Select New Bowler (Same bowler cannot bowl consecutive overs)"
                vm.bowlerIndex == null && ballsInOver > 0 -> "Select Bowler to Complete Over"
                else -> "Select Bowler"
            },
            players = bowlerPool,
            jokerPlayer = if (battingTeamPlayers.any { it.isJoker } && (!vm.jokerOutInCurrentInnings)) null else jokerPlayer,
            totalWickets = totalWickets,
            battingTeamPlayers = battingTeamPlayers,
            bowlingTeamPlayers = bowlingTeamPlayers,
            jokerOversThisInnings = vm.jokerOversBowledThisInnings(),
            jokerOutInCurrentInnings = vm.jokerOutInCurrentInnings,
            onPlayerSelected = { player -> vm.onBowlerSelected(player, overrideToCompleteOverAllowed) },
            onDismiss = {
                if (ballsInOver > 0 && vm.bowlerIndex == null) {
                    Toast.makeText(context, "Please select a bowler to continue", Toast.LENGTH_SHORT).show()
                } else {
                    vm.showBowlerDialog = false
                    vm.midOverReplacementDueToJoker.value = false
                }
            },
            matchSettings = matchSettings,
            otherEndName = vm.pickerOtherEndName
        )
    }

    if (vm.showInningsBreakDialog) {
        vm.jokerOutInCurrentInnings = false
        EnhancedInningsBreakDialog(
            runs = vm.firstInningsRuns,
            wickets = vm.firstInningsWickets,
            overs = vm.firstInningsOvers,
            balls = vm.firstInningsBalls,
            battingTeam = battingTeamName,
            battingPlayers = vm.firstInningsBattingPlayersList,
            bowlingPlayers = vm.firstInningsBowlingPlayersList,
            totalOvers = matchSettings.totalOvers,
            onStartSecondInnings = { vm.onStartSecondInnings() },
            shortPitch = matchSettings.shortPitch,
        )
    }

    if (vm.showMatchCompleteDialog) {
        EnhancedMatchCompleteDialog(
            matchId = vm.matchId,
            firstInningsRuns = vm.firstInningsRuns,
            firstInningsWickets = vm.firstInningsWickets,
            secondInningsRuns = calculatedTotalRuns,
            secondInningsWickets = totalWickets,
            team1Name = vm.initialBattingTeamName,
            team2Name = vm.initialBowlingTeamName,
            jokerPlayerName = vm.jokerName.takeIf { it.isNotEmpty() },
            team1CaptainName = if (vm.initialBattingTeamName == vm.team1Name) vm.team1CaptainName else vm.team2CaptainName,
            team2CaptainName = if (vm.initialBattingTeamName == vm.team1Name) vm.team2CaptainName else vm.team1CaptainName,
            firstInningsBattingPlayers = vm.firstInningsBattingPlayersList,
            firstInningsBowlingPlayers = vm.firstInningsBowlingPlayersList,
            secondInningsBattingPlayers = vm.secondInningsBattingPlayers,
            secondInningsBowlingPlayers = vm.secondInningsBowlingPlayers,
            firstInningsPartnerships = vm.firstInningsPartnerships,
            secondInningsPartnerships = vm.partnerships,
            firstInningsFallOfWickets = vm.firstInningsFallOfWickets,
            secondInningsFallOfWickets = vm.fallOfWickets,
            onNewMatch = {
                val intent = android.content.Intent(context, MainActivity::class.java)
                intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                context.startActivity(intent)
                (context as androidx.activity.ComponentActivity).finish()
            },
            onDismiss = { vm.showMatchCompleteDialog = false },
            matchSettings = matchSettings,
            groupId = vm.groupId,
            groupName = vm.groupName,
            scope = scope,
            repo = vm.repo,
            inProgressManager = vm.inProgressManager,
            allDeliveries = vm.allDeliveries.toList()
        )
    }

    if (vm.showExitDialog) {
        AlertDialog(
            onDismissRequest = { vm.showExitDialog = false },
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
                        intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                        context.startActivity(intent)
                        (context as androidx.activity.ComponentActivity).finish()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Exit", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.showExitDialog = false }) {
                    Text("Continue Match")
                }
            },
        )
    }

    // Unlimited Undo Password Dialog
    if (vm.showUnlimitedUndoDialog) {
        var password by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                vm.showUnlimitedUndoDialog = false
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
                        if (vm.pendingUnlimitedUndoValue) {
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
                        val prefs = context.getSharedPreferences("stumpd_prefs", android.content.Context.MODE_PRIVATE)
                        val savedPassword = prefs.getString("deletion_password", null)

                        if (savedPassword == null) {
                            prefs.edit().putString("deletion_password", password).apply()
                            com.oreki.stumpd.utils.FeatureFlags.setUnlimitedUndoEnabled(context, vm.pendingUnlimitedUndoValue)
                            vm.unlimitedUndoEnabled = vm.pendingUnlimitedUndoValue
                            vm.showUnlimitedUndoDialog = false
                            password = ""
                            errorMessage = null
                            Toast.makeText(
                                context,
                                if (vm.pendingUnlimitedUndoValue) "✅ Unlimited undo enabled" else "✅ Undo locked to 2 balls",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else if (password == savedPassword) {
                            com.oreki.stumpd.utils.FeatureFlags.setUnlimitedUndoEnabled(context, vm.pendingUnlimitedUndoValue)
                            vm.unlimitedUndoEnabled = vm.pendingUnlimitedUndoValue
                            vm.showUnlimitedUndoDialog = false
                            password = ""
                            errorMessage = null
                            Toast.makeText(
                                context,
                                if (vm.pendingUnlimitedUndoValue) "✅ Unlimited undo enabled" else "✅ Undo locked to 2 balls",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
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
                        vm.showUnlimitedUndoDialog = false
                        password = ""
                        errorMessage = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (vm.showRetirementDialog) {
        RetirementDialog(
            striker = striker,
            nonStriker = nonStriker,
            onRetireBatsman = { position -> vm.onRetireBatsman(position) },
            onDismiss = { vm.showRetirementDialog = false }
        )
    }

    if (vm.showWicketDialog) {
        WicketTypeDialog(
            onWicketSelected = { wicketType -> vm.onWicketTypeSelected(wicketType) },
            onDismiss = { vm.showWicketDialog = false },
        )
    }

    if (vm.showRunOutDialog) {
        RunOutDialog(
            striker = striker,
            nonStriker = nonStriker,
            onConfirm = { input ->
                vm.pendingRunOutInput = input
                vm.showRunOutDialog = false
                vm.showFielderSelectionDialog = true
                vm.pendingWicketType = WicketType.RUN_OUT
            },
            onDismiss = { vm.showRunOutDialog = false }
        )
    }

    // Handle run-out fielder selection
    if (vm.showFielderSelectionDialog && vm.pendingWicketType == WicketType.RUN_OUT && vm.pendingRunOutInput != null) {
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
            onFielderSelected = { fielder -> vm.onFielderSelectedForRunOut(fielder) },
            onDismiss = {
                vm.showFielderSelectionDialog = false
                vm.pendingRunOutInput = null
                vm.pendingWicketType = null
            }
        )
    }
    
    // Fielder selection dialog for CAUGHT and STUMPED (not RUN_OUT - that's handled above)
    if (vm.showFielderSelectionDialog && vm.pendingWicketType != null && vm.pendingWicketType != WicketType.RUN_OUT) {
        val jokerAvailableForFielding = jokerPlayer != null &&
            striker?.isJoker != true &&
            nonStriker?.isJoker != true &&
            bowler?.isJoker != true

        FielderSelectionDialog(
            wicketType = vm.pendingWicketType!!,
            bowlingTeamPlayers = bowlingTeamPlayers,
            jokerPlayer = jokerPlayer,
            jokerAvailableForFielding = jokerAvailableForFielding,
            currentBowler = bowler,
            onFielderSelected = { fielder -> vm.onFielderSelectedForCaughtStumped(fielder) },
            onDismiss = {
                vm.showFielderSelectionDialog = false
                vm.pendingWicketType = null
            }
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
    firstInningsPartnerships: List<Partnership> = emptyList(),
    secondInningsPartnerships: List<Partnership> = emptyList(),
    firstInningsFallOfWickets: List<FallOfWicket> = emptyList(),
    secondInningsFallOfWickets: List<FallOfWicket> = emptyList(),
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
        // Partnerships and Fall of Wickets
        firstInningsPartnerships = firstInningsPartnerships,
        secondInningsPartnerships = secondInningsPartnerships,
        firstInningsFallOfWickets = firstInningsFallOfWickets,
        secondInningsFallOfWickets = secondInningsFallOfWickets,
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

