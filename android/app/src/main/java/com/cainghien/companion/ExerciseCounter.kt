package com.cainghien.companion

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

enum class ExerciseKind { PUSH_UP, SQUAT, PLANK }

enum class CameraView { SIDE, FRONT, DIAGONAL, UNKNOWN }

data class BodyPoint(val x: Float, val y: Float, val confidence: Float)

data class ExercisePose(
    val shoulder: BodyPoint,
    val elbow: BodyPoint,
    val wrist: BodyPoint,
    val hip: BodyPoint,
    val knee: BodyPoint,
    val ankle: BodyPoint,
    val side: Int,
    val otherShoulder: BodyPoint? = null,
    val otherHip: BodyPoint? = null
)

class ExerciseCounter(val kind: ExerciseKind) {
    var reps: Int = 0
        private set

    var plankMillis: Long = 0L
        private set

    val points: Int
        get() = when (kind) {
            ExerciseKind.PUSH_UP -> reps / PUSH_UP_PER_POINT
            ExerciseKind.SQUAT -> reps / SQUAT_PER_POINT
            ExerciseKind.PLANK -> (plankMillis / PLANK_BLOCK_MS).toInt()
        }

    var feedback: String = startingFeedback()
        private set

    var correction: String? = null
        private set

    var view: CameraView = CameraView.SIDE
        private set

    val viewLabel: String
        get() = when (view) {
            CameraView.SIDE -> "Bên hông"
            CameraView.FRONT -> "Chính diện"
            CameraView.DIAGONAL -> "Chếch 45°"
            CameraView.UNKNOWN -> "Chưa rõ góc"
        }

    private enum class Phase { FIND_TOP, READY, LOWERING, RISING }
    private enum class Extremum { NONE, TOP, BOTTOM }

    private var phase = Phase.FIND_TOP
    private var extremum = Extremum.NONE
    private var extremumSince: Long = 0L
    private var cycleSince: Long? = null
    private var lastTime: Long? = null
    private var lastSide: Int? = null
    private var lastView: CameraView? = null

    fun update(pose: ExercisePose?, nowMs: Long) {
        if (pose == null) {
            invalidate("Đưa toàn thân vào khung hình.")
            return
        }
        if (!landmarksValid(pose)) {
            invalidate("Chưa thấy rõ các khớp. Điều chỉnh camera.")
            return
        }
        correction = null
        view = classifyView(pose)
        if (lastView != null && lastView != view) clearInFlight()
        lastView = view

        if (kind == ExerciseKind.SQUAT) {
            if (!upright(pose)) {
                invalidate("Đứng thẳng trên sàn, đừng nằm hoặc cúi gập người.")
                return
            }
        } else {
            if (!orientationOk(pose, view)) {
                invalidate(orientationMessage(view))
                return
            }
            if (angle(pose.shoulder, pose.hip, pose.knee) < STRAIGHT_ANGLE ||
                angle(pose.hip, pose.knee, pose.ankle) < STRAIGHT_ANGLE
            ) {
                invalidate("Giữ vai, hông và chân thành một đường thẳng.")
                return
            }
        }

        if (kind == ExerciseKind.PLANK && !forearmSupport(pose)) {
            invalidate("Đặt khuỷu tay dưới vai, gập tay gần vuông góc.")
            return
        }

        val previousTime = lastTime
        if (previousTime != null) {
            val elapsed = nowMs - previousTime
            if (nowMs <= previousTime || elapsed < 0L || elapsed > MAX_INTERVAL_MS ||
                lastSide != pose.side
            ) {
                clearInFlight()
            }
        }

        if (kind == ExerciseKind.PLANK) {
            lastTime?.let { plankMillis += nowMs - it }
            feedback = "Giữ thân thẳng, chống cẳng tay và thở đều."
        } else {
            val jointAngle = if (kind == ExerciseKind.PUSH_UP) {
                angle(pose.shoulder, pose.elbow, pose.wrist)
            } else {
                angle(pose.hip, pose.knee, pose.ankle)
            }
            updateCycle(jointAngle, nowMs)
        }
        lastTime = nowMs
        lastSide = pose.side
    }

    fun pause() {
        invalidate("Đã tạm dừng. Vào tư thế để tiếp tục.", isCorrection = false)
    }

