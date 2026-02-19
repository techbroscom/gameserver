package com.mygame.backend.game

import com.mygame.backend.economy.EconomyService
import com.mygame.backend.game.engine.GameEngineRegistry
import com.mygame.backend.game.engine.GamePhase
import com.mygame.backend.models.GameEvent
import com.mygame.backend.models.TargetType
import com.mygame.backend.repository.GameResultRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.slf4j.LoggerFactory

class GameLoop(
    private val roomId: String,
    private val gameType: String,
    private val gameStateManager: GameStateManager,
    private val economyService: EconomyService,
    private val gameResultRepository: GameResultRepository,
    private val broadcastEvent: suspend (String, GameEvent) -> Unit
) {
    private val logger = LoggerFactory.getLogger(GameLoop::class.java)
    private var job: Job? = null
    private val eventQueue = Channel<GameEvent>(Channel.UNLIMITED)
    private val tickRateMs = 50L // 20 TPS

    fun start() {
        if (job != null) return
        job = CoroutineScope(Dispatchers.Default).launch {
            val engine = GameEngineRegistry.get(gameType)
            logger.info("Starting game loop for room $roomId with engine ${engine.gameType}")

            var lastTickTime = System.currentTimeMillis()

            while (isActive) {
                val now = System.currentTimeMillis()
                val delta = now - lastTickTime
                lastTickTime = now

                val currentState = gameStateManager.getState(roomId)
                if (currentState == null || currentState.phase == GamePhase.GAME_OVER) {
                    break
                }

                // Process queued events
                // For simplicity in this loop, we might process one by one or batch
                // But the requirement says "collect queued events", implying we drain the queue
                
                // NOTE: In a real "tick" loop we often process input -> update -> output
                // Here we process events as they come in? Or drain the channel?
                // Let's drain the channel up to a limit or all.
                
                // Since this `GameLoop` is per room, we can just consume what's available.
                // However, `onPlayerEvent` is called synchronously in typical Ktor handlers usually?
                // But the requirements say "Each tick: collect queued events -> validate -> update".
                // So the handler should push to `eventQueue`, and we process here.
                
                // Draining the queue:
                while (true) {
                    val event = eventQueue.tryReceive().getOrNull() ?: break
                    val stateBeforeEvent = gameStateManager.getState(roomId) ?: break
                    
                    val eventResult = engine.onPlayerEvent(stateBeforeEvent, event.senderId, event.opCode, event.payload)
                    
                    if (eventResult.error != null) {
                        // Send error to player
                        // We need a way to send error. broadcastEvent handles GameEvent. 
                        // Error is usually a ServerMessage. 
                        // But let's assume valid events just modify state, invalid ones are rejected 
                        // at the handler level OR we send an error event here.
                        // For now we log.
                        logger.warn("Error processing event in room $roomId: ${eventResult.error}")
                    } else {
                        gameStateManager.setState(roomId, eventResult.updatedState)
                         
                        // Broadcast events
                        eventResult.broadcastToRoom.forEach { outgoing ->
                            broadcastEvent(roomId, outgoing)
                        }
                        
                        // Check win condition immediately after event?
                        val gameResult = engine.checkWinCondition(eventResult.updatedState)
                        if (gameResult != null) {
                           handleGameOver(gameResult)
                           break // Exit loop
                        }
                    }
                }
                
                // Tick the engine
                val stateAfterEvents = gameStateManager.getState(roomId)
                if (stateAfterEvents != null && stateAfterEvents.phase != GamePhase.GAME_OVER) {
                     val tickResult = engine.onTick(stateAfterEvents, delta)
                     gameStateManager.setState(roomId, tickResult.updatedState)
                     
                     tickResult.broadcastEvents.forEach { outgoing ->
                         broadcastEvent(roomId, outgoing)
                     }
                     
                     // Check win condition after tick (e.g. time limit)
                     val gameResult = engine.checkWinCondition(tickResult.updatedState)
                     if (gameResult != null) {
                         handleGameOver(gameResult)
                         break
                     }
                }

                // Sleep remainder
                val elapsed = System.currentTimeMillis() - now
                val sleepTime = tickRateMs - elapsed
                if (sleepTime > 0) {
                    delay(sleepTime)
                }
            }
            logger.info("Game loop for room $roomId ended")
        }
    }
    
    fun enqueueEvent(event: GameEvent) {
        eventQueue.trySend(event)
    }

    fun stop() {
        job?.cancel()
        job = null
    }
    
    private suspend fun handleGameOver(result: com.mygame.backend.game.engine.GameResult) {
        val finalState = gameStateManager.getState(roomId)?.copy(phase = GamePhase.GAME_OVER)
        if (finalState != null) {
            gameStateManager.setState(roomId, finalState)
        }

        economyService.applyGameResult(roomId, result)
        gameResultRepository.save(
            roomId = roomId, 
            gameType = gameType, 
            result = result, 
            durationMs = finalState?.elapsedMs ?: 0
        )
        
        // Broadcast GAME_OVER
        val gameOverEvent = GameEvent(
            senderId = "SERVER",
            roomId = roomId,
            opCode = -1, // System event
            payload = mapOf("result" to Json.encodeToJsonElement(result)),
            targetType = TargetType.BROADCAST
        )
        // We use the generic broadcaster but usually we send a specific ServerMessage type.
        // But the GameLoop is generic. The caller/GameHandler handles wrapping GameEvent into WebSocket frames?
        // Or broadCastEvent sends the frame. 
        // We'll assume broadcastEvent does the right thing.
        broadcastEvent(roomId, gameOverEvent)
        
        stop()
    }
}
