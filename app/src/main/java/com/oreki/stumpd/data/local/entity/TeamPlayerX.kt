package com.oreki.stumpd.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "team_players",
    primaryKeys = ["teamName", "playerId"],
    indices = [Index("playerId")]
)
data class TeamPlayerX(
    val teamName: String,
    val playerId: String
)
