package com.mygame.backend.db.tables

import org.jetbrains.exposed.sql.Table

object PlayerGameStats : Table() {
    val playerId = varchar("player_id", 128) references Players.id
    val gameType = varchar("game_type", 50)
    val elo = integer("elo").default(1000)
    val wins = integer("wins").default(0)
    val gamesPlayed = integer("games_played").default(0)

    override val primaryKey = PrimaryKey(playerId, gameType)
}
