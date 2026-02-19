package com.mygame.backend.session

import com.mygame.backend.models.ServerMessage
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class PlayerSession(
    val playerId: String,
    val session: WebSocketSession
) {
    suspend fun send(message: ServerMessage) {
        val json = Json.encodeToString(message)
        try {
            session.send(Frame.Text(json))
        } catch (e: Exception) {
            // Handle disconnection or error
        }
    }
}
