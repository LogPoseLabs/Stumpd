package com.oreki.stumpd.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tracks groups that this device has joined via invite codes.
 * Used to sync data only for groups the user is a member of.
 */
@Entity(
    tableName = "joined_groups",
    indices = [Index("inviteCode")]
)
data class JoinedGroupEntity(
    @PrimaryKey val groupId: String, // The remote group ID
    val inviteCode: String, // The code used to join
    val groupName: String, // Cached group name
    val joinedAt: Long = System.currentTimeMillis()
)
