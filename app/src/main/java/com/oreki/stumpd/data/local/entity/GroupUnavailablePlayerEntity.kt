package com.oreki.stumpd.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "group_unavailable_players",
    primaryKeys = ["groupId", "playerId"],
    indices = [Index("playerId")]
)
data class GroupUnavailablePlayerEntity(
    val groupId: String,
    val playerId: String
)
