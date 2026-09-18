package com.oreki.stumpd.data.sync

/**
 * Decides how a local match should interact with an existing Firestore match document.
 *
 * Uploads to Firestore for group-scoped matches are allowed only for the destination group's owner.
 */
enum class MatchUploadDecision {
    /** Upload using the current user's Firebase uid as [ownerId]. */
    UploadAsOwner,

    /** Cloud copy is newer — do not upload; download from cloud instead. */
    SkipCloudNewer,

    /** Local and cloud are already aligned — no writes needed. */
    SkipAlreadySynced,

    /** Caller is not allowed to upload this match (e.g. not the group owner). */
    SkipNotAuthorized,
}

object MatchCloudSyncPolicy {

    fun decideMatchUpload(
        userId: String,
        localUpdatedAt: Long,
        cloudDocExists: Boolean,
        cloudOwnerId: String?,
        cloudUpdatedAt: Long,
        groupId: String?,
        isDestinationGroupOwner: Boolean,
    ): MatchUploadDecision {
        val grouped = !groupId.isNullOrBlank()
        if (grouped && !isDestinationGroupOwner) {
            return MatchUploadDecision.SkipNotAuthorized
        }

        if (!cloudDocExists) {
            return MatchUploadDecision.UploadAsOwner
        }

        if (cloudUpdatedAt > localUpdatedAt) {
            return MatchUploadDecision.SkipCloudNewer
        }

        if (cloudOwnerId == userId) {
            return if (localUpdatedAt > cloudUpdatedAt) {
                MatchUploadDecision.UploadAsOwner
            } else {
                MatchUploadDecision.SkipAlreadySynced
            }
        }

        if (grouped && isDestinationGroupOwner) {
            // Group owner may reclaim a stale cloud ownerId (including equal timestamps).
            return MatchUploadDecision.UploadAsOwner
        }

        return MatchUploadDecision.SkipNotAuthorized
    }
}
