package com.oreki.stumpd.ui.scoring

import com.oreki.stumpd.*
import com.oreki.stumpd.domain.model.*
import com.oreki.stumpd.ui.components.InningsPillOption
import com.oreki.stumpd.ui.components.InningsPillRow

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.graphicsLayer
import com.oreki.stumpd.ui.theme.StumpdMotion
import com.oreki.stumpd.domain.match.effectiveRuns
import com.oreki.stumpd.domain.match.isWicket
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LiveScoreTab(
    state: LiveScoreUiState,
    actions: LiveScoreActions,
    battingTeamPlayers: List<Player>,
    striker: Player?,
    nonStriker: Player?,
    bowler: Player?,
    jokerPlayer: Player?,
    currentOverDeliveries: List<DeliveryUI>,
    modifier: Modifier = Modifier,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
) {
    // Destructured so the body below reads as it did when these were 42 separate parameters.
    val battingTeamName = state.battingTeamName
    val currentInnings = state.currentInnings
    val matchSettings = state.matchSettings
    val calculatedTotalRuns = state.totalRuns
    val totalWickets = state.totalWickets
    val currentOver = state.currentOver
    val ballsInOver = state.ballsInOver
    val totalExtras = state.totalExtras
    val firstInningsRuns = state.firstInningsRuns
    val showSingleSideLayout = state.showSingleSideLayout
    val availableBatsmen = state.availableBatsmen
    val currentBowlerSpell = state.currentBowlerSpell
    val isInningsComplete = state.isInningsComplete
    val isPowerplayActive = state.isPowerplayActive
    val currentPartnershipRuns = state.partnership.runs
    val currentPartnershipBalls = state.partnership.balls
    val currentPartnershipBatsman1Name = state.partnership.batsman1Name
    val currentPartnershipBatsman2Name = state.partnership.batsman2Name
    val currentPartnershipBatsman1Runs = state.partnership.batsman1Runs
    val currentPartnershipBatsman2Runs = state.partnership.batsman2Runs
    val currentPartnershipBatsman1Balls = state.partnership.batsman1Balls
    val currentPartnershipBatsman2Balls = state.partnership.batsman2Balls
    val onSelectStriker = actions.onSelectStriker
    val onSelectNonStriker = actions.onSelectNonStriker
    val onSelectBowler = actions.onSelectBowler
    val onSwapStrike = actions.onSwapStrike
    val onScoreRuns = actions.onScoreRuns
    val onShowExtras = actions.onShowExtras
    val onShowWicket = actions.onShowWicket
    val onUndo = actions.onUndo
    val onWide = actions.onWide
    val onRetire = actions.onRetire
    val onFix = actions.onFix
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(2.dp))
        ScoreHeaderCard(
            battingTeamName = battingTeamName,
            currentInnings = currentInnings,
            runsToChase = state.runsToChase,
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
            maxOversPerBowler = matchSettings.maxOversPerBowler,
            shortPitch = matchSettings.shortPitch,
        )

        // Active partnership (show when there is progress: runs from extras-only balls or off the bat)
        if ((currentPartnershipRuns > 0 || currentPartnershipBalls > 0) &&
            currentPartnershipBatsman1Name != null && currentPartnershipBatsman2Name != null) {
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
            widthSizeClass = widthSizeClass,
            onScoreRuns = onScoreRuns,
            onShowExtras = onShowExtras,
            onShowWicket = onShowWicket,
            onUndo = onUndo,
            onWide = onWide,
            onRetire = onRetire,
            onFix = onFix,
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
                            // The ball just bowled scales in, so the eye is drawn to what
                            // changed rather than having to re-read the whole over.
                            val isLatest = index == currentOverDeliveries.lastIndex
                            val landed = remember(d, index) { Animatable(if (isLatest) 0.7f else 1f) }
                            LaunchedEffect(d, index) { if (isLatest) landed.animateTo(1f, StumpdMotion.springy()) }

                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = when {
                                    d.outcome == "W" -> MaterialTheme.colorScheme.errorContainer
                                    d.highlight -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 32.dp)
                                    .graphicsLayer { scaleX = landed.value; scaleY = landed.value }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        d.outcome,
                                        style = MaterialTheme.typography.labelMedium,
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
    // In the second innings the two innings are picked with pills rather than stacked as
    // collapsible cards, which is how the saved scorecard reads too.
    val hasFirstInnings = currentInnings == 2 && firstInningsBattingPlayersList.isNotEmpty()
    var showingFirstInnings by remember(hasFirstInnings) { mutableStateOf(false) }
    
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
    
    Column(modifier = modifier.fillMaxSize()) {
        InningsPillRow(
            options = listOf(
                InningsPillOption(battingTeamName, "batting"),
                InningsPillOption(bowlingTeamName, "1st innings"),
            ).takeIf { hasFirstInnings }.orEmpty(),
            selectedIndex = if (showingFirstInnings) 1 else 0,
            onSelect = { showingFirstInnings = it == 1 },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
        // The innings the pills have selected — only one is on screen at a time.
        if (!showingFirstInnings) item {
            val completedBatters = if (currentInnings == 1) completedBattersInnings1 else completedBattersInnings2
            val completedBowlers = if (currentInnings == 1) completedBowlersInnings1 else completedBowlersInnings2
            val activeBatters = battingTeamPlayers.filter { player ->
                player.ballsFaced > 0 || player.runs > 0 || player.isRetired ||
                player.name == striker?.name || player.name == nonStriker?.name
            }
            val activeBowlers = bowlingTeamPlayers.filter { it.ballsBowled > 0 || it.wickets > 0 || it.runsConceded > 0 }
            
            InningsScorecardCard(
                title = "Current Innings • $battingTeamName batting",
                isExpanded = true,
                onToggleExpand = {},
                collapsible = false,
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

        
        if (showingFirstInnings && hasFirstInnings) {
            item {
                InningsScorecardCard(
                    title = "First Innings • $bowlingTeamName",
                    isExpanded = true,
                    onToggleExpand = {},
                    collapsible = false,
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
}

/**
 * One innings in one card: batting, bowling, partnerships and fall of wickets.
 *
 * Shared by live scoring, the spectator view and the saved Full Scorecard. The optional
 * parameters are the things only some of those callers know — a finished innings has a final
 * score, extras and a list of players who never batted, a live one has a partnership still going.
 */
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
    shortPitch: Boolean = false,
    totalRuns: Int? = null,
    totalWickets: Int? = null,
    ballsBowled: Int? = null,
    extras: Int? = null,
    didNotBat: List<String> = emptyList(),
    didNotBowl: List<String> = emptyList(),
    bowlerWidesAndNoBalls: Map<String, Pair<Int, Int>> = emptyMap(),
    /** False where only one innings is on screen at a time, so there is nothing to collapse to. */
    collapsible: Boolean = true,
    /**
     * Tap-to-fix hooks, set only when the saved scorecard is in fix mode.
     *
     * Null at every other call site — live scoring and the spectator view have no corrections to
     * offer — so the rows behave exactly as before unless somebody has deliberately unlocked
     * editing. They hand back the row, not its index: the caller re-resolves by name against the
     * saved match, because [Player] has already lost the team and role that would identify it.
     */
    onFixBatter: ((Player) -> Unit)? = null,
    onFixBowler: ((Player) -> Unit)? = null,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Header — always visible, and carries the score so a collapsed innings still informs.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (collapsible) Modifier.clickable { onToggleExpand() } else Modifier)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (totalRuns != null && totalWickets != null) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            if (battingTeam.isNotBlank()) {
                                Text(
                                    text = battingTeam,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "$totalRuns/$totalWickets",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val balls = ballsBowled ?: 0
                            if (balls > 0) {
                                Text(
                                    text = "(${formatBallsAsOvers(balls)} ov) · RR " +
                                        String.format("%.2f", runRatePerOver(totalRuns, balls)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = muted
                                )
                            }
                        }
                    }
                }
                if (collapsible) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (isExpanded) {
                HorizontalDivider()

                // Batting
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "BATTING",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        ScorecardHeaderCell("Batter", 2f)
                        ScorecardHeaderCell("R", 0.7f)
                        ScorecardHeaderCell("B", 0.7f)
                        ScorecardHeaderCell("4s", 0.7f)
                        if (!shortPitch) ScorecardHeaderCell("6s", 0.7f)
                        ScorecardHeaderCell("SR", 0.9f)
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    batters.forEach { player ->
                        val sr = if (player.ballsFaced > 0)
                            String.format("%.1f", (player.runs.toFloat() / player.ballsFaced) * 100)
                        else "0.0"

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (onFixBatter != null)
                                        Modifier.clickable { onFixBatter(player) }
                                    else Modifier
                                )
                                .padding(vertical = 4.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    player.name,
                                    modifier = Modifier.weight(2f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (!player.isOut) FontWeight.Bold else FontWeight.Normal
                                )
                                ScorecardCell(player.runs.toString(), 0.7f, FontWeight.SemiBold)
                                ScorecardCell(player.ballsFaced.toString(), 0.7f)
                                ScorecardCell(player.fours.toString(), 0.7f)
                                if (!shortPitch) ScorecardCell(player.sixes.toString(), 0.7f)
                                ScorecardCell(sr, 0.9f)
                            }
                            if (player.isOut || player.isRetired) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 4.dp, top = 1.dp),
                                ) {
                                    Text(
                                        text = player.getDismissalText(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (onFixBatter != null) MaterialTheme.colorScheme.primary else muted,
                                        fontStyle = FontStyle.Italic,
                                    )
                                    if (onFixBatter != null) {
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Correct this dismissal",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Extras and total only reconcile once an innings is saved.
                    if (totalRuns != null && totalWickets != null) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        if (extras != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Extras", style = MaterialTheme.typography.bodySmall, color = muted)
                                Text(
                                    extras.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Total",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                buildString {
                                    append("$totalRuns/$totalWickets")
                                    val balls = ballsBowled ?: 0
                                    if (balls > 0) append(" (${formatBallsAsOvers(balls)} ov)")
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (didNotBat.isNotEmpty()) {
                        Text(
                            text = "Did not bat: ${didNotBat.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }

                HorizontalDivider()

                // Bowling
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "BOWLING",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        ScorecardHeaderCell("Bowler", 2f)
                        ScorecardHeaderCell("O", 0.7f)
                        ScorecardHeaderCell("M", 0.5f)
                        ScorecardHeaderCell("R", 0.7f)
                        ScorecardHeaderCell("W", 0.7f)
                        ScorecardHeaderCell("Econ", 0.9f)
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    bowlers.forEach { player ->
                        val econ = if (player.ballsBowled > 0)
                            String.format("%.1f", player.economy)
                        else "0.0"
                        val (wides, noBalls) = bowlerWidesAndNoBalls[player.name] ?: (0 to 0)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (onFixBowler != null)
                                        Modifier.clickable { onFixBowler(player) }
                                    else Modifier
                                )
                                .padding(vertical = 4.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    player.name,
                                    modifier = Modifier.weight(2f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (onFixBowler != null) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                )
                                ScorecardCell(formatBallsAsOvers(player.ballsBowled), 0.7f, FontWeight.SemiBold)
                                ScorecardCell(player.maidenOvers.toString(), 0.5f)
                                ScorecardCell(player.runsConceded.toString(), 0.7f)
                                ScorecardCell(player.wickets.toString(), 0.7f)
                                ScorecardCell(econ, 0.9f)
                            }
                            if (wides > 0 || noBalls > 0) {
                                val extrasList = buildList {
                                    if (wides > 0) add("$wides wd")
                                    if (noBalls > 0) add("$noBalls nb")
                                }
                                Text(
                                    extrasList.joinToString(", "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = muted,
                                    fontStyle = FontStyle.Italic,
                                    modifier = Modifier.padding(start = 4.dp, top = 1.dp)
                                )
                            }
                        }
                    }

                    if (didNotBowl.isNotEmpty()) {
                        Text(
                            text = "Did not bowl: ${didNotBowl.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }

                // Partnerships — collapsible, and includes the one still in progress when live.
                val hasLivePartnership = striker != null && nonStriker != null &&
                    (currentPartnershipRuns > 0 || currentPartnershipBalls > 0)
                if (partnerships.isNotEmpty() || hasLivePartnership) {
                    var partnershipsExpanded by remember { mutableStateOf(false) }

                    HorizontalDivider()
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        ScorecardSectionHeader(
                            label = "PARTNERSHIPS",
                            count = partnerships.size + if (hasLivePartnership) 1 else 0,
                            isExpanded = partnershipsExpanded,
                            tint = MaterialTheme.colorScheme.secondary,
                            onToggle = { partnershipsExpanded = !partnershipsExpanded }
                        )

                        if (partnershipsExpanded) {
                            if (hasLivePartnership) {
                                PartnershipRow(
                                    label = "current",
                                    runs = currentPartnershipRuns,
                                    balls = currentPartnershipBalls,
                                    batsman1 = "${currentPartnershipBatsman1Name ?: ""}: " +
                                        "$currentPartnershipBatsman1Runs* ($currentPartnershipBatsman1Balls)",
                                    batsman2 = "${currentPartnershipBatsman2Name ?: ""}: " +
                                        "$currentPartnershipBatsman2Runs* ($currentPartnershipBatsman2Balls)",
                                    highlight = true
                                )
                            }
                            partnerships.forEachIndexed { index, partnership ->
                                val wicket = index + 1
                                val suffix = when {
                                    wicket % 10 == 1 && wicket != 11 -> "st"
                                    wicket % 10 == 2 && wicket != 12 -> "nd"
                                    wicket % 10 == 3 && wicket != 13 -> "rd"
                                    else -> "th"
                                }
                                PartnershipRow(
                                    label = "$wicket$suffix wkt",
                                    runs = partnership.runs,
                                    balls = partnership.balls,
                                    batsman1 = "${partnership.batsman1Name}: ${partnership.batsman1Runs}",
                                    batsman2 = "${partnership.batsman2Name}: ${partnership.batsman2Runs}",
                                    highlight = partnership.isActive
                                )
                            }
                        }
                    }
                }

                // Fall of wickets — collapsible
                if (fallOfWickets.isNotEmpty()) {
                    var fallOfWicketsExpanded by remember { mutableStateOf(false) }

                    HorizontalDivider()
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        ScorecardSectionHeader(
                            label = "FALL OF WICKETS",
                            count = fallOfWickets.size,
                            isExpanded = fallOfWicketsExpanded,
                            tint = MaterialTheme.colorScheme.error,
                            onToggle = { fallOfWicketsExpanded = !fallOfWicketsExpanded }
                        )

                        if (fallOfWicketsExpanded) {
                            fallOfWickets.forEach { fow ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${fow.wicketNumber}-${fow.runs}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        fow.batsmanName,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                    )
                                    Text(
                                        "${String.format("%.1f", fow.overs)} ov",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = muted
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.ScorecardHeaderCell(label: String, weight: Float) {
    Text(
        label,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun RowScope.ScorecardCell(
    value: String,
    weight: Float,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    Text(
        value,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = fontWeight
    )
}

/** Sub-section header inside an innings card: label, item count, and a chevron. */
@Composable
private fun ScorecardSectionHeader(
    label: String,
    count: Int,
    isExpanded: Boolean,
    tint: androidx.compose.ui.graphics.Color,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$label ($count)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = tint
        )
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun PartnershipRow(
    label: String,
    runs: Int,
    balls: Int,
    batsman1: String,
    batsman2: String,
    highlight: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (highlight) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "$runs ($balls)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (highlight) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(batsman1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(batsman2, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun OversTab(
    modifier: Modifier = Modifier,
    allDeliveries: List<DeliveryUI>,
    /** Team names for the innings pills. Without them the pills read "First/Second Innings". */
    firstInningsTeamName: String = "",
    secondInningsTeamName: String = "",
) {
    val hasSecondInnings = allDeliveries.any { it.inning == 2 }
    var selectedInnings by remember(hasSecondInnings) {
        mutableStateOf(if (hasSecondInnings) 2 else 1)
    }
    val deliveries = remember(allDeliveries, selectedInnings) {
        allDeliveries.filter { it.inning == selectedInnings }
    }

    Column(modifier = modifier.fillMaxSize()) {
        InningsPillRow(
            options = listOf(
                InningsPillOption(firstInningsTeamName.ifBlank { "First Innings" }, "1st innings"),
                InningsPillOption(secondInningsTeamName.ifBlank { "Second Innings" }, "2nd innings"),
            ).takeIf { hasSecondInnings }.orEmpty(),
            selectedIndex = selectedInnings - 1,
            onSelect = { selectedInnings = it + 1 },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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
        
        if (deliveries.isEmpty()) {
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
            run {
                // Group by over within the selected innings
                val deliveriesByOver = deliveries.groupBy { it.over }
                
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
                                val runsInOver = overDeliveries.sumOf { it.effectiveRuns() }
                                val wicketsInOver = overDeliveries.count { it.isWicket() }
                                
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
