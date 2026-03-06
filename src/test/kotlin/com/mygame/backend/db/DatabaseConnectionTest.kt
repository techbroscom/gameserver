package com.mygame.backend.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlin.test.Test
import kotlin.test.assertTrue

class DatabaseConnectionTest {
    @Test
    fun `test database connection`() {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:postgresql://gameserver-techbroscommunity-8b49.a.aivencloud.com:11174/defaultdb?sslmode=require"
            username = "avnadmin"
            password = ""
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 1 // minimal pool for testing
            connectionTimeout = 5000 // 5 seconds timeout
        }
        
        HikariDataSource(config).use { ds ->
            ds.connection.use { conn ->
                assertTrue(conn.isValid(5), "Connection should be valid")
                println("Successfully connected to database!")
            }
        }
    }
}
