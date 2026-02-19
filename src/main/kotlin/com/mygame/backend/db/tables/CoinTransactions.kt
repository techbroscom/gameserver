package com.mygame.backend.db.tables

import org.jetbrains.exposed.sql.Table

object CoinTransactions : Table() {
    val id = varchar("id", 128)
    val playerId = reference("player_id", Players.id)
    val delta = long("delta")
    val reason = varchar("reason", 50)
    val balanceAfter = long("balance_after")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
