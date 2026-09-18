package com.oreki.stumpd.viewmodel

import com.oreki.stumpd.domain.match.swapSidesEnteringInnings
import com.oreki.stumpd.ui.scoring.ScoringEffect
import com.oreki.stumpd.domain.model.*
import com.oreki.stumpd.ui.scoring.NoBallOutcomeHolders

/**
 * Pure scoring logic extracted from ScoringViewModel.
 * Handles runs, extras, wickets, partnerships, deliveries, undo, overs, and innings transitions.
 */
class ScoringEngine(private val vm: ScoringViewModel) {

    private val matchSettings get() = vm.matchSettings

    // ── Delivery helpers ─────────────────────────────────────────────

    fun addDelivery(outcome: String, highlight: Boolean = false, runs: Int = 0) {
        val ballNumber = (vm.ballsInOver % 6) + 1
        val entry = DeliveryUI(
            inning = vm.currentInnings,
            over = vm.currentOver + 1,
            ballInOver = ballNumber,
            outcome = outcome,
            highlight = highlight,
            strikerName = vm.striker?.name ?: "",
            nonStrikerName = vm.nonStriker?.name ?: "",
            bowlerName = vm.bowler?.name ?: "",
            runs = runs
        )
        vm.allDeliveries.add(entry)
    }

    fun pushSnapshot() {
        vm.deliveryHistory.add(
            DeliverySnapshot(
                strikerIndex = vm.strikerIndex,
                nonStrikerIndex = vm.nonStrikerIndex,
                bowlerIndex = vm.bowlerIndex,
                battingTeamPlayers = vm.battingTeamPlayers.map { it.copy() },
                bowlingTeamPlayers = vm.bowlingTeamPlayers.map { it.copy() },
                totalWickets = vm.totalWickets,
                currentOver = vm.currentOver,
                ballsInOver = vm.ballsInOver,
                runsConcededInCurrentOver = vm.runsConcededInCurrentOver,
                totalExtras = vm.totalExtras,
                calculatedTotalRuns = vm.calculatedTotalRuns,
                previousBowlerName = vm.previousBowlerName,
                midOverReplacementDueToJoker = vm.midOverReplacementDueToJoker.value,
                jokerBallsBowledInnings1 = vm.jokerBallsBowledInnings1,
                jokerBallsBowledInnings2 = vm.jokerBallsBowledInnings2,
                jokerOutInCurrentInnings = vm.jokerOutInCurrentInnings,
                currentBowlerSpell = vm.currentBowlerSpell,
                powerplayDoublingDoneInnings1 = vm.powerplayDoublingDoneInnings1,
                powerplayDoublingDoneInnings2 = vm.powerplayDoublingDoneInnings2,
                isNoBallRunOut = vm.isNoBallRunOut,
                completedBattersInnings1 = vm.completedBattersInnings1.map { it.copy() },
                completedBattersInnings2 = vm.completedBattersInnings2.map { it.copy() },
                completedBowlersInnings1 = vm.completedBowlersInnings1.map { it.copy() },
                completedBowlersInnings2 = vm.completedBowlersInnings2.map { it.copy() },
                currentPartnershipRuns = vm.currentPartnershipRuns,
                currentPartnershipBalls = vm.currentPartnershipBalls,
                currentPartnershipBatsman1Runs = vm.currentPartnershipBatsman1Runs,
                currentPartnershipBatsman2Runs = vm.currentPartnershipBatsman2Runs,
                currentPartnershipBatsman1Balls = vm.currentPartnershipBatsman1Balls,
                currentPartnershipBatsman2Balls = vm.currentPartnershipBatsman2Balls,
                currentPartnershipBatsman1Name = vm.currentPartnershipBatsman1Name,
                currentPartnershipBatsman2Name = vm.currentPartnershipBatsman2Name,
                partnerships = vm.partnerships.map { it.copy() },
                fallOfWickets = vm.fallOfWickets.map { it.copy() },
                currentInnings = vm.currentInnings,
                runsToChase = vm.runsToChase,
                completedBattersSuperOver = vm.completedBattersSuperOver.mapValues { (_, v) -> v.map { it.copy() } },
                completedBowlersSuperOver = vm.completedBowlersSuperOver.mapValues { (_, v) -> v.map { it.copy() } },
            )
        )
    }

    /**
     * Drops the trail entry for a delivery that has just been rolled back.
     *
     * Called after the snapshot has been restored, so the position now points at the ball about
     * to be bowled and anything past it is the delivery being undone. The old test — trail over
     * equals `currentOver + 1` — missed the ball that *ended* an over, because by then
     * `currentOver` had advanced, so the score rolled back while the ball stayed in the trail
     * and the Overs tab kept showing it.
     */
    fun removeLastDeliveryIfAny() {
        val last = vm.allDeliveries.lastOrNull() ?: return
        if (last.inning != vm.currentInnings) return
        val restoredBalls = vm.currentOver * 6 + vm.ballsInOver
        val lastBalls = (last.over - 1) * 6 + last.ballInOver
        if (lastBalls > restoredBalls) {
            vm.allDeliveries.removeAt(vm.allDeliveries.lastIndex)
        }
    }

    /**
     * Rolls back one delivery.
     *
     * [silent] suppresses the toasts for callers that undo as a means to an end — restating a
     * mis-entered ball, for instance, where "Last delivery undone" would be a lie about what just
     * happened. Returns whether anything was undone.
     */
    fun undoLastDelivery(silent: Boolean = false): Boolean {
        if (vm.deliveryHistory.isEmpty()) {
            if (!silent) vm.toast("Nothing to undo")
            return false
        }
        val peek = vm.deliveryHistory.last()
        // Undo stops at the start of the innings it is in, for any innings past the first — the
        // rule used to name innings 2 specifically, which would have let a super-over undo bleed
        // back into the chase. The snapshot's own innings is the belt to that braces: a snapshot
        // from a different innings is never popped.
        val pastFirstInnings = vm.currentInnings > 1
        val atStartOfInnings = pastFirstInnings && vm.currentOver == 0 && vm.ballsInOver == 0
        val wouldCrossInningsStart =
            pastFirstInnings && peek.currentOver == 0 && peek.ballsInOver == 0
        if (atStartOfInnings || wouldCrossInningsStart || peek.currentInnings != vm.currentInnings) {
            if (!silent) {
                vm.toast("Cannot undo beyond the start of this innings")
            }
            return false
        }
        val snap = vm.deliveryHistory.removeAt(vm.deliveryHistory.lastIndex)
        vm.strikerIndex = snap.strikerIndex
        vm.nonStrikerIndex = snap.nonStrikerIndex
        vm.bowlerIndex = snap.bowlerIndex
        vm.battingTeamPlayers = snap.battingTeamPlayers.toMutableList()
        vm.bowlingTeamPlayers = snap.bowlingTeamPlayers.toMutableList()
        vm.totalWickets = snap.totalWickets
        vm.currentOver = snap.currentOver
        vm.ballsInOver = snap.ballsInOver
        vm.runsConcededInCurrentOver = snap.runsConcededInCurrentOver
        vm.totalExtras = snap.totalExtras
        vm.previousBowlerName = snap.previousBowlerName
        vm.midOverReplacementDueToJoker.value = snap.midOverReplacementDueToJoker
        vm.jokerBallsBowledInnings1 = snap.jokerBallsBowledInnings1
        vm.jokerBallsBowledInnings2 = snap.jokerBallsBowledInnings2
        vm.jokerOutInCurrentInnings = snap.jokerOutInCurrentInnings
        vm.currentBowlerSpell = snap.currentBowlerSpell
        vm.powerplayDoublingDoneInnings1 = snap.powerplayDoublingDoneInnings1
        vm.powerplayDoublingDoneInnings2 = snap.powerplayDoublingDoneInnings2
        vm.isNoBallRunOut = snap.isNoBallRunOut
        vm.completedBattersInnings1 = snap.completedBattersInnings1.toMutableList()
        vm.completedBattersInnings2 = snap.completedBattersInnings2.toMutableList()
        vm.completedBowlersInnings1 = snap.completedBowlersInnings1.toMutableList()
        vm.completedBowlersInnings2 = snap.completedBowlersInnings2.toMutableList()
        vm.currentPartnershipRuns = snap.currentPartnershipRuns
        vm.currentPartnershipBalls = snap.currentPartnershipBalls
        vm.currentPartnershipBatsman1Runs = snap.currentPartnershipBatsman1Runs
        vm.currentPartnershipBatsman2Runs = snap.currentPartnershipBatsman2Runs
        vm.currentPartnershipBatsman1Balls = snap.currentPartnershipBatsman1Balls
        vm.currentPartnershipBatsman2Balls = snap.currentPartnershipBatsman2Balls
        vm.currentPartnershipBatsman1Name = snap.currentPartnershipBatsman1Name
        vm.currentPartnershipBatsman2Name = snap.currentPartnershipBatsman2Name
        vm.partnerships = snap.partnerships.map { it.copy() }
        vm.fallOfWickets = snap.fallOfWickets.map { it.copy() }
        vm.currentInnings = snap.currentInnings
        vm.runsToChase = snap.runsToChase
        vm.completedBattersSuperOver = snap.completedBattersSuperOver
        vm.completedBowlersSuperOver = snap.completedBowlersSuperOver
        vm.showBowlerDialog = false
        vm.showBatsmanDialog = false
        vm.showExtrasDialog = false
        vm.showWicketDialog = false
        removeLastDeliveryIfAny()
        if (!silent) vm.toast("Last delivery undone")
        return true
    }

