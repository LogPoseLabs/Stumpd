package com.oreki.stumpd.ui.scoring

import com.oreki.stumpd.*
import com.oreki.stumpd.domain.model.SuperOverInnings
import com.oreki.stumpd.domain.match.assembleMatch
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
import com.oreki.stumpd.ui.theme.stumpd
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.oreki.stumpd.data.repository.MatchRepository
import com.oreki.stumpd.data.manager.InProgressMatchManager
import com.oreki.stumpd.ui.history.rememberTournamentRepository
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
    allDeliveries: List<DeliveryUI> = emptyList(),
    /**
     * The eliminator's outcome, when there was one.
     *
     * This dialog writes the match as soon as it composes, so it is only ever shown once the
     * result is settled — either the scores were not level, or a super over decided it, or the
     * scorer chose to leave it a tie.
     */
    superOverWinner: String? = null,
    superOvers: List<SuperOverInnings> = emptyList(),
    /**
     * The tournament fixture this match settles, when it is one.
     *
     * [team1Id] and [team2Id] follow [team1Name] and [team2Name] — that is, batted first and
     * bowled first — because that is the order a saved match stores.
     */
    tournamentId: String? = null,
    tournamentFixtureId: String? = null,
    team1Id: String? = null,
    team2Id: String? = null
) {
    val context = LocalContext.current
    val tournamentRepo = rememberTournamentRepository()

    // Result computation (tie-safe), shared with the correction path so a changed score can be
    // re-resolved rather than leaving a stale winner and margin behind.
    val result = resolveMatchResult(
        team1Name = team1Name,
        team2Name = team2Name,
        firstInningsRuns = firstInningsRuns,
        secondInningsRuns = secondInningsRuns,
        secondInningsWickets = secondInningsWickets,
        chasingSquadSize = secondInningsBattingPlayers.size
            .takeIf { it > 0 } ?: matchSettings.maxPlayersPerTeam,
        allowSingleSideBatting = matchSettings.allowSingleSideBatting,
        superOverWinner = superOverWinner,
    )
    val isTie = result.winnerTeam == "TIE"

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
        val match = assembleMatch(
            team1Name = team1Name,
            team2Name = team2Name,
            jokerPlayerName = jokerPlayerName,
            team1CaptainName = team1CaptainName,
            team2CaptainName = team2CaptainName,
            firstInningsRuns = firstInningsRuns,
            firstInningsWickets = firstInningsWickets,
            secondInningsRuns = secondInningsRuns,
            secondInningsWickets = secondInningsWickets,
            winnerTeam = result.winnerTeam,
            winningMargin = result.winningMargin,
            firstInningsBattingStats = firstInningsBattingStats,
            firstInningsBowlingStats = firstInningsBowlingStats,
            secondInningsBattingStats = secondInningsBattingStats,
            secondInningsBowlingStats = secondInningsBowlingStats,
            firstInningsPartnerships = firstInningsPartnerships,
            secondInningsPartnerships = secondInningsPartnerships,
            firstInningsFallOfWickets = firstInningsFallOfWickets,
            secondInningsFallOfWickets = secondInningsFallOfWickets,
            matchSettings = matchSettings,
            groupId = finalGroupId,
            groupName = finalGroupName,
            allDeliveries = allDeliveries.toList(),
            superOverWinner = superOverWinner,
            superOvers = superOvers,
            tournamentId = tournamentId,
            tournamentFixtureId = tournamentFixtureId,
            team1Id = team1Id,
            team2Id = team2Id
        )
        scope.launch {
            repo.saveMatch(match)
            savedMatchId = match.id // Store the saved match ID for navigation

            // Close out the fixture, if this was one. After the save and never in front of it:
            // a tournament failure must not be able to lose a played match. The table and the
            // bracket are derived on read, so this only has to record which match it was.
            if (tournamentFixtureId != null) {
                tournamentRepo.recordFixtureResult(tournamentFixtureId, match.id)
                    .onFailure {
                        android.util.Log.e("MatchComplete", "Fixture not closed out", it)
                    }
            }
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
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.stumpd.successContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                // Phrased the way the rest of the app phrases it — "won by Super
                                // Over" reads like a margin, which it isn't.
                                text = when {
                                    isTie && superOverWinner != null -> "Match Tied after the Super Over"
                                    isTie -> "Match Tied"
                                    result.winningMargin == "Super Over" ->
                                        "${result.winnerTeam} won the Super Over"

                                    else -> "${result.winnerTeam} won by ${result.winningMargin}"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isTie)
                                    MaterialTheme.colorScheme.tertiary
                                else
                                    MaterialTheme.colorScheme.primary,
                            )

                            // The super over is offered *before* the match is written — see
                            // ScoringEngine.finishSecondInnings — so by the time this dialog is on
                            // screen the result is settled and there is nothing to offer here.
                        }
                    }
                }

                // Team 1 summary
                item {
                    Text(
                        text = "$team1Name - 1st Innings: $firstInningsRuns/$firstInningsWickets",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                item {
                    Text(
                        text = "Batting",
                        style = MaterialTheme.typography.bodyMedium,
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
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.tertiary,
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
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                item {
                    Text(
                        text = "Batting",
                        style = MaterialTheme.typography.bodyMedium,
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
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.tertiary,
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
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.stumpd.warningContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "🃏 Joker Performance: $jokerName",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                    val totalRuns = (jokerFirstInningsBat?.runs ?: 0) + (jokerSecondInningsBat?.runs ?: 0)
                                    val totalWickets = (jokerFirstInningsBowl?.wickets ?: 0) + (jokerSecondInningsBowl?.wickets ?: 0)
                                    Text(
                                        text = "Total: $totalRuns runs, $totalWickets wickets",
                                        style = MaterialTheme.typography.bodySmall,
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
            ) {
                // The label matches what onNewMatch actually does: a fixture returns to the
                // tournament it came from, not to a blank "start another match" screen.
                Text(if (tournamentId != null) "Back to Tournament" else "New Match")
            }
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
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        when (type) {
            "batting" -> {
                val boundaryText = if (shortPitch) "4s:${player.fours}" else "4s:${player.fours} 6s:${player.sixes}"
                Text(
                    text = "${player.runs}${if (!player.isOut && player.ballsFaced > 0) "*" else ""} (${player.ballsFaced}) - $boundaryText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            "bowling" -> {
                Text(
                    text = "${player.wickets}/${player.runsConceded} (${"%.1f".format(player.oversBowled)} ov) Eco: ${"%.1f".format(player.economy)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

