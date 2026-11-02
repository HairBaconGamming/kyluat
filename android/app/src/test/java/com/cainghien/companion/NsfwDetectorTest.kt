package com.cainghien.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NsfwDetectorTest {

    @Test
    fun labelsFollowTheModelOrder() {
        assertEquals(listOf("drawings", "hentai", "neutral", "porn", "sexy"), NsfwDetector.LABELS)
    }

    @Test
    fun unsafeScoreAddsHentaiAndPornOnly() {
        val scores = floatArrayOf(0.1f, 0.25f, 0.2f, 0.4f, 0.05f)
        assertEquals(0.65f, NsfwDetector.unsafeScore(scores), 0.0001f)
    }

    @Test
    fun tilesCoverCentreAndEveryCorner() {
        val tiles = NsfwDetector.TILE_FRACTIONS
        assertEquals(5, tiles.size)
        for (tile in tiles) {
            assertTrue(tile[0] in 0f..1f && tile[1] in 0f..1f)
            assertTrue(tile[2] in 0f..1f && tile[3] in 0f..1f)
            assertTrue(tile[0] < tile[2])
            assertTrue(tile[1] < tile[3])
        }
        assertTrue(tiles.any { it[0] > 0f && it[1] > 0f })
        assertTrue(tiles.any { it[2] < 1f && it[3] < 1f })
    }
}
