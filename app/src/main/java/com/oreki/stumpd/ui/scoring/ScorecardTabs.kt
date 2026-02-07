package com.oreki.stumpd.ui.scoring

import com.oreki.stumpd.*
import com.oreki.stumpd.domain.model.*

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    context: Context,
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
    onToggleUnlimitedUndo: (Boolean) -> Unit,
    currentPartnershipRuns: Int = 0,
    currentPartnershipBalls: Int = 0,
    currentPartnershipBatsman1Name: String? = null,
    currentPartnershipBatsman2Name: String? = null,
    currentPartnershipBatsman1Runs: Int = 0,
    currentPartnershipBatsman2Runs: Int = 0,
    currentPartnershipBatsman1Balls: Int = 0,
    currentPartnershipBatsman2Balls: Int = 0
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
            shortPitch = matchSettings.shortPitch,
        )

        // Active partnership
        if (currentPartnershipBalls > 0 && currentPartnershipBatsman1Name != null && currentPartnershipBatsman2Name != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Batsman 1
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            currentPartnershipBatsman1Name ?: "",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            "$currentPartnershipBatsman1Runs ($currentPartnershipBatsman1Balls)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Partnership total in center
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$currentPartnershipRuns",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "$currentPartnershipBalls balls",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                    // Batsman 2
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(
                            currentPartnershipBatsman2Name ?: "",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            "$currentPartnershipBatsman2Runs ($currentPartnershipBatsman2Balls)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

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

/**
 * Derive batting order from deliveries — returns names in the order they first appeared at crease.
 */
fun deriveBattingOrder(deliveries: List<DeliveryUI>, inning: Int): List<String> {
    val order = mutableListOf<String>()
    deliveries.filter { it.inning == inning }.forEach { d ->
        if (d.strikerName.isNotBlank() && d.strikerName !in order) order.add(d.strikerName)
        if (d.nonStrikerName.isNotBlank() && d.nonStrikerName !in order) order.add(d.nonStrikerName)
    }
    return order
}

/**
 * Derive bowling order from deliveries — returns names in the order they first bowled.
 */
fun deriveBowlingOrder(deliveries: List<DeliveryUI>, inning: Int): List<String> {
    val order = mutableListOf<String>()
    deliveries.filter { it.inning == inning }.forEach { d ->
        if (d.bowlerName.isNotBlank() && d.bowlerName !in order) order.add(d.bowlerName)
    }
    return order
}

/**
 * Sort players according to an ordered name list. Players not in the order come last.
 */
fun List<Player>.sortedByOrder(order: List<String>): List<Player> {
    val orderMap = order.withIndex().associate { (i, name) -> name to i }
    return sortedBy { orderMap[it.name] ?: Int.MAX_VALUE }
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
    allDeliveries: List<DeliveryUI> = emptyList(),
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
    currentPartnershipBatsman2Name: String? = null,
    shortPitch: Boolean = false
) {
    var currentInningsExpanded by remember { mutableStateOf(true) }
    var firstInningsExpanded by remember { mutableStateOf(false) }
    
    // Derive batting/bowling order from deliveries
    val currentBattingOrder = remember(allDeliveries.size, currentInnings) {
        deriveBattingOrder(allDeliveries, currentInnings)
    }
    val currentBowlingOrder = remember(allDeliveries.size, currentInnings) {
        deriveBowlingOrder(allDeliveries, currentInnings)
    }
    val firstInningsBattingOrder = remember(allDeliveries.size) {
        deriveBattingOrder(allDeliveries, 1)
    }
    val firstInningsBowlingOrder = remember(allDeliveries.size) {
        deriveBowlingOrder(allDeliveries, 1)
    }
    
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Current Innings - Collapsible
        item {
            val completedBatters = if (currentInnings == 1) completedBattersInnings1 else completedBattersInnings2
            val completedBowlers = if (currentInnings == 1) completedBowlersInnings1 else completedBowlersInnings2
            val activeBatters = battingTeamPlayers.filter { player ->
                player.ballsFaced > 0 || player.runs > 0 || player.isRetired ||
                player.name == striker?.name || player.name == nonStriker?.name
            }
            val activeBowlers = bowlingTeamPlayers.filter { it.ballsBowled > 0 || it.wickets > 0 || it.runsConceded > 0 }
            
            InningsScorecardCard(
                title = "Current Innings • $battingTeamName batting",
                isExpanded = currentInningsExpanded,
                onToggleExpand = { currentInningsExpanded = !currentInningsExpanded },
                battingTeam = battingTeamName,
                bowlingTeam = bowlingTeamName,
                batters = (completedBatters + activeBatters).distinctBy { it.name }.sortedByOrder(currentBattingOrder),
                bowlers = (completedBowlers + activeBowlers).distinctBy { it.name }.sortedByOrder(currentBowlingOrder),
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
                currentPartnershipBatsman2Name = currentPartnershipBatsman2Name,
                shortPitch = shortPitch
            )
        }

        
        // First Innings (if in 2nd innings) - Collapsible
        if (currentInnings == 2 && firstInningsBattingPlayersList.isNotEmpty()) {
            item {
                InningsScorecardCard(
                    title = "First Innings",
                    isExpanded = firstInningsExpanded,
                    onToggleExpand = { firstInningsExpanded = !firstInningsExpanded },
                    battingTeam = "",
                    bowlingTeam = "",
                    batters = firstInningsBattingPlayersList.sortedByOrder(firstInningsBattingOrder),
                    bowlers = firstInningsBowlingPlayersList.sortedByOrder(firstInningsBowlingOrder),
                    partnerships = firstInningsPartnerships,
                    fallOfWickets = firstInningsFallOfWickets,
                    striker = null,
                    nonStriker = null,
                    currentPartnershipRuns = 0,
                    currentPartnershipBalls = 0,
                    currentPartnershipBatsman1Runs = 0,
                    currentPartnershipBatsman2Runs = 0,
                    shortPitch = shortPitch
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
    currentPartnershipBatsman2Name: String? = null,
    shortPitch: Boolean = false
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
                        if (!shortPitch) {
                            Text("6s", modifier = Modifier.weight(0.7f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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
                                if (!shortPitch) {
                                    Text(player.sixes.toString(), modifier = Modifier.weight(0.7f), fontSize = 13.sp)
                                }
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
                        Text("M", modifier = Modifier.weight(0.5f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Text(player.maidenOvers.toString(), modifier = Modifier.weight(0.5f), fontSize = 13.sp)
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
