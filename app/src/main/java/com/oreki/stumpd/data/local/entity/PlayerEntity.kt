package com.oreki.stumpd.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "players")
data class PlayerEntity(
    @PrimaryKey val id: String, // PlayerId.value
    val name: String,
    val isJoker: Boolean
)
