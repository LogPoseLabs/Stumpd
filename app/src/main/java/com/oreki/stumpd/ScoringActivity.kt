package com.oreki.stumpd

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.oreki.stumpd.ui.theme.StumpdTheme

class ScoringActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Extract data from intent
        val team1Name = intent.getStringExtra("team1_name") ?: "Team A"
        val team2Name = intent.getStringExtra("team2_name") ?: "Team B"
        val jokerName = intent.getStringExtra("joker_name") ?: ""
        val team1PlayerNames = intent.getStringArrayExtra("team1_players") ?: arrayOf("Player 1", "Player 2", "Player 3")
        val team2PlayerNames = intent.getStringArrayExtra("team2_players") ?: arrayOf("Player 4", "Player 5", "Player 6")
        val matchSettingsJson = intent.getStringExtra("match_settings") ?: ""

        setContent {
            StumpdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ScoringScreen(
                        team1Name = team1Name,
                        team2Name = team2Name,
                        jokerName = jokerName,
                        team1PlayerNames = team1PlayerNames,
                        team2PlayerNames = team2PlayerNames,
                        matchSettingsJson = matchSettingsJson,
                    )
                }
            }
        }
    }
}

@Composable
fun ScoringScreen(
    team1Name: String = "Team A",
    team2Name: String = "Team B",
    jokerName: String = "",
    team1PlayerNames: Array<String> = arrayOf("Player 1", "Player 2", "Player 3"),
    team2PlayerNames: Array<String> = arrayOf("Player 4", "Player 5", "Player 6"),
    matchSettingsJson: String = "",
) {
    val context = LocalContext.current
    val gson = Gson()

    // Parse match settings or use defaults
    val matchSettings = remember {
        try {
            if (matchSettingsJson.isNotEmpty()) {
                gson.fromJson(matchSettingsJson, MatchSettings::class.java)
            } else {
                MatchSettingsManager(context).getDefaultMatchSettings()
            }
        } catch (e: Exception) {
            MatchSettings()
        }
    }

    // Create teams with dynamic player data as MutableList
    var team1Players by remember {
        mutableStateOf(team1PlayerNames.map { Player(it) }.toMutableList())
    }

    var team2Players by remember {
        mutableStateOf(team2PlayerNames.map { Player(it) }.toMutableList())
    }

    val jokerPlayer = remember {
        if (jokerName.isNotEmpty()) Player(jokerName, isJoker = true) else null
    }

    // Current innings and teams
    var currentInnings by remember { mutableStateOf(1) }
    var battingTeamPlayers by remember { mutableStateOf(team1Players) }
    var bowlingTeamPlayers by remember { mutableStateOf(team2Players) }
    var battingTeamName by remember { mutableStateOf(team1Name) }
    var bowlingTeamName by remember { mutableStateOf(team2Name) }

    // First innings final stats
    var firstInningsRuns by remember { mutableStateOf(0) }
    var firstInningsWickets by remember { mutableStateOf(0) }
    var firstInningsOvers by remember { mutableStateOf(0) }
    var firstInningsBalls by remember { mutableStateOf(0) }

    var firstInningsBattingPlayersList by remember { mutableStateOf<List<Player>>(emptyList()) }
    var firstInningsBowlingPlayersList by remember { mutableStateOf<List<Player>>(emptyList()) }

    var secondInningsBattingPlayers by remember { mutableStateOf<List<Player>>(emptyList()) }
    var secondInningsBowlingPlayers by remember { mutableStateOf<List<Player>>(emptyList()) }

    // Current match state
    var totalWickets by remember { mutableStateOf(0) }
    var currentOver by remember { mutableStateOf(0) }
    var ballsInOver by remember { mutableStateOf(0) }
    var totalExtras by remember { mutableStateOf(0) }

    // UPDATED: Calculate totals INCLUDING extras
    val calculatedTotalRuns = remember(battingTeamPlayers, totalExtras) {
        battingTeamPlayers.sumOf { it.runs } + totalExtras
    }

    val calculatedTotalRunsConceded = remember(bowlingTeamPlayers) {
        bowlingTeamPlayers.sumOf { it.runsConceded }
    }

    // Bowler rotation tracking
    var previousBowlerIndex by remember { mutableStateOf<Int?>(null) }
    var currentBowlerSpell by remember { mutableStateOf(0) }

    // Current players (indices into the team lists)
    var strikerIndex by remember { mutableStateOf<Int?>(null) }
    var nonStrikerIndex by remember { mutableStateOf<Int?>(null) }
    var bowlerIndex by remember { mutableStateOf<Int?>(null) }

    // Get current players
    val striker = strikerIndex?.let { battingTeamPlayers.getOrNull(it) }
    val nonStriker = nonStrikerIndex?.let { battingTeamPlayers.getOrNull(it) }
    val bowler = bowlerIndex?.let { bowlingTeamPlayers.getOrNull(it) }

    // Dialog states
    var showBatsmanDialog by remember { mutableStateOf(false) }
    var showBowlerDialog by remember { mutableStateOf(false) }
    var showWicketDialog by remember { mutableStateOf(false) }
    var showInningsBreakDialog by remember { mutableStateOf(false) }
    var showMatchCompleteDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showLiveScorecardDialog by remember { mutableStateOf(false) }
    var showExtrasDialog by remember { mutableStateOf(false) }
    var selectingBatsman by remember { mutableStateOf(1) }

    // Unified helper functions that ensure synchronization
    fun updateStrikerAndTotals(updateFunction: (Player) -> Player) {
        strikerIndex?.let { index ->
            val newPlayersList = battingTeamPlayers.toMutableList()
            newPlayersList[index] = updateFunction(newPlayersList[index])
            battingTeamPlayers = newPlayersList
        }
    }

    fun updateBowlerStats(updateFunction: (Player) -> Player) {
        bowlerIndex?.let { index ->
            val newPlayersList = bowlingTeamPlayers.toMutableList()
            newPlayersList[index] = updateFunction(newPlayersList[index])
            bowlingTeamPlayers = newPlayersList
        }
    }

    fun swapStrike() {
        // Only swap if single side batting is disabled and non-striker exists
        if (!matchSettings.allowSingleSideBatting && nonStriker != null) {
            val temp = strikerIndex
            strikerIndex = nonStrikerIndex
            nonStrikerIndex = temp
        }
    }

    // UPDATED: Better innings completion check
    val availableBatsmen = battingTeamPlayers.count { !it.isOut }
    val isInningsComplete =
        currentOver >= matchSettings.totalOvers ||
                (currentInnings == 2 && calculatedTotalRuns > firstInningsRuns) ||
                // All out logic - consider single side batting
                if (matchSettings.allowSingleSideBatting) {
                    availableBatsmen == 0 // Only complete when NO batsmen left
                } else {
                    totalWickets >= battingTeamPlayers.size - 1 // Traditional: need 2 batsmen
                }

    // Enhanced Check for match completion with second innings stats
    LaunchedEffect(isInningsComplete) {
        if (isInningsComplete) {
            if (currentInnings == 1) {
                // Save first innings stats using calculated values
                firstInningsRuns = calculatedTotalRuns
                firstInningsWickets = totalWickets
                firstInningsOvers = currentOver
                firstInningsBalls = ballsInOver

                // Save first innings player lists for later use
                firstInningsBattingPlayersList = battingTeamPlayers.filter { it.ballsFaced > 0 || it.runs > 0 }
                firstInningsBowlingPlayersList = bowlingTeamPlayers.filter { it.ballsBowled > 0 || it.wickets > 0 || it.runsConceded > 0 }

                showInningsBreakDialog = true
            } else {
                // Second innings complete - save second innings stats
                val secondInningsRuns = calculatedTotalRuns
                val secondInningsWickets = totalWickets
                val secondInningsOvers = currentOver
                val secondInningsBalls = ballsInOver

                // Save second innings player lists
                val secondInningsBattingPlayersList = battingTeamPlayers.filter { it.ballsFaced > 0 || it.runs > 0 }
                val secondInningsBowlingPlayersList = bowlingTeamPlayers.filter { it.ballsBowled > 0 || it.wickets > 0 || it.runsConceded > 0 }

                // Store second innings data in state variables for dialog access
                secondInningsBattingPlayers = secondInningsBattingPlayersList
                secondInningsBowlingPlayers = secondInningsBowlingPlayersList

                showMatchCompleteDialog = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // Enhanced Score Header with match settings info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32)),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "$battingTeamName - Innings $currentInnings (${matchSettings.totalOvers} overs)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )

                Text(
                    text = "$calculatedTotalRuns/$totalWickets",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Overs: $currentOver.$ballsInOver/${matchSettings.totalOvers}",
                        fontSize = 14.sp,
                        color = Color.White,
                    )

                    val runRate = if (currentOver == 0 && ballsInOver == 0) {
                        0.0
                    } else {
                        calculatedTotalRuns.toDouble() / ((currentOver * 6 + ballsInOver) / 6.0)
                    }
                    Text(
                        text = "RR: ${"%.2f".format(runRate)}",
                        fontSize = 14.sp,
                        color = Color.White,
                    )

                    if (totalExtras > 0) {
                        Text(
                            text = "Extras: $totalExtras",
                            fontSize = 14.sp,
                            color = Color.Yellow,
                        )
                    }
                }

                // UPDATED: Add breakdown below main score
                if (totalExtras > 0) {
                    val playerRuns = battingTeamPlayers.sumOf { it.runs }
                    Text(
                        text = "($playerRuns runs + $totalExtras extras)",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        fontStyle = FontStyle.Italic
                    )
                }

                // Show target in second innings
                if (currentInnings == 2) {
                    val target = firstInningsRuns + 1
                    val required = target - calculatedTotalRuns
                    val ballsLeft = (matchSettings.totalOvers - currentOver) * 6 - ballsInOver
                    val requiredRunRate = if (ballsLeft > 0) (required.toDouble() / ballsLeft) * 6 else 0.0

                    Text(
                        text = if (required > 0) {
                            "Need $required runs in $ballsLeft balls (RRR: ${"%.2f".format(requiredRunRate)})"
                        } else {
                            "🎉 Target achieved!"
                        },
                        fontSize = 14.sp,
                        color = if (required > 0) Color.Yellow else Color.Green,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Enhanced Players Card with Live Scorecard button
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Current Players",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    Row {
                        // Live Scorecard Button
                        IconButton(
                            onClick = { showLiveScorecardDialog = true },
                        ) {
                            Icon(
                                Icons.Default.List,
                                contentDescription = "Live Scorecard",
                                tint = Color(0xFF2196F3),
                            )
                        }

                        IconButton(
                            onClick = {
                                if (calculatedTotalRuns > 0 || currentOver > 0) {
                                    showExitDialog = true
                                } else {
                                    val intent = android.content.Intent(context, MainActivity::class.java)
                                    context.startActivity(intent)
                                    (context as androidx.activity.ComponentActivity).finish()
                                }
                            },
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back to Home",
                                tint = Color(0xFF2E7D32),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // UPDATED: Enhanced batsmen display with single side batting awareness
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (matchSettings.allowSingleSideBatting && nonStriker == null)
                        Arrangement.Center else Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectingBatsman = 1
                                showBatsmanDialog = true
                            },
                    ) {
                        Text(
                            text = "🏏 ${striker?.name ?: "Select Batsman 1"}",
                            fontWeight = FontWeight.Bold,
                            color = if (striker == null) Color.Red else Color(0xFF2E7D32),
                        )
                        striker?.let { currentStriker ->
                            Text(
                                text = "${currentStriker.runs}${if (!currentStriker.isOut && currentStriker.ballsFaced > 0) "*" else ""} (${currentStriker.ballsFaced}) - 4s: ${currentStriker.fours}, 6s: ${currentStriker.sixes}",
                                fontSize = 12.sp,
                                color = Color.Gray,
                            )
                            Text(
                                text = "SR: ${"%.1f".format(currentStriker.strikeRate)}",
                                fontSize = 10.sp,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    // Only show non-striker if not single side batting or if non-striker exists
                    if (!matchSettings.allowSingleSideBatting || nonStriker != null) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (!matchSettings.allowSingleSideBatting) {
                                        selectingBatsman = 2
                                        showBatsmanDialog = true
                                    }
                                },
                            horizontalAlignment = Alignment.End,
                        ) {
                            Text(
                                text = "${nonStriker?.name ?: if (matchSettings.allowSingleSideBatting) "Single Side" else "Select Batsman 2"}",
                                fontWeight = FontWeight.Normal,
                                color = if (nonStriker == null && !matchSettings.allowSingleSideBatting) Color.Red else Color.Black,
                            )
                            nonStriker?.let { currentNonStriker ->
                                Text(
                                    text = "${currentNonStriker.runs}${if (!currentNonStriker.isOut && currentNonStriker.ballsFaced > 0) "*" else ""} (${currentNonStriker.ballsFaced}) - 4s: ${currentNonStriker.fours}, 6s: ${currentNonStriker.sixes}",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                )
                                Text(
                                    text = "SR: ${"%.1f".format(currentNonStriker.strikeRate)}",
                                    fontSize = 10.sp,
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }

                // Swap Strike Button (only if not single side batting and both players exist)
                if (striker != null && nonStriker != null && !matchSettings.allowSingleSideBatting) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            swapStrike()
                            Toast.makeText(context, "Strike swapped! ${striker?.name} now on strike", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Swap Strike",
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Swap Strike", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bowler with real-time stats
                Column(
                    modifier = Modifier.clickable { showBowlerDialog = true },
                ) {
                    Text(
                        text = "⚾ Bowler: ${bowler?.name ?: "Select Bowler"}",
                        fontWeight = FontWeight.Medium,
                        color = if (bowler == null) Color.Red else Color.Black,
                    )
                    bowler?.let { currentBowler ->
                        Text(
                            text = "${"%.1f".format(currentBowler.oversBowled)} overs, ${currentBowler.runsConceded} runs, ${currentBowler.wickets} wickets",
                            fontSize = 12.sp,
                            color = Color.Gray,
                        )
                        Text(
                            text = "Economy: ${"%.1f".format(currentBowler.economy)} | Spell: $currentBowlerSpell over${if (currentBowlerSpell != 1) "s" else ""}",
                            fontSize = 10.sp,
                            color = Color(0xFFFF5722),
                            fontWeight = FontWeight.Medium,
                        )

                        // Show next over warning
                        if (ballsInOver >= 4) {
                            Text(
                                text = "⚠️ Bowler change required after this over",
                                fontSize = 10.sp,
                                color = Color(0xFFFF5722),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // Show joker info if available
                jokerPlayer?.let { joker ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "🃏 Joker Available: ${joker.name}",
                        fontSize = 12.sp,
                        color = Color(0xFFFF9800),
                        fontWeight = FontWeight.Medium,
                    )
                }

                // Show single side batting status
                if (matchSettings.allowSingleSideBatting && striker != null && nonStriker == null && availableBatsmen == 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                    ) {
                        Text(
                            text = "⚡ Single Side Batting: ${striker?.name} continues alone",
                            fontSize = 12.sp,
                            color = Color(0xFFFF9800),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // UPDATED: Scoring Buttons with proper single side batting check
        if (striker != null && (nonStriker != null || (matchSettings.allowSingleSideBatting && availableBatsmen >= 1)) && bowler != null && !isInningsComplete) {
            Text(
                text = "Runs",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (i in 0..6) {
                    Button(
                        onClick = {
                            // Update striker stats using unified method
                            updateStrikerAndTotals { player ->
                                player.copy(
                                    runs = player.runs + i,
                                    ballsFaced = player.ballsFaced + 1,
                                    fours = if (i == 4) player.fours + 1 else player.fours,
                                    sixes = if (i == 6) player.sixes + 1 else player.sixes,
                                )
                            }

                            // Update bowler stats using unified method
                            updateBowlerStats { player ->
                                player.copy(
                                    runsConceded = player.runsConceded + i,
                                    ballsBowled = player.ballsBowled + 1,
                                )
                            }

                            // Change strike on odd runs (only if not single side batting and non-striker exists)
                            if (i % 2 == 1 && !matchSettings.allowSingleSideBatting && nonStriker != null) {
                                swapStrike()
                            }

                            // Update over count
                            ballsInOver += 1
                            if (ballsInOver == 6) {
                                currentOver += 1
                                ballsInOver = 0

                                // Enforce bowler change rule
                                previousBowlerIndex = bowlerIndex
                                bowlerIndex = null
                                currentBowlerSpell = 0

                                // Swap strike at end of over (if not single side batting and non-striker exists)
                                if (!matchSettings.allowSingleSideBatting && nonStriker != null) {
                                    swapStrike()
                                }
                                showBowlerDialog = true

                                Toast.makeText(context, "Over complete! Select new bowler", Toast.LENGTH_LONG).show()
                            }

                            Toast.makeText(
                                context,
                                "$i run(s) scored by ${striker?.name}! Total: $calculatedTotalRuns",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (i) {
                                4 -> Color(0xFF4CAF50)
                                6 -> Color(0xFF2196F3)
                                else -> Color(0xFF9E9E9E)
                            },
                        ),
                    ) {
                        Text(i.toString(), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Enhanced Extras and Wicket buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { showExtrasDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                ) {
                    Text("Extras", fontSize = 12.sp)
                }

                Button(
                    onClick = { showWicketDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                ) {
                    Text("Wicket", fontSize = 12.sp)
                }
            }
        } else if (isInningsComplete) {
            // Innings complete message
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5)),
            ) {
                Text(
                    text = if (currentInnings == 1) "First Innings Complete! Total: $calculatedTotalRuns runs" else "Match Complete!",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7B1FA2),
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            // Setup required message
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "⚠️ Please select players to start scoring",
                        fontSize = 16.sp,
                        color = Color(0xFFFF9800),
                        textAlign = TextAlign.Center,
                    )

                    if (matchSettings.allowSingleSideBatting) {
                        Text(
                            text = "Single side batting enabled - only one batsman required",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }
            }
        }
    }

    // Enhanced Live Scorecard Dialog
    if (showLiveScorecardDialog) {
        LiveScorecardDialog(
            currentInnings = currentInnings,
            battingTeamName = battingTeamName,
            bowlingTeamName = bowlingTeamName,
            battingTeamPlayers = battingTeamPlayers,
            bowlingTeamPlayers = bowlingTeamPlayers,
            firstInningsBattingPlayers = firstInningsBattingPlayersList,
            firstInningsBowlingPlayers = firstInningsBowlingPlayersList,
            firstInningsRuns = firstInningsRuns,
            firstInningsWickets = firstInningsWickets,
            currentRuns = calculatedTotalRuns,
            currentWickets = totalWickets,
            currentOvers = currentOver,
            currentBalls = ballsInOver,
            totalOvers = matchSettings.totalOvers,
            jokerPlayerName = jokerName,
            onDismiss = { showLiveScorecardDialog = false },
        )
    }

    // Enhanced Extras Dialog
    if (showExtrasDialog) {
        ExtrasDialog(
            matchSettings = matchSettings,
            onExtraSelected = { extraType, totalRuns ->
                when (extraType) {
                    ExtraType.OFF_SIDE_WIDE, ExtraType.LEG_SIDE_WIDE -> {
                        // Wide balls: runs to team, no ball count for bowler, batsman doesn't face
                        updateBowlerStats { player ->
                            player.copy(runsConceded = player.runsConceded + totalRuns)
                        }
                        totalExtras += totalRuns

                        // Change strike only on odd additional runs (not base wide)
                        val baseWideRuns = if (extraType == ExtraType.OFF_SIDE_WIDE) matchSettings.offSideWideRuns else matchSettings.legSideWideRuns
                        val additionalRuns = totalRuns - baseWideRuns
                        if (additionalRuns % 2 == 1 && !matchSettings.allowSingleSideBatting && nonStriker != null) {
                            swapStrike()
                        }

                        Toast.makeText(context, "${extraType.displayName}! +$totalRuns runs", Toast.LENGTH_SHORT).show()
                    }

                    ExtraType.NO_BALL -> {
                        // No ball: runs to team, ball counts for batsman, NO ball count for bowler
                        updateStrikerAndTotals { player ->
                            player.copy(ballsFaced = player.ballsFaced + 1)
                        }
                        updateBowlerStats { player ->
                            player.copy(
                                runsConceded = player.runsConceded + totalRuns
                                // Note: ballsBowled is NOT incremented for no balls
                            )
                        }
                        totalExtras += totalRuns

                        // Change strike only on odd additional runs (not base no-ball)
                        val additionalRuns = totalRuns - matchSettings.noballRuns
                        if (additionalRuns % 2 == 1 && !matchSettings.allowSingleSideBatting && nonStriker != null) {
                            swapStrike()
                        }

                        // No ball doesn't advance the over count
                        Toast.makeText(context, "No ball! +$totalRuns runs", Toast.LENGTH_SHORT).show()
                    }

                    ExtraType.BYE, ExtraType.LEG_BYE -> {
                        // Byes/Leg byes: runs to team, ball counts for both batsman and bowler
                        updateStrikerAndTotals { player ->
                            player.copy(ballsFaced = player.ballsFaced + 1)
                        }
                        updateBowlerStats { player ->
                            player.copy(ballsBowled = player.ballsBowled + 1)
                        }
                        totalExtras += totalRuns
                        ballsInOver += 1

                        // Check for over completion
                        if (ballsInOver == 6) {
                            currentOver += 1
                            ballsInOver = 0
                            previousBowlerIndex = bowlerIndex
                            bowlerIndex = null
                            currentBowlerSpell = 0

                            // Swap strike at end of over (if not single side batting and non-striker exists)
                            if (!matchSettings.allowSingleSideBatting && nonStriker != null) {
                                swapStrike()
                            }
                            showBowlerDialog = true
                            Toast.makeText(context, "Over complete! Select new bowler", Toast.LENGTH_LONG).show()
                        } else {
                            // Change strike only on odd total runs
                            val baseByeRuns = if (extraType == ExtraType.BYE) matchSettings.byeRuns else matchSettings.legByeRuns
                            val additionalRuns = totalRuns - baseByeRuns
                            if (additionalRuns % 2 == 1 && !matchSettings.allowSingleSideBatting && nonStriker != null) {
                                swapStrike()
                            }
                        }

                        Toast.makeText(context, "${extraType.displayName}! +$totalRuns runs", Toast.LENGTH_SHORT).show()
                    }
                }

                showExtrasDialog = false
            },
            onDismiss = { showExtrasDialog = false }
        )
    }


    // UPDATED: Enhanced batsman selection dialog
    if (showBatsmanDialog) {
        val availableBatsmenCount = battingTeamPlayers.count { !it.isOut }

        EnhancedPlayerSelectionDialog(
            title = when {
                selectingBatsman == 1 && nonStriker == null && matchSettings.allowSingleSideBatting ->
                    "Select Batsman (Single Side Batting)"
                selectingBatsman == 1 -> "Select First Batsman"
                else -> "Select Second Batsman"
            },
            players = battingTeamPlayers,
            jokerPlayer = jokerPlayer,
            currentStrikerIndex = strikerIndex,
            currentNonStrikerIndex = nonStrikerIndex,
            allowSingleSide = matchSettings.allowSingleSideBatting,
            onPlayerSelected = { player ->
                if (selectingBatsman == 1) {
                    strikerIndex = if (player.isJoker) {
                        if (!battingTeamPlayers.any { it.isJoker }) {
                            val newList = battingTeamPlayers.toMutableList()
                            newList.add(jokerPlayer!!.copy())
                            battingTeamPlayers = newList
                            battingTeamPlayers.size - 1
                        } else {
                            battingTeamPlayers.indexOfFirst { it.isJoker }
                        }
                    } else {
                        battingTeamPlayers.indexOfFirst { it.name == player.name }
                    }

                    // Auto-select non-striker if available and not single side batting
                    if (!matchSettings.allowSingleSideBatting && nonStriker == null && availableBatsmenCount > 1) {
                        val otherAvailable = battingTeamPlayers.find { !it.isOut && it.name != player.name }
                        if (otherAvailable != null) {
                            nonStrikerIndex = battingTeamPlayers.indexOf(otherAvailable)
                        }
                    }
                } else {
                    // Only allow second batsman if not single side batting
                    if (!matchSettings.allowSingleSideBatting) {
                        nonStrikerIndex = if (player.isJoker) {
                            if (!battingTeamPlayers.any { it.isJoker }) {
                                val newList = battingTeamPlayers.toMutableList()
                                newList.add(jokerPlayer!!.copy())
                                battingTeamPlayers = newList
                                battingTeamPlayers.size - 1
                            } else {
                                battingTeamPlayers.indexOfFirst { it.isJoker }
                            }
                        } else {
                            battingTeamPlayers.indexOfFirst { it.name == player.name }
                        }
                    }
                }
                showBatsmanDialog = false
            },
            onDismiss = { showBatsmanDialog = false },
        )
    }

    // Bowler Selection Dialog with rotation rules
    if (showBowlerDialog) {
        EnhancedPlayerSelectionDialog(
            title = if (previousBowlerIndex != null) "Select New Bowler (Same bowler cannot bowl consecutive overs)" else "Select Bowler",
            players = bowlingTeamPlayers.filterIndexed { index, _ ->
                index != previousBowlerIndex // Exclude previous bowler
            },
            jokerPlayer = if (previousBowlerIndex != null && bowlingTeamPlayers.getOrNull(previousBowlerIndex!!)?.isJoker == true) {
                null // Exclude joker if they were the previous bowler
            } else {
                jokerPlayer
            },
            onPlayerSelected = { player ->
                bowlerIndex = if (player.isJoker) {
                    if (!bowlingTeamPlayers.any { it.isJoker }) {
                        val newList = bowlingTeamPlayers.toMutableList()
                        newList.add(jokerPlayer!!.copy())
                        bowlingTeamPlayers = newList
                        bowlingTeamPlayers.size - 1
                    } else {
                        bowlingTeamPlayers.indexOfFirst { it.isJoker }
                    }
                } else {
                    bowlingTeamPlayers.indexOfFirst { it.name == player.name }
                }

                currentBowlerSpell = 1
                showBowlerDialog = false
            },
            onDismiss = {
                if (previousBowlerIndex != null) {
                    Toast.makeText(context, "Please select a new bowler to continue", Toast.LENGTH_SHORT).show()
                } else {
                    showBowlerDialog = false
                }
            },
        )
    }

    // UPDATED: Enhanced Wicket Dialog with single side batting logic
    if (showWicketDialog) {
        WicketTypeDialog(
            onWicketSelected = { wicketType ->
                // Update match totals
                totalWickets += 1

                // Update striker stats (mark as out and face ball)
                updateStrikerAndTotals { player ->
                    player.copy(
                        isOut = true,
                        ballsFaced = player.ballsFaced + 1,
                    )
                }

                // Update bowler stats (add wicket and ball)
                updateBowlerStats { player ->
                    player.copy(
                        wickets = player.wickets + 1,
                        ballsBowled = player.ballsBowled + 1,
                    )
                }

                // Update over count
                ballsInOver += 1
                if (ballsInOver == 6) {
                    currentOver += 1
                    ballsInOver = 0
                    previousBowlerIndex = bowlerIndex
                    bowlerIndex = null
                    currentBowlerSpell = 0
                    showBowlerDialog = true
                }

                Toast.makeText(
                    context,
                    "Wicket! ${striker?.name} is ${wicketType.name.lowercase().replace("_", " ")}",
                    Toast.LENGTH_LONG,
                ).show()

                // ENHANCED: Better single side batting logic
                val availableBatsmenAfterWicket = battingTeamPlayers.count { !it.isOut }

                when {
                    // Case 1: No batsmen left - innings over
                    availableBatsmenAfterWicket == 0 -> {
                        strikerIndex = null
                        nonStrikerIndex = null
                        // Innings will end automatically via isInningsComplete
                    }

                    // Case 2: Single side batting enabled and only 1 batsman left
                    matchSettings.allowSingleSideBatting && availableBatsmenAfterWicket == 1 -> {
                        val lastBatsman = battingTeamPlayers.indexOfFirst { !it.isOut }
                        strikerIndex = lastBatsman
                        nonStrikerIndex = null
                        Toast.makeText(
                            context,
                            "Single side batting: ${battingTeamPlayers[lastBatsman].name} continues alone",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    // Case 3: Traditional cricket - need 2 batsmen
                    !matchSettings.allowSingleSideBatting && availableBatsmenAfterWicket == 1 -> {
                        strikerIndex = null
                        nonStrikerIndex = null
                        // Innings ends (traditional all-out)
                    }

                    // Case 4: Multiple batsmen available - select new batsman
                    else -> {
                        strikerIndex = null
                        selectingBatsman = 1
                        if (ballsInOver == 0 && currentOver > 0) {
                            // Bowler dialog will show automatically
                        } else {
                            showBatsmanDialog = true
                        }
                    }
                }

                showWicketDialog = false
            },
            onDismiss = { showWicketDialog = false },
        )
    }

    // Enhanced Innings Break Dialog with detailed stats
    if (showInningsBreakDialog) {
        EnhancedInningsBreakDialog(
            runs = firstInningsRuns,
            wickets = firstInningsWickets,
            overs = firstInningsOvers,
            balls = firstInningsBalls,
            battingTeam = battingTeamName,
            bowlingTeam = bowlingTeamName,
            battingPlayers = firstInningsBattingPlayersList,
            bowlingPlayers = firstInningsBowlingPlayersList,
            totalOvers = matchSettings.totalOvers,
            onStartSecondInnings = {
                currentInnings = 2

                // Swap teams and their names
                val tempPlayers = battingTeamPlayers
                val tempName = battingTeamName

                battingTeamPlayers = bowlingTeamPlayers
                bowlingTeamPlayers = tempPlayers
                battingTeamName = bowlingTeamName
                bowlingTeamName = tempName

                // Reset all player stats for second innings
                battingTeamPlayers = battingTeamPlayers.map { player ->
                    player.copy(
                        runs = 0,
                        ballsFaced = 0,
                        fours = 0,
                        sixes = 0,
                        isOut = false,
                    )
                }.toMutableList()

                bowlingTeamPlayers = bowlingTeamPlayers.map { player ->
                    player.copy(
                        wickets = 0,
                        runsConceded = 0,
                        ballsBowled = 0,
                    )
                }.toMutableList()

                // Reset match state
                totalWickets = 0
                currentOver = 0
                ballsInOver = 0
                totalExtras = 0
                previousBowlerIndex = null
                currentBowlerSpell = 0

                // Reset player indices
                strikerIndex = null
                nonStrikerIndex = null
                bowlerIndex = null

                showInningsBreakDialog = false
                showBatsmanDialog = true
                selectingBatsman = 1
            },
        )
    }

    // Enhanced Match Complete Dialog with comprehensive player info
    if (showMatchCompleteDialog) {
        EnhancedMatchCompleteDialog(
            firstInningsRuns = firstInningsRuns,
            firstInningsWickets = firstInningsWickets,
            secondInningsRuns = calculatedTotalRuns,
            secondInningsWickets = totalWickets,
            team1Name = team1Name,
            team2Name = team2Name,
            jokerPlayerName = jokerName.takeIf { it.isNotEmpty() },
            firstInningsBattingPlayers = firstInningsBattingPlayersList,
            firstInningsBowlingPlayers = firstInningsBowlingPlayersList,
            secondInningsBattingPlayers = secondInningsBattingPlayers,
            secondInningsBowlingPlayers = secondInningsBowlingPlayers,
            onNewMatch = {
                val intent = android.content.Intent(context, MainActivity::class.java)
                intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                context.startActivity(intent)
                (context as androidx.activity.ComponentActivity).finish()
            },
            onDismiss = { showMatchCompleteDialog = false },
            matchSettings = matchSettings
        )
    }

    // Exit confirmation dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit Match?") },
            text = { Text("Are you sure you want to exit? Match progress will be lost.") },
            confirmButton = {
                Button(
                    onClick = {
                        val intent = android.content.Intent(context, MainActivity::class.java)
                        context.startActivity(intent)
                        (context as androidx.activity.ComponentActivity).finish()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                ) {
                    Text("Exit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Continue Match")
                }
            },
        )
    }
}


// Add the missing saveMatchToHistory function and other helper functions from your existing code
fun saveMatchToHistory(
    team1Name: String,
    team2Name: String,
    jokerPlayerName: String?,
    firstInningsRuns: Int,
    firstInningsWickets: Int,
    secondInningsRuns: Int,
    secondInningsWickets: Int,
    winnerTeam: String,
    winningMargin: String,
    firstInningsBattingStats: List<PlayerMatchStats> = emptyList(),
    firstInningsBowlingStats: List<PlayerMatchStats> = emptyList(),
    secondInningsBattingStats: List<PlayerMatchStats> = emptyList(),
    secondInningsBowlingStats: List<PlayerMatchStats> = emptyList(),
    context: android.content.Context,
    matchSettings: MatchSettings,
) {
    android.util.Log.d("SaveMatch", "Attempting to save match: $team1Name vs $team2Name")

    // Find top performers across all innings
    val allBattingStats = firstInningsBattingStats + secondInningsBattingStats
    val allBowlingStats = firstInningsBowlingStats + secondInningsBowlingStats

    val topBatsman = allBattingStats.maxByOrNull { it.runs }
    val topBowler = allBowlingStats.maxByOrNull { it.wickets }

    val storageManager = MatchStorageManager(context)
    val matchHistory = MatchHistory(
        team1Name = team1Name,
        team2Name = team2Name,
        jokerPlayerName = jokerPlayerName,
        firstInningsRuns = firstInningsRuns,
        firstInningsWickets = firstInningsWickets,
        secondInningsRuns = secondInningsRuns,
        secondInningsWickets = secondInningsWickets,
        winnerTeam = winnerTeam,
        winningMargin = winningMargin,
        firstInningsBatting = firstInningsBattingStats,
        firstInningsBowling = firstInningsBowlingStats,
        secondInningsBatting = secondInningsBattingStats,
        secondInningsBowling = secondInningsBowlingStats,
        team1Players = firstInningsBattingStats + secondInningsBowlingStats,
        team2Players = firstInningsBowlingStats + secondInningsBattingStats,
        topBatsman = topBatsman,
        topBowler = topBowler,
        matchDate = System.currentTimeMillis(),
        matchSettings = matchSettings
    )

    storageManager.saveMatch(matchHistory)

    val allMatches = storageManager.getAllMatches()
    android.util.Log.d("SaveMatch", "Match saved with detailed stats! Total matches now: ${allMatches.size}")
    android.util.Log.d("stats", "Current match stats : $matchHistory")

    android.widget.Toast.makeText(
        context,
        "Match with detailed stats saved! Total: ${allMatches.size} matches 🏏📊",
        android.widget.Toast.LENGTH_LONG,
    ).show()
}

// Extension function to convert Player to PlayerMatchStats
fun Player.toMatchStats(teamName: String): PlayerMatchStats {
    return PlayerMatchStats(
        name = this.name,
        runs = this.runs,
        ballsFaced = this.ballsFaced,
        fours = this.fours,
        sixes = this.sixes,
        wickets = this.wickets,
        runsConceded = this.runsConceded,
        oversBowled = this.oversBowled,
        isOut = this.isOut,
        isJoker = this.isJoker,
        team = teamName
    )
}

// Enhanced Live Scorecard Dialog with complete player info
@Composable
fun LiveScorecardDialog(
    currentInnings: Int,
    battingTeamName: String,
    bowlingTeamName: String,
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
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "🏏 Live Scorecard",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.height(500.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Current Innings Score
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32)),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "$battingTeamName - Innings $currentInnings",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text(
                                text = "$currentRuns/$currentWickets ($currentOvers.$currentBalls/$totalOvers overs)",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )

                            if (currentInnings == 2) {
                                val target = firstInningsRuns + 1
                                val required = target - currentRuns
                                Text(
                                    text = if (required > 0) "Need $required runs" else "Target achieved!",
                                    fontSize = 12.sp,
                                    color = if (required > 0) Color.Yellow else Color.Green,
                                )
                            }
                        }
                    }
                }

                // First Innings Summary (if in second innings)
                if (currentInnings == 2) {
                    item {
                        Text(
                            text = "First Innings Summary",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32),
                        )
                    }

                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F8FF))) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "${if (battingTeamName == "Team A") "Team B" else "Team A"}: $firstInningsRuns/$firstInningsWickets",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )

                                if (firstInningsBattingPlayers.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Top Performers:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    val topBat = firstInningsBattingPlayers.maxByOrNull { it.runs }
                                    val topBowl = firstInningsBowlingPlayers.maxByOrNull { it.wickets }

                                    topBat?.let {
                                        Text("🏏 ${it.name}: ${it.runs} runs", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    topBowl?.let {
                                        if (it.wickets > 0) {
                                            Text("⚾ ${it.name}: ${it.wickets} wickets", fontSize = 11.sp, color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Current Innings Batting
                item {
                    Text(
                        text = "Current Innings - Batting",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2196F3),
                    )
                }

                val activeBatsmen = battingTeamPlayers.filter { it.ballsFaced > 0 || it.runs > 0 }
                if (activeBatsmen.isNotEmpty()) {
                    items(activeBatsmen.sortedByDescending { it.runs }) { player ->
                        LivePlayerStatCard(player, "batting")
                    }
                } else {
                    item {
                        Text(
                            text = "No batting data yet",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }

                // Players yet to bat
                val yetToBat = battingTeamPlayers.filter { it.ballsFaced == 0 && it.runs == 0 }
                if (yetToBat.isNotEmpty()) {
                    item {
                        Text(
                            text = "Yet to bat: ${yetToBat.joinToString(", ") { it.name }}",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                // Current Innings Bowling
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Current Innings - Bowling",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5722),
                    )
                }

                val activeBowlers = bowlingTeamPlayers.filter { it.ballsBowled > 0 || it.wickets > 0 || it.runsConceded > 0 }
                if (activeBowlers.isNotEmpty()) {
                    items(activeBowlers.sortedByDescending { it.wickets }) { player ->
                        LivePlayerStatCard(player, "bowling")
                    }
                } else {
                    item {
                        Text(
                            text = "No bowling data yet",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }

                // Players who didn't bowl yet
                val didNotBowl = bowlingTeamPlayers.filter { it.ballsBowled == 0 && it.wickets == 0 && it.runsConceded == 0 }
                if (didNotBowl.isNotEmpty()) {
                    item {
                        Text(
                            text = "Yet to bowl: ${didNotBowl.joinToString(", ") { it.name }}",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                // Joker player status
                if (jokerPlayerName.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                            val jokerInBatting = battingTeamPlayers.find { it.name == jokerPlayerName && it.isJoker }
                            val jokerInBowling = bowlingTeamPlayers.find { it.name == jokerPlayerName && it.isJoker }

                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "🃏 Joker Player: $jokerPlayerName",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF9800),
                                )

                                when {
                                    jokerInBatting != null -> {
                                        Text(
                                            text = "Currently batting: ${jokerInBatting.runs} runs (${jokerInBatting.ballsFaced} balls)",
                                            fontSize = 10.sp,
                                            color = Color(0xFFFF9800),
                                        )
                                    }
                                    jokerInBowling != null -> {
                                        Text(
                                            text = "Currently bowling: ${jokerInBowling.wickets}/${jokerInBowling.runsConceded} (${"%.1f".format(
                                                jokerInBowling.oversBowled,
                                            )} overs)",
                                            fontSize = 10.sp,
                                            color = Color(0xFFFF9800),
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = "Available for both teams",
                                            fontSize = 10.sp,
                                            color = Color(0xFFFF9800),
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
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
fun LivePlayerStatCard(
    player: Player,
    type: String,
) {
    Row(
        modifier =
            Modifier
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
            color = if (player.isJoker) Color(0xFFFF9800) else Color.Black,
        )

        when (type) {
            "batting" -> {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${player.runs}${if (!player.isOut && player.ballsFaced > 0) "*" else ""} (${player.ballsFaced})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    if (player.fours > 0 || player.sixes > 0) {
                        Text(
                            text = "4s:${player.fours} 6s:${player.sixes}",
                            fontSize = 10.sp,
                            color = Color.Gray,
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
                        color = Color(0xFFFF5722),
                    )
                    Text(
                        text = "${"%.1f".format(player.oversBowled)} ov, Eco: ${"%.1f".format(player.economy)}",
                        fontSize = 10.sp,
                        color = Color.Gray,
                    )
                }
            }
        }
    }
}

// Enhanced Innings Break Dialog
@Composable
fun EnhancedInningsBreakDialog(
    runs: Int,
    wickets: Int,
    overs: Int,
    balls: Int,
    battingTeam: String,
    bowlingTeam: String,
    battingPlayers: List<Player>,
    bowlingPlayers: List<Player>,
    totalOvers: Int,
    onStartSecondInnings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { },
        title = {
            Text(
                text = "🏏 First Innings Complete",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.height(400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32))) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = battingTeam,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text(
                                text = "$runs/$wickets ($overs.$balls/$totalOvers overs)",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "Batting Performance",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2196F3),
                    )
                }

                items(battingPlayers.sortedByDescending { it.runs }.take(5)) { player ->
                    LivePlayerStatCard(player, "batting")
                }

                val didNotBat = battingPlayers.filter { it.ballsFaced == 0 && it.runs == 0 }
                if (didNotBat.isNotEmpty()) {
                    item {
                        Text(
                            text = "Did not bat: ${didNotBat.joinToString(", ") { it.name }}",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Bowling Performance",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5722),
                    )
                }

                items(bowlingPlayers.sortedByDescending { it.wickets }.take(5)) { player ->
                    LivePlayerStatCard(player, "bowling")
                }

                val didNotBowl = bowlingPlayers.filter { it.ballsBowled == 0 && it.wickets == 0 }
                if (didNotBowl.isNotEmpty()) {
                    item {
                        Text(
                            text = "Did not bowl: ${didNotBowl.joinToString(", ") { it.name }}",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onStartSecondInnings,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
            ) {
                Text("Start 2nd Innings")
            }
        },
    )
}

@Composable
fun ExtrasDialog(
    matchSettings: MatchSettings,
    onExtraSelected: (ExtraType, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedExtraType by remember { mutableStateOf<ExtraType?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Extra Type + Runs") },
        text = {
            if (selectedExtraType == null) {
                // Step 1: Select extra type
                Column {
                    Text("Select Extra Type:", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

                    ExtraType.values().forEach { extraType ->
                        val baseRuns = when(extraType) {
                            ExtraType.OFF_SIDE_WIDE -> matchSettings.offSideWideRuns
                            ExtraType.LEG_SIDE_WIDE -> matchSettings.legSideWideRuns
                            ExtraType.NO_BALL -> matchSettings.noballRuns
                            ExtraType.BYE -> matchSettings.byeRuns
                            ExtraType.LEG_BYE -> matchSettings.legByeRuns
                        }

                        Button(
                            onClick = { selectedExtraType = extraType },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                        ) {
                            Text("${extraType.displayName} ($baseRuns)")
                        }
                    }
                }
            } else {
                // Step 2: Select additional runs (0-6)
                Column {
                    Text("${selectedExtraType!!.displayName} + Additional Runs:",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp))

                    val baseRuns = when(selectedExtraType!!) {
                        ExtraType.OFF_SIDE_WIDE -> matchSettings.offSideWideRuns
                        ExtraType.LEG_SIDE_WIDE -> matchSettings.legSideWideRuns
                        ExtraType.NO_BALL -> matchSettings.noballRuns
                        ExtraType.BYE -> matchSettings.byeRuns
                        ExtraType.LEG_BYE -> matchSettings.legByeRuns
                    }

                    LazyColumn {
                        items((0..6).toList()) { additionalRuns ->
                            val totalRuns = baseRuns + additionalRuns
                            Button(
                                onClick = { onExtraSelected(selectedExtraType!!, totalRuns) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = when(additionalRuns) {
                                        0 -> Color(0xFFFF9800)
                                        4 -> Color(0xFF4CAF50)
                                        6 -> Color(0xFF2196F3)
                                        else -> Color(0xFF9E9E9E)
                                    }
                                )
                            ) {
                                Text("${selectedExtraType!!.displayName} + $additionalRuns runs = $totalRuns total")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedExtraType != null) {
                TextButton(onClick = { selectedExtraType = null }) {
                    Text("Back")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun WicketTypeDialog(
    onWicketSelected: (WicketType) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "How was the batsman out?",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            LazyColumn {
                items(WicketType.values()) { wicketType ->
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onWicketSelected(wicketType) },
                        colors =
                            CardDefaults.cardColors(
                                containerColor = Color(0xFFFFEBEE),
                            ),
                    ) {
                        Text(
                            text =
                                wicketType.name
                                    .lowercase()
                                    .replace("_", " ")
                                    .uppercase(),
                            modifier = Modifier.padding(16.dp),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun EnhancedMatchCompleteDialog(
    firstInningsRuns: Int,
    firstInningsWickets: Int,
    secondInningsRuns: Int,
    secondInningsWickets: Int,
    team1Name: String,
    team2Name: String,
    jokerPlayerName: String?,
    firstInningsBattingPlayers: List<Player> = emptyList(),
    firstInningsBowlingPlayers: List<Player> = emptyList(),
    secondInningsBattingPlayers: List<Player> = emptyList(),
    secondInningsBowlingPlayers: List<Player> = emptyList(),
    onNewMatch: () -> Unit,
    onDismiss: () -> Unit,
    matchSettings: MatchSettings
) {
    val context = LocalContext.current
    val winner = if (secondInningsRuns > firstInningsRuns) team2Name else team1Name
    val margin =
        if (secondInningsRuns > firstInningsRuns) {
            "${calculateWicketMargin(secondInningsWickets)} wickets"
        } else {
            "${firstInningsRuns - secondInningsRuns} runs"
        }

    // Convert Player objects to PlayerMatchStats for analysis
    val firstInningsBattingStats = firstInningsBattingPlayers.map { it.toMatchStats(team1Name) }
    val firstInningsBowlingStats = firstInningsBowlingPlayers.map { it.toMatchStats(team2Name) }
    val secondInningsBattingStats = secondInningsBattingPlayers.map { it.toMatchStats(team2Name) }
    val secondInningsBowlingStats = secondInningsBowlingPlayers.map { it.toMatchStats(team1Name) }

    // Find overall top performers across both innings
    val allBattingStats = firstInningsBattingStats + secondInningsBattingStats
    val allBowlingStats = firstInningsBowlingStats + secondInningsBowlingStats
    val topBatsman = allBattingStats.maxByOrNull { it.runs }
    val topBowler = allBowlingStats.maxByOrNull { it.wickets }

    // Find best performances by innings
    val bestFirstInningsBat = firstInningsBattingStats.maxByOrNull { it.runs }
    val bestSecondInningsBat = secondInningsBattingStats.maxByOrNull { it.runs }

    LaunchedEffect(Unit) {
        // Save complete match with all detailed statistics
        saveMatchToHistory(
            team1Name = team1Name,
            team2Name = team2Name,
            jokerPlayerName = jokerPlayerName,
            firstInningsRuns = firstInningsRuns,
            firstInningsWickets = firstInningsWickets,
            secondInningsRuns = secondInningsRuns,
            secondInningsWickets = secondInningsWickets,
            winnerTeam = winner,
            winningMargin = margin,
            firstInningsBattingStats = firstInningsBattingStats,
            firstInningsBowlingStats = firstInningsBowlingStats,
            secondInningsBattingStats = secondInningsBattingStats,
            secondInningsBowlingStats = secondInningsBowlingStats,
            context = context,
            matchSettings = matchSettings
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "🏆 Match Complete!",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD700),
            )
        },
        text = {
            LazyColumn {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E8)),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val winner = if (secondInningsRuns > firstInningsRuns) team2Name else team1Name
                            val margin =
                                if (secondInningsRuns > firstInningsRuns) {
                                    "${10 - secondInningsWickets} wickets"
                                } else {
                                    "${firstInningsRuns - secondInningsRuns} runs"
                                }

                            Text(
                                text = "$winner won by $margin",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32),
                            )
                        }
                    }
                }

                // First Innings Summary
                item {
                    Text(
                        text = "$team1Name - 1st Innings: $firstInningsRuns/$firstInningsWickets",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // First Innings Batting
                item {
                    Text(
                        text = "Batting",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2196F3),
                    )
                }

                items(firstInningsBattingPlayers.sortedByDescending { it.runs }) { player ->
                    PlayerStatCard(player, "batting")
                }

                // Players who didn't bat in first innings
                val team1AllPlayers =
                    firstInningsBattingPlayers +
                        firstInningsBowlingPlayers.filter { bowler ->
                            !firstInningsBattingPlayers.any { it.name == bowler.name }
                        }

                // First Innings Bowling
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

                // Players who didn't bowl in first innings
                val team2AllPlayers =
                    firstInningsBowlingPlayers +
                        firstInningsBattingPlayers.filter { batter ->
                            !firstInningsBowlingPlayers.any { it.name == batter.name }
                        }

                // Second Innings Summary
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "$team2Name - 2nd Innings: $secondInningsRuns/$secondInningsWickets",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Second Innings Batting
                item {
                    Text(
                        text = "Batting",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2196F3),
                    )
                }

                items(secondInningsBattingPlayers.sortedByDescending { it.runs }) { player ->
                    PlayerStatCard(player, "batting")
                }

                // Second Innings Bowling
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

                // Joker player performance
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
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "🃏 Joker Performance: $jokerName",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF9800),
                                    )

                                    val totalRuns = (jokerFirstInningsBat?.runs ?: 0) + (jokerSecondInningsBat?.runs ?: 0)
                                    val totalWickets = (jokerFirstInningsBowl?.wickets ?: 0) + (jokerSecondInningsBowl?.wickets ?: 0)

                                    Text(
                                        text = "Total: $totalRuns runs, $totalWickets wickets",
                                        fontSize = 12.sp,
                                        color = Color(0xFFFF9800),
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
            ) {
                Text("New Match")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("View Details")
            }
        },
    )
}

@Composable
fun PlayerStatCard(
    player: Player,
    type: String,
) {
    Row(
        modifier =
            Modifier
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
                Text(
                    text = "${player.runs}${if (!player.isOut && player.ballsFaced > 0) "*" else ""} (${player.ballsFaced}) - 4s:${player.fours} 6s:${player.sixes}",
                    fontSize = 12.sp,
                    color = Color.Gray,
                )
            }
            "bowling" -> {
                Text(
                    text = "${player.wickets}/${player.runsConceded} (${"%.1f".format(
                        player.oversBowled,
                    )} ov) Eco: ${"%.1f".format(player.economy)}",
                    fontSize = 12.sp,
                    color = Color.Gray,
                )
            }
        }
    }
}

// Helper function to calculate wicket margin
fun calculateWicketMargin(wicketsLost: Int): Int {
    return 10 - wicketsLost // Assuming 10 wickets total
}

// Enhanced Player Selection Dialog with Current Stats
@Composable
fun EnhancedPlayerSelectionDialog(
    title: String,
    players: List<Player>,
    jokerPlayer: Player? = null,
    currentStrikerIndex: Int? = null,
    currentNonStrikerIndex: Int? = null,
    allowSingleSide: Boolean = false,
    onPlayerSelected: (Player) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.height(300.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Filter available players
                val availablePlayers =
                    players.filterIndexed { index, player ->
                        // Player is available if:
                        // 1. Not currently out
                        // 2. Not already selected as striker or non-striker
                        // 3. Or if single side batting is allowed, can reselect same player
                        !player.isOut &&
                            (index != currentStrikerIndex && index != currentNonStrikerIndex)
                    }

                items(availablePlayers) { player ->
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPlayerSelected(player) },
                        colors =
                            CardDefaults.cardColors(
                                containerColor = if (player.isJoker) Color(0xFFFFF3E0) else Color(0xFFF5F5F5),
                            ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Text(
                                text = if (player.isJoker) "🃏 ${player.name}" else player.name,
                                fontWeight = FontWeight.Medium,
                                color = if (player.isJoker) Color(0xFFFF9800) else Color.Black,
                            )

                            // Show current match stats for batsmen
                            if (title.contains("Batsman", ignoreCase = true)) {
                                if (player.ballsFaced > 0 || player.runs > 0) {
                                    Text(
                                        text = "${player.runs}${if (!player.isOut && player.ballsFaced > 0) "*" else ""} (${player.ballsFaced}) - SR: ${"%.1f".format(
                                            player.strikeRate,
                                        )}",
                                        fontSize = 12.sp,
                                        color = if (player.isJoker) Color(0xFFFF9800) else Color.Gray,
                                    )
                                    if (player.fours > 0 || player.sixes > 0) {
                                        Text(
                                            text = "4s: ${player.fours}, 6s: ${player.sixes}",
                                            fontSize = 10.sp,
                                            color = Color.Gray,
                                        )
                                    }
                                } else {
                                    Text(
                                        text = if (player.isJoker) "JOKER - Available for both teams" else "Yet to bat",
                                        fontSize = 12.sp,
                                        color = if (player.isJoker) Color(0xFFFF9800) else Color.Gray,
                                        fontStyle = FontStyle.Italic,
                                    )
                                }
                            }

                            // Show current match stats for bowlers
                            if (title.contains("Bowler", ignoreCase = true)) {
                                if (player.ballsBowled > 0 || player.wickets > 0 || player.runsConceded > 0) {
                                    Text(
                                        text = "${player.wickets}/${player.runsConceded} (${"%.1f".format(
                                            player.oversBowled,
                                        )} ov) - Eco: ${"%.1f".format(player.economy)}",
                                        fontSize = 12.sp,
                                        color = if (player.isJoker) Color(0xFFFF9800) else Color.Gray,
                                    )
                                } else {
                                    Text(
                                        text = if (player.isJoker) "JOKER - Available for both teams" else "Yet to bowl",
                                        fontSize = 12.sp,
                                        color = if (player.isJoker) Color(0xFFFF9800) else Color.Gray,
                                        fontStyle = FontStyle.Italic,
                                    )
                                }
                            }
                        }
                    }
                }

                // Add joker option if available and not already in the team
                jokerPlayer?.let { joker ->
                    if (!players.any { it.name == joker.name && it.isJoker } && !joker.isOut) {
                        item {
                            Card(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onPlayerSelected(joker) },
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = Color(0xFFFFF3E0),
                                    ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                ) {
                                    Text(
                                        text = "🃏 ${joker.name}",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF9800),
                                    )

                                    if (title.contains("Batsman", ignoreCase = true)) {
                                        Text(
                                            text = "JOKER - Can bat for this team (${joker.runs} runs, ${joker.ballsFaced} balls)",
                                            fontSize = 12.sp,
                                            color = Color(0xFFFF9800),
                                        )
                                    } else if (title.contains("Bowler", ignoreCase = true)) {
                                        Text(
                                            text = "JOKER - Can bowl for this team (${joker.wickets} wickets, ${"%.1f".format(
                                                joker.oversBowled,
                                            )} overs)",
                                            fontSize = 12.sp,
                                            color = Color(0xFFFF9800),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Show message if no players available
                if (availablePlayers.isEmpty() && jokerPlayer == null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                        ) {
                            Text(
                                text =
                                    if (title.contains("Batsman", ignoreCase = true)) {
                                        if (allowSingleSide) "All batsmen are out or selected" else "No available batsmen"
                                    } else {
                                        "No available bowlers"
                                    },
                                modifier = Modifier.padding(16.dp),
                                color = Color(0xFFF44336),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
