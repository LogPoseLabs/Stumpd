package com.oreki.stumpd.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "group_members",
    primaryKeys = ["groupId", "playerId"],
    indices = [Index("playerId")]
)
data class GroupMemberEntity(
    val groupId: String,
    val playerId: String
)
