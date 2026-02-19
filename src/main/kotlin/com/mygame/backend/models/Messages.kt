package com.mygame.backend.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
sealed class ClientMessage {
    abstract val requestId: String
}

@Serializable @SerialName("JOIN_LOBBY")
data class JoinLobbyMessage(override val requestId: String, val jwt: String? = null) : ClientMessage()

@Serializable @SerialName("LIST_ROOMS")
data class ListRoomsMessage(override val requestId: String, val gameType: String? = null) : ClientMessage()

@Serializable @SerialName("CREATE_ROOM")
data class CreateRoomMessage(
    override val requestId: String,
    val name: String,
    val gameType: String,
    val maxPlayers: Int,
    val isPrivate: Boolean,
    val password: String? = null,
    val entryFee: Long = 0
) : ClientMessage()

@Serializable @SerialName("JOIN_ROOM")
data class JoinRoomMessage(override val requestId: String, val roomId: String, val password: String? = null) : ClientMessage()

@Serializable @SerialName("LEAVE_ROOM")
data class LeaveRoomMessage(override val requestId: String) : ClientMessage()

@Serializable @SerialName("START_GAME")
data class StartGameMessage(override val requestId: String) : ClientMessage()

@Serializable @SerialName("END_GAME") // For testing or admin mostly
data class EndGameMessage(override val requestId: String) : ClientMessage()

@Serializable @SerialName("SEND_EVENT")
data class SendEventMessage(
    override val requestId: String,
    val opCode: Int,
    val payload: Map<String, JsonElement>
) : ClientMessage()

@Serializable @SerialName("PING")
data class PingMessage(override val requestId: String) : ClientMessage()

@Serializable @SerialName("PONG")
data class PongMessage(override val requestId: String) : ClientMessage()

// --------------------------------------------------------

@Serializable
sealed class ServerMessage {
    abstract val timestamp: Long
}

@Serializable @SerialName("ERROR")
data class ErrorMessage(
    val requestId: String,
    val code: Int,
    val message: String,
    override val timestamp: Long = System.currentTimeMillis()
) : ServerMessage()

@Serializable @SerialName("ROOM_LIST")
data class RoomListMessage(
    val requestId: String,
    val rooms: List<RoomDto>,
    override val timestamp: Long = System.currentTimeMillis()
) : ServerMessage()

@Serializable @SerialName("ROOM_JOINED")
data class RoomJoinedMessage(
    val requestId: String,
    val room: RoomDto, // Full room info
    override val timestamp: Long = System.currentTimeMillis()
) : ServerMessage()

@Serializable @SerialName("PLAYER_JOINED")
data class PlayerJoinedMessage(
    val player: PlayerDto,
    override val timestamp: Long = System.currentTimeMillis()
) : ServerMessage()

@Serializable @SerialName("PLAYER_LEFT")
data class PlayerLeftMessage(
    val playerId: String,
    override val timestamp: Long = System.currentTimeMillis()
) : ServerMessage()

@Serializable @SerialName("GAME_STARTED")
data class GameStartedMessage(
    val initialDelta: JsonElement, // or full GameState
    override val timestamp: Long = System.currentTimeMillis()
) : ServerMessage()

@Serializable @SerialName("GAME_OVER")
data class GameOverMessage(
    val result: JsonElement, // GameResult
    override val timestamp: Long = System.currentTimeMillis()
) : ServerMessage()

@Serializable @SerialName("EVENT")
data class EventMessage(
    val senderId: String,
    val opCode: Int,
    val payload: Map<String, JsonElement>,
    override val timestamp: Long = System.currentTimeMillis()
) : ServerMessage()

@Serializable @SerialName("MATCH_FOUND")
data class MatchFoundMessage(
    val roomId: String,
    val gameType: String,
    override val timestamp: Long = System.currentTimeMillis()
) : ServerMessage()

@Serializable @SerialName("PING")
data class ServerPingMessage(override val timestamp: Long = System.currentTimeMillis()) : ServerMessage()

@Serializable @SerialName("PONG")
data class ServerPongMessage(val requestId: String, override val timestamp: Long = System.currentTimeMillis()) : ServerMessage()

@Serializable
data class RoomDto(
    val id: String,
    val name: String,
    val gameType: String,
    val maxPlayers: Int,
    val currentPlayers: Int,
    val isPrivate: Boolean,
    val state: String, // WAITING, IN_GAME, FINISHED
    val hostId: String,
    val entryFee: Long
)

@Serializable
data class PlayerDto(
    val id: String,
    val username: String,
    val level: Int
)
