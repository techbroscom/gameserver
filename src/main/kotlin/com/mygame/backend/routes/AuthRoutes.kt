package com.mygame.backend.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.mygame.backend.repository.PlayerRepository
import com.mygame.backend.models.AuthRequest
import com.mygame.backend.models.AuthResponse
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.config.*
import java.util.*

fun Route.authRoutes(playerRepository: PlayerRepository, config: ApplicationConfig) {
    val secret = config.property("jwt.secret").getString()
    val issuer = config.property("jwt.issuer").getString()
    val audience = config.property("jwt.audience").getString()
    
    post("/auth/register") {
        val request = try {
            call.receive<AuthRequest>()
        } catch (e: Exception) {
            return@post call.respondText("Invalid request format", status = io.ktor.http.HttpStatusCode.BadRequest)
        }
        val username = request.username
        val password = request.password

        val player = playerRepository.create(username, password)
        if (player != null) {
            val token = JWT.create()
                .withAudience(audience)
                .withIssuer(issuer)
                .withClaim("id", player.id)
                .withExpiresAt(Date(System.currentTimeMillis() + 86400000)) // 24h
                .sign(Algorithm.HMAC256(secret))
                
            call.respond(AuthResponse(token, player.toDto()))
        } else {
            call.respondText("Username already exists", status = io.ktor.http.HttpStatusCode.Conflict)
        }
    }

    post("/auth/login") {
        val request = try {
            call.receive<AuthRequest>()
        } catch (e: Exception) {
            return@post call.respondText("Invalid request format", status = io.ktor.http.HttpStatusCode.BadRequest)
        }
        val username = request.username
        val password = request.password
        
        val player = playerRepository.validateCredentials(username, password)
        if (player != null) {
            val token = JWT.create()
                .withAudience(audience)
                .withIssuer(issuer)
                .withClaim("id", player.id)
                .withExpiresAt(Date(System.currentTimeMillis() + 86400000)) // 24h
                .sign(Algorithm.HMAC256(secret))
            
            call.respond(AuthResponse(token, player.toDto()))
        } else {
            call.respondText("Invalid credentials", status = io.ktor.http.HttpStatusCode.Unauthorized)
        }
    }
}
