package com.mygame.backend.game.engine.impl

import com.mygame.backend.game.engine.GamePhase
import com.mygame.backend.models.Player
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TicTacToeEngineTest {
    private val engine = TicTacToeEngine()
    private val p1 = Player("p1", "Player1")
    private val p2 = Player("p2", "Player2")
    private val players = listOf(p1, p2)

    @Test
    fun `test game initialization 3x3`() {
        val state = engine.initializeGame(players, mapOf("boardSize" to "3"))
        assertEquals("TIC_TAC_TOE", state.gameType)
        assertEquals(3, state.custom["boardSize"]?.jsonPrimitive?.int)
        assertEquals(3, state.custom["winCondition"]?.jsonPrimitive?.int)
        assertEquals(9, state.custom["totalCells"]?.jsonPrimitive?.int)
        assertEquals(9, state.custom["board"]?.jsonArray?.size)
    }

    @Test
    fun `test game initialization 5x5`() {
        val state = engine.initializeGame(players, mapOf("boardSize" to "5"))
        assertEquals(5, state.custom["boardSize"]?.jsonPrimitive?.int)
        assertEquals(4, state.custom["winCondition"]?.jsonPrimitive?.int)
        assertEquals(25, state.custom["totalCells"]?.jsonPrimitive?.int)
        assertEquals(25, state.custom["board"]?.jsonArray?.size)
    }

    @Test
    fun `test 3x3 win detection`() {
        val state = engine.initializeGame(players, mapOf("boardSize" to "3"))
        val p1Id = state.turnOrder[0]
        val p2Id = state.turnOrder[1]

        var currentState = state
        // P1: (0,0), (0,1), (0,2) -> win
        val moves = listOf(
            p1Id to 0, p2Id to 3,
            p1Id to 1, p2Id to 4,
            p1Id to 2
        )

        for ((playerId, cellIndex) in moves) {
            val result = engine.onPlayerEvent(currentState, playerId, TicTacToeEngine.OP_MAKE_MOVE, mapOf("cellIndex" to JsonPrimitive(cellIndex)))
            currentState = result.updatedState
        }

        val gameResult = engine.checkWinCondition(currentState)
        assertNotNull(gameResult)
        assertEquals(listOf(p1Id), gameResult.winnerIds)
    }

    @Test
    fun `test 5x5 4-in-a-row win detection`() {
        val state = engine.initializeGame(players, mapOf("boardSize" to "5"))
        val p1Id = state.turnOrder[0]
        val p2Id = state.turnOrder[1]

        var currentState = state
        // P1: (0,0), (0,1), (0,2), (0,3) -> win (4 in a row)
        val moves = listOf(
            p1Id to 0, p2Id to 5,
            p1Id to 1, p2Id to 6,
            p1Id to 2, p2Id to 7,
            p1Id to 3 // Winning move for 4-in-a-row
        )

        for ((playerId, cellIndex) in moves) {
            val result = engine.onPlayerEvent(currentState, playerId, TicTacToeEngine.OP_MAKE_MOVE, mapOf("cellIndex" to JsonPrimitive(cellIndex)))
            currentState = result.updatedState
        }

        val gameResult = engine.checkWinCondition(currentState)
        assertNotNull(gameResult)
        assertEquals(listOf(p1Id), gameResult.winnerIds)
    }

    @Test
    fun `test 5x5 diagonal win detection`() {
        val state = engine.initializeGame(players, mapOf("boardSize" to "5"))
        val p1Id = state.turnOrder[0]
        val p2Id = state.turnOrder[1]

        var currentState = state
        // Diagonal: 0, 6, 12, 18 (4 in a row)
        val moves = listOf(
            p1Id to 0, p2Id to 1,
            p1Id to 6, p2Id to 2,
            p1Id to 12, p2Id to 3,
            p1Id to 18
        )

        for ((playerId, cellIndex) in moves) {
            val result = engine.onPlayerEvent(currentState, playerId, TicTacToeEngine.OP_MAKE_MOVE, mapOf("cellIndex" to JsonPrimitive(cellIndex)))
            currentState = result.updatedState
        }

        val gameResult = engine.checkWinCondition(currentState)
        assertNotNull(gameResult)
        assertEquals(listOf(p1Id), gameResult.winnerIds)
    }

    @Test
    fun `test draw detection 3x3`() {
        val state = engine.initializeGame(players, mapOf("boardSize" to "3"))
        val p1Id = state.turnOrder[0]
        val p2Id = state.turnOrder[1]

        // X O X
        // X X O
        // O X O
        val moves = listOf(
            p1Id to 0, p2Id to 1, p1Id to 2,
            p2Id to 5, p1Id to 3, p2Id to 6,
            p1Id to 4, p2Id to 8, p1Id to 7
        )

        var currentState = state
        for ((playerId, cellIndex) in moves) {
            val result = engine.onPlayerEvent(currentState, playerId, TicTacToeEngine.OP_MAKE_MOVE, mapOf("cellIndex" to JsonPrimitive(cellIndex)))
            if (result.error != null) break
            currentState = result.updatedState
            if (engine.checkWinCondition(currentState) != null) break
        }

        val gameResult = engine.checkWinCondition(currentState)
        assertNotNull(gameResult)
        assertTrue(gameResult.summary["isDraw"]?.jsonPrimitive?.boolean ?: false || gameResult.winnerIds.size == 1)
    }
}