    private fun updateCycle(jointAngle: Double, nowMs: Long) {
        cycleSince?.let {
            if (nowMs - it > MAX_CYCLE_MS) clearInFlight()
        }

        val topAngle = if (kind == ExerciseKind.PUSH_UP) 155.0 else 160.0
        val bottomAngle = if (kind == ExerciseKind.PUSH_UP) 100.0 else 105.0
        val current = when {
            jointAngle >= topAngle -> Extremum.TOP
            jointAngle <= bottomAngle -> Extremum.BOTTOM
            else -> Extremum.NONE
        }
        if (current != extremum) {
            extremum = current
            extremumSince = nowMs
        }
        val stable = current != Extremum.NONE && nowMs - extremumSince >= STABLE_MS

        when (phase) {
            Phase.FIND_TOP -> {
                feedback = startingFeedback()
                if (current == Extremum.TOP && stable) {
                    phase = Phase.READY
                    feedback = lowerFeedback()
                }
            }
            Phase.READY -> {
                feedback = lowerFeedback()
                if (jointAngle < topAngle - DEPARTURE_MARGIN) {
                    phase = Phase.LOWERING
                    cycleSince = nowMs
                    feedback = if (current == Extremum.BOTTOM) {
                        "Giữ vị trí thấp một nhịp."
                    } else {
                        lowerFeedback()
                    }
                }
            }
            Phase.LOWERING -> {
                feedback = if (current == Extremum.BOTTOM) {
                    "Giữ vị trí thấp một nhịp."
                } else {
                    "Hạ thấp thêm, giữ tư thế vững.".also { correction = it }
                }
                if (current == Extremum.BOTTOM && stable) {
                    phase = Phase.RISING
                    feedback = riseFeedback()
                } else if (current == Extremum.TOP && stable) {
                    phase = Phase.READY
                    cycleSince = null
                    feedback = lowerFeedback()
                }
            }
            Phase.RISING -> {
                feedback = riseFeedback()
                if (current == Extremum.TOP && stable) {
                    val duration = nowMs - (cycleSince ?: nowMs)
                    if (duration in MIN_CYCLE_MS..MAX_CYCLE_MS) {
                        reps += 1
                        feedback = "Đã ghi nhận. ${lowerFeedback()}"
                    } else {
                        feedback = "Chậm lại, thực hiện trọn một nhịp.".also { correction = it }
                    }
                    phase = Phase.READY
                    cycleSince = null
                }
            }
        }
    }

    private fun invalidate(message: String, isCorrection: Boolean = true) {
        clearInFlight()
        feedback = message
        correction = if (isCorrection) message else null
    }

    private fun clearInFlight() {
        phase = Phase.FIND_TOP
        extremum = Extremum.NONE
        extremumSince = 0L
        cycleSince = null
        lastTime = null
        lastSide = null
        plankMillis -= plankMillis % PLANK_BLOCK_MS
    }

    private fun classifyView(pose: ExercisePose): CameraView {
        val os = pose.otherShoulder
        val oh = pose.otherHip
        if (os == null || oh == null || !pointUsable(os) || !pointUsable(oh)) return CameraView.SIDE
        val midShoulder = BodyPoint((pose.shoulder.x + os.x) / 2f, (pose.shoulder.y + os.y) / 2f, 1f)
        val midHip = BodyPoint((pose.hip.x + oh.x) / 2f, (pose.hip.y + oh.y) / 2f, 1f)
        val torso = distance(midShoulder, midHip)
        if (torso < MIN_SEGMENT_PX) return CameraView.UNKNOWN
        val ratio = distance(pose.shoulder, os) / torso
        return when {
            ratio >= FRONT_RATIO -> CameraView.FRONT
            ratio <= SIDE_RATIO -> CameraView.SIDE
            else -> CameraView.DIAGONAL
        }
    }

    private fun orientationOk(pose: ExercisePose, view: CameraView): Boolean = when (view) {
        CameraView.SIDE -> horizontal(pose, 0.5)
        CameraView.FRONT -> shouldersLevel(pose)
        CameraView.DIAGONAL -> horizontal(pose, 1.0) || shouldersLevel(pose)
        CameraView.UNKNOWN -> false
    }

    private fun orientationMessage(view: CameraView): String = when (view) {
        CameraView.FRONT -> "Giữ hai vai ngang nhau, đừng nghiêng người."
        CameraView.SIDE -> "Đặt camera bên hông, giữ thân gần song song với sàn."
        else -> "Xoay máy rõ bên hông hoặc chính diện để đếm đúng."
    }

    private fun shouldersLevel(pose: ExercisePose): Boolean {
        val os = pose.otherShoulder ?: return false
        if (!pointUsable(os)) return false
        val torso = distance(pose.shoulder, pose.hip)
        if (torso < MIN_SEGMENT_PX) return false
        return abs(pose.shoulder.y.toDouble() - os.y) <= torso * LEVEL_TOL
    }

