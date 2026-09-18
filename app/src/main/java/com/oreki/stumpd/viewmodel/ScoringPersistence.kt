package com.oreki.stumpd.viewmodel

import android.util.Log
import com.oreki.stumpd.domain.match.battingSideIsFirstInningsSide
import com.google.gson.Gson
import com.oreki.stumpd.domain.model.PartnershipsPersistenceState
import com.oreki.stumpd.domain.model.SuperOverPersistenceState
import com.oreki.stumpd.data.local.db.StumpdDb
import com.oreki.stumpd.data.models.createMatchInProgress
import com.oreki.stumpd.data.models.deliverySnapshotsAlignedToDeliveryCount
import com.oreki.stumpd.data.models.parseDeliverySnapshotList
import com.oreki.stumpd.data.models.parsePartnershipsPersistenceState
import com.oreki.stumpd.data.models.toPlayerList
import com.oreki.stumpd.data.sync.firebase.EnhancedFirebaseAuthHelper
import com.oreki.stumpd.data.sync.firebase.FirestoreInProgressMatchDao
import com.oreki.stumpd.data.sync.sharing.MatchSharingManager
import com.oreki.stumpd.domain.model.DeliveryUI
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Match persistence (auto-save, resume, Firebase auto-share) extracted from ScoringViewModel.
 */
class ScoringPersistence(private val vm: ScoringViewModel, private val gson: Gson) {

    fun startFirebaseAutoShare() {
        vm.viewModelScope.launch {
            try {
                delay(1000)
                val context = vm.getApplication<android.app.Application>()
                val authHelper = EnhancedFirebaseAuthHelper(context)
                var userId = authHelper.currentUserId
                if (userId == null) {
                    Log.d("ScoringVM", "No userId, signing in anonymously...")
                    val user = authHelper.signInAnonymously()
                    userId = user?.uid ?: run {
                        Log.e("ScoringVM", "Auto-share: Authentication failed"); return@launch
                    }
                    Log.d("ScoringVM", "Auto-share: Signed in with userId: $userId")
                    delay(2000)
                } else {
                    Log.d("ScoringVM", "Auto-share: Already authenticated with userId: $userId")
                    delay(1000)
                }
                Log.d("ScoringVM", "Auto-share: Attempting to share matchId: ${vm.matchId}")
                val result = MatchSharingManager().shareMatch(
                    ownerId = userId, matchId = vm.matchId, ownerName = null, expiryHours = 48
                )
                result.onSuccess { code -> Log.d("ScoringVM", "Auto-share: SUCCESS! Code: $code") }
                    .onFailure { e -> Log.e("ScoringVM", "Auto-share: FAILED - ${e.message}", e) }
            } catch (e: Exception) {
                Log.e("ScoringVM", "Auto-share: Exception", e)
            }
        }
    }

