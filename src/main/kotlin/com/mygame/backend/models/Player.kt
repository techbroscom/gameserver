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
}
