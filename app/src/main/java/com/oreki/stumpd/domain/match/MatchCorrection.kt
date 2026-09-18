package com.oreki.stumpd.domain.match

import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.model.MatchSettings
import com.oreki.stumpd.domain.model.WicketType

/**
 * A single, deliberate change to a finished match.
 *
 * These exist because scoring mistakes are inevitable — the wrong batter gets marked out, a ball
 * is credited to the wrong bowler — and until now the only remedy was editing the database by
 * hand. Each op is a pure transformation of [MatchHistory] so it can be tested without a
 * database, previewed before it's written, and re-applied if the storage underneath changes.
 *
 * Ops address *events*, not list positions: a dismissal is identified by its wicket number, which
 * is the only handle that survives the rows being rewritten.
 */
sealed interface MatchCorrection {

    /**
     * Moves a wicket to the batter who was actually dismissed, and/or corrects how.
     *
     * The commonest mistake in the app: two batters are at the crease and the wrong one is
     * tapped. Deliberately keeps the wicket where it fell — the score and over are right, only
     * the name and the manner of dismissal were wrong.
     */
    data class ReassignDismissal(
        val innings: Int,
        val wicketNumber: Int,
        /** The batter who was really out. Pass the current one to change only the manner. */
        val outBatterName: String,
        val dismissalType: WicketType,
        val bowlerName: String?,
        val fielderName: String?,
    ) : MatchCorrection

    /**
     * Hands one player's whole match to someone else.
     *
     * For when the figures are right but the name isn't — a stand-in was entered, or the wrong
     * member of a group with shared nicknames was picked at team selection.
     */
    /**
     * Moves an over — or a single ball — to the bowler who actually bowled it.
     *
     * [over] is 1-based, matching the ball-by-ball record (the live scorer counts overs from zero
     * internally, so this is the trail's numbering, not the ViewModel's). [ballInOver] null means
     * the whole over.
     */
    data class ReassignBowler(
        val innings: Int,
        val over: Int,
        val ballInOver: Int?,
        val toBowlerName: String,
    ) : MatchCorrection

    /**
     * Corrects what a single delivery was worth.
     *
     * [over] and [ballInOver] address the ball as the ball-by-ball record numbers it (both
     * 1-based). [newKind] says whether it was off the bat or an extra, and [newTotalRuns] is what
     * the delivery added to the team in total, penalty included.
     */
    data class AmendBall(
        val innings: Int,
        val over: Int,
        val ballInOver: Int,
        val newKind: DeliveryOutcome.Kind,
        val newTotalRuns: Int,
    ) : MatchCorrection

    data class SubstitutePlayer(
        val fromName: String,
        val toPlayerId: String,
        val toName: String,
    ) : MatchCorrection
}

/** Why a correction can't be applied. Rejections are absolute; the commit doesn't happen. */
data class CorrectionError(val code: String, val message: String)

/** Something the user should see before committing, but which doesn't block it. */
data class CorrectionWarning(val code: String, val message: String)

sealed interface CorrectionOutcome {
    data class Applied(
        val before: MatchHistory,
        val after: MatchHistory,
        val warnings: List<CorrectionWarning> = emptyList(),
        /** What changed, in the scorecard's own words, for the confirmation step. */
        val diff: MatchCorrectionDiff.Diff = MatchCorrectionDiff.Diff(emptyList(), false, false),
        /** Inconsistencies the match already had and this correction didn't address. */
        val preExistingProblems: List<MatchInvariants.Violation> = emptyList(),
        /** Inconsistencies this correction cleared up. */
        val resolvedProblems: List<MatchInvariants.Violation> = emptyList(),
    ) : CorrectionOutcome

    data class Rejected(val errors: List<CorrectionError>) : CorrectionOutcome
}

/**
 * Applies corrections and re-derives everything that follows from them.
 *
 * Nothing here touches a database: the caller loads a match, applies, shows the user what would
 * change, and only then writes. That split is the whole safety story for a feature that can
 * rewrite a result.
 */
object MatchCorrectionEngine {

    fun apply(
        match: MatchHistory,
        corrections: List<MatchCorrection>,
        settings: MatchSettings,
    ): CorrectionOutcome {
        var working = match
        val warnings = mutableListOf<CorrectionWarning>()

        corrections.forEach { correction ->
            when (val step = applyOne(working, correction, settings)) {
                is CorrectionOutcome.Rejected -> return step
                is CorrectionOutcome.Applied -> {
                    working = step.after
                    warnings += step.warnings
                }
            }
        }

        val after = MatchRecompute.recompute(working, settings)

        // Judged against where the match started: a correction is refused for what it breaks,
        // never for what it inherited. Plenty of older matches already fail one of these, and
        // refusing them would lock the feature out of exactly the matches that need it.
        val introduced = MatchInvariants.introduced(match, after, settings)
        if (introduced.isNotEmpty()) {
            return CorrectionOutcome.Rejected(
                introduced.map { CorrectionError("INVARIANT_${it.code}", it.message) }
            )
        }

        val remaining = MatchInvariants.check(after, settings)
        return CorrectionOutcome.Applied(
            before = match,
            after = after,
            warnings = warnings,
            diff = MatchCorrectionDiff.of(match, after),
            preExistingProblems = remaining,
            resolvedProblems = MatchInvariants.resolved(match, after, settings),
        )
    }

    private fun applyOne(
        match: MatchHistory,
        correction: MatchCorrection,
        settings: MatchSettings,
    ): CorrectionOutcome = when (correction) {
        is MatchCorrection.ReassignDismissal -> ReassignDismissalOp.apply(match, correction, settings)
        is MatchCorrection.AmendBall -> AmendBallOp.apply(match, correction, settings)
        is MatchCorrection.ReassignBowler -> ReassignBowlerOp.apply(match, correction)
        is MatchCorrection.SubstitutePlayer -> SubstitutePlayerOp.apply(match, correction)
    }
}