    // ── Striker / Bowler stat updates ────────────────────────────────

    fun updateStrikerAndTotals(updateFunction: (Player) -> Player) {
        vm.strikerIndex?.let { index ->
            val newPlayersList = vm.battingTeamPlayers.toMutableList()
            newPlayersList[index] = updateFunction(newPlayersList[index])
            vm.battingTeamPlayers = newPlayersList
        }
    }

    fun updateBowlerStats(updateFunction: (Player) -> Player) {
        vm.bowlerIndex?.let { index ->
            val newPlayersList = vm.bowlingTeamPlayers.toMutableList()
            newPlayersList[index] = updateFunction(newPlayersList[index])
            vm.bowlingTeamPlayers = newPlayersList
        }
    }

    // ── Partnership tracking ─────────────────────────────────────────

    fun initializePartnership() {
        if (vm.striker != null && vm.nonStriker != null) {
            vm.currentPartnershipRuns = 0
            vm.currentPartnershipBalls = 0
            vm.currentPartnershipBatsman1Runs = 0
            vm.currentPartnershipBatsman2Runs = 0
            vm.currentPartnershipBatsman1Balls = 0
            vm.currentPartnershipBatsman2Balls = 0
            vm.currentPartnershipBatsman1Name = vm.striker!!.name
            vm.currentPartnershipBatsman2Name = vm.nonStriker!!.name
        }
    }

    fun updatePartnershipOnRuns(runs: Int, isLegalDelivery: Boolean = true, creditStriker: Boolean = true) {
        if (vm.striker != null && vm.nonStriker != null) {
            if (isLegalDelivery) {
                vm.currentPartnershipBalls++
                if (vm.striker!!.name == vm.currentPartnershipBatsman1Name) vm.currentPartnershipBatsman1Balls++
                else if (vm.striker!!.name == vm.currentPartnershipBatsman2Name) vm.currentPartnershipBatsman2Balls++
            }
            vm.currentPartnershipRuns += runs
            if (creditStriker) {
                if (vm.striker!!.name == vm.currentPartnershipBatsman1Name) vm.currentPartnershipBatsman1Runs += runs
                else if (vm.striker!!.name == vm.currentPartnershipBatsman2Name) vm.currentPartnershipBatsman2Runs += runs
            }
        }
    }

    /**
     * The single place a wicket is recorded: applies the delivery to the running stand, closes
     * that stand, appends the fall-of-wicket entry and bumps the wicket count.
     *
     * Callers must call [pushSnapshot] first so undo can restore the pre-wicket state, and must
     * not touch `vm.totalWickets` themselves. Keeping the increment here is what keeps
     * `fallOfWickets.size` and `totalWickets` in step, which in turn keeps `wicketNumber` unique.
     */
    fun endPartnershipAndRecordWicket(
        outPlayer: Player,
        isRunOut: Boolean = false,
        runsOnDelivery: Int = 0,
        isLegalDelivery: Boolean = true,
        creditStriker: Boolean = false
    ) {
        updatePartnershipOnRuns(runsOnDelivery, isLegalDelivery, creditStriker)
        vm.totalWickets += 1
        vm.emitEffect(ScoringEffect.Wicket)

        // Recorded even when the stand was scoreless: one row per wicket is what keeps the
        // partnership list aligned with the fall-of-wickets list and the "Nth wicket" labels.
        if (vm.currentPartnershipBatsman1Name != null && vm.currentPartnershipBatsman2Name != null) {
            val partnership = Partnership(
                batsman1Name = vm.currentPartnershipBatsman1Name!!,
                batsman2Name = vm.currentPartnershipBatsman2Name!!,
                runs = vm.currentPartnershipRuns,
                balls = vm.currentPartnershipBalls,
                batsman1Runs = vm.currentPartnershipBatsman1Runs,
                batsman2Runs = vm.currentPartnershipBatsman2Runs,
                isActive = false
            )
            vm.partnerships = vm.partnerships + partnership
        }
        val teamScore = vm.calculatedTotalRuns
        val totalBalls = vm.currentOver * 6 + vm.ballsInOver + 1
        val oversCompleted = (totalBalls / 6).toDouble() + ((totalBalls % 6) / 10.0)

        val fow = FallOfWicket(
            batsmanName = outPlayer.name,
            runs = teamScore,
            overs = oversCompleted,
            // Derived from the list, which is cleared per innings, so this stays 1..N within the
            // innings. A repeated number would silently REPLACE the earlier row when saved.
            wicketNumber = vm.fallOfWickets.size + 1,
            dismissalType = outPlayer.dismissalType?.name,
            bowlerName = outPlayer.bowlerName,
            fielderName = outPlayer.fielderName
        )
        vm.fallOfWickets = vm.fallOfWickets + fow
        clearCurrentPartnership()
    }

    private fun clearCurrentPartnership() {
        vm.currentPartnershipRuns = 0
        vm.currentPartnershipBalls = 0
        vm.currentPartnershipBatsman1Runs = 0
        vm.currentPartnershipBatsman2Runs = 0
        vm.currentPartnershipBatsman1Balls = 0
        vm.currentPartnershipBatsman2Balls = 0
        vm.currentPartnershipBatsman1Name = null
        vm.currentPartnershipBatsman2Name = null
    }

    /**
     * Records the stand still in progress when an innings ends, so the last unbeaten
     * partnership is kept. Idempotent: clearing the names makes a second call a no-op, so a
     * re-entered innings-complete cannot duplicate the row.
     */
    fun closeCurrentPartnershipIfAny() {
        val batsman1 = vm.currentPartnershipBatsman1Name
        val batsman2 = vm.currentPartnershipBatsman2Name
        val faced = vm.currentPartnershipRuns > 0 || vm.currentPartnershipBalls > 0
        if (batsman1 != null && batsman2 != null && faced) {
            vm.partnerships = vm.partnerships + Partnership(
                batsman1Name = batsman1,
                batsman2Name = batsman2,
                runs = vm.currentPartnershipRuns,
                balls = vm.currentPartnershipBalls,
                batsman1Runs = vm.currentPartnershipBatsman1Runs,
                batsman2Runs = vm.currentPartnershipBatsman2Runs,
                isActive = true
            )
        }
        clearCurrentPartnership()
    }

