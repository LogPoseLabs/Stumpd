package com.oreki.stumpd.data.repository

import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.oreki.stumpd.data.local.entity.MatchCorrectionLogEntity
import com.oreki.stumpd.data.local.db.StumpdDb
import com.oreki.stumpd.domain.match.CorrectionError
import com.oreki.stumpd.domain.match.CorrectionOutcome
import com.oreki.stumpd.domain.match.MatchCorrection
import com.oreki.stumpd.domain.match.MatchCorrectionEngine
import com.oreki.stumpd.domain.model.MatchSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loading a match, working out what a correction would do to it, and — only when asked twice —
 * writing it back.
 *
 * The split is the point. [preview] is read-only and cheap, so the user can see the consequences
 * (including a changed result or a reshuffled award) before committing. [commit] re-reads the
 * match under an optimistic lock and writes the whole graph as the new truth.
 *
 * Two things this deliberately doesn't do. It doesn't trigger a cloud sync — the caller does that
 * after a successful commit, the way the adopt and merge flows already do, because the sync
 * manager belongs to the UI's entry point rather than to a repository. And it doesn't decide
 * whether the *user* is allowed to correct anything; [editability] reports what the sync rules
 * imply so the UI can warn, but the passcode gate lives in the UI.
 */
class MatchCorrectionRepository(
    private val db: StumpdDb,
    private val matchRepository: MatchRepository,
    private val groupRepository: GroupRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val TAG = "MatchCorrection"
    }

    /** What happens to a correction once it's written. */
    sealed interface CommitResult {
        data class Committed(val outcome: CorrectionOutcome.Applied) : CommitResult

        data class Rejected(val errors: List<CorrectionError>) : CommitResult
    }

    /**
     * Whether a correction to this match will survive.
     *
     * A grouped match can only be uploaded by the group's owner, so a correction made by anyone
     * else is right locally and then replaced by the owner's copy at the next sync. That's worth
     * knowing *before* typing a password and re-scoring a wicket.
     */
    sealed interface Editability {
        data object Fine : Editability

        /**
         * This device can't upload the match, so it can't correct it either.
         *
         * A local-only correction is worse than none: it is right on this phone, wrong everywhere
         * else in the group, and then silently replaced by the owner's copy at the next sync —
         * leaving the person who made it sure they fixed something. The owner makes the
         * correction, or nobody does.
         */
        data class NotOurs(val groupName: String) : Editability
        data class NoSuchMatch(val matchId: String) : Editability
    }

    suspend fun editability(matchId: String): Editability = withContext(ioDispatcher) {
        val match = db.matchDao().getById(matchId) ?: return@withContext Editability.NoSuchMatch(matchId)
        val groupId = match.groupId ?: return@withContext Editability.Fine
        val group = groupRepository.getGroupById(groupId) ?: return@withContext Editability.Fine
        if (group.isOwner) Editability.Fine else Editability.NotOurs(group.name)
    }

    /**
     * What [corrections] would do, without writing anything.
     *
     * Also returns the match's `updatedAt`, which [commit] needs as an optimistic lock: a cloud
     * download between preview and commit would otherwise let the user confirm a diff computed
     * against a match that no longer exists in that form.
     */
    suspend fun preview(
        matchId: String,
        corrections: List<MatchCorrection>,
    ): Pair<CorrectionOutcome, Long> = withContext(ioDispatcher) {
        val row = db.matchDao().getById(matchId)
            ?: return@withContext notFound(matchId) to 0L
        val match = matchRepository.getMatchWithStats(matchId)
            ?: return@withContext notFound(matchId) to row.updatedAt

        MatchCorrectionEngine.apply(
            match = match,
            corrections = corrections,
            settings = match.matchSettings ?: MatchSettings(),
        ) to row.updatedAt
    }

    /**
     * Applies [corrections] and writes the match back as the complete truth for its id.
     *
     * [expectedUpdatedAt] comes from the [preview] the user confirmed. If the match has changed
     * since — almost always a cloud download landing in between — the commit is refused rather
     * than silently applied to different figures.
     */
    suspend fun commit(
        matchId: String,
        corrections: List<MatchCorrection>,
        expectedUpdatedAt: Long,
    ): CommitResult = withContext(ioDispatcher) {
        val row = db.matchDao().getById(matchId)
            ?: return@withContext CommitResult.Rejected(
                listOf(CorrectionError("MATCH_NOT_FOUND", "That match is no longer on this device."))
            )

        if (row.updatedAt != expectedUpdatedAt) {
            return@withContext CommitResult.Rejected(
                listOf(
                    CorrectionError(
                        "MATCH_CHANGED",
                        "This match changed while you were reviewing the correction — probably a " +
                            "sync from another device. Open it again and redo the change.",
                    )
                )
            )
        }

        // Checked here and not just at the UI gate: a correction that can't be uploaded has no
        // business being written at all, whichever screen asked for it.
        (editability(matchId) as? Editability.NotOurs)?.let { notOurs ->
            return@withContext CommitResult.Rejected(
                listOf(
                    CorrectionError(
                        "NOT_GROUP_OWNER",
                        "Only the owner of ${notOurs.groupName} can correct its matches — an " +
                            "edit made here would be replaced at the next sync.",
                    )
                )
            )
        }

        val match = matchRepository.getMatchWithStats(matchId)
            ?: return@withContext CommitResult.Rejected(
                listOf(CorrectionError("MATCH_NOT_FOUND", "That match is no longer on this device."))
            )

        when (
            val outcome = MatchCorrectionEngine.apply(
                match = match,
                corrections = corrections,
                settings = match.matchSettings ?: MatchSettings(),
            )
        ) {
            is CorrectionOutcome.Rejected -> CommitResult.Rejected(outcome.errors)

            is CorrectionOutcome.Applied -> {
                // The whole graph, replacing what was there: a correction can remove a bowling
                // row or shorten a list, which an insert-only save would leave behind. This also
                // stamps a fresh `updatedAt`, which is what marks the match for re-upload.
                matchRepository.replaceMatchGraph(outcome.after)
                recordInLog(matchId, outcome)
                Log.d(
                    TAG,
                    "Corrected $matchId: ${outcome.diff.lines.size} change(s), " +
                        "result changed = ${outcome.diff.resultChanged}",
                )
                CommitResult.Committed(outcome = outcome)
            }
        }
    }

    /** The corrections applied to a match, newest first, for the scorecard's summary. */
    suspend fun log(matchId: String): List<CorrectionLogEntry> = withContext(ioDispatcher) {
        db.matchCorrectionLogDao().forMatch(matchId).map { row ->
            CorrectionLogEntry(
                appliedAt = row.appliedAt,
                deviceLabel = row.deviceLabel,
                summary = row.summary.split("\n").filter { it.isNotBlank() },
            )
        }
    }

    data class CorrectionLogEntry(
        val appliedAt: Long,
        val deviceLabel: String,
        val summary: List<String>,
    )

    /**
     * Writes what changed, and the match as it was.
     *
     * Best-effort: a correction that succeeded must not be reported as failed because its note
     * couldn't be filed. Only the lines describing actual changes are kept — "result unchanged"
     * is useful in a preview and noise in a log.
     */
    private suspend fun recordInLog(matchId: String, outcome: CorrectionOutcome.Applied) {
        runCatching {
            val changes = outcome.diff.lines
                .filterNot { it.text.startsWith("Result unchanged") }
                .map { it.text }
            db.matchCorrectionLogDao().insert(
                MatchCorrectionLogEntity(
                    matchId = matchId,
                    appliedAt = System.currentTimeMillis(),
                    deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                    summary = changes.joinToString("\n"),
                    beforeJson = runCatching { Gson().toJson(outcome.before) }.getOrNull(),
                )
            )
        }.onFailure { Log.w(TAG, "Correction applied but not logged for $matchId", it) }
    }

    private fun notFound(matchId: String) = CorrectionOutcome.Rejected(
        listOf(CorrectionError("MATCH_NOT_FOUND", "No match with id $matchId on this device."))
    )
}
