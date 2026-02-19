package com.mygame.backend.routes

import com.mygame.backend.game.engine.GameEngineRegistry
import com.mygame.backend.repository.CoinTransactionRepository
import com.mygame.backend.repository.PlayerRepository
import com.mygame.backend.room.MatchmakingService
import com.mygame.backend.room.RoomManager
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class MatchmakingRequest(
    val gameType: String = "BINGO",
    val minPlayers: Int = 2,
    val maxPlayers: Int = 2
)

fun Route.apiRoutes(
    roomManager: RoomManager, 
    matchmakingService: MatchmakingService,
    playerRepository: PlayerRepository,
    coinTransactionRepository: CoinTransactionRepository
) {
    authenticate("auth-jwt") {
        route("/rooms") {
            get {
                val type = call.request.queryParameters["gameType"]
                val rooms = roomManager.listRooms(type)
                call.respond(rooms.map { it.toDto() })
            }
            get("/{id}") {
                val id = call.parameters["id"] ?: return@get call.respondText("Missing ID", status = io.ktor.http.HttpStatusCode.BadRequest)
                val room = roomManager.getRoom(id)
                if (room != null) {
                    call.respond(room.toDto())
                } else {
                    call.respondText("Room not found", status = io.ktor.http.HttpStatusCode.NotFound)
                }
            }
        }
        
        route("/players") {
            get("/{id}/stats") {
                val id = call.parameters["id"] ?: return@get call.respondText("Missing ID", status = io.ktor.http.HttpStatusCode.BadRequest)
                val player = playerRepository.findById(id)
                if (player != null) {
                    call.respond(mapOf(
                        "id" to player.id,
                        "username" to player.username,
                        "coins" to player.coins,
                        "xp" to player.xp,
                        "level" to player.level,
                        "elo" to player.elo,
                        "gamesPlayed" to player.gamesPlayed,
                        "wins" to player.wins
                    ))
                } else {
                    call.respondText("Player not found", status = io.ktor.http.HttpStatusCode.NotFound)
                }
            }
            
            get("/{id}/transactions") {
                val id = call.parameters["id"] ?: return@get call.respondText("Missing ID", status = io.ktor.http.HttpStatusCode.BadRequest)
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L
                val history = coinTransactionRepository.getHistory(id, limit, offset)
                call.respond(history)
            }
        }
        
        route("/matchmaking") {
            post("/join") {
                val principal = call.principal<JWTPrincipal>()
                val playerId = principal?.payload?.getClaim("id")?.asString() ?: return@post
                val req = try { call.receive<MatchmakingRequest>() } catch(e: Exception) { MatchmakingRequest() }
                
                matchmakingService.addToQueue(playerId, req.gameType, req.minPlayers, req.maxPlayers)
                call.respond(mapOf("status" to "QUEUED"))
            }
            
            post("/leave") {
                val principal = call.principal<JWTPrincipal>()
                val playerId = principal?.payload?.getClaim("id")?.asString() ?: return@post
                matchmakingService.removeFromQueue(playerId)
                call.respond(mapOf("status" to "LEFT_QUEUE"))
            }
        }
        
        get("/games/types") {
            call.respond(GameEngineRegistry.listTypes())
        }
        
        get("/leaderboard") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val leaders = playerRepository.getLeaderboard(limit).map { 
                mapOf("username" to it.username, "elo" to it.elo, "wins" to it.wins)
            }
            call.respond(leaders)
        }
    }
}
