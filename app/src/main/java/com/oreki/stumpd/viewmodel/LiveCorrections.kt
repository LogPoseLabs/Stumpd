package com.oreki.stumpd.viewmodel

import com.oreki.stumpd.domain.match.DeliveryOutcome
import com.oreki.stumpd.domain.match.isLegalBall
import com.oreki.stumpd.domain.model.FallOfWicket
import com.oreki.stumpd.domain.model.Player
import com.oreki.stumpd.domain.model.WicketType

/**
 * The three mistakes worth fixing without leaving the scoring screen: the wrong batter given out,
 * the wrong bowler credited with the over, and the wrong runs on the last ball.
 *
 * Deliberately narrow. These work on the *live* ViewModel state, which has a different shape from
 * a saved [com.oreki.stumpd.domain.model.MatchHistory] — indices into the two squads, a partnership
 * in progress, an over half bowled — so the saved-match correction engine can't be reused as-is.
 * What they share is the discipline: snapshot first, so a correction is itself undoable by the Undo
 * button sitting right beside it; refuse rather than guess when the state doesn't support the fix;
 * and never touch a figure the mistake didn't affect. Anything they refuse is still correctable
 * from the full editor once the match is saved.
 *
 * Each returns a message: null when it worked, otherwise the reason it didn't, for the caller to
 * show.
 */
