package com.mygame.backend.game.engine

object GameEngineRegistry {
    private val engines = mutableMapOf<String, GameEngine>()

    fun register(engine: GameEngine) {
        engines[engine.gameType] = engine
    }

    fun get(gameType: String): GameEngine {
        return engines[gameType] ?: throw IllegalArgumentException("Unknown game type: $gameType")
    }

    fun listTypes(): List<String> = engines.keys.toList()
}
