package com.mygame.backend.economy

import com.mygame.backend.game.engine.GameResult
import com.mygame.backend.repository.CoinTransactionRepository
import com.mygame.backend.repository.PlayerRepository
import org.slf4j.LoggerFactory

class EconomyService(
    private val playerRepository: PlayerRepository,
    private val transactionRepository: CoinTransactionRepository
) {
    private val logger = LoggerFactory.getLogger(EconomyService::class.java)

    suspend fun deductEntryFee(playerId: String, gameType: String): Boolean {
        val config = RewardConfig.get(gameType)
        if (config.entryFee <= 0) return true

        val player = playerRepository.findById(playerId) ?: return false
        if (player.coins < config.entryFee) return false

        val newBalance = playerRepository.updateCoins(playerId, -config.entryFee)
        if (newBalance != null) {
            transactionRepository.create(
                playerId, -config.entryFee, "ENTRY_FEE", newBalance
            )
            return true
        }
        return false
    }

    suspend fun applyGameResult(roomId: String, result: GameResult) {
        // Apply Coins and XP
        result.coinDeltas.forEach { (playerId, delta) ->
            val newCoins = playerRepository.updateCoins(playerId, delta)
            if (newCoins != null) {
                val reason = if (delta > 0) "GAME_WIN" else "GAME_LOSS"
                transactionRepository.create(playerId, delta, reason, newCoins)
            }
        }
        
        // Apply XP and Check Level Up
        result.xpDeltas.forEach { (playerId, xpDelta) ->
            val isWin = result.winnerIds.contains(playerId)
            val eloDelta = if (isWin) 25 else -15 
            
            playerRepository.updateStats(playerId, xpDelta, eloDelta, isWin)
            
            checkLevelUp(playerId)
        }
    }

    private suspend fun checkLevelUp(playerId: String) {
        val player = playerRepository.findById(playerId) ?: return
        val currentLevel = player.level
        val nextLevelXp = calculateXpForLevel(currentLevel + 1)
        
        if (player.xp >= nextLevelXp) {
            val newLevel = currentLevel + 1
            logger.info("Player $playerId leveled up to $newLevel!")
            // DB update for level would go here
        }
    }

    private fun calculateXpForLevel(level: Int): Int {
        return when (level) {
            1 -> 0
            2 -> 100
            3 -> 300
            4 -> 600
            5 -> 1000
            else -> 1000 + (level - 5) * 500
        }
    }
}
