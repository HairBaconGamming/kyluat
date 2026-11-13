package com.cainghien.companion

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
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
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class ExerciseActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private lateinit var quota: QuotaManager
    private lateinit var voice: VoiceCoach
    private lateinit var preview: PreviewView
    private lateinit var skeleton: ExerciseSkeleton
    private lateinit var title: TextView
    private lateinit var viewBadge: TextView
    private lateinit var soundIcon: ImageView
    private lateinit var countNum: TextView
    private lateinit var countUnit: TextView
    private lateinit var points: TextView
    private lateinit var feedback: TextView
    private lateinit var startButton: TextView
    private lateinit var bottomPanel: LinearLayout
    private lateinit var sessionBar: LinearLayout
    private lateinit var slimCount: TextView
    private lateinit var slimPoints: TextView
    private lateinit var collapseLink: TextView
    private var placementCue: String? = null
    private lateinit var segViews: List<TextView>
    private var counter = ExerciseCounter(ExerciseKind.PUSH_UP)
    private val stabilizer = PoseStabilizer()
    private var creditedPoints = 0
    private var lastCorrection: String? = null
    private var frontCamera = true
    private var permissionDenied = false
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var camera: Camera? = null
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

    private class CameraSession(val landmarker: PoseLandmarker) {
        var closed = false
        var processing = false
        var layout: PreviewLayout? = null
    }

    /**
     * Snapshot of everything needed to place a landmark on the overlay: the upright analyzer
     * bitmap size, whether the preview is mirrored, and the overlay size.
     */
    private data class PreviewLayout(
        val imageWidth: Int,
        val imageHeight: Int,
        val mirrored: Boolean,
        val viewWidth: Int,
        val viewHeight: Int
    ) {
        val content: ViewMapping.ContentRect
            get() = ViewMapping.contentRect(imageWidth, imageHeight, viewWidth, viewHeight)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        quota = QuotaManager(prefs)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        voice = VoiceCoach(this) { available ->
            if (!available && prefs.exerciseVoice) {
                Toast.makeText(this, "Máy chưa có giọng tiếng Việt. Chữ hướng dẫn vẫn hiện trên màn hình.", Toast.LENGTH_LONG).show()
            }
            renderSound()
        }
        voice.enabled = prefs.exerciseVoice
        setContentView(buildRoot())
        selectSeg(0, notify = false)
        render()
    }

    private fun buildRoot(): FrameLayout {
        val root = FrameLayout(this)
        root.setBackgroundColor(BG)
        root.fitsSystemWindows = true
        preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))
        skeleton = ExerciseSkeleton(this)
        root.addView(skeleton, FrameLayout.LayoutParams(-1, -1))
        root.addView(buildTopBar(), FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        feedback = TextView(this).apply {
            text = "Bấm Bắt đầu để mở camera."
            textSize = 12.5f
            setTextColor(AMBER)
            gravity = Gravity.CENTER
            setPadding(Ui.dp(this@ExerciseActivity, 14), Ui.dp(this@ExerciseActivity, 9),
                Ui.dp(this@ExerciseActivity, 14), Ui.dp(this@ExerciseActivity, 9))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        root.addView(feedback, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = Ui.dp(this@ExerciseActivity, 78)
            leftMargin = Ui.dp(this@ExerciseActivity, 20)
            rightMargin = Ui.dp(this@ExerciseActivity, 20)
        })
        root.addView(buildBottomPanel(), FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            leftMargin = Ui.dp(this@ExerciseActivity, 10)
            rightMargin = Ui.dp(this@ExerciseActivity, 10)
            bottomMargin = Ui.dp(this@ExerciseActivity, 10)
        })
        root.addView(buildSessionBar(), FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            leftMargin = Ui.dp(this@ExerciseActivity, 10)
            rightMargin = Ui.dp(this@ExerciseActivity, 10)
            bottomMargin = Ui.dp(this@ExerciseActivity, 10)
        })
        return root
    }

    private fun buildSessionBar(): LinearLayout {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.background = Ui.rounded(PANEL, 14, this, PANEL_LINE)
        bar.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10))
        bar.visibility = View.GONE
        slimCount = Ui.label(this, "0 cái", 20f, Color.WHITE, true)
        bar.addView(slimCount)
        slimPoints = Ui.label(this, "+0 điểm · hôm nay 0 điểm", 11.5f, GREEN_TEXT, true)
        slimPoints.setPadding(Ui.dp(this, 10), 0, 0, 0)
        bar.addView(slimPoints, LinearLayout.LayoutParams(0, -2, 1f))
        val stop = Ui.label(this, "Dừng", 13.5f, Color.WHITE, true)
        stop.gravity = Gravity.CENTER
        stop.background = Ui.ripple(Ui.rounded(GREEN, 10, this), 0x33FFFFFF)
        stop.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8))
        stop.isClickable = true
        stop.setOnClickListener {
            stopCamera()
            feedback.text = "Đã dừng camera. Điểm đã nhận được giữ lại."
            render()
        }
        bar.addView(stop)
        bar.isClickable = true
        bar.setOnClickListener { setCollapsed(false) }
        sessionBar = bar
        return bar
    }

    private fun setCollapsed(collapsed: Boolean) {
        bottomPanel.visibility = if (collapsed) View.GONE else View.VISIBLE
        sessionBar.visibility = if (collapsed) View.VISIBLE else View.GONE
        collapseLink.visibility = if (!collapsed && session != null) View.VISIBLE else View.GONE
    }

    private fun circleIcon(res: Int, onClick: () -> Unit): FrameLayout {
        val box = FrameLayout(this)
        box.background = Ui.ripple(Ui.rounded(Color.parseColor("#26FFFFFF"), 999, this, LINE), 0x33FFFFFF)
        box.isClickable = true
        box.setOnClickListener { onClick() }
        val icon = Ui.icon(this, res, Color.WHITE, 17)
        box.addView(icon, FrameLayout.LayoutParams(Ui.dp(this, 17), Ui.dp(this, 17), Gravity.CENTER))
        box.tag = icon
        return box
    }

    private fun buildTopBar(): LinearLayout {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), 0)
        bar.addView(circleIcon(R.drawable.ic_chevron_left) { finish() },
            LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)))
        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        titles.setPadding(Ui.dp(this, 11), 0, Ui.dp(this, 8), 0)
        title = Ui.label(this, "Chống đẩy", 17f, Color.WHITE, true)
        title.setShadowLayer(6f, 0f, Ui.dp(this, 1).toFloat(), 0xB3000000.toInt())
        titles.addView(title)
        viewBadge = Ui.label(this, "Góc nhìn: bên hông", 10.5f, Color.parseColor("#E8ECEF"), true)
        viewBadge.background = Ui.rounded(Color.parseColor("#73000000"), 999, this, LINE)
        viewBadge.setPadding(Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 8), Ui.dp(this, 2))
        titles.addView(viewBadge, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Ui.dp(this@ExerciseActivity, 3) })
        bar.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        val sound = circleIcon(R.drawable.ic_volume_on) { toggleVoice() }
        soundIcon = sound.tag as ImageView
        bar.addView(sound, LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)))
        bar.addView(circleIcon(R.drawable.ic_camera_flip) { flipCamera() },
            LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)).apply {
                leftMargin = Ui.dp(this@ExerciseActivity, 8)
            })
        return bar
    }

    private fun buildBottomPanel(): LinearLayout {
        val panel = LinearLayout(this)
        bottomPanel = panel
        panel.orientation = LinearLayout.VERTICAL
        panel.background = Ui.rounded(PANEL, 18, this, PANEL_LINE)
        panel.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 12))
        collapseLink = Ui.label(this, "Thu gọn bảng", 11f, DIM)
        collapseLink.gravity = Gravity.END
        collapseLink.visibility = View.GONE
        collapseLink.isClickable = true
        collapseLink.setOnClickListener { setCollapsed(true) }
        panel.addView(collapseLink, Ui.match(this))
        val countRow = LinearLayout(this)
        countRow.orientation = LinearLayout.HORIZONTAL
        countRow.gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
        countNum = Ui.label(this, "0", 40f, Color.WHITE, true)
        countUnit = Ui.label(this, "cái", 15f, DIM, true)
        countRow.addView(countNum)
        countRow.addView(countUnit, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = Ui.dp(this@ExerciseActivity, 6) })
        panel.addView(countRow, LinearLayout.LayoutParams(-1, -2))
        points = Ui.label(this, "+0 điểm bài này · hôm nay 0 điểm", 12.5f, GREEN_TEXT, true)
        points.gravity = Gravity.CENTER
        panel.addView(points, Ui.match(this, topMarginDp = 4))
        val seg = LinearLayout(this)
        seg.orientation = LinearLayout.HORIZONTAL
        seg.background = Ui.rounded(Color.parseColor("#1AFFFFFF"), 11, this)
        seg.setPadding(Ui.dp(this, 3), Ui.dp(this, 3), Ui.dp(this, 3), Ui.dp(this, 3))
        segViews = listOf("Chống đẩy", "Squat", "Plank").mapIndexed { index, label ->
            val cell = Ui.label(this, label, 12.5f, DIM, true)
            cell.gravity = Gravity.CENTER
            cell.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8))
            cell.isClickable = true
            cell.setOnClickListener { selectSeg(index, notify = true) }
            seg.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
            cell
        }
        panel.addView(seg, Ui.match(this, topMarginDp = 12))
        startButton = Ui.label(this, "Bắt đầu", 15.5f, Color.WHITE, true)
        startButton.gravity = Gravity.CENTER
        startButton.background = Ui.ripple(Ui.rounded(GREEN, 13, this), 0x33FFFFFF)
        startButton.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14))
        startButton.isClickable = true
        startButton.setOnClickListener {
            if (session != null) {
                stopCamera()
                feedback.text = "Đã dừng camera. Điểm đã nhận được giữ lại."
                render()
            } else {
                requestStart()
            }
        }
        panel.addView(startButton, Ui.match(this, topMarginDp = 10))
        val privacy = Ui.label(this, "Quyền riêng tư & lưu ý an toàn", 11f, DIM)
        privacy.gravity = Gravity.CENTER
        privacy.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_info, 0, 0, 0)
        privacy.compoundDrawableTintList = ColorStateList.valueOf(DIM)
        privacy.compoundDrawablePadding = Ui.dp(this, 5)
        privacy.isClickable = true
        privacy.setOnClickListener { showPrivacy() }
        panel.addView(privacy, Ui.match(this, topMarginDp = 9))
        return panel
    }

    private fun selectSeg(index: Int, notify: Boolean) {
        segViews.forEachIndexed { i, cell ->
            if (i == index) { cell.background = Ui.rounded(GREEN, 8, this); cell.setTextColor(Color.WHITE) }
            else { cell.background = null; cell.setTextColor(DIM) }
        }
        if (!notify) return
        stopCamera()
        counter = ExerciseCounter(ExerciseKind.values()[index])
        stabilizer.reset()
        creditedPoints = 0
        lastCorrection = null
        title.text = segViews[index].text
        feedback.text = "Đã đổi bài. Bấm Bắt đầu khi sẵn sàng."
        render()
    }

    private fun toggleVoice() {
        if (!prefs.exerciseVoice && !voice.available) {
            AlertDialog.Builder(this)
                .setTitle("Chưa có giọng tiếng Việt")
                .setMessage("Máy chưa cài giọng đọc tiếng Việt. Bạn có thể cài giọng (tự chọn, không tự tải) hoặc tiếp tục chỉ với chữ trên màn hình.")
                .setPositiveButton("Cài giọng") { _, _ ->
                    try { startActivity(VoiceCoach.installVoiceIntent()) }
                    catch (error: Exception) { Toast.makeText(this, "Không mở được cài đặt giọng đọc.", Toast.LENGTH_SHORT).show() }
                }
                .setNegativeButton("Chỉ dùng chữ", null)
                .show()
            return
        }
        prefs.exerciseVoice = !prefs.exerciseVoice
        voice.enabled = prefs.exerciseVoice
        if (!prefs.exerciseVoice) voice.reset()
        renderSound()
    }

    private fun renderSound() {
        soundIcon.setImageResource(if (prefs.exerciseVoice) R.drawable.ic_volume_on else R.drawable.ic_volume_off)
        soundIcon.alpha = if (prefs.exerciseVoice && voice.available) 1f else 0.55f
    }

    private fun flipCamera() {
        stopCamera()
        frontCamera = !frontCamera
        feedback.text = "Đã đổi camera. Bấm Bắt đầu để tiếp tục."
        render()
    }

    private fun showPrivacy() {
        AlertDialog.Builder(this)
            .setTitle("Quyền riêng tư & an toàn")
            .setMessage(
                "Camera chỉ bật khi bạn bấm Bắt đầu. Ảnh được xử lý ngay trên máy, không lưu hoặc gửi đi. " +
                    "Rời màn tập hoặc bấm Dừng sẽ tắt camera; phần động tác chưa hoàn thành không có điểm. " +
                    "Bộ nhận diện MediaPipe Pose Landmarker (Apache-2.0) chạy hoàn toàn trên máy, không gửi ảnh hay số liệu đi đâu. " +
                    "Giọng nói dùng TTS có sẵn của máy, không gửi âm thanh đi. " +
                    "Nhận diện có thể sai khi thiếu sáng hoặc khuất người. Dừng tập nếu đau hoặc chóng mặt."
            )
            .setPositiveButton("Đã hiểu", null)
            .show()
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
            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("pose_landmarker_full.task").build())
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, image ->
                    val current = session
                    if (current != null) onPose(current, result, image)
                }
                .setErrorListener {
                    runOnUiThread {
                        if (session != null) cameraError("Nhận diện bị gián đoạn. Đã dừng camera; bấm Bắt đầu để thử lại.")
                    }
                }
                .build()
            val active = CameraSession(PoseLandmarker.createFromOptions(this, options))
            session = active
            cameraRotation = preview.display?.rotation ?: Surface.ROTATION_0
            counter.pause()
            lastCorrection = null
            placementCue = null
            setCollapsed(true)
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
                        .setTargetResolution(Size(960, 720))
                        .setTargetRotation(cameraRotation)
                        .setOutputImageRotationEnabled(true)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis = analyzer
                    analyzer.setAnalyzer(main) { frame -> analyze(active, frame) }
                    val viewPort = preview.viewPort
                    if (viewPort == null) {
                        cameraError("Không mở được camera. Bạn vẫn dùng được các tính năng khác; hãy thử lại.")
                        return@addListener
                    }
                    val group = UseCaseGroup.Builder()
                        .setViewPort(viewPort)
                        .addUseCase(cameraPreview)
                        .addUseCase(analyzer)
                        .build()
                    val boundCamera = cameraProvider.bindToLifecycle(this, selector, group)
                    camera = boundCamera
                    boundCamera.cameraInfo.cameraState.observe(this) { state ->
                        if (session === active && state.error != null) {
                            cameraError("Camera đang bận hoặc bị gián đoạn. Đóng app khác dùng camera rồi thử lại.")
                        }
                    }
                    feedback.text = "Đưa toàn thân vào khung hình; app tự nhận góc nhìn."
                } catch (error: Exception) {
                    cameraError("Không mở được camera. Bạn vẫn dùng được các tính năng khác; hãy thử lại.")
                }
            }, main)
        } catch (error: Exception) {
            cameraError("Không khởi động được bộ nhận diện. Bấm Bắt đầu để thử lại.")
        }
    }

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
        val bitmap = try {
            frame.toBitmap()
        } catch (error: Exception) {
            null
        }
        frame.close()
        if (bitmap == null) {
            counter.pause()
            return
        }
        // toBitmap() is already upright and cropped to the viewport, so the overlay only needs
        // FILL_CENTER scaling plus the front-preview mirror; no rotation term.
        active.layout = PreviewLayout(
            bitmap.width,
            bitmap.height,
            frontCamera,
            skeleton.width,
            skeleton.height
        )
        active.processing = true
        try {
            active.landmarker.detectAsync(BitmapImageBuilder(bitmap).build(), SystemClock.elapsedRealtime())
        } catch (error: Exception) {
            active.processing = false
            cameraError("Không xử lý được hình ảnh. Đã dừng camera; hãy thử lại.")
        }
    }

    private fun onPose(active: CameraSession, result: PoseLandmarkerResult, image: MPImage) {
        runOnUiThread {
            image.close()
            active.processing = false
            if (active.closed || session !== active) {
                if (active.closed) active.landmarker.close()
                return@runOnUiThread
            }
            if (!prefs.protectionEnabled) {
                cameraError("Bảo vệ đã tắt. Đã dừng camera và ngừng cộng điểm.")
                return@runOnUiThread
            }
            val now = result.timestampMs()
            stabilizer.upperBody = counter.kind == ExerciseKind.PUSH_UP
            val body = stabilizer.update(bodyCandidates(result, active), now)
            // Candidates are already in view space; no second projection.
            counter.update(body, now)
            val delta = counter.points - creditedPoints
            if (delta > 0) {
                quota.awardSession(delta)
                creditedPoints = counter.points
            }
            skeleton.show(body)
            val visibleBottom = (if (sessionBar.visibility == View.VISIBLE) sessionBar.top
                else bottomPanel.top).toFloat()
            placementCue = Framing.feetCue(body?.ankle?.y, visibleBottom)
            speakCorrection(placementCue ?: counter.correction)
            feedback.text = placementCue ?: counter.feedback
            render()
            if (active.closed) active.landmarker.close()
        }
    }

    private fun speakCorrection(correction: String?) {
        if (correction == null) { lastCorrection = null; return }
        if (correction != lastCorrection) { lastCorrection = correction; voice.speakCorrection(correction) }
    }

    private fun bodyCandidates(result: PoseLandmarkerResult, active: CameraSession): List<ExercisePose> {
        val landmarks = result.landmarks().firstOrNull() ?: return emptyList()
        val world = result.worldLandmarks().firstOrNull()
        val layout = active.layout ?: return emptyList()
        val sides = listOf(
            intArrayOf(11, 13, 15, 23, 25, 27),
            intArrayOf(12, 14, 16, 24, 26, 28),
        )
        val otherIds = listOf(intArrayOf(12, 24), intArrayOf(11, 23))
        // Landmarks are projected into overlay pixels once per frame; working in view pixels
        // the whole way down keeps counter geometry and drawing consistent under preview crop.
        fun read(index: Int): BodyPoint? {
            val landmark = landmarks.getOrNull(index) ?: return null
            val nx = landmark.x()
            val ny = landmark.y()
            if (nx !in 0f..1f || ny !in 0f..1f) return null
            val (x, y) = ViewMapping.toView(nx, ny, layout.content, layout.mirrored)
            return BodyPoint(x, y, landmark.visibility().orElse(0f))
        }
        fun readWorld(index: Int): WorldPoint? {
            val landmark = world?.getOrNull(index) ?: return null
            val visibility = landmark.visibility().orElse(0f)
            if (visibility < 0.5f) return null
            return WorldPoint(landmark.x(), landmark.y(), landmark.z(), visibility)
        }
        return sides.mapIndexedNotNull { side, ids ->
            val shoulder = read(ids[0]) ?: return@mapIndexedNotNull null
            val hip = read(ids[3]) ?: return@mapIndexedNotNull null
            // Missing joints carry confidence 0 so gates and drawing skip them. Push-up can
            // count from the upper body alone, so legs out of frame must not drop the side.
            val elbow = read(ids[1]) ?: BodyPoint(shoulder.x, shoulder.y, 0f)
            val wrist = read(ids[2]) ?: BodyPoint(shoulder.x, shoulder.y, 0f)
            val knee = read(ids[4]) ?: BodyPoint(hip.x, hip.y, 0f)
            val ankle = read(ids[5]) ?: BodyPoint(hip.x, hip.y, 0f)
            val chain = if (world != null) {
                val ws = readWorld(ids[0])
                val we = readWorld(ids[1])
                val ww = readWorld(ids[2])
                val wh = readWorld(ids[3])
                if (ws != null && we != null && ww != null && wh != null) {
                    val wk = readWorld(ids[4]) ?: WorldPoint(0f, 0f, 0f, 0f)
                    val wa = readWorld(ids[5]) ?: WorldPoint(0f, 0f, 0f, 0f)
                    WorldChain(ws, we, ww, wh, wk, wa)
                } else null
            } else null
            ExercisePose(shoulder, elbow, wrist, hip, knee, ankle, side,
                otherShoulder = read(otherIds[side][0]), otherHip = read(otherIds[side][1]), world = chain)
        }
    }

    private fun render() {
        countNum.text = if (counter.kind == ExerciseKind.PLANK) "${counter.plankMillis / 1000}" else "${counter.reps}"
        countUnit.text = if (counter.kind == ExerciseKind.PLANK) "giây" else "cái"
        quota.resetIfNeeded()
        points.text = "+${counter.points} điểm bài này · hôm nay ${quota.pointsToday()} điểm"
        slimCount.text = "${countNum.text} ${countUnit.text}"
        slimPoints.text = "+${counter.points} điểm · hôm nay ${quota.pointsToday()} điểm"
        viewBadge.text = "Góc nhìn: ${counter.viewLabel.lowercase()}"
        val tone = when {
            placementCue != null || counter.correction != null -> AMBER to AMBER_BG
            session != null -> GREEN_TEXT to GREEN_BG
            else -> Color.parseColor("#E8ECEF") to Color.parseColor("#CC111619")
        }
        feedback.setTextColor(tone.first)
        feedback.background = Ui.rounded(tone.second, 999, this, LINE)
        renderSound()
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
            if (!active.processing) active.landmarker.close()
        }
        analysis?.clearAnalyzer()
        analysis = null
        camera?.cameraInfo?.cameraState?.removeObservers(this)
        camera = null
        provider?.unbindAll()
        stabilizer.reset()
        lastCorrection = null
        placementCue = null
        voice.reset()
        counter.pause()
        skeleton.show(null)
        setCollapsed(false)
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

    override fun onDestroy() {
        voice.shutdown()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != CAMERA_PERMISSION) return
        permissionDenied = grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED
        feedback.text = if (permissionDenied) "Chưa cấp quyền camera. Không thể tự đếm; các tính năng khác vẫn hoạt động."
        else "Đã cấp quyền camera. Bấm Bắt đầu khi sẵn sàng."
        render()
    }

    private class ExerciseSkeleton(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GREEN_TEXT
            strokeWidth = Ui.dp(context, 3).toFloat()
            strokeCap = Paint.Cap.ROUND
        }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GREEN_TEXT
            style = Paint.Style.FILL
        }
        private val dotRadius = Ui.dp(context, 3.5f).toFloat()
        private var pose: ExercisePose? = null

        fun show(pose: ExercisePose?) {
            this.pose = pose
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val p = pose ?: return
            fun x(point: BodyPoint) = point.x
            fun y(point: BodyPoint) = point.y
            val lines = listOf(p.wrist to p.elbow, p.elbow to p.shoulder, p.shoulder to p.hip,
                p.hip to p.knee, p.knee to p.ankle)
            for ((a, b) in lines) {
                if (a.confidence >= .7f && b.confidence >= .7f) canvas.drawLine(x(a), y(a), x(b), y(b), paint)
            }
            for (point in listOf(p.shoulder, p.elbow, p.wrist, p.hip, p.knee, p.ankle)) {
                if (point.confidence >= .7f) canvas.drawCircle(x(point), y(point), dotRadius, dot)
            }
        }
    }

    companion object {
        private const val CAMERA_PERMISSION = 47
        private val BG = Color.parseColor("#0B0E11")
        private val PANEL = Color.parseColor("#D10B0E11")
        private val PANEL_LINE = Color.parseColor("#24FFFFFF")
        private val LINE = Color.parseColor("#33FFFFFF")
        private val DIM = Color.parseColor("#B7C0C7")
        private val GREEN = Color.parseColor("#3D8B74")
        private val GREEN_TEXT = Color.parseColor("#8FD0B9")
        private val GREEN_BG = Color.parseColor("#EB14332A")
        private val AMBER = Color.parseColor("#F0C987")
        private val AMBER_BG = Color.parseColor("#EB3A2E14")
    }
}