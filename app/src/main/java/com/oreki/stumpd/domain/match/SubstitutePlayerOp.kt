package com.oreki.stumpd.domain.match

import com.oreki.stumpd.data.sync.MatchGroupAdoption
import com.oreki.stumpd.data.sync.MergeDestPlayer
import com.oreki.stumpd.domain.model.MatchHistory

/**
 * "That was the wrong person entirely" — one player's figures belong to somebody else.
 *
 * Happens when a match is scored with a stand-in name, or when two players in a group share a
 * nickname and the wrong one gets picked at team selection. The figures are right; the identity
 * isn't.
 *
 * A player's identity is scattered across a saved match — an id on the stats rows, and a *name*
 * on dismissal credits, partnerships, fall of wickets, every delivery (including inside run-out
 * outcome strings), the joker, both captains and the award — so this delegates to
 * [MatchGroupAdoption.remapNames], the single traversal that knows all of them. Nothing numeric
 * changes; only who owns it.
 */
object SubstitutePlayerOp {

    fun apply(
        match: MatchHistory,
        correction: MatchCorrection.SubstitutePlayer,
    ): CorrectionOutcome {
        val from = MatchGroupAdoption.normalizePlayerName(correction.fromName)
        val to = MatchGroupAdoption.normalizePlayerName(correction.toName)

        if (from.isBlank() || to.isBlank()) {
            return reject("EMPTY_NAME", "Both the old and the new player must be named.")
        }
        if (from == to) {
            return reject("NO_OP", "${correction.toName} is already the player on the scorecard.")
        }

        val names = MatchGroupAdoption.collectPlayerNames(match)
            .map { MatchGroupAdoption.normalizePlayerName(it) }
            .toSet()

        if (from !in names) {
            return reject(
                "PLAYER_NOT_IN_MATCH",
                "${correction.fromName} doesn't appear anywhere in this match.",
            )
        }
        if (to in names) {
            // Two scorecard lines would have to be added together, and their dismissals,
            // partnerships and spells can't be merged without inventing data.
            return reject(
                "WOULD_MERGE_PLAYERS",
                "${correction.toName} already played in this match. Substituting would merge two " +
                    "players' figures, which this can't do safely.",
            )
        }

        val substituted = MatchGroupAdoption.remapNames(match) { name ->
            if (MatchGroupAdoption.normalizePlayerName(name) == from) {
                MergeDestPlayer(id = correction.toPlayerId, name = correction.toName)
            } else {
                null
            }
        }

        return CorrectionOutcome.Applied(before = match, after = substituted)
    }

    private fun reject(code: String, message: String) =
        CorrectionOutcome.Rejected(listOf(CorrectionError(code, message)))
}