    /**
     * Stashes the finished innings' stands and wickets, and clears the live lists for the next one.
     *
     * Innings 2 is stashed as well as innings 1 — it used to be left in the live lists for the save
     * to read, which was safe only while nothing followed it. A super-over wicket appends to those
     * same lists, so without this an eliminator dismissal would land in the second innings' fall of
     * wickets.
     *
     * Super overs keep nothing: `innings` is part of the primary key of both the partnerships and
     * fall-of-wickets tables, and the cloud split drops anything that isn't innings 1 or 2. An
     * eliminator has no meaningful partnership progression anyway.
     */
    fun saveInningsPartnershipsAndWickets() {
        closeCurrentPartnershipIfAny()
        when (vm.currentInnings) {
            1 -> {
                vm.firstInningsPartnerships = vm.partnerships
                vm.firstInningsFallOfWickets = vm.fallOfWickets
            }

            2 -> {
                vm.secondInningsPartnerships = vm.partnerships
                vm.secondInningsFallOfWickets = vm.fallOfWickets
            }
        }
        vm.partnerships = emptyList()
        vm.fallOfWickets = emptyList()
    }

    fun swapStrike() {
        val si = vm.strikerIndex
        val nsi = vm.nonStrikerIndex
        if (si == null || nsi == null) return
        vm.strikerIndex = nsi
        vm.nonStrikerIndex = si
    }

    fun initPartnershipIfNeeded() {
        if (vm.striker != null && vm.nonStriker != null && vm.currentPartnershipBatsman1Name == null) {
            vm.currentPartnershipRuns = 0
            vm.currentPartnershipBalls = 0
            vm.currentPartnershipBatsman1Runs = 0
            vm.currentPartnershipBatsman2Runs = 0
            vm.currentPartnershipBatsman1Balls = 0
            vm.currentPartnershipBatsman2Balls = 0
            vm.currentPartnershipBatsman1Name = vm.striker!!.name
            vm.currentPartnershipBatsman2Name = vm.nonStriker!!.name
        }
    }

    // ── Over completion ──────────────────────────────────────────────

    fun handleOverCompletionIfNeeded() {
        if (vm.ballsInOver == 6) {
            vm.selection.recordCurrentBowlerIfAny()
            vm.currentOver += 1
            vm.ballsInOver = 0
            vm.previousBowlerName = vm.bowler?.name
            vm.bowlerIndex = null
            vm.currentBowlerSpell = 0
            vm.midOverReplacementDueToJoker.value = false
            if (vm.showBatsmanDialog) {
                vm.pendingSwapAfterBatsmanPick = !vm.showSingleSideLayout
                vm.pendingBowlerDialogAfterBatsmanPick = true
            } else {
                if (!vm.showSingleSideLayout) swapStrike()
                vm.showBowlerDialog = true
            }
            vm.toastLong("Over complete! Select new bowler")
        }
    }

    // ── Scoring: Runs ────────────────────────────────────────────────

    fun onRunScored(runs: Int) {
        pushSnapshot()
        updateStrikerAndTotals { player ->
            player.copy(
                runs = player.runs + runs, ballsFaced = player.ballsFaced + 1,
                dots = if (runs == 0) player.dots + 1 else player.dots,
                singles = if (runs == 1) player.singles + 1 else player.singles,
                twos = if (runs == 2) player.twos + 1 else player.twos,
                threes = if (runs == 3) player.threes + 1 else player.threes,
                fours = if (runs == 4) player.fours + 1 else player.fours,
                sixes = if (runs == 6) player.sixes + 1 else player.sixes,
            )
        }
        updateBowlerStats { player ->
            player.copy(runsConceded = player.runsConceded + runs, ballsBowled = player.ballsBowled + 1)
        }
        vm.runsConcededInCurrentOver += runs
        updatePartnershipOnRuns(runs, isLegalDelivery = true, creditStriker = true)
        vm.selection.incJokerBallIfBowledThisDelivery()
        addDelivery(outcome = runs.toString(), highlight = (runs == 4 || runs == 6), runs = runs)
        when (runs) {
            4 -> vm.emitEffect(ScoringEffect.Four)
            6 -> vm.emitEffect(ScoringEffect.Six)
        }
        if (runs % 2 == 1 && !vm.showSingleSideLayout) swapStrike()
        vm.ballsInOver += 1
        handleOverCompletionIfNeeded()
    }

    // ── Scoring: Quick Wide ──────────────────────────────────────────

    fun onQuickWide() {
        pushSnapshot()
        val totalRuns = matchSettings.wideRuns
        updateBowlerStats { player -> player.copy(runsConceded = player.runsConceded + totalRuns) }
        vm.runsConcededInCurrentOver += totalRuns
        vm.totalExtras += totalRuns
        updatePartnershipOnRuns(totalRuns, isLegalDelivery = false, creditStriker = false)
        addDelivery("Wd", runs = totalRuns)
        val totalAfter = vm.battingTeamPlayers.sumOf { it.runs } + vm.totalExtras
        vm.toast("Wide! +$totalRuns runs. Total: $totalAfter")
    }

    // ── Scoring: Extras ──────────────────────────────────────────────

