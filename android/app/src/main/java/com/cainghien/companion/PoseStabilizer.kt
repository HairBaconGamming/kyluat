package com.cainghien.companion

import kotlin.math.exp
import kotlin.math.sqrt

class PoseStabilizer {
    var pose: ExercisePose? = null
        private set

    private var lastGoodAt: Long? = null
    private var currentSide: Int? = null
    private var pendingSide: Int? = null
    private var pendingSideSince: Long = 0L

    fun update(candidates: List<ExercisePose>, nowMs: Long): ExercisePose? {
        val usable = candidates.filter { coreUsable(it) }
        val best = usable.maxByOrNull { confidence(it) } ?: return dropout(nowMs)
        if (currentSide == null) {
            currentSide = best.side
        } else if (best.side != currentSide) {
            if (pendingSide != best.side) {
                pendingSide = best.side
                pendingSideSince = nowMs
            } else if (nowMs - pendingSideSince >= SIDE_HOLD_MS) {
                currentSide = best.side
                pendingSide = null
                pose = null
                lastGoodAt = null
            }
        } else {
            pendingSide = null
        }
        var chosen = usable.firstOrNull { it.side == currentSide }
        if (chosen == null) {
            val held = holdIfFresh(nowMs)
            if (held != null) return held
            val pending = pendingSide
            if (pending != null && usable.any { it.side == pending }) {
                currentSide = pending
                pendingSide = null
                pose = null
                lastGoodAt = null
                chosen = usable.firstOrNull { it.side == currentSide }
            }
            if (chosen == null) {
                reset()
                return null
            }
        }
        val previous = pose
        val smoothed = if (previous == null || previous.side != chosen.side) chosen
        else smooth(previous, chosen, nowMs) ?: return dropout(nowMs)
        pose = smoothed
        lastGoodAt = nowMs
        return smoothed
    }

    fun reset() {
        pose = null
        lastGoodAt = null
        currentSide = null
        pendingSide = null
    }

    private fun dropout(nowMs: Long): ExercisePose? {
        val held = holdIfFresh(nowMs)
        if (held != null) return held
        reset()
        return null
    }

    private fun holdIfFresh(nowMs: Long): ExercisePose? {
        val held = pose
        val at = lastGoodAt
        return if (held != null && at != null && nowMs >= at && nowMs - at <= HOLD_MS) held else null
    }

    private fun smooth(previous: ExercisePose, next: ExercisePose, nowMs: Long): ExercisePose? {
        val at = lastGoodAt ?: return next
        val dt = nowMs - at
        if (dt <= 0L) return next
        val torso = distance(next.shoulder, next.hip)
        if (torso < MIN_SEGMENT_PX) return next
        val core = listOf(
            previous.shoulder to next.shoulder,
            previous.hip to next.hip,
            previous.knee to next.knee,
            previous.ankle to next.ankle
        )
        if (core.any { distance(it.first, it.second) > torso * JUMP_RATIO }) return null
        val alpha = (1.0 - exp(-dt.toDouble() / TAU_MS)).toFloat()
        fun blend(a: BodyPoint, b: BodyPoint) =
            BodyPoint(a.x + (b.x - a.x) * alpha, a.y + (b.y - a.y) * alpha, b.confidence)
        fun blendNullable(a: BodyPoint?, b: BodyPoint?) = if (a != null && b != null) blend(a, b) else b
        return next.copy(
            shoulder = blend(previous.shoulder, next.shoulder),
            elbow = blend(previous.elbow, next.elbow),
            wrist = blend(previous.wrist, next.wrist),
            hip = blend(previous.hip, next.hip),
            knee = blend(previous.knee, next.knee),
            ankle = blend(previous.ankle, next.ankle),
            otherShoulder = blendNullable(previous.otherShoulder, next.otherShoulder),
            otherHip = blendNullable(previous.otherHip, next.otherHip)
        )
    }

    private fun coreUsable(pose: ExercisePose): Boolean =
        listOf(pose.shoulder, pose.hip, pose.knee, pose.ankle).all {
            it.x.isFinite() && it.y.isFinite() && it.confidence.isFinite() && it.confidence >= MIN_CONFIDENCE
        }

    private fun confidence(pose: ExercisePose): Float =
        minOf(pose.shoulder.confidence, pose.hip.confidence, pose.knee.confidence, pose.ankle.confidence)

    private fun distance(a: BodyPoint, b: BodyPoint): Double {
        val dx = a.x.toDouble() - b.x
        val dy = a.y.toDouble() - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private companion object {
        const val HOLD_MS = 400L
        const val SIDE_HOLD_MS = 600L
        const val TAU_MS = 90.0
        const val JUMP_RATIO = 0.6
        const val MIN_CONFIDENCE = 0.7f
        const val MIN_SEGMENT_PX = 8.0
    }
}