package com.cainghien.companion

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Test

class ViewMappingTest {
    @Test
    fun `square image in square view fits exactly`() {
        val r = ViewMapping.contentRect(100, 100, 200, 200)
        assertEquals(0f, r.left, 1e-4f)
        assertEquals(0f, r.top, 1e-4f)
        assertEquals(200f, r.right, 1e-4f)
        assertEquals(200f, r.bottom, 1e-4f)
    }

    @Test
    fun `wide image in tall view covers and crops horizontally`() {
        // 400x300 image into 100x100 view: scale by height (100/300), width overflows.
        val r = ViewMapping.contentRect(400, 300, 100, 100)
        assertEquals(100f, r.height, 1e-4f)
        assertEquals(133.3333f, r.width, 1e-3f)
        assertEquals(-16.6667f, r.left, 1e-3f)
        assertEquals(0f, r.top, 1e-4f)
    }

    @Test
    fun `tall image in wide view covers and crops vertically`() {
        val r = ViewMapping.contentRect(300, 400, 100, 100)
        assertEquals(100f, r.width, 1e-4f)
        assertEquals(133.3333f, r.height, 1e-3f)
        assertEquals(0f, r.left, 1e-4f)
        assertEquals(-16.6667f, r.top, 1e-3f)
    }

    @Test
    fun `center landmark lands at view center regardless of crop`() {
        val r = ViewMapping.contentRect(400, 300, 100, 100)
        val (x, y) = ViewMapping.toView(0.5f, 0.5f, r, mirrored = false)
        assertEquals(50f, x, 1e-3f)
        assertEquals(50f, y, 1e-3f)
    }

    @Test
    fun `corners map to content rect corners`() {
        val r = ViewMapping.contentRect(400, 300, 100, 100)
        val (tlx, tly) = ViewMapping.toView(0f, 0f, r, mirrored = false)
        assertEquals(r.left, tlx, 1e-3f)
        assertEquals(r.top, tly, 1e-3f)
        val (brx, bry) = ViewMapping.toView(1f, 1f, r, mirrored = false)
        assertEquals(r.right, brx, 1e-3f)
        assertEquals(r.bottom, bry, 1e-3f)
    }

    @Test
    fun `mirror flips horizontally about the view center`() {
        val r = ViewMapping.contentRect(100, 100, 200, 200)
        val (x, y) = ViewMapping.toView(0.25f, 0.5f, r, mirrored = true)
        assertEquals(150f, x, 1e-3f)
        assertEquals(100f, y, 1e-3f)
        val (cx, _) = ViewMapping.toView(0.5f, 0.5f, r, mirrored = true)
        assertEquals(100f, cx, 1e-3f)
    }

    @Test
    fun `degenerate sizes fall back to the view rect`() {
        val r = ViewMapping.contentRect(0, 0, 200, 200)
        assertEquals(0f, r.left, 1e-4f)
        assertEquals(200f, r.right, 1e-4f)
        val (x, y) = ViewMapping.toView(0.5f, 0.5f, r, mirrored = false)
        assertEquals(100f, x, 1e-3f)
        assertEquals(100f, y, 1e-3f)
    }

    @Test
    fun `landmarks from a rotated frame land on the unrotated preview`() {
        val r = ViewMapping.contentRect(300, 400, 300, 400)
        for (mirrored in listOf(false, true)) {
            for (rotation in listOf(0, 90, 180, 270)) {
                val (x, y) = rotated(0.2f, 0.7f, rotation)
                val (vx, vy) = ViewMapping.toView(x, y, r, mirrored, rotation)
                val label = "rotation=$rotation mirrored=$mirrored"
                assertEquals(label, if (mirrored) 240f else 60f, vx, 1e-3f)
                assertEquals(label, 280f, vy, 1e-3f)
            }
        }
    }

    @Test
    fun `upright coordinates keep pixel distances for every rotation`() {
        val r = ViewMapping.contentRect(300, 400, 300, 400)
        val expected = hypot((0.5f - 0.2f) * 300f, (0.1f - 0.7f) * 400f)
        for (rotation in listOf(0, 90, 180, 270)) {
            val (ax, ay) = rotated(0.2f, 0.7f, rotation).let { ViewMapping.toUpright(it.first, it.second, r, rotation) }
            val (bx, by) = rotated(0.5f, 0.1f, rotation).let { ViewMapping.toUpright(it.first, it.second, r, rotation) }
            assertEquals("rotation=$rotation", expected, hypot(ax - bx, ay - by), 1e-2f)
        }
    }

    // Normalised position after Matrix.postRotate turns the frame clockwise.
    private fun rotated(x: Float, y: Float, rotation: Int): Pair<Float, Float> = when (rotation) {
        90 -> (1f - y) to x
        180 -> (1f - x) to (1f - y)
        270 -> y to (1f - x)
        else -> x to y
    }
}