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
                assertEquals(2, counter.points)
                advance(counter, pose(kind, 170.0, side), 3_000L, 8_000L)
                assertEquals(2, counter.reps)
                assertEquals(0L, counter.plankMillis)
            }
        }
    }

    @Test
    fun `squat awards only complete pairs`() {
        val counter = ExerciseCounter(ExerciseKind.SQUAT)
        for ((index, expected) in listOf(0, 2, 2, 4).withIndex()) {
            cycle(counter, index * 1_600L)
            assertEquals(index + 1, counter.reps)
            assertEquals(expected, counter.points)
        }
    }

    @Test
    fun `top and bottom angle thresholds are distinct for each exercise`() {
        for (kind in repetitionKinds) {
            val top = if (kind == ExerciseKind.PUSH_UP) 155.0 else 160.0
            val bottom = if (kind == ExerciseKind.PUSH_UP) 100.0 else 105.0
            val accepted = ExerciseCounter(kind)
            cycle(accepted, 0L, top = top + 0.1, bottom = bottom - 0.1)
            assertEquals("$kind just inside thresholds", 1, accepted.reps)
            val insufficientTop = ExerciseCounter(kind)
            cycle(insufficientTop, 0L, top = top - 0.1, bottom = bottom - 5.0)
            assertEquals("$kind below top threshold", 0, insufficientTop.reps)
            val insufficientDepth = ExerciseCounter(kind)
            cycle(insufficientDepth, 0L, top = top + 5.0, bottom = bottom + 0.1)
            assertEquals("$kind above bottom threshold", 0, insufficientDepth.reps)
        }
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
    fun `initial top and bottom each require 180ms continuously`() {
        for (kind in repetitionKinds) {
            val shortTop = ExerciseCounter(kind)
            shortTop.update(pose(kind, 170.0), 0L)
            shortTop.update(pose(kind, 170.0), 179L)
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
            shortBottom.update(pose(kind, 90.0), 579L)
            shortBottom.update(pose(kind, 130.0), 600L)
            shortBottom.update(pose(kind, 170.0), 1_000L)
            shortBottom.update(pose(kind, 170.0), 1_180L)
            assertEquals(0, shortBottom.reps)
        }
    }

    @Test
    fun `returning top must also remain stable for 180ms`() {
        for (kind in repetitionKinds) {
            val counter = ExerciseCounter(kind)
            primeBottom(counter)
            counter.update(pose(kind, 170.0), 1_200L)
            counter.update(pose(kind, 170.0), 1_379L)
            assertEquals(0, counter.reps)
            counter.update(pose(kind, 170.0), 1_380L)
            assertEquals(1, counter.reps)
        }
    }

    @Test
    fun `alternating top noise cannot arm the counter`() {
        for (kind in repetitionKinds) {
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
    }

    @Test
    fun `bottom noise cannot accumulate disjoint stable intervals`() {
        for (kind in repetitionKinds) {
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
    }

    @Test
    fun `returning top noise postpones credit until a stable top`() {
        for (kind in repetitionKinds) {
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
    }

    @Test
    fun `cycle minimum is 800ms and a rejected fast cycle cannot mature while stationary`() {
        for (kind in repetitionKinds) {
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
    }

    @Test
    fun `waiting at the initial top does not pad a fast cycle`() {
        for (kind in repetitionKinds) {
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
    }

    @Test
    fun `an in flight cycle expires after ten seconds`() {
        for (kind in repetitionKinds) {
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
    }

    @Test
    fun `500ms intervals are allowed but 501ms gaps discard the movement`() {
        for (kind in repetitionKinds) {
            for (gap in listOf(500L, 501L)) {
                val counter = ExerciseCounter(kind)
                primeBottom(counter)
                counter.update(pose(kind, 170.0), 800L + gap)
                counter.update(pose(kind, 170.0), 980L + gap)
                assertEquals(if (gap == 500L) 1 else 0, counter.reps)
            }
        }
    }

    @Test
    fun `duplicate regressing and overflowing timestamps discard the cycle`() {
        for (kind in repetitionKinds) {
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
    }

    @Test
    fun `missing pose and a side switch discard partial cycles`() {
        for (kind in repetitionKinds) {
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
    }

    @Test
    fun `pause preserves earned repetitions and points but not a partial movement`() {
        for (kind in repetitionKinds) {
            val counter = ExerciseCounter(kind)
            cycle(counter, 0L)
            cycle(counter, 1_600L)
            primeBottom(counter, 3_200L)
            counter.pause()
            counter.pause()
            assertEquals(2, counter.reps)
            assertEquals(2, counter.points)
            assertTrue(counter.feedback.contains("tạm dừng"))
            counter.update(pose(kind, 170.0), 4_200L)
            counter.update(pose(kind, 170.0), 4_400L)
            assertEquals(2, counter.reps)
            cycle(counter, 4_600L)
            assertEquals(3, counter.reps)
            assertEquals(if (kind == ExerciseKind.PUSH_UP) 3 else 2, counter.points)
        }
    }

    @Test
    fun `low confidence in any required landmark resets a cycle`() {
        for (kind in repetitionKinds) {
            for (index in 0..5) {
                val counter = ExerciseCounter(kind)
                primeBottom(counter)
                counter.update(changePoint(pose(kind, 90.0), index) { it.copy(confidence = 0.699f) }, 1_000L)
                counter.update(pose(kind, 170.0), 1_200L)
                counter.update(pose(kind, 170.0), 1_400L)
                assertEquals("$kind landmark $index", 0, counter.reps)
            }
        }
    }

    @Test
    fun `confidence of exactly point seven is accepted`() {
        for (kind in repetitionKinds) {
            val counter = ExerciseCounter(kind)
            cycle(counter, 0L, transform = { mapPoints(it) { point -> point.copy(confidence = 0.7f) } })
            assertEquals(1, counter.reps)
        }
        val counter = ExerciseCounter(ExerciseKind.PLANK)
        hold(counter, mapPoints(plank()) { it.copy(confidence = 0.7f) }, 0L, 5_000L)
        assertEquals(1, counter.points)
    }

    @Test
    fun `empty and nonfinite landmarks never earn credit`() {
        for (kind in ExerciseKind.values()) {
            val missing = ExerciseCounter(kind)
            hold(missing, null, 0L, 6_000L)
            assertEquals(0, missing.points)
            for (index in 0..5) {
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
            for ((start, end) in listOf(0 to 1, 1 to 2, 0 to 3, 3 to 4, 4 to 5)) {
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
    fun `sagging hips and bent knees invalidate horizontal pushups and plank`() {
        for (index in listOf(3, 4)) {
            val bend: (ExercisePose) -> ExercisePose = { input ->
                changePoint(input, index) { it.copy(y = it.y + 150f) }
            }
            val push = ExerciseCounter(ExerciseKind.PUSH_UP)
            cycle(push, 0L, transform = bend)
            assertEquals(0, push.reps)
            val plankCounter = ExerciseCounter(ExerciseKind.PLANK)
            hold(plankCounter, bend(plank()), 0L, 6_000L)
            assertEquals(0L, plankCounter.plankMillis)
        }
    }

    @Test
    fun `plank only accepts forearm elbow angles from 65 to 110 degrees`() {
        for (side in 0..1) {
            for (degrees in listOf(64.9, 65.1, 90.0, 109.9, 110.1, 170.0)) {
                val counter = ExerciseCounter(ExerciseKind.PLANK)
                hold(counter, plank(degrees, side), 0L, 5_000L)
                assertEquals("angle $degrees side $side", if (degrees in 65.0..110.0) 1 else 0, counter.points)
            }
        }
    }

    @Test
    fun `plank requires elbows below and reasonably under the shoulders`() {
        for (rotation in listOf(45.0, -45.0, 180.0)) {
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
    fun `plank earns zero at 4999ms one at five seconds and two at ten seconds`() {
        for (side in 0..1) {
            val counter = ExerciseCounter(ExerciseKind.PLANK)
            val pose = plank(side = side)
            hold(counter, pose, 0L, 4_999L)
            assertEquals(4_999L, counter.plankMillis)
            assertEquals(0, counter.points)
            counter.update(pose, 5_000L)
            assertEquals(5_000L, counter.plankMillis)
            assertEquals(1, counter.points)
            advance(counter, pose, 5_000L, 10_000L, 500L)
            assertEquals(10_000L, counter.plankMillis)
            assertEquals(2, counter.points)
            assertEquals(0, counter.reps)
        }
    }

    @Test
    fun `all plank interruptions preserve whole blocks but discard remainder and gap time`() {
        for (interruption in listOf("pause", "missing", "confidence", "posture", "gap", "regression", "duplicate", "side")) {
            val counter = ExerciseCounter(ExerciseKind.PLANK)
            hold(counter, plank(), 0L, 7_999L)
            val side = if (interruption == "side") 1 else 0
            val resumeAt = when (interruption) {
                "pause" -> {
                    counter.pause()
                    counter.pause()
                    10_000L
                }
                "missing" -> {
                    counter.update(null, 8_099L)
                    8_599L
                }
                "confidence" -> {
                    counter.update(plank().let { it.copy(wrist = it.wrist.copy(confidence = 0.69f)) }, 8_099L)
                    8_599L
                }
                "posture" -> {
                    counter.update(pushUp(170.0), 8_099L)
                    8_599L
                }
                "gap" -> 8_500L
                "regression" -> 7_899L
                "duplicate" -> 7_999L
                else -> 8_199L
            }
            counter.update(plank(side = side), resumeAt)
            assertEquals(interruption, 5_000L, counter.plankMillis)
            assertEquals(interruption, 1, counter.points)
            advance(counter, plank(side = side), resumeAt, resumeAt + 4_999L)
            assertEquals(interruption, 9_999L, counter.plankMillis)
            assertEquals(interruption, 1, counter.points)
            counter.update(plank(side = side), resumeAt + 5_000L)
            assertEquals(interruption, 10_000L, counter.plankMillis)
            assertEquals(interruption, 2, counter.points)
        }
    }

    @Test
    fun `repeated sub five second holds cannot bank hidden partial credit`() {
        val counter = ExerciseCounter(ExerciseKind.PLANK)
        repeat(10) {
            val start = it * 10_000L
            hold(counter, plank(), start, start + 4_999L)
            counter.update(null, start + 5_000L)
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
            assertEquals(if (kind == ExerciseKind.PUSH_UP) 121 else 120, counter.points)
        }
        val counter = ExerciseCounter(ExerciseKind.PLANK)
        hold(counter, plank(), 0L, 605_000L)
        assertEquals(121, counter.points)
    }

    private fun cycle(
        counter: ExerciseCounter,
        start: Long,
        side: Int = 0,
        top: Double = 170.0,
        bottom: Double = 90.0,
        transform: (ExercisePose) -> ExercisePose = { it }
    ) {
        val angles = listOf(top, top, 130.0, bottom, bottom, 130.0, top, top)
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
