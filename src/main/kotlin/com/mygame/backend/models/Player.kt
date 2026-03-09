package com.mygame.backend.models

import kotlinx.serialization.Serializable

@Serializable
data class Player(
    val id: String,
    val authId: String,
    val username: String?,
    // Sensitive data like password hash is not in the base model or is transient
    val coins: Long = 500,
    val xp: Int = 0,
    val level: Int = 1,
    val elo: Int = 1000,
    val gamesPlayed: Int = 0,
    val wins: Int = 0,
    val lastFreeCoinsCollectedAt: Long = 0,
    val avatarId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDto() = PlayerDto(id, authId, username, level, avatarId)
    fun toStatsDto() = PlayerStatsDto(id, authId, username, coins, xp, level, elo, gamesPlayed, wins, lastFreeCoinsCollectedAt, avatarId)
}

@Serializable
data class PlayerDto(
    val id: String,
    val authId: String,
    val username: String?,
    val level: Int,
    val avatarId: String?
)

@Serializable
data class PlayerStatsDto(
    val id: String,
    val authId: String,
    val username: String?,
    val coins: Long,
    val xp: Int,
    val level: Int,
    val elo: Int,
    val gamesPlayed: Int,
    val wins: Int,
    val lastFreeCoinsCollectedAt: Long,
    val avatarId: String?
)

@Serializable
data class LeaderboardEntryDto(
    val authId: String,
    val username: String?,
    val elo: Int,
    val wins: Int,
    val avatarId: String? = null
)
