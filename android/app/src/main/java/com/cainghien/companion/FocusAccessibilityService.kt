package com.cainghien.companion

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
    private var lastUsageTick = 0L
    private var lastToast = 0L
    private val executor = Executors.newSingleThreadExecutor()

    private var neverBlocked: Set<String> = emptySet()
    private val launchable = HashMap<String, Boolean>()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Runtime.screenOn = false
                    finishGym()
                    removeGymPill()
                    cancelGymNotification()
                    lastUsageTick = SystemClock.elapsedRealtime()
                }
                Intent.ACTION_SCREEN_ON -> {
                    Runtime.screenOn = true
                    lastUsageTick = SystemClock.elapsedRealtime()
                    evaluate()
                }
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
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        })
        handler.postDelayed(tick, 1500)
        handler.postDelayed(poll, 2000)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Toast.makeText(this, "Kỷ luật đã bật bảo vệ", Toast.LENGTH_SHORT).show()
        reassertBlockedWindow()
        evaluate()
    }

    private val tick = object : Runnable {
        override fun run() {
            try {
                if (prefs.protectionEnabled) {
                    quota.resetIfNeeded()
                    val social = Runtime.currentPackage in prefs.socialPackages
                    if (
                        social && Runtime.screenOn && !Runtime.focusActive &&
                        quota.isBreakActive() && Runtime.currentPackage != packageName
                    ) {
                        val now = SystemClock.elapsedRealtime()
                        val delta = ((now - lastUsageTick) / 1000).toInt().coerceIn(0, 10)
                        lastUsageTick = now
                        if (delta > 0) quota.consume(delta)
                    } else {
                        lastUsageTick = SystemClock.elapsedRealtime()
                    }
                    reassertBlockedWindow()
                    evaluate()
                    updateBreakNotification()
                    updateGym()
                } else {
                    removeOverlay()
                    finishGym()
                    removeGymPill()
                    cancelGymNotification()
                    cancelBreakNotification()
                }
            } catch (error: Exception) {
                removeOverlay()
            }
            handler.postDelayed(this, 5000)
        }
    }

    private val poll = object : Runnable {
        override fun run() {
            network.execute {
                try {
                    if (!prefs.protectionEnabled) {
                        Runtime.focusActive = false
                        return@execute
                    }
                    val pair = secure.load()
                    if (pair == null) {
                        Runtime.focusActive = false
                        handler.post { evaluate() }
                        return@execute
                    }
                    val summary = Api.summary(pair.baseUrl, pair.token)
                    Runtime.focusActive = summary.focusActive
                    Runtime.focusText = summary.focusText
                    Runtime.lastSyncElapsed = SystemClock.elapsedRealtime()
                    if (prefs.pendingGymCheckin) postGymCheckin()
                    handler.post { evaluate() }
                } catch (error: Exception) {
                    val last = Runtime.lastSyncElapsed
                    if (last > 0 && SystemClock.elapsedRealtime() - last > 15 * 60 * 1000L) {
                        Runtime.focusActive = false
                        handler.post { evaluate() }
                    }
                }
            }
            handler.postDelayed(this, 20000)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == "com.android.systemui") return
        if (isTransientHelper(pkg)) return
        Runtime.currentPackage = pkg
        lastUsageTick = SystemClock.elapsedRealtime()
        try {
            evaluate()
        } catch (error: Exception) {
            removeOverlay()
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

    /** Cửa sổ phụ (ads, webview, "android") có thể làm mất màn khoá — kiểm tra lại app thật đang hiển thị. */
    private fun reassertBlockedWindow() {
        val current = Runtime.currentPackage
        if (current.isNotEmpty() && current in prefs.socialPackages) return
        val active = try {
            rootInActiveWindow?.packageName?.toString()
        } catch (error: Exception) {
            null
        } ?: return
        if (active.isNotEmpty() && active != packageName && active in prefs.socialPackages) {
            Runtime.currentPackage = active
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
            removeOverlay()
            return
        }
        val social = pkg in prefs.socialPackages
        if (!social) {
            removeOverlay()
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
                "60 phút mạng xã hội đã dùng xong",
                "Bạn đã cam kết chỉ lướt 1 tiếng mỗi ngày. Quỹ làm mới lúc 0:00 — còn ${resetLabel()}.",
                false,
            )
            quota.isBreakActive() -> removeOverlay()
            else -> showOverlay(
                "Ứng dụng được bảo vệ",
                "App này đang trong danh sách chặn",
                "Mở Kỷ luật để dùng quỹ mạng xã hội trong ngày, hoặc quay lại việc đang học.",
                true,
            )
        }
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
        }
        val seconds = ((SystemClock.elapsedRealtime() - Runtime.gymStartElapsed) / 1000).toInt()
        if (seconds >= 600 && !Runtime.gymAwarded) {
            Runtime.gymAwarded = true
            prefs.pendingGymCheckin = true
            postGymCheckin()
        }
        showGymPill(seconds)
        showGymNotification(seconds)
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
            "Buổi tập · ${Ui.clockText(seconds)} / 10:00"
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

    private fun showGymNotification(seconds: Int) {
        val text = if (Runtime.gymAwarded) {
            "Đã tính 1 buổi gym"
        } else {
            "${Ui.clockText(seconds)} / 10:00 liên tục"
        }
        notify(42, "Đang tập cùng Kỷ luật", text)
    }

    private fun cancelGymNotification() {
        try {
            notifications.cancel(42)
        } catch (error: Exception) {
        }
    }

    private fun updateBreakNotification() {
        if (!prefs.protectionEnabled) {
            cancelBreakNotification()
            return
        }
        if (quota.isBreakActive()) {
            val minutes = (quota.breakRemainingSeconds() + 59) / 60
            notify(41, "Đang nghỉ có kiểm soát", "Còn $minutes phút trong quỹ mạng xã hội hôm nay")
        } else {
            cancelBreakNotification()
        }
    }

    private fun cancelBreakNotification() {
        try {
            notifications.cancel(41)
        } catch (error: Exception) {
        }
    }

    private fun notify(id: Int, title: String, text: String) {
        try {
            val intent = Intent(this, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                this, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = Notification.Builder(this, "protection")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(pending)
                .build()
            notifications.notify(id, notification)
        } catch (error: Exception) {
        }
    }

    private fun createChannels() {
        notifications.createNotificationChannel(
            NotificationChannel("protection", "Bảo vệ", NotificationManager.IMPORTANCE_LOW)
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
        cancelGymNotification()
        cancelBreakNotification()
        network.shutdownNow()
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private val OVERLAY_ACCENT = Color.parseColor("#3D8B74")
        private val PILL_BG = Color.parseColor("#E6151A1E")
        private val PILL_LINE = Color.parseColor("#2E363C")

        /** Nhóm này luôn được tính là ứng dụng thật để không bao giờ che màn hình khẩn cấp. */
        private val ALWAYS_TRACK = listOf(
            "incall", "telecom", "telephony", "dialer", "settings", "systemui",
            "launcher", "permissioncontroller", "packageinstaller", "sms", "mms",
        )
    }
}