    fun onExtraSelected(extraType: ExtraType, totalRuns: Int) {
        pushSnapshot()
        when (extraType) {
            ExtraType.OFF_SIDE_WIDE, ExtraType.LEG_SIDE_WIDE -> {
                updateBowlerStats { player -> player.copy(runsConceded = player.runsConceded + totalRuns) }
                vm.runsConcededInCurrentOver += totalRuns
                vm.totalExtras += totalRuns
                val baseWideRuns = matchSettings.wideRuns
                val additionalRuns = totalRuns - baseWideRuns
                updatePartnershipOnRuns(baseWideRuns, isLegalDelivery = false, creditStriker = false)
                if (additionalRuns > 0) updatePartnershipOnRuns(additionalRuns, isLegalDelivery = false, creditStriker = true)
                if (additionalRuns % 2 == 1 && !vm.showSingleSideLayout) swapStrike()
                addDelivery("Wd+${totalRuns}", runs = totalRuns)
                vm.toast("${extraType.displayName}! +$totalRuns runs")
            }
            ExtraType.NO_BALL -> {
                val baseNoBallRuns = matchSettings.noballRuns
                val additionalRuns = totalRuns - baseNoBallRuns
                updateBowlerStats { player -> player.copy(runsConceded = player.runsConceded + totalRuns) }
                vm.runsConcededInCurrentOver += totalRuns
                vm.totalExtras += baseNoBallRuns
                updatePartnershipOnRuns(baseNoBallRuns, isLegalDelivery = false, creditStriker = false)
                if (additionalRuns > 0) {
                    updateStrikerAndTotals { player ->
                        player.copy(
                            runs = player.runs + additionalRuns, ballsFaced = player.ballsFaced + 1,
                            dots = if (additionalRuns == 0) player.dots + 1 else player.dots,
                            singles = if (additionalRuns == 1) player.singles + 1 else player.singles,
                            twos = if (additionalRuns == 2) player.twos + 1 else player.twos,
                            threes = if (additionalRuns == 3) player.threes + 1 else player.threes,
                            fours = if (additionalRuns == 4) player.fours + 1 else player.fours,
                            sixes = if (additionalRuns == 6) player.sixes + 1 else player.sixes
                        )
                    }
                    // A no-ball is not a legal delivery, so it must not add a partnership ball
                    // (that would push partnership balls past the innings ball count). The
                    // batsman's own ballsFaced does count it, which is the intended divergence.
                    updatePartnershipOnRuns(additionalRuns, isLegalDelivery = false, creditStriker = true)
                } else {
                    // Ball faced with nothing off the bat, so it is a dot for the batter.
                    updateStrikerAndTotals { player ->
                        player.copy(ballsFaced = player.ballsFaced + 1, dots = player.dots + 1)
                    }
                    updatePartnershipOnRuns(0, isLegalDelivery = false, creditStriker = false)
                }
                val sub = NoBallOutcomeHolders.noBallSubOutcome.value
                val ro = NoBallOutcomeHolders.noBallRunOutInput.value
                val bo = NoBallOutcomeHolders.noBallBoundaryOutInput.value
                when (sub) {
                    NoBallSubOutcome.BOUNDARY_OUT -> handleNoBallBoundaryOut(totalRuns, bo)
                    NoBallSubOutcome.RUN_OUT -> handleNoBallRunOut(totalRuns, ro)
                    else -> {
                        if (additionalRuns % 2 == 1 && !vm.showSingleSideLayout) swapStrike()
                        addDelivery("Nb+${totalRuns}", runs = totalRuns)
                        vm.toast("No ball! +$totalRuns runs")
                    }
                }
                NoBallOutcomeHolders.noBallSubOutcome.value = NoBallSubOutcome.NONE
                NoBallOutcomeHolders.noBallRunOutInput.value = null
                NoBallOutcomeHolders.noBallBoundaryOutInput.value = null
            }
            ExtraType.BYE, ExtraType.LEG_BYE -> {
                // Byes are not credited to the batter, so the ball faced counts as a dot.
                updateStrikerAndTotals { player ->
                    player.copy(ballsFaced = player.ballsFaced + 1, dots = player.dots + 1)
                }
                updateBowlerStats { player -> player.copy(ballsBowled = player.ballsBowled + 1) }
                vm.selection.incJokerBallIfBowledThisDelivery()
                vm.totalExtras += totalRuns
                updatePartnershipOnRuns(totalRuns, isLegalDelivery = true, creditStriker = false)
                addDelivery(if (extraType == ExtraType.BYE) "B+$totalRuns" else "Lb+$totalRuns", runs = totalRuns)
                vm.ballsInOver += 1
                if (vm.ballsInOver == 6) {
                    vm.selection.recordCurrentBowlerIfAny()
                    vm.currentOver += 1
                    vm.ballsInOver = 0
                    vm.previousBowlerName = vm.bowler?.name
                    vm.bowlerIndex = null
                    vm.currentBowlerSpell = 0
                    vm.midOverReplacementDueToJoker.value = false
                    if (!vm.showSingleSideLayout) swapStrike()
                    vm.showBowlerDialog = true
                    vm.toastLong("Over complete! Select new bowler")
                } else {
                    val baseByeRuns = if (extraType == ExtraType.BYE) matchSettings.byeRuns else matchSettings.legByeRuns
                    val additionalRuns2 = totalRuns - baseByeRuns
                    if (additionalRuns2 % 2 == 1 && !vm.showSingleSideLayout) swapStrike()
                }
                vm.toast("${extraType.displayName}! +$totalRuns runs")
            }
        }
        val totalAfter = vm.battingTeamPlayers.sumOf { it.runs } + vm.totalExtras
        vm.toast("${extraType.displayName}: +$totalRuns. Total: $totalAfter")
        vm.showExtrasDialog = false
    }

    private fun handleNoBallBoundaryOut(totalRuns: Int, bo: NoBallBoundaryOutInput?) {
        val outName = bo?.outBatterName?.trim()?.takeIf { it.isNotEmpty() } ?: vm.striker?.name ?: "Batsman"
        val outIndex = vm.battingTeamPlayers.indexOfFirst { it.name.equals(outName, ignoreCase = true) }
        val validIndex = if (outIndex != -1) outIndex else vm.strikerIndex
        if (validIndex == null) {
            vm.toastLong("Boundary out: could not resolve batter. Aborting wicket.")
            addDelivery("Nb+${totalRuns}", runs = totalRuns)
            return
        }
        pushSnapshot()
        val newList = vm.battingTeamPlayers.toMutableList()
        val dismissedPlayer = newList[validIndex]
        newList[validIndex] = dismissedPlayer.copy(
            isOut = true, ballsFaced = dismissedPlayer.ballsFaced + 1,
            dismissalType = WicketType.BOUNDARY_OUT, bowlerName = vm.bowler?.name
        )
        val outSnapshot = newList[validIndex].copy()
        vm.battingTeamPlayers = newList
        recordCompletedBatter(outSnapshot)
        // The no-ball itself was already applied to the stand by the NO_BALL branch above.
        endPartnershipAndRecordWicket(outSnapshot, isRunOut = false, isLegalDelivery = false)
        if (vm.strikerIndex == validIndex) {
            vm.strikerIndex = null; vm.selectingBatsman = 1; vm.pickerOtherEndName = vm.nonStriker?.name
        } else if (vm.nonStrikerIndex == validIndex) {
            vm.nonStrikerIndex = null; vm.selectingBatsman = 2; vm.pickerOtherEndName = vm.striker?.name
        } else {
            vm.strikerIndex = null; vm.selectingBatsman = 1; vm.pickerOtherEndName = vm.nonStriker?.name
        }
        vm.showBatsmanDialog = true
        addDelivery("Nb+${totalRuns}; BO(${outSnapshot.name})", highlight = true, runs = totalRuns)
        vm.toast("No ball + Boundary out! Total: ${vm.battingTeamPlayers.sumOf { it.runs } + vm.totalExtras}")
    }

    private fun handleNoBallRunOut(totalRuns: Int, ro: RunOutInput?) {
        vm.isNoBallRunOut = true
        if (ro == null) {
            addDelivery("Nb+${totalRuns}", runs = totalRuns)
            vm.toast("No ball! +$totalRuns runs")
            return
        }
        pushSnapshot()
        val name = ro.whoOut.trim()
        val byName = vm.battingTeamPlayers.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        val byStriker = if (vm.striker?.name.equals(name, true)) vm.strikerIndex else null
        val byNonStriker = if (vm.nonStriker?.name.equals(name, true)) vm.nonStrikerIndex else null
        val resolvedIndex = when {
            byName != -1 -> byName; byStriker != null -> byStriker; byNonStriker != null -> byNonStriker; else -> vm.strikerIndex
        }
        if (resolvedIndex == null) {
            vm.toastLong("Run out on No ball: player \"$name\" not found")
            addDelivery("Nb+${totalRuns}", runs = totalRuns)
            return
        }
        val updated = vm.battingTeamPlayers.toMutableList()
        updated[resolvedIndex] = updated[resolvedIndex].copy(isOut = true, dismissalType = WicketType.RUN_OUT)
        val outSnapshot = updated[resolvedIndex].copy()
        vm.battingTeamPlayers = updated
        recordCompletedBatter(outSnapshot)
        // The no-ball itself was already applied to the stand by the NO_BALL branch above.
        endPartnershipAndRecordWicket(outSnapshot, isRunOut = true, isLegalDelivery = false)
        if (ro.end == RunOutEnd.STRIKER_END) {
            if (vm.strikerIndex == resolvedIndex) vm.strikerIndex = null
            vm.selectingBatsman = 1; vm.pickerOtherEndName = vm.nonStriker?.name
        } else {
            if (vm.nonStrikerIndex == resolvedIndex) vm.nonStrikerIndex = null
            vm.selectingBatsman = 2; vm.pickerOtherEndName = vm.striker?.name
        }
        vm.showBatsmanDialog = true
        addDelivery("Nb+${totalRuns}; RO(${outSnapshot.name} @ ${if (ro.end == RunOutEnd.STRIKER_END) "S" else "NS"})", highlight = true, runs = totalRuns)
        vm.toast("No ball + Run out! Total: ${vm.battingTeamPlayers.sumOf { it.runs } + vm.totalExtras}")
    }

    // ── Quick Wide / No-Ball dialog confirmed ────────────────────────

