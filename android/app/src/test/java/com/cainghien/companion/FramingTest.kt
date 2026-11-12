package com.cainghien.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FramingTest {
    @Test
    fun `ankle above the visible bottom needs no cue`() {
        assertNull(Framing.feetCue(600f, 680f))
        assertNull(Framing.feetCue(680f, 680f))
    }

    @Test
    fun `ankle behind the panel raises the placement cue`() {
        assertEquals(Framing.FEET_HIDDEN_CUE, Framing.feetCue(681f, 680f))
        assertEquals(Framing.FEET_HIDDEN_CUE, Framing.feetCue(1000f, 680f))
    }

    @Test
    fun `missing or nonfinite ankle never raises the cue`() {
        assertNull(Framing.feetCue(null, 680f))
        assertNull(Framing.feetCue(Float.NaN, 680f))
        assertNull(Framing.feetCue(Float.POSITIVE_INFINITY, 680f))
    }
}