    fun resumeMatch() {
        if (!vm.isResuming || vm.resumedMatchLoaded) return
        vm.viewModelScope.launch {
            val savedMatch = vm.inProgressManager.loadMatch()
            if (savedMatch != null && savedMatch.matchId == vm.matchId) {
                try {
                    vm.currentInnings = savedMatch.currentInnings
                    vm.currentOver = savedMatch.currentOver
                    vm.ballsInOver = savedMatch.ballsInOver
                    vm.totalWickets = savedMatch.totalWickets
                    vm.team1Players = savedMatch.team1PlayersJson.toPlayerList(gson).toMutableList()
                    vm.team2Players = savedMatch.team2PlayersJson.toPlayerList(gson).toMutableList()

                    val t1First = vm.isTeam1BatsFirst()
                    // Which side is in depends on the innings, and not on its parity: innings 3
                    // has the *second* innings' side batting again. One shared rule, so resume and
                    // auto-save cannot drift apart.
                    val firstInningsSideBatting =
                        battingSideIsFirstInningsSide(savedMatch.currentInnings)
                    val battingIsTeam1 = firstInningsSideBatting == t1First
                    vm.battingTeamPlayers = if (battingIsTeam1) vm.team1Players else vm.team2Players
                    vm.bowlingTeamPlayers = if (battingIsTeam1) vm.team2Players else vm.team1Players
                    vm.battingTeamName = if (battingIsTeam1) vm.team1Name else vm.team2Name
                    vm.bowlingTeamName = if (battingIsTeam1) vm.team2Name else vm.team1Name

                    vm.strikerIndex = savedMatch.strikerIndex
                    vm.nonStrikerIndex = savedMatch.nonStrikerIndex
                    vm.bowlerIndex = savedMatch.bowlerIndex
                    vm.firstInningsRuns = savedMatch.firstInningsRuns
                    vm.firstInningsWickets = savedMatch.firstInningsWickets
                    vm.firstInningsOvers = savedMatch.firstInningsOvers
                    vm.firstInningsBalls = savedMatch.firstInningsBalls
                    vm.totalExtras = savedMatch.totalExtras
                    vm.jokerOutInCurrentInnings = savedMatch.jokerOutInCurrentInnings
                    vm.jokerBallsBowledInnings1 = savedMatch.jokerBallsBowledInnings1
                    vm.jokerBallsBowledInnings2 = savedMatch.jokerBallsBowledInnings2
                    vm.powerplayRunsInnings1 = savedMatch.powerplayRunsInnings1
                    vm.powerplayRunsInnings2 = savedMatch.powerplayRunsInnings2
                    vm.powerplayDoublingDoneInnings1 = savedMatch.powerplayDoublingDoneInnings1
                    vm.powerplayDoublingDoneInnings2 = savedMatch.powerplayDoublingDoneInnings2

                    savedMatch.completedBattersInnings1Json?.let {
                        vm.completedBattersInnings1 = it.toPlayerList(gson).toMutableList()
                    }
                    savedMatch.completedBattersInnings2Json?.let {
                        vm.completedBattersInnings2 = it.toPlayerList(gson).toMutableList()
                    }
                    savedMatch.completedBowlersInnings1Json?.let {
                        vm.completedBowlersInnings1 = it.toPlayerList(gson).toMutableList()
                    }
                    savedMatch.completedBowlersInnings2Json?.let {
                        vm.completedBowlersInnings2 = it.toPlayerList(gson).toMutableList()
                    }
                    savedMatch.firstInningsBattingPlayersJson?.let {
                        vm.firstInningsBattingPlayersList = it.toPlayerList(gson)
                    }
                    savedMatch.firstInningsBowlingPlayersJson?.let {
                        vm.firstInningsBowlingPlayersList = it.toPlayerList(gson)
                    }
                    savedMatch.allDeliveriesJson?.let { json ->
                        try {
                            val deliveries = gson.fromJson(json, Array<DeliveryUI>::class.java).toList()
                            vm.allDeliveries.clear()
                            vm.allDeliveries.addAll(deliveries)
                        } catch (e: Exception) {
                            Log.e("ScoringVM", "Failed to restore deliveries", e)
                        }
                    }

                    vm.deliveryHistory.clear()
                    val restoredSnaps = savedMatch.deliveryHistoryJson.parseDeliverySnapshotList(gson)
                    val dCount = vm.allDeliveries.size
                    vm.deliveryHistory.addAll(
                        deliverySnapshotsAlignedToDeliveryCount(restoredSnaps, dCount)
                    )

                    savedMatch.partnershipsStateJson.parsePartnershipsPersistenceState(gson)?.let { ps ->
                        vm.currentPartnershipRuns = ps.currentPartnershipRuns
                        vm.currentPartnershipBalls = ps.currentPartnershipBalls
                        vm.currentPartnershipBatsman1Runs = ps.currentPartnershipBatsman1Runs
                        vm.currentPartnershipBatsman2Runs = ps.currentPartnershipBatsman2Runs
                        vm.currentPartnershipBatsman1Balls = ps.currentPartnershipBatsman1Balls
                        vm.currentPartnershipBatsman2Balls = ps.currentPartnershipBatsman2Balls
                        vm.currentPartnershipBatsman1Name = ps.currentPartnershipBatsman1Name
                        vm.currentPartnershipBatsman2Name = ps.currentPartnershipBatsman2Name
                        vm.partnerships = ps.partnerships.map { it.copy() }
                        vm.firstInningsPartnerships = ps.firstInningsPartnerships.map { it.copy() }
                        // Matches saved before wicket numbering was made innings-scoped can carry
                        // duplicates, which would REPLACE each other on save. Renumber densely.
                        vm.fallOfWickets = ps.fallOfWickets.mapIndexed { index, fow ->
                            fow.copy(wicketNumber = index + 1)
                        }
                        vm.firstInningsFallOfWickets = ps.firstInningsFallOfWickets.mapIndexed { index, fow ->
                            fow.copy(wicketNumber = index + 1)
                        }
                        vm.secondInningsPartnerships = ps.secondInningsPartnerships.map { it.copy() }
                        vm.secondInningsFallOfWickets =
                            ps.secondInningsFallOfWickets.mapIndexed { index, fow ->
                                fow.copy(wicketNumber = index + 1)
                            }
                    }

                    // The eliminator, and the target the innings in progress is chasing. Without
                    // the latter a resumed chase would never end, because nothing else records it.
                    val so = savedMatch.superOverStateJson
                        ?.let { runCatching { gson.fromJson(it, SuperOverPersistenceState::class.java) }.getOrNull() }
                    vm.secondInningsRuns = so?.secondInningsRuns ?: 0
                    vm.secondInningsWickets = so?.secondInningsWickets ?: 0
                    vm.superOvers = so?.superOvers.orEmpty()
                    vm.superOverWinner = so?.superOverWinner
                    vm.completedBattersSuperOver = so?.completedBattersSuperOver.orEmpty()
                    vm.completedBowlersSuperOver = so?.completedBowlersSuperOver.orEmpty()

                    // The stored row is authoritative for the fixture link: it was written when
                    // the match started, and the resuming intent may not carry it.
                    savedMatch.tournamentId?.let { vm.tournamentId = it }
                    savedMatch.tournamentFixtureId?.let { vm.tournamentFixtureId = it }
                    savedMatch.team1Id?.let { vm.team1Id = it }
                    savedMatch.team2Id?.let { vm.team2Id = it }
                    vm.runsToChase = so?.runsToChase
                        ?: when {
                            savedMatch.currentInnings == 2 -> savedMatch.firstInningsRuns
                            else -> null
                        }

                    vm.resumedMatchLoaded = true
                    vm.toastLong("Match resumed from Over ${vm.currentOver}.${vm.ballsInOver}")
                } catch (e: Exception) {
                    Log.e("ScoringVM", "Failed to resume match", e)
                    vm.toastLong("Failed to resume match. Starting fresh.")
                }
            }
        }
    }

