package com.oreki.stumpd

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

class ScoringViewModel(
    private val app: Application,
    private val storage: MatchStorageManager = MatchStorageManager(app)
) : ViewModel() {

    // Inputs/settings
    var matchSettings by mutableStateOf(MatchSettings())
        private set
    var team1Name by mutableStateOf("Team A")
    var team2Name by mutableStateOf("Team B")
    var jokerName by mutableStateOf("")
    var jokerPlayer: Player? = null

    // Core match state
    var currentInnings by mutableStateOf(1)
    var battingTeamPlayers by mutableStateOf(mutableListOf<Player>())
    var bowlingTeamPlayers by mutableStateOf(mutableListOf<Player>())

    var strikerIndex by mutableStateOf<Int?>(null)
    var nonStrikerIndex by mutableStateOf<Int?>(null)
    var bowlerIndex by mutableStateOf<Int?>(null)

    var totalWickets by mutableStateOf(0)
    var currentOver by mutableStateOf(0)
    var ballsInOver by mutableStateOf(0)
    var totalExtras by mutableStateOf(0)

    // Joker
    var jokerBallsBowledInnings1 by mutableStateOf(0)
    var jokerBallsBowledInnings2 by mutableStateOf(0)
    var jokerOutInCurrentInnings by mutableStateOf(false)
    var midOverReplacementDueToJoker by mutableStateOf(false)

    // Ledgers
    var completedBattersInnings1 by mutableStateOf(mutableListOf<Player>())
    var completedBattersInnings2 by mutableStateOf(mutableListOf<Player>())
    var completedBowlersInnings1 by mutableStateOf(mutableListOf<Player>())
    var completedBowlersInnings2 by mutableStateOf(mutableListOf<Player>())

    // First innings snapshot
    var firstInningsRuns by mutableStateOf(0)
    var firstInningsWickets by mutableStateOf(0)
    var firstInningsOvers by mutableStateOf(0)
    var firstInningsBalls by mutableStateOf(0)
    var firstInningsBattingPlayersList by mutableStateOf(listOf<Player>())
    var firstInningsBowlingPlayersList by mutableStateOf(listOf<Player>())

    // Second innings summaries
    var secondInningsBattingPlayers by mutableStateOf(listOf<Player>())
    var secondInningsBowlingPlayers by mutableStateOf(listOf<Player>())

    // UI flags
    var showBatsmanDialog by mutableStateOf(false)
    var showBowlerDialog by mutableStateOf(false)
    var showWicketDialog by mutableStateOf(false)
    var showExtrasDialog by mutableStateOf(false)
    var showInningsBreakDialog by mutableStateOf(false)
    var showMatchCompleteDialog by mutableStateOf(false)
    var showExitDialog by mutableStateOf(false)
    var showLiveScorecardDialog by mutableStateOf(false)
    var selectingBatsman by mutableStateOf(1)
    var previousBowlerName by mutableStateOf<String?>(null)

    // Undo stack
    private val deliveryHistory = mutableStateListOf<DeliverySnapshot>()

    data class DeliverySnapshot(
        val strikerIndex: Int?,
        val nonStrikerIndex: Int?,
        val bowlerIndex: Int?,
        val battingTeamPlayers: List<Player>,
        val bowlingTeamPlayers: List<Player>,
        val totalWickets: Int,
        val currentOver: Int,
        val ballsInOver: Int,
        val totalExtras: Int,
        val previousBowlerName: String?,
        val midOverReplacementDueToJoker: Boolean,
        val jokerBallsBowledInnings1: Int,
        val jokerBallsBowledInnings2: Int,
        val completedBattersInnings1: List<Player>,
        val completedBattersInnings2: List<Player>,
        val completedBowlersInnings1: List<Player>,
        val completedBowlersInnings2: List<Player>,
    )

    val striker get() = strikerIndex?.let { battingTeamPlayers.getOrNull(it) }
    val nonStriker get() = nonStrikerIndex?.let { battingTeamPlayers.getOrNull(it) }
    val bowler get() = bowlerIndex?.let { bowlingTeamPlayers.getOrNull(it) }

    val calculatedTotalRuns: Int
        get() = battingTeamPlayers.sumOf { it.runs } + totalExtras

    fun initFromIntent(
        t1Name: String,
        t2Name: String,
        jName: String,
        t1Players: List<String>,
        t2Players: List<String>,
        settings: MatchSettings
    ) {
        if (battingTeamPlayers.isNotEmpty()) return // already initialized (e.g., rotation)
        team1Name = t1Name
        team2Name = t2Name
        jokerName = jName
        matchSettings = settings
        battingTeamPlayers = t1Players.map { Player(it) }.toMutableList()
        bowlingTeamPlayers = t2Players.map { Player(it) }.toMutableList()
        jokerPlayer = jName.takeIf { it.isNotBlank() }?.let { Player(it, isJoker = true) }
    }

    private fun recordCurrentBowlerIfAny() {
        val idx = bowlerIndex ?: return
        val p = bowlingTeamPlayers.getOrNull(idx) ?: return
        val list = if (currentInnings == 1) completedBowlersInnings1 else completedBowlersInnings2
        val i = list.indexOfFirst { it.name.equals(p.name, true) }
        if (i == -1) list.add(p.copy()) else list[i] = p.copy()
    }

    fun swapStrike() {
        val t = strikerIndex
        strikerIndex = nonStrikerIndex
        nonStrikerIndex = t
    }

    fun showSingleSideLayout(): Boolean {
        val extra = if (jokerPlayer != null && !battingTeamPlayers.any { it.isJoker } && !jokerOutInCurrentInnings) 1 else 0
        val available = battingTeamPlayers.count { !it.isOut } + extra
        return matchSettings.allowSingleSideBatting && available == 1
    }

    fun isInningsComplete(): Boolean {
        val extra = if (jokerPlayer != null && !battingTeamPlayers.any { it.isJoker } && !jokerOutInCurrentInnings) 1 else 0
        val available = battingTeamPlayers.count { !it.isOut } + extra
        val allOut = if (matchSettings.allowSingleSideBatting) available == 0 else totalWickets >= battingTeamPlayers.size - 1
        val oversDone = currentOver >= matchSettings.totalOvers
        val chaseOver = currentInnings == 2 && calculatedTotalRuns > firstInningsRuns
        return oversDone || chaseOver || allOut
    }

    fun pushSnapshot() {
        deliveryHistory.add(
            DeliverySnapshot(
                strikerIndex, nonStrikerIndex, bowlerIndex,
                battingTeamPlayers.map { it.copy() },
                bowlingTeamPlayers.map { it.copy() },
                totalWickets, currentOver, ballsInOver, totalExtras,
                previousBowlerName, midOverReplacementDueToJoker,
                jokerBallsBowledInnings1, jokerBallsBowledInnings2,
                completedBattersInnings1.map { it.copy() },
                completedBattersInnings2.map { it.copy() },
                completedBowlersInnings1.map { it.copy() },
                completedBowlersInnings2.map { it.copy() },
            )
        )
    }

    fun undoLastDelivery(): Boolean {
        if (deliveryHistory.isEmpty()) return false
        val s = deliveryHistory.removeLast()
        strikerIndex = s.strikerIndex
        nonStrikerIndex = s.nonStrikerIndex
        bowlerIndex = s.bowlerIndex
        battingTeamPlayers = s.battingTeamPlayers.toMutableList()
        bowlingTeamPlayers = s.bowlingTeamPlayers.toMutableList()
        totalWickets = s.totalWickets
        currentOver = s.currentOver
        ballsInOver = s.ballsInOver
        totalExtras = s.totalExtras
        previousBowlerName = s.previousBowlerName
        midOverReplacementDueToJoker = s.midOverReplacementDueToJoker
        jokerBallsBowledInnings1 = s.jokerBallsBowledInnings1
        jokerBallsBowledInnings2 = s.jokerBallsBowledInnings2
        completedBattersInnings1 = s.completedBattersInnings1.toMutableList()
        completedBattersInnings2 = s.completedBattersInnings2.toMutableList()
        completedBowlersInnings1 = s.completedBowlersInnings1.toMutableList()
        completedBowlersInnings2 = s.completedBowlersInnings2.toMutableList()
        showBowlerDialog = false
        showBatsmanDialog = false
        showExtrasDialog = false
        showWicketDialog = false
        return true
    }

    private fun incJokerBallIfBowledThisDelivery() {
        if (bowler?.isJoker == true) {
            if (currentInnings == 1) jokerBallsBowledInnings1++ else jokerBallsBowledInnings2++
        }
    }

    private fun advanceBallAndHandleOverEnd() {
        ballsInOver++
        if (ballsInOver == 6) {
            currentOver++
            ballsInOver = 0
            recordCurrentBowlerIfAny()
            previousBowlerName = bowler?.name
            bowlerIndex = null
            midOverReplacementDueToJoker = false
            if (!showSingleSideLayout()) swapStrike()
            showBowlerDialog = true
        }
    }

    fun scoreRuns(runs: Int) {
        pushSnapshot()
        strikerIndex?.let { idx ->
            val p = battingTeamPlayers[idx]
            battingTeamPlayers[idx] = p.copy(
                runs = p.runs + runs,
                ballsFaced = p.ballsFaced + 1,
                fours = p.fours + if (runs == 4) 1 else 0,
                sixes = p.sixes + if (runs == 6) 1 else 0
            )
        }
        bowlerIndex?.let { idx ->
            val b = bowlingTeamPlayers[idx]
            bowlingTeamPlayers[idx] = b.copy(
                runsConceded = b.runsConceded + runs,
                ballsBowled = b.ballsBowled + 1
            )
        }
        incJokerBallIfBowledThisDelivery()
        if (runs % 2 == 1 && !showSingleSideLayout()) swapStrike()
        advanceBallAndHandleOverEnd()
    }

    // EXTRAS
    fun addWide(totalRuns: Int) {
        pushSnapshot()
        bowlerIndex?.let { idx ->
            val b = bowlingTeamPlayers[idx]
            bowlingTeamPlayers[idx] = b.copy(runsConceded = b.runsConceded + totalRuns)
        }
        totalExtras += totalRuns
        val base = matchSettings.legSideWideRuns // or offSide handled by caller
        val addl = totalRuns - base
        if (addl % 2 == 1 && !showSingleSideLayout()) swapStrike()
    }

    fun addNoBall(totalRuns: Int) {
        pushSnapshot()
        strikerIndex?.let { idx ->
            val p = battingTeamPlayers[idx]
            battingTeamPlayers[idx] = p.copy(ballsFaced = p.ballsFaced + 1)
        }
        bowlerIndex?.let { idx ->
            val b = bowlingTeamPlayers[idx]
            bowlingTeamPlayers[idx] = b.copy(runsConceded = b.runsConceded + totalRuns)
        }
        totalExtras += totalRuns
        val addl = totalRuns - matchSettings.noballRuns
        if (addl % 2 == 1 && !showSingleSideLayout()) swapStrike()
    }

    fun addByeOrLegBye(totalRuns: Int) {
        pushSnapshot()
        strikerIndex?.let { idx ->
            val p = battingTeamPlayers[idx]
            battingTeamPlayers[idx] = p.copy(ballsFaced = p.ballsFaced + 1)
        }
        bowlerIndex?.let { idx ->
            val b = bowlingTeamPlayers[idx]
            bowlingTeamPlayers[idx] = b.copy(ballsBowled = b.ballsBowled + 1)
        }
        incJokerBallIfBowledThisDelivery()
        totalExtras += totalRuns
        advanceBallAndHandleOverEnd()
        val base = matchSettings.byeRuns // or legBye handled by caller for addl parity
        val addl = totalRuns - base
        if (addl % 2 == 1 && !showSingleSideLayout()) swapStrike()
    }

    // Wicket
    fun wicket(onToast: (String) -> Unit) {
        pushSnapshot()
        totalWickets += 1
        strikerIndex?.let { idx ->
            val p = battingTeamPlayers[idx]
            battingTeamPlayers[idx] = p.copy(isOut = true, ballsFaced = p.ballsFaced + 1)
            val snap = p.copy()
            val ledger = if (currentInnings == 1) completedBattersInnings1 else completedBattersInnings2
            val i = ledger.indexOfFirst { it.name.equals(snap.name, true) }
            if (i == -1) ledger.add(snap) else ledger[i] = snap
        }
        bowlerIndex?.let { idx ->
            val b = bowlingTeamPlayers[idx]
            bowlingTeamPlayers[idx] = b.copy(wickets = b.wickets + 1, ballsBowled = b.ballsBowled + 1)
        }
        incJokerBallIfBowledThisDelivery()
        advanceBallAndHandleOverEnd()
        onToast("Wicket!")
    }

    // Innings complete handling (called by screen when vm.isInningsComplete() flips true)
    fun finalizeFirstInnings() {
        firstInningsRuns = calculatedTotalRuns
        firstInningsWickets = totalWickets
        firstInningsOvers = currentOver + 1
        firstInningsBalls = ballsInOver
        firstInningsBattingPlayersList = (battingTeamPlayers.filter { it.ballsFaced > 0 || it.runs > 0 } + completedBattersInnings1).distinctBy { it.name }
        firstInningsBowlingPlayersList = (bowlingTeamPlayers.filter { it.ballsBowled > 0 || it.wickets > 0 || it.runsConceded > 0 } + completedBowlersInnings1).distinctBy { it.name }
        previousBowlerName = null
        showInningsBreakDialog = true
    }

    fun startSecondInnings() {
        currentInnings = 2
        // swap teams
        val tempPlayers = battingTeamPlayers
        val tempName = team1Name
        battingTeamPlayers = bowlingTeamPlayers
        bowlingTeamPlayers = tempPlayers
        team1Name = team2Name
        team2Name = tempName

        battingTeamPlayers = battingTeamPlayers.map { it.copy(runs = 0, ballsFaced = 0, fours = 0, sixes = 0, isOut = false) }.toMutableList()
        bowlingTeamPlayers = bowlingTeamPlayers.map { it.copy(wickets = 0, runsConceded = 0, ballsBowled = 0, isOut = false) }.toMutableList()
        battingTeamPlayers = battingTeamPlayers.filter { !it.isJoker }.toMutableList()
        bowlingTeamPlayers = bowlingTeamPlayers.filter { !it.isJoker }.toMutableList()

        jokerOutInCurrentInnings = false
        // keep joker balls ledgers as-is
        totalWickets = 0
        currentOver = 0
        ballsInOver = 0
        totalExtras = 0
        previousBowlerName = null
        completedBowlersInnings2 = mutableListOf()
        strikerIndex = null
        nonStrikerIndex = null
        bowlerIndex = null

        showInningsBreakDialog = false
        showBatsmanDialog = true
        selectingBatsman = 1
    }

    private var savedOnce = false
    fun saveCompletedMatchAndNavigate(
        context: Context,
        team1OriginalName: String,
        team2OriginalName: String,
        onSuccessNavigate: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (savedOnce) {
            onSuccessNavigate()
            return
        }
        try {
            val isTie = calculatedTotalRuns == firstInningsRuns
            val chasingWon = currentInnings == 2 && calculatedTotalRuns > firstInningsRuns
            val winner = when {
                isTie -> "TIE"
                chasingWon -> team2OriginalName
                else -> team1OriginalName
            }
            val margin = when {
                isTie -> "Scores level"
                chasingWon -> "${10 - totalWickets} wickets"
                else -> "${firstInningsRuns - calculatedTotalRuns} runs"
            }

            val firstBat = firstInningsBattingPlayersList.map { it.toMatchStats(team1OriginalName) }
            val firstBowl = firstInningsBowlingPlayersList.map { it.toMatchStats(team2OriginalName) }
            val secondBat = battingTeamPlayers.map { it.toMatchStats(team2OriginalName) }
            val secondBowl = bowlingTeamPlayers.map { it.toMatchStats(team1OriginalName) }

            val matchHistory = MatchHistory(
                team1Name = team1OriginalName,
                team2Name = team2OriginalName,
                jokerPlayerName = jokerName.ifBlank { null },
                firstInningsRuns = firstInningsRuns,
                firstInningsWickets = firstInningsWickets,
                secondInningsRuns = calculatedTotalRuns,
                secondInningsWickets = totalWickets,
                winnerTeam = winner,
                winningMargin = margin,
                firstInningsBatting = firstBat,
                firstInningsBowling = firstBowl,
                secondInningsBatting = secondBat,
                secondInningsBowling = secondBowl,
                team1Players = firstBat + secondBowl,
                team2Players = firstBowl + secondBat,
                topBatsman = (firstBat + secondBat).maxByOrNull { it.runs },
                topBowler = (firstBowl + secondBowl).maxByOrNull { it.wickets },
                matchSettings = matchSettings
            )
            storage.saveMatch(matchHistory)
            savedOnce = true
            onSuccessNavigate()
        } catch (t: Throwable) {
            onError(t)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application)
                ScoringViewModel(app)
            }
        }
    }
}