class LiveCorrections(
    private val vm: ScoringViewModel,
    private val engine: ScoringEngine,
) {

    // ── Wrong batter given out ──────────────────────────────────────────────────────

    /** The wicket a live fix can still reach: the most recent one in this innings. */
    fun lastWicket(): FallOfWicket? = vm.fallOfWickets.lastOrNull()

    /** How that wicket currently reads, for the dialog's "this is what you're changing" line. */
    fun lastWicketSummary(): String? {
        val fow = lastWicket() ?: return null
        val type = fow.dismissalType?.lowercase()?.replace('_', ' ') ?: "out"
        val by = listOfNotNull(
            fow.fielderName?.takeIf { it.isNotBlank() && fow.dismissalType == WicketType.CAUGHT.name }
                ?.let { "c $it" },
            fow.bowlerName?.takeIf { it.isNotBlank() }?.let { "b $it" },
        ).joinToString(" ")
        return "Wicket ${fow.wicketNumber}: ${fow.batsmanName} — " +
            (by.takeIf { it.isNotBlank() } ?: type)
    }

    /**
     * Who the last wicket could be moved to.
     *
     * Only the other batter from the stand that ended, and only while they're still at the crease.
     * That restriction is what keeps the fix cheap and safe: both names are already on the
     * archived partnership row, so the partnership needs no change at all and the team's wicket
     * count is untouched. Moving a wicket to a batter who wasn't in that stand would falsify the
     * partnership and the fall-of-wickets score, so it's left to the post-match editor, which
     * recomputes both.
     */
    fun swapCandidatesForLastWicket(): List<Player> {
        val fow = lastWicket() ?: return emptyList()
        val stand = vm.partnerships.lastOrNull() ?: return emptyList()
        val partners = setOf(stand.batsman1Name.lowercase(), stand.batsman2Name.lowercase())
        return listOfNotNull(vm.striker, vm.nonStriker)
            .filter { it.name.lowercase() in partners }
            .filter { !it.name.equals(fow.batsmanName, ignoreCase = true) }
            .distinctBy { it.name.lowercase() }
    }

    /**
     * Moves the last wicket from whoever is credited with it to [toName].
     *
     * Only the dismissal moves — type, bowler and fielder. Runs and balls faced stay where they
     * are, because the ball was bowled to whoever was on strike whatever the scorer then typed,
     * and the bowler's wicket and the fielder's catch stay too: the same ball dismissed somebody,
     * just not the batter written down.
     */
    fun reassignLastWicket(toName: String): String? {
        val fow = lastWicket() ?: return "No wicket has fallen in this innings yet."
        val target = swapCandidatesForLastWicket().firstOrNull { it.name.equals(toName, true) }
            ?: return "$toName wasn't in that partnership, so this has to wait for the full " +
                "editor after the match."

        val outIndex = vm.battingTeamPlayers.indexOfFirst { it.name.equals(fow.batsmanName, true) }
        val targetIndex = vm.battingTeamPlayers.indexOfFirst { it.name.equals(target.name, true) }
        if (outIndex == -1 || targetIndex == -1) {
            return "Couldn't find both batters in the squad — fix this from the full editor."
        }
        if (vm.battingTeamPlayers.count { it.name.equals(fow.batsmanName, true) } > 1 ||
            vm.battingTeamPlayers.count { it.name.equals(target.name, true) } > 1
        ) {
            // Every relationship in a match is keyed on the name, so two identical names cannot
            // be told apart. Refusing is the only honest answer.
            return "Two players in this squad share a name, so this can't be corrected safely."
        }

        engine.pushSnapshot()

        val squad = vm.battingTeamPlayers.toMutableList()
        val wronglyOut = squad[outIndex]
        squad[outIndex] = wronglyOut.copy(
            isOut = false, dismissalType = null, bowlerName = null, fielderName = null,
        )
        squad[targetIndex] = squad[targetIndex].copy(
            isOut = true,
            dismissalType = fow.dismissalType?.let { runCatching { WicketType.valueOf(it) }.getOrNull() },
            bowlerName = fow.bowlerName,
            fielderName = fow.fielderName,
        )
        vm.battingTeamPlayers = squad

        // The fall-of-wickets row keeps its score and over — the wicket fell when it fell.
        vm.fallOfWickets = vm.fallOfWickets.dropLast(1) + fow.copy(batsmanName = target.name)

        // The archived snapshot the scorecard reads for a batter who has finished: the wrongly-out
        // one goes back to the crease, so their row comes from the live squad again.
        val archive = { list: MutableList<Player> ->
            list.removeAll { it.name.equals(fow.batsmanName, true) }
            list.removeAll { it.name.equals(target.name, true) }
            list.add(squad[targetIndex].copy())
            list
        }
        if (vm.currentInnings == 1) {
            vm.completedBattersInnings1 = archive(vm.completedBattersInnings1.toMutableList())
        } else {
            vm.completedBattersInnings2 = archive(vm.completedBattersInnings2.toMutableList())
        }

        // Whoever's slot the newly-out batter occupied is now the returning batter's.
        when (targetIndex) {
            vm.strikerIndex -> vm.strikerIndex = outIndex
            vm.nonStrikerIndex -> vm.nonStrikerIndex = outIndex
        }

        // The trail spells the dismissed batter out inside run-out and boundary-out outcomes.
        val lastWicketBall = vm.allDeliveries.indexOfLast {
            it.inning == vm.currentInnings && DeliveryOutcome.embeddedOutName(it.outcome) != null
        }
        if (lastWicketBall != -1) {
            val ball = vm.allDeliveries[lastWicketBall]
            if (DeliveryOutcome.embeddedOutName(ball.outcome)
                    ?.equals(fow.batsmanName, true) == true
            ) {
                vm.allDeliveries[lastWicketBall] = ball.copy(
                    outcome = DeliveryOutcome.withEmbeddedOutName(ball.outcome, target.name)
                )
            }
        }

        vm.autoSaveMatch()
        return null
    }

    // ── Wrong bowler credited ───────────────────────────────────────────────────────

    /**
     * Every over of this innings that could be moved to another bowler.
     *
     * Not just the over in progress: a mis-typed bowler is usually noticed later — somebody looks
     * at the over strip in the third over and sees the first one credited to the wrong player — and
     * making them wait for the match to end to fix it is how a wrong scorecard gets saved. Any over
     * with balls in it and a single bowler recorded against them can be moved.
     */
    fun reassignableOvers(): List<Int> =
        vm.allDeliveries
            .filter { it.inning == vm.currentInnings }
            .map { it.over }
            .distinct()
            .sorted()
            .filter { bowlerOfOver(it) != null }

    /**
     * The over to offer first: the one in progress, or the one just finished if this one hasn't
     * started. Null when nothing has been bowled.
     */
    fun reassignableOver(): Int? {
        val thisOver = vm.currentOverNumber
        val overs = reassignableOvers()
        return when {
            thisOver in overs -> thisOver
            else -> overs.lastOrNull()
        }
    }

    /** Who the trail says bowled [over]. Null if more than one name appears, which a fix can't split. */
    fun bowlerOfOver(over: Int): String? {
        val names = vm.allDeliveries
            .filter { it.inning == vm.currentInnings && it.over == over }
            .mapNotNull { it.bowlerName?.takeIf { n -> n.isNotBlank() } }
            .distinct()
        return names.singleOrNull()
    }

    /** Bowlers the over could be given to: the bowling side, minus whoever already has it. */
    fun bowlerCandidates(over: Int): List<Player> {
        val current = bowlerOfOver(over)
        return vm.bowlingTeamPlayers.filterNot { it.name.equals(current, true) }
    }

    /**
     * Moves every ball of [over] from the bowler credited with it to [toName], figures and all.
     *
     * The deltas come from the delivery trail rather than from the bowler's running totals,
     * because the totals are cumulative across the innings and there is no per-over breakdown in
     * them. Byes and leg-byes move with the balls but not with the runs — the scorer's convention
     * here is that they're a legal ball not charged to the bowler — and a wicket only moves if the
     * bowler was credited with it in the first place, which run-outs and boundary-outs are not.
     */
    fun reassignOverBowler(over: Int, toName: String): String? {
        val balls = vm.allDeliveries.filter { it.inning == vm.currentInnings && it.over == over }
        if (balls.isEmpty()) return "There are no balls recorded in over $over."
        val fromName = bowlerOfOver(over)
            ?: return "Over $over has more than one bowler in it, so it can't be moved in one go."
        if (fromName.equals(toName, true)) return null

        val fromIndex = vm.bowlingTeamPlayers.indexOfFirst { it.name.equals(fromName, true) }
        val toIndex = vm.bowlingTeamPlayers.indexOfFirst { it.name.equals(toName, true) }
        if (fromIndex == -1 || toIndex == -1) {
            return "Couldn't find both bowlers in the fielding side."
        }
        if (vm.bowlingTeamPlayers.count { it.name.equals(fromName, true) } > 1 ||
            vm.bowlingTeamPlayers.count { it.name.equals(toName, true) } > 1
        ) {
            return "Two players in this side share a name, so this can't be corrected safely."
        }

        val legalBalls = balls.count { it.isLegalBall() }
        val runs = balls.sumOf { DeliveryOutcome.runsChargedToBowler(it.outcome, it.runs) }
        // Credited wickets only: the fall-of-wickets rows for this over that name this bowler.
        val creditedWickets = vm.fallOfWickets.count { fow ->
            fow.bowlerName?.equals(fromName, true) == true &&
                fow.overs > (over - 1).toDouble() && fow.overs <= over.toDouble() &&
                fow.dismissalType?.let { creditsBowler(it) } == true
        }
        // A maiden is only counted once the over is complete, so a part-over never carries one.
        val maiden = if (legalBalls >= 6 && runs == 0) 1 else 0

        engine.pushSnapshot()

        val side = vm.bowlingTeamPlayers.toMutableList()
        side[fromIndex] = side[fromIndex].copy(
            ballsBowled = (side[fromIndex].ballsBowled - legalBalls).coerceAtLeast(0),
            runsConceded = (side[fromIndex].runsConceded - runs).coerceAtLeast(0),
            wickets = (side[fromIndex].wickets - creditedWickets).coerceAtLeast(0),
            maidenOvers = (side[fromIndex].maidenOvers - maiden).coerceAtLeast(0),
        )
        side[toIndex] = side[toIndex].copy(
            ballsBowled = side[toIndex].ballsBowled + legalBalls,
            runsConceded = side[toIndex].runsConceded + runs,
            wickets = side[toIndex].wickets + creditedWickets,
            maidenOvers = side[toIndex].maidenOvers + maiden,
        )
        vm.bowlingTeamPlayers = side

        balls.forEach { ball ->
            val at = vm.allDeliveries.indexOf(ball)
            if (at != -1) vm.allDeliveries[at] = ball.copy(bowlerName = toName)
        }

        // The dismissals in that over, wherever they're recorded: the fall-of-wickets rows, the
        // batters still in the squad list, and the archived snapshots of those who are out.
        vm.fallOfWickets = vm.fallOfWickets.map { fow ->
            if (fow.bowlerName?.equals(fromName, true) == true &&
                fow.overs > (over - 1).toDouble() && fow.overs <= over.toDouble()
            ) fow.copy(bowlerName = toName) else fow
        }
        val outInThatOver = vm.fallOfWickets
            .filter { it.overs > (over - 1).toDouble() && it.overs <= over.toDouble() }
            .map { it.batsmanName.lowercase() }
            .toSet()
        val renameBowler = { p: Player ->
            if (p.name.lowercase() in outInThatOver && p.bowlerName?.equals(fromName, true) == true)
                p.copy(bowlerName = toName) else p
        }
        vm.battingTeamPlayers = vm.battingTeamPlayers.map(renameBowler).toMutableList()
        vm.completedBattersInnings1 = vm.completedBattersInnings1.map(renameBowler).toMutableList()
        vm.completedBattersInnings2 = vm.completedBattersInnings2.map(renameBowler).toMutableList()

        // If the over being fixed is the one in progress, the new name is also who's bowling it.
        if (over == vm.currentOverNumber) {
            vm.bowlerIndex = toIndex
            vm.currentBowlerSpell = vm.currentBowlerSpell.coerceAtLeast(1)
        }

        vm.autoSaveMatch()
        return null
    }

    /** Which dismissals put a wicket against the bowler's name, as the scoring screen credits them. */
    private fun creditsBowler(dismissalType: String): Boolean =
        when (runCatching { WicketType.valueOf(dismissalType) }.getOrNull()) {
            WicketType.BOWLED, WicketType.CAUGHT, WicketType.LBW,
            WicketType.STUMPED, WicketType.HIT_WICKET,
            -> true

            else -> false
        }

    // ── Wrong runs on the last ball ─────────────────────────────────────────────────

    /** How the last ball of this innings reads, for the dialog's heading. */
    fun lastBallSummary(): String? {
        val ball = vm.allDeliveries.lastOrNull { it.inning == vm.currentInnings } ?: return null
        // A wicket clears the striker as it's written, so there's often no name to name.
        val facing = ball.strikerName.takeIf { it.isNotBlank() }?.let { " to $it" }.orEmpty()
        return "Over ${ball.over}.${ball.ballInOver} — ${ball.outcome}$facing"
    }

    /**
     * Re-enters the last ball as [runs] off the bat.
     *
     * Undo-and-rescore rather than patch: every counter the ball touched — the batter's runs and
     * shot tally, the bowler's figures, the partnership, the over strip, the strike — is restored
     * by the snapshot and then re-applied by the ordinary scoring path, so there is no second
     * implementation of the arithmetic to keep in step. The cost is that undoing afterwards goes
     * back to before the original ball rather than to the mistake, which is the more useful place
     * to land anyway.
     */
    fun restateLastDelivery(runs: Int): String? {
        if (vm.allDeliveries.none { it.inning == vm.currentInnings }) {
            return "No ball has been bowled in this innings yet."
        }
        if (!engine.undoLastDelivery(silent = true)) return "That ball can't be taken back."
        vm.onRunScored(runs)
        return null
    }

    /**
     * Clears the last ball and leaves the keypad ready for it to be entered again.
     *
     * The way out for anything the fixed choices don't cover — an extra typed as runs, a wicket
     * that wasn't, a no-ball off which four were also run.
     */
    fun clearLastDelivery(): String? {
        if (vm.allDeliveries.none { it.inning == vm.currentInnings }) {
            return "No ball has been bowled in this innings yet."
        }
        if (!engine.undoLastDelivery(silent = true)) return "That ball can't be taken back."
        return null
    }
}