    fun onQuickWideDialogConfirmed(totalRuns: Int) {
        pushSnapshot()
        updateBowlerStats { player -> player.copy(runsConceded = player.runsConceded + totalRuns) }
        vm.runsConcededInCurrentOver += totalRuns
        vm.totalExtras += totalRuns
        val baseWideRuns = matchSettings.wideRuns
        val additionalRuns = totalRuns - baseWideRuns
        updatePartnershipOnRuns(baseWideRuns, isLegalDelivery = false, creditStriker = false)
        if (additionalRuns > 0) updatePartnershipOnRuns(additionalRuns, isLegalDelivery = false, creditStriker = true)
        if (additionalRuns % 2 == 1 && !vm.showSingleSideLayout) swapStrike()
        addDelivery("Wd+${totalRuns}", runs = totalRuns)
        vm.toast("Wide! +$totalRuns runs. Total: ${vm.battingTeamPlayers.sumOf { it.runs } + vm.totalExtras}")
        vm.showQuickWideDialog = false
    }

    fun onStumpingOnWide(runs: Int) {
        vm.showQuickWideDialog = false
        vm.pendingWicketType = WicketType.STUMPED
        vm.pendingWideExtraType = ExtraType.LEG_SIDE_WIDE
        vm.pendingWideRuns = runs
        vm.showFielderSelectionDialog = true
    }

    fun onQuickNoBallConfirmed(totalRuns: Int) {
        pushSnapshot()
        val baseNoBallRuns = matchSettings.noballRuns
        val additionalRuns = totalRuns - baseNoBallRuns
        updateBowlerStats { player -> player.copy(runsConceded = player.runsConceded + totalRuns) }
        vm.totalExtras += baseNoBallRuns
        updatePartnershipOnRuns(baseNoBallRuns, isLegalDelivery = false, creditStriker = false)
        if (additionalRuns > 0) {
            updateStrikerAndTotals { player ->
                player.copy(
                    runs = player.runs + additionalRuns, ballsFaced = player.ballsFaced + 1,
                    dots = if (additionalRuns == 0) player.dots + 1 else player.dots,
                    singles = if (additionalRuns == 1) player.singles + 1 else player.singles,
                    twos = if (additionalRuns == 2) player.twos + 1 else player.twos,
                    threes = if (additionalRuns == 3) player.threes + 1 else player.threes,
                    fours = if (additionalRuns == 4) player.fours + 1 else player.fours,
                    sixes = if (additionalRuns == 6) player.sixes + 1 else player.sixes
                )
            }
            // Not a legal delivery - see the note in onExtraSelected's NO_BALL branch.
            updatePartnershipOnRuns(additionalRuns, isLegalDelivery = false, creditStriker = true)
        } else {
            // Ball faced with nothing off the bat, so it is a dot for the batter.
            updateStrikerAndTotals { player ->
                player.copy(ballsFaced = player.ballsFaced + 1, dots = player.dots + 1)
            }
            updatePartnershipOnRuns(0, isLegalDelivery = false, creditStriker = false)
        }
        if (additionalRuns % 2 == 1 && !vm.showSingleSideLayout) swapStrike()
        addDelivery("Nb+${totalRuns}", runs = totalRuns)
        vm.toast("No-ball! +$totalRuns runs. Total: ${vm.battingTeamPlayers.sumOf { it.runs } + vm.totalExtras}")
        vm.showQuickNoBallDialog = false
    }

    // ── Wicket handling ──────────────────────────────────────────────

    fun onWicketTypeSelected(wicketType: WicketType) {
        if (wicketType == WicketType.RUN_OUT) {
            vm.showWicketDialog = false; vm.showRunOutDialog = true; return
        }
        if (wicketType == WicketType.CAUGHT || wicketType == WicketType.STUMPED) {
            vm.pendingWicketType = wicketType; vm.showWicketDialog = false; vm.showFielderSelectionDialog = true; return
        }
        processDirectWicket(wicketType)
    }

    private fun processDirectWicket(wicketType: WicketType) {
        val dismissedIndex = vm.strikerIndex
        val jokerWasOut = vm.striker?.isJoker == true
        pushSnapshot()
        updateStrikerAndTotals { p ->
            p.copy(
                isOut = true, ballsFaced = p.ballsFaced + 1, dots = p.dots + 1,
                dismissalType = wicketType, bowlerName = vm.bowler?.name,
            )
        }
        val outSnapshot = dismissedIndex?.let { vm.battingTeamPlayers.getOrNull(it)?.copy() }
        outSnapshot?.let { endPartnershipAndRecordWicket(it, isRunOut = false) }
        if (outSnapshot != null && outSnapshot.isOut) recordCompletedBatter(outSnapshot)
        updateBowlerStats { player -> player.copy(wickets = player.wickets + 1, ballsBowled = player.ballsBowled + 1) }
        val curStrikerIndex = vm.strikerIndex
        val curNonStrikerIndex = vm.nonStrikerIndex
        val curStrikerName = vm.striker?.name
        val curNonStrikerName = vm.nonStriker?.name
        val jokerWasBowling = vm.bowler?.isJoker == true
        vm.toastLong("Wicket! ${vm.striker?.name} is ${wicketType.name.lowercase().replace("_", " ")}")
        handleJokerOutRemoval(jokerWasOut)
        handlePostWicketReplacement(dismissedIndex, curStrikerIndex, curNonStrikerIndex, curStrikerName, curNonStrikerName, jokerWasBowling, jokerWasOut)
        addDelivery("W", highlight = true, runs = 0)
        vm.ballsInOver += 1
        vm.selection.incJokerBallIfBowledThisDelivery()
        handleOverCompletionIfNeeded()
        vm.showWicketDialog = false
        val totalAfter = vm.battingTeamPlayers.sumOf { it.runs } + vm.totalExtras
        vm.toastLong("Wicket! ${outSnapshot?.name ?: "Batsman"} ${wicketType.name.lowercase().replace('_', ' ')}. Total: $totalAfter.")
    }

    fun onFielderSelectedForCaughtStumped(fielder: Player?) {
        fielder?.let { fieldingPlayer ->
            val fielderIndex = vm.bowlingTeamPlayers.indexOfFirst { it.name == fieldingPlayer.name }
            if (fielderIndex != -1) {
                val updated = vm.bowlingTeamPlayers.toMutableList()
                updated[fielderIndex] = when (vm.pendingWicketType) {
                    WicketType.CAUGHT -> updated[fielderIndex].copy(catches = updated[fielderIndex].catches + 1)
                    WicketType.STUMPED -> updated[fielderIndex].copy(stumpings = updated[fielderIndex].stumpings + 1)
                    else -> updated[fielderIndex]
                }
                vm.bowlingTeamPlayers = updated
            } else if (fieldingPlayer.isJoker) {
                // The joker isn't in the fielding side's list until they do something, so this
                // path adds them. Increment rather than assign: it used to set the counter to 1,
                // which quietly lost a joker's second catch of the innings.
                val jokerWithFielding = when (vm.pendingWicketType) {
                    WicketType.CAUGHT -> fieldingPlayer.copy(catches = fieldingPlayer.catches + 1)
                    WicketType.STUMPED -> fieldingPlayer.copy(stumpings = fieldingPlayer.stumpings + 1)
                    else -> fieldingPlayer
                }
                vm.bowlingTeamPlayers = (vm.bowlingTeamPlayers + jokerWithFielding).toMutableList()
            }
        }
        val isStumpingOnWide = vm.pendingWideExtraType != null
        val wicketType = vm.pendingWicketType!!
        val dismissedIndex = vm.strikerIndex
        val jokerWasOut = vm.striker?.isJoker == true
        pushSnapshot()
        updateStrikerAndTotals { p ->
            p.copy(
                isOut = true,
                ballsFaced = if (isStumpingOnWide) p.ballsFaced else p.ballsFaced + 1,
                dots = if (isStumpingOnWide) p.dots else p.dots + 1,
                dismissalType = wicketType, bowlerName = vm.bowler?.name, fielderName = fielder?.name
            )
        }
        if (isStumpingOnWide) {
            updateBowlerStats { player -> player.copy(runsConceded = player.runsConceded + vm.pendingWideRuns) }
            vm.runsConcededInCurrentOver += vm.pendingWideRuns
            vm.totalExtras += vm.pendingWideRuns
            addDelivery("Wd+${vm.pendingWideRuns} W", highlight = true, runs = vm.pendingWideRuns)
        }
        val outSnapshot = dismissedIndex?.let { vm.battingTeamPlayers.getOrNull(it)?.copy() }
        outSnapshot?.let {
            // A stumping off a wide is not a legal ball, and the wide runs belong to the stand.
            endPartnershipAndRecordWicket(
                it,
                isRunOut = false,
                runsOnDelivery = if (isStumpingOnWide) vm.pendingWideRuns else 0,
                isLegalDelivery = !isStumpingOnWide
            )
        }
        if (outSnapshot != null && outSnapshot.isOut) recordCompletedBatter(outSnapshot)
        updateBowlerStats { player -> player.copy(wickets = player.wickets + 1, ballsBowled = player.ballsBowled + 1) }
        val curStrikerIndex = vm.strikerIndex; val curNonStrikerIndex = vm.nonStrikerIndex
        val curStrikerName = vm.striker?.name; val curNonStrikerName = vm.nonStriker?.name
        val jokerWasBowling = vm.bowler?.isJoker == true
        val fielderCredit = if (fielder != null) " (${fielder.name})" else ""
        vm.toastLong("Wicket! ${vm.striker?.name} is ${wicketType.name.lowercase().replace("_", " ")}$fielderCredit")
        handleJokerOutRemoval(jokerWasOut)
        handlePostWicketReplacement(dismissedIndex, curStrikerIndex, curNonStrikerIndex, curStrikerName, curNonStrikerName, jokerWasBowling, jokerWasOut)
        if (!isStumpingOnWide) {
            addDelivery("W", highlight = true, runs = 0)
            vm.ballsInOver += 1
            vm.selection.incJokerBallIfBowledThisDelivery()
            handleOverCompletionIfNeeded()
        }
        vm.showFielderSelectionDialog = false; vm.pendingWicketType = null; vm.pendingWideExtraType = null; vm.pendingWideRuns = 0
        val totalAfter = vm.battingTeamPlayers.sumOf { it.runs } + vm.totalExtras
        vm.toastLong("Wicket! ${outSnapshot?.name ?: "Batsman"} ${wicketType.name.lowercase().replace('_', ' ')}. Total: $totalAfter.")
    }

