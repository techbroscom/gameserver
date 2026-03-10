package com.mygame.backend.routes

import com.mygame.backend.economy.EconomyService
import com.mygame.backend.game.engine.GameEngineRegistry
import com.mygame.backend.models.LeaderboardEntryDto
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

@Serializable
data class UpdateUsernameRequest(
    val username: String
)

@Serializable
data class UpdateAvatarRequest(
    val avatarId: String
)

fun Route.apiRoutes(
    roomManager: RoomManager, 
    matchmakingService: MatchmakingService,
    playerRepository: PlayerRepository,
    coinTransactionRepository: CoinTransactionRepository,
    economyService: EconomyService
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
                    call.respond(player.toStatsDto())
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

            post("/collect-free") {
                val principal = call.principal<JWTPrincipal>()
                val playerId = principal?.payload?.getClaim("id")?.asString() ?: return@post
                
                val newBalance = playerRepository.collectFreeCoins(playerId)
                if (newBalance != null) {
                    coinTransactionRepository.create(playerId, 50, "FREE_REWARD", newBalance)
                    call.respond(mapOf("coins" to newBalance))
                } else {
                    call.respondText("Reward not ready yet", status = io.ktor.http.HttpStatusCode.TooManyRequests)
                }
            }

            post("/claim-daily-reward") {
                val principal = call.principal<JWTPrincipal>()
                val playerId = principal?.payload?.getClaim("id")?.asString() ?: return@post
                
                val result = economyService.claimDailyReward(playerId)
                if (result.success) {
                    call.respond(mapOf(
                        "amount" to result.amount,
                        "newBalance" to result.newBalance,
                        "streak" to result.streak
                    ))
                } else {
                    call.respondText("Daily reward already claimed or player not found", status = io.ktor.http.HttpStatusCode.BadRequest)
                }
            }

            post("/purchase") {
                val principal = call.principal<JWTPrincipal>()
                val playerId = principal?.payload?.getClaim("id")?.asString() ?: return@post
                val amount = call.request.queryParameters["amount"]?.toLongOrNull() ?: return@post call.respondText("Invalid amount", status = io.ktor.http.HttpStatusCode.BadRequest)
                
                val newBalance = playerRepository.purchaseCoins(playerId, amount)
                if (newBalance != null) {
                    coinTransactionRepository.create(playerId, amount, "PURCHASE", newBalance)
                    call.respond(mapOf("coins" to newBalance))
                } else {
                    call.respondText("Purchase failed", status = io.ktor.http.HttpStatusCode.BadRequest)
                }
            }

            post("/username") {
                val principal = call.principal<JWTPrincipal>()
                val playerId = principal?.payload?.getClaim("id")?.asString() ?: return@post
                val req = try { call.receive<UpdateUsernameRequest>() } catch(e: Exception) { return@post call.respondText("Invalid request format", status = io.ktor.http.HttpStatusCode.BadRequest) }
                
                val username = req.username.trim()
                if (username.isBlank()) {
                    return@post call.respondText("Username cannot be empty", status = io.ktor.http.HttpStatusCode.BadRequest)
                }
                
                val success = playerRepository.updateUsername(playerId, username)
                if (success) {
                    call.respond(mapOf("status" to "SUCCESS", "username" to username))
                } else {
                    val suggestions = mutableListOf<String>()
                    while(suggestions.size < 3) {
                        val suggested = username + kotlin.random.Random.nextInt(1000, 9999).toString()
                        if (!playerRepository.isUsernameTaken(suggested)) {
                            suggestions.add(suggested)
                        }
                    }
                    call.respond(io.ktor.http.HttpStatusCode.Conflict, mapOf(
                        "error" to "Username already taken",
                        "suggestions" to suggestions
                    ))
                }
            }

            post("/avatar") {
                val principal = call.principal<JWTPrincipal>()
                val playerId = principal?.payload?.getClaim("id")?.asString() ?: return@post
                val req = try { call.receive<UpdateAvatarRequest>() } catch(e: Exception) { return@post call.respondText("Invalid request format", status = io.ktor.http.HttpStatusCode.BadRequest) }
                
                val success = playerRepository.updateAvatar(playerId, req.avatarId)
                if (success) {
                    call.respond(mapOf("status" to "SUCCESS", "avatarId" to req.avatarId))
                } else {
                    call.respondText("Failed to update avatar", status = io.ktor.http.HttpStatusCode.InternalServerError)
                }
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
                LeaderboardEntryDto(it.authId, it.username, it.elo, it.wins, it.avatarId)
            }
            call.respond(leaders)
        }
    }
}
