package com.oreki.stumpd

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.oreki.stumpd.ui.theme.StumpdTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class ScoringActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        val team1Name = intent.getStringExtra("team1_name") ?: "Team A"
        val team2Name = intent.getStringExtra("team2_name") ?: "Team B"
        val jokerName = intent.getStringExtra("joker_name") ?: ""
        val team1PlayerNames = intent.getStringArrayExtra("team1_players") ?: arrayOf("Player 1", "Player 2", "Player 3")
        val team2PlayerNames = intent.getStringArrayExtra("team2_players") ?: arrayOf("Player 4", "Player 5", "Player 6")
        val team1PlayerIds = intent.getStringArrayExtra("team1_player_ids") ?: emptyArray()
        val team2PlayerIds = intent.getStringArrayExtra("team2_player_ids") ?: emptyArray()
        val matchSettingsJson = intent.getStringExtra("match_settings") ?: ""
        val groupId = intent.getStringExtra("group_id")
        val groupName = intent.getStringExtra("group_name")
        val tossWinner = intent.getStringExtra("toss_winner")
        val tossChoice = intent.getStringExtra("toss_choice")


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
                        team1PlayerIds = team1PlayerIds,
                        team2PlayerIds = team2PlayerIds,
                        matchSettingsJson = matchSettingsJson,
                        groupId = groupId,
                        groupName = groupName,
                        tossChoice = tossChoice,
                        tossWinner = tossWinner
                    )
                }
            }
        }
    }
}

object NoBallOutcomeHolders {
    // Set by ExtrasDialog for NO_BALL flow; consumed in NO_BALL handler and cleared
    val noBallSubOutcome = mutableStateOf(NoBallSubOutcome.NONE)
    val noBallRunOutInput = mutableStateOf<RunOutInput?>(null)
    val noBallBoundaryOutInput = mutableStateOf<NoBallBoundaryOutInput?>(null)
}