    private fun pointUsable(p: BodyPoint): Boolean =
        p.x.isFinite() && p.y.isFinite() && p.confidence.isFinite() && p.confidence >= MIN_CONFIDENCE

    private fun startingFeedback(): String = when (kind) {
        ExerciseKind.PUSH_UP -> "Duỗi thẳng tay, giữ thân thẳng để bắt đầu."
        ExerciseKind.SQUAT -> "Đứng thẳng, duỗi chân để bắt đầu."
        ExerciseKind.PLANK -> "Chống cẳng tay, giữ thân thẳng và gần song song với sàn."
    }

    private fun lowerFeedback(): String = if (kind == ExerciseKind.PUSH_UP) {
        "Hạ người có kiểm soát."
    } else {
        "Hạ hông xuống có kiểm soát."
    }

    private fun riseFeedback(): String = if (kind == ExerciseKind.PUSH_UP) {
        "Đẩy người lên, duỗi thẳng tay."
    } else {
        "Đứng lên, duỗi thẳng chân."
    }

    private fun landmarksValid(pose: ExercisePose): Boolean {
        val points = listOf(pose.shoulder, pose.elbow, pose.wrist, pose.hip, pose.knee, pose.ankle)
        if (points.any { !it.x.isFinite() || !it.y.isFinite() ||
                !it.confidence.isFinite() || it.confidence < MIN_CONFIDENCE
            }
        ) return false
        return distance(pose.shoulder, pose.elbow) >= MIN_SEGMENT_PX &&
            distance(pose.elbow, pose.wrist) >= MIN_SEGMENT_PX &&
            distance(pose.shoulder, pose.hip) >= MIN_SEGMENT_PX &&
            distance(pose.hip, pose.knee) >= MIN_SEGMENT_PX &&
            distance(pose.knee, pose.ankle) >= MIN_SEGMENT_PX
    }

    private fun horizontal(pose: ExercisePose, slope: Double): Boolean =
        abs(pose.ankle.y.toDouble() - pose.shoulder.y) <=
            abs(pose.ankle.x.toDouble() - pose.shoulder.x) * slope

    private fun upright(pose: ExercisePose): Boolean {
        val torsoDrop = pose.hip.y.toDouble() - pose.shoulder.y
        val legDrop = pose.ankle.y.toDouble() - pose.hip.y
        val fullDrop = pose.ankle.y.toDouble() - pose.shoulder.y
        return torsoDrop >= distance(pose.shoulder, pose.hip) * 0.45 &&
            legDrop >= distance(pose.knee, pose.ankle) * 0.4 &&
            abs(pose.ankle.x.toDouble() - pose.shoulder.x) <= fullDrop * 0.8
    }

    private fun forearmSupport(pose: ExercisePose): Boolean {
        val elbowAngle = angle(pose.shoulder, pose.elbow, pose.wrist)
        val upperArm = distance(pose.shoulder, pose.elbow)
        return elbowAngle in 65.0..110.0 &&
            pose.elbow.y.toDouble() - pose.shoulder.y >= upperArm * 0.6 &&
            abs(pose.elbow.x.toDouble() - pose.shoulder.x) <= upperArm * 0.6
    }

    private fun distance(a: BodyPoint, b: BodyPoint): Double {
        val dx = a.x.toDouble() - b.x
        val dy = a.y.toDouble() - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun angle(a: BodyPoint, joint: BodyPoint, b: BodyPoint): Double {
        val ax = a.x.toDouble() - joint.x
        val ay = a.y.toDouble() - joint.y
        val bx = b.x.toDouble() - joint.x
        val by = b.y.toDouble() - joint.y
        val cosine = (ax * bx + ay * by) / (distance(a, joint) * distance(b, joint))
        return acos(cosine.coerceIn(-1.0, 1.0)) * 180.0 / PI
    }

    private companion object {
        const val MIN_CONFIDENCE = 0.7f
        const val MIN_SEGMENT_PX = 8.0
        const val STRAIGHT_ANGLE = 160.0
        const val DEPARTURE_MARGIN = 5.0
        const val STABLE_MS = 180L
        const val MIN_CYCLE_MS = 800L
        const val MAX_CYCLE_MS = 10_000L
        const val MAX_INTERVAL_MS = 500L
        const val PLANK_BLOCK_MS = 10_000L
        const val PUSH_UP_PER_POINT = 3
        const val SQUAT_PER_POINT = 5
        const val FRONT_RATIO = 0.6
        const val SIDE_RATIO = 0.3
        const val LEVEL_TOL = 0.25
    }
}