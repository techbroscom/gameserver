package com.mygame.backend.plugins

import com.mygame.backend.repository.CoinTransactionRepository
import com.mygame.backend.repository.PlayerRepository
import com.mygame.backend.room.MatchmakingService
import com.mygame.backend.room.RoomManager
import com.mygame.backend.routes.apiRoutes
import com.mygame.backend.routes.authRoutes
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    val playerRepository by inject<PlayerRepository>()
    val roomManager by inject<RoomManager>()
    val matchmakingService by inject<MatchmakingService>()
    val coinTransactionRepository by inject<CoinTransactionRepository>()
    
    routing {
        get("/") {
            call.respondText("Game Server is Running!")
        }
        get("/health") {
            call.respondText("OK")
        }
        
        authRoutes(playerRepository, environment.config)
        apiRoutes(roomManager, matchmakingService, playerRepository, coinTransactionRepository)
    }
}