    fun onFielderSelectedForRunOut(fielder: Player?) {
        val input = vm.pendingRunOutInput!!
        pushSnapshot()
        val runsCompleted = input.runsCompleted
        val outPlayerName = input.whoOut
        val outEnd = input.end
        val curStrikerIndex = vm.strikerIndex; val curNonStrikerIndex = vm.nonStrikerIndex
        val curStrikerName = vm.striker?.name; val curNonStrikerName = vm.nonStriker?.name
        updateStrikerAndTotals { p ->
            // The completed runs have to land in the shot buckets too, otherwise runs and
            // singles/twos/threes disagree and dots cannot be derived from them.
            p.copy(
                runs = p.runs + runsCompleted,
                ballsFaced = p.ballsFaced + 1,
                dots = p.dots + if (runsCompleted == 0) 1 else 0,
                singles = p.singles + if (runsCompleted == 1) 1 else 0,
                twos = p.twos + if (runsCompleted == 2) 1 else 0,
                threes = p.threes + if (runsCompleted == 3) 1 else 0,
                fours = p.fours + if (runsCompleted == 4) 1 else 0,
                sixes = p.sixes + if (runsCompleted == 6) 1 else 0,
            )
        }
        updateBowlerStats { b ->
            b.copy(runsConceded = b.runsConceded + runsCompleted, ballsBowled = if (vm.isNoBallRunOut) b.ballsBowled else b.ballsBowled + 1)
        }
        fielder?.let { fieldingPlayer ->
            val fidx = vm.bowlingTeamPlayers.indexOfFirst { it.name == fieldingPlayer.name }
            if (fidx != -1) {
                val upd = vm.bowlingTeamPlayers.toMutableList()
                upd[fidx] = upd[fidx].copy(runOuts = upd[fidx].runOuts + 1)
                vm.bowlingTeamPlayers = upd
            } else if (fieldingPlayer.isJoker) {
                vm.bowlingTeamPlayers = (
                    vm.bowlingTeamPlayers +
                        fieldingPlayer.copy(runOuts = fieldingPlayer.runOuts + 1)
                    ).toMutableList()
            }
        }
        val outIndex = vm.battingTeamPlayers.indexOfFirst { it.name.equals(outPlayerName, ignoreCase = true) }
        if (outIndex == -1) {
            vm.toastLong("Could not find player \"$outPlayerName\" in batting team")
            vm.showFielderSelectionDialog = false; vm.pendingRunOutInput = null; vm.pendingWicketType = null; return
        }
        val newBatting = vm.battingTeamPlayers.toMutableList()
        val wasJoker = newBatting[outIndex].isJoker
        val jokerWasBowling = vm.bowler?.isJoker == true
        newBatting[outIndex] = newBatting[outIndex].copy(isOut = true, dismissalType = WicketType.RUN_OUT, fielderName = fielder?.name)
        vm.battingTeamPlayers = newBatting
        val outSnapshot = vm.battingTeamPlayers[outIndex].copy()
        // Recorded before the joker index shuffle below, which can null striker/nonStriker and
        // would then stop the completed runs being credited to the stand.
        endPartnershipAndRecordWicket(
            outSnapshot,
            isRunOut = true,
            runsOnDelivery = runsCompleted,
            isLegalDelivery = !vm.isNoBallRunOut,
            creditStriker = true
        )
        recordCompletedBatter(outSnapshot)
        if (wasJoker) {
            vm.jokerOutInCurrentInnings = true
            if (vm.strikerIndex == outIndex) vm.strikerIndex = null
            if (vm.nonStrikerIndex == outIndex) vm.nonStrikerIndex = null
            else if (vm.nonStrikerIndex != null && vm.nonStrikerIndex!! > outIndex) vm.nonStrikerIndex = vm.nonStrikerIndex!! - 1
        }
        if ((outIndex == curStrikerIndex && outEnd == RunOutEnd.NON_STRIKER_END) ||
            (outIndex == curNonStrikerIndex && outEnd == RunOutEnd.STRIKER_END)) {
            vm.toastLong("Note: $outPlayerName position mismatch with selected end. Honouring scorer input.")
        }
        if (vm.strikerIndex == outIndex) vm.strikerIndex = null
        if (vm.nonStrikerIndex == outIndex) vm.nonStrikerIndex = null
        if (outIndex == curNonStrikerIndex && outEnd == RunOutEnd.STRIKER_END) {
            vm.nonStrikerIndex = curStrikerIndex; vm.strikerIndex = null
        } else if (outIndex == curStrikerIndex && outEnd == RunOutEnd.NON_STRIKER_END) {
            vm.strikerIndex = curNonStrikerIndex; vm.nonStrikerIndex = null
        }
        val availableBatsmenAfterRunOut = vm.battingTeamPlayers.count { !it.isOut }
        val jokerAvailForBat = vm.jokerPlayer != null && !vm.battingTeamPlayers.any { it.isJoker } && !vm.jokerOutInCurrentInnings
        val totalAvail = availableBatsmenAfterRunOut + if (jokerAvailForBat) 1 else 0
        when {
            totalAvail == 0 -> { vm.strikerIndex = null; vm.nonStrikerIndex = null }
            matchSettings.allowSingleSideBatting && totalAvail == 1 -> {
                if (jokerAvailForBat && availableBatsmenAfterRunOut == 0) {
                    vm.selectingBatsman = if (outEnd == RunOutEnd.STRIKER_END) 1 else 2
                    vm.pickerOtherEndName = if (outEnd == RunOutEnd.STRIKER_END) curNonStrikerName else curStrikerName
                    vm.showBatsmanDialog = true
                } else {
                    val lastBatsman = vm.battingTeamPlayers.indexOfFirst { !it.isOut }
                    if (outEnd == RunOutEnd.STRIKER_END) vm.strikerIndex = lastBatsman else vm.nonStrikerIndex = lastBatsman
                }
            }
            !matchSettings.allowSingleSideBatting && totalAvail == 1 -> { vm.strikerIndex = null; vm.nonStrikerIndex = null }
            else -> {
                if (outEnd == RunOutEnd.STRIKER_END) {
                    vm.selectingBatsman = 1; vm.pickerOtherEndName = curNonStrikerName; vm.showBatsmanDialog = true
                } else {
                    vm.selectingBatsman = 2; vm.pickerOtherEndName = curStrikerName; vm.showBatsmanDialog = true
                }
            }
        }
        val label = "${runsCompleted} + RO (${outPlayerName} @ ${if (outEnd == RunOutEnd.STRIKER_END) "S" else "NS"})"
        addDelivery(label, highlight = true, runs = runsCompleted)
        if (!vm.isNoBallRunOut) { vm.ballsInOver += 1; vm.selection.incJokerBallIfBowledThisDelivery() }
        handleOverCompletionIfNeeded()
        val fielderCredit = if (fielder != null) " (${fielder.name})" else ""
        vm.toastLong("Run out! $outPlayerName dismissed$fielderCredit. $runsCompleted run(s) recorded.")
        vm.showFielderSelectionDialog = false; vm.pendingRunOutInput = null; vm.pendingWicketType = null; vm.isNoBallRunOut = false
    }