    fun autoSaveMatch() {
        try {
            val t1First = vm.isTeam1BatsFirst()
            val battingIsTeam1 = battingSideIsFirstInningsSide(vm.currentInnings) == t1First
            val currentTeam1Players = if (battingIsTeam1) vm.battingTeamPlayers else vm.bowlingTeamPlayers
            val currentTeam2Players = if (battingIsTeam1) vm.bowlingTeamPlayers else vm.battingTeamPlayers

            val matchInProgress = createMatchInProgress(
                matchId = vm.matchId, team1Name = vm.team1Name, team2Name = vm.team2Name,
                jokerName = vm.jokerName, team1PlayerIds = vm.team1PlayerIds, team2PlayerIds = vm.team2PlayerIds,
                team1PlayerNames = vm.team1PlayerNames, team2PlayerNames = vm.team2PlayerNames,
                matchSettingsJson = vm.matchSettingsJson, groupId = vm.groupId, groupName = vm.groupName,
                tossWinner = vm.tossWinner, tossChoice = vm.tossChoice,
                currentInnings = vm.currentInnings, currentOver = vm.currentOver, ballsInOver = vm.ballsInOver,
                team1Players = currentTeam1Players, team2Players = currentTeam2Players,
                strikerIndex = vm.strikerIndex, nonStrikerIndex = vm.nonStrikerIndex, bowlerIndex = vm.bowlerIndex,
                firstInningsRuns = vm.firstInningsRuns, firstInningsWickets = vm.firstInningsWickets,
                firstInningsOvers = vm.firstInningsOvers, firstInningsBalls = vm.firstInningsBalls,
                bowlingTeamPlayers = vm.bowlingTeamPlayers, totalExtras = vm.totalExtras,
                wides = 0, noBalls = 0, byes = 0, legByes = 0,
                completedBattersInnings1 = vm.completedBattersInnings1, completedBattersInnings2 = vm.completedBattersInnings2,
                completedBowlersInnings1 = vm.completedBowlersInnings1, completedBowlersInnings2 = vm.completedBowlersInnings2,
                firstInningsBattingPlayers = vm.firstInningsBattingPlayersList,
                firstInningsBowlingPlayers = vm.firstInningsBowlingPlayersList,
                jokerOutInCurrentInnings = vm.jokerOutInCurrentInnings,
                jokerBallsBowledInnings1 = vm.jokerBallsBowledInnings1, jokerBallsBowledInnings2 = vm.jokerBallsBowledInnings2,
                powerplayRunsInnings1 = vm.powerplayRunsInnings1, powerplayRunsInnings2 = vm.powerplayRunsInnings2,
                powerplayDoublingDoneInnings1 = vm.powerplayDoublingDoneInnings1, powerplayDoublingDoneInnings2 = vm.powerplayDoublingDoneInnings2,
                allDeliveries = vm.allDeliveries.toList(), totalWickets = vm.totalWickets,
                calculatedTotalRuns = vm.calculatedTotalRuns, deliveryHistory = vm.deliveryHistory.toList(),
                partnershipsState = PartnershipsPersistenceState(
                    currentPartnershipRuns = vm.currentPartnershipRuns, currentPartnershipBalls = vm.currentPartnershipBalls,
                    currentPartnershipBatsman1Runs = vm.currentPartnershipBatsman1Runs,
                    currentPartnershipBatsman2Runs = vm.currentPartnershipBatsman2Runs,
                    currentPartnershipBatsman1Balls = vm.currentPartnershipBatsman1Balls,
                    currentPartnershipBatsman2Balls = vm.currentPartnershipBatsman2Balls,
                    currentPartnershipBatsman1Name = vm.currentPartnershipBatsman1Name,
                    currentPartnershipBatsman2Name = vm.currentPartnershipBatsman2Name,
                    partnerships = vm.partnerships.map { it.copy() },
                    fallOfWickets = vm.fallOfWickets.map { it.copy() },
                    firstInningsPartnerships = vm.firstInningsPartnerships.map { it.copy() },
                    firstInningsFallOfWickets = vm.firstInningsFallOfWickets.map { it.copy() },
                    secondInningsPartnerships = vm.secondInningsPartnerships.map { it.copy() },
                    secondInningsFallOfWickets = vm.secondInningsFallOfWickets.map { it.copy() },
                ),
                superOverState = SuperOverPersistenceState(
                    runsToChase = vm.runsToChase,
                    secondInningsRuns = vm.secondInningsRuns,
                    secondInningsWickets = vm.secondInningsWickets,
                    superOvers = vm.superOvers,
                    superOverWinner = vm.superOverWinner,
                    completedBattersSuperOver = vm.completedBattersSuperOver,
                    completedBowlersSuperOver = vm.completedBowlersSuperOver,
                ),
                // The fixture link, or a process death here would orphan it.
                tournamentId = vm.tournamentId,
                tournamentFixtureId = vm.tournamentFixtureId,
                team1Id = vm.team1Id,
                team2Id = vm.team2Id,
                gson = gson
            )
            vm.viewModelScope.launch {
                Log.d("ScoringVM", "=== Starting auto-save ===")
                vm.inProgressManager.saveMatch(matchInProgress)
                Log.d("ScoringVM", "Saved to Room DB")
                try {
                    val context = vm.getApplication<android.app.Application>()
                    val authHelper = EnhancedFirebaseAuthHelper(context)
                    val userId = authHelper.currentUserId
                    Log.d("ScoringVM", "Firestore sync - userId: $userId")
                    if (userId != null) {
                        val entity = StumpdDb.get(context).inProgressMatchDao().getLatest()
                        Log.d("ScoringVM", "Entity retrieved: matchId=${entity?.matchId}")
                        if (entity != null) {
                            FirestoreInProgressMatchDao().uploadInProgressMatch(userId, entity)
                            Log.d("ScoringVM", "Live match synced to Firestore")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ScoringVM", "Failed to sync match to Firestore", e)
                }
            }
        } catch (e: Exception) {
            Log.e("ScoringVM", "Failed to auto-save match", e)
        }
    }
}
