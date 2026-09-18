package com.oreki.stumpd.ui.scoring

import com.oreki.stumpd.*

import com.oreki.stumpd.data.preferences.MatchSettingsManager
import com.oreki.stumpd.domain.model.*
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
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
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.oreki.stumpd.ui.theme.Feedback
import com.oreki.stumpd.ui.theme.rememberHaptics
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.ui.theme.StumpdTopBar
import java.util.UUID
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oreki.stumpd.viewmodel.ScoringViewModel
import com.oreki.stumpd.viewmodel.ScoringViewModelFactory
import com.oreki.stumpd.viewmodel.ScoringInitParams
import com.oreki.stumpd.viewmodel.ToastEvent
import dagger.hilt.android.AndroidEntryPoint


/**
 * How long a celebration waits for the scoring surface to clear before being dropped. Long enough
 * to cover choosing a dismissal type and the next batsman, short enough that it can't surface
 * after an innings break.
 */
private const val CELEBRATION_GRACE_MS = 6_000L

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@AndroidEntryPoint
class ScoringActivity : ComponentActivity() {
    
    private var persistedMatchId: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
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
        // Present only for a tournament fixture; keyed to team1/team2 as set up, not to who bats.
        val tournamentId = intent.getStringExtra("tournament_id")
        val tournamentFixtureId = intent.getStringExtra("tournament_fixture_id")
        val team1Id = intent.getStringExtra("team1_id")
        val team2Id = intent.getStringExtra("team2_id")
        
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
            resumeMatchId = if (persistedMatchId != null) matchId else resumeMatchId,
            tournamentId = tournamentId,
            tournamentFixtureId = tournamentFixtureId,
            team1Id = team1Id,
            team2Id = team2Id
        )
        val factory = ScoringViewModelFactory(application, initParams)

        setContent {
            StumpdTheme {
                val windowSizeClass = calculateWindowSizeClass(this@ScoringActivity)
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val vm: ScoringViewModel = viewModel(factory = factory)
                    ScoringScreen(
                        viewModel = vm,
                        widthSizeClass = windowSizeClass.widthSizeClass,
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
    viewModel: ScoringViewModel,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
) {
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

    // Celebrations and haptics: collected as events, so nothing replays on recomposition or
    // re-fires when a mid-match process death is restored.
    var celebration by remember { mutableStateOf<ScoringEffect?>(null) }
    // Received but not yet shown. A wicket lands the instant the dismissal type is tapped, and
    // the new-batsman dialog opens on top of it immediately — so an effect played at that moment
    // spends its whole life behind a dialog scrim and is never seen. It waits here until the
    // scoring surface is clear, which for a wicket is the moment the batsman has been chosen.
    var pendingCelebration by remember { mutableStateOf<ScoringEffect?>(null) }
    // `vibrationFeedback` is a global preference, not a per-match one. It has existed unread
    // since before this screen had any feedback at all; this is what finally honours it.
    val settingsManager = remember { MatchSettingsManager(context) }
    val hapticsEnabled = remember { settingsManager.getGlobalSettings().vibrationFeedback }
    val haptics = rememberHaptics(enabled = hapticsEnabled)
    LaunchedEffect(vm) {
        vm.scoringEffect.collect { effect ->
            pendingCelebration = effect
            // The haptic is not deferred: it's felt rather than watched, so it belongs at the
            // true moment of the wicket, dialog or no dialog.
            haptics.perform(
                when (effect) {
                    ScoringEffect.Four, ScoringEffect.Six -> Feedback.Confirm
                    ScoringEffect.Wicket -> Feedback.Reject
                }
            )
        }
    }
    val surfaceObscured = vm.showWicketDialog || vm.showRunOutDialog ||
        vm.showFielderSelectionDialog || vm.showBatsmanDialog || vm.showBowlerDialog ||
        vm.showExtrasDialog || vm.showQuickWideDialog || vm.showQuickNoBallDialog ||
        vm.showRetirementDialog || vm.showInningsBreakDialog || vm.showMatchCompleteDialog ||
        vm.showExitDialog || vm.showLiveScorecardDialog ||
        vm.showFixMenu || vm.showFixWicketDialog || vm.showFixBowlerDialog ||
        vm.showFixLastBallDialog || vm.showSuperOverOfferDialog ||
        vm.showSuperOverIntervalDialog
    LaunchedEffect(pendingCelebration, surfaceObscured) {
        val pending = pendingCelebration ?: return@LaunchedEffect
        if (surfaceObscured) {
            // Don't hold it indefinitely — an innings break can sit open for minutes, and a
            // flourish arriving after that reads as a glitch rather than as feedback.
            delay(CELEBRATION_GRACE_MS)
            pendingCelebration = null
            return@LaunchedEffect
        }
        celebration = pending
        pendingCelebration = null
    }

    // Remembered so the set is stable across recompositions; the lambdas only capture `vm`,
    // `context` and the non-striker's name for the swap toast.
    val liveScoreActions = remember(vm, context, nonStriker?.name) {
        LiveScoreActions(
            onSelectStriker = { vm.selectingBatsman = 1; vm.showBatsmanDialog = true },
            onSelectNonStriker = { vm.selectingBatsman = 2; vm.showBatsmanDialog = true },
            onSelectBowler = { vm.showBowlerDialog = true },
            onSwapStrike = {
                vm.swapStrike()
                Toast.makeText(
                    context,
                    "Strike swapped! ${nonStriker?.name} now on strike",
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onScoreRuns = { runs ->
                // Boundaries get their own, firmer feedback from the effect collector above.
                if (runs != 4 && runs != 6) haptics.perform(Feedback.Tick)
                vm.onRunScored(runs)
            },
            onShowExtras = { vm.showExtrasDialog = true },
            onShowWicket = { vm.showWicketDialog = true },
            onUndo = { vm.undoLastDelivery() },
            onWide = { vm.onQuickWide() },
            onRetire = { vm.showRetirementDialog = true },
            onFix = { vm.showFixMenu = true },
        )
    }

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

    val tabFontSize = when (widthSizeClass) {
        WindowWidthSizeClass.Expanded -> 11.sp
        WindowWidthSizeClass.Medium -> 12.sp
        WindowWidthSizeClass.Compact -> 14.sp
        else -> 14.sp
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
    Box(modifier = Modifier.fillMaxSize()) {
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
                state = vm.liveScoreUiState,
                actions = liveScoreActions,
                battingTeamPlayers = battingTeamPlayers,
                striker = striker,
                nonStriker = nonStriker,
                bowler = bowler,
                jokerPlayer = jokerPlayer,
                currentOverDeliveries = vm.currentOverDeliveries,
                modifier = Modifier.padding(16.dp),
                widthSizeClass = widthSizeClass,
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
                        allDeliveries = vm.allDeliveries,
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
                allDeliveries = vm.allDeliveries,
                firstInningsTeamName = vm.initialBattingTeamName,
                secondInningsTeamName = vm.initialBowlingTeamName,
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

        // Above the scoreboard, not inside it: the live tab is large and non-skippable, so
        // animating within it would recompose the whole surface every frame. This also means the
        // flourish can't intercept a tap — the next ball is always ready.
        CelebrationOverlay(
            effect = celebration,
            onFinished = { celebration = null },
            modifier = Modifier.padding(padding),
        )
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
            jokerPlayer = if (!matchSettings.jokerCanBowl ||
                (battingTeamPlayers.any { it.isJoker } && !vm.jokerOutInCurrentInnings)
            ) null else jokerPlayer,
            totalWickets = totalWickets,
            battingTeamPlayers = battingTeamPlayers,
            bowlingTeamPlayers = bowlingTeamPlayers,
            jokerOversThisInnings = vm.jokerOversBowledThisInnings(),
            jokerOutInCurrentInnings = vm.jokerOutInCurrentInnings,
            onPlayerSelected = { player -> vm.onBowlerSelected(player, overrideToCompleteOverAllowed) },
            // Show the over cap on the row rather than accepting the tap and rejecting it with
            // a toast, which made the limit look like it was not being enforced at all.
            ineligibleReason = { p ->
                val bowled = if (p.isJoker) vm.jokerBallsBowledThisInningsRaw() else p.ballsBowled
                val capOvers = if (p.isJoker) matchSettings.jokerMaxOvers else matchSettings.maxOversPerBowler
                val remaining = (capOvers * 6 - bowled).coerceAtLeast(0)
                val needed = if (ballsInOver == 0) 6 else 6 - ballsInOver
                when {
                    remaining == 0 -> "Over limit reached ($capOvers of $capOvers overs bowled)"
                    remaining < needed && !overrideToCompleteOverAllowed ->
                        "Only $remaining ball${if (remaining == 1) "" else "s"} left of the $capOvers-over limit"
                    else -> null
                }
            },
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

    // ── Super over ──────────────────────────────────────────────────
    // Both of these stand between a tied match and the complete dialog, which writes the match as
    // soon as it appears. Nothing is saved until the tie is either broken or accepted.
    if (vm.showSuperOverOfferDialog) {
        SuperOverOfferDialog(
            scoresLevelAt = vm.firstInningsRuns,
            // Entering an odd innings keeps the same side batting, so whoever just batted goes
            // again — which is the real rule: the side that batted second starts the super over.
            battingFirstInSuperOver = vm.battingTeamName,
            superOversPlayed = vm.superOvers.size / 2,
            onStart = { vm.startSuperOver() },
            onDecline = { vm.declineSuperOver() },
        )
    }

    if (vm.showSuperOverIntervalDialog) {
        vm.superOvers.lastOrNull()?.let { justBowled ->
            SuperOverIntervalDialog(
                justBowled = justBowled,
                chasingTeam = justBowled.bowlingTeam,
                onStart = { vm.startSuperOver() },
            )
        }
    }

    if (vm.showMatchCompleteDialog) {
        EnhancedMatchCompleteDialog(
            matchId = vm.matchId,
            firstInningsRuns = vm.firstInningsRuns,
            firstInningsWickets = vm.firstInningsWickets,
            // The snapshots, not the live figures: a super over resets those, and this dialog is
            // shown after it.
            secondInningsRuns = vm.secondInningsRuns,
            secondInningsWickets = vm.secondInningsWickets,
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
            secondInningsPartnerships = vm.secondInningsPartnerships,
            firstInningsFallOfWickets = vm.firstInningsFallOfWickets,
            secondInningsFallOfWickets = vm.secondInningsFallOfWickets,
            onNewMatch = {
                if (vm.tournamentId != null) {
                    // A real navigation rather than a bare finish(), because a fixture can also
                    // be reached by resuming an in-progress match from History — which never
                    // puts a tournament screen on this activity's back stack at all.
                    context.startActivity(
                        com.oreki.stumpd.ui.tournament.TournamentActivity.intent(context, vm.tournamentId!!)
                    )
                    (context as androidx.activity.ComponentActivity).finish()
                } else {
                    val intent = android.content.Intent(context, MainActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                    context.startActivity(intent)
                    (context as androidx.activity.ComponentActivity).finish()
                }
            },
            onDismiss = { vm.showMatchCompleteDialog = false },
            matchSettings = matchSettings,
            groupId = vm.groupId,
            groupName = vm.groupName,
            scope = scope,
            repo = vm.repo,
            inProgressManager = vm.inProgressManager,
            allDeliveries = vm.allDeliveries.toList(),
            superOverWinner = vm.superOverWinner,
            superOvers = vm.superOvers,
            tournamentId = vm.tournamentId,
            tournamentFixtureId = vm.tournamentFixtureId,
            // team1 means "batted first" in a saved match, so the ids swap with the names — the
            // same expression the captains above use, for the same reason.
            team1Id = if (vm.initialBattingTeamName == vm.team1Name) vm.team1Id else vm.team2Id,
            team2Id = if (vm.initialBattingTeamName == vm.team1Name) vm.team2Id else vm.team1Id
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
                        if (vm.tournamentId != null) {
                            // Same reasoning as the complete dialog's "Back to Tournament".
                            context.startActivity(
                                com.oreki.stumpd.ui.tournament.TournamentActivity.intent(context, vm.tournamentId!!)
                            )
                            (context as androidx.activity.ComponentActivity).finish()
                        } else {
                            val intent = android.content.Intent(context, MainActivity::class.java)
                            intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                            context.startActivity(intent)
                            (context as androidx.activity.ComponentActivity).finish()
                        }
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

    // ── Mid-match corrections ───────────────────────────────────────
    // Each fix reports null on success or the reason it refused, which goes straight to a toast:
    // the refusals are all "this needs the post-match editor", and saying so is more use than a
    // disabled button with no explanation.
    val onFixResult: (String?) -> Unit = { problem ->
        vm.showFixMenu = false
        vm.showFixWicketDialog = false
        vm.showFixBowlerDialog = false
        vm.showFixLastBallDialog = false
        if (problem == null) vm.toast("Corrected — Undo still reverses it")
        else vm.toastLong(problem)
    }

    if (vm.showFixMenu) {
        FixMenuDialog(
            corrections = vm.corrections,
            onFixWicket = { vm.showFixMenu = false; vm.showFixWicketDialog = true },
            onFixBowler = { vm.showFixMenu = false; vm.showFixBowlerDialog = true },
            onFixLastBall = { vm.showFixMenu = false; vm.showFixLastBallDialog = true },
            onDismiss = { vm.showFixMenu = false },
        )
    }

    if (vm.showFixWicketDialog) {
        FixWicketDialog(
            corrections = vm.corrections,
            onResult = onFixResult,
            onDismiss = { vm.showFixWicketDialog = false },
        )
    }

    if (vm.showFixBowlerDialog) {
        FixBowlerDialog(
            corrections = vm.corrections,
            onResult = onFixResult,
            onDismiss = { vm.showFixBowlerDialog = false },
        )
    }

    if (vm.showFixLastBallDialog) {
        FixLastBallDialog(
            corrections = vm.corrections,
            shortPitch = vm.matchSettings.shortPitch,
            onResult = onFixResult,
            onDismiss = { vm.showFixLastBallDialog = false },
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


