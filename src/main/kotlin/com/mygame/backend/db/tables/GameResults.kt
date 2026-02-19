package com.mygame.backend.db.tables

import org.jetbrains.exposed.sql.Table

object GameResults : Table() {
    val id = varchar("id", 128)
    val roomId = varchar("room_id", 128)
    val gameType = varchar("game_type", 50)
    val winnerIds = text("winner_ids") // JSON array
    val rankingsJson = text("rankings_json")
    val coinDeltasJson = text("coin_deltas_json")
    val durationMs = long("duration_ms")
    val endedAt = long("ended_at")

    override val primaryKey = PrimaryKey(id)
}
