package com.oreki.stumpd

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oreki.stumpd.ui.theme.SectionCard
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.ui.theme.StumpdTopBar
import com.oreki.stumpd.ui.theme.SuccessContainer
import java.text.SimpleDateFormat
import java.util.*
import com.oreki.stumpd.ui.theme.Label
import com.oreki.stumpd.ui.theme.sectionContainer
import com.oreki.stumpd.ui.theme.successContainerAdaptive

class MatchDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val matchId = intent.getStringExtra("match_id") ?: ""

        setContent {
            StumpdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MatchDetailScreen(matchId)
                }
            }
        }
    }
}


@Composable
fun MatchDetailScreen(matchId: String) {
    val context = LocalContext.current
    val storageManager = remember { MatchStorageManager(context) }
    val match = remember { storageManager.getAllMatches().find { it.id == matchId } }

    if (match == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Match not found", fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val intent = Intent(context, MatchHistoryActivity::class.java)
                        context.startActivity(intent)
                        (context as ComponentActivity).finish()
                    }
                ) {
                    Text("Back to History")
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            StumpdTopBar(
                title = "Match Scorecard",
                subtitle = "${match.team1Name} vs ${match.team2Name}",
                onBack = {
                    val intent = Intent(context, MatchHistoryActivity::class.java)
                    context.startActivity(intent)
                    (context as ComponentActivity).finish()
                },
                onHome = {
                    val intent = Intent(context, MainActivity::class.java)
                    context.startActivity(intent)
                    (context as ComponentActivity).finish()
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                SectionCard(title = "Match Info", sectionContainerColor=sectionContainer()) {
                    Text("${match.team1Name} vs ${match.team2Name}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Label(
                        SimpleDateFormat("MMMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
                            .format(Date(match.matchDate))
                    )
                    match.jokerPlayerName?.let {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Label("Joker"); Text(it, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }

            item {
                val isTie = match.winnerTeam.equals("TIE", ignoreCase = true)

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = bannerContainerFor(isTie)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isTie) "🏆 TIE" else "🏆 ${match.winnerTeam}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Label(
                            if (isTie) "scores level" else "won by ${match.winningMargin}"
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            item {
                // Innings Summary
                Text(
                    text = "Innings Summary",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            item {
                // First Innings Enhanced
                EnhancedInningsSummaryCard(
                    title = "${match.team1Name} - 1st Innings",
                    runs = match.firstInningsRuns,
                    wickets = match.firstInningsWickets,
                    runRate = if (match.firstInningsRuns > 0) match.firstInningsRuns / (match.matchSettings?.totalOvers?.toDouble()!!) else 5.0,
                    match = match,
                    isFirstInnings = true
                )
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            item {
                // Second Innings Enhanced
                EnhancedInningsSummaryCard(
                    title = "${match.team2Name} - 2nd Innings",
                    runs = match.secondInningsRuns,
                    wickets = match.secondInningsWickets,
                    runRate = if (match.secondInningsRuns > 0) match.secondInningsRuns / (match.matchSettings?.totalOvers?.toDouble()!!) else 5.0,
                    match = match,
                    isFirstInnings = false
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // Add Full Scorecard Button
            item {
                Button(
                    onClick = {
                        val intent =
                            android.content.Intent(context, FullScorecardActivity::class.java)
                        intent.putExtra("match_id", matchId)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "📊 View Full Scorecard",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                // Match Stats
                Text(
                    text = "Match Statistics",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            item {
                MatchStatsCard(match)
            }
        }
    }
}

@Composable
fun EnhancedInningsSummaryCard(
    title: String,
    runs: Int,
    wickets: Int,
    runRate: Double,
    match: MatchHistory,
    isFirstInnings: Boolean
) {
    // Get batting and bowling players for this innings
    val battingPlayers = if (isFirstInnings) match.firstInningsBatting else match.secondInningsBatting
    val bowlingPlayers = if (isFirstInnings) match.firstInningsBowling else match.secondInningsBowling

    // Get all team players to find who didn't play
    val battingTeamAllPlayers = if (isFirstInnings) {
        (match.firstInningsBatting + match.secondInningsBowling).distinctBy { it.name }
    } else {
        (match.secondInningsBatting + match.firstInningsBowling).distinctBy { it.name }
    }

    val bowlingTeamAllPlayers = if (isFirstInnings) {
        (match.firstInningsBowling + match.secondInningsBatting).distinctBy { it.name }
    } else {
        (match.secondInningsBowling + match.firstInningsBatting).distinctBy { it.name }
    }

    // Find top performers for this innings
    val topBatsman = battingPlayers.maxByOrNull { it.runs }
    val topBowler = bowlingPlayers.maxByOrNull { it.wickets }

    // Find players who didn't play
    val didNotBat = battingTeamAllPlayers.filter { player ->
        !battingPlayers.any { it.name == player.name }
    }

    val didNotBowl = bowlingTeamAllPlayers.filter { player ->
        !bowlingPlayers.any { it.name == player.name }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = sectionContainer()),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Score and Run Rate Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$runs/$wickets",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.25.sp
                )

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Run Rate",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "%.2f".format(runRate),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Target info for second innings
            if (!isFirstInnings) {
                val target = match.firstInningsRuns + 1
                val required = target - runs
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (required > 0) "Target: $target (needed $required more)"
                    else "Target achieved!",
                    fontSize = 12.sp,
                    color = if (required > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontStyle = FontStyle.Italic
                )
            }

            // Top Performers Section
            if (topBatsman != null || topBowler != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "🌟 Top Performers",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Top Batsman
                topBatsman?.let { batsman ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🏏",
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (batsman.isJoker) "🃏 ${batsman.name}" else batsman.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (batsman.isJoker) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "${batsman.runs}${if (batsman.isOut) "" else "*"} (${batsman.ballsFaced}) - SR: ${"%.1f".format(batsman.strikeRate)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                // Top Bowler
                topBowler?.let { bowler ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "⚾",
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (bowler.isJoker) "🃏 ${bowler.name}" else bowler.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (bowler.isJoker) MaterialTheme.colorScheme.secondary else Color.Black
                            )
                        }

                        Text(
                            text = "${bowler.wickets}/${bowler.runsConceded} (${"%.1f".format(bowler.oversBowled)}) - Eco: ${"%.1f".format(bowler.economy)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Players who didn't play section
            if (didNotBat.isNotEmpty() || didNotBowl.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))

                if (didNotBat.isNotEmpty()) {
                    val statusText = if (!isFirstInnings && wickets < 10 && runs <= match.firstInningsRuns) {
                        "Yet to bat: ${didNotBat.joinToString(", ") { it.name }}"
                    } else {
                        "Did not bat: ${didNotBat.joinToString(", ") { it.name }}"
                    }
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic
                    )
                }

                if (didNotBowl.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Did not bowl: ${didNotBowl.joinToString(", ") { it.name }}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }
    }
}

@Composable
fun MatchStatsCard(match: MatchHistory) {
    val totalRuns = match.firstInningsRuns + match.secondInningsRuns
    val totalWickets = match.firstInningsWickets + match.secondInningsWickets
    val averageScore = totalRuns / 2

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Match Overview",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            StatRow("Total Runs", totalRuns.toString())
            StatRow("Total Wickets", totalWickets.toString())
            StatRow("Average Score", averageScore.toString())
            StatRow("Highest Score", maxOf(match.firstInningsRuns, match.secondInningsRuns).toString())
            StatRow("Lowest Score", minOf(match.firstInningsRuns, match.secondInningsRuns).toString())

            // Top performers across both innings
            val allBattingPerformances = match.firstInningsBatting + match.secondInningsBatting
            val allBowlingPerformances = match.firstInningsBowling + match.secondInningsBowling

            val topScorer = allBattingPerformances.maxByOrNull { it.runs }
            val topWicketTaker = allBowlingPerformances.maxByOrNull { it.wickets }

            if (topScorer != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Top Performers",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                StatRow("Top Scorer", "${topScorer.name} (${topScorer.runs} runs)")
            }

            if (topWicketTaker != null && topWicketTaker.wickets > 0) {
                StatRow("Best Bowler", "${topWicketTaker.name} (${topWicketTaker.wickets} wickets)")
            }
        }
    }
}

@Composable
private fun bannerContainerFor(isTie: Boolean): Color {
    return if (isTie) {
        // neutral/grey container for TIE
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        // success/greenish container (your adaptive helper if you have one)
        successContainerAdaptive()
    }
}