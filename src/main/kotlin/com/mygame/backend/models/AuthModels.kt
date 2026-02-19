package com.mygame.backend.models

import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    val token: String,
    val player: PlayerDto
)

@Serializable
data class AuthRequest(
    val username: String,
    val password: String
)