@Composable
fun ScoringScreen(
    team1Name: String = "Team A",
    team2Name: String = "Team B",
    jokerName: String = "",
    team1PlayerNames: Array<String> = arrayOf("Player 1", "Player 2", "Player 3"),
    team2PlayerNames: Array<String> = arrayOf("Player 4", "Player 5", "Player 6"),
    team1PlayerIds: Array<String> = arrayOf("1","2"),
    team2PlayerIds: Array<String> = arrayOf("1","2"),
    matchSettingsJson: String = "",
    groupId: String?,
    groupName: String?,
    tossChoice: String?,
    tossWinner: String?,
) {
    val context = LocalContext.current
    val gson = Gson()

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
    var topBarVisible by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    fun revealTopBarTemporarily(timeoutMs: Long = 3500L) {
        topBarVisible = true
        scope.launch {
            delay(timeoutMs)
            topBarVisible = false
        }
    }
    val playerStorage = PlayerStorageManager(context)
    val all = remember { playerStorage.getAllPlayers().associateBy { it.id } }

    var team1Players by remember {
        mutableStateOf(
            if (team1PlayerIds.isNotEmpty()) {
                team1PlayerIds.map { id ->
                    val sp = all[id]
                    Player(id = PlayerId(id), name = sp?.name ?: id)
                }.toMutableList()
            } else {
                team1PlayerNames.map { Player(name = it) }.toMutableList()
            }
        )
    }
    var team2Players by remember {
        mutableStateOf(
            if (team2PlayerIds.isNotEmpty()) {
                team2PlayerIds.map { id ->
                    val sp = all[id]
                    Player(id = PlayerId(id), name = sp?.name ?: id)
                }.toMutableList()
            } else {
                team2PlayerNames.map { Player(name = it) }.toMutableList()
            }
        )
    }

    val jokerPlayer = remember {
        if (jokerName.isNotEmpty()) {
            Player(name = jokerName, isJoker = true)
        } else null
    }

    var currentInnings by remember { mutableStateOf(1) }
    // read toss extras once
//    val tossWinnerExtra = remember { (LocalContext.current as? ComponentActivity)?.intent?.getStringExtra("toss_winner") ?: "" }
//    val tossChoiceExtra = remember { (LocalContext.current as? ComponentActivity)?.intent?.getStringExtra("toss_choice") ?: "" }

    val initialBattingTeamPlayers: MutableList<Player>
    val initialBowlingTeamPlayers: MutableList<Player>
    val initialBattingTeamName: String
    val initialBowlingTeamName: String

    if (tossWinner != null && tossChoice != null) {
        val team1Won = tossWinner.equals(team1Name, ignoreCase = true)
        val battingChosen = tossChoice.contains("Batting", ignoreCase = true)

        // Decide who bats first
        val team1BatsFirst = (team1Won && battingChosen) || (!team1Won && !battingChosen)

        if (team1BatsFirst) {
            initialBattingTeamPlayers = team1Players
            initialBowlingTeamPlayers = team2Players
            initialBattingTeamName = team1Name
            initialBowlingTeamName = team2Name
        } else {
            initialBattingTeamPlayers = team2Players
            initialBowlingTeamPlayers = team1Players
            initialBattingTeamName = team2Name
            initialBowlingTeamName = team1Name
        }
    } else {
        // default
        initialBattingTeamPlayers = team1Players
        initialBowlingTeamPlayers = team2Players
        initialBattingTeamName = team1Name
        initialBowlingTeamName = team2Name
    }

    var battingTeamPlayers by remember { mutableStateOf(initialBattingTeamPlayers) }
    var bowlingTeamPlayers by remember { mutableStateOf(initialBowlingTeamPlayers) }
    var battingTeamName by remember { mutableStateOf(initialBattingTeamName) }
    var bowlingTeamName by remember { mutableStateOf(initialBowlingTeamName) }


    var firstInningsRuns by remember { mutableStateOf(0) }
    var firstInningsWickets by remember { mutableStateOf(0) }
    var firstInningsOvers by remember { mutableStateOf(0) }
    var firstInningsBalls by remember { mutableStateOf(0) }
    var firstInningsBattingPlayersList by remember { mutableStateOf<List<Player>>(emptyList()) }
    var firstInningsBowlingPlayersList by remember { mutableStateOf<List<Player>>(emptyList()) }
    var secondInningsBattingPlayers by remember { mutableStateOf<List<Player>>(emptyList()) }
    var secondInningsBowlingPlayers by remember { mutableStateOf<List<Player>>(emptyList()) }

    var totalWickets by remember { mutableStateOf(0) }
    var currentOver by remember { mutableStateOf(0) }
    var ballsInOver by remember { mutableStateOf(0) }
    var totalExtras by remember { mutableStateOf(0) }

    val calculatedTotalRuns = remember(battingTeamPlayers, totalExtras) {
        battingTeamPlayers.sumOf { it.runs } + totalExtras
    }

    var currentBowlerSpell by remember { mutableStateOf(0) }
    var strikerIndex by remember { mutableStateOf<Int?>(null) }
    var nonStrikerIndex by remember { mutableStateOf<Int?>(null) }
    var bowlerIndex by remember { mutableStateOf<Int?>(null) }

    val striker = strikerIndex?.let { battingTeamPlayers.getOrNull(it) }
    val nonStriker = nonStrikerIndex?.let { battingTeamPlayers.getOrNull(it) }
    val bowler = bowlerIndex?.let { bowlingTeamPlayers.getOrNull(it) }

    var showBatsmanDialog by remember { mutableStateOf(false) }
    var showBowlerDialog by remember { mutableStateOf(false) }
    var showWicketDialog by remember { mutableStateOf(false) }
    var showInningsBreakDialog by remember { mutableStateOf(false) }
    var showMatchCompleteDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showLiveScorecardDialog by remember { mutableStateOf(false) }
    var showExtrasDialog by remember { mutableStateOf(false) }
    var selectingBatsman by remember { mutableStateOf(1) }

    var previousBowlerName by remember { mutableStateOf<String?>(null) }

    var completedBattersInnings1 by remember { mutableStateOf(mutableListOf<Player>()) }
    var completedBattersInnings2 by remember { mutableStateOf(mutableListOf<Player>()) }

    var completedBowlersInnings1 by remember { mutableStateOf(mutableListOf<Player>()) }
    var completedBowlersInnings2 by remember { mutableStateOf(mutableListOf<Player>()) }

    var jokerOutInCurrentInnings by remember { mutableStateOf(false) }
    // Joker per-innings ball counters and helpers (ADD)
    var jokerBallsBowledInnings1 by remember { mutableStateOf(0) }
    var jokerBallsBowledInnings2 by remember { mutableStateOf(0) }
    val midOverReplacementDueToJoker = remember { mutableStateOf(false) }

    val deliveryHistory = remember { mutableStateListOf<DeliverySnapshot>() }
    val allDeliveries = remember { mutableStateListOf<DeliveryUI>() }
    // Derived state lists
    val currentOverNumber by remember { derivedStateOf { currentOver + 1 } }   // 1‑based
    val currentOverDeliveries by remember(allDeliveries, currentOver, currentInnings) {
        derivedStateOf {
            allDeliveries.filter { it.inning == currentInnings && it.over == currentOverNumber }
        }
    }

    var showRunOutDialog by remember { mutableStateOf(false) }
    var isNoBallRunOut by remember { mutableStateOf(false) }
    var pickerOtherEndName by remember { mutableStateOf<String?>(null) }
    var pendingSwapAfterBatsmanPick by remember { mutableStateOf(false) }
    var pendingBowlerDialogAfterBatsmanPick by remember { mutableStateOf(false) }

    fun addDelivery(outcome: String, highlight: Boolean = false) {
        // ball number shown 1..6 based on ballsInOver AFTER increment (so compute from current)
        val ballNumber = (ballsInOver % 6) + 1
        val entry = DeliveryUI(
            inning = currentInnings,
            over = currentOver + 1,
            ballInOver = ballNumber,
            outcome = outcome,
            highlight = highlight
        )
        allDeliveries.add(entry)
    }

    fun pushSnapshot() {
        deliveryHistory.add(
            DeliverySnapshot(
                strikerIndex = strikerIndex,
                nonStrikerIndex = nonStrikerIndex,
                bowlerIndex = bowlerIndex,
                battingTeamPlayers = battingTeamPlayers.map { it.copy() },
                bowlingTeamPlayers = bowlingTeamPlayers.map { it.copy() },
                totalWickets = totalWickets,
                currentOver = currentOver,
                ballsInOver = ballsInOver,
                totalExtras = totalExtras,
                calculatedTotalRuns = calculatedTotalRuns,
                previousBowlerName = previousBowlerName,
                midOverReplacementDueToJoker = midOverReplacementDueToJoker.value,
                jokerBallsBowledInnings1 = jokerBallsBowledInnings1,
                jokerBallsBowledInnings2 = jokerBallsBowledInnings2,
                completedBattersInnings1 = completedBattersInnings1.map { it.copy() },
                completedBattersInnings2 = completedBattersInnings2.map { it.copy() },
                completedBowlersInnings1 = completedBowlersInnings1.map { it.copy() },
                completedBowlersInnings2 = completedBowlersInnings2.map { it.copy() },
            )
        )
        if (deliveryHistory.size > 2) deliveryHistory.removeAt(0)
    }

    fun removeLastDeliveryIfAny() {
        if (allDeliveries.isNotEmpty()) {
            val last = allDeliveries.last()
            // Only remove if it is from the current over; prevents crossing to previous over
            if (last.over == currentOver + 1) {
                allDeliveries.removeAt(allDeliveries.lastIndex)
            }
        }
    }

    fun undoLastDelivery() {
        if (deliveryHistory.isEmpty()) {
            Toast.makeText(context, "Nothing to undo", Toast.LENGTH_SHORT).show()
            return
        }
        val peek = deliveryHistory.last()
        val atStartOfSecond = (currentInnings == 2 && currentOver == 0 && ballsInOver == 0)
        val wouldGoBeforeStartOfSecond = (currentInnings == 2 && peek.currentOver == 0 && peek.ballsInOver == 0)
        if (atStartOfSecond || wouldGoBeforeStartOfSecond) {
            Toast.makeText(context, "Cannot undo beyond 0.0 overs in 2nd innings", Toast.LENGTH_SHORT).show()
            return
        }
        val snap = deliveryHistory.removeAt(deliveryHistory.lastIndex)
        strikerIndex = snap.strikerIndex
        nonStrikerIndex = snap.nonStrikerIndex
        bowlerIndex = snap.bowlerIndex
        battingTeamPlayers = snap.battingTeamPlayers.toMutableList()
        bowlingTeamPlayers = snap.bowlingTeamPlayers.toMutableList()
        totalWickets = snap.totalWickets
        currentOver = snap.currentOver
        ballsInOver = snap.ballsInOver
        totalExtras = snap.totalExtras
        previousBowlerName = snap.previousBowlerName
        midOverReplacementDueToJoker.value = snap.midOverReplacementDueToJoker
        jokerBallsBowledInnings1 = snap.jokerBallsBowledInnings1
        jokerBallsBowledInnings2 = snap.jokerBallsBowledInnings2
        completedBattersInnings1 = snap.completedBattersInnings1.toMutableList()
        completedBattersInnings2 = snap.completedBattersInnings2.toMutableList()
        completedBowlersInnings1 = snap.completedBowlersInnings1.toMutableList()
        completedBowlersInnings2 = snap.completedBowlersInnings2.toMutableList()
        showBowlerDialog = false
        showBatsmanDialog = false
        showExtrasDialog = false
        showWicketDialog = false
        removeLastDeliveryIfAny()
        Toast.makeText(context, "Last delivery undone", Toast.LENGTH_SHORT).show()
    }

    fun recordCurrentBowlerIfAny() {
        val idx = bowlerIndex
        if (idx != null) {
            val p = bowlingTeamPlayers.getOrNull(idx)
            if (p != null && (p.ballsBowled > 0 || p.wickets > 0 || p.runsConceded > 0)) {
                if (currentInnings == 1) {
                    val exists = completedBowlersInnings1.indexOfFirst { it.name.equals(p.name, true) }
                    completedBowlersInnings1 =
                        if (exists == -1) (completedBowlersInnings1 + p.copy()).toMutableList()
                        else completedBowlersInnings1.mapIndexed { i, old -> if (i == exists) p.copy() else old }.toMutableList()
                } else {
                    val exists = completedBowlersInnings2.indexOfFirst { it.name.equals(p.name, true) }
                    completedBowlersInnings2 =
                        if (exists == -1) (completedBowlersInnings2 + p.copy()).toMutableList()
                        else completedBowlersInnings2.mapIndexed { i, old -> if (i == exists) p.copy() else old }.toMutableList()
                }
            }
        }
    }

    fun jokerBallsBowledThisInningsRaw(): Int =
        if (currentInnings == 1) jokerBallsBowledInnings1 else jokerBallsBowledInnings2

    fun jokerOversBowledThisInnings(): Double {
        val b = jokerBallsBowledThisInningsRaw()
        return (b / 6) + (b % 6) * 0.1
    }

    fun incJokerBallIfBowledThisDelivery() {
        val currentBowler = bowler
        if (currentBowler?.isJoker == true) {
            if (currentInnings == 1) jokerBallsBowledInnings1++ else jokerBallsBowledInnings2++
        }
    }


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
        val si = strikerIndex
        val nsi = nonStrikerIndex
        if (si == null || nsi == null) return  // only swap when both ends are occupied
        strikerIndex = nsi
        nonStrikerIndex = si
    }

    fun ensureJokerStatsAppliedOnAdd() {
        if (jokerPlayer == null) return
        val exists = bowlingTeamPlayers.indexOfFirst { it.isJoker }
        if (exists == -1) return
        val balls = if (currentInnings == 1) jokerBallsBowledInnings1 else jokerBallsBowledInnings2
        if (balls <= 0) return
        val list = bowlingTeamPlayers.toMutableList()
        val j = list[exists]
        list[exists] = j.copy(ballsBowled = balls)
        bowlingTeamPlayers = list
    }

    // At the top level - replace existing calculation
    val jokerAvailableForBatting = jokerPlayer != null &&
            !battingTeamPlayers.any { it.isJoker } &&
            !jokerOutInCurrentInnings

    val availableBatsmen = battingTeamPlayers.count { !it.isOut } +
            if (jokerAvailableForBatting) 1 else 0

    val showSingleSideLayout = matchSettings.allowSingleSideBatting && availableBatsmen == 1


    val isInningsComplete = currentOver >= matchSettings.totalOvers
            || (currentInnings == 2 && calculatedTotalRuns > firstInningsRuns)
            || if (matchSettings.allowSingleSideBatting) (availableBatsmen == 0) else (totalWickets >= battingTeamPlayers.size - 1)

    LaunchedEffect(isInningsComplete) {
        if (isInningsComplete) {
            if (currentInnings == 1) {
                firstInningsRuns = calculatedTotalRuns
                firstInningsWickets = totalWickets
                firstInningsOvers = currentOver
                firstInningsBalls = ballsInOver
                firstInningsBattingPlayersList =
                    (battingTeamPlayers.filter { it.ballsFaced > 0 || it.runs > 0 } + completedBattersInnings1)
                        .distinctBy { it.name }
                firstInningsBowlingPlayersList =
                    (bowlingTeamPlayers.filter { it.ballsBowled > 0 || it.wickets > 0 || it.runsConceded > 0 } +
                            completedBowlersInnings1)
                        .distinctBy { it.name }
                previousBowlerName = null
                showInningsBreakDialog = true
            } else {
                val secondInningsBattingPlayersList =
                    (battingTeamPlayers.filter { it.ballsFaced > 0 || it.runs > 0 } + completedBattersInnings2)
                        .distinctBy { it.name }
                secondInningsBattingPlayers = secondInningsBattingPlayersList
                val secondInningsBowlingPlayersList =
                    (bowlingTeamPlayers.filter { it.ballsBowled > 0 || it.wickets > 0 || it.runsConceded > 0 } +
                            completedBowlersInnings2)
                        .distinctBy { it.name }
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
        Spacer(Modifier.height(8.dp))
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
            firstInningsRuns = firstInningsRuns
        )

        Spacer(modifier = Modifier.height(16.dp))

        PlayersCard(
            showSingleSideLayout = showSingleSideLayout,
            striker = striker,
            nonStriker = nonStriker,
            bowler = bowler,
            availableBatsmen = availableBatsmen,
            onSelectStriker = {
                selectingBatsman = 1
                showBatsmanDialog = true
            },
            onSelectNonStriker = {
                selectingBatsman = 2
                showBatsmanDialog = true
            },
            onSelectBowler = { showBowlerDialog = true },
            onSwapStrike = {
                swapStrike()
                Toast.makeText(context, "Strike swapped! ${nonStriker?.name} now on strike", Toast.LENGTH_SHORT).show()
            },
            onShowLiveScorecard = { showLiveScorecardDialog = true },
            onBackPressed = {
                if (calculatedTotalRuns > 0 || currentOver > 0) {
                    showExitDialog = true
                } else {
                    val intent = android.content.Intent(context, MainActivity::class.java)
                    context.startActivity(intent)
                    (context as ComponentActivity).finish()
                }
            },
            currentBowlerSpell = currentBowlerSpell,
            jokerPlayer = jokerPlayer,
        )

        Spacer(modifier = Modifier.height(16.dp))

        ScoringButtons(
            striker = striker,
            nonStriker = nonStriker,
            bowler = bowler,
            isInningsComplete = isInningsComplete,
            matchSettings = matchSettings,
            availableBatsmen = availableBatsmen,
            calculatedTotalRuns = calculatedTotalRuns,
            onScoreRuns = { runs ->
                pushSnapshot()
                updateStrikerAndTotals { player ->
                    player.copy(
                        runs = player.runs + runs,
                        ballsFaced = player.ballsFaced + 1,
                        fours = if (runs == 4) player.fours + 1 else player.fours,
                        sixes = if (runs == 6) player.sixes + 1 else player.sixes,
                    )
                }
                updateBowlerStats { player ->
                    player.copy(
                        runsConceded = player.runsConceded + runs,
                        ballsBowled = player.ballsBowled + 1,
                    )
                }
                incJokerBallIfBowledThisDelivery()

                addDelivery(
                    outcome = runs.toString(),
                    highlight = (runs == 4 || runs == 6)
                )
                if (runs % 2 == 1 && !showSingleSideLayout) {
                    swapStrike()
                }
                ballsInOver += 1
                if (ballsInOver == 6) {
                    currentOver += 1
                    ballsInOver = 0
                    // snapshot current bowler’s final over
                    recordCurrentBowlerIfAny()
                    previousBowlerName = bowler?.name
                    bowlerIndex = null
                    currentBowlerSpell = 0
                    midOverReplacementDueToJoker.value = false
                    if (!showSingleSideLayout) {
                        swapStrike()
                    }
                    showBowlerDialog = true
                    Toast.makeText(context, "Over complete! Select new bowler", Toast.LENGTH_LONG).show()
                }
            },
            onShowExtras = { showExtrasDialog = true },
            onShowWicket = { showWicketDialog = true },
            onUndo = { undoLastDelivery() }
        )

        @OptIn(ExperimentalLayoutApi::class)
        if (currentOverDeliveries.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    maxItemsInEachRow = 6
                ) {
                    Text(
                        "Over ${currentOver + 1}:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    currentOverDeliveries.forEach { d ->
                        AssistChip(
                            onClick = {},
                            label = { Text(d.outcome, fontSize = 11.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = when {
                                    d.outcome == "W" -> MaterialTheme.colorScheme.errorContainer
                                    d.highlight      -> MaterialTheme.colorScheme.tertiaryContainer
                                    else             -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                labelColor = when {
                                    d.outcome == "W" -> MaterialTheme.colorScheme.onErrorContainer
                                    d.highlight      -> MaterialTheme.colorScheme.onTertiaryContainer
                                    else             -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        )
                    }
                }
            }
        }
    }

    if (showLiveScorecardDialog) {
        LiveScorecardDialog(
            currentInnings = currentInnings,
            battingTeamName = battingTeamName,
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

    if (showExtrasDialog) {
        ExtrasDialog(
            matchSettings = matchSettings,
            onExtraSelected = { extraType, totalRuns ->
                pushSnapshot()
                when (extraType) {
                    ExtraType.OFF_SIDE_WIDE, ExtraType.LEG_SIDE_WIDE -> {
                        updateBowlerStats { player ->
                            player.copy(runsConceded = player.runsConceded + totalRuns)
                        }
                        totalExtras += totalRuns
                        val baseWideRuns = if (extraType == ExtraType.OFF_SIDE_WIDE) matchSettings.offSideWideRuns else matchSettings.legSideWideRuns
                        val additionalRuns = totalRuns - baseWideRuns
                        if (additionalRuns % 2 == 1 && !showSingleSideLayout) {
                            swapStrike()
                        }
                        addDelivery("Wd+${totalRuns}")
                        Toast.makeText(context, "${extraType.displayName}! +$totalRuns runs", Toast.LENGTH_SHORT).show()
                    }
                    ExtraType.NO_BALL -> {
                        // DO NOT increment ballsInOver or bowler.ballsBowled on No ball
                        // Bowler concedes totalRuns; all counted in extras for now
                        updateBowlerStats { player ->
                            player.copy(runsConceded = player.runsConceded + totalRuns)
                        }
                        totalExtras += totalRuns

                        val sub = NoBallOutcomeHolders.noBallSubOutcome.value
                        val ro = NoBallOutcomeHolders.noBallRunOutInput.value
                        val bo = NoBallOutcomeHolders.noBallBoundaryOutInput.value

                        when (sub) {
                            NoBallSubOutcome.BOUNDARY_OUT -> {
                                // Custom gully rule: BoundaryOut behaves like a regular wicket (no run increment), allowed on No ball.
                                // Identify out batter: given name or default striker
                                val outName = bo?.outBatterName?.trim()?.takeIf { it.isNotEmpty() } ?: striker?.name ?: "Batsman"
                                val outIndex = battingTeamPlayers.indexOfFirst { it.name.equals(outName, ignoreCase = true) }
                                val validIndex = if (outIndex != -1) outIndex else strikerIndex

                                if (validIndex == null) {
                                    Toast.makeText(context, "Boundary out: could not resolve batter. Aborting wicket.", Toast.LENGTH_LONG).show()
                                    addDelivery("Nb+${totalRuns}")
                                } else {
                                    pushSnapshot()
                                    totalWickets += 1

                                    // Mark out but DO NOT add any runs; do NOT increment balls/over or bowler balls
                                    val newList = battingTeamPlayers.toMutableList()
                                    val dismissedPlayer = newList[validIndex]
                                    newList[validIndex] = dismissedPlayer.copy(
                                        isOut = true,
                                        ballsFaced = dismissedPlayer.ballsFaced + 1
                                    )
                                    val outSnapshot = newList[validIndex].copy()
                                    battingTeamPlayers = newList

                                    // Record completed batter snapshot
                                    if (currentInnings == 1) {
                                        if (completedBattersInnings1.none { it.name.equals(outSnapshot.name, true) }) {
                                            completedBattersInnings1 = (completedBattersInnings1 + outSnapshot).toMutableList()
                                        } else {
                                            completedBattersInnings1 = completedBattersInnings1.map {
                                                if (it.name.equals(outSnapshot.name, true)) outSnapshot else it
                                            }.toMutableList()
                                        }
                                    } else {
                                        if (completedBattersInnings2.none { it.name.equals(outSnapshot.name, true) }) {
                                            completedBattersInnings2 = (completedBattersInnings2 + outSnapshot).toMutableList()
                                        } else {
                                            completedBattersInnings2 = completedBattersInnings2.map {
                                                if (it.name.equals(outSnapshot.name, true)) outSnapshot else it
                                            }.toMutableList()
                                        }
                                    }

                                    // Clear the correct end slot; do not advance over or ballsInOver
                                    if (strikerIndex == validIndex) {
                                        strikerIndex = null
                                        selectingBatsman = 1
                                        pickerOtherEndName = nonStriker?.name
                                    } else if (nonStrikerIndex == validIndex) {
                                        nonStrikerIndex = null
                                        selectingBatsman = 2
                                        pickerOtherEndName = striker?.name
                                    } else {
                                        // Didn't match the two active ends; default to striker replacement
                                        strikerIndex = null
                                        selectingBatsman = 1
                                        pickerOtherEndName = nonStriker?.name
                                    }
                                    showBatsmanDialog = true

                                    // Log and toast
                                    addDelivery("Nb+${totalRuns}; BO(${outSnapshot.name})", highlight = true)
                                    val totalAfter = battingTeamPlayers.sumOf { it.runs } + totalExtras
                                    Toast.makeText(context, "No ball + Boundary out! Total: $totalAfter", Toast.LENGTH_SHORT).show()
                                }
                            }
                            NoBallSubOutcome.RUN_OUT -> {
                                isNoBallRunOut = true
                                val input = ro
                                if (input == null) {
                                    addDelivery("Nb+${totalRuns}")
                                    Toast.makeText(context, "No ball! +$totalRuns runs", Toast.LENGTH_SHORT).show()
                                } else {
                                    pushSnapshot()

                                    // Resolve outIndex robustly
                                    val name = input.whoOut.trim()
                                    val byName = battingTeamPlayers.indexOfFirst { it.name.equals(name, ignoreCase = true) }
                                    val byStriker = if (striker?.name.equals(name, true)) strikerIndex else null
                                    val byNonStriker = if (nonStriker?.name.equals(name, true)) nonStrikerIndex else null
                                    val resolvedIndex = when {
                                        byName != -1 -> byName
                                        byStriker != null -> byStriker
                                        byNonStriker != null -> byNonStriker
                                        else -> strikerIndex // last-resort: treat as striker
                                    }

                                    if (resolvedIndex == null) {
                                        Toast.makeText(context, "Run out on No ball: player \"$name\" not found", Toast.LENGTH_LONG).show()
                                        addDelivery("Nb+${totalRuns}")
                                    } else {
                                        // Mark out; do NOT change ballsInOver or bowler.ballsBowled on No ball
                                        val updated = battingTeamPlayers.toMutableList()
                                        updated[resolvedIndex] = updated[resolvedIndex].copy(isOut = true)
                                        val outSnapshot = updated[resolvedIndex].copy()
                                        battingTeamPlayers = updated
                                        totalWickets += 1

                                        // Ledger
                                        if (currentInnings == 1) {
                                            if (completedBattersInnings1.none { it.name.equals(outSnapshot.name, true) }) {
                                                completedBattersInnings1 = (completedBattersInnings1 + outSnapshot).toMutableList()
                                            } else {
                                                completedBattersInnings1 = completedBattersInnings1.map {
                                                    if (it.name.equals(outSnapshot.name, true)) outSnapshot else it
                                                }.toMutableList()
                                            }
                                        } else {
                                            if (completedBattersInnings2.none { it.name.equals(outSnapshot.name, true) }) {
                                                completedBattersInnings2 = (completedBattersInnings2 + outSnapshot).toMutableList()
                                            } else {
                                                completedBattersInnings2 = completedBattersInnings2.map {
                                                    if (it.name.equals(outSnapshot.name, true)) outSnapshot else it
                                                }.toMutableList()
                                            }
                                        }

                                        // Only clear the end where wicket occurred; keep the other end intact.
                                        if (input.end == RunOutEnd.STRIKER_END) {
                                            if (strikerIndex == resolvedIndex) {
                                                strikerIndex = null
                                            }
                                            // if mismatch (non-striker dismissed but scorer said S), still free striker by intent ONLY if indexes matched
                                            selectingBatsman = 1
                                            pickerOtherEndName = nonStriker?.name
                                        } else { // NON_STRIKER_END
                                            if (nonStrikerIndex == resolvedIndex) {
                                                nonStrikerIndex = null
                                            }
                                            selectingBatsman = 2
                                            pickerOtherEndName = striker?.name
                                        }
                                        showBatsmanDialog = true

                                        addDelivery("Nb+${totalRuns}; RO(${outSnapshot.name} @ ${if (input.end == RunOutEnd.STRIKER_END) "S" else "NS"})", highlight = true)
                                        val totalAfter = battingTeamPlayers.sumOf { it.runs } + totalExtras
                                        Toast.makeText(context, "No ball + Run out! Total: $totalAfter", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            else -> {
                                // None: just Nb + runs; do not increment balls/over here either.
                                val additionalRuns = totalRuns - matchSettings.noballRuns
                                if (additionalRuns % 2 == 1 && !showSingleSideLayout) {
                                    swapStrike()
                                }
                                addDelivery("Nb+${totalRuns}")
                                Toast.makeText(context, "No ball! +$totalRuns runs", Toast.LENGTH_SHORT).show()
                            }
                        }

                        // Clear holders after consumption
                        NoBallOutcomeHolders.noBallSubOutcome.value = NoBallSubOutcome.NONE
                        NoBallOutcomeHolders.noBallRunOutInput.value = null
                        NoBallOutcomeHolders.noBallBoundaryOutInput.value = null
                    }
                    ExtraType.BYE, ExtraType.LEG_BYE -> {
                        updateStrikerAndTotals { player ->
                            player.copy(ballsFaced = player.ballsFaced + 1)
                        }
                        updateBowlerStats { player ->
                            player.copy(ballsBowled = player.ballsBowled + 1)
                        }
                        incJokerBallIfBowledThisDelivery()
                        totalExtras += totalRuns
                        ballsInOver += 1
                        addDelivery(if (extraType == ExtraType.BYE) "B+$totalRuns" else "Lb+$totalRuns")
                        if (ballsInOver == 6) {
                            currentOver += 1
                            ballsInOver = 0
                            recordCurrentBowlerIfAny()
                            previousBowlerName = bowler?.name
                            bowlerIndex = null
                            currentBowlerSpell = 0
                            midOverReplacementDueToJoker.value = false
                            if (!showSingleSideLayout) {
                                swapStrike()
                            }
                            showBowlerDialog = true
                            Toast.makeText(context, "Over complete! Select new bowler", Toast.LENGTH_LONG).show()
                        } else {
                            val baseByeRuns = if (extraType == ExtraType.BYE) matchSettings.byeRuns else matchSettings.legByeRuns
                            val additionalRuns = totalRuns - baseByeRuns
                            if (additionalRuns % 2 == 1 && !showSingleSideLayout) {
                                swapStrike()
                            }
                        }
                        Toast.makeText(context, "${extraType.displayName}! +$totalRuns runs", Toast.LENGTH_SHORT).show()
                    }
                }
                val totalAfter = battingTeamPlayers.sumOf { it.runs } + totalExtras
                Toast.makeText(context, "${extraType.displayName}: +$totalRuns. Total: $totalAfter", Toast.LENGTH_SHORT).show()
                showExtrasDialog = false
            },
            onDismiss = { showExtrasDialog = false },
            striker = striker,
            nonStriker = nonStriker
        )
    }

    if (showBatsmanDialog) {
        val availableBatsmenCount = battingTeamPlayers.count { !it.isOut }
        val jokerAvailableForBattingInsideBatsmanDialog = jokerPlayer != null &&
                !battingTeamPlayers.any { it.isJoker } &&
                !jokerPlayer.isOut


        EnhancedPlayerSelectionDialog(
            title = when {
                selectingBatsman == 1 -> "Select Striker"
                else -> "Select Non-Striker"
            },
            players = battingTeamPlayers,
            // FIXED: Only show joker if wickets have fallen (not for opening)
            jokerPlayer = if (totalWickets == 0 || jokerOutInCurrentInnings) null else jokerPlayer,
            currentStrikerIndex = strikerIndex,
            currentNonStrikerIndex = nonStrikerIndex,
            allowSingleSide = matchSettings.allowSingleSideBatting,
            totalWickets = totalWickets,
            battingTeamPlayers = battingTeamPlayers,
            bowlingTeamPlayers = bowlingTeamPlayers,
            jokerOversThisInnings = jokerOversBowledThisInnings(),
            onPlayerSelected = { player ->
                if (selectingBatsman == 1) {
                    if (player.isJoker) {
                        // Remove joker from bowling team if they were bowling
                        val jokerBowlingIndex = bowlingTeamPlayers.indexOfFirst { it.isJoker }
                        if (jokerBowlingIndex != -1) {
                            // Reset bowler if joker was bowling
                            if (bowlerIndex == jokerBowlingIndex) {
                                recordCurrentBowlerIfAny()
                                bowlerIndex = null
                                currentBowlerSpell = 0
                                if (ballsInOver > 0) {
                                    midOverReplacementDueToJoker.value = true
                                    Toast.makeText(context, "🃏 Joker switched to bat. Select a new bowler to complete the over.", Toast.LENGTH_LONG).show()
                                    showBowlerDialog = true
                                }
                            }

                            val newBowlingList = bowlingTeamPlayers.toMutableList()
                            newBowlingList.removeAt(jokerBowlingIndex)
                            bowlingTeamPlayers = newBowlingList

                            // Update previous bowler index
                            if (previousBowlerName == jokerName) {
                                previousBowlerName = null
                            }
                        }

                        // Add joker to batting team
                        if (!battingTeamPlayers.any { it.isJoker }) {
                            val newList = battingTeamPlayers.toMutableList()
                            newList.add(jokerPlayer!!.copy())
                            battingTeamPlayers = newList
                            strikerIndex = battingTeamPlayers.size - 1
                        } else {
                            strikerIndex = battingTeamPlayers.indexOfFirst { it.isJoker }
                        }
                        jokerOutInCurrentInnings = false
                    } else {
                        strikerIndex = battingTeamPlayers.indexOfFirst { it.name.trim().equals(player.name.trim(), ignoreCase = true) }
                    }

                    if (!matchSettings.allowSingleSideBatting && (availableBatsmenCount + if (jokerAvailableForBattingInsideBatsmanDialog) 1 else 0) > 1) {
                        selectingBatsman = 2
                        // Keep dialog open for second selection
                    } else {
                        showBatsmanDialog = false

                        if (pendingSwapAfterBatsmanPick) {
                            if (!showSingleSideLayout) swapStrike()
                            pendingSwapAfterBatsmanPick = false
                        }
                        if (pendingBowlerDialogAfterBatsmanPick) {
                            showBowlerDialog = true
                            pendingBowlerDialogAfterBatsmanPick = false
                        }

                    }
                } else {
                    // Second batsman selection - same logic for joker
                    if (player.isJoker) {
                        // Remove joker from bowling team if they were bowling
                        val jokerBowlingIndex = bowlingTeamPlayers.indexOfFirst { it.isJoker }
                        if (jokerBowlingIndex != -1) {
                            // Reset bowler if joker was bowling
                            if (bowlerIndex == jokerBowlingIndex) {
                                recordCurrentBowlerIfAny()
                                bowlerIndex = null
                                currentBowlerSpell = 0
                                if (ballsInOver > 0) {
                                    midOverReplacementDueToJoker.value = true
                                    Toast.makeText(context, "🃏 Joker switched to bat. Select a new bowler to complete the over.", Toast.LENGTH_LONG).show()
                                    showBowlerDialog = true
                                }
                            }
                            val newBowlingList = bowlingTeamPlayers.toMutableList()
                            newBowlingList.removeAt(jokerBowlingIndex)
                            bowlingTeamPlayers = newBowlingList

                            // Update previous bowler index
                            if (previousBowlerName == jokerName) {
                                previousBowlerName = null
                            }
                        }

                        if (!battingTeamPlayers.any { it.isJoker }) {
                            val newList = battingTeamPlayers.toMutableList()
                            newList.add(jokerPlayer!!.copy())
                            battingTeamPlayers = newList
                            nonStrikerIndex = battingTeamPlayers.size - 1
                        } else {
                            nonStrikerIndex = battingTeamPlayers.indexOfFirst { it.isJoker }
                        }
                    } else {
                        nonStrikerIndex = battingTeamPlayers.indexOfFirst { it.name.trim().equals(player.name.trim(), ignoreCase = true) }
                    }
                    jokerOutInCurrentInnings = false
                    showBatsmanDialog = false

                    if (pendingSwapAfterBatsmanPick) {
                        if (!showSingleSideLayout) swapStrike()
                        pendingSwapAfterBatsmanPick = false
                    }
                    if (pendingBowlerDialogAfterBatsmanPick) {
                        showBowlerDialog = true
                        pendingBowlerDialogAfterBatsmanPick = false
                    }

                }
            },
            jokerOutInCurrentInnings = jokerOutInCurrentInnings,
            onDismiss = { showBatsmanDialog = false },
            matchSettings = matchSettings,
            otherEndName = pickerOtherEndName
        )
    }

    if (showBowlerDialog) {
        val bowlerPool = if (ballsInOver == 0) {
            val prev = previousBowlerName?.trim()
            bowlingTeamPlayers.filter { !it.name.trim().equals(prev, ignoreCase = true) }
        } else {
            bowlingTeamPlayers
        }
        // Allow cap override only at the start of an over if nobody can legally bowl 6 balls
        val overrideToCompleteOverAllowed = (ballsInOver == 0) && run {
            val prev = previousBowlerName?.trim()
            val startOverPool = if (prev != null) {
                bowlingTeamPlayers.filter { !it.name.trim().equals(prev, ignoreCase = true) }
            } else {
                bowlingTeamPlayers
            }

            val anyoneHasSix = startOverPool.any { p ->
                val isJoker = p.isJoker
                val ballsSoFar = if (isJoker) {
                    // Use the innings-ledger for Joker
                    jokerBallsBowledThisInningsRaw()
                } else {
                    p.ballsBowled
                }
                val capOvers = if (isJoker) matchSettings.jokerMaxOvers else matchSettings.maxOversPerBowler
                val capBalls = capOvers * 6
                val remainingBalls = (capBalls - ballsSoFar).coerceAtLeast(0)
                remainingBalls >= 6
            }
            !anyoneHasSix
        }
        EnhancedPlayerSelectionDialog(
            title = when {
                ballsInOver == 0 && previousBowlerName != null -> "Select New Bowler (Same bowler cannot bowl consecutive overs)"
                bowlerIndex == null && ballsInOver > 0 -> "Select Bowler to Complete Over"
                else -> "Select Bowler"
            },
            players = bowlerPool,
            // FIXED: Only show joker if they're not currently batting
            jokerPlayer = if (battingTeamPlayers.any { it.isJoker }) null else jokerPlayer,
            totalWickets = totalWickets,
            battingTeamPlayers = battingTeamPlayers,
            bowlingTeamPlayers = bowlingTeamPlayers,
            jokerOversThisInnings = jokerOversBowledThisInnings(),
            jokerOutInCurrentInnings = jokerOutInCurrentInnings,
            onPlayerSelected = { player ->
                // Normalize lookup for current stats
                fun norm(s: String): String = s.trim().replace(Regex("\\s+"), " ")
                val ballsSoFar = if (player.isJoker) {
                    // Always trust the innings ledger for Joker
                    jokerBallsBowledThisInningsRaw()
                } else {
                    val idxInBowling = bowlingTeamPlayers.indexOfFirst {
                        norm(it.name).equals(norm(player.name), ignoreCase = true)
                    }
                    bowlingTeamPlayers.getOrNull(idxInBowling)?.ballsBowled ?: 0
                }

                // Determine per-player cap and remaining legal balls
                val capOvers = if (player.isJoker) matchSettings.jokerMaxOvers else matchSettings.maxOversPerBowler
                val capBalls = capOvers * 6
                val remainingBalls = (capBalls - ballsSoFar).coerceAtLeast(0)

                // If selecting the same bowler mid-over, just close the dialog silently
                run {
                    val currentIdx = bowlerIndex
                    if (ballsInOver > 0 && currentIdx != null) {
                        val current = bowlingTeamPlayers.getOrNull(currentIdx)
                        if (current != null) {
                            fun norm(s: String): String = s.trim().replace(Regex("\\s+"), " ")
                            val same = (!player.isJoker && norm(current.name).equals(norm(player.name), ignoreCase = true)) ||
                                    (player.isJoker && current.isJoker)
                            if (same) {
                                showBowlerDialog = false
                                return@EnhancedPlayerSelectionDialog
                            }
                        }
                    }
                }

// 1) New-over requires 6 legal balls (with one-over override if nobody has 6)
                if (ballsInOver == 0 && remainingBalls < 6) {
                    val canOverrideThisOver =
                        overrideToCompleteOverAllowed && !player.isJoker
                    if (!canOverrideThisOver) {
                        val who = if (player.isJoker) "Joker" else player.name
                        Toast.makeText(
                            context,
                            "$who cannot start a new over (only $remainingBalls legal ball${if (remainingBalls != 1) "s" else ""} remaining; needs 6).",
                            Toast.LENGTH_LONG
                        ).show()
                        return@EnhancedPlayerSelectionDialog
                    } else {
                        Toast.makeText(
                            context,
                            "${player.name} will exceed max-over cap only to complete this over.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }


// 2) Mid-over replacement (only allowed after Joker left mid-over)
                if (ballsInOver > 0) {
                    if (!midOverReplacementDueToJoker.value) {
                        Toast.makeText(context, "Mid-over bowler change is only allowed when replacing the Joker.", Toast.LENGTH_LONG).show()
                        return@EnhancedPlayerSelectionDialog
                    }
                    val need = 6 - ballsInOver
                    if (remainingBalls < need) {
                        val who = if (player.isJoker) "Joker" else player.name
                        Toast.makeText(context, "$who cannot replace mid-over (needs $need balls, only $remainingBalls remaining).", Toast.LENGTH_LONG).show()
                        return@EnhancedPlayerSelectionDialog
                    }
                }

// Proceed with selection
                if (player.isJoker) {
                    if (!bowlingTeamPlayers.any { it.isJoker } && jokerPlayer != null) {
                        val newList = bowlingTeamPlayers.toMutableList()
                        newList.add(jokerPlayer.copy())
                        bowlingTeamPlayers = newList
                    }
// Apply the innings-ledger balls to the live Joker entry
                    ensureJokerStatsAppliedOnAdd()
                    bowlerIndex = bowlingTeamPlayers.indexOfFirst { it.isJoker }
                } else {
                    bowlerIndex = bowlingTeamPlayers.indexOfFirst { norm(it.name).equals(norm(player.name), ignoreCase = true) }
                }

                currentBowlerSpell = 1
                showBowlerDialog = false
                midOverReplacementDueToJoker.value = false
            },
            onDismiss = {
                if (ballsInOver > 0 && bowlerIndex == null) {
                    Toast.makeText(context, "Please select a bowler to continue", Toast.LENGTH_SHORT).show()
                } else {
                    showBowlerDialog = false
                    // Defensive: ensure flag is not stuck
                    midOverReplacementDueToJoker.value = false
                }
            },
            matchSettings = matchSettings,
            otherEndName = pickerOtherEndName
        )
    }

    if (showInningsBreakDialog) {
        jokerOutInCurrentInnings = false
        EnhancedInningsBreakDialog(
            runs = firstInningsRuns,
            wickets = firstInningsWickets,
            overs = firstInningsOvers,
            balls = firstInningsBalls,
            battingTeam = battingTeamName,
            battingPlayers = firstInningsBattingPlayersList,
            bowlingPlayers = firstInningsBowlingPlayersList,
            totalOvers = matchSettings.totalOvers,
            onStartSecondInnings = {
                currentInnings = 2
                val tempPlayers = battingTeamPlayers
                val tempName = battingTeamName
                battingTeamPlayers = bowlingTeamPlayers
                bowlingTeamPlayers = tempPlayers
                battingTeamName = bowlingTeamName
                bowlingTeamName = tempName
                battingTeamPlayers = battingTeamPlayers.map { player ->
                    player.copy(runs = 0, ballsFaced = 0, fours = 0, sixes = 0, isOut = false)
                }.toMutableList()
                bowlingTeamPlayers = bowlingTeamPlayers.map { player ->
                    player.copy(wickets = 0, runsConceded = 0, ballsBowled = 0, isOut = false)
                }.toMutableList()
                // Make sure Joker is not already in either side at innings start
                battingTeamPlayers = battingTeamPlayers.filter { !it.isJoker }.toMutableList()
                bowlingTeamPlayers = bowlingTeamPlayers.filter { !it.isJoker }.toMutableList()
                // Joker flags/ball counters reset for new innings context
                jokerOutInCurrentInnings = false

                totalWickets = 0
                currentOver = 0
                ballsInOver = 0
                totalExtras = 0
                previousBowlerName = null
                completedBowlersInnings2 = mutableListOf()
                currentBowlerSpell = 0
                strikerIndex = null
                nonStrikerIndex = null
                bowlerIndex = null
                showInningsBreakDialog = false
                showBatsmanDialog = true
                selectingBatsman = 1
            },
        )
    }

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
                intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                context.startActivity(intent)
                (context as androidx.activity.ComponentActivity).finish()
            },
            onDismiss = {
                showMatchCompleteDialog = false

                val intent = android.content.Intent(context, FullScorecardActivity::class.java)
                intent.putExtra("match_id","")
                context.startActivity(intent)
                (context as androidx.activity.ComponentActivity).finish()
            },
            matchSettings = matchSettings,
            groupId = groupId,
            groupName = groupName
        )
    }

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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
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

    if (showWicketDialog) {
        WicketTypeDialog(
            onWicketSelected = { wicketType ->
                val dismissedIndex = strikerIndex
                val jokerWasOut = striker?.isJoker == true

                if (wicketType == WicketType.RUN_OUT) {
                    showWicketDialog = false
                    showRunOutDialog = true
                    return@WicketTypeDialog
                }

                // --- begin replacement logic ---
                pushSnapshot()
                totalWickets += 1

                // mark striker as out in the striker's record
                updateStrikerAndTotals { p ->
                    p.copy(isOut = true, ballsFaced = p.ballsFaced + 1)
                }

                val outSnapshot = dismissedIndex?.let { idx ->
                    battingTeamPlayers.getOrNull(idx)?.copy()
                }

                // ledger update (unchanged)
                if (outSnapshot != null && (outSnapshot.ballsFaced > 0 || outSnapshot.runs > 0)) {
                    if (currentInnings == 1) {
                        if (completedBattersInnings1.none { it.name.equals(outSnapshot.name, true) }) {
                            completedBattersInnings1 =
                                (completedBattersInnings1 + outSnapshot).toMutableList()
                        } else {
                            completedBattersInnings1 = completedBattersInnings1
                                .map { if (it.name.equals(outSnapshot.name, true)) outSnapshot else it }
                                .toMutableList()
                        }
                    } else {
                        if (completedBattersInnings2.none { it.name.equals(outSnapshot.name, true) }) {
                            completedBattersInnings2 =
                                (completedBattersInnings2 + outSnapshot).toMutableList()
                        } else {
                            completedBattersInnings2 = completedBattersInnings2
                                .map { if (it.name.equals(outSnapshot.name, true)) outSnapshot else it }
                                .toMutableList()
                        }
                    }
                }

                // update bowler (unchanged)
                updateBowlerStats { player ->
                    player.copy(
                        wickets = player.wickets + 1,
                        ballsBowled = player.ballsBowled + 1
                    )
                }

                // cache current end names/indexes BEFORE any index changes
                val curStrikerIndex = strikerIndex
                val curNonStrikerIndex = nonStrikerIndex
                val curStrikerName = striker?.name
                val curNonStrikerName = nonStriker?.name

                val jokerWasBowling = bowler?.isJoker == true

                Toast.makeText(
                    context,
                    "Wicket! ${striker?.name} is ${wicketType.name.lowercase().replace("_", " ")}",
                    Toast.LENGTH_LONG
                ).show()

                // Handle joker-out removal (unchanged)
                if (jokerWasOut) {
                    jokerOutInCurrentInnings = true
                    val jokerBattingIndex = battingTeamPlayers.indexOfFirst { it.isJoker }
                    if (jokerBattingIndex != -1) {
                        val newBattingList = battingTeamPlayers.toMutableList()
                        newBattingList.removeAt(jokerBattingIndex)
                        battingTeamPlayers = newBattingList

                        if (strikerIndex == jokerBattingIndex) strikerIndex = null
                        if (nonStrikerIndex == jokerBattingIndex) nonStrikerIndex = null
                        else if (nonStrikerIndex != null && nonStrikerIndex!! > jokerBattingIndex) {
                            nonStrikerIndex = nonStrikerIndex!! - 1
                        }
                    }
                    Toast.makeText(
                        context,
                        "🃏 Joker is now available for bowling!",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                // Recompute availability AFTER any joker removal
                val availableBatsmenAfterWicket = battingTeamPlayers.count { !it.isOut }
                val jokerAvailableForBattingInWicketDialog = jokerPlayer != null &&
                        !battingTeamPlayers.any { it.isJoker } &&
                        !jokerOutInCurrentInnings

                val totalAvailableBatsmen = availableBatsmenAfterWicket + if (jokerAvailableForBattingInWicketDialog) 1 else 0

                when {
                    totalAvailableBatsmen == 0 -> {
                        strikerIndex = null
                        nonStrikerIndex = null
                    }

                    // single-side batting: be end-aware (place last batter into the end that is empty)
                    matchSettings.allowSingleSideBatting && totalAvailableBatsmen == 1 -> {
                        if (jokerAvailableForBattingInWicketDialog && availableBatsmenAfterWicket == 0) {
                            // Joker is only available — open picker at the end that was just freed
                            selectingBatsman = if (dismissedIndex == curStrikerIndex) 1 else 2
                            pickerOtherEndName = if (dismissedIndex == curStrikerIndex) curNonStrikerName else curStrikerName
                            showBatsmanDialog = true
                            Toast.makeText(context, "🃏 Only joker available to bat!", Toast.LENGTH_LONG).show()
                        } else {
                            // keep the remaining batter in the slot that is NOT dismissed
                            val lastBatsman = battingTeamPlayers.indexOfFirst { !it.isOut }
                            if (curStrikerIndex == dismissedIndex) {
                                // striker was dismissed earlier → place last batsman in striker slot
                                strikerIndex = lastBatsman
                            } else {
                                // non-striker slot was freed earlier → place last batsman in non-striker slot
                                nonStrikerIndex = lastBatsman
                            }
                            Toast.makeText(
                                context,
                                "Single side batting: ${battingTeamPlayers[lastBatsman].name} continues alone",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    !matchSettings.allowSingleSideBatting && totalAvailableBatsmen == 1 -> {
                        strikerIndex = null
                        nonStrikerIndex = null
                    }

                    else -> {
                        // Normal case: striker wicket (this dialog is for striker) -> ONLY clear striker slot
                        // Do NOT promote non-striker to striker; keep non-striker untouched.
                        Toast.makeText(context, "Normal Out flow!", Toast.LENGTH_LONG).show()
                        strikerIndex = null
                        selectingBatsman = 1
                        pickerOtherEndName = curNonStrikerName
                        showBatsmanDialog = true
                        Toast.makeText(context, "End of Normal Out flow", Toast.LENGTH_LONG).show()
                        if (jokerWasBowling && !jokerWasOut) {
                            Toast.makeText(context, "🃏 Joker can now bat!", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                // --- now handle ball/over progression AFTER replacement decision ---
                ballsInOver += 1
                incJokerBallIfBowledThisDelivery()
                addDelivery("W", highlight = true)
                if (ballsInOver == 6) {
                    currentOver += 1
                    ballsInOver = 0
                    recordCurrentBowlerIfAny()
                    previousBowlerName = bowler?.name
                    bowlerIndex = null
                    currentBowlerSpell = 0
                    midOverReplacementDueToJoker.value = false

                    if (showBatsmanDialog) {
                        pendingSwapAfterBatsmanPick = !showSingleSideLayout
                        pendingBowlerDialogAfterBatsmanPick = true
                    } else {
                        if (!showSingleSideLayout) swapStrike()
                        showBowlerDialog = true
                    }
                }

                showWicketDialog = false
                val bowlerName = bowler?.name ?: "Bowler"
                val outName = outSnapshot?.name ?: "Batsman"
                val totalAfter = battingTeamPlayers.sumOf { it.runs } + totalExtras
                Toast.makeText(
                    context,
                    "Wicket! $outName ${wicketType.name.lowercase().replace('_', ' ')}. Total: $totalAfter. $bowlerName to continue.",
                    Toast.LENGTH_LONG
                ).show()
            },
            onDismiss = { showWicketDialog = false },
        )
    }

    if (showRunOutDialog) {
        RunOutDialog(
            striker = striker,
            nonStriker = nonStriker,
            onConfirm = { input ->
                pushSnapshot()

                val runsCompleted = input.runsCompleted
                val outPlayerName = input.whoOut
                val outEnd = input.end

                // Snapshot current refs to avoid race with later index changes
                val curStrikerIndex = strikerIndex
                val curNonStrikerIndex = nonStrikerIndex
                val curStriker = striker
                val curNonStriker = nonStriker
                val curStrikerName = curStriker?.name
                val curNonStrikerName = curNonStriker?.name

                // 1) Update striker and bowler stats exactly from scorer input
                updateStrikerAndTotals { p ->
                    p.copy(
                        runs = p.runs + runsCompleted,
                        ballsFaced = p.ballsFaced + 1,
                        fours = p.fours + if (runsCompleted == 4) 1 else 0,
                        sixes = p.sixes + if (runsCompleted == 6) 1 else 0
                    )
                }
                updateBowlerStats { b ->
                    b.copy(
                        runsConceded = b.runsConceded + runsCompleted,
                        ballsBowled = if (isNoBallRunOut) b.ballsBowled else b.ballsBowled + 1
                    )
                }

                // 2) Find the dismissed player by name
                val outIndex = battingTeamPlayers.indexOfFirst { it.name.equals(outPlayerName, ignoreCase = true) }
                if (outIndex == -1) {
                    Toast.makeText(context, "Could not find player \"$outPlayerName\" in batting team", Toast.LENGTH_LONG).show()
                    showRunOutDialog = false
                    return@RunOutDialog
                }

                // 3) Mark the player out
                val newBatting = battingTeamPlayers.toMutableList()
                newBatting[outIndex] = newBatting[outIndex].copy(isOut = true)
                battingTeamPlayers = newBatting

                // 4) Record completed batter snapshot
                val outSnapshot = battingTeamPlayers[outIndex].copy()
                if (currentInnings == 1) {
                    completedBattersInnings1 = completedBattersInnings1
                        .filterNot { it.name.equals(outSnapshot.name, true) }
                        .toMutableList()
                        .apply { add(outSnapshot) }
                } else {
                    completedBattersInnings2 = completedBattersInnings2
                        .filterNot { it.name.equals(outSnapshot.name, true) }
                        .toMutableList()
                        .apply { add(outSnapshot) }
                }

                // 5) Increment wickets
                totalWickets += 1

                // 6) Defensive / helpful warning if scorer input looks inconsistent
                if ((outIndex == curStrikerIndex && outEnd == RunOutEnd.NON_STRIKER_END) ||
                    (outIndex == curNonStrikerIndex && outEnd == RunOutEnd.STRIKER_END)
                ) {
                    // Inform scorer but continue to honor their input
                    Toast.makeText(
                        context,
                        "Note: $outPlayerName was listed at ${if (outIndex == curStrikerIndex) "striker" else "non-striker"}, " +
                                "but you selected ${if (outEnd == RunOutEnd.STRIKER_END) "Striker's End" else "Non-Striker's End"}. " +
                                "Honouring scorer input.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                // 7) Remove the out player from whichever slot they occupied
                if (strikerIndex == outIndex) strikerIndex = null
                if (nonStrikerIndex == outIndex) nonStrikerIndex = null

                // 7b) If scorer’s "end" does not match the dismissed player’s slot, adjust indexes
                if (outIndex == curNonStrikerIndex && outEnd == RunOutEnd.STRIKER_END) {
                    // Scorer says wicket was at striker end but non-striker is actually out
                    // → shift striker into non-striker slot
                    nonStrikerIndex = curStrikerIndex
                    strikerIndex = null
                } else if (outIndex == curStrikerIndex && outEnd == RunOutEnd.NON_STRIKER_END) {
                    // Scorer says wicket was at non-striker end but striker is actually out
                    // → shift non-striker into striker slot
                    strikerIndex = curNonStrikerIndex
                    nonStrikerIndex = null
                }

                // 8) Replacement goes at the end scorer picked (even if different from whoOut slot)
                if (outEnd == RunOutEnd.STRIKER_END) {
                    selectingBatsman = 1
                    pickerOtherEndName = curNonStrikerName // keep other batter as is
                    showBatsmanDialog = true
                } else {
                    selectingBatsman = 2
                    pickerOtherEndName = curStrikerName
                    showBatsmanDialog = true
                }


                // 9) Ball & over progression (as before)
                if (!isNoBallRunOut) {
                    ballsInOver += 1
                    incJokerBallIfBowledThisDelivery()
                }
                // 10) Delivery log + feedback
                val label = "${runsCompleted} + RO (${outPlayerName} @ ${if (outEnd == RunOutEnd.STRIKER_END) "S" else "NS"})"
                addDelivery(label, highlight = true)
                if (ballsInOver == 6) {
                    currentOver += 1
                    ballsInOver = 0
                    recordCurrentBowlerIfAny()
                    previousBowlerName = bowler?.name
                    bowlerIndex = null
                    currentBowlerSpell = 0
                    midOverReplacementDueToJoker.value = false
                    if (showBatsmanDialog) {
                        pendingSwapAfterBatsmanPick = !showSingleSideLayout
                        pendingBowlerDialogAfterBatsmanPick = true
                    } else {
                        if (!showSingleSideLayout) swapStrike()
                        showBowlerDialog = true
                    }
                }


                Toast.makeText(
                    context,
                    "Run out! $outPlayerName dismissed. $runsCompleted run(s) recorded.",
                    Toast.LENGTH_LONG
                ).show()

                showRunOutDialog = false
                isNoBallRunOut = false
            },
            onDismiss = { showRunOutDialog = false }
        )
    }

}

@Composable
fun ScoreHeaderCard(
    battingTeamName: String,
    currentInnings: Int,
    matchSettings: MatchSettings,
    calculatedTotalRuns: Int,
    totalWickets: Int,
    currentOver: Int,
    ballsInOver: Int,
    totalExtras: Int,
    battingTeamPlayers: List<Player>,
    firstInningsRuns: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "$battingTeamName - Innings $currentInnings (${matchSettings.totalOvers} overs)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.surface,
            )
            Text(
                text = "$calculatedTotalRuns/$totalWickets",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.surface,
            )
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Overs: $currentOver.$ballsInOver/${matchSettings.totalOvers}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.surface,
                )
                val runRate = if (currentOver == 0 && ballsInOver == 0) 0.0
                else calculatedTotalRuns.toDouble() / ((currentOver * 6 + ballsInOver) / 6.0)
                Text(
                    text = "RR: ${"%.2f".format(runRate)}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.surface,
                )
                if (totalExtras > 0) {
                    Text(
                        text = "Extras: $totalExtras",
                        fontSize = 14.sp,
                        color = Color.Yellow,
                    )
                }
            }
            if (totalExtras > 0) {
                val playerRuns = battingTeamPlayers.sumOf { it.runs }
                Text(
                    text = "($playerRuns runs + $totalExtras extras)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    fontStyle = FontStyle.Italic
                )
            }
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
}

@Composable
fun PlayersCard(
    showSingleSideLayout: Boolean,
    striker: Player?,
    nonStriker: Player?,
    bowler: Player?,
    availableBatsmen: Int,
    onSelectStriker: () -> Unit,
    onSelectNonStriker: () -> Unit,
    onSelectBowler: () -> Unit,
    onSwapStrike: () -> Unit,
    onShowLiveScorecard: () -> Unit,
    onBackPressed: () -> Unit,
    currentBowlerSpell: Int,
    jokerPlayer: Player?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row {
                    IconButton(onClick = onShowLiveScorecard) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = "Live Scorecard",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Home",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            BattingSection(
                showSingleSideLayout = showSingleSideLayout,
                striker = striker,
                nonStriker = nonStriker,
                onSelectStriker = onSelectStriker,
                onSelectNonStriker = onSelectNonStriker
            )

            if (showSingleSideLayout && striker != null) {
                SingleSideBattingStatus(striker.name)
            }

            if (striker != null && nonStriker != null && !showSingleSideLayout && availableBatsmen > 1) {
                SwapStrikeButton(onSwap = onSwapStrike)
            }

            Spacer(modifier = Modifier.height(12.dp))

            BowlerSection(
                bowler = bowler,
                currentBowlerSpell = currentBowlerSpell,
                onSelectBowler = onSelectBowler
            )

            jokerPlayer?.let { joker ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "🃏 Joker Available: ${joker.name}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun BattingSection(
    showSingleSideLayout: Boolean,
    striker: Player?,
    nonStriker: Player?,
    onSelectStriker: () -> Unit,
    onSelectNonStriker: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (showSingleSideLayout) Arrangement.Center else Arrangement.SpaceBetween,
    ) {
        // Striker Column (Always shown)
        BatsmanColumn(
            player = striker,
            onClick = onSelectStriker,
            center = showSingleSideLayout,
            isStriker = true,
            isLastBatsman = (showSingleSideLayout && striker != null),
            modifier = if (showSingleSideLayout) Modifier else Modifier.weight(1f)
        )

        // Non-Striker Column - Show when NOT in single-side layout
        if (!showSingleSideLayout) {
            BatsmanColumn(
                player = nonStriker,
                onClick = onSelectNonStriker,
                center = false,
                isStriker = false,
                isLastBatsman = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun BatsmanColumn(
    player: Player?,
    onClick: () -> Unit,
    center: Boolean = false,
    isStriker: Boolean,
    isLastBatsman: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = if (center) Alignment.CenterHorizontally else if (isStriker) Alignment.Start else Alignment.End
    ) {
        Text(
            text = "🏏 ${player?.name ?: if (isStriker) "Select Striker" else "Select Non-Striker"}",
            fontWeight = if (isStriker) FontWeight.Bold else FontWeight.Normal,
            color = if (player == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        player?.let {
            Text(
                text = "${it.runs}${if (!it.isOut && it.ballsFaced > 0) "*" else ""} (${it.ballsFaced}) - 4s: ${it.fours}, 6s: ${it.sixes}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "SR: ${"%.1f".format(it.strikeRate)}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
        if (isLastBatsman) {
            Text(
                text = "⚡ Last Batsman",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic
            )
        }
    }
}

@Composable
fun SingleSideBattingStatus(strikerName: String) {
    Spacer(modifier = Modifier.height(8.dp))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Text(
            text = "⚡ Single Side Batting: $strikerName continues alone",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
fun SwapStrikeButton(onSwap: () -> Unit) {
    Spacer(modifier = Modifier.height(8.dp))
    Button(
        onClick = onSwap,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
    ) {
        Spacer(modifier = Modifier.width(4.dp))
        Text("Swap Strike", fontSize = 12.sp)
    }
}

@Composable
fun BowlerSection(
    bowler: Player?,
    currentBowlerSpell: Int,
    onSelectBowler: () -> Unit
) {
    Column(modifier = Modifier.clickable { onSelectBowler() }) {
        Text(
            text = "⚾ Bowler: ${bowler?.name ?: "Select Bowler"}",
            fontWeight = FontWeight.Medium,
            color = if (bowler == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        bowler?.let { currentBowler ->
            Text(
                text = "${"%.1f".format(currentBowler.oversBowled)} overs, ${currentBowler.runsConceded} runs, ${currentBowler.wickets} wickets",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Economy: ${"%.1f".format(currentBowler.economy)} | Spell: $currentBowlerSpell over${if (currentBowlerSpell != 1) "s" else ""}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun ScoringButtons(
    striker: Player?,
    nonStriker: Player?,
    bowler: Player?,
    isInningsComplete: Boolean,
    matchSettings: MatchSettings,
    availableBatsmen: Int,
    calculatedTotalRuns: Int,
    onScoreRuns: (Int) -> Unit,
    onShowExtras: () -> Unit,
    onShowWicket: () -> Unit,
    onUndo: () -> Unit
) {
    val canStartScoring = striker != null && bowler != null &&
            (nonStriker != null || (matchSettings.allowSingleSideBatting && availableBatsmen == 1))

    when {
        canStartScoring && !isInningsComplete -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Runs",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))

                if (matchSettings.shortPitch) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (0..4).forEach { RunButton(it, onScoreRuns) }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (0..6).forEach { RunButton(it, onScoreRuns) }
                    }
                }
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionTonalButton(
                        label = "Extras",
                        modifier = Modifier.weight(1f),
                        onClick = onShowExtras
                    )
                    // Prominent Wicket
                    Button(
                        onClick = onShowWicket,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text("Wicket", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    ActionTonalButton(
                        label = "Undo",
                        modifier = Modifier.weight(1f),
                        onClick = onUndo
                    )
                }
            }
        }

        isInningsComplete -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                ),
            ) {
                Text(
                    text = "Innings Complete! Total: $calculatedTotalRuns runs",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }

        else -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "⚠️ Please select players to start scoring",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center,
                    )
                    if (matchSettings.allowSingleSideBatting) {
                        Text(
                            text = "Single side batting enabled - only one batsman required",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RunButton(
    value: Int,
    onClick: (Int) -> Unit,
) {
    // Square, compact, clearly a button (not a pill/chip)
    Button(
        onClick = { onClick(value) },
        modifier = Modifier
            .size(40.dp)
            .fillMaxWidth(), // square; adjust to taste (52–64.dp)
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.filledTonalButtonColors()
    ) {
        Text(
            text = value.toString(),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ActionTonalButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.filledTonalButtonColors()
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private data class Agg(
    val id: String?,
    val name: String,
    val team: String,
    var runs: Int = 0,
    var balls: Int = 0,
    var fours: Int = 0,
    var sixes: Int = 0,
    var notOut: Boolean = false,
    var wkts: Int = 0,
    var rcv: Int = 0,
    var ballsBowled: Int = 0,
    var isJoker: Boolean = false
)

private fun summarizeAgg(a: Agg): String {
    val bat = if (a.balls > 0) "${a.runs}${if (a.notOut) "*" else ""}(${a.balls})" else ""
    val bowl = if (a.ballsBowled > 0) "${a.wkts}/${a.rcv}" else ""
    return listOf(bat, bowl).filter { it.isNotBlank() }.joinToString(" and ")
}

private fun computeMatchEconomy(
    firstBat: List<PlayerMatchStats>,
    secondBat: List<PlayerMatchStats>,
    totalOvers: Int
): Double {
    val totalRuns = firstBat.sumOf { it.runs } + secondBat.sumOf { it.runs }
    val oversApprox = totalOvers * 2.0
    return if (oversApprox > 0) totalRuns / oversApprox else 0.0
}

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
    groupId: String,
    groupName: String
) {
    android.util.Log.d("SaveMatch", "Attempting to save match: $team1Name vs $team2Name")
    // Simple leaders for display
    val allBattingStats = firstInningsBattingStats + secondInningsBattingStats
    val allBowlingStats = firstInningsBowlingStats + secondInningsBowlingStats
    val topBatsman = allBattingStats.maxByOrNull { it.runs }
    val topBowler = allBowlingStats
        .filter { it.oversBowled > 0 }
        .maxWithOrNull { a, b ->
            when {
                a.wickets != b.wickets -> a.wickets.compareTo(b.wickets)
                else -> {
                    val economyA = a.runsConceded.toDouble() / a.oversBowled
                    val economyB = b.runsConceded.toDouble() / b.oversBowled
                    economyB.compareTo(economyA)
                }
            }
        }

    val wasChaseWin = winnerTeam == team2Name
    val matchEconomy = computeMatchEconomy(firstInningsBattingStats, secondInningsBattingStats, matchSettings.totalOvers)

// SINGLE aggregation for impacts
    val aggMap: LinkedHashMap<String, Agg> = linkedMapOf()

    fun keyOf(p: PlayerMatchStats) = "${p.team}::${p.name.trim().lowercase()}"

    fun mergeInto(map: LinkedHashMap<String, Agg>, incoming: Agg) {
        val k = "${incoming.team}::${incoming.name.trim().lowercase()}"
        val a = map[k]
        if (a == null) {
            map[k] = incoming
        } else {
            a.runs += incoming.runs
            a.balls += incoming.balls
            a.fours += incoming.fours
            a.sixes += incoming.sixes
            a.notOut = a.notOut || incoming.notOut
            a.wkts += incoming.wkts
            a.rcv += incoming.rcv
            a.ballsBowled += incoming.ballsBowled
            a.isJoker = a.isJoker || incoming.isJoker
        }
    }

    fun addBatting(ps: List<PlayerMatchStats>) {
        ps.forEach { p ->
            mergeInto(
                aggMap,
                Agg(
                    id = p.id,
                    name = p.name,
                    team = p.team,
                    runs = p.runs,
                    balls = p.ballsFaced,
                    fours = p.fours,
                    sixes = p.sixes,
                    notOut = (!p.isOut && p.ballsFaced > 0),
                    isJoker = p.isJoker
                )
            )
        }
    }

    fun addBowling(ps: List<PlayerMatchStats>) {
        ps.forEach { p ->
            mergeInto(
                aggMap,
                Agg(
                    id = p.id,
                    name = p.name,
                    team = p.team,
                    wkts = p.wickets,
                    rcv = p.runsConceded,
                    ballsBowled = (p.oversBowled * 6).toInt(),
                    isJoker = p.isJoker
                )
            )
        }
    }

    addBatting(firstInningsBattingStats)
    addBatting(secondInningsBattingStats)
    addBowling(firstInningsBowlingStats)
    addBowling(secondInningsBowlingStats)

    // Single scoring function closes over matchEconomy/wasChaseWin/winnerTeam
    fun scoreAgg(a: Agg): Double {
        val overs = matchSettings.totalOvers.toDouble()

        // dynamic weights
        val runsWeight   = 20.0 / overs
        val fourBonus    = 1.0 * (20.0 / overs)
        val sixBonus     = 1.5 * (20.0 / overs)
        val notOutBonus  = 2.0 * (20.0 / overs)
        val wicketWeight = 50.0 / overs

        // batting
        val sr = if (a.balls > 0) a.runs * 100.0 / a.balls else 0.0
        var bat = a.runs * runsWeight +
                a.fours * fourBonus +
                a.sixes * sixBonus +
                (if (a.notOut) notOutBonus else 0.0)

        val srBonus = if (a.balls >= 10)
            kotlin.math.max(0.0, kotlin.math.min(10.0, (sr - 100.0) / 5.0))
        else 0.0
        val chaseBonus = if (wasChaseWin) kotlin.math.min(15.0, a.runs / 5.0) else 0.0
        bat += srBonus + chaseBonus

        // bowling
        val eco = if (a.ballsBowled > 0) a.rcv * 6.0 / a.ballsBowled else 0.0
        val bowlBase = a.wkts * wicketWeight -
                0.1 * a.rcv -
                2.0 * kotlin.math.max(0.0, eco - matchEconomy)
        val fiveW = if (a.wkts >= 5) 20.0 else if (a.wkts >= 4) 10.0 else 0.0
        var bowl = if (a.ballsBowled > 0) bowlBase + fiveW else 0.0

        // joker discount on scoring but still record stats
        if (a.isJoker && a.ballsBowled > 0 && a.balls > 0) { bat *= 0.8; bowl *= 0.8 }

        val base = when {
            a.balls > 0 && a.ballsBowled > 0 -> 0.6 * bat + 0.5 * bowl
            a.balls > 0 -> bat
            else -> bowl
        }

        return if (a.team == winnerTeam) base * 1.10 else base
    }

    // Build impacts once
    val impactsUnsorted: List<PlayerImpact> = aggMap.values.map { a ->
        val impact = scoreAgg(a)
        PlayerImpact(
            id = a.id, name = a.name, team = a.team,
            impact = "%.1f".format(impact).toDouble(),
            summary = summarizeAgg(a),
            isJoker = a.isJoker,
            runs = a.runs, balls = a.balls, fours = a.fours, sixes = a.sixes,
            wickets = a.wkts, runsConceded = a.rcv, oversBowled = a.ballsBowled / 6.0
        )
    }

    val playerImpactsListWithoutJoker: List<PlayerImpact> =
        impactsUnsorted
            .filterNot { it.isJoker }
            .sortedByDescending { it.impact }

    val playerImpactsListWithJoker: List<PlayerImpact> = impactsUnsorted.sortedByDescending { it.impact }

    // POTM = top of impact list
    val potm = playerImpactsListWithoutJoker.firstOrNull()

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
        matchSettings = matchSettings,
        groupId = groupId,
        groupName = groupName,
        shortPitch = matchSettings.shortPitch,
        // POTM from impacts
        playerOfTheMatchId = potm?.id,
        playerOfTheMatchName = potm?.name,
        playerOfTheMatchTeam = potm?.team,
        playerOfTheMatchImpact = potm?.impact,
        playerOfTheMatchSummary = potm?.summary,
        playerImpacts = playerImpactsListWithJoker
    )

    val storageManager = MatchStorageManager(context)
    storageManager.saveMatch(matchHistory)

    val allMatches = storageManager.getAllMatches()
    android.util.Log.d("SaveMatch", "Match saved with detailed stats! Total matches now: ${allMatches.size}")

    Toast.makeText(
        context,
        "Match with detailed stats saved! Total: ${allMatches.size} matches 🏏📊",
        android.widget.Toast.LENGTH_LONG,
    ).show()
}

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
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "$battingTeamName - Innings $currentInnings",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.surface,
                            )
                            Text(
                                text = "$currentRuns/$currentWickets ($currentOvers.$currentBalls/$totalOvers overs)",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.surface,
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

                if (currentInnings == 2) {
                    item {
                        Text(
                            text = "First Innings Summary",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    item {
                        Card(colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )) {
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

                                    // FIXED: Better logic for top bowler - wickets first, then economy
                                    val topBowl = firstInningsBowlingPlayers
                                        .filter { it.ballsBowled > 0 }
                                        .maxWithOrNull(
                                            compareBy<Player> { it.wickets } // 1st: Most wickets
                                                .thenBy { -(it.runsConceded.toDouble() / (it.ballsBowled / 6.0)) } // 2nd: Best economy
                                                .thenByDescending { it.ballsBowled } // 3rd: Most overs bowled (if needed)
                                        )

                                    topBat?.let {
                                        Text("🏏 ${it.name}: ${it.runs} runs", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    topBowl?.let {
                                        if (it.wickets > 0) {
                                            Text("⚾ ${it.name}: ${it.wickets} wickets", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        } else {
                                            // Show economy when no wickets taken
                                            val economy = if (it.ballsBowled > 0) it.runsConceded.toDouble() / (it.ballsBowled / 6.0) else 0.0
                                            Text("⚾ ${it.name}: Best economy ${"%.1f".format(economy)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }

                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Current Innings - Batting",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }

                val yetToBat = battingTeamPlayers.filter { it.ballsFaced == 0 && it.runs == 0 }
                if (yetToBat.isNotEmpty()) {
                    item {
                        Text(
                            text = "Yet to bat: ${yetToBat.joinToString(", ") { it.name }}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }

                val didNotBowl = bowlingTeamPlayers.filter { it.ballsBowled == 0 && it.wickets == 0 && it.runsConceded == 0 }
                if (didNotBowl.isNotEmpty()) {
                    item {
                        Text(
                            text = "Yet to bowl: ${didNotBowl.joinToString(", ") { it.name }}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                if (jokerPlayerName.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                            val jokerInBatting = battingTeamPlayers.find { it.name == jokerPlayerName && it.isJoker }
                            val jokerInBowling = bowlingTeamPlayers.find { it.name == jokerPlayerName && it.isJoker }
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "🃏 Joker: $jokerPlayerName",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                                when {
                                    jokerInBatting != null -> {
                                        Text(
                                            text = "Currently batting: ${jokerInBatting.runs} runs (${jokerInBatting.ballsFaced} balls)",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                    }
                                    jokerInBowling != null -> {
                                        Text(
                                            text = "Currently bowling: ${jokerInBowling.wickets}/${jokerInBowling.runsConceded} (${"%.1f".format(jokerInBowling.oversBowled)} overs)",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = "Available for both teams",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.secondary,
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
            color = if (player.isJoker) MaterialTheme.colorScheme.secondary else Color.Black,
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun EnhancedInningsBreakDialog(
    runs: Int, wickets: Int, overs: Int, balls: Int,
    battingTeam: String,
    battingPlayers: List<Player>, bowlingPlayers: List<Player>,
    totalOvers: Int,
    onStartSecondInnings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { },
        confirmButton = {
            Button(onClick = onStartSecondInnings) { Text("Start 2nd Innings") }
        },
        title = {
            Text("🏏 First Innings Complete", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
// Summary banner
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(battingTeam, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                            Text("$runs/$wickets ($overs.$balls/$totalOvers ov)", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
// Batting top 3
                item { Text("Top Batting", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary) }
                items(battingPlayers.sortedByDescending { it.runs }.take(3)) { p ->
                    StatRowCompact(name = p.name, left = "${p.runs}${if (!p.isOut && p.ballsFaced > 0) "*" else ""} (${p.ballsFaced})", right = "4s:${p.fours} 6s:${p.sixes}")
                }
// Bowling top 3
                item { Text("Top Bowling", style = MaterialTheme.typography.titleSmall, color = Color(0xFFFF5722)) }
                items(bowlingPlayers.sortedByDescending { it.wickets }.take(3)) { p ->
                    StatRowCompact(name = p.name, left = "${p.wickets}/${p.runsConceded}", right = "${"%.1f".format(p.oversBowled)} ov - Eco ${"%.1f".format(p.economy)}")
                }
            }
        }
    )
}

@Composable
private fun StatRowCompact(name: String, left: String, right: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(left, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(right, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExtrasDialog(
    matchSettings: MatchSettings,
    onExtraSelected: (ExtraType, Int) -> Unit,
    onDismiss: () -> Unit,
    striker: Player?,
    nonStriker: Player?
) {
    var selectedExtraType by remember { mutableStateOf<ExtraType?>(null) }

    // No-ball subflow state
    var showNoBallOutcomeStep by remember { mutableStateOf(false) }
    var showRunOutOnNoBall by remember { mutableStateOf(false) }
    var showBoundaryOutOnNoBall by remember { mutableStateOf(false) }

    fun resetNoBallHolders() {
        NoBallOutcomeHolders.noBallSubOutcome.value = NoBallSubOutcome.NONE
        NoBallOutcomeHolders.noBallRunOutInput.value = null
        NoBallOutcomeHolders.noBallBoundaryOutInput.value = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Extras", style = MaterialTheme.typography.titleLarge) },
        text = {
            when {
                // STEP 1 — Type
                selectedExtraType == null -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Select extra type", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ExtraType.values().forEach { extraType ->
                            val baseRuns = when (extraType) {
                                ExtraType.OFF_SIDE_WIDE -> matchSettings.offSideWideRuns
                                ExtraType.LEG_SIDE_WIDE  -> matchSettings.legSideWideRuns
                                ExtraType.NO_BALL        -> matchSettings.noballRuns
                                ExtraType.BYE            -> matchSettings.byeRuns
                                ExtraType.LEG_BYE        -> matchSettings.legByeRuns
                            }
                            // Emphasis: filled for most common, tonal for others
                            val isPrimary = extraType == ExtraType.NO_BALL || extraType == ExtraType.OFF_SIDE_WIDE || extraType == ExtraType.LEG_SIDE_WIDE
                            val buttonColors = if (isPrimary) ButtonDefaults.buttonColors()
                            else ButtonDefaults.filledTonalButtonColors()
                            Button(
                                onClick = {
                                    selectedExtraType = extraType
                                    showNoBallOutcomeStep = (extraType == ExtraType.NO_BALL)
                                    if (!showNoBallOutcomeStep) resetNoBallHolders()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = buttonColors,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("${extraType.displayName}  (+$baseRuns base)")
                            }
                        }
                    }
                }

                // STEP 2 — No-ball outcome
                selectedExtraType == ExtraType.NO_BALL && showNoBallOutcomeStep -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("No-ball outcome", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        // None
                        FilledTonalButton(
                            onClick = {
                                NoBallOutcomeHolders.noBallSubOutcome.value = NoBallSubOutcome.NONE
                                showNoBallOutcomeStep = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("None (just No ball + runs)") }

                        // Run out
                        Button(
                            onClick = {
                                NoBallOutcomeHolders.noBallSubOutcome.value = NoBallSubOutcome.RUN_OUT
                                showRunOutOnNoBall = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Run out on No ball") }

                        // Boundary out
                        FilledTonalButton(
                            onClick = {
                                NoBallOutcomeHolders.noBallSubOutcome.value = NoBallSubOutcome.BOUNDARY_OUT
                                NoBallOutcomeHolders.noBallBoundaryOutInput.value = NoBallBoundaryOutInput(outBatterName = null)
                                // Immediately finalize: Boundary Out has no extra runs UI
                                val base = matchSettings.noballRuns
                                onExtraSelected(ExtraType.NO_BALL, base)   // <-- fire the scoring callback now
                                showBoundaryOutOnNoBall = false
                                showNoBallOutcomeStep = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) { Text("Boundary out on No ball") }

                        if (showRunOutOnNoBall) {
                            RunOutDialog(
                                striker = striker,
                                nonStriker = nonStriker,
                                onConfirm = { input ->
                                    NoBallOutcomeHolders.noBallRunOutInput.value = input
                                    showRunOutOnNoBall = false
                                    showNoBallOutcomeStep = false
                                },
                                onDismiss = { showRunOutOnNoBall = false }
                            )
                        }
                    }
                }
                // STEP 3 — Runs
                else -> {
                    val baseRuns = when (selectedExtraType!!) {
                        ExtraType.OFF_SIDE_WIDE -> matchSettings.offSideWideRuns
                        ExtraType.LEG_SIDE_WIDE -> matchSettings.legSideWideRuns
                        ExtraType.NO_BALL       -> matchSettings.noballRuns
                        ExtraType.BYE           -> matchSettings.byeRuns
                        ExtraType.LEG_BYE       -> matchSettings.legByeRuns
                    }
                    val isBoundaryOut =
                        selectedExtraType == ExtraType.NO_BALL &&
                                NoBallOutcomeHolders.noBallSubOutcome.value == NoBallSubOutcome.BOUNDARY_OUT

                    if (isBoundaryOut) {
                        // Do nothing here; selection will be finalized in onExtraSelected handler that already logs Nb+base
                        Text("No ball + Boundary out recorded", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        return@AlertDialog
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${selectedExtraType!!.displayName} · pick additional runs",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Horizontal wrap of compact buttons per M3
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = 5
                        ) {
                            // Before building buttons:
                            val additionalOptions =
                                if (selectedExtraType == ExtraType.NO_BALL) (0..6).toList()
                                else (0..4).toList() // cap at 4 for Wd, B, Lb

                            // Then use additionalOptions instead of (0..6)
                            additionalOptions.forEach { add ->
                                val total = baseRuns + add
                                val colors =
                                    when (add) {
                                        0 -> ButtonDefaults.filledTonalButtonColors()
                                        4 -> ButtonDefaults.buttonColors()
                                        6 -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                        else -> ButtonDefaults.outlinedButtonColors()
                                    }
                                val shape = RoundedCornerShape(12.dp)
                                val btn: @Composable (@Composable () -> Unit) -> Unit =
                                    if (add in listOf(1,2,3,5)) {
                                        { content -> OutlinedButton(
                                            onClick = { onExtraSelected(selectedExtraType!!, total) },
                                            shape = shape) { content() } }
                                    } else {
                                        { content -> Button(
                                            onClick = { onExtraSelected(selectedExtraType!!, total) },
                                            colors = colors,
                                            shape = shape) { content() } }
                                    }
                                btn { Text("+$add = $total") }
                            }
                        }
                    }
                }
            }
        },
        // Buttons per M3: primary action right, secondary left
        confirmButton = {
            // Primary: Continue or Apply depending on step
            val label = when {
                selectedExtraType == null -> "Close"
                selectedExtraType == ExtraType.NO_BALL && showNoBallOutcomeStep -> "Back"
                else -> "Back"
            }
            TextButton(onClick = {
                when {
                    selectedExtraType == null -> onDismiss()
                    selectedExtraType == ExtraType.NO_BALL && showNoBallOutcomeStep -> {
                        showNoBallOutcomeStep = false
                        resetNoBallHolders()
                        selectedExtraType = null
                    }
                    else -> {
                        // Back from runs to types
                        resetNoBallHolders()
                        selectedExtraType = null
                    }
                }
            }) { Text(label) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onWicketSelected(wicketType) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        ),
                    ) {
                        Text(
                            text = wicketType.name.lowercase().replace("_", " ").uppercase(),
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
    matchSettings: MatchSettings,
    groupId: String?,
    groupName: String?
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
    val margin: String? = when {
        isTie -> null
        chasingWon -> "${calculateWicketMargin(secondInningsWickets)} wickets"
        else -> "${firstInningsRuns - secondInningsRuns} runs"
    }

    val firstInningsBattingStats = remember(firstInningsBattingPlayers, team1Name) {
        firstInningsBattingPlayers.map { it.toMatchStats(team1Name) }
    }
    val firstInningsBowlingStats = remember(firstInningsBowlingPlayers, team2Name) {
        firstInningsBowlingPlayers.map { it.toMatchStats(team2Name) }
    }
    val secondInningsBattingStats = remember(secondInningsBattingPlayers, team2Name) {
        secondInningsBattingPlayers.map { it.toMatchStats(team2Name) }
    }
    val secondInningsBowlingStats = remember(secondInningsBowlingPlayers, team1Name) {
        secondInningsBowlingPlayers.map { it.toMatchStats(team1Name) }
    }

    val finalGroupId = groupId ?: "1"
    val finalGroupName = groupName ?: "Default"
    // Save history (tie-friendly placeholders)
    LaunchedEffect(Unit) {
        saveMatchToHistory(
            team1Name = team1Name,
            team2Name = team2Name,
            jokerPlayerName = jokerPlayerName,
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
            context = context,
            matchSettings = matchSettings,
            groupId = finalGroupId,
            groupName = finalGroupName
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                    PlayerStatCard(player, "batting")
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
                    PlayerStatCard(player, "batting")
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
            TextButton(
                onClick = {
                    val storageManager = MatchStorageManager(context)
                    val latestMatchId = storageManager.getLatestMatchID()
                    val intent = Intent(context, FullScorecardActivity::class.java)
                    intent.putExtra("match_id", latestMatchId)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    context.startActivity(intent)
                    (context as ComponentActivity).finish()
                }
            ) {
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
                Text(
                    text = "${player.runs}${if (!player.isOut && player.ballsFaced > 0) "*" else ""} (${player.ballsFaced}) - 4s:${player.fours} 6s:${player.sixes}",
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

fun calculateWicketMargin(wicketsLost: Int): Int {
    return 10 - wicketsLost
}

@Composable
fun EnhancedPlayerSelectionDialog(
    title: String,
    players: List<Player>,
    jokerPlayer: Player? = null,
    currentStrikerIndex: Int? = null,
    currentNonStrikerIndex: Int? = null,
    allowSingleSide: Boolean = false,
    totalWickets: Int = 0, // Add this parameter
    battingTeamPlayers: List<Player> = emptyList(), // Add this parameter
    bowlingTeamPlayers: List<Player> = emptyList(), // Add this parameter
    jokerOversThisInnings: Double = 0.0,
    jokerOutInCurrentInnings: Boolean = false,
    onPlayerSelected: (Player) -> Unit,
    onDismiss: () -> Unit,
    matchSettings: MatchSettings,
    otherEndName: String? = null,
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
                val availablePlayers = players.filterIndexed { index, player ->
                    if (player.isOut) return@filterIndexed false
                    val pickingStriker = title.contains("Striker", ignoreCase = true) && !title.contains("Non", ignoreCase = true)
                    val pickingNonStriker = title.contains("Non-Striker", ignoreCase = true)

                    fun sameName(a: String?, b: String?) =
                        a != null && b != null && a.trim().equals(b.trim(), ignoreCase = true)

                    if (pickingStriker) {
                        val excludeByIndex = (index == currentNonStrikerIndex)
                        val excludeByName = sameName(player.name, otherEndName)
                        !(excludeByIndex || excludeByName)
                    } else if (pickingNonStriker) {
                        val excludeByIndex = (index == currentStrikerIndex)
                        val excludeByName = sameName(player.name, otherEndName)
                        !(excludeByIndex || excludeByName)
                    } else {
                        true
                    }
                }


                items(availablePlayers) { player ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayerSelected(player) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (player.isJoker)
                                MaterialTheme.colorScheme.secondaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (player.isJoker) "🃏 ${player.name}" else player.name,
                                fontWeight = FontWeight.Medium,
                                color = if (player.isJoker)
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (title.contains("Striker", ignoreCase = true)) {
                                if (player.ballsFaced > 0 || player.runs > 0) {
                                    Text(
                                        text = "${player.runs}${if (!player.isOut && player.ballsFaced > 0) "*" else ""} (${player.ballsFaced}) - SR: ${"%.1f".format(player.strikeRate)}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (player.fours > 0 || player.sixes > 0) {
                                        Text(
                                            text = "4s: ${player.fours}, 6s: ${player.sixes}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Text(
                                        text = if (player.isJoker) "JOKER - Available for both teams" else "Yet to bat",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontStyle = FontStyle.Italic,
                                    )
                                }
                            }
                            if (title.contains("Bowler", ignoreCase = true)) {
                                if (player.ballsBowled > 0 || player.wickets > 0 || player.runsConceded > 0) {
                                    Text(
                                        text = "${player.wickets}/${player.runsConceded} (${"%.1f".format(player.oversBowled)} ov) - Eco: ${"%.1f".format(player.economy)}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = if (player.isJoker) "JOKER - Available for both teams" else "Yet to bowl",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontStyle = FontStyle.Italic,
                                    )
                                }
                            }
                        }
                    }
                }

                // FIXED: Proper joker availability logic based on rules
                jokerPlayer?.let { joker ->
                    val showJoker = when {
                        title.contains("Striker", ignoreCase = true) -> {
                            val notInBattingTeam = !battingTeamPlayers.any { it.isJoker }
                            val wicketsFallen = totalWickets > 0
                            notInBattingTeam && !jokerOutInCurrentInnings && wicketsFallen
                        }
                        title.contains("Bowler", ignoreCase = true) -> {
                            val notInBowlingTeam = !bowlingTeamPlayers.any { it.isJoker }
                            val notBattingNow = !battingTeamPlayers.any { it.isJoker }
                            val withinCap = jokerOversThisInnings < matchSettings.jokerMaxOvers
                            notInBowlingTeam && notBattingNow && withinCap
                        }
                        else -> false
                    }

                    if (showJoker) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlayerSelected(joker) },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                ),
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "🃏 ${joker.name}",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = if (title.contains("Striker", ignoreCase = true)) {
                                            "JOKER - Available to bat"
                                        } else {
                                            "JOKER - Available to bowl"
                                        },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                // Show empty state only if no regular players and no joker available
                if (availablePlayers.isEmpty() &&
                    (jokerPlayer == null ||
                            (title.contains("Striker", ignoreCase = true) &&
                                    (!jokerPlayer.let { joker ->
                                        val notInBattingTeam = !battingTeamPlayers.any { it.isJoker }
                                        val notOut = !joker.isOut
                                        val wicketsFallen = totalWickets > 0
                                        val notOpeningPair = !(totalWickets == 0 && title.contains("First", ignoreCase = true))
                                        notInBattingTeam && notOut && (wicketsFallen || notOpeningPair)
                                    })))) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        ) {
                            Text(
                                text = if (title.contains("Striker", ignoreCase = true)) {
                                    if (allowSingleSide) "All batsmen are out" else "No available batsmen"
                                } else {
                                    "No available bowlers"
                                },
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
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

@Composable
fun RunOutDialog(
    striker: Player?,
    nonStriker: Player?,
    onConfirm: (RunOutInput) -> Unit,
    onDismiss: () -> Unit
) {
    var runsCompleted by remember { mutableStateOf(0) }
    var selectedWho by remember { mutableStateOf<Player?>(null) }
    var selectedEnd by remember { mutableStateOf<RunOutEnd?>(null) }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("Run Out") },
        text = {
            Column {
                // Runs Completed (0–3)
                Text("Runs completed before wicket:")
                Row {
                    (0..3).forEach { run ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            RadioButton(
                                selected = runsCompleted == run,
                                onClick = { runsCompleted = run }
                            )
                            Text("$run")
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Who got out (actual player names)
                Text("Who got out?")
                Column {
                    striker?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedWho == striker,
                                onClick = { selectedWho = striker }
                            )
                            Text(it.name)
                        }
                    }
                    nonStriker?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedWho == nonStriker,
                                onClick = { selectedWho = nonStriker }
                            )
                            Text(it.name)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // End of wicket
                Text("At which end?")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedEnd == RunOutEnd.STRIKER_END,
                        onClick = { selectedEnd = RunOutEnd.STRIKER_END }
                    )
                    Text("Striker’s End", Modifier.padding(end = 16.dp))

                    RadioButton(
                        selected = selectedEnd == RunOutEnd.NON_STRIKER_END,
                        onClick = { selectedEnd = RunOutEnd.NON_STRIKER_END }
                    )
                    Text("Non-Striker’s End")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val input = RunOutInput(
                        runsCompleted = runsCompleted,
                        whoOut = selectedWho?.name ?: "",
                        end = selectedEnd ?: RunOutEnd.STRIKER_END
                    )
                    onConfirm(input)
                },
                enabled = selectedWho != null && selectedEnd != null
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("Cancel")
            }
        }
    )
}

