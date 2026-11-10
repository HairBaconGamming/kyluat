package com.cainghien.companion

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Size
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlin.math.min

class ExerciseActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private lateinit var quota: QuotaManager
    private lateinit var preview: PreviewView
    private lateinit var skeleton: ExerciseSkeleton
    private lateinit var count: TextView
    private lateinit var points: TextView
    private lateinit var feedback: TextView
    private lateinit var guide: TextView
    private lateinit var startButton: UiButton
    private lateinit var lensButton: UiButton
    private var counter = ExerciseCounter(ExerciseKind.PUSH_UP)
    private var creditedPoints = 0
    private var frontCamera = true
    private var permissionDenied = false
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var camera: Camera? = null
    private var trackedSide: Int? = null
    private var session: CameraSession? = null
    private var cameraRotation = Surface.ROTATION_0
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            if (session == null) return
            val display = preview.display ?: return
            if (display.displayId == displayId && display.rotation != cameraRotation) {
                cameraError("Đã xoay máy. Đặt máy cố định rồi bấm Bắt đầu để tiếp tục.")
            }
        }
    }

    private class CameraSession(val detector: PoseDetector) {
        var closed = false
        var processing = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        quota = QuotaManager(prefs)
        Ui.chrome(this)
        setContentView(Ui.scroll(buildPage()))
        updateGuide()
        render()
    }

    private fun buildPage(): LinearLayout {
        val page = Ui.screen(this)
        page.addView(Ui.backHeader(this, "Tập với camera", "Đếm trên máy · không lưu hình ảnh") { finish() })
        page.addView(Ui.segmented(this, listOf("Chống đẩy", "Squat", "Plank"), 0) { index ->
            stopCamera()
            counter = ExerciseCounter(ExerciseKind.values()[index])
            creditedPoints = 0
            updateGuide()
            render()
            feedback.text = "Đã đổi bài. Bấm Bắt đầu khi sẵn sàng."
        }, Ui.match(this, topMarginDp = 8))

        val cameraBox = FrameLayout(this)
        cameraBox.background = Ui.rounded(Ui.SURFACE, 16, this, Ui.LINE)
        cameraBox.clipToOutline = true
        preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        cameraBox.addView(preview, FrameLayout.LayoutParams(-1, -1))
        skeleton = ExerciseSkeleton(this)
        cameraBox.addView(skeleton, FrameLayout.LayoutParams(-1, -1))
        page.addView(cameraBox, LinearLayout.LayoutParams(-1, Ui.dp(this, 300)).apply {
            topMargin = Ui.dp(this@ExerciseActivity, 14)
        })

        val result = Ui.hero(this)
        count = Ui.label(this, "0 cái", 34f, Ui.TEXT, true).apply { gravity = Gravity.CENTER }
        points = Ui.label(this, "0 điểm học", 15f, Ui.ACCENT, true).apply { gravity = Gravity.CENTER }
        feedback = Ui.label(this, "Bấm Bắt đầu để mở camera.", 14f, Ui.TEXT_2).apply {
            gravity = Gravity.CENTER
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        result.addView(count)
        result.addView(points, Ui.match(this, topMarginDp = 5))
        result.addView(feedback, Ui.match(this, topMarginDp = 8))
        page.addView(result, Ui.match(this, topMarginDp = 12))

        startButton = Ui.button(this, "Bắt đầu", Variant.PRIMARY, big = true)
        startButton.setOnClickListener {
            if (session != null) {
                stopCamera()
                feedback.text = "Đã dừng camera. Điểm đã nhận được giữ lại."
                render()
            } else {
                requestStart()
            }
        }
        page.addView(startButton, Ui.match(this))
        lensButton = Ui.button(this, "Dùng camera sau", Variant.GHOST)
        lensButton.setOnClickListener {
            stopCamera()
            frontCamera = !frontCamera
            lensButton.text = if (frontCamera) "Dùng camera sau" else "Dùng camera trước"
            feedback.text = "Đã đổi camera. Bấm Bắt đầu để tiếp tục."
        }
        page.addView(lensButton, Ui.match(this, topMarginDp = 6))
        guide = Ui.label(this, "", 13.5f, Ui.TEXT_2)
        page.addView(guide, Ui.match(this, topMarginDp = 12))
        page.addView(Ui.note(this,
            "Camera chỉ bật khi bạn bấm Bắt đầu. Ảnh được xử lý ngay trên máy, không lưu hoặc gửi đi. " +
                "Rời màn tập hoặc bấm Dừng sẽ tắt camera; phần động tác chưa hoàn thành không có điểm. " +
                "Google ML Kit gửi số liệu sử dụng, hiệu năng SDK và thông tin kỹ thuật thiết bị/app tới Google, không gửi ảnh hay kết quả nhận diện. " +
                "Nhận diện có thể sai khi thiếu sáng hoặc khuất người. Dừng tập nếu đau hoặc chóng mặt.",
            R.drawable.ic_info,
        ))
        return page
    }

    private fun updateGuide() {
        guide.text = when (counter.kind) {
            ExerciseKind.PUSH_UP -> "1 cái = 1 điểm. Đặt điện thoại cố định nhìn ngang bên hông, " +
                "thấy rõ vai, tay, hông và chân. Bắt đầu với tay thẳng, hạ người rồi đẩy lên đủ một nhịp."
            ExerciseKind.SQUAT -> "Mỗi 2 cái = 2 điểm. Đặt camera nhìn ngang toàn thân. " +
                "Đứng thẳng, hạ gối rồi đứng lên; một lần lên xuống hoàn chỉnh mới được đếm."
            ExerciseKind.PLANK -> "Mỗi 5 giây = 1 điểm. Plank (đo sàn) trên cẳng tay, giữ lưng và chân thẳng. " +
                "Đặt camera nhìn ngang toàn thân. Mất tư thế sẽ bỏ phần chưa đủ 5 giây, không trừ điểm đã nhận."
        }
    }

    private fun requestStart() {
        if (!prefs.protectionEnabled) {
            feedback.text = "Bật Bảo vệ trong Kỷ luật để nhận điểm học khi tập."
            return
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (permissionDenied && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            } else {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION)
            }
            return
        }
        startCamera()
    }

    private fun startCamera() {
        if (session != null || isFinishing || isDestroyed) return
        try {
            val options = PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build()
            val active = CameraSession(PoseDetection.getClient(options))
            session = active
            cameraRotation = preview.display?.rotation ?: Surface.ROTATION_0
            counter.pause()
            preview.visibility = View.VISIBLE
            startButton.text = "Dừng"
            feedback.text = "Đang mở camera…"
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val future = ProcessCameraProvider.getInstance(this)
            val main = ContextCompat.getMainExecutor(this)
            future.addListener({
                if (active.closed || session !== active) return@addListener
                try {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    val selector = if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    if (!cameraProvider.hasCamera(selector)) {
                        cameraError("Máy không có camera này. Hãy đổi camera rồi thử lại.")
                        return@addListener
                    }
                    val cameraPreview = Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .setTargetRotation(cameraRotation)
                        .build()
                    cameraPreview.setSurfaceProvider(preview.surfaceProvider)
                    val analyzer = ImageAnalysis.Builder()
                        .setTargetResolution(Size(640, 480))
                        .setTargetRotation(cameraRotation)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis = analyzer
                    analyzer.setAnalyzer(main) { frame -> analyze(active, frame) }
                    val boundCamera = cameraProvider.bindToLifecycle(this, selector, cameraPreview, analyzer)
                    camera = boundCamera
                    boundCamera.cameraInfo.cameraState.observe(this) { state ->
                        if (session === active && state.error != null) {
                            cameraError("Camera đang bận hoặc bị gián đoạn. Đóng app khác dùng camera rồi thử lại.")
                        }
                    }
                    feedback.text = "Đưa toàn thân vào khung hình, đặt máy nhìn ngang."
                } catch (error: Exception) {
                    cameraError("Không mở được camera. Bạn vẫn dùng được các tính năng khác; hãy thử lại.")
                }
            }, main)
        } catch (error: Exception) {
            cameraError("Không khởi động được bộ nhận diện. Bấm Bắt đầu để thử lại.")
        }
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyze(active: CameraSession, frame: ImageProxy) {
        if (active.closed || active.processing || session !== active) {
            frame.close()
            return
        }
        if (!prefs.protectionEnabled) {
            frame.close()
            cameraError("Bảo vệ đã tắt. Đã dừng camera và ngừng cộng điểm.")
            return
        }
        val image = frame.image
        if (image == null) {
            frame.close()
            counter.pause()
            return
        }
        val capturedAt = SystemClock.elapsedRealtime()
        val rotation = frame.imageInfo.rotationDegrees
        val rotated = rotation == 90 || rotation == 270
        val width = if (rotated) frame.height else frame.width
        val height = if (rotated) frame.width else frame.height
        active.processing = true
        try {
            active.detector.process(InputImage.fromMediaImage(image, rotation))
                .addOnSuccessListener { pose ->
                    if (!active.closed && session === active && prefs.protectionEnabled) {
                        val body = bodyPose(pose, width, height)
                        counter.update(body, capturedAt)
                        val delta = counter.points - creditedPoints
                        if (delta > 0) {
                            quota.awardSession(delta)
                            creditedPoints = counter.points
                        }
                        skeleton.show(body, width, height, frontCamera)
                        feedback.text = counter.feedback
                        render()
                    }
                }
                .addOnFailureListener {
                    if (!active.closed && session === active) cameraError("Nhận diện bị gián đoạn. Đã dừng camera; bấm Bắt đầu để thử lại.")
                }
                .addOnCompleteListener {
                    frame.close()
                    active.processing = false
                    if (active.closed) active.detector.close()
                }
        } catch (error: Exception) {
            frame.close()
            active.processing = false
            cameraError("Không xử lý được hình ảnh. Đã dừng camera; hãy thử lại.")
        }
    }

    private fun bodyPose(pose: Pose, width: Int, height: Int): ExercisePose? {
        val sides = listOf(
            intArrayOf(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST,
                PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE),
            intArrayOf(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST,
                PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE),
        )
        val candidates = sides.mapIndexedNotNull { side, ids ->
            val landmarks = ids.map { pose.getPoseLandmark(it) ?: return@mapIndexedNotNull null }
            val p = landmarks.map {
                val position = it.position
                if (position.x !in 0f..width.toFloat() || position.y !in 0f..height.toFloat()) return@mapIndexedNotNull null
                BodyPoint(position.x, position.y, it.inFrameLikelihood)
            }
            ExercisePose(p[0], p[1], p[2], p[3], p[4], p[5], side)
        }
        fun confidence(body: ExercisePose) = minOf(body.shoulder.confidence, body.elbow.confidence,
            body.wrist.confidence, body.hip.confidence, body.knee.confidence, body.ankle.confidence)
        val selected = candidates.firstOrNull { it.side == trackedSide && confidence(it) >= .7f }
            ?: candidates.maxByOrNull { confidence(it) }
        trackedSide = selected?.side
        return selected
    }

    private fun render() {
        count.text = if (counter.kind == ExerciseKind.PLANK) "${counter.plankMillis / 1000} giây" else "${counter.reps} cái"
        quota.resetIfNeeded()
        points.text = "+${counter.points} điểm bài này · hôm nay ${quota.pointsToday()} điểm"
    }

    private fun cameraError(message: String) {
        stopCamera()
        feedback.text = message
        render()
    }

    private fun stopCamera() {
        val active = session
        session = null
        if (active != null) {
            active.closed = true
            if (!active.processing) active.detector.close()
        }
        analysis?.clearAnalyzer()
        analysis = null
        camera?.cameraInfo?.cameraState?.removeObservers(this)
        camera = null
        provider?.unbindAll()
        trackedSide = null
        counter.pause()
        skeleton.show(null, 1, 1, false)
        preview.visibility = View.INVISIBLE
        startButton.text = "Bắt đầu"
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && session != null) {
            cameraError("Màn tập không còn được chọn. Đã dừng camera; bấm Bắt đầu để tiếp tục.")
        }
    }

    override fun onPause() {
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        stopCamera()
        feedback.text = "Đã dừng camera. Bấm Bắt đầu để tiếp tục."
        render()
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != CAMERA_PERMISSION) return
        permissionDenied = grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED
        feedback.text = if (permissionDenied) "Chưa cấp quyền camera. Không thể tự đếm; các tính năng khác vẫn hoạt động."
        else "Đã cấp quyền camera. Bấm Bắt đầu khi sẵn sàng."
    }

    private class ExerciseSkeleton(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Ui.ACCENT
            strokeWidth = Ui.dp(context, 3).toFloat()
            strokeCap = Paint.Cap.ROUND
        }
        private var pose: ExercisePose? = null
        private var imageWidth = 1
        private var imageHeight = 1
        private var mirrored = false

        fun show(pose: ExercisePose?, width: Int, height: Int, mirrored: Boolean) {
            this.pose = pose
            imageWidth = width
            imageHeight = height
            this.mirrored = mirrored
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val p = pose ?: return
            val scale = min(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
            val dx = (width - imageWidth * scale) / 2
            val dy = (height - imageHeight * scale) / 2
            fun x(point: BodyPoint) = dx + (if (mirrored) imageWidth - point.x else point.x) * scale
            fun y(point: BodyPoint) = dy + point.y * scale
            val lines = listOf(p.wrist to p.elbow, p.elbow to p.shoulder, p.shoulder to p.hip,
                p.hip to p.knee, p.knee to p.ankle)
            for ((a, b) in lines) {
                if (a.confidence >= .7f && b.confidence >= .7f) canvas.drawLine(x(a), y(a), x(b), y(b), paint)
            }
        }
    }

    companion object {
        private const val CAMERA_PERMISSION = 47
    }
}
