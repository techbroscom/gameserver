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

    suspend fun findByAuthId(authId: String): Player? = dbQuery {
        Players.selectAll().where { Players.authId eq authId }
            .map { toPlayer(it) }
            .singleOrNull()
    }

    suspend fun create(requestedAuthId: String, passwordPlain: String): Player? {
         if (findByAuthId(requestedAuthId) != null) return null
         
         val hash = BCrypt.hashpw(passwordPlain, BCrypt.gensalt())
         val userId = UUID.randomUUID().toString()
         
         dbQuery {
            Players.insert {
                it[id] = userId
                it[authId] = requestedAuthId
                it[username] = null
                it[passwordHash] = hash
                it[coins] = 1000
                it[xp] = 0
                it[level] = 1
                it[elo] = 1000
                it[gamesPlayed] = 0
                it[wins] = 0
                it[lastLogin] = System.currentTimeMillis()
                it[lastFreeCoinsCollectedAt] = 0L
                it[createdAt] = System.currentTimeMillis()
            }
        }
        return findById(userId)
    }

    suspend fun validateCredentials(authId: String, passwordPlain: String): Player? = dbQuery {
        val row = Players.selectAll().where { Players.authId eq authId }.singleOrNull() ?: return@dbQuery null
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

    suspend fun collectFreeCoins(id: String): Long? = dbQuery {
        val player = Players.selectAll().where { Players.id eq id }.singleOrNull() ?: return@dbQuery null
        val now = System.currentTimeMillis()
        val lastCollected = player[Players.lastFreeCoinsCollectedAt]
        val fourHours = 4 * 60 * 60 * 1000L

        if (now - lastCollected < fourHours) return@dbQuery null

        val newBalance = player[Players.coins] + 50
        Players.update({ Players.id eq id }) {
            it[coins] = newBalance
            it[lastFreeCoinsCollectedAt] = now
        }
        newBalance
    }

    suspend fun updateLoginStreak(id: String, now: Long) = dbQuery {
        val player = Players.selectAll().where { Players.id eq id }.singleOrNull() ?: return@dbQuery
        val lastLogin = player[Players.lastLogin]
        val currentStreak = player[Players.loginStreak]
        
        val oneDayMs = 24 * 60 * 60 * 1000L
        val twoDaysMs = 48 * 60 * 60 * 1000L
        
        val newStreak = when {
            lastLogin == 0L -> 1
            now - lastLogin < oneDayMs -> currentStreak // Same day login, no change
            now - lastLogin < twoDaysMs -> currentStreak + 1 // Consecutive day
            else -> 1 // Streak broken
        }
        
        Players.update({ Players.id eq id }) {
            it[Players.lastLogin] = now
            it[Players.loginStreak] = newStreak
        }
    }

    suspend fun claimDailyReward(id: String, amount: Long, now: Long): Long? = dbQuery {
        val player = Players.selectAll().where { Players.id eq id }.singleOrNull() ?: return@dbQuery null
        val lastClaimed = player[Players.lastDailyRewardClaimedAt]
        val oneDayMs = 24 * 60 * 60 * 1000L

        if (now - lastClaimed < oneDayMs) return@dbQuery null

        val newBalance = player[Players.coins] + amount
        Players.update({ Players.id eq id }) {
            it[coins] = newBalance
            it[lastDailyRewardClaimedAt] = now
        }
        newBalance
    }

    suspend fun updateLevel(id: String, newLevel: Int) = dbQuery {
        Players.update({ Players.id eq id }) {
            it[level] = newLevel
        }
    }

    suspend fun purchaseCoins(id: String, amount: Long): Long? = updateCoins(id, amount)

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

    suspend fun isUsernameTaken(username: String): Boolean = dbQuery {
        Players.selectAll().where { Players.username eq username }.count() > 0
    }

    suspend fun updateUsername(id: String, username: String): Boolean = dbQuery {
        if (isUsernameTaken(username)) return@dbQuery false
        
        val rowsUpdated = Players.update({ Players.id eq id }) {
            it[Players.username] = username
        }
        rowsUpdated > 0
    }

    suspend fun updateAvatar(id: String, avatarId: String): Boolean = dbQuery {
        val rowsUpdated = Players.update({ Players.id eq id }) {
            it[Players.avatarId] = avatarId
        }
        rowsUpdated > 0
    }

    private fun toPlayer(row: ResultRow): Player = Player(
        id = row[Players.id],
        authId = row[Players.authId],
        username = row[Players.username],
        coins = row[Players.coins],
        xp = row[Players.xp],
        level = row[Players.level],
        elo = row[Players.elo],
        gamesPlayed = row[Players.gamesPlayed],
        wins = row[Players.wins],
        lastFreeCoinsCollectedAt = row[Players.lastFreeCoinsCollectedAt],
        lastDailyRewardClaimedAt = row[Players.lastDailyRewardClaimedAt],
        loginStreak = row[Players.loginStreak],
        avatarId = row[Players.avatarId],
        createdAt = row[Players.createdAt]
    )
}
