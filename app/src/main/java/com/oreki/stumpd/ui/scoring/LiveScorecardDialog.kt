package com.oreki.stumpd.ui.scoring

import com.oreki.stumpd.*
import com.oreki.stumpd.domain.model.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    shortPitch: Boolean = false,
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
                        LivePlayerStatCard(player, "batting", shortPitch = shortPitch)
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
    shortPitch: Boolean = false,
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
                    if (player.fours > 0 || (!shortPitch && player.sixes > 0)) {
                        val boundaryText = if (shortPitch) "4s:${player.fours}" else "4s:${player.fours} 6s:${player.sixes}"
                        Text(
                            text = boundaryText,
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
