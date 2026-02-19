package com.mygame.backend.db.tables

import org.jetbrains.exposed.sql.Table

object Players : Table() {
    val id = varchar("id", 128)
    val username = varchar("username", 50).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val coins = long("coins").default(500)
    val xp = integer("xp").default(0)
    val level = integer("level").default(1)
    val elo = integer("elo").default(1000)
    val gamesPlayed = integer("games_played").default(0)
    val wins = integer("wins").default(0)
    val lastLogin = long("last_login").default(0)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
