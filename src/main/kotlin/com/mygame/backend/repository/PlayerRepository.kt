package com.mygame.backend.repository

import com.mygame.backend.db.DatabaseFactory.dbQuery
import com.mygame.backend.db.tables.Players
import com.mygame.backend.models.Player
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID

class PlayerRepository {
    suspend fun findById(id: String): Player? = dbQuery {
        Players.selectAll().where { Players.id eq id }
            .map { toPlayer(it) }
            .singleOrNull()
    }

    suspend fun findByUsername(username: String): Player? = dbQuery {
        Players.selectAll().where { Players.username eq username }
            .map { toPlayer(it) }
            .singleOrNull()
    }

    suspend fun create(username: String, passwordPlain: String): Player? {
         if (findByUsername(username) != null) return null
         
         val hash = BCrypt.hashpw(passwordPlain, BCrypt.gensalt())
         val userId = UUID.randomUUID().toString()
         
         dbQuery {
            Players.insert {
                it[id] = userId
                it[this.username] = username
                it[this.passwordHash] = hash
                it[coins] = 1000
                it[xp] = 0
                it[level] = 1
                it[elo] = 1000
                it[gamesPlayed] = 0
                it[wins] = 0
                it[lastLogin] = System.currentTimeMillis()
                it[createdAt] = System.currentTimeMillis()
            }
        }
        return findById(userId)
    }

    suspend fun validateCredentials(username: String, passwordPlain: String): Player? = dbQuery {
        val row = Players.selectAll().where { Players.username eq username }.singleOrNull() ?: return@dbQuery null
        val storedHash = row[Players.passwordHash]
        
        if (BCrypt.checkpw(passwordPlain, storedHash)) {
            toPlayer(row)
        } else {
            null
        }
    }

    suspend fun updateCoins(id: String, amount: Long): Long? = dbQuery {
        val current = Players.selectAll().where { Players.id eq id }.singleOrNull() ?: return@dbQuery null
        val newBalance = current[Players.coins] + amount
        if (newBalance < 0) return@dbQuery null 

        Players.update({ Players.id eq id }) {
            it[coins] = newBalance
        }
        newBalance
    }

    suspend fun updateStats(id: String, xpDelta: Int, eloDelta: Int, isWin: Boolean) = dbQuery {
        val current = Players.selectAll().where { Players.id eq id }.singleOrNull() ?: return@dbQuery
        
        val newXp = current[Players.xp] + xpDelta
        val newElo = current[Players.elo] + eloDelta
        val newGames = current[Players.gamesPlayed] + 1
        val newWins = current[Players.wins] + (if (isWin) 1 else 0)

        Players.update({ Players.id eq id }) {
            it[xp] = newXp
            it[elo] = newElo
            it[gamesPlayed] = newGames
            it[wins] = newWins
        }
    }
    
    suspend fun getLeaderboard(limit: Int = 10): List<Player> = dbQuery {
        Players.selectAll()
            .orderBy(Players.elo to SortOrder.DESC)
            .limit(limit)
            .map { toPlayer(it) }
    }

    private fun toPlayer(row: ResultRow): Player = Player(
        id = row[Players.id],
        username = row[Players.username],
        coins = row[Players.coins],
        xp = row[Players.xp],
        level = row[Players.level],
        elo = row[Players.elo],
        gamesPlayed = row[Players.gamesPlayed],
        wins = row[Players.wins],
        createdAt = row[Players.createdAt]
    )
}
