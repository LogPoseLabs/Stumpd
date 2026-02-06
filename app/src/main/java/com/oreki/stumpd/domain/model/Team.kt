package com.oreki.stumpd.domain.model

// Enhanced Team data class
data class Team(
    val name: String,
    val players: MutableList<Player> = mutableListOf(), // Changed from List to MutableList
) {
    val regularPlayersCount: Int
        get() = players.count { !it.isJoker }
}
