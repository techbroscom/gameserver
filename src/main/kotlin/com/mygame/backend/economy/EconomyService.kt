package com.mygame.backend.economy

import com.mygame.backend.game.engine.GameResult
import com.mygame.backend.repository.CoinTransactionRepository
import com.mygame.backend.repository.PlayerRepository
import org.slf4j.LoggerFactory

class EconomyService(
    private val playerRepository: PlayerRepository,
    private val transactionRepository: CoinTransactionRepository
) {
    data class DailyRewardResult(val success: Boolean, val amount: Long = 0, val newBalance: Long = 0, val streak: Int = 0)
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

    suspend fun claimDailyReward(playerId: String): DailyRewardResult {
        val player = playerRepository.findById(playerId) ?: return DailyRewardResult(false)
        val now = System.currentTimeMillis()
        
        // Update streak first
        playerRepository.updateLoginStreak(playerId, now)
        val updatedPlayer = playerRepository.findById(playerId) ?: return DailyRewardResult(false)
        
        val baseReward = RewardConfig.DAILY_LOGIN_REWARD
        val streakBonus = (updatedPlayer.loginStreak - 1) * RewardConfig.STREAK_BONUS
        val totalReward = baseReward + streakBonus
        
        val newBalance = playerRepository.claimDailyReward(playerId, totalReward, now)
        return if (newBalance != null) {
            transactionRepository.create(playerId, totalReward, "DAILY_LOGIN_REWARD", newBalance)
            DailyRewardResult(true, totalReward, newBalance, updatedPlayer.loginStreak)
        } else {
            DailyRewardResult(false)
        }
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
            
            playerRepository.updateLevel(playerId, newLevel)
            
            val coinReward = RewardConfig.LEVEL_UP_REWARD_COINS
            val eloReward = RewardConfig.LEVEL_UP_REWARD_ELO
            
            val newBalance = playerRepository.updateCoins(playerId, coinReward)
            if (newBalance != null) {
                transactionRepository.create(playerId, coinReward, "LEVEL_UP_REWARD", newBalance)
            }
            
            // Stats update for ELO on level up
            playerRepository.updateStats(playerId, 0, eloReward, false) // false is win but we don't increment win count here
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
