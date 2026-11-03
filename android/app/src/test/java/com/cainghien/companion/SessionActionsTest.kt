package com.cainghien.companion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionActionsTest {
    @Test
    fun `scheduled focus does not expose manual session actions`() {
        assertFalse(hasManualSession(null))
        assertFalse(hasManualSession(""))
        assertFalse(hasManualSession("   "))
    }

    @Test
    fun `manual session keeps its controls and countdown`() {
        assertTrue(hasManualSession("Phone focus"))
    }
}
