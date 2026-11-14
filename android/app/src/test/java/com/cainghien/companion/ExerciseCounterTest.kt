package com.cainghien.companion

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCounterTest {
    private val repetitionKinds = listOf(ExerciseKind.PUSH_UP, ExerciseKind.SQUAT)

    @Test
    fun `new counters have no credit and show a positioning cue`() {
        for (kind in ExerciseKind.values()) {
            val counter = ExerciseCounter(kind)
            assertEquals(kind, counter.kind)
            assertEquals(0, counter.reps)
            assertEquals(0L, counter.plankMillis)
            assertEquals(0, counter.points)
            assertTrue(counter.feedback.isNotBlank())
        }
    }

    @Test
    fun `fixtures have the requested joint angles on both mirrored sides`() {
        for (side in 0..1) {
            for (degrees in listOf(65.0, 90.0, 100.0, 105.0, 110.0, 155.0, 160.0, 170.0)) {
                val push = pushUp(degrees, side)
                val squat = squat(degrees, side)
                val plank = plank(degrees, side)
                assertEquals(degrees, jointAngle(push.shoulder, push.elbow, push.wrist), 0.001)
                assertEquals(degrees, jointAngle(squat.hip, squat.knee, squat.ankle), 0.001)
                assertEquals(degrees, jointAngle(plank.shoulder, plank.elbow, plank.wrist), 0.001)
            }
        }
    }

    @Test
    fun `full cycles count once each on either side`() {
        for (kind in repetitionKinds) {
            for (side in 0..1) {
                val counter = ExerciseCounter(kind)
                cycle(counter, 0L, side = side)
                assertEquals(1, counter.reps)
                cycle(counter, 1_600L, side = side)
                assertEquals(2, counter.reps)
                assertEquals(0, counter.points)
                advance(counter, pose(kind, 170.0, side), 3_000L, 8_000L)
                assertEquals(2, counter.reps)
                assertEquals(0L, counter.plankMillis)
            }
        }
    }

    @Test
    fun `squat awards only complete groups of five`() {
        val counter = ExerciseCounter(ExerciseKind.SQUAT)
        for ((index, expected) in listOf(0, 0, 0, 0, 1, 1).withIndex()) {
            cycle(counter, index * 1_600L)
            assertEquals(index + 1, counter.reps)
            assertEquals(expected, counter.points)
        }
    }

    @Test
    fun `squat top and bottom angle thresholds are distinct`() {
        val kind = ExerciseKind.SQUAT
        val top = 150.0
        val bottom = 105.0
        val accepted = ExerciseCounter(kind)
        cycle(accepted, 0L, top = top + 0.1, bottom = bottom - 0.1)
        assertEquals("just inside thresholds", 1, accepted.reps)
        val insufficientTop = ExerciseCounter(kind)
        cycle(insufficientTop, 0L, top = top - 0.1, bottom = bottom - 5.0)
        assertEquals("below top threshold", 0, insufficientTop.reps)
        val insufficientDepth = ExerciseCounter(kind)
        cycle(insufficientDepth, 0L, top = top + 5.0, bottom = bottom + 0.1)
        assertEquals("above bottom threshold", 0, insufficientDepth.reps)
    }

    @Test
    fun `pushup counts partial lockout reps but gates depth and swing`() {
        val partial = ExerciseCounter(ExerciseKind.PUSH_UP)
        cycle(partial, 0L, top = 130.0, bottom = 90.0)
        assertEquals("partial lockout with a 40 degree swing", 1, partial.reps)
        val sideShallow = ExerciseCounter(ExerciseKind.PUSH_UP)
        cycle(sideShallow, 0L, top = 170.0, bottom = 120.0)
        assertEquals("2D side valley above 100 degrees while the shoulders sink", 1, sideShallow.reps)
        val boundary = ExerciseCounter(ExerciseKind.PUSH_UP)
        cycle(boundary, 0L, top = 160.0, bottom = 124.9)
        assertEquals("valley just inside the image depth gate", 1, boundary.reps)
        val shallow = ExerciseCounter(ExerciseKind.PUSH_UP)
        cycle(shallow, 0L, top = 165.0, bottom = 125.1)
        assertEquals("valley above the image depth gate", 0, shallow.reps)
        val smallSwing = ExerciseCounter(ExerciseKind.PUSH_UP)
        cycle(smallSwing, 0L, top = 125.0, bottom = 96.0)
        assertEquals("swing below the amplitude gate", 0, smallSwing.reps)
    }

    @Test
    fun `world pushup depth gate stays at 100 degrees`() {
        for ((top, bottom, expected) in listOf(Triple(150.0, 99.9, 1), Triple(155.0, 100.1, 0))) {
            val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
            cycle(counter, 0L, top = top, bottom = bottom, transform = {
                val degrees = jointAngle(it.shoulder, it.elbow, it.wrist)
                withOther(pushUp(170.0), 300.0).copy(world = worldChain(180.0, degrees))
            })
            assertEquals(CameraView.FRONT, counter.view)
            assertEquals("world valley $bottom", expected, counter.reps)
        }
    }

    @Test
    fun `arm landmarks bending while the shoulders stay level cannot count`() {
        val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
        val straight = pushUp(170.0)
        val bent = pushUp(80.0)
        val lift = bent.shoulder.y - straight.shoulder.y
        val glitch = straight.copy(
            elbow = bent.elbow.copy(y = bent.elbow.y - lift),
            wrist = bent.wrist.copy(y = bent.wrist.y - lift)
        )
        assertEquals(80.0, jointAngle(glitch.shoulder, glitch.elbow, glitch.wrist), 0.01)
        repeat(12) { index -> counter.update(if (index % 2 == 0) straight else glitch, index * 200L) }
        assertEquals(CameraView.SIDE, counter.view)
        assertEquals(0, counter.reps)
        cycle(counter, 2_400L, top = 170.0, bottom = 120.0)
        assertEquals(1, counter.reps)
    }

    @Test
    fun `starting down cannot earn a repetition by merely rising`() {
        for (kind in repetitionKinds) {
            val counter = ExerciseCounter(kind)
            counter.update(pose(kind, 90.0), 0L)
            counter.update(pose(kind, 90.0), 200L)
            counter.update(pose(kind, 130.0), 400L)
            counter.update(pose(kind, 170.0), 600L)
            counter.update(pose(kind, 170.0), 800L)
            assertEquals(0, counter.reps)
            cycle(counter, 1_000L)
            assertEquals(1, counter.reps)
        }
    }

    @Test
    fun `partial and stationary movements earn nothing`() {
        for (kind in repetitionKinds) {
            val partial = ExerciseCounter(kind)
            repeat(5) { cycle(partial, it * 1_600L, bottom = 130.0) }
            assertEquals(0, partial.reps)
            for (degrees in listOf(90.0, 130.0, 170.0)) {
                val stationary = ExerciseCounter(kind)
                hold(stationary, pose(kind, degrees), 0L, 12_000L)
                assertEquals(0, stationary.reps)
                assertEquals(0, stationary.points)
            }
        }
    }

    @Test
    fun `squat initial top and bottom each require 120ms continuously`() {
        val kind = ExerciseKind.SQUAT
        val shortTop = ExerciseCounter(kind)
        shortTop.update(pose(kind, 170.0), 0L)
        shortTop.update(pose(kind, 170.0), 119L)
        shortTop.update(pose(kind, 90.0), 400L)
        shortTop.update(pose(kind, 90.0), 580L)
        shortTop.update(pose(kind, 130.0), 800L)
        shortTop.update(pose(kind, 170.0), 1_000L)
        shortTop.update(pose(kind, 170.0), 1_180L)
        assertEquals(0, shortTop.reps)

        val shortBottom = ExerciseCounter(kind)
        shortBottom.update(pose(kind, 170.0), 0L)
        shortBottom.update(pose(kind, 170.0), 180L)
        shortBottom.update(pose(kind, 130.0), 200L)
        shortBottom.update(pose(kind, 90.0), 400L)
        shortBottom.update(pose(kind, 90.0), 519L)
        shortBottom.update(pose(kind, 130.0), 600L)
        shortBottom.update(pose(kind, 170.0), 1_000L)
        shortBottom.update(pose(kind, 170.0), 1_180L)
        assertEquals(0, shortBottom.reps)
    }

    @Test
    fun `pushup credits a fast descent without any dwell`() {
        val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
        counter.update(pushUp(170.0), 0L)
        counter.update(pushUp(130.0), 100L)
        counter.update(pushUp(90.0), 200L)
        assertEquals(0, counter.reps)
        counter.update(pushUp(100.0), 300L)
        assertEquals(1, counter.reps)
    }

    @Test
    fun `squat returning top must also remain stable for 120ms`() {
        val kind = ExerciseKind.SQUAT
        val counter = ExerciseCounter(kind)
        primeBottom(counter)
        counter.update(pose(kind, 170.0), 1_200L)
        counter.update(pose(kind, 170.0), 1_319L)
        assertEquals(0, counter.reps)
        counter.update(pose(kind, 170.0), 1_320L)
        assertEquals(1, counter.reps)
    }

    @Test
    fun `pushup credits the valley before the return to the top`() {
        val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
        counter.update(pushUp(170.0), 0L)
        counter.update(pushUp(90.0), 200L)
        assertEquals(0, counter.reps)
        counter.update(pushUp(100.0), 400L)
        assertEquals(1, counter.reps)
        counter.update(pushUp(170.0), 600L)
        assertEquals(1, counter.reps)
    }

    @Test
    fun `squat alternating top noise cannot arm the counter`() {
        val kind = ExerciseKind.SQUAT
        val counter = ExerciseCounter(kind)
        repeat(10) {
            counter.update(pose(kind, if (it % 2 == 0) 170.0 else 145.0), it * 100L)
        }
        counter.update(pose(kind, 90.0), 1_000L)
        counter.update(pose(kind, 90.0), 1_200L)
        counter.update(pose(kind, 170.0), 1_400L)
        counter.update(pose(kind, 170.0), 1_600L)
        assertEquals(0, counter.reps)
    }

    @Test
    fun `pushup top noise never counts but a genuine descent still does`() {
        val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
        repeat(10) {
            counter.update(pushUp(if (it % 2 == 0) 170.0 else 145.0), it * 100L)
        }
        assertEquals(0, counter.reps)
        counter.update(pushUp(90.0), 1_000L)
        counter.update(pushUp(100.0), 1_100L)
        assertEquals(1, counter.reps)
        counter.update(pushUp(170.0), 1_300L)
        assertEquals(1, counter.reps)
    }

    @Test
    fun `squat bottom noise cannot accumulate disjoint stable intervals`() {
        val kind = ExerciseKind.SQUAT
        val counter = ExerciseCounter(kind)
        counter.update(pose(kind, 170.0), 0L)
        counter.update(pose(kind, 170.0), 200L)
        repeat(10) {
            counter.update(pose(kind, if (it % 2 == 0) 90.0 else 120.0), 400L + it * 100L)
        }
        counter.update(pose(kind, 170.0), 1_500L)
        counter.update(pose(kind, 170.0), 1_700L)
        assertEquals(0, counter.reps)
    }

    @Test
    fun `pushup bottom jitter after a rep adds nothing`() {
        val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
        counter.update(pushUp(170.0), 0L)
        counter.update(pushUp(90.0), 200L)
        counter.update(pushUp(130.0), 400L)
        assertEquals(1, counter.reps)
        repeat(10) {
            counter.update(pushUp(if (it % 2 == 0) 100.0 else 120.0), 600L + it * 100L)
        }
        counter.update(pushUp(170.0), 1_700L)
        assertEquals(1, counter.reps)
    }

    @Test
    fun `squat returning top noise postpones credit until a stable top`() {
        val kind = ExerciseKind.SQUAT
        val counter = ExerciseCounter(kind)
        primeBottom(counter)
        repeat(8) {
            counter.update(pose(kind, if (it % 2 == 0) 170.0 else 145.0), 1_000L + it * 100L)
        }
        assertEquals(0, counter.reps)
        counter.update(pose(kind, 170.0), 1_800L)
        counter.update(pose(kind, 170.0), 1_980L)
        assertEquals(1, counter.reps)
    }

    @Test
    fun `pushup top noise after a credited valley changes nothing`() {
        val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
        counter.update(pushUp(170.0), 0L)
        counter.update(pushUp(90.0), 200L)
        counter.update(pushUp(100.0), 400L)
        assertEquals(1, counter.reps)
        repeat(8) {
            counter.update(pushUp(if (it % 2 == 0) 170.0 else 145.0), 600L + it * 100L)
        }
        assertEquals(1, counter.reps)
    }

    @Test
    fun `squat cycle minimum is 800ms and a rejected fast cycle cannot mature while stationary`() {
        val kind = ExerciseKind.SQUAT
        for (duration in listOf(799L, 800L)) {
            val counter = ExerciseCounter(kind)
            counter.update(pose(kind, 170.0), 0L)
            counter.update(pose(kind, 170.0), 180L)
            counter.update(pose(kind, 130.0), 200L)
            counter.update(pose(kind, 90.0), 400L)
            counter.update(pose(kind, 90.0), 580L)
            counter.update(pose(kind, 130.0), 600L)
            counter.update(pose(kind, 170.0), 200L + duration - 180L)
            counter.update(pose(kind, 170.0), 200L + duration)
            val expected = if (duration == 800L) 1 else 0
            assertEquals(expected, counter.reps)
            advance(counter, pose(kind, 170.0), 200L + duration, 4_000L)
            assertEquals(expected, counter.reps)
        }
    }

    @Test
    fun `pushup allows fast reps but a refractory window blocks double bounces`() {
        val fast = ExerciseCounter(ExerciseKind.PUSH_UP)
        fast.update(pushUp(170.0), 0L)
        fast.update(pushUp(90.0), 200L)
        fast.update(pushUp(130.0), 400L)
        assertEquals(1, fast.reps)
        fast.update(pushUp(170.0), 600L)
        fast.update(pushUp(90.0), 800L)
        fast.update(pushUp(130.0), 1_000L)
        assertEquals(2, fast.reps)
        val bounce = ExerciseCounter(ExerciseKind.PUSH_UP)
        bounce.update(pushUp(170.0), 0L)
        bounce.update(pushUp(90.0), 200L)
        bounce.update(pushUp(130.0), 400L)
        assertEquals(1, bounce.reps)
        bounce.update(pushUp(90.0), 500L)
        bounce.update(pushUp(130.0), 600L)
        assertEquals(1, bounce.reps)
        bounce.update(pushUp(170.0), 800L)
        bounce.update(pushUp(90.0), 1_000L)
        bounce.update(pushUp(130.0), 1_200L)
        assertEquals(2, bounce.reps)
    }

    @Test
    fun `squat waiting at the initial top does not pad a fast cycle`() {
        val kind = ExerciseKind.SQUAT
        val counter = ExerciseCounter(kind)
        hold(counter, pose(kind, 170.0), 0L, 20_000L)
        counter.update(pose(kind, 130.0), 20_200L)
        counter.update(pose(kind, 90.0), 20_300L)
        counter.update(pose(kind, 90.0), 20_480L)
        counter.update(pose(kind, 170.0), 20_500L)
        counter.update(pose(kind, 170.0), 20_680L)
        advance(counter, pose(kind, 170.0), 20_680L, 22_000L)
        assertEquals(0, counter.reps)
    }

    @Test
    fun `pushup resting at the top still counts the next genuine rep`() {
        val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
        hold(counter, pushUp(170.0), 0L, 20_000L)
        assertEquals(0, counter.reps)
        counter.update(pushUp(90.0), 20_200L)
        counter.update(pushUp(100.0), 20_300L)
        assertEquals(1, counter.reps)
    }

    @Test
    fun `squat in flight cycle expires after ten seconds`() {
        val kind = ExerciseKind.SQUAT
        for (duration in listOf(10_000L, 10_001L)) {
            val counter = ExerciseCounter(kind)
            counter.update(pose(kind, 170.0), 0L)
            counter.update(pose(kind, 170.0), 180L)
            counter.update(pose(kind, 130.0), 200L)
            counter.update(pose(kind, 90.0), 400L)
            counter.update(pose(kind, 90.0), 580L)
            val returnedAt = 200L + duration - 180L
            advance(counter, pose(kind, 90.0), 580L, returnedAt - 200L)
            counter.update(pose(kind, 170.0), returnedAt)
            counter.update(pose(kind, 170.0), returnedAt + 180L)
            assertEquals(if (duration == 10_000L) 1 else 0, counter.reps)
        }
    }

    @Test
    fun `pushup holding the bottom for twelve seconds adds no extra rep`() {
        val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
        counter.update(pushUp(170.0), 0L)
        counter.update(pushUp(90.0), 200L)
        counter.update(pushUp(100.0), 400L)
        assertEquals(1, counter.reps)
        hold(counter, pushUp(90.0), 600L, 12_000L)
        counter.update(pushUp(170.0), 12_200L)
        assertEquals(1, counter.reps)
    }

    @Test
    fun `squat 500ms intervals are allowed but 501ms gaps discard the movement`() {
        val kind = ExerciseKind.SQUAT
        for (gap in listOf(500L, 501L)) {
            val counter = ExerciseCounter(kind)
            primeBottom(counter)
            counter.update(pose(kind, 170.0), 800L + gap)
            counter.update(pose(kind, 170.0), 980L + gap)
            assertEquals(if (gap == 500L) 1 else 0, counter.reps)
        }
    }

    @Test
    fun `pushup gaps keep earned reps but discard the pending swing`() {
        for (gap in listOf(500L, 501L)) {
            val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
            counter.update(pushUp(170.0), 0L)
            counter.update(pushUp(115.0), 200L)
            counter.update(pushUp(115.0), 200L + gap)
            counter.update(pushUp(90.0), 400L + gap)
            counter.update(pushUp(100.0), 600L + gap)
            assertEquals(if (gap == 500L) 1 else 0, counter.reps)
        }
        val kept = ExerciseCounter(ExerciseKind.PUSH_UP)
        kept.update(pushUp(170.0), 0L)
        kept.update(pushUp(90.0), 200L)
        kept.update(pushUp(100.0), 400L)
        kept.update(pushUp(170.0), 1_200L)
        assertEquals(1, kept.reps)
    }

    @Test
    fun `squat duplicate regressing and overflowing timestamps discard the cycle`() {
        val kind = ExerciseKind.SQUAT
        for (timestamp in listOf(800L, 700L, Long.MIN_VALUE, Long.MAX_VALUE)) {
            val counter = ExerciseCounter(kind)
            primeBottom(counter)
            counter.update(pose(kind, 170.0), timestamp)
            counter.update(pose(kind, 170.0), 1_000L)
            counter.update(pose(kind, 170.0), 1_200L)
            assertEquals(0, counter.reps)
            cycle(counter, 1_400L)
            assertEquals(1, counter.reps)
        }
    }

    @Test
    fun `pushup bad timestamps discard the pending swing but not earned reps`() {
        for (timestamp in listOf(800L, 700L, Long.MIN_VALUE, Long.MAX_VALUE)) {
            val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
            counter.update(pushUp(170.0), 0L)
            counter.update(pushUp(170.0), 200L)
            counter.update(pushUp(130.0), 400L)
            counter.update(pushUp(115.0), 800L)
            counter.update(pushUp(115.0), timestamp)
            counter.update(pushUp(115.0), 1_000L)
            counter.update(pushUp(90.0), 1_200L)
            counter.update(pushUp(100.0), 1_400L)
            assertEquals(0, counter.reps)
            cycle(counter, 1_600L)
            assertEquals(1, counter.reps)
        }
    }

    @Test
    fun `squat missing pose and a side switch discard partial cycles`() {
        val kind = ExerciseKind.SQUAT
        for (switchSide in listOf(false, true)) {
            val counter = ExerciseCounter(kind)
            primeBottom(counter)
            counter.update(if (switchSide) pose(kind, 90.0, 1) else null, 1_000L)
            val side = if (switchSide) 1 else 0
            counter.update(pose(kind, 170.0, side), 1_200L)
            counter.update(pose(kind, 170.0, side), 1_400L)
            assertEquals(0, counter.reps)
            cycle(counter, 1_600L, side = side)
            assertEquals(1, counter.reps)
        }
    }

    @Test
    fun `pushup missing pose and a side switch discard the pending swing`() {
        for (switchSide in listOf(false, true)) {
            val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
            counter.update(pushUp(170.0), 0L)
            counter.update(pushUp(130.0), 200L)
            counter.update(if (switchSide) pushUp(115.0, 1) else null, 400L)
            val side = if (switchSide) 1 else 0
            counter.update(pushUp(115.0, side), 600L)
            counter.update(pushUp(90.0, side), 800L)
            counter.update(pushUp(100.0, side), 1_000L)
            assertEquals(0, counter.reps)
            cycle(counter, 1_200L, side = side)
            assertEquals(1, counter.reps)
        }
    }

    @Test
    fun `squat pause preserves earned repetitions and points but not a partial movement`() {
        val kind = ExerciseKind.SQUAT
        val counter = ExerciseCounter(kind)
        cycle(counter, 0L)
        cycle(counter, 1_600L)
        primeBottom(counter, 3_200L)
        counter.pause()
        counter.pause()
        assertEquals(2, counter.reps)
        assertEquals(0, counter.points)
        assertTrue(counter.feedback.contains("tạm dừng"))
        counter.update(pose(kind, 170.0), 4_200L)
        counter.update(pose(kind, 170.0), 4_400L)
        assertEquals(2, counter.reps)
        cycle(counter, 4_600L)
        assertEquals(3, counter.reps)
        assertEquals(0, counter.points)
    }

    @Test
    fun `pushup pause preserves earned reps but not a pending swing`() {
        val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
        cycle(counter, 0L)
        cycle(counter, 1_600L)
        counter.update(pushUp(170.0), 3_200L)
        counter.update(pushUp(130.0), 3_400L)
        counter.pause()
        assertEquals(2, counter.reps)
        assertTrue(counter.feedback.contains("tạm dừng"))
        counter.update(pushUp(115.0), 4_200L)
        counter.update(pushUp(90.0), 4_400L)
        counter.update(pushUp(100.0), 4_600L)
        assertEquals(2, counter.reps)
        cycle(counter, 4_800L)
        assertEquals(3, counter.reps)
        assertEquals(1, counter.points)
    }

    @Test
    fun `squat low confidence in any required landmark resets a cycle`() {
        val kind = ExerciseKind.SQUAT
        for (index in requiredIndices(kind)) {
            val counter = ExerciseCounter(kind)
            primeBottom(counter)
            counter.update(changePoint(pose(kind, 90.0), index) { it.copy(confidence = 0.499f) }, 1_000L)
            counter.update(pose(kind, 170.0), 1_200L)
            counter.update(pose(kind, 170.0), 1_400L)
            assertEquals("landmark $index", 0, counter.reps)
        }
    }

    @Test
    fun `pushup low confidence in any required landmark resets the pending swing`() {
        for (index in listOf(0, 3)) {
            val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
            counter.update(pushUp(170.0), 0L)
            counter.update(pushUp(130.0), 200L)
            counter.update(changePoint(pushUp(115.0), index) { it.copy(confidence = 0.499f) }, 400L)
            counter.update(pushUp(115.0), 600L)
            counter.update(pushUp(90.0), 800L)
            counter.update(pushUp(100.0), 1_000L)
            assertEquals("landmark $index", 0, counter.reps)
        }
    }

    @Test
    fun `confidence of exactly point five is accepted`() {
        for (kind in repetitionKinds) {
            val counter = ExerciseCounter(kind)
            cycle(counter, 0L, transform = { mapPoints(it) { point -> point.copy(confidence = 0.5f) } })
            assertEquals(1, counter.reps)
        }
        val counter = ExerciseCounter(ExerciseKind.PLANK)
        hold(counter, mapPoints(plank()) { it.copy(confidence = 0.5f) }, 0L, 10_000L)
        assertEquals(1, counter.points)
    }

    @Test
    fun `pushup accepts occluded arms and a thirty degree descent`() {
        val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
        cycle(counter, 0L, top = 125.0, bottom = 94.0, transform = {
            it.copy(elbow = it.elbow.copy(confidence = 0.15f), wrist = it.wrist.copy(confidence = 0.2f))
        })
        assertEquals(1, counter.reps)
    }

    @Test
    fun `empty and nonfinite landmarks never earn credit`() {
        for (kind in ExerciseKind.values()) {
            val missing = ExerciseCounter(kind)
            hold(missing, null, 0L, 6_000L)
            assertEquals(0, missing.points)
            for (index in requiredIndices(kind)) {
                for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
                    for (field in 0..2) {
                        val counter = ExerciseCounter(kind)
                        val corrupt: (ExercisePose) -> ExercisePose = { input ->
                            changePoint(input, index) {
                                when (field) {
                                    0 -> it.copy(x = value)
                                    1 -> it.copy(y = value)
                                    else -> it.copy(confidence = value)
                                }
                            }
                        }
                        if (kind == ExerciseKind.PLANK) {
                            hold(counter, corrupt(plank()), 0L, 6_000L)
                        } else {
                            cycle(counter, 0L, transform = corrupt)
                        }
                        assertEquals(0, counter.reps)
                        assertEquals(0, counter.points)
                        assertEquals(0L, counter.plankMillis)
                    }
                }
            }
        }
    }

    @Test
    fun `zero and tiny required segments are rejected`() {
        for (kind in ExerciseKind.values()) {
            val pairs = when (kind) {
                ExerciseKind.SQUAT -> listOf(0 to 3, 3 to 4, 4 to 5)
                ExerciseKind.PUSH_UP -> listOf(0 to 1, 1 to 2, 0 to 3)
                ExerciseKind.PLANK -> listOf(0 to 1, 1 to 2, 0 to 3, 3 to 4, 4 to 5)
            }
            for ((start, end) in pairs) {
                for (length in listOf(0f, 1f, 7.9f)) {
                    val counter = ExerciseCounter(kind)
                    val collapse: (ExercisePose) -> ExercisePose = { input ->
                        val point = allPoints(input)[start]
                        changePoint(input, end) { point.copy(x = point.x + length) }
                    }
                    if (kind == ExerciseKind.PLANK) {
                        hold(counter, collapse(plank()), 0L, 6_000L)
                    } else {
                        cycle(counter, 0L, transform = collapse)
                    }
                    assertEquals(0, counter.reps)
                    assertEquals(0, counter.points)
                }
            }
        }
    }

    @Test
    fun `standing arm curls are neither pushups nor plank`() {
        for (side in 0..1) {
            val push = ExerciseCounter(ExerciseKind.PUSH_UP)
            val steps = listOf(170.0, 170.0, 130.0, 90.0, 90.0, 130.0, 170.0, 170.0)
            steps.forEachIndexed { index, degrees -> push.update(standingCurl(degrees, side), index * 200L) }
            assertEquals(0, push.reps)
            val plankCounter = ExerciseCounter(ExerciseKind.PLANK)
            hold(plankCounter, standingCurl(90.0, side), 0L, 6_000L)
            assertEquals(0L, plankCounter.plankMillis)
        }
    }

    @Test
    fun `lying and inverted squats do not count`() {
        for (rotation in listOf(90.0, 180.0, -80.0)) {
            val counter = ExerciseCounter(ExerciseKind.SQUAT)
            cycle(counter, 0L, transform = { rotate(it, rotation) })
            assertEquals(0, counter.reps)
        }
    }

    @Test
    fun `sagging hips invalidate plank but pushup tolerates imperfect form`() {
        for (index in listOf(3, 4)) {
            val bend: (ExercisePose) -> ExercisePose = { input ->
                changePoint(input, index) { it.copy(y = it.y + 150f) }
            }
            val push = ExerciseCounter(ExerciseKind.PUSH_UP)
            cycle(push, 0L, transform = bend)
            assertEquals(1, push.reps)
            val plankCounter = ExerciseCounter(ExerciseKind.PLANK)
            hold(plankCounter, bend(plank()), 0L, 6_000L)
            assertEquals(0L, plankCounter.plankMillis)
        }
    }

    @Test
    fun `pushup counts from upper body when legs are out of frame`() {
        val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
        cycle(counter, 0L, transform = { upperBody(it) })
        assertEquals(1, counter.reps)
        cycle(counter, 1_600L, transform = { upperBody(it) })
        assertEquals(2, counter.reps)
    }

    @Test
    fun `pushup upper body accepts front view when a world arm angle is present`() {
        val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
        val front: (ExercisePose) -> ExercisePose = {
            val degrees = jointAngle(it.shoulder, it.elbow, it.wrist)
            upperBody(withOther(it, 300.0)).copy(world = worldChain(90.0, degrees))
        }
        cycle(counter, 0L, transform = front)
        assertEquals(1, counter.reps)
    }

    @Test
    fun `pushup upper body front view without world landmarks is rejected`() {
        val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
        cycle(counter, 0L, transform = { upperBody(withOther(it, 300.0)) })
        assertEquals(0, counter.reps)
    }

    @Test
    fun `standing arm bends with legs out of frame do not count`() {
        for (side in 0..1) {
            val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
            cycle(counter, 0L, side = side, transform = { upperBody(standingCurl(90.0, side)) })
            assertEquals(0, counter.reps)
        }
    }

    @Test
    fun `squat still requires the legs even though pushup does not`() {
        val counter = ExerciseCounter(ExerciseKind.SQUAT)
        cycle(counter, 0L, transform = { upperBody(it) })
        assertEquals(0, counter.reps)
    }

    @Test
    fun `plank accepts relaxed forearm angles but not straight arms`() {
        for (side in 0..1) {
            for (degrees in listOf(44.9, 45.1, 60.0, 90.0, 120.0, 134.9, 135.1, 170.0)) {
                val counter = ExerciseCounter(ExerciseKind.PLANK)
                hold(counter, plank(degrees, side), 0L, 10_000L)
                assertEquals("angle $degrees side $side", if (degrees in 45.0..135.0) 1 else 0, counter.points)
            }
        }
    }

    @Test
    fun `plank requires elbows below and reasonably under the shoulders`() {
        for (rotation in listOf(75.0, -75.0, 180.0)) {
            val base = plank()
            val moved = rotate(base, rotation, base.shoulder)
            val wrongArms = base.copy(elbow = moved.elbow, wrist = moved.wrist)
            assertEquals(90.0, jointAngle(wrongArms.shoulder, wrongArms.elbow, wrongArms.wrist), 0.001)
            val counter = ExerciseCounter(ExerciseKind.PLANK)
            hold(counter, wrongArms, 0L, 6_000L)
            assertEquals(0L, counter.plankMillis)
        }
        val straightArms = ExerciseCounter(ExerciseKind.PLANK)
        hold(straightArms, pushUp(170.0), 0L, 6_000L)
        assertEquals(0, straightArms.points)
    }

    @Test
    fun `plank tolerates moderate hip bends and elbow offsets`() {
        for (offset in listOf(-60f, 60f)) {
            val base = plank()
            val counter = ExerciseCounter(ExerciseKind.PLANK)
            hold(counter, base.copy(hip = base.hip.copy(y = base.hip.y + offset)), 0L, 10_000L)
            assertEquals(1, counter.points)
        }
        for (rotation in listOf(-45.0, 45.0)) {
            val base = plank()
            val moved = rotate(base, rotation, base.shoulder)
            val counter = ExerciseCounter(ExerciseKind.PLANK)
            hold(counter, base.copy(elbow = moved.elbow, wrist = moved.wrist), 0L, 10_000L)
            assertEquals(1, counter.points)
        }
        val tilted = ExerciseCounter(ExerciseKind.PLANK)
        hold(tilted, rotate(plank(), 25.0), 0L, 10_000L)
        assertEquals(1, tilted.points)
    }

    @Test
    fun `plank earns zero at 9999ms one at ten seconds and two at twenty seconds`() {
        for (side in 0..1) {
            val counter = ExerciseCounter(ExerciseKind.PLANK)
            val pose = plank(side = side)
            hold(counter, pose, 0L, 9_999L)
            assertEquals(9_999L, counter.plankMillis)
            assertEquals(0, counter.points)
            counter.update(pose, 10_000L)
            assertEquals(10_000L, counter.plankMillis)
            assertEquals(1, counter.points)
            advance(counter, pose, 10_000L, 20_000L, 500L)
            assertEquals(20_000L, counter.plankMillis)
            assertEquals(2, counter.points)
            assertEquals(0, counter.reps)
        }
    }

    @Test
    fun `all plank interruptions preserve whole blocks but discard remainder and gap time`() {
        for (interruption in listOf("pause", "missing", "confidence", "posture", "gap", "regression", "duplicate", "side")) {
            val counter = ExerciseCounter(ExerciseKind.PLANK)
            hold(counter, plank(), 0L, 12_999L)
            val side = if (interruption == "side") 1 else 0
            val resumeAt = when (interruption) {
                "pause" -> {
                    counter.pause()
                    counter.pause()
                    15_000L
                }
                "missing" -> {
                    counter.update(null, 13_099L)
                    13_599L
                }
                "confidence" -> {
                    counter.update(plank().let { it.copy(wrist = it.wrist.copy(confidence = 0.49f)) }, 13_099L)
                    13_599L
                }
                "posture" -> {
                    counter.update(pushUp(170.0), 13_099L)
                    13_599L
                }
                "gap" -> 13_500L
                "regression" -> 12_899L
                "duplicate" -> 12_999L
                else -> 13_199L
            }
            counter.update(plank(side = side), resumeAt)
            assertEquals(interruption, 10_000L, counter.plankMillis)
            assertEquals(interruption, 1, counter.points)
            advance(counter, plank(side = side), resumeAt, resumeAt + 9_999L)
            assertEquals(interruption, 19_999L, counter.plankMillis)
            assertEquals(interruption, 1, counter.points)
            counter.update(plank(side = side), resumeAt + 10_000L)
            assertEquals(interruption, 20_000L, counter.plankMillis)
            assertEquals(interruption, 2, counter.points)
        }
    }

    @Test
    fun `repeated sub ten second holds cannot bank hidden partial credit`() {
        val counter = ExerciseCounter(ExerciseKind.PLANK)
        repeat(10) {
            val start = it * 20_000L
            hold(counter, plank(), start, start + 9_999L)
            counter.update(null, start + 10_000L)
            assertEquals(0L, counter.plankMillis)
            assertEquals(0, counter.points)
        }
    }

    @Test
    fun `first frame and long gaps never credit elapsed plank time`() {
        val counter = ExerciseCounter(ExerciseKind.PLANK)
        counter.update(plank(), 1_000_000L)
        assertEquals(0L, counter.plankMillis)
        counter.update(plank(), 1_000_500L)
        assertEquals(500L, counter.plankMillis)
        counter.update(plank(), 1_001_001L)
        assertEquals(0L, counter.plankMillis)
        counter.update(plank(), 1_001_251L)
        assertEquals(250L, counter.plankMillis)
        counter.update(plank(), Long.MAX_VALUE)
        assertEquals(0L, counter.plankMillis)
        counter.update(plank(), Long.MIN_VALUE)
        assertEquals(0L, counter.plankMillis)
    }

    @Test
    fun `feedback guides position phase and pause without being empty`() {
        val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
        val start = counter.feedback
        counter.update(null, 0L)
        assertTrue(counter.feedback.contains("khung hình"))
        counter.update(pushUp(170.0), 200L)
        counter.update(pushUp(170.0), 400L)
        assertTrue(counter.feedback.contains("Hạ"))
        counter.update(pushUp(90.0), 600L)
        counter.update(pushUp(90.0), 800L)
        assertTrue(counter.feedback.contains("Đẩy"))
        counter.pause()
        assertTrue(counter.feedback.contains("tạm dừng"))
        assertTrue(start.isNotBlank())
    }

    @Test
    fun `counter imposes no release reward cap`() {
        for (kind in repetitionKinds) {
            val counter = ExerciseCounter(kind)
            repeat(121) { cycle(counter, it * 1_600L) }
            assertEquals(121, counter.reps)
            assertEquals(if (kind == ExerciseKind.PUSH_UP) 40 else 24, counter.points)
        }
        val counter = ExerciseCounter(ExerciseKind.PLANK)
        hold(counter, plank(), 0L, 1_210_000L)
        assertEquals(121, counter.points)
    }

    @Test
    fun `side view counts pushups but front without world asks to turn the phone`() {
        val side = ExerciseCounter(ExerciseKind.PUSH_UP)
        cycle(side, 0L, transform = { withOther(it, 120.0) })
        assertEquals(1, side.reps)
        assertEquals(CameraView.SIDE, side.view)
        val front = ExerciseCounter(ExerciseKind.PUSH_UP)
        cycle(front, 0L, transform = { withOther(it, 300.0) })
        assertEquals(0, front.reps)
        assertEquals(CameraView.FRONT, front.view)
        assertTrue(front.correction != null)
    }

    @Test
    fun `squat ignores arm landmarks entirely`() {
        val counter = ExerciseCounter(ExerciseKind.SQUAT)
        cycle(counter, 0L, transform = {
            it.copy(
                elbow = BodyPoint(Float.NaN, Float.NaN, Float.NaN),
                wrist = BodyPoint(0f, 0f, 0f)
            )
        })
        assertEquals(1, counter.reps)
    }

    @Test
    fun `front view shallow squats earn nothing`() {
        val counter = ExerciseCounter(ExerciseKind.SQUAT)
        repeat(3) { cycle(counter, it * 1_600L, bottom = 130.0, transform = { p -> withOther(p, 200.0) }) }
        assertEquals(0, counter.reps)
    }

    @Test
    fun `front camera view counts full squat cycles`() {
        val counter = ExerciseCounter(ExerciseKind.SQUAT)
        cycle(counter, 0L, transform = { withOther(it, 200.0) })
        assertEquals(1, counter.reps)
        assertEquals(CameraView.FRONT, counter.view)
    }

    @Test
    fun `tilted shoulders are rejected in front view with a correction`() {
        val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
        cycle(counter, 0L, transform = {
            val r = withOther(rotate(it, 90.0), 200.0)
            val os = r.otherShoulder!!
            r.copy(otherShoulder = os.copy(y = os.y + 150f))
        })
        assertEquals(0, counter.reps)
        assertTrue(counter.correction != null)
    }

    @Test
    fun `a sustained camera view switch discards an in-flight repetition`() {
        val counter = ExerciseCounter(ExerciseKind.SQUAT)
        counter.update(squat(170.0), 0L)
        counter.update(squat(170.0), 200L)
        counter.update(squat(90.0), 400L)
        counter.update(squat(90.0), 600L)
        val frontBottom = withOther(squat(90.0), 200.0)
        for (time in listOf(800L, 1_000L, 1_200L, 1_400L)) counter.update(frontBottom, time)
        assertEquals(CameraView.FRONT, counter.view)
        assertEquals(0, counter.reps)
        counter.update(withOther(squat(170.0), 200.0), 1_600L)
        counter.update(withOther(squat(170.0), 200.0), 1_800L)
        assertEquals(0, counter.reps)
    }

    @Test
    fun `a sustained view switch keeps the cycle when world landmarks drive the angle`() {
        val counter = ExerciseCounter(ExerciseKind.SQUAT)
        counter.update(squat(170.0), 0L)
        counter.update(squat(170.0), 200L)
        counter.update(squat(90.0), 400L)
        counter.update(squat(90.0), 600L)
        val frontBottom = withOther(squat(90.0), 200.0).copy(world = worldChain(90.0))
        for (time in listOf(800L, 1_000L, 1_200L, 1_400L)) counter.update(frontBottom, time)
        assertEquals(CameraView.FRONT, counter.view)
        val frontTop = withOther(squat(170.0), 200.0).copy(world = worldChain(170.0))
        counter.update(frontTop, 1_600L)
        counter.update(frontTop, 1_800L)
        assertEquals(1, counter.reps)
    }

    @Test
    fun `a brief view flap does not discard an in-flight repetition`() {
        val counter = ExerciseCounter(ExerciseKind.SQUAT)
        counter.update(squat(170.0), 0L)
        counter.update(squat(170.0), 200L)
        counter.update(squat(90.0), 400L)
        counter.update(squat(90.0), 600L)
        counter.update(squat(90.0), 800L)
        val frontTop = withOther(squat(170.0), 200.0)
        counter.update(frontTop, 1_000L)
        counter.update(frontTop, 1_200L)
        counter.update(squat(170.0), 1_400L)
        counter.update(squat(170.0), 1_600L)
        assertEquals(1, counter.reps)
        assertEquals(CameraView.SIDE, counter.view)
    }

    @Test
    fun `view label reflects the detected camera angle`() {
        val side = ExerciseCounter(ExerciseKind.PUSH_UP)
        side.update(pushUp(170.0), 0L)
        assertEquals("Bên hông", side.viewLabel)
        val front = ExerciseCounter(ExerciseKind.PUSH_UP)
        front.update(withOther(rotate(pushUp(170.0), 90.0), 200.0), 0L)
        assertEquals("Chính diện", front.viewLabel)
        val diagonal = ExerciseCounter(ExerciseKind.PUSH_UP)
        diagonal.update(withOther(rotate(pushUp(170.0), 90.0), 120.0), 0L)
        assertEquals("Chếch 45°", diagonal.viewLabel)
    }

    @Test
    fun `world landmarks count front squats whose image legs stay straight`() {
        val angles = listOf(170.0, 170.0, 130.0, 90.0, 90.0, 130.0, 170.0, 170.0)
        val withWorld = ExerciseCounter(ExerciseKind.SQUAT)
        angles.forEachIndexed { index, degrees ->
            withWorld.update(withOther(squat(170.0), 200.0).copy(world = worldChain(degrees)), index * 200L)
        }
        assertEquals(1, withWorld.reps)
        val without = ExerciseCounter(ExerciseKind.SQUAT)
        angles.forEachIndexed { index, _ -> without.update(withOther(squat(170.0), 200.0), index * 200L) }
        assertEquals(0, without.reps)
    }

    @Test
    fun `world landmarks count pushups whose image elbow stays straight`() {
        val angles = listOf(170.0, 170.0, 130.0, 90.0, 90.0, 130.0, 170.0, 170.0)
        val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
        angles.forEachIndexed { index, degrees ->
            // Front view: the 2D elbow is foreshortened, so the world arm angle must drive.
            counter.update(withOther(pushUp(170.0), 300.0).copy(world = worldChain(180.0, degrees)), index * 200L)
        }
        assertEquals(1, counter.reps)
    }

    @Test
    fun `changing between world and image angles cannot manufacture a repetition`() {
        val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
        val stabilizer = PoseStabilizer().apply { upperBody = true }
        val base = ExercisePose(
            point(200.0, 200.0), point(200.0, 300.0), point(300.0, 300.0),
            point(300.0, 360.0), point(400.0, 380.0), point(500.0, 400.0), 0,
            otherShoulder = point(280.0, 200.0), otherHip = point(380.0, 360.0),
            world = worldChain(180.0, 135.0)
        )
        repeat(6) { index ->
            val pose = if (index % 2 == 0) base else base.copy(
                elbow = base.elbow.copy(confidence = 0.49f),
                wrist = base.wrist.copy(confidence = 0.49f), world = null
            )
            counter.update(stabilizer.update(listOf(pose), index * 200L), index * 200L)
        }
        assertEquals(CameraView.DIAGONAL, counter.view)
        assertEquals(0, counter.reps)
        listOf(170.0, 130.0, 90.0, 120.0, 170.0).forEachIndexed { index, degrees ->
            val t = 1_200L + index * 200L
            val pose = base.copy(world = worldChain(180.0, degrees))
            counter.update(stabilizer.update(listOf(pose), t), t)
        }
        assertEquals(1, counter.reps)
    }

    @Test
    fun `view changes preserve descent in the newly selected coordinate space`() {
        for (toWorld in listOf(false, true)) {
            val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
            listOf(170.0, 130.0, 90.0, 90.0, 90.0, 100.0).forEachIndexed { index, degrees ->
                val base = pushUp(if (toWorld) 170.0 else degrees)
                    .copy(world = worldChain(180.0, if (toWorld) degrees else 170.0))
                val front = if (index == 0) !toWorld else toWorld
                counter.update(if (front) withOther(base, 300.0) else base, index * 200L)
            }
            assertEquals(if (toWorld) CameraView.FRONT else CameraView.SIDE, counter.view)
            assertEquals(1, counter.reps)
        }
    }

    @Test
    fun `view changes cannot count a stationary arm with differing image and world angles`() {
        val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
        repeat(24) { index ->
            val base = pushUp(90.0).copy(world = worldChain(180.0, 135.0))
            val pose = if (index / 6 % 2 == 0) base else withOther(base, 300.0)
            counter.update(pose, index * 200L)
        }
        assertEquals(0, counter.reps)
    }

    @Test
    fun `switching to a delayed world valley cannot credit the same rep twice`() {
        val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
        counter.update(pushUp(170.0).copy(world = worldChain(180.0, 170.0)), 0L)
        counter.update(pushUp(90.0).copy(world = worldChain(180.0, 130.0)), 200L)
        counter.update(pushUp(100.0).copy(world = worldChain(180.0, 90.0)), 400L)
        assertEquals(1, counter.reps)
        listOf(90.0, 90.0, 90.0, 100.0, 130.0).forEachIndexed { index, degrees ->
            val pose = withOther(pushUp(170.0), 300.0).copy(world = worldChain(180.0, degrees))
            counter.update(pose, 600L + index * 200L)
        }
        assertEquals(CameraView.FRONT, counter.view)
        assertEquals(1, counter.reps)
        listOf(170.0, 90.0, 100.0).forEachIndexed { index, degrees ->
            val pose = withOther(pushUp(170.0), 300.0).copy(world = worldChain(180.0, degrees))
            counter.update(pose, 1_600L + index * 200L)
        }
        assertEquals(2, counter.reps)
    }

    @Test
    fun `a source still descending after the other source counts must rearm before earning`() {
        for (toWorld in listOf(false, true)) {
            for (smooth in listOf(false, true)) {
                val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
                val stabilizer = PoseStabilizer().apply { upperBody = true }
                val fast = listOf(170.0, 90.0, 110.0, 170.0, 170.0, 170.0)
                val slow = listOf(170.0, 170.0, 150.0, 140.0, 90.0, 110.0)
                fast.indices.forEach { index ->
                    val base = pushUp(if (toWorld) fast[index] else slow[index])
                        .copy(world = worldChain(180.0, if (toWorld) slow[index] else fast[index]))
                    val front = if (toWorld) index > 0 else index == 0
                    val pose = if (front) withOther(base, 300.0) else base
                    val t = index * 200L
                    counter.update(if (smooth) stabilizer.update(listOf(pose), t) else pose, t)
                }
                assertEquals("toWorld=$toWorld smooth=$smooth", 1, counter.reps)
                listOf(170.0, 170.0, 90.0, 90.0, 130.0, 170.0, 170.0).forEachIndexed { index, degrees ->
                    val base = pushUp(degrees).copy(world = worldChain(180.0, degrees))
                    val pose = if (toWorld) withOther(base, 300.0) else base
                    val t = 1_200L + index * 200L
                    counter.update(if (smooth) stabilizer.update(listOf(pose), t) else pose, t)
                }
                assertEquals("next rep toWorld=$toWorld smooth=$smooth", 2, counter.reps)
            }
        }
    }

    @Test
    fun `world dropout cannot remove suppression of an already credited descent`() {
        val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
        val image = listOf(170.0, 90.0, 110.0, 170.0, 170.0, 170.0)
        val world = listOf(170.0, 170.0, 150.0, null, 90.0, 110.0)
        image.indices.forEach { index ->
            val base = pushUp(image[index]).copy(world = world[index]?.let { worldChain(180.0, it) })
            val pose = if (index == 0) base else withOther(base, 300.0)
            counter.update(pose, index * 200L)
        }
        assertEquals(CameraView.FRONT, counter.view)
        assertEquals(1, counter.reps)
    }

    @Test
    fun `moving torso with missing arms cannot earn repetitions`() {
        val counter = ExerciseCounter(ExerciseKind.PUSH_UP)
        val stabilizer = PoseStabilizer().apply { upperBody = true }
        counter.update(stabilizer.update(listOf(pushUp(170.0)), 0L), 0L)
        repeat(20) { index ->
            val body = pushUp(if (index % 4 < 2) 90.0 else 170.0)
            val missing = body.copy(elbow = body.shoulder.copy(confidence = 0f), wrist = body.shoulder.copy(confidence = 0f))
            val t = (index + 1) * 100L
            counter.update(stabilizer.update(listOf(missing), t), t)
        }
        assertEquals(0, counter.reps)
    }

    private fun worldPoint(x: Double, y: Double, z: Double) =
        WorldPoint(x.toFloat(), y.toFloat(), z.toFloat(), 0.99f)

    private fun worldChain(kneeDegrees: Double, elbowDegrees: Double = 180.0): WorldChain {
        val hip = worldPoint(0.0, 0.0, 0.0)
        val shoulder = worldPoint(0.0, 0.5, 0.0)
        val knee = worldPoint(0.0, -0.45, 0.0)
        val kr = Math.toRadians(kneeDegrees)
        val ankle = worldPoint(knee.x + 0.45 * sin(kr), knee.y + 0.45 * cos(kr), 0.0)
        val elbow = worldPoint(0.0, 0.2, 0.0)
        val er = Math.toRadians(elbowDegrees)
        val wrist = worldPoint(elbow.x + 0.3 * sin(er), elbow.y + 0.3 * cos(er), 0.0)
        return WorldChain(shoulder, elbow, wrist, hip, knee, ankle)
    }

    private fun requiredIndices(kind: ExerciseKind): List<Int> = when (kind) {
        ExerciseKind.SQUAT -> listOf(0, 3, 4, 5)
        // Push-up counts from the upper body; legs are optional.
        ExerciseKind.PUSH_UP -> listOf(0, 1, 2, 3)
        ExerciseKind.PLANK -> (0..5).toList()
    }

    private fun upperBody(pose: ExercisePose): ExercisePose = pose.copy(
        knee = pose.hip.copy(confidence = 0f),
        ankle = pose.hip.copy(confidence = 0f)
    )

    private fun withOther(pose: ExercisePose, span: Double): ExercisePose = pose.copy(
        otherShoulder = point(pose.shoulder.x + span, pose.shoulder.y.toDouble()),
        otherHip = point(pose.hip.x + span * 0.8, pose.hip.y.toDouble())
    )

    private fun cycle(
        counter: ExerciseCounter,
        start: Long,
        side: Int = 0,
        top: Double = 170.0,
        bottom: Double = 90.0,
        transform: (ExercisePose) -> ExercisePose = { it }
    ) {
        val middle = (top + bottom) / 2.0
        val angles = listOf(top, top, middle, bottom, bottom, middle, top, top)
        angles.forEachIndexed { index, degrees ->
            counter.update(transform(pose(counter.kind, degrees, side)), start + index * 200L)
        }
    }

    private fun primeBottom(counter: ExerciseCounter, start: Long = 0L) {
        listOf(170.0, 170.0, 130.0, 90.0, 90.0).forEachIndexed { index, degrees ->
            counter.update(pose(counter.kind, degrees), start + index * 200L)
        }
    }

    private fun hold(counter: ExerciseCounter, pose: ExercisePose?, from: Long, to: Long) {
        counter.update(pose, from)
        advance(counter, pose, from, to)
    }

    private fun advance(counter: ExerciseCounter, pose: ExercisePose?, from: Long, to: Long, step: Long = 250L) {
        var time = from
        while (time < to) {
            time = minOf(time + step, to)
            counter.update(pose, time)
        }
    }

    private fun point(x: Double, y: Double) = BodyPoint(x.toFloat(), y.toFloat(), 0.99f)

    private fun pose(kind: ExerciseKind, degrees: Double, side: Int = 0): ExercisePose = when (kind) {
        ExerciseKind.PUSH_UP -> pushUp(degrees, side)
        ExerciseKind.SQUAT -> squat(degrees, side)
        ExerciseKind.PLANK -> plank(degrees, side)
    }

    private fun pushUp(degrees: Double, side: Int = 0): ExercisePose {
        val half = Math.toRadians(degrees / 2.0)
        val shoulderY = 500.0 - 200.0 * sin(half)
        val bodyDrop = 480.0 - shoulderY
        return mirrored(
            ExercisePose(
                shoulder = point(200.0, shoulderY),
                elbow = point(200.0 + 100.0 * cos(half), shoulderY + 100.0 * sin(half)),
                wrist = point(200.0, 500.0),
                hip = point(200.0 + 650.0 * 0.42, shoulderY + bodyDrop * 0.42),
                knee = point(200.0 + 650.0 * 0.75, shoulderY + bodyDrop * 0.75),
                ankle = point(850.0, 480.0),
                side = side
            )
        )
    }

    private fun squat(degrees: Double, side: Int = 0): ExercisePose {
        val radians = Math.toRadians(degrees)
        val hipX = 500.0 - 200.0 * sin(radians)
        val hipY = 500.0 + 200.0 * cos(radians)
        val shoulderX = hipX + (180.0 - degrees) / 90.0 * 50.0
        val shoulderY = hipY - 210.0
        return mirrored(
            ExercisePose(
                shoulder = point(shoulderX, shoulderY),
                elbow = point(shoulderX + 30.0, shoulderY + 110.0),
                wrist = point(shoulderX + 75.0, shoulderY + 190.0),
                hip = point(hipX, hipY),
                knee = point(500.0, 500.0),
                ankle = point(500.0, 700.0),
                side = side
            )
        )
    }

    private fun plank(degrees: Double = 90.0, side: Int = 0): ExercisePose {
        val radians = Math.toRadians(degrees)
        return mirrored(
            ExercisePose(
                shoulder = point(320.0, 260.0),
                elbow = point(320.0, 370.0),
                wrist = point(320.0 - 120.0 * sin(radians), 370.0 - 120.0 * cos(radians)),
                hip = point(570.0, 300.0),
                knee = point(770.0, 330.0),
                ankle = point(950.0, 360.0),
                side = side
            )
        )
    }

    private fun standingCurl(degrees: Double, side: Int): ExercisePose {
        val body = squat(180.0)
        val radians = Math.toRadians(degrees)
        val elbow = body.shoulder.copy(y = body.shoulder.y + 110f)
        return mirrored(body.copy(
            elbow = elbow,
            wrist = point(elbow.x + 110.0 * sin(radians), elbow.y - 110.0 * cos(radians)),
            side = side
        ))
    }

    private fun mirrored(pose: ExercisePose): ExercisePose = if (pose.side == 0) pose else {
        mapPoints(pose) { it.copy(x = 1_200f - it.x) }
    }

    private fun rotate(pose: ExercisePose, degrees: Double, origin: BodyPoint = point(600.0, 400.0)): ExercisePose {
        val radians = Math.toRadians(degrees)
        return mapPoints(pose) {
            val x = it.x.toDouble() - origin.x
            val y = it.y.toDouble() - origin.y
            point(origin.x + x * cos(radians) - y * sin(radians), origin.y + x * sin(radians) + y * cos(radians))
        }
    }

    private fun allPoints(pose: ExercisePose) =
        listOf(pose.shoulder, pose.elbow, pose.wrist, pose.hip, pose.knee, pose.ankle)

    private fun mapPoints(pose: ExercisePose, transform: (BodyPoint) -> BodyPoint): ExercisePose = pose.copy(
        shoulder = transform(pose.shoulder),
        elbow = transform(pose.elbow),
        wrist = transform(pose.wrist),
        hip = transform(pose.hip),
        knee = transform(pose.knee),
        ankle = transform(pose.ankle)
    )

    private fun changePoint(pose: ExercisePose, index: Int, transform: (BodyPoint) -> BodyPoint): ExercisePose {
        var current = 0
        return mapPoints(pose) { if (current++ == index) transform(it) else it }
    }

    private fun jointAngle(a: BodyPoint, b: BodyPoint, c: BodyPoint): Double {
        val ux = a.x.toDouble() - b.x
        val uy = a.y.toDouble() - b.y
        val vx = c.x.toDouble() - b.x
        val vy = c.y.toDouble() - b.y
        val cosine = (ux * vx + uy * vy) / sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy))
        return Math.toDegrees(acos(cosine.coerceIn(-1.0, 1.0)))
    }
}
