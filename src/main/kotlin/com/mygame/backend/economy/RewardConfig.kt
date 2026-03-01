package com.mygame.backend.economy

data class GameReward(
    val entryFee: Long,
    val winnerCoins: Long,
    val loserCoins: Long,
    val winnerXp: Int,
    val loserXp: Int,
    val drawCoins: Long = 0,
    val drawXp: Int = 0
)

object RewardConfig {
    val BINGO = GameReward(
        entryFee = 10,
        winnerCoins = 100,
        loserCoins = -20,
        winnerXp = 50,
        loserXp = 10,
        drawCoins = 10,
        drawXp = 20
    )

    val NUMBER_GUESS = GameReward(
        entryFee = 0,
        winnerCoins = 50,
        loserCoins = -10,
        winnerXp = 30,
        loserXp = 5
    )

    val TIC_TAC_TOE = GameReward(
        entryFee = 10,
        winnerCoins = 50,
        loserCoins = -10,
        winnerXp = 30,
        loserXp = 5,
        drawCoins = 5,
        drawXp = 10
    )

    fun get(gameType: String): GameReward {
        return when (gameType) {
            "BINGO" -> BINGO
            "NUMBER_GUESS" -> NUMBER_GUESS
            "TIC_TAC_TOE" -> TIC_TAC_TOE
            else -> throw IllegalArgumentException("Unknown game type: $gameType")
        }
    }
}
