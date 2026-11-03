package com.cainghien.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFocusTest {
    private val now = 1_000_000_000L
    private val end = now + 25 * 60_000L

    @Test
    fun `unpaired phone runs focus on its own`() {
        assertTrue(localFocusActive(end, 0L, now))
        assertEquals(25 * 60, localFocusRemainingSeconds(end, 0L, now))
        assertTrue(localFocusText(end, 0L, now).startsWith("Đang focus"))
        assertFalse(localFocusFinished(end, 0L, now))
    }

    @Test
    fun `no local focus before one is started`() {
        assertFalse(localFocusActive(0L, 0L, now))
        assertEquals(0, localFocusRemainingSeconds(0L, 0L, now))
        assertEquals("", localFocusText(0L, 0L, now))
        assertFalse(localFocusFinished(0L, 0L, now))
    }

    @Test
    fun `expired session finishes and is ready to clear`() {
        assertTrue(localFocusFinished(end, 0L, end))
        assertFalse(localFocusActive(end, 0L, end + 1))
    }

    @Test
    fun `pending stop unlocks earlier than the session end`() {
        val stopAfter = now + 5 * 60_000L
        assertTrue(localFocusActive(end, stopAfter, now))
        assertEquals(5 * 60, localFocusRemainingSeconds(end, stopAfter, now))
        assertTrue(localFocusText(end, stopAfter, now).startsWith("Đang chờ dừng"))
        assertTrue(localFocusFinished(end, stopAfter, stopAfter))
    }

    @Test
    fun `stop later than the deadline never extends the session`() {
        val stopAfter = end + 10 * 60_000L
        assertTrue(localFocusActive(end, stopAfter, now))
        assertEquals(25 * 60, localFocusRemainingSeconds(end, stopAfter, now))
        assertFalse(localFocusFinished(end, stopAfter, now))
    }
}
