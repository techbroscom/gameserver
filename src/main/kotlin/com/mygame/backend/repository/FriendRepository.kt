package com.mygame.backend.repository

import com.mygame.backend.db.DatabaseFactory.dbQuery
import com.mygame.backend.db.tables.Friends
import com.mygame.backend.db.tables.Players
import com.mygame.backend.models.Player
import org.jetbrains.exposed.sql.*
import java.util.UUID

class FriendRepository {
    suspend fun addFriend(playerId: String, friendId: String): Boolean = dbQuery {
        if (isFriend(playerId, friendId)) return@dbQuery false

        Friends.insert {
            it[id] = UUID.randomUUID().toString()
            it[Friends.playerId] = playerId
            it[Friends.friendId] = friendId
            it[createdAt] = System.currentTimeMillis()
        }
        
        // Add reciprocal relationship
        Friends.insert {
            it[id] = UUID.randomUUID().toString()
            it[Friends.playerId] = friendId
            it[Friends.friendId] = playerId
            it[createdAt] = System.currentTimeMillis()
        }
        true
    }

    suspend fun isFriend(playerId: String, friendId: String): Boolean = dbQuery {
        Friends.selectAll().where { (Friends.playerId eq playerId) and (Friends.friendId eq friendId) }.count() > 0
    }

    suspend fun getFriendsForPlayer(playerId: String): List<String> = dbQuery {
        Friends.selectAll().where { Friends.playerId eq playerId }.map { it[Friends.friendId] }
    }
}
