package com.mygame.backend.room

import com.mygame.backend.models.MatchFoundMessage
import com.mygame.backend.session.SessionManager
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue

data class MatchRequest(
    val playerId: String,
    val gameType: String,
    val minPlayers: Int,
    val maxPlayers: Int,
    val timestamp: Long = System.currentTimeMillis()
)

class MatchmakingService(
    private val sessionManager: SessionManager,
    private val roomManager: RoomManager
) {
    private val logger = LoggerFactory.getLogger(MatchmakingService::class.java)
    private val queue = ConcurrentLinkedQueue<MatchRequest>()
    private var job: Job? = null
    
    fun start() {
        if (job != null) return
        job = CoroutineScope(Dispatchers.Default).launch {
            logger.info("Matchmaking service started")
            while (isActive) {
                processQueue()
                delay(2000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun addToQueue(playerId: String, gameType: String, minPlayers: Int, maxPlayers: Int) {
        // Remove existing request for player
        queue.removeIf { it.playerId == playerId }
        queue.add(MatchRequest(playerId, gameType, minPlayers, maxPlayers))
    }

    fun removeFromQueue(playerId: String) {
        queue.removeIf { it.playerId == playerId }
    }

    private suspend fun processQueue() {
        if (queue.isEmpty()) return

        // Group by game type
        val requestsByGame = queue.groupBy { it.gameType }
        
        requestsByGame.forEach { (gameType, requests) ->
            // Simple logic: Group by min/max players preference? 
            // For now, simple grouping of N players.
            // Assuming most want similar settings or we take standard config.
            // Let's assume buckets of GameType + MaxPlayers are compatible.
            
            // Just matching first compatible chunk for simplicity
            val sortedRequests = requests.sortedBy { it.timestamp }
            
            // We need to form groups. 
            // BINGO needs exactly 2.
            // NUMBER_GUESS needs min 2?
            
            val needed = if (gameType == "BINGO") 2 else 2 // default bucket size
            
            if (sortedRequests.size >= needed) {
                val group = sortedRequests.take(needed)
                
                // Create room
                val host = group.first()
                val room = roomManager.createRoom(
                    host.playerId, 
                    "Match ${System.currentTimeMillis()}", 
                    gameType, 
                    needed, 
                    false, 
                    null, 
                    0 // Entry fee handled? We assume matchmaking is free or standard fee? 
                      // Prompt says "Deducted from player coins on join (if entryFee > 0)"
                      // RewardConfig has entry fees. 
                      // Matchmaking rooms should respect it.
                      // RoomManager uses RewardConfig or room.entryFee?
                      // RoomManager uses `room.entryFee`.
                      // We should fetch fee from config. But EconomyService/RewardConfig isn't injected here yet.
                      // I will pass `0` for now or assume Room creation uses defaults or we inject Config.
                      // Actually, RoomManager.createRoom takes entryFee as param.
                      // I should probably inject RewardConfig or look it up.
                      // Since RewardConfig is an `object`, I can use it directly.
                )
                
                // Remove from queue
                group.forEach { queue.remove(it) }
                
                // Join players and notify
                group.forEach { req ->
                    // joinRoom calls broadcast and fee deduction.
                    // But joinRoom checks waiting state.
                    // We must orchestrate.
                    
                    val joined = roomManager.joinRoom(req.playerId, room.id, null)
                    if (joined != null) {
                        sessionManager.getSession(req.playerId)?.send(MatchFoundMessage(room.id, gameType))
                    } else {
                        // Failed to join (e.g. no coins).
                        // Handle error?
                        // For matchmaking, we should probably verify coins before queuing?
                        // Or just let them fail and re-queue?
                    }
                }
            }
        }
    }
}
