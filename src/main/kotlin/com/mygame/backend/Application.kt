package com.mygame.backend

import com.mygame.backend.plugins.configureWebSockets

import com.mygame.backend.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureDependencyInjection() // Must be first
    configureHTTP()
    configureSerialization()
    configureDatabase()
    configureAuthentication()
    configureWebSockets()
    configureRouting()
}
