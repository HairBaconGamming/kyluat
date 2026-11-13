package com.cainghien.companion

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseStabilizerTest {
    private fun point(x: Double, y: Double, confidence: Float = 0.95f) =
        BodyPoint(x.toFloat(), y.toFloat(), confidence)

    private fun standing(side: Int = 0, dx: Float = 0f, dy: Float = 0f, confidence: Float = 0.95f) = ExercisePose(
        shoulder = point(200.0 + dx, 200.0 + dy, confidence),
        elbow = point(230.0 + dx, 310.0 + dy, confidence),
        wrist = point(275.0 + dx, 390.0 + dy, confidence),
        hip = point(200.0 + dx, 410.0 + dy, confidence),
        knee = point(200.0 + dx, 560.0 + dy, confidence),
        ankle = point(200.0 + dx, 700.0 + dy, confidence),
        side = side
    )

    @Test
    fun `first frame is adopted as is`() {
        val stabilizer = PoseStabilizer()
        val pose = standing()
        assertEquals(pose, stabilizer.update(listOf(pose), 0L))
    }

    @Test
    fun `smoothing damps alternating jitter`() {
        val stabilizer = PoseStabilizer()
        stabilizer.update(listOf(standing()), 0L)
        var worst = 0f
        for (i in 1..12) {
            val jitter = if (i % 2 == 0) 6f else -6f
            val out = stabilizer.update(listOf(standing(dx = jitter)), i * 100L)!!
            worst = maxOf(worst, abs(out.shoulder.x - 200f))
        }
        assertTrue("worst deviation $worst", worst < 4.5f)
    }

    @Test
    fun `a teleporting landmark is rejected and the last good pose held`() {
        val stabilizer = PoseStabilizer()
        val good = stabilizer.update(listOf(standing()), 0L)!!
        val jumped = standing(dy = 600f)
        assertEquals(good, stabilizer.update(listOf(jumped), 100L))
        assertEquals(good, stabilizer.update(listOf(jumped), 300L))
        assertNull(stabilizer.update(listOf(jumped), 500L))
    }

    @Test
    fun `a single teleporting joint freezes instead of dropping the pose`() {
        val stabilizer = PoseStabilizer()
        val good = stabilizer.update(listOf(standing()), 0L)!!
        val jumped = standing().copy(ankle = point(200.0, 1300.0))
        val out = stabilizer.update(listOf(jumped), 100L)!!
        assertEquals(good.ankle.y, out.ankle.y, 0.01f)
        assertEquals(good.shoulder, out.shoulder)
    }

    @Test
    fun `empty frames hold the pose for 400ms then give up`() {
        val stabilizer = PoseStabilizer()
        val good = stabilizer.update(listOf(standing()), 0L)!!
        assertEquals(good, stabilizer.update(emptyList(), 100L))
        assertEquals(good, stabilizer.update(emptyList(), 400L))
        assertNull(stabilizer.update(emptyList(), 401L))
    }

    @Test
    fun `low confidence cores are unusable`() {
        val stabilizer = PoseStabilizer()
        val weak = standing().let { it.copy(hip = it.hip.copy(confidence = 0.6f)) }
        assertNull(stabilizer.update(listOf(weak), 0L))
    }

    @Test
    fun `upper-body mode keeps poses whose legs are out of frame`() {
        val partial = standing().let {
            it.copy(knee = it.hip.copy(confidence = 0f), ankle = it.hip.copy(confidence = 0f))
        }
        val full = PoseStabilizer()
        assertNull(full.update(listOf(partial), 0L))
        val upper = PoseStabilizer().apply { upperBody = true }
        assertNotNull(upper.update(listOf(partial), 0L))
    }

    @Test
    fun `placeholder arms do not make a candidate unusable`() {
        val stabilizer = PoseStabilizer()
        val pose = standing().let {
            it.copy(
                elbow = BodyPoint(it.shoulder.x, it.shoulder.y, 0f),
                wrist = BodyPoint(it.shoulder.x, it.shoulder.y, 0f)
            )
        }
        assertNotNull(stabilizer.update(listOf(pose), 0L))
    }

    @Test
    fun `side switches only after 600ms of sustained evidence`() {
        val stabilizer = PoseStabilizer()
        stabilizer.update(listOf(standing(side = 0)), 0L)
        val both = listOf(standing(side = 0), standing(side = 1, confidence = 0.99f))
        for (time in listOf(100L, 300L, 500L, 600L)) {
            assertEquals(0, stabilizer.update(both, time)!!.side)
        }
        assertEquals(1, stabilizer.update(both, 700L)!!.side)
    }

    @Test
    fun `a missing incumbent side holds briefly then follows the present side`() {
        val stabilizer = PoseStabilizer()
        val good = stabilizer.update(listOf(standing(side = 0)), 0L)!!
        assertEquals(good, stabilizer.update(listOf(standing(side = 1)), 100L))
        assertEquals(1, stabilizer.update(listOf(standing(side = 1)), 500L)!!.side)
    }

    @Test
    fun `reset forgets side and history`() {
        val stabilizer = PoseStabilizer()
        stabilizer.update(listOf(standing(side = 0)), 0L)
        stabilizer.reset()
        assertNull(stabilizer.pose)
        assertEquals(1, stabilizer.update(listOf(standing(side = 1)), 100L)!!.side)
    }

    @Test
    fun `smoothing keeps the newest confidence`() {
        val stabilizer = PoseStabilizer()
        stabilizer.update(listOf(standing()), 0L)
        val next = standing(dx = 3f).let { it.copy(knee = it.knee.copy(confidence = 0.8f)) }
        assertEquals(0.8f, stabilizer.update(listOf(next), 100L)!!.knee.confidence, 0f)
    }
}