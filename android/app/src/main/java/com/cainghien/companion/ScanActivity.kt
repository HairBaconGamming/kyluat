package com.cainghien.companion

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.CameraPreview
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory

class ScanActivity : Activity() {
    private var scanner: DecoratedBarcodeView? = null
    private var overlayView: ScanOverlay? = null
    private var loadingBox: LinearLayout? = null
    private var stateCard: LinearLayout? = null
    private var stateTitle: TextView? = null
    private var stateBody: TextView? = null
    private var statePrimary: UiButton? = null
    private var torchButton: UiButton? = null
    private var sweeper: ValueAnimator? = null
    private var torchOn = false
    private var delivered = false
    private var scanning = false
    private var cameraFailed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        val root = FrameLayout(this)
        root.setBackgroundColor(BG)

        if (hasCamera()) {
            try {
                val view = DecoratedBarcodeView(this)
                view.setDecoderFactory(DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE)))
                view.barcodeView.marginFraction = 0.14
                view.barcodeView.addStateListener(stateListener)
                view.cameraSettings.apply {
                    isContinuousFocusEnabled = true
                    isAutoFocusEnabled = true
                    isMeteringEnabled = true
                    isBarcodeSceneModeEnabled = true
                }
                view.viewFinder.setLaserVisibility(false)
                view.viewFinder.setMaskColor(MASK)
                view.statusView.visibility = View.GONE
                root.addView(view, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                ))
                scanner = view
            } catch (error: Exception) {
                cameraFailed = true
            }
        } else {
            cameraFailed = true
        }

        val overlay = ScanOverlay(this)
        overlayView = overlay
        root.addView(overlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        root.addView(buildTopBar(), FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP
        ))
        root.addView(buildBottomBox(), FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
        ))
        root.addView(buildStateCard(), FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        ).apply {
            leftMargin = Ui.dp(this@ScanActivity, 22)
            rightMargin = Ui.dp(this@ScanActivity, 22)
        })
        setContentView(root)
    }

    private fun buildTopBar(): LinearLayout {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), 0)

        val back = FrameLayout(this)
        back.background = Ui.ripple(Ui.rounded(Color.parseColor("#26FFFFFF"), 999, this), 0x33FFFFFF)
        back.isClickable = true
        back.setOnClickListener { finish() }
        back.addView(
            Ui.icon(this, R.drawable.ic_chevron_left, Color.WHITE, 18),
            FrameLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18), Gravity.CENTER),
        )
        bar.addView(back, LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)))

        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        titles.setPadding(Ui.dp(this, 12), 0, 0, 0)
        val title = Ui.label(this, "Quét mã kết nối", 19f, Color.WHITE, true)
        title.setShadowLayer(6f, 0f, Ui.dp(this, 1).toFloat(), 0xB3000000.toInt())
        titles.addView(title)
        titles.addView(Ui.label(this, "Mã QR hiện trong app Kỷ luật trên máy tính", 12f, TEXT_DIM))
        bar.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return bar
    }

    private fun buildBottomBox(): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), Ui.dp(this, 26))

        val loading = LinearLayout(this)
        loading.orientation = LinearLayout.HORIZONTAL
        loading.gravity = Gravity.CENTER_VERTICAL
        loading.background = Ui.rounded(Color.parseColor("#CC111619"), 999, this, CARD_LINE)
        loading.setPadding(Ui.dp(this, 14), Ui.dp(this, 9), Ui.dp(this, 16), Ui.dp(this, 9))
        val spinner = ProgressBar(this)
        spinner.indeterminateTintList = ColorStateList.valueOf(LASER)
        loading.addView(spinner, LinearLayout.LayoutParams(Ui.dp(this, 15), Ui.dp(this, 15)).apply {
            rightMargin = Ui.dp(this@ScanActivity, 9)
        })
        loading.addView(Ui.label(this, "Đang mở camera…", 12.5f, Color.parseColor("#E8ECEF")))
        loadingBox = loading
        box.addView(loading, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = Ui.dp(this@ScanActivity, 13) })

        val buttons = LinearLayout(this)
        buttons.orientation = LinearLayout.HORIZONTAL

        torchButton = Ui.button(this, "Bật đèn", Variant.GHOST).apply {
            background = Ui.ripple(Ui.rounded(Color.parseColor("#26FFFFFF"), 12, this@ScanActivity, CARD_LINE), 0x33FFFFFF)
            setTextColor(Color.parseColor("#E8ECEF"))
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_flash, 0, 0, 0)
            compoundDrawablePadding = Ui.dp(this@ScanActivity, 7)
            TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(Color.parseColor("#E8ECEF")))
            setOnClickListener { toggleTorch() }
        }
        buttons.addView(torchButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val manual = Ui.button(this, "Nhập mã 6 số", Variant.QUIET).apply {
            background = Ui.ripple(Ui.rounded(Color.parseColor("#26FFFFFF"), 12, this@ScanActivity, CARD_LINE), 0x33FFFFFF)
            setTextColor(Color.parseColor("#E8ECEF"))
            setOnClickListener { finish() }
        }
        buttons.addView(manual, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = Ui.dp(this@ScanActivity, 8)
        })
        box.addView(buttons, Ui.match(this))
        return box
    }

    private fun buildStateCard(): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = Ui.rounded(CARD, 18, this, CARD_LINE)
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18))
        card.visibility = View.GONE
        stateTitle = Ui.label(this, "", 16f, Color.WHITE, true)
        card.addView(stateTitle)
        stateBody = Ui.label(this, "", 12.5f, TEXT_DIM)
        card.addView(stateBody, Ui.match(this, topMarginDp = 7))
        statePrimary = Ui.button(this, "", Variant.PRIMARY, big = true).apply {
            background = Ui.ripple(Ui.rounded(Color.parseColor("#3D8B74"), 13, this@ScanActivity), 0x33FFFFFF)
        }
        card.addView(statePrimary, Ui.match(this, topMarginDp = 15))
        stateCard = card
        return card
    }

    private val callback = object : BarcodeCallback {
        override fun barcodeResult(result: BarcodeResult) {
            val text = result.text ?: return
            if (delivered) return
            delivered = true
            stopSweep()
            try {
                scanner?.pause()
            } catch (error: Exception) {
            }
            setResult(RESULT_OK, Intent().putExtra(EXTRA_CONTENTS, text))
            finish()
        }
    }

    private val stateListener = object : CameraPreview.StateListener {
        override fun previewSized() {
            runOnUiThread { overlayView?.invalidate() }
        }

        override fun previewStarted() {
            runOnUiThread { hideLoading() }
        }

        override fun previewStopped() {
        }

        override fun cameraError(error: Exception) {
            runOnUiThread {
                cameraFailed = true
                scanning = false
                stopSweep()
                showState(
                    "Không mở được camera",
                    "Camera có thể đang được app khác dùng. Bạn vẫn kết nối được bằng mã 6 số trên máy tính.",
                    "Nhập mã 6 số",
                ) { finish() }
            }
        }

        override fun cameraClosed() {
        }
    }

    override fun onResume() {
        super.onResume()
        delivered = false
        when {
            cameraFailed -> showState(
                "Không mở được camera",
                "Điện thoại này không có camera hoặc thư viện camera lỗi. Bạn vẫn kết nối được bằng mã 6 số trên máy tính.",
                "Nhập mã 6 số",
            ) { finish() }
            checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED -> showState(
                "Cần quyền camera",
                "Màn này dùng camera để đọc mã QR kết nối, không chụp và không lưu hình ảnh.",
                "Cấp quyền camera",
            ) { requestPermissions(arrayOf(Manifest.permission.CAMERA), PERMISSION) }
            else -> {
                hideState()
                startScan()
            }
        }
    }

    private fun startScan() {
        if (scanning) return
        val view = scanner ?: return
        try {
            view.resume()
            view.decodeSingle(callback)
            scanning = true
            showLoading()
            startSweep()
        } catch (error: Exception) {
            cameraFailed = true
            stopSweep()
            showState(
                "Không mở được camera",
                "Bạn vẫn kết nối được bằng mã 6 số trên máy tính.",
                "Nhập mã 6 số",
            ) { finish() }
        }
    }

    override fun onPause() {
        scanning = false
        if (torchOn) toggleTorch()
        stopSweep()
        try {
            scanner?.pause()
        } catch (error: Exception) {
        }
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            hideState()
            startScan()
        } else {
            showState(
                "Chưa cấp quyền camera",
                "Không có quyền thì không quét được. Bạn vẫn kết nối được bằng mã 6 số trên máy tính.",
                "Nhập mã 6 số",
            ) { finish() }
        }
    }

    private fun toggleTorch() {
        val view = scanner ?: return
        try {
            if (torchOn) view.setTorchOff() else view.setTorchOn()
            torchOn = !torchOn
            torchButton?.text = if (torchOn) "Tắt đèn" else "Bật đèn"
        } catch (error: Exception) {
        }
    }

    private fun showLoading() {
        loadingBox?.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        loadingBox?.visibility = View.GONE
    }

    private fun showState(title: String, body: String, primary: String, action: () -> Unit) {
        hideLoading()
        stopSweep()
        stateTitle?.text = title
        stateBody?.text = body
        statePrimary?.text = primary
        statePrimary?.setOnClickListener { action() }
        stateCard?.visibility = View.VISIBLE
    }

    private fun hideState() {
        stateCard?.visibility = View.GONE
    }

    private fun startSweep() {
        if (sweeper?.isRunning == true) return
        sweeper = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { overlayView?.fraction = it.animatedValue as Float }
            start()
        }
    }

    private fun stopSweep() {
        sweeper?.cancel()
        sweeper = null
    }

    private fun hasCamera(): Boolean = try {
        packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    } catch (error: Exception) {
        false
    }

    override fun onDestroy() {
        stopSweep()
        super.onDestroy()
    }

    private inner class ScanOverlay(context: Context) : View(context) {
        var fraction = 0f
            set(value) {
                field = value
                invalidate()
            }

        private val rect = RectF()
        private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = px(1.2f)
            color = Color.parseColor("#40FFFFFF")
        }
        private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = px(3.4f)
            strokeCap = Paint.Cap.ROUND
            color = CORNER
        }
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = px(2f)
            strokeCap = Paint.Cap.ROUND
            color = LASER
        }

        override fun onDraw(canvas: Canvas) {
            val view = scanner ?: return
            val framing = try {
                view.barcodeView.framingRect
            } catch (error: Exception) {
                null
            } ?: return
            rect.set(framing.left.toFloat(), framing.top.toFloat(), framing.right.toFloat(), framing.bottom.toFloat())
            if (rect.width() <= 0f || rect.height() <= 0f) return

            val radius = px(20f)
            canvas.drawRoundRect(rect, radius, radius, framePaint)

            val arm = px(28f)
            canvas.drawLine(rect.left, rect.top + arm, rect.left, rect.top, cornerPaint)
            canvas.drawLine(rect.left, rect.top, rect.left + arm, rect.top, cornerPaint)
            canvas.drawLine(rect.right - arm, rect.top, rect.right, rect.top, cornerPaint)
            canvas.drawLine(rect.right, rect.top, rect.right, rect.top + arm, cornerPaint)
            canvas.drawLine(rect.right, rect.bottom - arm, rect.right, rect.bottom, cornerPaint)
            canvas.drawLine(rect.right, rect.bottom, rect.right - arm, rect.bottom, cornerPaint)
            canvas.drawLine(rect.left + arm, rect.bottom, rect.left, rect.bottom, cornerPaint)
            canvas.drawLine(rect.left, rect.bottom, rect.left, rect.bottom - arm, cornerPaint)

            val inset = px(20f)
            val y = rect.top + inset + (rect.height() - inset * 2) * fraction
            canvas.drawLine(rect.left + inset, y, rect.right - inset, y, linePaint)
        }

        private fun px(value: Float): Float = value * resources.displayMetrics.density
    }

    companion object {
        const val EXTRA_CONTENTS = "qr_contents"
        const val REQUEST = 22
        private const val PERMISSION = 21
        private val BG = Color.parseColor("#0B0E11")
        private val MASK = Color.parseColor("#B3050809")
        private val CARD = Color.parseColor("#F2151A1E")
        private val CARD_LINE = Color.parseColor("#33FFFFFF")
        private val TEXT_DIM = Color.parseColor("#B7C0C7")
        private val CORNER = Color.parseColor("#4FA98D")
        private val LASER = Color.parseColor("#8FD0B9")
    }
}
