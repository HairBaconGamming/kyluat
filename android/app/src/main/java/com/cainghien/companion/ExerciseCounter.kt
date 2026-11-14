package com.cainghien.companion

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

enum class ExerciseKind { PUSH_UP, SQUAT, PLANK }

enum class CameraView { SIDE, FRONT, DIAGONAL, UNKNOWN }

data class BodyPoint(val x: Float, val y: Float, val confidence: Float)

data class WorldPoint(val x: Float, val y: Float, val z: Float, val confidence: Float)

data class WorldChain(
    val shoulder: WorldPoint,
    val elbow: WorldPoint,
    val wrist: WorldPoint,
    val hip: WorldPoint,
    val knee: WorldPoint,
    val ankle: WorldPoint
)

data class ExercisePose(
    val shoulder: BodyPoint,
    val elbow: BodyPoint,
    val wrist: BodyPoint,
    val hip: BodyPoint,
    val knee: BodyPoint,
    val ankle: BodyPoint,
    val side: Int,
    val otherShoulder: BodyPoint? = null,
    val otherHip: BodyPoint? = null,
    val world: WorldChain? = null
)

// Tilt is against gravity (horizontal in the upright frame); heights are above the body's support.
data class FloorMetrics(
    val tilt: Double, // degrees: push-up torso (shoulder→hip), plank body (shoulder→ankle)
    val height: Double, // shoulder height ÷ whole arm (push-up, above wrists) or upper arm (plank, above elbows)
    val armAngle: Double, // degrees to the floor, 90 = perpendicular
    val drop: Double? = null, // push-up: shoulder descent since the last top ÷ arm
    val lastDrop: Double? = null, // push-up: descent at the last judged valley ÷ arm
    val lastTilt: Double? = null, // push-up: torso tilt at the last judged valley
    val hipHeight: Double? = null // plank: hip height ÷ shoulder height
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

    var floor: FloorMetrics? = null
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
    private val imagePushCycle = PushCycle(PUSH_IMAGE_BOTTOM)
    private val worldPushCycle = PushCycle(PUSH_WORLD_BOTTOM)
    private var pushLastRep = -PUSH_MIN_REP_MS
    private var pushRejection: String? = null
    private var pushRejectionUntil = 0L
    private var lastTime: Long? = null
    private var lastSide: Int? = null
    private var viewInitialized = false
    private var pendingView: CameraView? = null
    private var pendingViewSince: Long = 0L

    fun update(pose: ExercisePose?, nowMs: Long) {
        if (pose == null) {
            floor = null
            invalidate(if (kind == ExerciseKind.PUSH_UP) "Đưa vai, tay và hông vào khung hình." else "Đưa toàn thân vào khung hình.")
            return
        }
        if (!landmarksValid(pose)) {
            floor = null
            invalidate("Chưa thấy rõ các khớp. Điều chỉnh camera.")
            return
        }
        correction = null
        val classified = classifyView(pose)
        if (!viewInitialized) {
            view = classified
            viewInitialized = true
        } else if (classified != view) {
            if (pendingView != classified) {
                pendingView = classified
                pendingViewSince = nowMs
            } else if (nowMs - pendingViewSince >= VIEW_HOLD_MS) {
                view = classified
                pendingView = null
                // World angles are view-independent; only 2D fallbacks change meaning.
                if (pose.world == null) clearInFlight()
            }
        } else {
            pendingView = null
        }
        floor = when (kind) {
            ExerciseKind.PUSH_UP -> pushMetrics(pose)
            ExerciseKind.PLANK -> plankMetrics(pose)
            ExerciseKind.SQUAT -> null
        }

        if (kind == ExerciseKind.SQUAT) {
            if (!upright(pose)) {
                invalidate("Đứng thẳng trên sàn, đừng nằm hoặc cúi gập người.")
                return
            }
        } else if (kind == ExerciseKind.PUSH_UP) {
            // Imperfect form (piked hips, sagging belly) still counts: only reject an upright
            // torso with no hands-down support, which is a standing arm-bend, not a push-up.
            if (!pushupPosture(pose)) {
                invalidate("Vào tư thế chống đẩy: tay chống sàn, thân không dựng đứng.")
                return
            }
            if (view == CameraView.UNKNOWN ||
                (pose.world == null && view == CameraView.FRONT)
            ) {
                invalidate(orientationMessage(view))
                return
            }
        } else {
            if (!orientationOk(pose, view)) {
                invalidate(orientationMessage(view))
                return
            }
            val w = pose.world
            val bodyStraight = if (w != null) {
                angle3(w.shoulder, w.hip, w.knee) >= STRAIGHT_ANGLE &&
                    angle3(w.hip, w.knee, w.ankle) >= STRAIGHT_ANGLE
            } else {
                angle(pose.shoulder, pose.hip, pose.knee) >= STRAIGHT_ANGLE &&
                    angle(pose.hip, pose.knee, pose.ankle) >= STRAIGHT_ANGLE
            }
            if (!bodyStraight) {
                invalidate("Giữ vai, hông và chân thành một đường thẳng.")
                return
            }
        }

        if (kind == ExerciseKind.PLANK) {
            if (!forearmSupport(pose)) {
                invalidate("Đặt khuỷu tay dưới vai, gập tay gần vuông góc.")
                return
            }
            floor?.let(::plankFloorIssue)?.let {
                invalidate(it)
                return
            }
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
        } else if (kind == ExerciseKind.PUSH_UP) {
            updatePushCycle(pose, pushMetrics(pose), nowMs)
        } else {
            val jointAngle = when {
                pose.world != null -> angle3(pose.world.hip, pose.world.knee, pose.world.ankle)
                view == CameraView.SIDE -> angle(pose.hip, pose.knee, pose.ankle)
                else -> squatDepthAngle(pose)
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

        // 150° rather than full extension: world knees read ~155° standing.
        val topAngle = 150.0
        val bottomAngle = 105.0
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

    private fun updatePushCycle(pose: ExercisePose, metrics: FloorMetrics, nowMs: Long) {
        val imageAngle = angle(pose.shoulder, pose.elbow, pose.wrist)
        val worldAngle = pose.world?.let { angle3(it.shoulder, it.elbow, it.wrist) }
        val ready = nowMs - pushLastRep >= PUSH_MIN_REP_MS
        // Independent histories preserve a real descent without treating a source switch as motion.
        // Depth grows as the shoulders sink towards the floor. The image floor is fixed in the
        // upright frame, so image depth is the shoulder moving along gravity (per-frame wrists
        // glitch too much); world points are hip-centred, so world depth is relative to the wrists.
        val arm = distance(pose.shoulder, pose.elbow) + distance(pose.elbow, pose.wrist)
        val imageResult = imagePushCycle.update(imageAngle, pose.shoulder.y.toDouble(), arm, metrics.tilt, ready)
        val world = pose.world
        val worldResult = if (world != null && worldAngle != null) {
            worldPushCycle.update(
                worldAngle, world.shoulder.y.toDouble() - world.wrist.y,
                length3(world.shoulder, world.elbow) + length3(world.elbow, world.wrist), metrics.tilt, ready
            )
        } else PushResult.NONE.also { worldPushCycle.reset(preserveSuppression = true) }
        val usesWorld = view != CameraView.SIDE && worldAngle != null
        val active = if (usesWorld) worldPushCycle else imagePushCycle
        val result = if (usesWorld) worldResult else imageResult
        val counted = result == PushResult.COUNTED
        if (counted) {
            reps += 1
            pushLastRep = nowMs
            pushRejection = null
            // The unselected source must not credit the same descent after a view change.
            if (usesWorld) imagePushCycle.suppress(imageAngle) else worldPushCycle.suppress(worldAngle)
        } else if (result == PushResult.TILTED ||
            // A bend with level shoulders is arm-landmark noise, not an attempt: stay silent.
            (result == PushResult.SHALLOW && (active.lastDrop ?: 0.0) >= PUSH_DROP / 2)
        ) {
            pushRejection = if (result == PushResult.SHALLOW) "Hạ cả thân trên xuống gần sàn, đừng chỉ gập tay."
                else "Giữ thân gần song song với sàn khi hạ xuống."
            pushRejectionUntil = nowMs + PUSH_REJECTION_MS
        }
        floor = metrics.copy(drop = active.drop, lastDrop = active.lastDrop, lastTilt = active.lastTilt)
        val jointAngle = if (usesWorld) worldAngle!! else imageAngle
        val bottom = if (usesWorld) PUSH_WORLD_BOTTOM else PUSH_IMAGE_BOTTOM
        val rejection = pushRejection?.takeIf { nowMs < pushRejectionUntil }
        feedback = when {
            counted -> "Đã ghi nhận. ${lowerFeedback()}"
            rejection != null -> rejection.also { correction = it }
            jointAngle <= bottom + 10.0 -> riseFeedback()
            else -> lowerFeedback()
        }
    }

    private enum class PushResult { NONE, COUNTED, SHALLOW, TILTED }

    private class PushCycle(private val bottom: Double) {
        private var peak = 0.0
        private var candidate = 180.0
        private var swing = 0.0
        private var armed = true
        private var valley = 0.0
        private var suppressed = false
        private var suppressedValley = 180.0
        private var top = Double.MAX_VALUE
        private var sink = 0.0
        private var valleyTilt = 0.0
        private var reach = 0.0
        private var depth = Double.NaN

        var lastDrop: Double? = null
            private set
        var lastTilt: Double? = null
            private set
        val drop: Double?
            get() = if (reach > 0.0 && depth.isFinite()) (depth - top).coerceAtLeast(0.0) / reach else null

        fun update(jointAngle: Double, depth: Double, arm: Double, tilt: Double, ready: Boolean): PushResult {
            if (suppressed) {
                suppressedValley = minOf(suppressedValley, jointAngle)
                if (jointAngle < suppressedValley + PUSH_REARM) return PushResult.NONE
                reset()
            }
            this.depth = depth
            if (!armed) {
                if (jointAngle < valley + PUSH_REARM) return PushResult.NONE
                armed = true
                peak = jointAngle
                candidate = jointAngle
                swing = 0.0
                restartSink(depth, arm)
            }
            top = minOf(top, depth)
            reach = maxOf(reach, arm)
            peak = maxOf(peak, jointAngle)
            var result = PushResult.NONE
            if (jointAngle <= candidate) {
                candidate = jointAngle
                swing = maxOf(swing, peak - candidate)
                // Read at the valley frame: a glitching arm bends while the shoulder stays level.
                sink = depth - top
                valleyTilt = tilt
            } else if (jointAngle >= candidate + PUSH_CONFIRM) {
                if (candidate <= bottom && swing >= PUSH_AMP && ready) {
                    lastDrop = sink / reach
                    lastTilt = valleyTilt
                    result = when {
                        sink < PUSH_DROP * reach -> PushResult.SHALLOW
                        valleyTilt > PUSH_MAX_TILT -> PushResult.TILTED
                        else -> PushResult.COUNTED
                    }
                }
                if (result == PushResult.COUNTED) {
                    armed = false
                    valley = candidate
                }
                peak = jointAngle
                candidate = 180.0
                swing = 0.0
                restartSink(depth, arm)
            }
            return result
        }

        private fun restartSink(depth: Double, arm: Double) {
            top = depth
            sink = 0.0
            reach = arm
        }

        fun suppress(jointAngle: Double?) {
            suppressed = true
            suppressedValley = minOf(candidate, jointAngle ?: 180.0, if (armed) 180.0 else valley)
        }

        fun reset(preserveSuppression: Boolean = false) {
            peak = 0.0
            candidate = 180.0
            swing = 0.0
            armed = true
            valley = 0.0
            top = Double.MAX_VALUE
            sink = 0.0
            valleyTilt = 0.0
            reach = 0.0
            depth = Double.NaN
            if (!preserveSuppression) {
                suppressed = false
                suppressedValley = 180.0
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
        imagePushCycle.reset()
        worldPushCycle.reset()
        pushLastRep = -PUSH_MIN_REP_MS
        pushRejection = null
        lastTime = null
        lastSide = null
        pendingView = null
        plankMillis -= plankMillis % PLANK_BLOCK_MS
    }

    // Knee angle in the image stays ~180° when squatting towards the camera,
    // so front/diagonal views measure depth by how far the hip drops along the leg.
    private fun squatDepthAngle(pose: ExercisePose): Double {
        val leg = distance(pose.hip, pose.knee) + distance(pose.knee, pose.ankle)
        if (leg < MIN_SEGMENT_PX) return 180.0
        val drop = (pose.ankle.y.toDouble() - pose.hip.y) / leg
        return 90.0 + 90.0 * ((drop - 0.5) / 0.5).coerceIn(0.0, 1.0)
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
        // A horizontal torso projects shoulder width onto the same image axis as the
        // torso, inflating the ratio; only a near-head-on view must fail the side gate.
        if (abs(midHip.y - midShoulder.y) <= abs(midHip.x - midShoulder.x)) {
            return if (ratio >= FRONT_RATIO_HORIZONTAL) CameraView.FRONT else CameraView.SIDE
        }
        return when {
            ratio >= FRONT_RATIO -> CameraView.FRONT
            ratio <= SIDE_RATIO -> CameraView.SIDE
            else -> CameraView.DIAGONAL
        }
    }

    private fun orientationOk(pose: ExercisePose, view: CameraView): Boolean = when (view) {
        // The side-view body angle is judged against the floor in plankFloorIssue.
        CameraView.SIDE -> true
        CameraView.FRONT -> kind == ExerciseKind.SQUAT && shouldersLevel(pose)
        CameraView.DIAGONAL -> horizontal(pose, 1.0) || shouldersLevel(pose)
        CameraView.UNKNOWN -> false
    }

    private fun orientationMessage(view: CameraView): String = when {
        view == CameraView.FRONT && kind != ExerciseKind.SQUAT ->
            "Bài này cần góc bên hông hoặc chếch 45°. Xoay máy rồi thử lại."
        view == CameraView.FRONT -> "Giữ hai vai ngang nhau, đừng nghiêng người."
        view == CameraView.SIDE -> "Đặt camera bên hông, giữ thân gần song song với sàn."
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
        ExerciseKind.PUSH_UP -> "Chống tay xuống sàn, vào tư thế thoải mái để bắt đầu."
        ExerciseKind.SQUAT -> "Đứng thẳng, duỗi chân để bắt đầu."
        ExerciseKind.PLANK -> "Chống cẳng tay, giữ thân thẳng và gần song song với sàn."
    }

    private fun lowerFeedback(): String = if (kind == ExerciseKind.PUSH_UP) {
        "Hạ người có kiểm soát."
    } else {
        "Hạ hông xuống có kiểm soát."
    }

    private fun riseFeedback(): String = if (kind == ExerciseKind.PUSH_UP) {
        "Đẩy người lên trong tầm sức của bạn."
    } else {
        "Đứng lên, duỗi thẳng chân."
    }

    private fun landmarksValid(pose: ExercisePose): Boolean {
        val points = when (kind) {
            ExerciseKind.SQUAT -> listOf(pose.shoulder, pose.hip, pose.knee, pose.ankle)
            ExerciseKind.PUSH_UP -> listOf(pose.shoulder, pose.elbow, pose.wrist, pose.hip)
            ExerciseKind.PLANK -> listOf(pose.shoulder, pose.elbow, pose.wrist, pose.hip, pose.knee, pose.ankle)
        }
        if (points.any { !it.x.isFinite() || !it.y.isFinite() || !it.confidence.isFinite() }) return false
        val visible = if (kind == ExerciseKind.PUSH_UP) listOf(pose.shoulder, pose.hip) else points
        if (visible.any { it.confidence < MIN_CONFIDENCE }) return false
        val segments = when (kind) {
            ExerciseKind.SQUAT -> listOf(pose.shoulder to pose.hip, pose.hip to pose.knee, pose.knee to pose.ankle)
            ExerciseKind.PUSH_UP -> listOf(pose.shoulder to pose.elbow, pose.elbow to pose.wrist, pose.shoulder to pose.hip)
            ExerciseKind.PLANK -> listOf(pose.shoulder to pose.elbow, pose.elbow to pose.wrist, pose.shoulder to pose.hip,
                pose.hip to pose.knee, pose.knee to pose.ankle)
        }
        return segments.all { distance(it.first, it.second) >= MIN_SEGMENT_PX }
    }

    // A push-up (even piked or sagged) has the hands below the shoulder and the torso leaning
    // away from vertical; a standing arm-bend has a vertical torso. Tolerates imperfect form.
    private fun pushupPosture(pose: ExercisePose): Boolean {
        val dx = abs(pose.hip.x.toDouble() - pose.shoulder.x)
        val dy = abs(pose.hip.y.toDouble() - pose.shoulder.y)
        return pose.wrist.y > pose.shoulder.y && dx >= dy * 0.5
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

    private fun forearmSupport(pose: ExercisePose): Boolean =
        angle(pose.shoulder, pose.elbow, pose.wrist) in 45.0..135.0

    private fun pushMetrics(pose: ExercisePose): FloorMetrics {
        val w = pose.world?.takeIf { view != CameraView.SIDE }
        val rise: Double
        val run: Double
        val arm: Double
        if (w != null) {
            rise = w.wrist.y.toDouble() - w.shoulder.y
            run = hypot(w.wrist.x.toDouble() - w.shoulder.x, w.wrist.z.toDouble() - w.shoulder.z)
            arm = length3(w.shoulder, w.elbow) + length3(w.elbow, w.wrist)
        } else {
            rise = pose.wrist.y.toDouble() - pose.shoulder.y
            run = abs(pose.wrist.x.toDouble() - pose.shoulder.x)
            arm = distance(pose.shoulder, pose.elbow) + distance(pose.elbow, pose.wrist)
        }
        return FloorMetrics(
            tilt = floorTilt(pose.shoulder, pose.hip, pose.world?.shoulder, pose.world?.hip),
            height = if (arm > 0.0) rise / arm else 0.0,
            armAngle = Math.toDegrees(atan2(rise, run))
        )
    }

    // Heights are measured above the elbow→ankle support line, so camera roll cancels out.
    private fun plankMetrics(pose: ExercisePose): FloorMetrics {
        val ux = pose.ankle.x.toDouble() - pose.elbow.x
        val uy = pose.ankle.y.toDouble() - pose.elbow.y
        val length = hypot(ux, uy)
        var nx = 0.0
        var ny = -1.0
        if (length >= MIN_SEGMENT_PX) {
            nx = uy / length
            ny = -ux / length
            if (ny > 0.0) {
                nx = -nx
                ny = -ny
            }
        }
        fun above(p: BodyPoint) = (p.x.toDouble() - pose.elbow.x) * nx + (p.y.toDouble() - pose.elbow.y) * ny
        val shoulderHeight = above(pose.shoulder)
        val height = shoulderHeight / distance(pose.shoulder, pose.elbow)
        return FloorMetrics(
            tilt = floorTilt(pose.shoulder, pose.ankle, pose.world?.shoulder, pose.world?.ankle),
            height = height,
            armAngle = Math.toDegrees(asin(height.coerceIn(-1.0, 1.0))),
            hipHeight = if (shoulderHeight > 0.0) above(pose.hip) / shoulderHeight else null
        )
    }

    // Angle of a→b against the floor; world points remove the foreshortening of non-side views.
    private fun floorTilt(a: BodyPoint, b: BodyPoint, wa: WorldPoint?, wb: WorldPoint?): Double {
        if (view != CameraView.SIDE && wa != null && wb != null) {
            val run = hypot(wb.x.toDouble() - wa.x, wb.z.toDouble() - wa.z)
            return Math.toDegrees(atan2(abs(wb.y.toDouble() - wa.y), run))
        }
        return Math.toDegrees(atan2(abs(b.y.toDouble() - a.y), abs(b.x.toDouble() - a.x)))
    }

    private fun plankFloorIssue(m: FloorMetrics): String? {
        val hip = m.hipHeight
        return when {
            m.tilt > PLANK_MAX_TILT -> "Hạ thân gần song song với sàn, chỉ nghiêng nhẹ."
            m.armAngle < PLANK_MIN_ARM -> "Đặt khuỷu tay dưới vai, gập tay gần vuông góc."
            hip == null || hip < PLANK_HIP_LOW -> "Nâng hông lên, đừng để thân võng xuống sàn."
            hip > PLANK_HIP_HIGH -> "Hạ hông xuống, giữ thân song song với sàn."
            else -> null
        }
    }

    private fun length3(a: WorldPoint, b: WorldPoint): Double {
        val dx = a.x.toDouble() - b.x
        val dy = a.y.toDouble() - b.y
        val dz = a.z.toDouble() - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
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

    private fun angle3(a: WorldPoint, joint: WorldPoint, b: WorldPoint): Double {
        val ax = a.x.toDouble() - joint.x
        val ay = a.y.toDouble() - joint.y
        val az = a.z.toDouble() - joint.z
        val bx = b.x.toDouble() - joint.x
        val by = b.y.toDouble() - joint.y
        val bz = b.z.toDouble() - joint.z
        val la = sqrt(ax * ax + ay * ay + az * az)
        val lb = sqrt(bx * bx + by * by + bz * bz)
        if (la < 1e-6 || lb < 1e-6) return 180.0
        val cosine = (ax * bx + ay * by + az * bz) / (la * lb)
        return acos(cosine.coerceIn(-1.0, 1.0)) * 180.0 / PI
    }

    private companion object {
        const val MIN_CONFIDENCE = 0.5f
        const val MIN_SEGMENT_PX = 8.0
        const val STRAIGHT_ANGLE = 135.0
        const val DEPARTURE_MARGIN = 5.0
        const val STABLE_MS = 120L
        const val MIN_CYCLE_MS = 800L
        const val MAX_CYCLE_MS = 10_000L
        const val MAX_INTERVAL_MS = 500L
        const val VIEW_HOLD_MS = 600L
        const val PLANK_BLOCK_MS = 10_000L
        const val PUSH_UP_PER_POINT = 3
        const val SQUAT_PER_POINT = 5
        // Push-up valley detector: depth, swing below the recent peak, confirmation rise,
        // re-arm rise above the counted valley, and refractory gap between credited reps.
        const val PUSH_WORLD_BOTTOM = 100.0
        // 2D side-view valleys often stay above 100°, so the image gate is looser.
        const val PUSH_IMAGE_BOTTOM = 125.0
        const val PUSH_AMP = 30.0
        const val PUSH_CONFIRM = 10.0
        const val PUSH_REARM = 20.0
        const val PUSH_MIN_REP_MS = 300L
        // Floor gates at the valley: the shoulders must sink towards the floor by a share of the
        // whole arm (arm-landmark glitches bend with level shoulders; a 125° elbow sinks ~0.11),
        // with the torso only tilted. Real fixture reps sink ≥0.31 and tilt ≤21°.
        const val PUSH_DROP = 0.08
        const val PUSH_MAX_TILT = 35.0
        const val PUSH_REJECTION_MS = 1500L
        const val PLANK_MAX_TILT = 25.0
        const val PLANK_MIN_ARM = 40.0
        const val PLANK_HIP_LOW = 0.3
        const val PLANK_HIP_HIGH = 1.6
        const val FRONT_RATIO = 0.6
        const val FRONT_RATIO_HORIZONTAL = 0.85
        const val SIDE_RATIO = 0.3
        const val LEVEL_TOL = 0.25
    }
}