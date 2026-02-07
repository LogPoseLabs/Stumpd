package com.oreki.stumpd

import com.oreki.stumpd.domain.model.*
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
import com.oreki.stumpd.data.sync.realtime.RealTimeMatchListener
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.google.gson.Gson
import kotlinx.coroutines.launch

/**
 * Spectator Activity - Read-only live view of a match
 * Updates in real-time as the match progresses
 */
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
    val scope = rememberCoroutineScope()
    
    var matchState by remember { mutableStateOf<com.oreki.stumpd.data.local.entity.InProgressMatchEntity?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lastUpdated by remember { mutableStateOf<Long>(0L) }
    
    // Start real-time listener for in-progress match
    LaunchedEffect(matchId) {
        try {
            val listener = RealTimeMatchListener()
            
            // Listen to IN-PROGRESS match
            listener.listenToInProgressMatch(ownerId, matchId).collect { match ->
                if (match != null) {
                    val newTimestamp = System.currentTimeMillis()
                    Log.d("SpectatorActivity", "=== Match update received ===")
                    Log.d("SpectatorActivity", "Match ID: ${match.matchId}")
                    Log.d("SpectatorActivity", "Innings: ${match.currentInnings}")
                    Log.d("SpectatorActivity", "Over: ${match.currentOver}.${match.ballsInOver}")
                    Log.d("SpectatorActivity", "Wickets: ${match.totalWickets}")
                    Log.d("SpectatorActivity", "Last updated diff: ${newTimestamp - lastUpdated}ms")
                    
                    matchState = match
                    lastUpdated = newTimestamp
                    isLoading = false
                } else {
                    Log.w("SpectatorActivity", "Received null match update")
                }
            }
        } catch (e: Exception) {
            Log.e("SpectatorActivity", "Failed to start listener", e)
            errorMessage = "Failed to connect: ${e.message}"
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Live Match Spectator")
                        if (shareCode.isNotEmpty()) {
                            Text(
                                "Code: $shareCode",
                                fontSize = 12.sp,
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
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
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
                errorMessage != null -> {
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
                            text = errorMessage ?: "Unknown error",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                matchState == null -> {
                    Text(
                        text = "Match not found or ended",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                else -> {
                    // Show match data - use key to force recomposition on data changes
                    key(lastUpdated) {
                        LiveInProgressMatchView(matchState!!, lastUpdated)
                    }
                }
            }
        }
    }
}

@Composable
fun LiveInProgressMatchView(match: com.oreki.stumpd.data.local.entity.InProgressMatchEntity, lastUpdated: Long) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Live", "Scorecard", "Overs", "Partnerships")
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
    
    val currentBattingTeam = if (match.currentInnings == 1) team1Players else team2Players
    val currentBowlingTeam = if (match.currentInnings == 1) team2Players else team1Players
    val battingTeamName = if (match.currentInnings == 1) match.team1Name else match.team2Name
    val bowlingTeamName = if (match.currentInnings == 1) match.team2Name else match.team1Name
    
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
    
    // Compute partnerships from deliveries
    val currentInningsPartnerships = remember(lastUpdated) {
        buildPartnershipsFromDeliveries(deliveries.filter { it.inning == match.currentInnings })
    }
    val firstInningsPartnerships = remember(lastUpdated) {
        if (match.currentInnings == 2) buildPartnershipsFromDeliveries(deliveries.filter { it.inning == 1 })
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
                Text("LIVE", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.weight(1f))
                Text("Updated ${getTimeAgo(lastUpdated)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer)
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
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
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
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                // First innings score when in 2nd innings
                if (match.currentInnings == 2) {
                    val firstBattingTeam = match.team1Name
                    Text(
                        "$firstBattingTeam: ${match.firstInningsRuns}/${match.firstInningsWickets} (${match.firstInningsOvers}.${match.firstInningsBalls})",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
        }
        
        // Tabs
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title, fontSize = 13.sp) }
                )
            }
        }
        
        // Tab content
        when (selectedTabIndex) {
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
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Batting", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            
                            // Header
                            Row(Modifier.fillMaxWidth()) {
                                Text("Batter", modifier = Modifier.weight(2f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("R", modifier = Modifier.weight(0.6f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("B", modifier = Modifier.weight(0.6f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("4s", modifier = Modifier.weight(0.6f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (!shortPitch) {
                                    Text("6s", modifier = Modifier.weight(0.6f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("SR", modifier = Modifier.weight(0.8f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Text("No batsmen at crease", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = FontStyle.Italic)
                            }
                        }
                    }
                    
                    // Current bowler
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Bowling", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.height(8.dp))
                            
                            Row(Modifier.fillMaxWidth()) {
                                Text("Bowler", modifier = Modifier.weight(2f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("O", modifier = Modifier.weight(0.7f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("M", modifier = Modifier.weight(0.5f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("R", modifier = Modifier.weight(0.7f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("W", modifier = Modifier.weight(0.7f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Econ", modifier = Modifier.weight(0.9f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            
                            if (bowler != null) {
                                SpectatorBowlerRow(bowler)
                            } else {
                                Text("No bowler selected", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = FontStyle.Italic)
                            }
                        }
                    }
                    
                    // Active partnership
                    val activePartnership = currentInningsPartnerships.lastOrNull { it.isActive }
                    if (activePartnership != null && activePartnership.balls > 0) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Partnership", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        "${activePartnership.runs} (${activePartnership.balls})",
                                        fontSize = 16.sp,
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
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "${activePartnership.batsman2Name}: ${activePartnership.batsman2Runs}",
                                        fontSize = 13.sp,
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
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("This Over", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
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
                                                    fontSize = 13.sp,
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
                                    Text("New over starting...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = FontStyle.Italic)
                                }
                            }
                        }
                    }
                    
                    // Run Rate info
                    val totalBalls = match.currentOver * 6 + match.ballsInOver
                    if (totalBalls > 0) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            elevation = CardDefaults.cardElevation(1.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                val crr = actualTotalRuns.toFloat() / (totalBalls / 6.0f)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("CRR", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("%.2f".format(crr), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                if (match.currentInnings == 2) {
                                    val target = match.firstInningsRuns + 1
                                    val remaining = target - actualTotalRuns
                                    val ballsRemaining = (totalOvers * 6) - totalBalls
                                    if (ballsRemaining > 0) {
                                        val rrr = remaining.toFloat() / (ballsRemaining / 6.0f)
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("RRR", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("%.2f".format(rrr), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (rrr > crr) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary)
                                        }
                                    }
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Extras", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${match.totalExtras}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                    allDeliveries = deliveries
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
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    if (currentInningsPartnerships.isEmpty()) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Text(
                                    "No partnerships yet.",
                                    modifier = Modifier.padding(16.dp),
                                    fontSize = 14.sp,
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
                                fontSize = 15.sp,
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
            fontSize = 13.sp,
            fontWeight = if (isStriker) FontWeight.Bold else FontWeight.Normal
        )
        Text(player.runs.toString(), modifier = Modifier.weight(0.6f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(player.ballsFaced.toString(), modifier = Modifier.weight(0.6f), fontSize = 13.sp)
        Text(player.fours.toString(), modifier = Modifier.weight(0.6f), fontSize = 13.sp)
        if (!shortPitch) {
            Text(player.sixes.toString(), modifier = Modifier.weight(0.6f), fontSize = 13.sp)
        }
        Text(sr, modifier = Modifier.weight(0.8f), fontSize = 13.sp)
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
        Text(player.name, modifier = Modifier.weight(2f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(oversStr, modifier = Modifier.weight(0.7f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(player.maidenOvers.toString(), modifier = Modifier.weight(0.5f), fontSize = 13.sp)
        Text(player.runsConceded.toString(), modifier = Modifier.weight(0.7f), fontSize = 13.sp)
        Text(player.wickets.toString(), modifier = Modifier.weight(0.7f), fontSize = 13.sp)
        Text(econ, modifier = Modifier.weight(0.9f), fontSize = 13.sp)
    }
}

/**
 * Compute partnerships from ball-by-ball deliveries for a single innings.
 * A new partnership starts whenever the pair of batsmen changes (wicket fell).
 */
private fun buildPartnershipsFromDeliveries(inningsDeliveries: List<DeliveryUI>): List<Partnership> {
    if (inningsDeliveries.isEmpty()) return emptyList()
    
    val partnerships = mutableListOf<Partnership>()
    
    var currentPair: Pair<String, String>? = null
    var pRuns = 0
    var pBalls = 0
    var b1Runs = 0
    var b2Runs = 0
    
    for (delivery in inningsDeliveries) {
        val bat1 = minOf(delivery.strikerName, delivery.nonStrikerName)
        val bat2 = maxOf(delivery.strikerName, delivery.nonStrikerName)
        if (bat1.isBlank() && bat2.isBlank()) continue
        
        val pair = bat1 to bat2
        
        if (currentPair != null && pair != currentPair) {
            // Partnership ended
            partnerships.add(
                Partnership(
                    batsman1Name = currentPair.first,
                    batsman2Name = currentPair.second,
                    runs = pRuns,
                    balls = pBalls,
                    batsman1Runs = b1Runs,
                    batsman2Runs = b2Runs,
                    isActive = false
                )
            )
            pRuns = 0; pBalls = 0; b1Runs = 0; b2Runs = 0
        }
        
        currentPair = pair
        pRuns += delivery.runs
        pBalls++
        // Attribute runs to the striker
        if (delivery.strikerName == bat1) {
            b1Runs += delivery.runs
        } else {
            b2Runs += delivery.runs
        }
    }
    
    // Add the final (possibly active) partnership
    if (currentPair != null) {
        partnerships.add(
            Partnership(
                batsman1Name = currentPair.first,
                batsman2Name = currentPair.second,
                runs = pRuns,
                balls = pBalls,
                batsman1Runs = b1Runs,
                batsman2Runs = b2Runs,
                isActive = true
            )
        )
    }
    
    return partnerships
}

@Composable
private fun SpectatorPartnershipCard(
    partnershipNumber: Int,
    partnership: Partnership,
    isActive: Boolean,
    maxRuns: Int
) {
    val containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    else MaterialTheme.colorScheme.surfaceContainerHigh
    
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
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isActive) {
                        Spacer(Modifier.width(6.dp))
                        Text("Active", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${partnership.runs} runs",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${partnership.balls} balls",
                        fontSize = 11.sp,
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
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${partnership.batsman2Name}: ${partnership.batsman2Runs}",
                    fontSize = 13.sp,
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
                    fontSize = 12.sp,
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
                    fontSize = 12.sp,
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
