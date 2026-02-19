package com.mygame.backend.plugins

import com.mygame.backend.handler.GameHandler
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import org.koin.ktor.ext.inject
import kotlin.time.Duration.Companion.seconds

fun Application.configureWebSockets() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    
    val gameHandler by inject<GameHandler>()

    routing {
        authenticate("auth-jwt") {
            webSocket("/game") {
                val principal = call.principal<io.ktor.server.auth.jwt.JWTPrincipal>()
                val playerId = principal?.payload?.getClaim("id")?.asString()
                
                if (playerId != null) {
                    gameHandler.handleSession(this, playerId)
                } else {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No Player ID found in token"))
                }
            }
        }
    }
}
