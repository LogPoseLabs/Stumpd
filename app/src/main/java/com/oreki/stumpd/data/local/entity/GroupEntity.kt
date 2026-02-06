package com.oreki.stumpd.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val inviteCode: String? = null, // 6-character alphanumeric invite code (for joining)
    val claimCode: String? = null, // Secret recovery code (for ownership recovery)
    val isOwner: Boolean = true // True if this device created the group
)
