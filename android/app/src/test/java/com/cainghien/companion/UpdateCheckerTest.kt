package com.cainghien.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun `version compare walks each numeric part`() {
        assertTrue(UpdateChecker.isNewer("3.8", "3.7"))
        assertTrue(UpdateChecker.isNewer("3.10", "3.9"))
        assertTrue(UpdateChecker.isNewer("4", "3.99"))
        assertTrue(UpdateChecker.isNewer("3.7.1", "3.7"))
        assertFalse(UpdateChecker.isNewer("3.7", "3.7"))
        assertFalse(UpdateChecker.isNewer("3.7", "3.8"))
        assertFalse(UpdateChecker.isNewer("3.6", "3.7"))
    }

    @Test
    fun `asset pick only accepts this repo's apk names`() {
        val prefix = "https://github.com/HairBaconGamming/kyluat/releases/download/v1.0/"
        val release = UpdateChecker.pick(
            listOf(
                "KYLUAT-Setup-v1.2.exe" to prefix + "KYLUAT-Setup-v1.2.exe",
                "kyluat-android-v3.8.apk" to prefix + "kyluat-android-v3.8.apk",
            )
        )
        assertEquals("3.8", release?.version)
        assertEquals(prefix + "kyluat-android-v3.8.apk", release?.url)
    }

    @Test
    fun `asset pick rejects foreign hosts and malformed names`() {
        assertNull(
            UpdateChecker.pick(
                listOf("kyluat-android-v9.9.apk" to "https://evil.example/kyluat-android-v9.9.apk")
            )
        )
        assertNull(UpdateChecker.pick(listOf("kyluat-android-latest.apk" to "x")))
        assertNull(UpdateChecker.pick(emptyList()))
    }
}
