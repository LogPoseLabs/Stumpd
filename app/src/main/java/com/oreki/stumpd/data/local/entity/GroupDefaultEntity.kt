package com.oreki.stumpd.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "group_defaults")
data class GroupDefaultEntity(
    @PrimaryKey val groupId: String,
    val groundName: String,
    val format: String, // BallFormat.name
    val shortPitch: Boolean,
    val matchSettingsJson: String? // serialized MatchSettings
)
