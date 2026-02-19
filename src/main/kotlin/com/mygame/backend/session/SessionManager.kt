package com.mygame.backend.session

import java.util.concurrent.ConcurrentHashMap

class SessionManager {
    private val sessions = ConcurrentHashMap<String, PlayerSession>()

    fun addSession(playerId: String, session: PlayerSession) {
        sessions[playerId] = session
    }

    fun removeSession(playerId: String) {
        sessions.remove(playerId)
    }

    fun getSession(playerId: String): PlayerSession? {
        return sessions[playerId]
    }
    
    fun getAllSessions(): List<PlayerSession> {
        return sessions.values.toList()
    }
}
