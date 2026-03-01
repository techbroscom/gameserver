package com.mygame.backend.game.engine.impl

import com.mygame.backend.game.engine.*
import com.mygame.backend.models.GameEvent
import com.mygame.backend.models.Player
import com.mygame.backend.models.TargetType
import kotlinx.serialization.json.*

class TicTacToeEngine : GameEngine {
    override val gameType: String = "TIC_TAC_TOE"

    companion object {
        const val BOARD_SIZE = 3
        const val TOTAL_CELLS = 9
        const val OP_MAKE_MOVE = 20

        // All 8 winning lines: 3 rows, 3 cols, 2 diagonals
        val WIN_LINES = listOf(
            // Rows
            listOf(0, 1, 2),
            listOf(3, 4, 5),
            listOf(6, 7, 8),
            // Columns
            listOf(0, 3, 6),
            listOf(1, 4, 7),
            listOf(2, 5, 8),
            // Diagonals
            listOf(0, 4, 8),
            listOf(2, 4, 6)
        )
    }

    override fun initializeGame(players: List<Player>, config: Map<String, String>): GameState {
        // Create empty 3x3 board — "" means empty, "X" or "O" for marks
        val emptyBoard = List(TOTAL_CELLS) { "" }

        val turnOrder = players.map { it.id }.shuffled()

        // Assign marks: first player in turnOrder = "X", second = "O"
        val marks = mutableMapOf<String, String>()
        turnOrder.forEachIndexed { index, playerId ->
            marks[playerId] = if (index == 0) "X" else "O"
        }

        val initialPlayerStates = players.associate { player ->
            val mark = marks[player.id] ?: "X"
            val custom = mutableMapOf<String, JsonElement>(
                "mark" to JsonPrimitive(mark)
            )
            player.id to PlayerGameState(
                playerId = player.id,
                custom = custom,
                isAlive = true
            )
        }

        return GameState(
            roomId = "", // Will be set by manager
            gameType = gameType,
            players = initialPlayerStates,
            phase = GamePhase.IN_PROGRESS,
            turnOrder = turnOrder,
            currentTurnIndex = 0,
            custom = mutableMapOf(
                "board" to Json.encodeToJsonElement(emptyBoard),
                "moveCount" to JsonPrimitive(0),
                "marks" to Json.encodeToJsonElement(marks),
                "startedAt" to JsonPrimitive(System.currentTimeMillis())
            )
        )
    }

    override fun onTick(state: GameState, deltaMs: Long): TickResult {
        // No tick-based logic for Tic Tac Toe
        return TickResult(state)
    }

    override fun onPlayerEvent(
        state: GameState,
        senderId: String,
        opCode: Int,
        payload: Map<String, JsonElement>
    ): EventResult {
        if (opCode != OP_MAKE_MOVE) return EventResult(state, error = "Invalid OpCode")

        // Validate turn
        val currentTurnPlayer = state.turnOrder[state.currentTurnIndex % state.turnOrder.size]
        if (senderId != currentTurnPlayer) return EventResult(state, error = "Not your turn")

        // Parse cell index
        val cellIndex = payload["cellIndex"]?.jsonPrimitive?.intOrNull
            ?: return EventResult(state, error = "Missing cellIndex")

        if (cellIndex !in 0 until TOTAL_CELLS) return EventResult(state, error = "Cell index out of range")

        // Get current board
        val board = state.custom["board"]?.jsonArray?.map { it.jsonPrimitive.content }?.toMutableList()
            ?: return EventResult(state, error = "Invalid board state")

        // Validate cell is empty
        if (board[cellIndex].isNotEmpty()) return EventResult(state, error = "Cell already occupied")

        // Get player's mark
        val marks = state.custom["marks"]?.jsonObject
            ?: return EventResult(state, error = "Invalid marks state")
        val playerMark = marks[senderId]?.jsonPrimitive?.content
            ?: return EventResult(state, error = "Player mark not found")

        // Place the mark
        board[cellIndex] = playerMark

        // Update state
        val moveCount = (state.custom["moveCount"]?.jsonPrimitive?.intOrNull ?: 0) + 1
        state.custom["board"] = Json.encodeToJsonElement(board)
        state.custom["moveCount"] = JsonPrimitive(moveCount)

        // Advance turn
        val nextTurnIndex = state.currentTurnIndex + 1
        val nextTurnPlayerId = state.turnOrder[nextTurnIndex % state.turnOrder.size]

        // Create broadcast event
        val event = GameEvent(
            senderId = senderId,
            roomId = state.roomId,
            opCode = OP_MAKE_MOVE,
            payload = mapOf(
                "cellIndex" to JsonPrimitive(cellIndex),
                "mark" to JsonPrimitive(playerMark),
                "placedBy" to JsonPrimitive(senderId),
                "nextTurnPlayerId" to JsonPrimitive(nextTurnPlayerId),
                "board" to Json.encodeToJsonElement(board),
                "moveCount" to JsonPrimitive(moveCount)
            ),
            targetType = TargetType.BROADCAST
        )

        val newState = state.copy(
            currentTurnIndex = nextTurnIndex,
            custom = state.custom
        )

        return EventResult(updatedState = newState, broadcastToRoom = listOf(event))
    }

    override fun checkWinCondition(state: GameState): GameResult? {
        val board = state.custom["board"]?.jsonArray?.map { it.jsonPrimitive.content }
            ?: return null
        val marks = state.custom["marks"]?.jsonObject ?: return null
        val moveCount = state.custom["moveCount"]?.jsonPrimitive?.intOrNull ?: 0

        // Check if any player has won
        for ((playerId, markElement) in marks) {
            val mark = markElement.jsonPrimitive.content
            for (line in WIN_LINES) {
                if (line.all { board[it] == mark }) {
                    // This player wins!
                    val winnerId = playerId
                    val loserId = state.players.keys.first { it != winnerId }

                    return GameResult(
                        winnerIds = listOf(winnerId),
                        loserIds = listOf(loserId),
                        rankings = listOf(
                            RankedPlayer(winnerId, 1, 1),
                            RankedPlayer(loserId, 2, 0)
                        ),
                        coinDeltas = mapOf(
                            winnerId to 50L,
                            loserId to -10L
                        ),
                        xpDeltas = mapOf(
                            winnerId to 30,
                            loserId to 5
                        ),
                        summary = mapOf(
                            "totalMoves" to JsonPrimitive(moveCount),
                            "winningMark" to JsonPrimitive(mark)
                        )
                    )
                }
            }
        }

        // Check for draw (all cells filled, no winner)
        if (moveCount >= TOTAL_CELLS) {
            val playerIds = state.players.keys.toList()
            return GameResult(
                winnerIds = playerIds, // Both are "winners" in a draw
                loserIds = emptyList(),
                rankings = playerIds.map { RankedPlayer(it, 1, 0) },
                coinDeltas = playerIds.associateWith { 5L },
                xpDeltas = playerIds.associateWith { 10 },
                summary = mapOf(
                    "totalMoves" to JsonPrimitive(moveCount),
                    "isDraw" to JsonPrimitive(true)
                )
            )
        }

        return null // Game still in progress
    }

    override fun onPlayerDisconnect(state: GameState, playerId: String): GameState {
        return state
    }
}
