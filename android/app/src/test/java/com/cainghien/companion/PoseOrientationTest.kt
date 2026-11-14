package com.cainghien.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseOrientationTest {
    @Test
    fun `physical orientation works without display autorotation`() {
        for ((degrees, surfaceRotation) in listOf(0 to 0, 90 to 3, 180 to 2, 270 to 1, 359 to 0)) {
            val orientation = PoseOrientation()
            assertTrue(orientation.update(degrees))
            assertEquals(surfaceRotation, orientation.targetRotation)
        }
    }

    @Test
    fun `sensor jitter near a quadrant boundary does not change coordinates`() {
        val orientation = PoseOrientation()
        orientation.update(0)
        for (degrees in listOf(44, 47, 43, 59, 359, 301)) {
            assertFalse(orientation.update(degrees))
            assertEquals(0, orientation.targetRotation)
        }
        assertTrue(orientation.update(60))
        assertEquals(3, orientation.targetRotation)
        for (degrees in listOf(46, 44, 31, 90, 149)) assertFalse(orientation.update(degrees))
        assertTrue(orientation.update(30))
        assertEquals(0, orientation.targetRotation)
    }

    @Test
    fun `unknown orientation cannot silently reuse gravity and invalidates old frames`() {
        val orientation = PoseOrientation()
        assertNull(orientation.targetRotation)
        assertFalse(orientation.update(-1))
        orientation.update(90)
        val frameRevision = orientation.revision
        assertTrue(orientation.update(-1))
        assertNull(orientation.targetRotation)
        assertTrue(orientation.revision > frameRevision)
        assertFalse(orientation.update(-1))
        orientation.update(90)
        assertEquals(3, orientation.targetRotation)
        assertTrue(orientation.revision > frameRevision)
    }

    @Test
    fun `rotating away and back still invalidates an in flight frame`() {
        val orientation = PoseOrientation()
        orientation.update(0)
        val frameRevision = orientation.revision
        orientation.update(90)
        orientation.update(0)
        assertEquals(0, orientation.targetRotation)
        assertEquals(frameRevision + 2, orientation.revision)
    }

    @Test
    fun `extra bitmap rotation accounts for baked display rotation and both lens directions`() {
        for (sensor in listOf(0, 90, 180, 270)) {
            for (display in 0..3) {
                for (physical in 0..3) {
                    for (front in listOf(false, true)) {
                        fun sensorRelativeTo(target: Int): Int =
                            (sensor + (if (front) target * 90 else -target * 90) + 360) % 360
                        val extra = PoseOrientation.inputRotation(sensorRelativeTo(display), sensorRelativeTo(physical))
                        assertEquals(sensorRelativeTo(physical), (sensorRelativeTo(display) + extra) % 360)
                        if (physical == display) assertEquals(0, extra)
                    }
                }
            }
        }
        assertEquals(90, PoseOrientation.inputRotation(90, 180))
        assertEquals(270, PoseOrientation.inputRotation(270, 180))
    }
}
