package com.mygame.backend.game.engine.impl

import com.mygame.backend.game.engine.*
import com.mygame.backend.models.GameEvent
import com.mygame.backend.models.Player
import com.mygame.backend.models.TargetType
import kotlinx.serialization.json.*

class TicTacToeEngine : GameEngine {
    override val gameType: String = "TIC_TAC_TOE"

    companion object {
        const val OP_MAKE_MOVE = 20
        const val MOVE_TIMEOUT_MS = 30000L

        fun generateWinLines(boardSize: Int, winCondition: Int): List<List<Int>> {
            val lines = mutableListOf<List<Int>>()

            // Horizontal
            for (r in 0 until boardSize) {
                for (c in 0..boardSize - winCondition) {
                    val line = mutableListOf<Int>()
                    for (i in 0 until winCondition) {
                        line.add(r * boardSize + (c + i))
                    }
                    lines.add(line)
                }
            }

            // Vertical
            for (c in 0 until boardSize) {
                for (r in 0..boardSize - winCondition) {
                    val line = mutableListOf<Int>()
                    for (i in 0 until winCondition) {
                        line.add((r + i) * boardSize + c)
                    }
                    lines.add(line)
                }
            }

            // Diagonal (Top-left to Bottom-right)
            for (r in 0..boardSize - winCondition) {
                for (c in 0..boardSize - winCondition) {
                    val line = mutableListOf<Int>()
                    for (i in 0 until winCondition) {
                        line.add((r + i) * boardSize + (c + i))
                    }
                    lines.add(line)
                }
            }

            // Diagonal (Top-right to Bottom-left)
            for (r in 0..boardSize - winCondition) {
                for (c in winCondition - 1 until boardSize) {
                    val line = mutableListOf<Int>()
                    for (i in 0 until winCondition) {
                        line.add((r + i) * boardSize + (c - i))
                    }
                    lines.add(line)
                }
            }

            return lines
        }
    }

    override fun initializeGame(players: List<Player>, config: Map<String, String>): GameState {
        val boardSize = config["boardSize"]?.toIntOrNull() ?: 3
        val winCondition = if (boardSize >= 5) 4 else 3
        val totalCells = boardSize * boardSize
        val winLines = generateWinLines(boardSize, winCondition)

        // Create empty board
        val emptyBoard = List(totalCells) { "" }

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
            roomId = "",
            gameType = gameType,
            players = initialPlayerStates,
            phase = GamePhase.IN_PROGRESS,
            turnOrder = turnOrder,
            currentTurnIndex = 0,
            custom = mutableMapOf(
                "board" to Json.encodeToJsonElement(emptyBoard),
                "moveCount" to JsonPrimitive(0),
                "marks" to Json.encodeToJsonElement(marks),
                "boardSize" to JsonPrimitive(boardSize),
                "winCondition" to JsonPrimitive(winCondition),
                "totalCells" to JsonPrimitive(totalCells),
                "winLines" to Json.encodeToJsonElement(winLines),
                "startedAt" to JsonPrimitive(System.currentTimeMillis()),
                "lastMoveTimestamp" to JsonPrimitive(System.currentTimeMillis())
            )
        )
    }

    override fun onTick(state: GameState, deltaMs: Long): TickResult {
        val lastMove = state.custom["lastMoveTimestamp"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
        val now = System.currentTimeMillis()

        if (now - lastMove > MOVE_TIMEOUT_MS && state.phase == GamePhase.IN_PROGRESS && state.custom["forfeitedPlayerId"] == null) {
            val currentTurnPlayer = state.turnOrder[state.currentTurnIndex % state.turnOrder.size]
            state.custom["forfeitedPlayerId"] = JsonPrimitive(currentTurnPlayer)
        }

        return TickResult(state)
    }

    override fun onPlayerEvent(
        state: GameState,
        senderId: String,
        opCode: Int,
        payload: Map<String, JsonElement>
    ): EventResult {
        if (opCode != OP_MAKE_MOVE) return EventResult(state, error = "Invalid OpCode")

        val currentTurnPlayer = state.turnOrder[state.currentTurnIndex % state.turnOrder.size]
        if (senderId != currentTurnPlayer) return EventResult(state, error = "Not your turn")

        val cellIndex = payload["cellIndex"]?.jsonPrimitive?.intOrNull
            ?: return EventResult(state, error = "Missing cellIndex")

        val totalCells = state.custom["totalCells"]?.jsonPrimitive?.intOrNull ?: 9
        if (cellIndex !in 0 until totalCells) return EventResult(state, error = "Cell index out of range")

        val board = state.custom["board"]?.jsonArray?.map { it.jsonPrimitive.content }?.toMutableList()
            ?: return EventResult(state, error = "Invalid board state")

        if (board[cellIndex].isNotEmpty()) return EventResult(state, error = "Cell already occupied")

        val marks = state.custom["marks"]?.jsonObject
            ?: return EventResult(state, error = "Invalid marks state")
        val playerMark = marks[senderId]?.jsonPrimitive?.content
            ?: return EventResult(state, error = "Player mark not found")

        board[cellIndex] = playerMark

        val moveCount = (state.custom["moveCount"]?.jsonPrimitive?.intOrNull ?: 0) + 1
        state.custom["board"] = Json.encodeToJsonElement(board)
        state.custom["moveCount"] = JsonPrimitive(moveCount)
        state.custom["lastMoveTimestamp"] = JsonPrimitive(System.currentTimeMillis())

        val nextTurnIndex = state.currentTurnIndex + 1
        val nextTurnPlayerId = state.turnOrder[nextTurnIndex % state.turnOrder.size]

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
        val totalCells = state.custom["totalCells"]?.jsonPrimitive?.intOrNull ?: 9
        
        val winLinesJson = state.custom["winLines"]?.jsonArray
            ?: return null
        val winLines = winLinesJson.map { line -> line.jsonArray.map { it.jsonPrimitive.int } }

        // Check for timeout/forfeit first
        val forfeitId = state.custom["forfeitedPlayerId"]?.jsonPrimitive?.content
        if (forfeitId != null) {
            val opponentId = state.players.keys.firstOrNull { it != forfeitId }
            if (opponentId != null) {
                return GameResult(
                    winnerIds = listOf(opponentId),
                    loserIds = listOf(forfeitId),
                    rankings = listOf(
                        RankedPlayer(opponentId, 1, 1),
                        RankedPlayer(forfeitId, 2, 0)
                    ),
                    coinDeltas = mapOf(opponentId to 50L, forfeitId to -10L),
                    xpDeltas = mapOf(opponentId to 30, forfeitId to 5),
                    summary = mapOf("reason" to JsonPrimitive("TIMEOUT"))
                )
            }
        }

        for ((playerId, markElement) in marks) {
            val mark = markElement.jsonPrimitive.content
            for (line in winLines) {
                if (line.all { board[it] == mark }) {
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

        if (moveCount >= totalCells) {
            val playerIds = state.players.keys.toList()
            return GameResult(
                winnerIds = playerIds,
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

        return null
    }

    override fun onPlayerDisconnect(state: GameState, playerId: String): GameState {
        return state
    }
}
