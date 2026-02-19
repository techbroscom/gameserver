package com.mygame.backend.game

import com.mygame.backend.game.engine.GameState
import java.util.concurrent.ConcurrentHashMap

class GameStateManager {
    private val states = ConcurrentHashMap<String, GameState>()

    fun getState(roomId: String): GameState? {
        return states[roomId]
    }

    fun setState(roomId: String, state: GameState) {
        states[roomId] = state
    }

    fun removeState(roomId: String) {
        states.remove(roomId)
    }
}
