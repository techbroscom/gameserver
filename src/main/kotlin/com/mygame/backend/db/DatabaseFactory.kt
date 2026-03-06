package com.mygame.backend.db

import com.mygame.backend.db.tables.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init(config: ApplicationConfig) {
        val driverClassName = config.property("storage.driverClassName").getString()
        val jdbcURL = config.property("storage.jdbcURL").getString()
        val user = config.property("storage.user").getString()
        val password = config.property("storage.password").getString()

        val database = Database.connect(
            datasource = hikari(driverClassName, jdbcURL, user, password)
        )

        transaction(database) {
            // Check if the old 'gamename' column exists. If it does, we assume 'username' is the old login name
            // and we need to rename them to 'auth_id' and 'username' respectively.
            exec("""
                DO ${'$'}${'$'}
                BEGIN
                    IF EXISTS (
                        SELECT 1 
                        FROM information_schema.columns 
                        WHERE table_name = 'players' AND column_name = 'gamename'
                    ) THEN
                        -- Rename the old login identifier 'username' to 'auth_id'
                        ALTER TABLE players RENAME COLUMN username TO auth_id;
                        -- Rename the old display name 'gamename' to 'username'
                        ALTER TABLE players RENAME COLUMN gamename TO username;
                    END IF;
                END
                ${'$'}${'$'};
            """)

            SchemaUtils.createMissingTablesAndColumns(Players, CoinTransactions, GameResults)
        }
    }

    private fun hikari(driver: String, url: String, user: String, pass: String): HikariDataSource {
        val config = HikariConfig().apply {
            driverClassName = driver
            jdbcUrl = url
            username = user
            password = pass
            maximumPoolSize = 5
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        }
        return HikariDataSource(config)
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}

