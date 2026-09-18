package com.oreki.stumpd.domain.match

import com.oreki.stumpd.domain.model.DeliveryUI

/**
 * The ball-by-ball trail's `outcome` string, read and written in one place.
 *
 * The trail is a *display* record: each entry carries a short string like `"4"`, `"Wd+2"` or
 * `"Nb+1; RO(Kushal @ NS)"` plus the total runs. Four different places grew their own parser for
 * that string, and they disagreed — the live Overs tab tests for `"WD"` and `"NB"` in upper case
 * against outcomes written `"Wd+2"` and `"Nb+2"`, so every extra fell through to zero while the
 * header six lines above summed the stored `runs`. The same card showed two different totals for
 * the same over.
 *
 * Everything here is calibrated to what `ScoringEngine` actually writes, which in two places
 * differs from the laws of cricket — byes count as a legal ball *and* aren't charged to the
 * bowler, and a no-ball costs the striker a ball faced but the bowler nothing. Those are the
 * app's conventions, and a correction has to preserve them rather than "fix" them silently.
 */
object DeliveryOutcome {

    /** What kind of delivery an outcome string describes. */
    enum class Kind { OFF_THE_BAT, WIDE, NO_BALL, BYE, LEG_BYE }

    /** A wicket ball, whatever else happened on it. */
    fun isWicket(outcome: String): Boolean =
        outcome == "W" ||
            outcome.endsWith(" W") ||
            outcome.contains("RO(") ||
            outcome.contains("RO (") ||
            outcome.contains("BO(")

    fun kindOf(outcome: String): Kind = when {
        outcome.startsWith("Wd") -> Kind.WIDE
        outcome.startsWith("Nb") -> Kind.NO_BALL
        outcome.startsWith("Lb") -> Kind.LEG_BYE
        // After Lb, so "Lb+2" isn't read as a bye.
        outcome.startsWith("B+") -> Kind.BYE
        else -> Kind.OFF_THE_BAT
    }

    /**
     * Whether this ball counted towards the over.
     *
     * Wides and no-balls don't; byes and leg-byes do, which is what the scorer records.
     */
    fun isLegalBall(outcome: String): Boolean = when (kindOf(outcome)) {
        Kind.WIDE, Kind.NO_BALL -> false
        else -> true
    }

    /**
     * Total runs the delivery added to the team, extras included.
     *
     * [storedRuns] — `DeliveryUI.runs` — is authoritative and is used whenever it's present; the
     * parse is only a fallback for rows written before that field existed.
     */
    fun totalRuns(outcome: String, storedRuns: Int): Int {
        if (storedRuns > 0) return storedRuns
        if (outcome == "W") return 0
        outcome.toIntOrNull()?.let { return it }
        val digits = outcome.substringAfter('+', "").takeWhile { it.isDigit() }.toIntOrNull()
        return when (kindOf(outcome)) {
            Kind.OFF_THE_BAT -> outcome.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
            Kind.WIDE, Kind.NO_BALL -> digits ?: 1
            Kind.BYE, Kind.LEG_BYE -> digits ?: 0
        }
    }

    /**
     * Runs charged against the bowler's figures.
     *
     * Byes and leg-byes are the batting side's doing, and `ScoringEngine` doesn't charge them —
     * so moving an over to another bowler must not move them either.
     */
    fun runsChargedToBowler(outcome: String, storedRuns: Int): Int = when (kindOf(outcome)) {
        Kind.BYE, Kind.LEG_BYE -> 0
        else -> totalRuns(outcome, storedRuns)
    }

    /**
     * Runs credited to the batter.
     *
     * [noBallRuns] is the group's penalty for a no-ball (`MatchSettings.noballRuns`); anything
     * beyond it came off the bat.
     */
    fun batterRuns(outcome: String, storedRuns: Int, noBallRuns: Int): Int =
        when (kindOf(outcome)) {
            Kind.OFF_THE_BAT -> totalRuns(outcome, storedRuns)
            Kind.NO_BALL -> (totalRuns(outcome, storedRuns) - noBallRuns).coerceAtLeast(0)
            Kind.WIDE, Kind.BYE, Kind.LEG_BYE -> 0
        }

    /** Renders the string `ScoringEngine` would have written for this outcome. */
    fun render(kind: Kind, totalRuns: Int): String = when (kind) {
        Kind.OFF_THE_BAT -> totalRuns.toString()
        Kind.WIDE -> if (totalRuns > 0) "Wd+$totalRuns" else "Wd"
        Kind.NO_BALL -> "Nb+$totalRuns"
        Kind.BYE -> "B+$totalRuns"
        Kind.LEG_BYE -> "Lb+$totalRuns"
    }

    /** True when a boundary is worth marking in the over strip, matching the live scorer. */
    fun isHighlight(kind: Kind, totalRuns: Int): Boolean =
        kind == Kind.OFF_THE_BAT && (totalRuns == 4 || totalRuns == 6)

    /**
     * The dismissed batter's name, when the outcome spells it out.
     *
     * Run-outs and boundary-outs embed it — `"2 + RO (Kushal @ NS)"`, `"Nb+4; BO(Kushal)"` — and
     * a plain `"W"` doesn't. Renaming or substituting a player has to rewrite these, which the
     * existing adopt/merge traversal misses.
     */
    fun embeddedOutName(outcome: String): String? {
        val marker = listOf("RO (", "RO(", "BO(").firstOrNull { outcome.contains(it) } ?: return null
        val after = outcome.substringAfter(marker)
        val inner = after.substringBefore(')')
        val name = inner.substringBefore(" @ ").trim()
        return name.ifBlank { null }
    }

    /** [embeddedOutName] rewritten, leaving the rest of the string — including the end — alone. */
    fun withEmbeddedOutName(outcome: String, newName: String): String {
        val current = embeddedOutName(outcome) ?: return outcome
        return outcome.replaceFirst(current, newName)
    }
}

/**
 * Total runs this delivery added, preferring the stored value.
 *
 * The one function every screen should use for "runs in this over".
 */
fun DeliveryUI.effectiveRuns(): Int = DeliveryOutcome.totalRuns(outcome, runs)

/** Whether this delivery counted towards the over. */
fun DeliveryUI.isLegalBall(): Boolean = DeliveryOutcome.isLegalBall(outcome)

/** Whether a wicket fell on this delivery. */
fun DeliveryUI.isWicket(): Boolean = DeliveryOutcome.isWicket(outcome)
