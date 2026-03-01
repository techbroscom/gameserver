package com.mygame.backend.models

import kotlinx.serialization.Serializable

@Serializable
data class Player(
    val id: String,
    val username: String,
    // Sensitive data like password hash is not in the base model or is transient
    val coins: Long = 500,
    val xp: Int = 0,
    val level: Int = 1,
    val elo: Int = 1000,
    val gamesPlayed: Int = 0,
    val wins: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDto() = PlayerDto(id, username, level)
    fun toStatsDto() = PlayerStatsDto(id, username, coins, xp, level, elo, gamesPlayed, wins)
}

@Serializable
data class PlayerStatsDto(
    val id: String,
    val username: String,
    val coins: Long,
    val xp: Int,
    val level: Int,
    val elo: Int,
    val gamesPlayed: Int,
    val wins: Int
)

@Serializable
data class LeaderboardEntryDto(
    val username: String,
    val elo: Int,
    val wins: Int
)
