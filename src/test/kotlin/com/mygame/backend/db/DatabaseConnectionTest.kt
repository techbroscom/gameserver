package com.mygame.backend.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlin.test.Test
import kotlin.test.assertTrue

class DatabaseConnectionTest {
    @Test
    fun `test database connection`() {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:postgresql://ep-falling-wave-a1s2ibgv.ap-southeast-1.pg.koyeb.app/gameserver"
            username = "koyeb-adm"
            password = "npg_Q9rnRlIzv3ce"
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
