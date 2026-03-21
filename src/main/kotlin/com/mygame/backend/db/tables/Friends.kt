package com.mygame.backend.db.tables

import org.jetbrains.exposed.sql.Table

object Friends : Table("friends") {
    val id = varchar("id", 50)
    val playerId = varchar("player_id", 50)
    val friendId = varchar("friend_id", 50)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