    fun onWideWithStumping(extraType: ExtraType, baseRuns: Int) {
        vm.showExtrasDialog = false; vm.pendingWicketType = WicketType.STUMPED
        vm.pendingWideExtraType = extraType; vm.pendingWideRuns = baseRuns; vm.showFielderSelectionDialog = true
    }

    // ── Shared wicket helpers ────────────────────────────────────────

    internal fun recordCompletedBatter(outSnapshot: Player) {
        /** Replace an existing entry for this batter, or add one — name-keyed, as everywhere. */
        fun merged(into: List<Player>): List<Player> =
            if (into.none { it.name.equals(outSnapshot.name, true) }) {
                into + outSnapshot
            } else {
                into.map { if (it.name.equals(outSnapshot.name, true)) outSnapshot else it }
            }

        when {
            vm.currentInnings == 1 ->
                vm.completedBattersInnings1 = merged(vm.completedBattersInnings1).toMutableList()

            vm.currentInnings == 2 ->
                vm.completedBattersInnings2 = merged(vm.completedBattersInnings2).toMutableList()

            // A super over keeps its own bucket per innings, so the two match innings are
            // untouched by an eliminator dismissal.
            else -> vm.completedBattersSuperOver = vm.completedBattersSuperOver +
                (vm.currentInnings to merged(vm.completedBattersSuperOver[vm.currentInnings].orEmpty()))
        }
    }

    private fun handleJokerOutRemoval(jokerWasOut: Boolean) {
        if (!jokerWasOut) return
        vm.jokerOutInCurrentInnings = true
        val jokerBattingIndex = vm.battingTeamPlayers.indexOfFirst { it.isJoker }
        if (jokerBattingIndex != -1) {
            if (vm.strikerIndex == jokerBattingIndex) vm.strikerIndex = null
            if (vm.nonStrikerIndex == jokerBattingIndex) vm.nonStrikerIndex = null
            else if (vm.nonStrikerIndex != null && vm.nonStrikerIndex!! > jokerBattingIndex) vm.nonStrikerIndex = vm.nonStrikerIndex!! - 1
        }
    }

    private fun handlePostWicketReplacement(
        dismissedIndex: Int?, curStrikerIndex: Int?, curNonStrikerIndex: Int?,
        curStrikerName: String?, curNonStrikerName: String?,
        jokerWasBowling: Boolean, jokerWasOut: Boolean,
    ) {
        val availableBatsmenAfterWicket = vm.battingTeamPlayers.count { !it.isOut }
        val jokerAvailForBat = vm.jokerPlayer != null && !vm.battingTeamPlayers.any { it.isJoker } && !vm.jokerOutInCurrentInnings
        val totalAvail = availableBatsmenAfterWicket + if (jokerAvailForBat) 1 else 0
        val inningsWillEnd = if (matchSettings.allowSingleSideBatting) totalAvail == 0 else totalAvail < 2
        when {
            inningsWillEnd -> { vm.strikerIndex = null; vm.nonStrikerIndex = null }
            matchSettings.allowSingleSideBatting && totalAvail == 1 -> {
                if (jokerAvailForBat && availableBatsmenAfterWicket == 0) {
                    vm.selectingBatsman = if (dismissedIndex == curStrikerIndex) 1 else 2
                    vm.pickerOtherEndName = if (dismissedIndex == curStrikerIndex) curNonStrikerName else curStrikerName
                    vm.showBatsmanDialog = true
                } else {
                    val lastBatsman = vm.battingTeamPlayers.indexOfFirst { !it.isOut }
                    if (curStrikerIndex == dismissedIndex) vm.strikerIndex = lastBatsman else vm.nonStrikerIndex = lastBatsman
                }
            }
            !matchSettings.allowSingleSideBatting && totalAvail == 1 -> { vm.strikerIndex = null; vm.nonStrikerIndex = null }
            else -> {
                if (dismissedIndex == curStrikerIndex) {
                    vm.strikerIndex = null; vm.selectingBatsman = 1; vm.pickerOtherEndName = curNonStrikerName
                } else {
                    vm.nonStrikerIndex = null; vm.selectingBatsman = 2; vm.pickerOtherEndName = curStrikerName
                }
                vm.showBatsmanDialog = true
            }
        }
    }

    // ── Innings transition ───────────────────────────────────────────

    fun onInningsComplete() {
        closeScoringDialogs()
        when {
            vm.currentInnings == 1 -> finishFirstInnings()
            vm.currentInnings == 2 -> finishSecondInnings()
            // A super over runs in pairs: the odd innings sets, the even one chases.
            vm.currentInnings % 2 == 1 -> finishSuperOverFirstHalf()
            else -> finishSuperOverSecondHalf()
        }
    }

    private fun closeScoringDialogs() {
        vm.showBatsmanDialog = false; vm.showBowlerDialog = false; vm.showWicketDialog = false
        vm.showExtrasDialog = false; vm.showQuickWideDialog = false; vm.showQuickNoBallDialog = false
    }

    /** Everyone who batted in the innings just finished: those out, plus those still in. */
    private fun battersOfFinishedInnings(completed: List<Player>): List<Player> {
        val activeBatters = vm.battingTeamPlayers.filter { player ->
            player.ballsFaced > 0 || player.runs > 0 ||
                vm.strikerIndex?.let { vm.battingTeamPlayers[it].name == player.name } == true ||
                vm.nonStrikerIndex?.let { vm.battingTeamPlayers[it].name == player.name } == true
        }
        val completedNames = completed.map { it.name }.toSet()
        return (completed + activeBatters.filterNot { it.name in completedNames }.map { it.copy() })
            .distinctBy { it.name }
    }

    /** Everyone who bowled in the innings just finished. */
    private fun bowlersOfFinishedInnings(completed: List<Player>): List<Player> =
        (
            vm.bowlingTeamPlayers
                .filter { it.ballsBowled > 0 || it.wickets > 0 || it.runsConceded > 0 }
                .map { it.copy() } + completed
            ).distinctBy { it.name }

    private fun finishFirstInnings() {
        vm.firstInningsRuns = vm.calculatedTotalRuns
        vm.firstInningsWickets = vm.totalWickets
        vm.firstInningsOvers = vm.currentOver
        vm.firstInningsBalls = vm.ballsInOver
        vm.firstInningsBattingPlayersList = battersOfFinishedInnings(vm.completedBattersInnings1)
        vm.firstInningsBowlingPlayersList = bowlersOfFinishedInnings(vm.completedBowlersInnings1)
        vm.previousBowlerName = null
        saveInningsPartnershipsAndWickets()
        vm.showInningsBreakDialog = true
    }

