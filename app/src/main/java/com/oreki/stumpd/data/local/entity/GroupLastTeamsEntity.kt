package com.oreki.stumpd.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "group_last_teams")
data class GroupLastTeamsEntity(
    @PrimaryKey val groupId: String,
    val team1PlayerIdsJson: String,
    val team2PlayerIdsJson: String,
    val team1Name: String,
    val team2Name: String
)
