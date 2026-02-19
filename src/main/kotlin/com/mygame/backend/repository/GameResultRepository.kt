package com.mygame.backend.repository

import com.mygame.backend.db.DatabaseFactory.dbQuery
import com.mygame.backend.db.tables.GameResults
import com.mygame.backend.game.engine.GameResult
import com.mygame.backend.util.IdGenerator
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert

class GameResultRepository {
    suspend fun save(roomId: String, gameType: String, result: GameResult, durationMs: Long) = dbQuery {
        GameResults.insert {
            it[id] = IdGenerator.generate()
            it[this.roomId] = roomId
            it[this.gameType] = gameType
            it[winnerIds] = Json.encodeToString(result.winnerIds)
            it[rankingsJson] = Json.encodeToString(result.rankings)
            it[coinDeltasJson] = Json.encodeToString(result.coinDeltas)
            it[this.durationMs] = durationMs
            it[endedAt] = System.currentTimeMillis()
        }
    }
}