    private fun finishSecondInnings() {
        // Snapshot the figures as well as the lists. The save used to read the second innings
        // live, which a super over would overwrite the moment it started.
        vm.secondInningsRuns = vm.calculatedTotalRuns
        vm.secondInningsWickets = vm.totalWickets
        vm.secondInningsBattingPlayers = battersOfFinishedInnings(vm.completedBattersInnings2)
        vm.secondInningsBowlingPlayers = bowlersOfFinishedInnings(vm.completedBowlersInnings2)
        saveInningsPartnershipsAndWickets()

        val levelScores = vm.secondInningsRuns == vm.firstInningsRuns
        if (levelScores && matchSettings.enableSuperOver) {
            // Nothing is saved yet: the match-complete dialog writes the match as soon as it
            // appears, so the eliminator has to be offered before it is ever shown.
            vm.showSuperOverOfferDialog = true
        } else {
            vm.showMatchCompleteDialog = true
        }
    }

    /** Records the innings just bowled in the eliminator, whichever half it was. */
    private fun recordSuperOverInnings() {
        vm.superOvers = vm.superOvers + SuperOverInnings(
            inning = vm.currentInnings,
            battingTeam = vm.battingTeamName,
            bowlingTeam = vm.bowlingTeamName,
            runs = vm.calculatedTotalRuns,
            wickets = vm.totalWickets,
            balls = vm.currentOver * 6 + vm.ballsInOver,
        )
        // An eliminator keeps no stands or fall-of-wickets rows — see
        // saveInningsPartnershipsAndWickets.
        closeCurrentPartnershipIfAny()
        vm.partnerships = emptyList()
        vm.fallOfWickets = emptyList()
    }

    private fun finishSuperOverFirstHalf() {
        recordSuperOverInnings()
        vm.showSuperOverIntervalDialog = true
    }

    private fun finishSuperOverSecondHalf() {
        recordSuperOverInnings()
        val pair = vm.superOvers.takeLast(2)
        val setter = pair.first()
        val chaser = pair.last()
        vm.superOverWinner = when {
            chaser.runs > setter.runs -> chaser.battingTeam
            chaser.runs < setter.runs -> setter.battingTeam
            // Level again. The scorer decides: another one, or leave it a tie.
            else -> null
        }
        if (vm.superOverWinner == null) {
            vm.showSuperOverOfferDialog = true
        } else {
            vm.showMatchCompleteDialog = true
        }
    }

    /** The scorer declined to break the tie — record it as one and finish the match. */
    fun declineSuperOver() {
        vm.superOverWinner = if (vm.superOvers.isEmpty()) null else "TIE"
        vm.showSuperOverOfferDialog = false
        vm.showSuperOverIntervalDialog = false
        vm.showMatchCompleteDialog = true
    }

    /** Starts the next super-over innings, whether it is the first or a repeat. */
    fun startSuperOver() = startInnings(vm.currentInnings + 1)

    /** Kept by name: the innings-break dialog and the scoring screen both call this. */
    fun onStartSecondInnings() = startInnings(2)

    /**
     * Begins innings [next] — the chase, or a half of a super over.
     *
     * The sides change ends entering an even innings only, which is what makes the eliminator work
     * out: the side that batted second in the match bats first in the super over, so 2 → 3 keeps
     * the same side in, and 3 → 4 swaps back. Figures are zeroed on both sides exactly as the
     * second innings always did, which is also right for a super over — the bowlers' over quotas
     * restart, as they should.
     */
    fun startInnings(next: Int) {
        if (swapSidesEnteringInnings(next)) {
            val tempPlayers = vm.battingTeamPlayers; val tempName = vm.battingTeamName
            vm.battingTeamPlayers = vm.bowlingTeamPlayers; vm.bowlingTeamPlayers = tempPlayers
            vm.battingTeamName = vm.bowlingTeamName; vm.bowlingTeamName = tempName
        }
        vm.battingTeamPlayers = vm.battingTeamPlayers.map { player ->
            player.copy(runs = 0, ballsFaced = 0, fours = 0, sixes = 0, isOut = false)
        }.toMutableList()
        vm.bowlingTeamPlayers = vm.bowlingTeamPlayers.map { player ->
            player.copy(wickets = 0, runsConceded = 0, ballsBowled = 0, isOut = false)
        }.toMutableList()
        // The joker is a shared stand-in, and is dropped at the second innings. By a super over
        // there is none left to place, which is the answer we want anyway: a player borrowed by
        // both sides has no business deciding an eliminator for one of them.
        vm.battingTeamPlayers = vm.battingTeamPlayers.filter { !it.isJoker }.toMutableList()
        vm.bowlingTeamPlayers = vm.bowlingTeamPlayers.filter { !it.isJoker }.toMutableList()
        vm.jokerOutInCurrentInnings = false
        vm.totalWickets = 0; vm.currentOver = 0; vm.ballsInOver = 0; vm.totalExtras = 0
        vm.previousBowlerName = null
        // An even innings chases what the odd one before it made.
        vm.runsToChase = if (next % 2 == 0) runsOfInnings(next - 1) else null
        when (next) {
            2 -> vm.completedBowlersInnings2 = mutableListOf()
            else -> {
                vm.completedBattersSuperOver = vm.completedBattersSuperOver - next
                vm.completedBowlersSuperOver = vm.completedBowlersSuperOver - next
            }
        }
        vm.currentBowlerSpell = 0; vm.strikerIndex = null; vm.nonStrikerIndex = null; vm.bowlerIndex = null
        vm.powerplayRunsInnings2 = 0; vm.powerplayDoublingDoneInnings2 = false
        vm.currentPartnershipRuns = 0; vm.currentPartnershipBalls = 0
        vm.currentPartnershipBatsman1Runs = 0; vm.currentPartnershipBatsman2Runs = 0
        vm.currentPartnershipBatsman1Balls = 0; vm.currentPartnershipBatsman2Balls = 0
        vm.currentPartnershipBatsman1Name = null; vm.currentPartnershipBatsman2Name = null
        vm.currentInnings = next
        vm.showInningsBreakDialog = false
        vm.showSuperOverOfferDialog = false; vm.showSuperOverIntervalDialog = false
        vm.showBatsmanDialog = true; vm.selectingBatsman = 1
    }

    /** What the side batting in [innings] made — the target for the innings that follows it. */
    private fun runsOfInnings(innings: Int): Int = when (innings) {
        1 -> vm.firstInningsRuns
        2 -> vm.secondInningsRuns
        else -> vm.superOvers.lastOrNull { it.inning == innings }?.runs ?: 0
    }

    fun checkPowerplayDoubling() {
        if (matchSettings.powerplayOvers > 0 && matchSettings.doubleRunsInPowerplay) {
            if (vm.currentOver == matchSettings.powerplayOvers) {
                if (vm.currentInnings == 1 && !vm.powerplayDoublingDoneInnings1) {
                    val runsToDouble = vm.calculatedTotalRuns - vm.powerplayRunsInnings1
                    if (runsToDouble > 0) {
                        vm.totalExtras += runsToDouble; vm.powerplayDoublingDoneInnings1 = true
                        vm.toastLong("Powerplay ended! $runsToDouble runs doubled! New total: ${vm.calculatedTotalRuns}")
                    }
                } else if (vm.currentInnings == 2 && !vm.powerplayDoublingDoneInnings2) {
                    val runsToDouble = vm.calculatedTotalRuns - vm.powerplayRunsInnings2
                    if (runsToDouble > 0) {
                        vm.totalExtras += runsToDouble; vm.powerplayDoublingDoneInnings2 = true
                        vm.toastLong("Powerplay ended! $runsToDouble runs doubled! New total: ${vm.calculatedTotalRuns}")
                    }
                }
            }
        }
    }
}
