package com.oreki.stumpd.domain.match

import com.oreki.stumpd.domain.model.DeliveryUI

/**
 * A super over, expressed as extra innings on top of the two the match already has.
 *
 * The match proper is innings 1 and 2. A super over is innings 3 and 4; if that is level too and
 * the scorer chooses to play another, it is 5 and 6, and so on. Nothing in the delivery trail
 * objects to that — [DeliveryUI.inning] is a plain Int, the stored trail is an opaque JSON blob and
 * the cloud copy keys each ball on its innings — so the whole eliminator costs no new innings
 * machinery, only the arithmetic here.
 *
 * Everything in this file is pure, so the live scoring screen, the saved scorecard, the spectator
 * view and the tests can all share one answer to "which innings is this, and who is batting".
 */

/** The first super-over innings. Innings 1 and 2 are the match. */
const val FIRST_SUPER_OVER_INNINGS = 3

/** One over a side. Not configurable: an eliminator is one over, as in the game. */
const val SUPER_OVER_OVERS = 1

/**
 * Two wickets end a super-over innings, so three batters can be involved.
 *
 * Clamp it to the squad before use — a three-a-side team with single-side batting off has only one
 * wicket to lose — with `maxWicketsForSquad`.
 */
const val SUPER_OVER_MAX_WICKETS = 2

fun isSuperOverInnings(inning: Int): Boolean = inning >= FIRST_SUPER_OVER_INNINGS

/**
 * The match proper: what batting and bowling figures, career stats, records and head-to-head are
 * computed over.
 *
 * Super-over runs and wickets don't count towards anyone's average in real cricket, and they
 * mustn't here either — one over of slogging would otherwise distort a strike rate built over a
 * season. Anything that walks the whole trail to derive a statistic should walk this instead.
 */
fun List<DeliveryUI>.mainMatchDeliveries(): List<DeliveryUI> =
    filter { !isSuperOverInnings(it.inning) }

/** Only the eliminator's balls, for the super-over card and the Overs tab. */
fun List<DeliveryUI>.superOverDeliveries(): List<DeliveryUI> =
    filter { isSuperOverInnings(it.inning) }

/** True for a ball bowled in a super over. */
fun DeliveryUI.isSuperOver(): Boolean = isSuperOverInnings(inning)

/**
 * Whether the sides change ends on the way into [innings].
 *
 * The side that batted second in the match bats first in the super over, which is the real rule and
 * also the convenient one: it makes "swap" true for even innings and false for odd. 1 → 2 swaps,
 * 2 → 3 keeps the same side batting, 3 → 4 swaps again.
 */
fun swapSidesEnteringInnings(innings: Int): Boolean = innings % 2 == 0

/**
 * Whether the side batting in [innings] is the one that batted first in the match.
 *
 * Needed because a saved match's `team1Name` means "batted first", and resume has to work out which
 * squad is at the crease from the innings number alone. Note it is not simply the parity of
 * [innings]: innings 1 and 2 are the wrong way round for that, which is exactly the bug a naive
 * `innings % 2 == 1` introduces.
 */
fun battingSideIsFirstInningsSide(innings: Int): Boolean = when (innings) {
    1 -> true
    2 -> false
    else -> innings % 2 == 0
}

/** "1st Innings", "2nd Innings", "Super Over", then "Super Over 2" for a repeat. */
fun inningsLabel(innings: Int): String = when {
    innings <= 1 -> "1st Innings"
    innings == 2 -> "2nd Innings"
    innings <= 4 -> "Super Over"
    else -> "Super Over ${(innings - 1) / 2}"
}

/**
 * Whether the innings in progress is over.
 *
 * The pure form of the live test, so it can be pinned by test and then extended. For the ordinary
 * two innings this is the same expression the scoring screen has always used: [runsToChase] is null
 * while a side is setting a target and [wicketCap] is null outside a super over, which switches
 * those two clauses off.
 */
fun inningsComplete(
    currentOver: Int,
    oversAllotted: Int,
    totalWickets: Int,
    wicketCap: Int?,
    runs: Int,
    runsToChase: Int?,
    availableBatsmen: Int,
    allowSingleSideBatting: Boolean,
): Boolean =
    currentOver >= oversAllotted ||
        (runsToChase != null && runs > runsToChase) ||
        (wicketCap != null && totalWickets >= wicketCap) ||
        if (allowSingleSideBatting) availableBatsmen == 0 else availableBatsmen < 2
