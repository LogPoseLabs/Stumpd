package com.oreki.stumpd

import com.oreki.stumpd.domain.model.*
import com.oreki.stumpd.domain.match.battingSideIsFirstInningsSide
import com.oreki.stumpd.ui.scoring.InningsScorecardCard
import com.oreki.stumpd.ui.scoring.OversTab
import com.oreki.stumpd.ui.scoring.deriveBattingOrder
import com.oreki.stumpd.ui.scoring.deriveBowlingOrder
import com.oreki.stumpd.ui.scoring.sortedByOrder
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import com.oreki.stumpd.ui.theme.MicroLabel
import com.oreki.stumpd.ui.theme.ScoreMedium
import com.oreki.stumpd.ui.theme.StatValue
import com.oreki.stumpd.ui.theme.hairline
import com.oreki.stumpd.ui.theme.stumpd
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.google.gson.Gson
import com.oreki.stumpd.data.models.parsePartnershipsPersistenceState
import com.oreki.stumpd.viewmodel.SpectatorViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint

/**
 * Spectator Activity - Read-only live view of a match
 * Updates in real-time as the match progresses
 */
@AndroidEntryPoint
class SpectatorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val matchId = intent.getStringExtra("MATCH_ID") ?: ""
        val ownerId = intent.getStringExtra("OWNER_ID") ?: ""
        val shareCode = intent.getStringExtra("SHARE_CODE") ?: ""
        
        if (matchId.isEmpty() || ownerId.isEmpty()) {
            Log.e("SpectatorActivity", "Missing matchId or ownerId")
            finish()
            return
        }

        setContent {
            StumpdTheme {
                SpectatorScreen(
                    matchId = matchId,
                    ownerId = ownerId,
                    shareCode = shareCode,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpectatorScreen(
    matchId: String,
    ownerId: String,
    shareCode: String,
    onBack: () -> Unit
) {
    val viewModel: SpectatorViewModel = hiltViewModel(
        creationCallback = { factory: SpectatorViewModel.Factory ->
            factory.create(matchId, ownerId)
        }
    )
    val uiState = viewModel.uiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Live Match Spectator")
                        if (shareCode.isNotEmpty()) {
                            Text(
                                "Code: $shareCode",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState) {
                is SpectatorViewModel.UiState.Loading -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Connecting to live match...")
                    }
                }
                is SpectatorViewModel.UiState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "⚠️ Connection Error",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.message,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is SpectatorViewModel.UiState.Content -> {
                    val lastUpdated = uiState.lastUpdatedMs
                    key(lastUpdated) {
                        LiveInProgressMatchView(uiState.match, lastUpdated)
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LiveInProgressMatchView(match: com.oreki.stumpd.data.local.entity.InProgressMatchEntity, lastUpdated: Long) {
    val tabs = listOf("Live", "Scorecard", "Overs", "Partnerships")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()
    val gson = remember { Gson() }
    
    // Parse match settings
    val matchSettings = remember(match.matchSettingsJson) {
        try {
            gson.fromJson(match.matchSettingsJson, MatchSettings::class.java)
        } catch (e: Exception) { null }
    }
    
    // Parse team players - explicitly depend on lastUpdated to force re-parsing
    val team1Players = remember(lastUpdated) {
        try { gson.fromJson(match.team1PlayersJson, Array<Player>::class.java).toList() }
        catch (e: Exception) { emptyList() }
    }
    val team2Players = remember(lastUpdated) {
        try { gson.fromJson(match.team2PlayersJson, Array<Player>::class.java).toList() }
        catch (e: Exception) { emptyList() }
    }
    
    // Which side is in follows the innings, not its parity: a super over has the second innings'
    // side batting again. Shared with resume and auto-save so a spectator can't see a different
    // answer from the scorer.
    val firstInningsSideBatting = battingSideIsFirstInningsSide(match.currentInnings)
    val currentBattingTeam = if (firstInningsSideBatting) team1Players else team2Players
    val currentBowlingTeam = if (firstInningsSideBatting) team2Players else team1Players
    val battingTeamName = if (firstInningsSideBatting) match.team1Name else match.team2Name
    val bowlingTeamName = if (firstInningsSideBatting) match.team2Name else match.team1Name
    
    // Parse first innings players (for scorecard in 2nd innings)
    val firstInningsBattingPlayers = remember(lastUpdated) {
        try { match.firstInningsBattingPlayersJson?.let { gson.fromJson(it, Array<Player>::class.java).toList() } ?: emptyList() }
        catch (e: Exception) { emptyList() }
    }
    val firstInningsBowlingPlayers = remember(lastUpdated) {
        try { match.firstInningsBowlingPlayersJson?.let { gson.fromJson(it, Array<Player>::class.java).toList() } ?: emptyList() }
        catch (e: Exception) { emptyList() }
    }
    
    // Parse completed players
    val completedBattersInnings1 = remember(lastUpdated) {
        try { match.completedBattersInnings1Json?.let { gson.fromJson(it, Array<Player>::class.java).toList() } ?: emptyList() }
        catch (e: Exception) { emptyList() }
    }
    val completedBowlersInnings1 = remember(lastUpdated) {
        try { match.completedBowlersInnings1Json?.let { gson.fromJson(it, Array<Player>::class.java).toList() } ?: emptyList() }
        catch (e: Exception) { emptyList() }
    }
    
    // Parse deliveries for overs
    val deliveries = remember(lastUpdated) {
        try { match.allDeliveriesJson?.let { gson.fromJson(it, Array<DeliveryUI>::class.java).toList() } ?: emptyList() }
        catch (e: Exception) { emptyList() }
    }
    
    // Current batsmen and bowler
    val striker = match.strikerIndex?.let { idx -> currentBattingTeam.getOrNull(idx) }
    val nonStriker = match.nonStrikerIndex?.let { idx -> currentBattingTeam.getOrNull(idx) }
    val bowler = match.bowlerIndex?.let { idx -> currentBowlingTeam.getOrNull(idx) }
    
    val actualTotalRuns = currentBattingTeam.sumOf { it.runs }
    val totalOvers = matchSettings?.totalOvers ?: 10
    val shortPitch = matchSettings?.shortPitch ?: false
    
    // Read the scorer's own partnership state rather than recomputing it from the delivery log.
    // The old reconstruction counted wides and no-balls as balls faced, and keyed the batting
    // pair off delivery.strikerName - which is written after the post-wicket replacement, so
    // wicket balls carried the incoming batsman (or a blank name) and split stands spuriously.
    val partnershipsState = remember(lastUpdated) {
        match.partnershipsStateJson.parsePartnershipsPersistenceState(gson)
    }
    val currentInningsPartnerships = remember(partnershipsState) {
        val state = partnershipsState ?: return@remember emptyList()
        val liveStand = if (
            state.currentPartnershipBatsman1Name != null &&
            state.currentPartnershipBatsman2Name != null &&
            (state.currentPartnershipRuns > 0 || state.currentPartnershipBalls > 0)
        ) {
            listOf(
                Partnership(
                    batsman1Name = state.currentPartnershipBatsman1Name,
                    batsman2Name = state.currentPartnershipBatsman2Name,
                    runs = state.currentPartnershipRuns,
                    balls = state.currentPartnershipBalls,
                    batsman1Runs = state.currentPartnershipBatsman1Runs,
                    batsman2Runs = state.currentPartnershipBatsman2Runs,
                    isActive = true,
                )
            )
        } else {
            emptyList()
        }
        state.partnerships + liveStand
    }
    val firstInningsPartnerships = remember(partnershipsState) {
        if (match.currentInnings == 2) partnershipsState?.firstInningsPartnerships ?: emptyList()
        else emptyList()
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Live indicator bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            MaterialTheme.colorScheme.error,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("LIVE", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.weight(1f))
                Text("Updated ${getTimeAgo(lastUpdated)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        
        // Score header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "${match.team1Name} vs ${match.team2Name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$battingTeamName: ",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "$actualTotalRuns/${match.totalWickets}",
                        style = ScoreMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "(${match.currentOver}.${match.ballsInOver}/$totalOvers ov)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                // Target info for 2nd innings
                if (match.currentInnings == 2) {
                    val target = match.firstInningsRuns + 1
                    val remaining = target - actualTotalRuns
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Target: $target • Need $remaining from ${(totalOvers * 6) - (match.currentOver * 6 + match.ballsInOver)} balls",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                // First innings score when in 2nd innings
                if (match.currentInnings == 2) {
                    val firstBattingTeam = match.team1Name
                    Text(
                        "$firstBattingTeam: ${match.firstInningsRuns}/${match.firstInningsWickets} (${match.firstInningsOvers}.${match.firstInningsBalls})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
        }
        
        // Tabs
        TabRow(selectedTabIndex = pagerState.currentPage) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(title, style = MaterialTheme.typography.bodySmall) }
                )
            }
        }
        
        // Tab content with swipe
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
        when (page) {
            // ===== LIVE TAB =====
            0 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Current batsmen
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            border = hairline(),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Batting", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            
                            // Header
                            Row(Modifier.fillMaxWidth()) {
                                Text("Batter", modifier = Modifier.weight(2f), style = MicroLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("R", modifier = Modifier.weight(0.6f), style = MicroLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("B", modifier = Modifier.weight(0.6f), style = MicroLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("4s", modifier = Modifier.weight(0.6f), style = MicroLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (!shortPitch) {
                                    Text("6s", modifier = Modifier.weight(0.6f), style = MicroLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("SR", modifier = Modifier.weight(0.8f), style = MicroLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            
                            // Striker
                            if (striker != null) {
                                SpectatorBatterRow(striker, isStriker = true, shortPitch = shortPitch)
                            }
                            // Non-striker
                            if (nonStriker != null) {
                                SpectatorBatterRow(nonStriker, isStriker = false, shortPitch = shortPitch)
                            }
                            
                            if (striker == null && nonStriker == null) {
                                Text("No batsmen at crease", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = FontStyle.Italic)
                            }
                        }
                    }
                    
                    // Current bowler
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            border = hairline(),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Bowling", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.height(8.dp))
                            
                            Row(Modifier.fillMaxWidth()) {
                                Text("Bowler", modifier = Modifier.weight(2f), style = MicroLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("O", modifier = Modifier.weight(0.7f), style = MicroLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("M", modifier = Modifier.weight(0.5f), style = MicroLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("R", modifier = Modifier.weight(0.7f), style = MicroLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("W", modifier = Modifier.weight(0.7f), style = MicroLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Econ", modifier = Modifier.weight(0.9f), style = MicroLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            
                            if (bowler != null) {
                                SpectatorBowlerRow(bowler)
                            } else {
                                Text("No bowler selected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = FontStyle.Italic)
                            }
                        }
                    }
                    
                    // Active partnership
                    val activePartnership = currentInningsPartnerships.lastOrNull { it.isActive }
                    if (activePartnership != null && activePartnership.balls > 0) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            border = hairline(),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Partnership", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        "${activePartnership.runs} (${activePartnership.balls})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${activePartnership.batsman1Name}: ${activePartnership.batsman1Runs}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "${activePartnership.batsman2Name}: ${activePartnership.batsman2Runs}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                    
                    // Recent deliveries (current over)
                    val currentOverDeliveries = deliveries.filter { it.over == match.currentOver + 1 && it.inning == match.currentInnings }
                    if (currentOverDeliveries.isNotEmpty() || deliveries.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            border = hairline(),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("This Over", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.height(8.dp))
                                
                                if (currentOverDeliveries.isNotEmpty()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        currentOverDeliveries.forEach { d ->
                                            Surface(
                                                shape = MaterialTheme.shapes.small,
                                                color = when {
                                                    d.outcome == "W" -> MaterialTheme.colorScheme.errorContainer
                                                    d.outcome.startsWith("Wd") || d.outcome.startsWith("Nb") -> MaterialTheme.colorScheme.tertiaryContainer
                                                    d.outcome == "0" -> MaterialTheme.colorScheme.surfaceVariant
                                                    else -> MaterialTheme.colorScheme.primaryContainer
                                                },
                                                modifier = Modifier.defaultMinSize(minWidth = 32.dp)
                                            ) {
                                                Text(
                                                    d.outcome,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium,
                                                    textAlign = TextAlign.Center,
                                                    color = when {
                                                        d.outcome == "W" -> MaterialTheme.colorScheme.onErrorContainer
                                                        else -> MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Text("New over starting...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = FontStyle.Italic)
                                }
                            }
                        }
                    }
                    
                    // Run Rate info
                    val totalBalls = match.currentOver * 6 + match.ballsInOver
                    if (totalBalls > 0) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            border = hairline(),
                            elevation = CardDefaults.cardElevation(1.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                val crr = actualTotalRuns.toFloat() / (totalBalls / 6.0f)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("CRR".uppercase(), style = MicroLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("%.2f".format(crr), style = StatValue, color = MaterialTheme.colorScheme.onSurface)
                                }
                                if (match.currentInnings == 2) {
                                    val target = match.firstInningsRuns + 1
                                    val remaining = target - actualTotalRuns
                                    val ballsRemaining = (totalOvers * 6) - totalBalls
                                    if (ballsRemaining > 0) {
                                        val rrr = remaining.toFloat() / (ballsRemaining / 6.0f)
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("RRR".uppercase(), style = MicroLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("%.2f".format(rrr), style = StatValue, color = if (rrr > crr) MaterialTheme.colorScheme.error else MaterialTheme.stumpd.success)
                                        }
                                    }
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Extras".uppercase(), style = MicroLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${match.totalExtras}", style = StatValue)
                                }
                            }
                        }
                    }
                }
            }
            
            // ===== SCORECARD TAB =====
            1 -> {
                var currentInningsExpanded by remember { mutableStateOf(true) }
                var firstInningsExpanded by remember { mutableStateOf(false) }
                
                // Derive batting/bowling order from deliveries
                val curBatOrder = remember(lastUpdated) { deriveBattingOrder(deliveries, match.currentInnings) }
                val curBowlOrder = remember(lastUpdated) { deriveBowlingOrder(deliveries, match.currentInnings) }
                val firstBatOrder = remember(lastUpdated) { deriveBattingOrder(deliveries, 1) }
                val firstBowlOrder = remember(lastUpdated) { deriveBowlingOrder(deliveries, 1) }
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Current innings scorecard
                    item {
                        val activeBatters = currentBattingTeam.filter { p ->
                            p.ballsFaced > 0 || p.runs > 0 || p.isRetired ||
                            p.name == striker?.name || p.name == nonStriker?.name
                        }
                        val completedBatters = if (match.currentInnings == 1) completedBattersInnings1 else emptyList()
                        val completedBowlers = if (match.currentInnings == 1) completedBowlersInnings1 else emptyList()
                        val activeBowlers = currentBowlingTeam.filter { it.ballsBowled > 0 || it.wickets > 0 || it.runsConceded > 0 }
                        
                        InningsScorecardCard(
                            title = "Current Innings • $battingTeamName",
                            isExpanded = currentInningsExpanded,
                            onToggleExpand = { currentInningsExpanded = !currentInningsExpanded },
                            battingTeam = battingTeamName,
                            bowlingTeam = bowlingTeamName,
                            batters = (completedBatters + activeBatters).distinctBy { it.name }.sortedByOrder(curBatOrder),
                            bowlers = (completedBowlers + activeBowlers).distinctBy { it.name }.sortedByOrder(curBowlOrder),
                            partnerships = currentInningsPartnerships,
                            striker = striker,
                            nonStriker = nonStriker,
                            shortPitch = shortPitch
                        )
                    }
                    
                    // First innings scorecard (if in 2nd innings)
                    if (match.currentInnings == 2 && firstInningsBattingPlayers.isNotEmpty()) {
                        item {
                            InningsScorecardCard(
                                title = "First Innings • ${match.team1Name}",
                                isExpanded = firstInningsExpanded,
                                onToggleExpand = { firstInningsExpanded = !firstInningsExpanded },
                                battingTeam = match.team1Name,
                                bowlingTeam = match.team2Name,
                                batters = firstInningsBattingPlayers.sortedByOrder(firstBatOrder),
                                bowlers = firstInningsBowlingPlayers.sortedByOrder(firstBowlOrder),
                                partnerships = firstInningsPartnerships,
                                shortPitch = shortPitch
                            )
                        }
                    }
                }
            }
            
            // ===== OVERS TAB =====
            2 -> {
                OversTab(
                    modifier = Modifier.padding(16.dp),
                    allDeliveries = deliveries,
                    firstInningsTeamName = match.team1Name,
                    secondInningsTeamName = match.team2Name,
                )
            }
            
            // ===== PARTNERSHIPS TAB =====
            3 -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Current innings partnerships
                    item {
                        Text(
                            "Innings ${match.currentInnings} • $battingTeamName",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    if (currentInningsPartnerships.isEmpty()) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Text(
                                    "No partnerships yet.",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    } else {
                        items(currentInningsPartnerships.size) { index ->
                            val p = currentInningsPartnerships[index]
                            SpectatorPartnershipCard(
                                partnershipNumber = index + 1,
                                partnership = p,
                                isActive = p.isActive,
                                maxRuns = currentInningsPartnerships.maxOf { it.runs }.coerceAtLeast(1)
                            )
                        }
                    }
                    
                    // First innings partnerships (if 2nd innings)
                    if (match.currentInnings == 2 && firstInningsPartnerships.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Innings 1 • ${match.team1Name}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        items(firstInningsPartnerships.size) { index ->
                            val p = firstInningsPartnerships[index]
                            SpectatorPartnershipCard(
                                partnershipNumber = index + 1,
                                partnership = p,
                                isActive = false,
                                maxRuns = firstInningsPartnerships.maxOf { it.runs }.coerceAtLeast(1)
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
private fun SpectatorBatterRow(player: Player, isStriker: Boolean, shortPitch: Boolean) {
    val sr = if (player.ballsFaced > 0) "%.1f".format((player.runs.toFloat() / player.ballsFaced) * 100) else "0.0"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${if (isStriker) "* " else ""}${player.name}",
            modifier = Modifier.weight(2f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isStriker) FontWeight.Bold else FontWeight.Normal
        )
        Text(player.runs.toString(), modifier = Modifier.weight(0.6f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        Text(player.ballsFaced.toString(), modifier = Modifier.weight(0.6f), style = MaterialTheme.typography.bodySmall)
        Text(player.fours.toString(), modifier = Modifier.weight(0.6f), style = MaterialTheme.typography.bodySmall)
        if (!shortPitch) {
            Text(player.sixes.toString(), modifier = Modifier.weight(0.6f), style = MaterialTheme.typography.bodySmall)
        }
        Text(sr, modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SpectatorBowlerRow(player: Player) {
    val overs = player.ballsBowled / 6
    val balls = player.ballsBowled % 6
    val oversStr = "$overs.$balls"
    val econ = if (overs > 0 || balls > 0) {
        val totalOvers = overs + (balls / 6.0)
        "%.1f".format(player.runsConceded / totalOvers)
    } else "0.0"
    
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(player.name, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        Text(oversStr, modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        Text(player.maidenOvers.toString(), modifier = Modifier.weight(0.5f), style = MaterialTheme.typography.bodySmall)
        Text(player.runsConceded.toString(), modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.bodySmall)
        Text(player.wickets.toString(), modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.bodySmall)
        Text(econ, modifier = Modifier.weight(0.9f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SpectatorPartnershipCard(
    partnershipNumber: Int,
    partnership: Partnership,
    isActive: Boolean,
    maxRuns: Int
) {
    // Active rows are tinted with the accent at low alpha rather than switching container
    // role, so the difference survives palettes where the two roles nearly match.
    val containerColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    else MaterialTheme.colorScheme.surfaceContainer
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(if (isActive) 3.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row: partnership number + total runs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ) {
                        Text(
                            "#$partnershipNumber",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isActive) {
                        Spacer(Modifier.width(6.dp))
                        Text("Active", style = MicroLabel, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${partnership.runs} runs",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${partnership.balls} balls",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Progress bar
            val fraction = (partnership.runs.toFloat() / maxRuns).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.extraSmall
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            shape = MaterialTheme.shapes.extraSmall
                        )
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Player contributions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${partnership.batsman1Name}: ${partnership.batsman1Runs}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${partnership.batsman2Name}: ${partnership.batsman2Runs}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun LiveMatchView(match: MatchHistory, lastUpdated: Long) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Live indicator
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            MaterialTheme.colorScheme.error,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "LIVE",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "Updated ${getTimeAgo(lastUpdated)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Match info
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "${match.team1Name} vs ${match.team2Name}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(match.matchDate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Team 1 score
        ScoreCard(
            teamName = match.team1Name,
            score = match.firstInningsRuns,
            wickets = match.firstInningsWickets
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Team 2 score
        ScoreCard(
            teamName = match.team2Name,
            score = match.secondInningsRuns,
            wickets = match.secondInningsWickets
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Result
        if (match.winnerTeam.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Result",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${match.winnerTeam} won by ${match.winningMargin}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "🔄 Auto-updating live",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun BatsmanRow(
    name: String,
    runs: Int,
    balls: Int,
    isStriker: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isStriker) {
                Text(
                    "*",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
            Text(
                name,
                fontWeight = if (isStriker) FontWeight.Bold else FontWeight.Normal
            )
        }
        
        Text(
            "$runs ($balls)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isStriker) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun ScoreCard(teamName: String, score: Int, wickets: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
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
                teamName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                "$score/$wickets",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

fun getTimeAgo(timestamp: Long): String {
    val secondsAgo = (System.currentTimeMillis() - timestamp) / 1000
    return when {
        secondsAgo < 5 -> "just now"
        secondsAgo < 60 -> "${secondsAgo}s ago"
        else -> "${secondsAgo / 60}m ago"
    }
}
