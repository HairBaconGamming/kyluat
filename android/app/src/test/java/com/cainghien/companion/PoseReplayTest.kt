package com.cainghien.companion

import java.io.BufferedReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class PoseReplayTest {
    @Test
    fun `replay real video landmarks through the production counter`() {
        val dir = javaClass.getResource("/replay") ?: return
        val files = java.io.File(dir.toURI()).listFiles { f -> f.extension == "csv" }?.sorted() ?: emptyList()
        assumeTrue("no replay fixtures yet", files.isNotEmpty())
        val results = mutableListOf<Triple<String, Long, Long?>>()
        val timings = mutableListOf<Triple<String, List<Long>, List<LongRange>>>()
        for (file in files) {
            val reader = file.bufferedReader()
            val meta = reader.readLine() ?: continue
            val kind = ExerciseKind.valueOf(Regex("kind=(\\w+)").find(meta)!!.groupValues[1])
            val expected = Regex("expected=(\\d+)").find(meta)?.groupValues?.get(1)?.toInt()
            // Optional per-rep windows taken from the video, so spurious counts cannot stand in for missed reps.
            val windows = Regex("rep_ms=(\\S+)").find(meta)?.groupValues?.get(1)?.split(",")?.map { window ->
                val (from, to) = window.split("-").map(String::toLong)
                from..to
            }
            reader.readLine()
            val counter = ExerciseCounter(kind)
            val stabilizer = PoseStabilizer().apply { upperBody = kind == ExerciseKind.PUSH_UP }
            val byTime = sortedMapOf<Long, MutableList<ExercisePose>>()
            reader.forEachLine { line -> parse(line)?.let { (t, pose) -> byTime.getOrPut(t) { mutableListOf() }.add(pose) } }
            reader.close()
            var reps = 0
            val repTimes = mutableListOf<Long>()
            var correction: String? = null
            val dumpDir = java.io.File("build/replay-metrics").apply { mkdirs() }
            val dump = java.io.File(dumpDir, file.nameWithoutExtension + ".tsv").printWriter()
            dump.println(DUMP_HEADER)
            for ((t, candidates) in byTime) {
                val pose = stabilizer.update(candidates, t)
                counter.update(pose, t)
                dump.println(dumpRow(t, pose, counter))
                if (counter.reps != reps) {
                    repeat(counter.reps - reps) { repTimes.add(t) }
                    reps = counter.reps
                    println("REP ${file.name} t=$t reps=$reps")
                }
                if (counter.correction != correction) {
                    correction = counter.correction
                    if (correction != null) println("CORR ${file.name} t=$t $correction")
                }
            }
            dump.close()
            val measured = if (kind == ExerciseKind.PLANK) counter.plankMillis / 1000 else counter.reps.toLong()
            println("REPLAY ${file.name}: reps=${counter.reps} plankSeconds=${counter.plankMillis / 1000} points=${counter.points}")
            assertTrue("replay produced no feedback", counter.feedback.isNotBlank())
            results.add(Triple(file.name, measured, expected?.toLong()))
            windows?.let { timings.add(Triple(file.name, repTimes, it)) }
        }
        for ((name, measured, expected) in results) {
            if (expected != null) assertEquals(name, expected, measured)
        }
        for ((name, times, windows) in timings) {
            assertEquals("$name rep times $times", windows.size, times.size)
            times.zip(windows).forEach { (t, window) -> assertTrue("$name rep at $t outside $window", t in window) }
        }
    }

    // Per-frame trace for tools/render_count_overlay.py and threshold calibration.
    private fun dumpRow(t: Long, pose: ExercisePose?, counter: ExerciseCounter): String {
        fun p(b: BodyPoint?) = if (b == null) "\t\t" else "${b.x}\t${b.y}\t${b.confidence}"
        fun w(b: WorldPoint?) = if (b == null) "\t\t\t" else "${b.x}\t${b.y}\t${b.z}\t${b.confidence}"
        val joints = listOf(pose?.shoulder, pose?.elbow, pose?.wrist, pose?.hip, pose?.knee, pose?.ankle).joinToString("\t") { p(it) }
        val world = pose?.world.let { c -> listOf(c?.shoulder, c?.elbow, c?.wrist, c?.hip, c?.knee, c?.ankle) }.joinToString("\t") { w(it) }
        val m = counter.floor
        val metrics = listOf(m?.tilt, m?.height, m?.armAngle, m?.drop, m?.lastDrop, m?.lastTilt, m?.hipHeight)
            .joinToString("\t") { it?.let { v -> "%.4f".format(java.util.Locale.ROOT, v) } ?: "" }
        return listOf(
            t, counter.reps, counter.plankMillis, counter.view, pose?.side ?: "",
            if (counter.correction != null) 1 else 0, counter.feedback, joints, world, metrics
        ).joinToString("\t")
    }

    private companion object {
        val DUMP_HEADER = (listOf("t", "reps", "plank_ms", "view", "side", "corr", "feedback") +
            listOf("s", "e", "w", "h", "k", "a").flatMap { listOf("${it}x", "${it}y", "${it}c") } +
            listOf("s", "e", "w", "h", "k", "a").flatMap { listOf("w${it}x", "w${it}y", "w${it}z", "w${it}c") } +
            listOf("tilt", "height", "arm", "drop", "last_drop", "last_tilt", "hip")
            ).joinToString("\t")
    }

    private fun parse(line: String): Pair<Long, ExercisePose>? {
        val cells = line.split(",")
        if (cells.size < 26) return null
        fun point(i: Int): BodyPoint? {
            val x = cells[i].toFloatOrNull() ?: return null
            val y = cells[i + 1].toFloatOrNull() ?: return null
            val c = cells[i + 2].toFloatOrNull() ?: return null
            return BodyPoint(x, y, c)
        }
        fun world(i: Int): WorldPoint? {
            val x = cells[i].toFloatOrNull() ?: return null
            val y = cells[i + 1].toFloatOrNull() ?: return null
            val z = cells[i + 2].toFloatOrNull() ?: return null
            val c = cells[i + 3].toFloatOrNull() ?: return null
            return WorldPoint(x, y, z, c)
        }
        val shoulder = point(2) ?: return null
        val hip = point(11) ?: return null
        val knee = point(14) ?: return null
        val ankle = point(17) ?: return null
        val elbow = point(5) ?: BodyPoint(shoulder.x, shoulder.y, 0f)
        val wrist = point(8) ?: BodyPoint(shoulder.x, shoulder.y, 0f)
        val chain = listOf(world(26), world(30), world(34), world(38))
            .takeIf { it.all { p -> p != null && p.confidence >= 0.5f } }
            ?.let {
                val kneeWorld = world(42)?.takeIf { p -> p.confidence >= 0.5f } ?: WorldPoint(0f, 0f, 0f, 0f)
                val ankleWorld = world(46)?.takeIf { p -> p.confidence >= 0.5f } ?: WorldPoint(0f, 0f, 0f, 0f)
                WorldChain(it[0]!!, it[1]!!, it[2]!!, it[3]!!, kneeWorld, ankleWorld)
            }
        return cells[0].toLong() to ExercisePose(
            shoulder, elbow, wrist, hip, knee, ankle, cells[1].toInt(),
            otherShoulder = point(20), otherHip = point(23), world = chain
        )
    }
}