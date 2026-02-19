package com.mygame.backend.session

import io.ktor.websocket.*
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionManagerTest {
    private val manager = SessionManager()
    private val session = mockk<WebSocketSession>()

    @Test
    fun `test add and get session`() {
        val playerSession = PlayerSession("p1", session)
        manager.addSession("p1", playerSession)
        
        assertEquals(playerSession, manager.getSession("p1"))
    }

    @Test
    fun `test remove session`() {
        val playerSession = PlayerSession("p1", session)
        manager.addSession("p1", playerSession)
        manager.removeSession("p1")
        
        assertNull(manager.getSession("p1"))
    }
}
