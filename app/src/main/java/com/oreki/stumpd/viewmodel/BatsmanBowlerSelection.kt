package com.oreki.stumpd.viewmodel

import com.oreki.stumpd.domain.model.Player

/**
 * Batsman/bowler selection, retirement, and joker helpers extracted from ScoringViewModel.
 */
class BatsmanBowlerSelection(private val vm: ScoringViewModel) {

    private val matchSettings get() = vm.matchSettings

    // ── Bowler recording & joker helpers ─────────────────────────────

    fun recordCurrentBowlerIfAny() {
        val idx = vm.bowlerIndex
        if (idx != null) {
            if (vm.runsConcededInCurrentOver == 0 && vm.ballsInOver == 6) {
                vm.bowlingTeamPlayers = vm.bowlingTeamPlayers.mapIndexed { i, player ->
                    if (i == idx) player.copy(maidenOvers = player.maidenOvers + 1) else player
                }.toMutableList()
            }
            val p = vm.bowlingTeamPlayers.getOrNull(idx)
            if (p != null && (p.ballsBowled > 0 || p.wickets > 0 || p.runsConceded > 0)) {
                /** Replace this bowler's entry if it's there, else append. Name-keyed. */
                fun merged(into: List<Player>): List<Player> {
                    val exists = into.indexOfFirst { it.name.equals(p.name, true) }
                    return if (exists == -1) {
                        into + p.copy()
                    } else {
                        into.mapIndexed { i, old -> if (i == exists) p.copy() else old }
                    }
                }

                when {
                    vm.currentInnings == 1 ->
                        vm.completedBowlersInnings1 = merged(vm.completedBowlersInnings1).toMutableList()

                    vm.currentInnings == 2 ->
                        vm.completedBowlersInnings2 = merged(vm.completedBowlersInnings2).toMutableList()

                    // A super over's bowler belongs to that innings, not to the chase.
                    else -> vm.completedBowlersSuperOver = vm.completedBowlersSuperOver +
                        (vm.currentInnings to merged(vm.completedBowlersSuperOver[vm.currentInnings].orEmpty()))
                }
            }
        }
        vm.runsConcededInCurrentOver = 0
    }

    fun jokerBallsBowledThisInningsRaw(): Int =
        if (vm.currentInnings == 1) vm.jokerBallsBowledInnings1 else vm.jokerBallsBowledInnings2

    fun jokerOversBowledThisInnings(): Double {
        val b = jokerBallsBowledThisInningsRaw()
        return (b / 6) + (b % 6) * 0.1
    }

    fun incJokerBallIfBowledThisDelivery() {
        val currentBowler = vm.bowler
        if (currentBowler?.isJoker == true) {
            if (vm.currentInnings == 1) vm.jokerBallsBowledInnings1++ else vm.jokerBallsBowledInnings2++
        }
    }

    fun ensureJokerStatsAppliedOnAdd() {
        if (vm.jokerPlayer == null) return
        val exists = vm.bowlingTeamPlayers.indexOfFirst { it.isJoker }
        if (exists == -1) return
        val balls = if (vm.currentInnings == 1) vm.jokerBallsBowledInnings1 else vm.jokerBallsBowledInnings2
        if (balls <= 0) return
        val list = vm.bowlingTeamPlayers.toMutableList()
        val j = list[exists]
        list[exists] = j.copy(ballsBowled = balls)
        vm.bowlingTeamPlayers = list
    }

    // ── Batsman selection ────────────────────────────────────────────

    fun onBatsmanSelected(player: Player) {
        val jokerAvailInsideDialog = vm.jokerPlayer != null &&
            !vm.battingTeamPlayers.any { it.isJoker } && !vm.jokerPlayer!!.isOut
        val availableBatsmenCount = vm.battingTeamPlayers.count { !it.isOut || it.isRetired }

        if (vm.selectingBatsman == 1) {
            handleBatsmanSelection(player, isStriker = true)
            if (!matchSettings.allowSingleSideBatting && vm.nonStrikerIndex == null &&
                (availableBatsmenCount + if (jokerAvailInsideDialog) 1 else 0) > 1
            ) {
                vm.selectingBatsman = 2
            } else {
                vm.showBatsmanDialog = false
                handlePendingAfterBatsmanPick()
            }
        } else {
            handleBatsmanSelection(player, isStriker = false)
            vm.showBatsmanDialog = false
            handlePendingAfterBatsmanPick()
        }
    }

    private fun handleBatsmanSelection(player: Player, isStriker: Boolean) {
        if (player.isJoker) {
            val jokerBowlingIndex = vm.bowlingTeamPlayers.indexOfFirst { it.isJoker }
            if (jokerBowlingIndex != -1) {
                if (vm.bowlerIndex == jokerBowlingIndex) {
                    recordCurrentBowlerIfAny()
                    vm.bowlerIndex = null
                    vm.currentBowlerSpell = 0
                    if (vm.ballsInOver > 0) {
                        vm.midOverReplacementDueToJoker.value = true
                        vm.toastLong("Joker switched to bat. Select a new bowler to complete the over.")
                        vm.showBowlerDialog = true
                    }
                }
                val newBowlingList = vm.bowlingTeamPlayers.toMutableList()
                newBowlingList.removeAt(jokerBowlingIndex)
                vm.bowlingTeamPlayers = newBowlingList
                if (vm.previousBowlerName == vm.jokerName) vm.previousBowlerName = null
            }
            if (!vm.battingTeamPlayers.any { it.isJoker }) {
                val newList = vm.battingTeamPlayers.toMutableList()
                newList.add(vm.jokerPlayer!!.copy())
                vm.battingTeamPlayers = newList
                if (isStriker) vm.strikerIndex = vm.battingTeamPlayers.size - 1 else vm.nonStrikerIndex = vm.battingTeamPlayers.size - 1
            } else {
                val idx = vm.battingTeamPlayers.indexOfFirst { it.isJoker }
                if (isStriker) vm.strikerIndex = idx else vm.nonStrikerIndex = idx
            }
            vm.jokerOutInCurrentInnings = false
        } else {
            val playerIndex = vm.battingTeamPlayers.indexOfFirst { it.name.trim().equals(player.name.trim(), ignoreCase = true) }
            if (isStriker) vm.strikerIndex = playerIndex else vm.nonStrikerIndex = playerIndex
            if (playerIndex >= 0 && vm.battingTeamPlayers[playerIndex].isRetired) {
                val unretiredPlayer = vm.battingTeamPlayers[playerIndex].copy(isRetired = false)
                vm.battingTeamPlayers = vm.battingTeamPlayers.toMutableList().apply { this[playerIndex] = unretiredPlayer }
                vm.toast("${unretiredPlayer.name} returns to bat")
            }
        }
    }

