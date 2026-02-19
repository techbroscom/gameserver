package com.mygame.backend.models

import kotlinx.serialization.Serializable

@Serializable
data class CoinTransaction(
    val id: String,
    val playerId: String,
    val amount: Long,
    val type: String, // Reason
    val timestamp: Long,
    val balanceAfter: Long
)
