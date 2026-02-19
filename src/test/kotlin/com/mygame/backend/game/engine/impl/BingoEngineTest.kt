package com.mygame.backend.game.engine.impl

import com.mygame.backend.game.engine.GamePhase
import com.mygame.backend.models.Player
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BingoEngineTest {
    private val engine = BingoEngine()
    private val p1 = Player("p1", "Player1")
    private val p2 = Player("p2", "Player2")
    private val players = listOf(p1, p2)

    @Test
    fun `test game initialization`() {
        val state = engine.initializeGame(players, emptyMap())
        assertEquals("BINGO", state.gameType)
        assertEquals(GamePhase.IN_PROGRESS, state.phase)
        assertEquals(2, state.players.size)
        // Check boards are present
        state.players.values.forEach { 
            val board = it.custom["board"]?.jsonArray
            assertNotNull(board)
            assertEquals(25, board.size)
        }
    }

    @Test
    fun `test call number`() {
        val state = engine.initializeGame(players, emptyMap())
        val currentPlayerId = state.turnOrder[state.currentTurnIndex % 2]
        
        // Pick a valid number (e.g. 1)
        val result = engine.onPlayerEvent(
            state, 
            currentPlayerId, 
            10, // OP_CALL_NUMBER
            mapOf("number" to kotlinx.serialization.json.JsonPrimitive(1))
        )
        
        assertEquals(null, result.error)
        val newState = result.updatedState
        
        // Assert number added to calledNumbers
        val called = newState.custom["calledNumbers"]!!.jsonArray.map { it.jsonPrimitive.int }
        assertTrue(called.contains(1))
        
        // Assert turn advanced
        assertEquals(1, newState.currentTurnIndex)
    }
}
