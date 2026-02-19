package com.mygame.backend.db

import org.jetbrains.exposed.sql.Database
import kotlin.test.Test

class InspectExposed {
    @Test
    fun debug() {
        println("=== DEBUGGING EXPOSED VERSION ===")
        val clazz = Database.Companion::class.java
        val source = clazz.protectionDomain.codeSource
        println("Database.Companion loaded from: ${source?.location}")
        
        println("Declared methods on Database.Companion:")
        clazz.declaredMethods.forEach { method ->
            println(method.toString())
        }
        println("=== END DEBUG ===")
    }
}
