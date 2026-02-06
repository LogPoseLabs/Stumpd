package com.oreki.stumpd.ui.scoring

import com.oreki.stumpd.*
import com.oreki.stumpd.domain.model.*

import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.oreki.stumpd.data.repository.MatchRepository
import com.oreki.stumpd.data.manager.InProgressMatchManager
import com.oreki.stumpd.data.sync.sharing.MatchSharingManager

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
    firstInningsPartnerships: List<Partnership> = emptyList(),
    secondInningsPartnerships: List<Partnership> = emptyList(),
    firstInningsFallOfWickets: List<FallOfWicket> = emptyList(),
    secondInningsFallOfWickets: List<FallOfWicket> = emptyList(),
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
    val totalPlayersInChasingTeam = matchSettings.maxPlayersPerTeam
    val margin: String? = when {
        isTie -> null
        chasingWon -> "${calculateWicketMargin(secondInningsWickets, totalPlayersInChasingTeam)} wickets"
        else -> "${firstInningsRuns - secondInningsRuns} runs"
    }

    // Build stats for DB — no joker merging needed.
    // Role-based storage (BAT/BOWL) keeps batting and bowling as separate rows,
    // so the joker naturally gets a BAT row and a BOWL row.
    val firstInningsBattingStats = firstInningsBattingPlayers.map { it.toMatchStats(team1Name) }
    val firstInningsBowlingStats = firstInningsBowlingPlayers.map { it.toMatchStats(team2Name) }

    val secondInningsBattingStats = secondInningsBattingPlayers.map { it.toMatchStats(team2Name) }
    val secondInningsBowlingStats = secondInningsBowlingPlayers.map { it.toMatchStats(team1Name) }


    val finalGroupId = groupId ?: "1"
    val finalGroupName = groupName ?: "Default"

    var isMatchSaved by remember { mutableStateOf(false) }
    var savedMatchId by remember { mutableStateOf<String?>(null) }

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
            firstInningsPartnerships = firstInningsPartnerships,
            secondInningsPartnerships = secondInningsPartnerships,
            firstInningsFallOfWickets = firstInningsFallOfWickets,
            secondInningsFallOfWickets = secondInningsFallOfWickets,
            context = context,
            matchSettings = matchSettings,
            groupId = finalGroupId,
            groupName = finalGroupName,
            allDeliveries = allDeliveries.toList()
        )
        scope.launch {
            repo.saveMatch(match)
            savedMatchId = match.id // Store the saved match ID for navigation
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
        onDismissRequest = {
            // When clicking outside the dialog, navigate to scorecard if match is saved
            if (isMatchSaved && savedMatchId != null) {
                val intent = Intent(context, FullScorecardActivity::class.java)
                intent.putExtra("match_id", savedMatchId)
                context.startActivity(intent)
                (context as ComponentActivity).finish()
            }
            onDismiss()
        },
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
                    PlayerStatCard(player, "batting", shortPitch = matchSettings.shortPitch)
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
                    PlayerStatCard(player, "batting", shortPitch = matchSettings.shortPitch)
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
                    if (isMatchSaved && savedMatchId != null) {
                    val intent = Intent(context, FullScorecardActivity::class.java)
                    intent.putExtra("match_id", savedMatchId)
                    context.startActivity(intent)
                    (context as ComponentActivity).finish()
                    } else {
                        Toast.makeText(context, "Please wait, saving match...", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = isMatchSaved && savedMatchId != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isMatchSaved && savedMatchId != null) {
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
    shortPitch: Boolean = false,
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
                val boundaryText = if (shortPitch) "4s:${player.fours}" else "4s:${player.fours} 6s:${player.sixes}"
                Text(
                    text = "${player.runs}${if (!player.isOut && player.ballsFaced > 0) "*" else ""} (${player.ballsFaced}) - $boundaryText",
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

fun calculateWicketMargin(wicketsLost: Int, totalPlayers: Int = 11): Int {
    // Max wickets possible is totalPlayers - 1 (last batter can't be out)
    return (totalPlayers - 1) - wicketsLost
}
