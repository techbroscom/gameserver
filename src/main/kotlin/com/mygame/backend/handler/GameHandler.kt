package com.mygame.backend.handler

import com.mygame.backend.economy.RewardConfig
import com.mygame.backend.game.engine.GameEngineRegistry
import com.mygame.backend.models.*
import com.mygame.backend.room.MatchmakingService
import com.mygame.backend.room.RoomManager
import com.mygame.backend.session.PlayerSession
import com.mygame.backend.session.SessionManager
import com.mygame.backend.util.RateLimiter
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.slf4j.LoggerFactory
import com.mygame.backend.models.GameEvent
import com.mygame.backend.models.TargetType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GameHandler(
    private val sessionManager: SessionManager,
    private val roomManager: RoomManager,
    private val matchmakingService: MatchmakingService
) {
    private val logger = LoggerFactory.getLogger(GameHandler::class.java)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun handleSession(session: WebSocketSession, playerId: String) {
        val playerSession = PlayerSession(playerId, session)
        sessionManager.addSession(playerId, playerSession)
        
        try {
            for (frame in session.incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    // Start of message processing
                    try {
                        val message = json.decodeFromString<ClientMessage>(text)
                        processMessage(message, playerId, playerSession)
                    } catch (e: Exception) {
                        logger.error("Error parsing message", e)
                        playerSession.send(ErrorMessage("unknown", 4006, "Invalid Message Format"))
                    }
                }
            }
        } finally {
            if (sessionManager.getSession(playerId) === playerSession) {
                sessionManager.removeSession(playerId)
            }
            // Handle forceful disconnect logic with grace period
            val room = roomManager.getPlayerRoom(playerId)
            if (room != null) {
                CoroutineScope(Dispatchers.Default).launch {
                    delay(60000) // 60s grace period
                    // Check if player reconnected (has a new active session)
                    if (sessionManager.getSession(playerId) == null) {
                        logger.info("Grace period expired for player $playerId, removing from room ${room.id}")
                        roomManager.leaveRoom(playerId, room.id)
                    } else {
                        logger.info("Player $playerId reconnected during grace period, keeping in room ${room.id}")
                    }
                }
            }
        }
    }
    
    private suspend fun processMessage(message: ClientMessage, playerId: String, session: PlayerSession) {
        // Rate limiting check could go here
        
        when (message) {
             is JoinLobbyMessage -> {
                 val room = roomManager.getPlayerRoom(playerId)
                 if (room != null && room.state != RoomState.FINISHED) {
                     logger.info("Player $playerId reconnected to lobby, found active room ${room.id}")
                     // Send RoomJoined first so client moves to room/game page
                     session.send(RoomJoinedMessage(message.requestId, room.toDto()))
                     
                     // If game is already started, send current state
                     if (room.state == RoomState.IN_GAME) {
                         roomManager.resendGameState(playerId, room.id)
                     }
                 }
             }
             is ListRoomsMessage -> {
                 val rooms = roomManager.listRooms(message.gameType).map { it.toDto() }
                 session.send(RoomListMessage(message.requestId, rooms))
             }
             is CreateRoomMessage -> {
                 try {
                     // Get fee from config if not provided logic, or use user provided?
                     // Prompt says "Room properties... entryFee...".
                     // RewardConfig has standard fees. 
                     // Let's use RewardConfig default for the game type if message.entryFee is 0?
                     // Or trust client? Never trust client.
                     // Use RewardConfig.
                     val config = try { RewardConfig.get(message.gameType) } catch(e: Exception) { null }
                     val fee = config?.entryFee ?: 0L
                     
                     val room = roomManager.createRoom(
                         hostId = playerId,
                         name = message.name,
                         gameType = message.gameType,
                         maxPlayers = message.maxPlayers,
                         isPrivate = message.isPrivate,
                         password = message.password,
                         entryFee = fee,
                         config = message.config
                     )
                     
                     // Auto-join host
                     val joined = roomManager.joinRoom(playerId, room.id, message.password)
                     if (joined != null) {
                         session.send(RoomJoinedMessage(message.requestId, joined.toDto()))
                     } else {
                         session.send(ErrorMessage(message.requestId, 4007, "Insufficient Coins or Error"))
                     }
                 } catch (e: Exception) {
                     session.send(ErrorMessage(message.requestId, 4006, "Creation Failed: ${e.message}"))
                 }
             }
             is JoinRoomMessage -> {
                 val room = roomManager.joinRoom(playerId, message.roomId, message.password)
                 if (room != null) {
                     session.send(RoomJoinedMessage(message.requestId, room.toDto()))
                 } else {
                     session.send(ErrorMessage(message.requestId, 4001, "Join Failed (Full/Private/NoCoins)"))
                 }
             }
             is LeaveRoomMessage -> {
                 val room = roomManager.getPlayerRoom(playerId)
                 if (room != null) {
                     roomManager.leaveRoom(playerId, room.id)
                 }
             }
             is CloseRoomMessage -> {
                 val room = roomManager.getPlayerRoom(playerId)
                 if (room != null) {
                     roomManager.leaveRoom(playerId, room.id)
                 }
             }

             is StartGameMessage -> {
                 val room = roomManager.getPlayerRoom(playerId)
                 if (room != null) {
                     roomManager.startGame(playerId, room.id)
                 } else {
                     session.send(ErrorMessage(message.requestId, 4008, "You are not in a room"))
                 }
             }
             is SendEventMessage -> {
                 val room = roomManager.getPlayerRoom(playerId)
                 if (room != null) {
                     val loop = roomManager.getGameLoop(room.id)
                     if (loop != null) {
                         val gameEvent = GameEvent(
                             senderId = playerId,
                             roomId = room.id,
                             opCode = message.opCode,
                             payload = message.payload,
                             targetType = TargetType.BROADCAST
                         )
                         loop.enqueueEvent(gameEvent)
                     } else {
                         session.send(ErrorMessage(message.requestId, 4009, "Game not in progress"))
                     }
                 } else {
                     session.send(ErrorMessage(message.requestId, 4008, "You are not in a room"))
                 }
             }
             is PingMessage -> session.send(ServerPongMessage(message.requestId))
             is PongMessage -> { /* Handle heartbeat stats */ }
             is EndGameMessage -> { /* handling end game manually */ }
             is VoiceSignalMessage -> {
                 val targetSession = sessionManager.getSession(message.targetId)
                 if (targetSession != null) {
                     targetSession.send(ServerVoiceSignalMessage(
                         senderId = playerId,
                         signal = message.signal
                     ))
                 } else {
                     // Optionally send an error if target is offline, though for WebRTC 
                     // usually we just let it fail silently or let the client handle timeout.
                     logger.debug("VoiceSignal target ${message.targetId} not found for $playerId")
                 }
             }
             else -> {}
        }
    }
}
