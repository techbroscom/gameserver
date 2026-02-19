package com.mygame.backend.room

import com.mygame.backend.economy.EconomyService
import com.mygame.backend.game.GameStateManager
import com.mygame.backend.models.RoomState
import com.mygame.backend.repository.GameResultRepository
import com.mygame.backend.repository.PlayerRepository
import com.mygame.backend.session.SessionManager
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RoomManagerTest {
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val gameStateManager = mockk<GameStateManager>(relaxed = true)
    private val economyService = mockk<EconomyService>(relaxed = true)
    private val playerRepository = mockk<PlayerRepository>(relaxed = true)
    private val gameResultRepository = mockk<GameResultRepository>(relaxed = true)
    
    private val roomManager = RoomManager(
        sessionManager, gameStateManager, economyService, playerRepository, gameResultRepository
    )

    @Test
    fun `test create room`() {
        val room = roomManager.createRoom("host", "My Room", "BINGO", 2, false, null, 10)
        
        assertNotNull(room)
        assertEquals("My Room", room.name)
        assertEquals("BINGO", room.gameType)
        assertEquals(RoomState.WAITING, room.state)
    }

    @Test
    fun `test join room`() = runTest {
        val room = roomManager.createRoom("host", "My Room", "BINGO", 2, false, null, 0)
        
        // Mock successful fee deduction and player lookup
        coEvery { economyService.deductEntryFee("p2", "BINGO") } returns true
        coEvery { playerRepository.findById("p2") } returns mockk(relaxed = true)
        
        val joined = roomManager.joinRoom("p2", room.id, null)
        
        assertNotNull(joined)
        assertEquals(1, joined.players.size) // host didn't join automatically in this test call, manual join
    }
}
