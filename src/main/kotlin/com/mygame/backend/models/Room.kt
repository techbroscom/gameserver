package com.mygame.backend.models

import java.util.concurrent.ConcurrentHashMap

enum class RoomState {
    WAITING, IN_GAME, FINISHED
}

data class Room(
    val id: String,
    val name: String,
    val gameType: String,
    val maxPlayers: Int,
    val isPrivate: Boolean,
    val password: String? = null,
    val hostPlayerId: String,
    val entryFee: Long,
    val createdAt: Long = System.currentTimeMillis()
) {
    @Volatile var state: RoomState = RoomState.WAITING
    // Thread-safe set of player IDs in the room
    val players = ConcurrentHashMap.newKeySet<String>()
    
    fun toDto() = RoomDto(
        id = id,
        name = name,
        gameType = gameType,
        maxPlayers = maxPlayers,
        currentPlayers = players.size,
        isPrivate = isPrivate,
        state = state.name,
        hostId = hostPlayerId,
        entryFee = entryFee
    )
}
