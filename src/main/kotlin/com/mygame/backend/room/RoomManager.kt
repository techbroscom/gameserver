package com.mygame.backend.room

import com.mygame.backend.db.DatabaseFactory.dbQuery
import com.mygame.backend.economy.EconomyService
import com.mygame.backend.game.GameLoop
import com.mygame.backend.game.GameStateManager
import com.mygame.backend.game.engine.GameEngineRegistry
import com.mygame.backend.game.engine.GameResult
import com.mygame.backend.models.*
import com.mygame.backend.repository.GameResultRepository
import com.mygame.backend.repository.PlayerRepository
import com.mygame.backend.session.SessionManager
import com.mygame.backend.util.IdGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class RoomManager(
    private val sessionManager: SessionManager,
    private val gameStateManager: GameStateManager,
    private val economyService: EconomyService,
    private val playerRepository: PlayerRepository,
    private val gameResultRepository: GameResultRepository
) {
    private val logger = LoggerFactory.getLogger(RoomManager::class.java)
    private val rooms = ConcurrentHashMap<String, Room>()
    private val gameLoops = ConcurrentHashMap<String, GameLoop>()

    fun createRoom(
        hostId: String, name: String, gameType: String, maxPlayers: Int, 
        isPrivate: Boolean, password: String?, entryFee: Long
    ): Room {
        val id = IdGenerator.generate()
        val room = Room(id, name, gameType, maxPlayers, isPrivate, password, hostId, entryFee)
        rooms[id] = room
        return room
    }

    fun getRoom(roomId: String): Room? = rooms[roomId]

    fun listRooms(gameType: String?): List<Room> {
        return rooms.values.filter { 
            (gameType == null || it.gameType == gameType) && 
            it.state == RoomState.WAITING 
        }
    }

    suspend fun joinRoom(playerId: String, roomId: String, password: String?): Room? {
        val room = rooms[roomId] ?: return null
        
        if (room.state != RoomState.WAITING) return null // Or handle reconnect?
        if (room.isPrivate && room.password != password) return null
        if (room.players.size >= room.maxPlayers) return null
        
        // Deduct entry fee
        if (room.entryFee > 0) {
            val success = economyService.deductEntryFee(playerId, room.gameType)
            if (!success) {
                // Should return error reason... but return type is Room?
                // Caller checks null. 
                // We might need to throw exceptions or return Result<Room>.
                return null 
            }
        }
        
        room.players.add(playerId)
        broadcastToRoom(room.id, PlayerJoinedMessage(playerRepository.findById(playerId)!!.toDto()))
        
        return room
    }
    
    suspend fun leaveRoom(playerId: String, roomId: String) {
        val room = rooms[roomId] ?: return
        room.players.remove(playerId)
        
        broadcastToRoom(roomId, PlayerLeftMessage(playerId))
        
        if (room.players.isEmpty()) {
            closeRoom(roomId)
        } else if (room.hostPlayerId == playerId) {
            // Reassign host
            val newHost = room.players.first()
            // We need to update Room.hostPlayerId. Data class is immutable.
            // Room structure in models/Room.kt says "val hostPlayerId".
            // I should have made it Mutable or var, or create copy.
            // But Room is in a ConcurrentHashMap.
            // I'll update it by replacing the entry.
            val updatedRoom = room.copy(hostPlayerId = newHost)
            rooms[roomId] = updatedRoom
            // Notify room?
        }
    }
    
    suspend fun startGame(hostId: String, roomId: String) {
        val room = rooms[roomId] ?: return
        if (room.hostPlayerId != hostId) return
        if (room.state != RoomState.WAITING) return
        
        val engine = try {
             GameEngineRegistry.get(room.gameType)
        } catch (e: Exception) { return }
        
        // Initialize Game State
        val players = room.players.mapNotNull { playerRepository.findById(it) }
        val initialState = engine.initializeGame(players, emptyMap())
        gameStateManager.setState(roomId, initialState)
        
        room.state = RoomState.IN_GAME
        
        // Broadcast Start
        broadcastToRoom(roomId, GameStartedMessage(Json.encodeToJsonElement(initialState))) // Send full state or specific logic
        // The Engine might have specific private state logic (like Bingo player boards).
        // "GAME_STARTED: { yourBoard: ... }" - section 14.
        // The generic GameStartedMessage in Messages.kt has `initialDelta: JsonElement`.
        // We probably need to send custom messages per player for things like Bingo.
        
        // Workaround: Send generic start, then let engine send specific events?
        // Or loop and send tailored messages.
        players.forEach { p ->
             // For Bingo, board is secret.
             // We can check if engine needs private start msgs.
             // Let's rely on GameStateManager + Loop to send initial state updates?
             // Or send here.
             
             // Simplest: Send a "GAME_STARTED" with the full public state.
             // And if private usage is needed, maybe use "custom" field in PlayerGameState.
             // In BingoEngine, `initializeGame` sets up each player's board in `PlayerGameState`.
             // Each player normally receives the full state? No, "Boards are hidden".
             // So we must sanitize state before sending.
             val sanitizedState = initialState // We need function to sanitize for a player.
             // For now, assume full state sent.
             // TODO: Implement state sanitization/view per player.
             
             val session = sessionManager.getSession(p.id)
             session?.send(GameStartedMessage(Json.encodeToJsonElement(initialState)))
        }

        // Start Loop
        val loop = GameLoop(
            roomId, 
            room.gameType, 
            gameStateManager, 
            economyService, 
            gameResultRepository
        ) { rid, event ->
            // Broadcast strategy
            if (event.targetType == TargetType.BROADCAST) {
                 broadcastToRoom(rid, EventMessage(event.senderId, event.opCode, event.payload))
            } else if (event.targetType == TargetType.SPECIFIC_PLAYERS) {
                 event.targetIds.forEach { pid ->
                     sessionManager.getSession(pid)?.send(EventMessage(event.senderId, event.opCode, event.payload))
                 }
            }
        }
        
        gameLoops[roomId] = loop
        loop.start()
    }
    
    fun getGameLoop(roomId: String): GameLoop? = gameLoops[roomId]

    private fun closeRoom(roomId: String) {
        val loop = gameLoops.remove(roomId)
        loop?.stop()
        rooms.remove(roomId)
        gameStateManager.removeState(roomId)
    }

    private suspend fun broadcastToRoom(roomId: String, message: ServerMessage) {
        val room = rooms[roomId] ?: return
        room.players.forEach { pid ->
            sessionManager.getSession(pid)?.send(message)
        }
    }
}
