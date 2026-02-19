package com.mygame.backend.di

import com.mygame.backend.db.DatabaseFactory
import com.mygame.backend.economy.EconomyService
import com.mygame.backend.game.GameStateManager
import com.mygame.backend.game.engine.GameEngineRegistry
import com.mygame.backend.game.engine.impl.BingoEngine
import com.mygame.backend.game.engine.impl.NumberGuessEngine
import com.mygame.backend.handler.GameHandler
import com.mygame.backend.repository.CoinTransactionRepository
import com.mygame.backend.repository.GameResultRepository
import com.mygame.backend.repository.PlayerRepository
import com.mygame.backend.room.MatchmakingService
import com.mygame.backend.room.RoomManager
import com.mygame.backend.session.SessionManager
import org.koin.dsl.module

val appModule = module {
    // Singletons
    single { DatabaseFactory }
    single { SessionManager() }
    single { GameStateManager() }
    
    // Repositories
    single { PlayerRepository() }
    single { CoinTransactionRepository() }
    single { GameResultRepository() }
    
    // Services
    single { EconomyService(get(), get()) }
    single { RoomManager(get(), get(), get(), get(), get()) }
    single { MatchmakingService(get(), get()) }
    
    // Handlers
    single { GameHandler(get(), get(), get()) }
    
    // Game Registry Init (eager)
    single(createdAtStart = true) { 
        GameEngineRegistry.register(BingoEngine())
        GameEngineRegistry.register(NumberGuessEngine())
        GameEngineRegistry
    }
}
