package com.mygame.backend.repository

import com.mygame.backend.db.DatabaseFactory.dbQuery
import com.mygame.backend.db.tables.CoinTransactions
import com.mygame.backend.models.CoinTransaction
import com.mygame.backend.util.IdGenerator
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class CoinTransactionRepository {
    suspend fun create(playerId: String, amount: Long, type: String, balanceAfter: Long) = dbQuery {
        CoinTransactions.insert {
            it[id] = IdGenerator.generate()
            it[this.playerId] = playerId
            it[this.delta] = amount
            it[this.reason] = type
            it[this.balanceAfter] = balanceAfter
            it[createdAt] = System.currentTimeMillis()
        }
    }

    suspend fun getHistory(playerId: String, limit: Int = 50, offset: Long = 0): List<CoinTransaction> = dbQuery {
        CoinTransactions.selectAll().where { CoinTransactions.playerId eq playerId }
            .orderBy(CoinTransactions.createdAt to SortOrder.DESC)
            .limit(limit, offset)
            .map {
                CoinTransaction(
                    id = it[CoinTransactions.id],
                    playerId = it[CoinTransactions.playerId],
                    amount = it[CoinTransactions.delta],
                    type = it[CoinTransactions.reason],
                    timestamp = it[CoinTransactions.createdAt],
                    balanceAfter = it[CoinTransactions.balanceAfter]
                )
            }
    }
}
