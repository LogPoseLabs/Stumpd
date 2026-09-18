package com.oreki.stumpd.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class MatchCloudSyncPolicyTest {

    private val me = "user-a"
    private val other = "user-b"
    private val groupId = "group-1"

    @Test
    fun `new ungrouped match uploads as owner`() {
        assertEquals(
            MatchUploadDecision.UploadAsOwner,
            MatchCloudSyncPolicy.decideMatchUpload(
                userId = me,
                localUpdatedAt = 100L,
                cloudDocExists = false,
                cloudOwnerId = null,
                cloudUpdatedAt = 0L,
                groupId = null,
                isDestinationGroupOwner = false,
            ),
        )
    }

    @Test
    fun `group member who is not group owner cannot upload`() {
        assertEquals(
            MatchUploadDecision.SkipNotAuthorized,
            MatchCloudSyncPolicy.decideMatchUpload(
                userId = me,
                localUpdatedAt = 200L,
                cloudDocExists = false,
                cloudOwnerId = null,
                cloudUpdatedAt = 0L,
                groupId = groupId,
                isDestinationGroupOwner = false,
            ),
        )
    }

    @Test
    fun `group owner uploads new group match`() {
        assertEquals(
            MatchUploadDecision.UploadAsOwner,
            MatchCloudSyncPolicy.decideMatchUpload(
                userId = me,
                localUpdatedAt = 100L,
                cloudDocExists = false,
                cloudOwnerId = null,
                cloudUpdatedAt = 0L,
                groupId = groupId,
                isDestinationGroupOwner = true,
            ),
        )
    }

    @Test
    fun `skips when cloud already has same owner and timestamp`() {
        assertEquals(
            MatchUploadDecision.SkipAlreadySynced,
            MatchCloudSyncPolicy.decideMatchUpload(
                userId = me,
                localUpdatedAt = 100L,
                cloudDocExists = true,
                cloudOwnerId = me,
                cloudUpdatedAt = 100L,
                groupId = groupId,
                isDestinationGroupOwner = true,
            ),
        )
    }

    @Test
    fun `uploads when local is newer than cloud for same owner`() {
        assertEquals(
            MatchUploadDecision.UploadAsOwner,
            MatchCloudSyncPolicy.decideMatchUpload(
                userId = me,
                localUpdatedAt = 200L,
                cloudDocExists = true,
                cloudOwnerId = me,
                cloudUpdatedAt = 100L,
                groupId = groupId,
                isDestinationGroupOwner = true,
            ),
        )
    }

    @Test
    fun `group owner reclaims stale cloud owner when local is newer`() {
        assertEquals(
            MatchUploadDecision.UploadAsOwner,
            MatchCloudSyncPolicy.decideMatchUpload(
                userId = me,
                localUpdatedAt = 200L,
                cloudDocExists = true,
                cloudOwnerId = other,
                cloudUpdatedAt = 100L,
                groupId = groupId,
                isDestinationGroupOwner = true,
            ),
        )
    }

    @Test
    fun `group owner skips upload when cloud is newer`() {
        assertEquals(
            MatchUploadDecision.SkipCloudNewer,
            MatchCloudSyncPolicy.decideMatchUpload(
                userId = me,
                localUpdatedAt = 50L,
                cloudDocExists = true,
                cloudOwnerId = other,
                cloudUpdatedAt = 100L,
                groupId = groupId,
                isDestinationGroupOwner = true,
            ),
        )
    }
}