    private fun handlePendingAfterBatsmanPick() {
        if (vm.pendingSwapAfterBatsmanPick) {
            if (!vm.showSingleSideLayout) vm.engine.swapStrike()
            vm.pendingSwapAfterBatsmanPick = false
        }
        if (vm.pendingBowlerDialogAfterBatsmanPick) {
            vm.showBowlerDialog = true
            vm.pendingBowlerDialogAfterBatsmanPick = false
        }
    }

    // ── Bowler selection ─────────────────────────────────────────────

    fun onBowlerSelected(player: Player, overrideToCompleteOverAllowed: Boolean) {
        fun norm(s: String): String = s.trim().replace(Regex("\\s+"), " ")

        // The picker already hides the joker when bowling is disabled; this is the backstop so
        // no other entry point (resume, spectator, a future shortcut) can slip past the rule.
        if (player.isJoker && !matchSettings.jokerCanBowl) {
            vm.toastLong("Joker is not allowed to bowl in this match.")
            return
        }
        val ballsSoFar = if (player.isJoker) jokerBallsBowledThisInningsRaw()
        else {
            val idxInBowling = vm.bowlingTeamPlayers.indexOfFirst { norm(it.name).equals(norm(player.name), ignoreCase = true) }
            vm.bowlingTeamPlayers.getOrNull(idxInBowling)?.ballsBowled ?: 0
        }
        val capOvers = if (player.isJoker) matchSettings.jokerMaxOvers else matchSettings.maxOversPerBowler
        val capBalls = capOvers * 6
        val remainingBalls = (capBalls - ballsSoFar).coerceAtLeast(0)

        run {
            val currentIdx = vm.bowlerIndex
            if (vm.ballsInOver > 0 && currentIdx != null) {
                val current = vm.bowlingTeamPlayers.getOrNull(currentIdx)
                if (current != null) {
                    val same = (!player.isJoker && norm(current.name).equals(norm(player.name), ignoreCase = true)) ||
                        (player.isJoker && current.isJoker)
                    if (same) { vm.showBowlerDialog = false; return }
                }
            }
        }

        if (vm.ballsInOver == 0 && remainingBalls < 6) {
            val canOverride = overrideToCompleteOverAllowed && !player.isJoker
            if (!canOverride) {
                val who = if (player.isJoker) "Joker" else player.name
                vm.toastLong("$who cannot start a new over (only $remainingBalls legal ball${if (remainingBalls != 1) "s" else ""} remaining; needs 6).")
                return
            } else {
                vm.toast("${player.name} will exceed max-over cap only to complete this over.")
            }
        }

        if (vm.ballsInOver > 0) {
            if (!vm.midOverReplacementDueToJoker.value) {
                vm.toastLong("Mid-over bowler change is only allowed when replacing the Joker.")
                return
            }
            val need = 6 - vm.ballsInOver
            if (remainingBalls < need) {
                val who = if (player.isJoker) "Joker" else player.name
                vm.toastLong("$who cannot replace mid-over (needs $need balls, only $remainingBalls remaining).")
                return
            }
        }

        if (player.isJoker) {
            if (!vm.bowlingTeamPlayers.any { it.isJoker } && vm.jokerPlayer != null) {
                vm.bowlingTeamPlayers = (vm.bowlingTeamPlayers + vm.jokerPlayer!!.copy()).toMutableList()
            }
            ensureJokerStatsAppliedOnAdd()
            vm.bowlerIndex = vm.bowlingTeamPlayers.indexOfFirst { it.isJoker }
        } else {
            vm.bowlerIndex = vm.bowlingTeamPlayers.indexOfFirst { norm(it.name).equals(norm(player.name), ignoreCase = true) }
        }
        vm.currentBowlerSpell = 1
        vm.showBowlerDialog = false
        vm.midOverReplacementDueToJoker.value = false
    }

    // ── Retirement ───────────────────────────────────────────────────

    fun onRetireBatsman(position: Int) {
        vm.retiringPosition = position
        vm.showRetirementDialog = false
        val batsmanIndex = if (position == 1) vm.strikerIndex else vm.nonStrikerIndex
        batsmanIndex?.let { idx ->
            val updatedPlayer = vm.battingTeamPlayers[idx].copy(isRetired = true)
            vm.battingTeamPlayers = vm.battingTeamPlayers.toMutableList().apply { this[idx] = updatedPlayer }
            if (position == 1) vm.strikerIndex = null else vm.nonStrikerIndex = null
            vm.selectingBatsman = position
            vm.showBatsmanDialog = true
            vm.toast("${updatedPlayer.name} retired")
        }
    }
}
