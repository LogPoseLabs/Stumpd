@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.oreki.stumpd

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oreki.stumpd.ui.theme.StumpdTheme

class FullScorecardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val matchId = intent.getStringExtra("match_id") ?: ""

        setContent {
            StumpdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    FullScorecardScreen(matchId)
                }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
fun FullScorecardScreen(matchId: String) {
    val context = LocalContext.current
    val storageManager = remember { MatchStorageManager(context) }
    val match = remember { storageManager.getAllMatches().find { it.id == matchId } }

    if (match == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Match not found", fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val intent = Intent(context, MatchDetailActivity::class.java)
                        intent.putExtra("match_id", matchId)
                        context.startActivity(intent)
                        (context as ComponentActivity).finish()
                    },
                ) {
                    Text("Back to Match Details")
                }
            }
        }
        return
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        item {
            // Enhanced Header with navigation options
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        val intent = Intent(context, MatchDetailActivity::class.java)
                        intent.putExtra("match_id", matchId)
                        context.startActivity(intent)
                        (context as ComponentActivity).finish()
                    },
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back to Match Details",
                        tint = Color(0xFF2E7D32),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Full Scorecard",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32),
                    )
                    Text(
                        text = "${match.team1Name} vs ${match.team2Name}",
                        fontSize = 16.sp,
                        color = Color.Gray,
                    )
                }

                // Quick navigation buttons
                Row {
                    IconButton(
                        onClick = {
                            val intent = Intent(context, MatchHistoryActivity::class.java)
                            context.startActivity(intent)
                            (context as ComponentActivity).finish()
                        },
                    ) {
                        Icon(
                            Icons.Default.List,
                            contentDescription = "Match History",
                            tint = Color(0xFF2E7D32),
                        )
                    }

                    IconButton(
                        onClick = {
                            val intent = Intent(context, MainActivity::class.java)
                            context.startActivity(intent)
                            (context as ComponentActivity).finish()
                        },
                    ) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = "Home",
                            tint = Color(0xFF2E7D32),
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        // FIRST INNINGS
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F8FF)),
            ) {
                Text(
                    text = "🏏 FIRST INNINGS",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32),
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Team 1 Batting (First Innings)
        item {
            Text(
                text = "${match.team1Name} Batting - ${match.firstInningsRuns}/${match.firstInningsWickets}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32),
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            val battingPlayers =
                if (match.firstInningsBatting.isNotEmpty()) {
                    match.firstInningsBatting
                } else {
                    generateSampleBattingData(match.team1Name, match.firstInningsRuns, match.firstInningsWickets, 1)
                }

            // Get all team players for "did not bat" calculation
            val allTeam1Players = (match.firstInningsBatting + match.secondInningsBowling).distinctBy { it.name }
            val team1DidNotBat =
                allTeam1Players.filter { player ->
                    !battingPlayers.any { it.name == player.name }
                }

            EnhancedBattingScorecardCard(
                players = battingPlayers,
                didNotBat = team1DidNotBat,
                isComplete = true, // First innings is always complete
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // Team 2 Bowling (First Innings)
        item {
            Text(
                text = "${match.team2Name} Bowling",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32),
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            val bowlingPlayers =
                if (match.firstInningsBowling.isNotEmpty()) {
                    match.firstInningsBowling
                } else {
                    generateSampleBowlingData(match.team2Name, match.firstInningsWickets, 1)
                }

            // Get all team players for "did not bowl" calculation
            val allTeam2Players = (match.firstInningsBowling + match.secondInningsBatting).distinctBy { it.name }
            val team2DidNotBowl =
                allTeam2Players.filter { player ->
                    !bowlingPlayers.any { it.name == player.name }
                }

            EnhancedBowlingScorecardCard(
                players = bowlingPlayers,
                didNotBowl = team2DidNotBowl,
            )
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }

        // SECOND INNINGS
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
            ) {
                Text(
                    text = "🏏 SECOND INNINGS",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32),
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Team 2 Batting (Second Innings)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${match.team2Name} Batting - ${match.secondInningsRuns}/${match.secondInningsWickets}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32),
                )

                // Target information
                val target = match.firstInningsRuns + 1
                val required = target - match.secondInningsRuns
                Text(
                    text = if (required > 0) "Target: $target" else "Target achieved!",
                    fontSize = 12.sp,
                    color = if (required > 0) Color(0xFFFF5722) else Color(0xFF4CAF50),
                    fontStyle = FontStyle.Italic,
                )
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            val battingPlayers =
                if (match.secondInningsBatting.isNotEmpty()) {
                    match.secondInningsBatting
                } else {
                    generateSampleBattingData(match.team2Name, match.secondInningsRuns, match.secondInningsWickets, 2)
                }

            // Get all team players for "did not bat" calculation
            val allTeam2Players = (match.secondInningsBatting + match.firstInningsBowling).distinctBy { it.name }
            val team2DidNotBat =
                allTeam2Players.filter { player ->
                    !battingPlayers.any { it.name == player.name }
                }

            // Check if second innings is complete
            val isSecondInningsComplete = match.secondInningsWickets >= 10 || match.secondInningsRuns > match.firstInningsRuns

            EnhancedBattingScorecardCard(
                players = battingPlayers,
                didNotBat = team2DidNotBat,
                isComplete = isSecondInningsComplete,
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // Team 1 Bowling (Second Innings)
        item {
            Text(
                text = "${match.team1Name} Bowling",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32),
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            val bowlingPlayers =
                if (match.secondInningsBowling.isNotEmpty()) {
                    match.secondInningsBowling
                } else {
                    generateSampleBowlingData(match.team1Name, match.secondInningsWickets, 2)
                }

            // Get all team players for "did not bowl" calculation
            val allTeam1Players = (match.secondInningsBowling + match.firstInningsBatting).distinctBy { it.name }
            val team1DidNotBowl =
                allTeam1Players.filter { player ->
                    !bowlingPlayers.any { it.name == player.name }
                }

            EnhancedBowlingScorecardCard(
                players = bowlingPlayers,
                didNotBowl = team1DidNotBowl,
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        // Match Summary
        item {
            MatchSummaryCard(match)
        }
    }
}

@Composable
fun EnhancedBattingScorecardCard(
    players: List<PlayerMatchStats>,
    didNotBat: List<PlayerMatchStats>,
    isComplete: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Batsman", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                Text("R", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("B", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("4s", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("6s", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("SR", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Player rows
            players.forEach { player ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (player.isJoker) "🃏 ${player.name}" else player.name,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(2f),
                        color = if (player.isJoker) Color(0xFFFF9800) else Color.Black,
                    )
                    Text("${player.runs}${if (player.isOut) "" else "*"}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("${player.ballsFaced}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("${player.fours}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("${player.sixes}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("${"%.1f".format(player.strikeRate)}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                }
            }

            // Show players who didn't bat
            if (didNotBat.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                val statusText =
                    if (isComplete) {
                        "Did not bat: ${didNotBat.joinToString(", ") { it.name }}"
                    } else {
                        "Yet to bat: ${didNotBat.joinToString(", ") { it.name }}"
                    }

                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun EnhancedBowlingScorecardCard(
    players: List<PlayerMatchStats>,
    didNotBowl: List<PlayerMatchStats>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Bowler", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                Text("O", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("R", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("W", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Eco", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Player rows
            players.forEach { player ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (player.isJoker) "🃏 ${player.name}" else player.name,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(2f),
                        color = if (player.isJoker) Color(0xFFFF9800) else Color.Black,
                    )
                    Text("${"%.1f".format(player.oversBowled)}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("${player.runsConceded}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("${player.wickets}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("${"%.1f".format(player.economy)}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                }
            }

            // Show players who didn't bowl
            if (didNotBowl.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Did not bowl: ${didNotBowl.joinToString(", ") { it.name }}",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun MatchSummaryCard(match: MatchHistory) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E8)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "🏆 Match Result",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${match.winnerTeam} won by ${match.winningMargin}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Show top performers if available
            match.topBatsman?.let { topBat ->
                Text(
                    text = "🏏 Top Batsman: ${topBat.name} - ${topBat.runs} runs",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            match.topBowler?.let { topBowl ->
                Text(
                    text = "⚾ Top Bowler: ${topBowl.name} - ${topBowl.wickets}/${topBowl.runs}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            match.jokerPlayerName?.let { joker ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "🃏 Joker Player: $joker",
                    fontSize = 12.sp,
                    color = Color(0xFFFF9800),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

// Keep all your existing helper functions unchanged
fun generateSampleBattingData(
    teamName: String,
    totalRuns: Int,
    totalWickets: Int,
    innings: Int,
): List<PlayerMatchStats> {
    val team1Players = listOf("Virat Kohli", "Rohit Sharma", "MS Dhoni", "KL Rahul", "Hardik Pandya")
    val team2Players = listOf("Kane Williamson", "David Warner", "Steve Smith", "Jos Buttler", "Ben Stokes")

    val playerNames =
        when (teamName) {
            "Team A" -> if (innings == 1) team1Players else team2Players
            "Team B" -> if (innings == 1) team2Players else team1Players
            else -> listOf("Player 1", "Player 2", "Player 3", "Player 4", "Player 5")
        }

    val players = mutableListOf<PlayerMatchStats>()
    var remainingRuns = totalRuns
    var playersOut = totalWickets

    val distribution =
        if (innings == 1) {
            listOf(0.35, 0.25, 0.20, 0.15, 0.05)
        } else {
            listOf(0.40, 0.30, 0.15, 0.10, 0.05)
        }

    playerNames.take(5).forEachIndexed { index, name ->
        if (index >= distribution.size) return@forEachIndexed

        val isOut = index < playersOut
        val runs = (totalRuns * distribution[index]).toInt()
        remainingRuns -= runs

        val ballsFaced =
            if (runs > 0) {
                val baseRate = if (innings == 1) 0.8 else 0.9
                (runs * baseRate + (5..15).random()).toInt()
            } else {
                0
            }

        val fours = runs / (if (innings == 1) 8 else 6)
        val sixes = runs / (if (innings == 1) 12 else 10)

        if (runs > 0 || ballsFaced > 0) {
            players.add(
                PlayerMatchStats(
                    name = name,
                    runs = runs,
                    ballsFaced = ballsFaced,
                    fours = fours,
                    sixes = sixes,
                    isOut = isOut,
                    team = teamName,
                ),
            )
        }
    }

    return players
}

fun generateSampleBowlingData(
    teamName: String,
    totalWickets: Int,
    innings: Int,
): List<PlayerMatchStats> {
    val team1Bowlers = listOf("Jasprit Bumrah", "Mohammed Shami", "Ravindra Jadeja", "Yuzvendra Chahal")
    val team2Bowlers = listOf("Pat Cummins", "Mitchell Starc", "Adam Zampa", "Josh Hazlewood")

    val bowlerNames =
        when (teamName) {
            "Team A" -> if (innings == 2) team1Bowlers else team2Bowlers
            "Team B" -> if (innings == 2) team2Bowlers else team1Bowlers
            else -> listOf("Bowler 1", "Bowler 2", "Bowler 3", "Bowler 4")
        }

    val bowlers = mutableListOf<PlayerMatchStats>()
    var remainingWickets = totalWickets

    bowlerNames.take(4).forEachIndexed { index, name ->
        val wickets =
            when {
                index == 0 -> (totalWickets / 2).coerceAtMost(3)
                index == 1 -> (remainingWickets / 2).coerceAtMost(2)
                else -> if (remainingWickets > 0) 1 else 0
            }

        remainingWickets -= wickets
        val overs = (2..5).random().toDouble()

        val baseEconomy = if (innings == 1) 7.0 else 8.5
        val runsConceded = (overs * baseEconomy + (-10..10).random()).toInt().coerceAtLeast(0)

        if (overs > 0 || wickets > 0 || runsConceded > 0) {
            bowlers.add(
                PlayerMatchStats(
                    name = name,
                    wickets = wickets,
                    runsConceded = runsConceded,
                    oversBowled = overs,
                    team = teamName,
                ),
            )
        }
    }

    return bowlers.filter { it.oversBowled > 0 || it.wickets > 0 }
}
