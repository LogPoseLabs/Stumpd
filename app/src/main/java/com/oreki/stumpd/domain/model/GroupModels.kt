package com.oreki.stumpd.domain.model

data class GroupDefaultSettings(
    val matchSettings: MatchSettings,
    val groundName: String = "",
    val format: String = BallFormat.WHITE_BALL.toString(),
    // If true -> short pitch; if false -> long pitch
    val shortPitch: Boolean = false,
)

data class PlayerGroup(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    // Store player ids (permanent membership)
    val playerIds: List<String> = emptyList(),
    // Store unavailable player ids (temporary availability toggle)
    val unavailablePlayerIds: List<String> = emptyList(),
    val defaults: GroupDefaultSettings,
)
