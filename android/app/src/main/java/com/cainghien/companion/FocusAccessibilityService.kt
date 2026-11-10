package com.cainghien.companion

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class FocusAccessibilityService : AccessibilityService() {
    private lateinit var prefs: Prefs
    private lateinit var quota: QuotaManager
    private lateinit var secure: SecureStore
    private lateinit var windowManager: WindowManager
    private lateinit var notifications: NotificationManager
    private val handler = Handler(Looper.getMainLooper())
    private val network = Executors.newSingleThreadExecutor()
    private var overlay: FrameLayout? = null
    private var overlayEyebrow: TextView? = null
    private var overlayTitle: TextView? = null
    private var overlaySubtitle: TextView? = null
    private var overlayOpen: UiButton? = null
    private var gymPill: View? = null
    private var gymPillText: TextView? = null
    private var quotaPill: View? = null
    private var quotaPillText: TextView? = null
    private var lastUsageTick = 0L
    private var lastToast = 0L
    private val executor = Executors.newSingleThreadExecutor()
    private var visualScanner: Any? = null

    private var neverBlocked: Set<String> = emptySet()
    private val launchable = HashMap<String, Boolean>()
    private var lastUpdateAttemptElapsed = 0L
    private var gymNotifMode = -1
    private var gymNotifStart = 0L

    /** Phiên trên máy tính: chỉ thưởng điểm khi thấy nó chạy tới phút cuối rồi biến mất. */
    private var remoteWasActive = false
    private var remoteLastRemaining = Int.MAX_VALUE

    private fun keyguard(): KeyguardManager? = try {
        getSystemService(KEYGUARD_SERVICE) as KeyguardManager
    } catch (error: Exception) {
        null
    }

    /** Đọc trạng thái khoá màn hình để không trừ quỹ lúc máy đang khoá. */
    private fun refreshDeviceState() {
        Runtime.keyguardLocked = try {
            keyguard()?.isKeyguardLocked ?: false
        } catch (error: Exception) {
            false
        }
    }

    private val appVersion: String by lazy {
        try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (error: Exception) {
            ""
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Không để lỗi nào thoát ra: tiến trình chết vì sự kiện hệ thống là Android tắt luôn Trợ năng.
            try {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        Runtime.screenOn = false
                        refreshDeviceState()
                        finishGym()
                        removeGymPill()
                        removeQuotaPill()
                        cancelGymNotification()
                        lastUsageTick = SystemClock.elapsedRealtime()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        Runtime.screenOn = true
                        refreshDeviceState()
                        lastUsageTick = SystemClock.elapsedRealtime()
                        evaluate()
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        refreshDeviceState()
                        lastUsageTick = SystemClock.elapsedRealtime()
                        evaluate()
                    }
                }
            } catch (error: Exception) {
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        quota = QuotaManager(prefs)
        secure = SecureStore(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        notifications = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createChannels()
        neverBlocked = try {
            Apps.neverBlocked(this)
        } catch (error: Exception) {
            emptySet()
        }
        refreshDeviceState()
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        })
        handler.postDelayed(tick, 1500)
        handler.postDelayed(secondTick, 1000)
        handler.postDelayed(poll, 2000)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Chỉ nhận sự kiện đổi cửa sổ (khai báo trong XML). Không đăng ký thêm chạm/cuốn/gõ phím:
        // lượng sự kiện lớn làm service bị coi là không phản hồi và Android tắt luôn Trợ năng.
        try {
            updateRuntimeFocus()
            Toast.makeText(this, "Kỷ luật đã bật bảo vệ", Toast.LENGTH_SHORT).show()
            evaluate()
        } catch (error: Exception) {
        }
    }

    private fun localFocusActive(): Boolean = localFocusActive(
        prefs.localFocusEndMs,
        prefs.localFocusStopAfterMs,
        System.currentTimeMillis(),
    )

    private fun updateRuntimeFocus() {
        val now = System.currentTimeMillis()
        // Phiên chạy hết giờ thì cộng điểm học trước khi xoá dấu vết; dừng sớm thì chỉ xoá.
        if (settleLocalFocus(prefs, quota, now)) announcePoints()
        val local = localFocusActive()
        val protection = prefs.protectionEnabled
        if (!protection) Runtime.remoteManualFocus = false
        val manual = manualFocusActive(protection, Runtime.remoteManualFocus, local)
        if (local && !Runtime.remoteManualFocus) {
            Runtime.focusText = localFocusText(
                prefs.localFocusEndMs,
                prefs.localFocusStopAfterMs,
                now,
            )
        }
        Runtime.focusActive = manual
    }

    private val tick = object : Runnable {
        override fun run() {
            try {
                if (prefs.protectionEnabled) {
                    updateRuntimeFocus()
                    refreshDeviceState()
                    quota.resetIfNeeded()
                    evaluate()
                    if (Build.VERSION.SDK_INT >= 30) maybeScanVisual()
                } else {
                    Runtime.remoteManualFocus = false
                    Runtime.focusActive = false
                    prefs.clearLocalFocus()
                    forgetRemoteSession()
                    removeOverlay()
                    removeQuotaPill()
                    finishGym()
                    removeGymPill()
                    cancelGymNotification()
                }
            } catch (error: Exception) {
                removeOverlay()
            }
            handler.postDelayed(this, 5000)
        }
    }

    /**
     * Nhịp 1 giây: trừ quỹ khi đang ở app mạng xã hội và cập nhật đồng hồ đếm ngược.
     * Tách khỏi nhịp 5 giây để số giây trừ đúng và pill nhảy mượt.
     */
    private val secondTick = object : Runnable {
        override fun run() {
            try {
                if (prefs.protectionEnabled) {
                    refreshDeviceState()
                    quota.resetIfNeeded()
                    val now = SystemClock.elapsedRealtime()
                    if (isConsumingQuota()) {
                        val delta = ((now - lastUsageTick) / 1000).toInt().coerceIn(0, 5)
                        if (delta > 0) {
                            lastUsageTick = now
                            quota.consume(delta)
                            if (quota.isExhausted()) evaluate()
                        }
                    } else {
                        lastUsageTick = now
                    }
                    updateQuotaPill()
                    updateGym()
                } else {
                    lastUsageTick = SystemClock.elapsedRealtime()
                    removeQuotaPill()
                }
            } catch (error: Exception) {
            }
            handler.postDelayed(this, 1000)
        }
    }

    /** Quỹ của cả ngày, mở app mạng xã hội là bắt đầu trừ, không cần "vào nghỉ". */
    private fun isConsumingQuota(): Boolean {
        if (!prefs.protectionEnabled) return false
        if (!Runtime.screenOn || Runtime.keyguardLocked) return false
        if (Runtime.focusActive) return false
        val pkg = Runtime.currentPackage
        if (pkg.isEmpty() || pkg == packageName) return false
        if (pkg !in prefs.socialPackages) return false
        return !quota.isExhausted()
    }

    /**
     * Máy tính gộp "đang focus" thành một cờ. Điện thoại chỉ khoá app và cấm dùng quỹ khi
     * đó là phiên thủ công; khung giờ theo lịch không có quyền khoá gì cả.
     */
    private fun applyRemoteFocus(summary: Summary) {
        Runtime.remoteManualFocus = summary.sessionLabel != null
        if (summary.focusActive) Runtime.focusText = summary.focusText
    }

    /**
     * Phiên chạy trên máy tính cũng được điểm, nhưng máy tính không nói "hết giờ" hay "bị dừng".
     * Chỉ thưởng khi lần hỏi cuối còn thấy phiên ở phút 0–1 rồi biến mất; dừng sớm, mất ghép nối
     * hay tắt bảo vệ đều đi qua [forgetRemoteSession] nên không có điểm.
     */
    private fun settleRemoteFocus(summary: Summary) {
        if (summary.sessionLabel != null) {
            remoteWasActive = true
            remoteLastRemaining = summary.sessionRemaining
            return
        }
        if (!remoteWasActive) return
        val completed = remoteSessionCompleted(true, false, remoteLastRemaining) && prefs.protectionEnabled
        forgetRemoteSession()
        if (completed) {
            quota.awardSession()
            announcePoints()
        }
    }

    private fun forgetRemoteSession() {
        remoteWasActive = false
        remoteLastRemaining = Int.MAX_VALUE
    }

    /** Báo cho người dùng biết phiên vừa rồi đổi được bao nhiêu phút lướt. */
    private fun announcePoints() {
        val bonus = quota.bonusMinutes()
        handler.post {
            try {
                val tail = if (bonus > 0) " · quỹ hôm nay +$bonus phút" else ""
                Toast.makeText(this, "Xong phiên tập trung · +$POINTS_PER_SESSION điểm học$tail", Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
            }
        }
    }

    private val poll = object : Runnable {
        override fun run() {
            network.execute {
                try {
                    maybeCheckUpdate()
                    if (!prefs.protectionEnabled) {
                        Runtime.remoteManualFocus = false
                        Runtime.focusActive = false
                        prefs.clearLocalFocus()
                        forgetRemoteSession()
                        return@execute
                    }
                    updateRuntimeFocus()
                    val pair = secure.load()
                    if (pair == null) {
                        Runtime.remoteManualFocus = false
                        forgetRemoteSession()
                        updateRuntimeFocus()
                        handler.post { evaluate() }
                        return@execute
                    }
                    val summary = Api.summary(pair.baseUrl, pair.token)
                    applyRemoteFocus(summary)
                    settleRemoteFocus(summary)
                    updateRuntimeFocus()
                    Runtime.lastSyncElapsed = SystemClock.elapsedRealtime()
                    if (prefs.pendingGymCheckin) postGymCheckin()
                    handler.post { evaluate() }
                } catch (error: Exception) {
                    val last = Runtime.lastSyncElapsed
                    if (last > 0 && SystemClock.elapsedRealtime() - last > 15 * 60 * 1000L) {
                        Runtime.remoteManualFocus = false
                        forgetRemoteSession()
                        updateRuntimeFocus()
                        handler.post { evaluate() }
                    }
                }
            }
            handler.postDelayed(this, 20000)
        }
    }

    /**
     * Chỉ nhận sự kiện đổi cửa sổ. Toàn bộ thân hàm nằm trong try/catch và phần vẽ được đẩy sang
     * handler: trả sự kiện về hệ thống càng nhanh thì Trợ năng càng ít bị coi là treo, còn một lỗi
     * lúc vẽ overlay không bao giờ làm chết tiến trình — chết tiến trình là Android tắt Trợ năng.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
            val pkg = event.packageName?.toString() ?: return
            if (pkg == "com.android.systemui") return
            // Cửa sổ overlay của chính Kỷ luật (màn khoá, pill) cũng phát TYPE_WINDOW_STATE_CHANGED.
            // Không lọc thì app tự coi là "đang mở Kỷ luật" rồi gỡ khoá sau một nhịp.
            if (isOwnOverlayEvent(pkg, event.className?.toString(), packageName)) return
            if (isTransientHelper(pkg)) return
            Runtime.currentPackage = pkg
            lastUsageTick = SystemClock.elapsedRealtime()
            handler.post {
                try {
                    evaluate()
                    updateQuotaPill()
                } catch (error: Exception) {
                    removeOverlay()
                }
            }
        } catch (error: Exception) {
        }
    }

    private fun isTransientHelper(pkg: String): Boolean {
        if (pkg in neverBlocked) return false
        if (ALWAYS_TRACK.any { pkg.contains(it, ignoreCase = true) }) return false
        return !isLaunchable(pkg)
    }

    private fun isLaunchable(pkg: String): Boolean = launchable.getOrPut(pkg) {
        try {
            packageManager.getLaunchIntentForPackage(pkg) != null
        } catch (error: Exception) {
            true
        }
    }

    private fun isIgnored(pkg: String): Boolean {
        if (pkg.isEmpty() || pkg == packageName) return true
        return pkg in neverBlocked
    }

    private fun evaluate() {
        if (!prefs.protectionEnabled) {
            removeOverlay()
            return
        }
        val pkg = Runtime.currentPackage
        if (isIgnored(pkg)) {
            scheduleHide()
            return
        }
        val social = pkg in prefs.socialPackages
        if (!social) {
            scheduleHide()
            return
        }
        when {
            Runtime.focusActive -> showOverlay(
                "Phiên focus đang chạy",
                "Đang trong phiên focus",
                if (Runtime.focusText.isNotBlank()) {
                    Runtime.focusText
                } else {
                    "Hoàn thành phiên trên máy tính để mở khóa ứng dụng này."
                },
                false,
            )
            quota.isExhausted() -> showOverlay(
                "Hết quỹ hôm nay",
                "Quỹ mạng xã hội đã dùng xong",
                "Bạn đã tự chốt mức ${quota.limitMinutes} phút mỗi ngày. Quỹ làm mới lúc 0:00 — còn ${resetLabel()}. " +
                    "Hoàn thành một phiên tập trung để có thêm phút thưởng.",
                false,
            )
            // Quỹ của cả ngày: còn quỹ thì dùng app bình thường, chỉ hiện đồng hồ đếm ngược.
            else -> removeOverlay()
        }
    }

    /**
     * Quét ảnh bằng mô hình trên máy. Mọi lỗi đều bỏ qua và tạm dừng quét, không bao giờ
     * làm hỏng lớp bảo vệ DNS hay khoá máy: thà bỏ sót còn hơn treo điện thoại.
     */
    private fun maybeScanVisual() {
        if (Build.VERSION.SDK_INT < 30) return
        if (!prefs.visualFilterEnabled || !Runtime.screenOn || overlay != null) return
        val pkg = Runtime.currentPackage
        if (pkg.isEmpty() || isIgnored(pkg) || ALWAYS_TRACK.any { pkg.contains(it, ignoreCase = true) }) return
        if (pkg !in VISUAL_BROWSERS && pkg !in prefs.socialPackages) return
        val scanner = visualScanner as? VisualScanner
            ?: VisualScanner(this) {
                if (Runtime.currentPackage == pkg) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    Toast.makeText(
                        this,
                        "Đã chặn nội dung 18+ trên màn hình",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }.also { visualScanner = it }
        scanner.maybeScan {
            prefs.visualFilterEnabled && prefs.protectionEnabled && Runtime.currentPackage == pkg
        }
    }

    private fun closeVisualScanner() {
        if (Build.VERSION.SDK_INT < 30) return
        (visualScanner as? VisualScanner)?.close()
        visualScanner = null
    }

    private fun resetLabel(): String {
        val seconds = quota.secondsUntilReset()
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return when {
            hours <= 0 -> "$minutes phút"
            minutes == 0 -> "$hours giờ"
            else -> "$hours giờ $minutes phút"
        }
    }

    private fun showOverlay(
        eyebrow: String,
        title: String,
        subtitle: String,
        openApp: Boolean,
    ) {
        if (!Settings.canDrawOverlays(this)) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastToast > 60000) {
                lastToast = now
                Toast.makeText(this, "Cần cấp quyền hiển thị trên ứng dụng khác", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (overlay == null) {
            createOverlay() ?: return
        }
        overlayEyebrow?.text = eyebrow.uppercase()
        overlayTitle?.text = title
        overlaySubtitle?.text = subtitle
        overlayOpen?.visibility = if (openApp) View.VISIBLE else View.GONE
    }

    private fun createOverlay(): FrameLayout? {
        val service = this
        val root = FrameLayout(service)
        root.setBackgroundColor(Color.parseColor("#E60A0E11"))

        val column = LinearLayout(service)
        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.CENTER_HORIZONTAL
        column.setPadding(dp(30), dp(26), dp(30), dp(26))

        val glyph = FrameLayout(service)
        val circle = GradientDrawable()
        circle.shape = GradientDrawable.OVAL
        circle.setColor(Color.parseColor("#332F6F5E"))
        circle.setStroke(dp(1), Color.parseColor("#6678BEA8"))
        glyph.background = circle
        val shield = ImageView(service)
        shield.setImageDrawable(service.getDrawable(R.drawable.ic_shield_alert))
        shield.imageTintList = ColorStateList.valueOf(Color.parseColor("#8FD0B9"))
        glyph.addView(shield, FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER))
        column.addView(glyph, LinearLayout.LayoutParams(dp(62), dp(62)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(16)
        })

        overlayEyebrow = Ui.eyebrow(service, "").apply {
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#8FD0B9"))
        }
        column.addView(overlayEyebrow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        overlayTitle = Ui.label(service, "", 22f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            letterSpacing = -0.02f
            setLineSpacing(0f, 1.25f)
            setPadding(0, dp(8), 0, dp(9))
        }
        column.addView(overlayTitle, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        overlaySubtitle = Ui.label(service, "", 13.5f, Color.parseColor("#B7C0C7")).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.55f)
        }
        column.addView(overlaySubtitle, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        overlayOpen = Ui.button(service, "Quay lại Kỷ luật", Variant.PRIMARY, big = true).apply {
            background = Ui.ripple(Ui.rounded(OVERLAY_ACCENT, 13, service), 0x33FFFFFF)
            setOnClickListener {
                removeOverlay()
                startActivity(
                    Intent(service, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            }
        }
        column.addView(overlayOpen, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(17) })

        val home = Ui.button(service, "Dùng app khác", Variant.GHOST).apply {
            background = Ui.ripple(
                Ui.rounded(Color.TRANSPARENT, 11, service, Color.parseColor("#3A4249")),
                0x22FFFFFF,
            )
            setTextColor(Color.parseColor("#C3CBD1"))
            setTypeface(Ui.MEDIUM, Typeface.NORMAL)
            setOnClickListener {
                performGlobalAction(GLOBAL_ACTION_HOME)
                removeOverlay()
            }
        }
        column.addView(home, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        column.addView(Ui.label(
            service,
            "Màn hình này không chặn Cuộc gọi, Tin nhắn hay Cài đặt. Khẩn cấp: tắt quyền Trợ năng của Kỷ luật trong Cài đặt Android.",
            11.5f,
            Color.parseColor("#8C969D"),
        ).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.5f)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) })

        root.addView(column, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        ))
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        return try {
            windowManager.addView(root, params)
            overlay = root
            root
        } catch (error: Exception) {
            overlay = null
            null
        }
    }

    private fun removeOverlay() {
        overlay?.let {
            try {
                windowManager.removeView(it)
            } catch (error: Exception) {
            }
        }
        overlay = null
        overlayEyebrow = null
        overlayTitle = null
        overlaySubtitle = null
        overlayOpen = null
    }

    /**
     * Lúc mở app, Android có thể báo thêm một cửa sổ phụ (launcher, splash) ngay sau app thật.
     * Gỡ khoá ngay theo sự kiện đó làm màn khoá chớp rồi mất; chờ một nhịp rồi đánh giá lại
     * theo app đang thật sự ở tiền cảnh.
     */
    private val hideCheck = Runnable {
        if (!prefs.protectionEnabled) {
            removeOverlay()
            return@Runnable
        }
        val pkg = Runtime.currentPackage
        if (pkg.isNotEmpty() && pkg in prefs.socialPackages) {
            evaluate()
        } else {
            removeOverlay()
        }
    }

    private fun scheduleHide() {
        if (overlay == null) return
        handler.removeCallbacks(hideCheck)
        handler.postDelayed(hideCheck, HIDE_DELAY_MS)
    }

    private fun updateGym() {
        val pkg = Runtime.currentPackage
        if (pkg != prefs.gymPackage || !Runtime.screenOn) {
            if (Runtime.gymStartElapsed > 0L) finishGym()
            removeGymPill()
            cancelGymNotification()
            return
        }
        if (Runtime.gymStartElapsed == 0L) {
            Runtime.gymStartElapsed = SystemClock.elapsedRealtime()
            Runtime.gymAwarded = false
            gymNotifMode = -1
        }
        val seconds = ((SystemClock.elapsedRealtime() - Runtime.gymStartElapsed) / 1000).toInt()
        if (seconds >= GYM_TARGET_SECONDS && !Runtime.gymAwarded) {
            Runtime.gymAwarded = true
            prefs.pendingGymCheckin = true
            postGymCheckin()
        }
        showGymPill(seconds)
        updateGymNotification()
    }

    private fun finishGym() {
        Runtime.finishGym()
        removeGymPill()
        cancelGymNotification()
    }

    private fun postGymCheckin() {
        network.execute {
            try {
                val pair = secure.load() ?: return@execute
                Api.checkin(pair.baseUrl, pair.token, "gym", 0, "phone · 10 phút liên tục trong app tập")
                prefs.pendingGymCheckin = false
            } catch (error: Exception) {
            }
        }
    }

    private fun showGymPill(seconds: Int) {
        if (!Settings.canDrawOverlays(this)) return
        val text = if (Runtime.gymAwarded) {
            "Đã tính buổi tập · ${Ui.clockText(seconds)}"
        } else {
            "Buổi tập · còn ${Ui.clockText((GYM_TARGET_SECONDS - seconds).coerceAtLeast(0))}"
        }
        if (gymPill == null) {
            val pill = LinearLayout(this)
            pill.orientation = LinearLayout.HORIZONTAL
            pill.gravity = Gravity.CENTER_VERTICAL
            pill.setPadding(dp(11), dp(7), dp(12), dp(7))
            pill.background = Ui.rounded(PILL_BG, 999, this, PILL_LINE)
            val dot = View(this)
            dot.background = Ui.oval(if (Runtime.gymAwarded) Color.parseColor("#8FD0B9") else OVERLAY_ACCENT, this)
            pill.addView(dot, LinearLayout.LayoutParams(dp(6), dp(6)).apply { rightMargin = dp(7) })
            val label = TextView(this)
            label.text = text
            label.textSize = 12.5f
            label.isSingleLine = true
            label.setTextColor(Color.parseColor("#E8ECEF"))
            label.setTypeface(Ui.MEDIUM, Typeface.BOLD)
            pill.addView(label)
            gymPillText = label
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(42)
            }
            try {
                windowManager.addView(pill, params)
                gymPill = pill
            } catch (error: Exception) {
                gymPillText = null
            }
        } else {
            gymPillText?.text = text
        }
    }

    private fun removeGymPill() {
        gymPill?.let {
            try {
                windowManager.removeView(it)
            } catch (error: Exception) {
            }
        }
        gymPill = null
        gymPillText = null
    }

    /** Quỹ trừ dần khi đang ở app mạng xã hội. Hiện đồng hồ đếm ngược ngay trên app đang dùng
     *  để người dùng thấy mình đang tiêu quỹ chứ không phải đợi mở lại Kỷ luật. */
    private fun updateQuotaPill() {
        if (!isConsumingQuota()) {
            removeQuotaPill()
            return
        }
        showQuotaPill(quota.remainingSeconds())
    }

    private fun showQuotaPill(secondsLeft: Int) {
        if (!Settings.canDrawOverlays(this)) return
        val text = "Quỹ · còn ${Ui.clockText(secondsLeft)}"
        if (quotaPill == null) {
            val pill = LinearLayout(this)
            pill.orientation = LinearLayout.HORIZONTAL
            pill.gravity = Gravity.CENTER_VERTICAL
            pill.setPadding(dp(11), dp(7), dp(12), dp(7))
            pill.background = Ui.rounded(PILL_BG, 999, this, PILL_LINE)
            val dot = View(this)
            dot.background = Ui.oval(OVERLAY_ACCENT, this)
            pill.addView(dot, LinearLayout.LayoutParams(dp(6), dp(6)).apply { rightMargin = dp(7) })
            val label = TextView(this)
            label.text = text
            label.textSize = 12.5f
            label.isSingleLine = true
            label.setTextColor(Color.parseColor("#E8ECEF"))
            label.setTypeface(Ui.MEDIUM, Typeface.BOLD)
            pill.addView(label)
            quotaPillText = label
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(if (gymPill == null) 42 else 86)
            }
            try {
                windowManager.addView(pill, params)
                quotaPill = pill
            } catch (error: Exception) {
                quotaPillText = null
            }
        } else {
            quotaPillText?.text = text
        }
    }

    private fun removeQuotaPill() {
        quotaPill?.let {
            try {
                windowManager.removeView(it)
            } catch (error: Exception) {
            }
        }
        quotaPill = null
        quotaPillText = null
    }

    /** Chỉ đăng lại thông báo khi đổi trạng thái, dùng chronometer để khỏi spam mỗi giây. */
    private fun updateGymNotification() {
        val mode = if (Runtime.gymAwarded) 1 else 0
        if (mode == gymNotifMode && Runtime.gymStartElapsed == gymNotifStart) return
        gymNotifMode = mode
        gymNotifStart = Runtime.gymStartElapsed
        try {
            val intent = Intent(this, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                this, 42, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = Notification.Builder(this, "protection")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .setContentIntent(pending)
                .setUsesChronometer(true)
            if (Runtime.gymAwarded) {
                builder.setContentTitle("Đã tính 1 buổi gym")
                    .setContentText("Buổi tập đang tiếp tục")
                    .setChronometerCountDown(false)
                    .setWhen(Runtime.gymStartElapsed)
            } else {
                builder.setContentTitle("Đang tập cùng Kỷ luật")
                    .setContentText("Giữ app tập trên màn hình để tính 1 buổi")
                    .setChronometerCountDown(true)
                    .setWhen(Runtime.gymStartElapsed + GYM_TARGET_SECONDS * 1000L)
            }
            notifications.notify(42, builder.build())
        } catch (error: Exception) {
        }
    }

    private fun cancelGymNotification() {
        gymNotifMode = -1
        try {
            notifications.cancel(42)
        } catch (error: Exception) {
        }
    }

    /**
     * Trợ năng là tiến trình sống lâu nhất của app, nên việc hỏi GitHub bản mới đặt ở đây:
     * máy tính tắt, app không mở thì điện thoại vẫn biết có bản mới và tự nhắc.
     */
    private fun maybeCheckUpdate() {
        if (!prefs.autoUpdate) return
        val now = System.currentTimeMillis()
        if (now - prefs.updateLastCheckMs < UPDATE_CHECK_INTERVAL_MS) return
        if (SystemClock.elapsedRealtime() - lastUpdateAttemptElapsed < UPDATE_RETRY_INTERVAL_MS) return
        lastUpdateAttemptElapsed = SystemClock.elapsedRealtime()
        val release = try {
            UpdateChecker.latest()
        } catch (error: Exception) {
            null
        } ?: return
        // Chỉ ghi mốc khi lấy được dữ liệu; mất mạng thì lần chạy sau được thử lại ngay.
        prefs.updateLastCheckMs = now
        val version = release.version
        if (!UpdateChecker.isNewer(version, appVersion)) {
            if (prefs.updateAvailableVersion.isNotBlank()) prefs.updateAvailableVersion = ""
            return
        }
        prefs.updateAvailableVersion = version
        if (prefs.updateNotifiedVersion == version) return
        prefs.updateNotifiedVersion = version
        notifyUpdate(version)
    }

    private fun notifyUpdate(version: String) {
        try {
            val intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(
                this, 43, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = Notification.Builder(this, "update")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Có bản Kỷ luật mới $version")
                .setContentText("Chạm để tải và cài. Dữ liệu và mã kết nối giữ nguyên.")
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
            notifications.notify(43, notification)
        } catch (error: Exception) {
        }
    }

    private fun createChannels() {
        notifications.createNotificationChannel(
            NotificationChannel("protection", "Bảo vệ", NotificationManager.IMPORTANCE_LOW)
        )
        notifications.createNotificationChannel(
            NotificationChannel("update", "Cập nhật", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(screenReceiver)
        } catch (error: Exception) {
        }
        removeOverlay()
        removeGymPill()
        removeQuotaPill()
        cancelGymNotification()
        network.shutdownNow()
        executor.shutdownNow()
        closeVisualScanner()
        super.onDestroy()
    }

    companion object {
        private const val HIDE_DELAY_MS = 800L
        private const val GYM_TARGET_SECONDS = 600
        private const val UPDATE_CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000
        private const val UPDATE_RETRY_INTERVAL_MS = 60L * 60 * 1000
        private val OVERLAY_ACCENT = Color.parseColor("#3D8B74")
        private val PILL_BG = Color.parseColor("#E6151A1E")
        private val PILL_LINE = Color.parseColor("#2E363C")

        /** Nhóm này luôn được tính là ứng dụng thật để không bao giờ che màn hình khẩn cấp. */
        private val ALWAYS_TRACK = listOf(
            "incall", "telecom", "telephony", "dialer", "settings", "systemui",
            "launcher", "permissioncontroller", "packageinstaller", "sms", "mms",
        )

        /** Chỉ đọc màn hình trình duyệt và app mạng xã hội người dùng đã chọn, không quét app ngân hàng hay tin nhắn. */
        private val VISUAL_BROWSERS = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "com.android.browser",
            "com.coccoc.trinhduyet",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "org.mozilla.focus",
            "com.brave.browser",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.opera.gx",
            "com.sec.android.app.sbrowser",
            "com.duckduckgo.mobile.android",
            "com.vivaldi.browser",
            "com.yandex.browser",
            "com.UCMobile.intl",
            "com.mi.globalbrowser",
            "com.huawei.browser",
            "com.heytap.browser",
            "com.coloros.browser",
            "com.vivo.browser",
            "com.ecosia.android",
            "com.cloudmosa.puffinFree",
            "com.google.android.youtube",
        )
    }
}
