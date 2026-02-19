package com.mygame.backend.economy

import com.mygame.backend.models.Player
import com.mygame.backend.repository.CoinTransactionRepository
import com.mygame.backend.repository.PlayerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EconomyServiceTest {
    private val playerRepo = mockk<PlayerRepository>(relaxed = true)
    private val transactionRepo = mockk<CoinTransactionRepository>(relaxed = true)
    private val service = EconomyService(playerRepo, transactionRepo)

    @Test
    fun `deduct entry fee success`() = runTest {
        val player = Player("p1", "Player1", coins = 100)
        coEvery { playerRepo.findById("p1") } returns player
        coEvery { playerRepo.updateCoins("p1", any()) } returns 90
        
        val result = service.deductEntryFee("p1", "BINGO") // Fee 10
        
        assertTrue(result)
        coVerify { transactionRepo.create("p1", -10, "ENTRY_FEE", 90) }
    }

    @Test
    fun `deduct entry fee insufficient funds`() = runTest {
        val player = Player("p1", "Player1", coins = 5)
        coEvery { playerRepo.findById("p1") } returns player
        
        val result = service.deductEntryFee("p1", "BINGO") // Fee 10
        
        assertFalse(result)
        coVerify(exactly = 0) { transactionRepo.create(any(), any(), any(), any()) }
    }
}
