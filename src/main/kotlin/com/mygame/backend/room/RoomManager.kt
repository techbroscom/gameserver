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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

    fun getPlayerRoom(playerId: String): Room? =
        rooms.values.firstOrNull { it.players.contains(playerId) }

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
        
        
        // Send a tailored GAME_STARTED to each player with ONLY their own board
        players.forEach { p ->
            val session = sessionManager.getSession(p.id)
            val myState = initialState.players[p.id]

            // Build a per-player JsonObject: only expose their own board + public turn info
            val perPlayerJson = JsonObject(mapOf(
                "roomId" to JsonPrimitive(initialState.roomId),
                "gameType" to JsonPrimitive(initialState.gameType),
                "turnOrder" to JsonArray(initialState.turnOrder.map { JsonPrimitive(it) }),
                "currentTurnIndex" to JsonPrimitive(initialState.currentTurnIndex),
                "players" to JsonObject(mapOf(
                    p.id to JsonObject(mapOf(
                        "playerId" to JsonPrimitive(p.id),
                        "isAlive" to JsonPrimitive(myState?.isAlive ?: true),
                        "custom" to JsonObject(myState?.custom ?: emptyMap())
                    ))
                ))
            ))
            session?.send(GameStartedMessage(perPlayerJson))
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
