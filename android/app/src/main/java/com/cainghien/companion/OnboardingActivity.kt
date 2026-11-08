package com.cainghien.companion

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class OnboardingActivity : Activity() {
    private data class AppEntry(val label: String, val packageName: String)

    private lateinit var prefs: Prefs
    private lateinit var secure: SecureStore
    private lateinit var quota: QuotaManager
    private val executor = Executors.newSingleThreadExecutor()
    private val icons = LruCache<String, Drawable.ConstantState>(80)
    private var apps: List<AppEntry>? = null
    private var step = 0
    private var quotaChoice = 60
    private var destroyed = false
    private var appQuery = ""
    private var pendingUnlockAction: (() -> Unit)? = null
    private var appRowsBox: LinearLayout? = null
    private var appCountLabel: TextView? = null
    private var actionHint: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        secure = SecureStore(this)
        quota = QuotaManager(prefs)
        Ui.chrome(this)
        quotaChoice = quota.limitMinutes
        if (savedInstanceState != null) {
            step = savedInstanceState.getInt("step", 0)
            quotaChoice = savedInstanceState.getInt("quota", quotaChoice)
            appQuery = savedInstanceState.getString("app_query", "") ?: ""
        }
        if (!prefs.onboardingDone && prefs.looksUntouched() && secure.load() == null) {
            // Người dùng mới chọn app từ đầu, đúng như preview; người đã dùng app thì giữ nguyên.
            prefs.socialPackages = emptySet()
        }
        loadApps()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("step", step)
        outState.putInt("quota", quotaChoice)
        outState.putString("app_query", appQuery)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (step > 0) {
            step -= 1
            render()
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        destroyed = true
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST) {
            if (resultCode == RESULT_OK) enableAdult() else prefs.adultFilterEnabled = false
        }
        if (requestCode == UNLOCK_REQUEST) {
            if (resultCode == RESULT_OK) pendingUnlockAction?.invoke()
            pendingUnlockAction = null
        }
        render()
    }

    /** Chạy [then] ngay nếu cổng đang mở; nếu không thì mở cổng 60 giây rồi chạy khi qua cổng. */
    private fun requireUnlock(then: () -> Unit) {
        if (prefs.rulesUnlocked(System.currentTimeMillis(), SystemClock.elapsedRealtime())) {
            then()
            return
        }
        pendingUnlockAction = then
        startActivityForResult(
            Intent(this, UnlockActivity::class.java)
                .putExtra(UnlockActivity.EXTRA_PURPOSE, UnlockActivity.PURPOSE_UNLOCK),
            UNLOCK_REQUEST,
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }

    private fun render() {
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(Ui.BG)
        page.setPadding(dp(16), dp(8), dp(16), dp(10))

        if (step in 1..5) page.addView(header())

        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.clipToPadding = false
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(0, dp(if (step == 0 || step == 6) 30 else 12), 0, dp(12))
        when (step) {
            0 -> welcome(body)
            1 -> permissions(body)
            2 -> appSelection(body)
            3 -> quotaStep(body)
            4 -> optionalStep(body)
            5 -> summary(body)
            else -> done(body)
        }
        scroll.addView(body)
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        page.addView(actions())
        setContentView(page)
    }

    private fun header(): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(2), 0, 0)

        val back = LinearLayout(this)
        back.background = Ui.ripple(Ui.rounded(Ui.SURFACE, 999, this, Ui.LINE), 0x12000000)
        back.gravity = Gravity.CENTER
        back.isClickable = true
        back.isFocusable = true
        back.contentDescription = "Quay lại"
        back.addView(Ui.icon(this, R.drawable.ic_chevron_left, Ui.TEXT, 18))
        back.setOnClickListener { onBackPressed() }
        row.addView(back, LinearLayout.LayoutParams(dp(38), dp(38)))

        val mid = LinearLayout(this)
        mid.orientation = LinearLayout.VERTICAL
        mid.setPadding(dp(11), 0, dp(8), 0)
        val label = Ui.label(this, "Bước $step / 5", 11.5f, Ui.TEXT_3)
        mid.addView(label)
        val segs = LinearLayout(this)
        segs.orientation = LinearLayout.HORIZONTAL
        segs.setPadding(0, dp(4), 0, 0)
        repeat(5) { index ->
            val seg = View(this)
            seg.background = Ui.rounded(if (index < step) Ui.ACCENT else Ui.STEP_OFF, 2, this)
            segs.addView(seg, LinearLayout.LayoutParams(0, dp(3), 1f).apply {
                if (index < 4) rightMargin = dp(4)
            })
        }
        mid.addView(segs, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(mid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        if (step in 2..4) {
            val skip = Ui.label(this, "Bỏ qua", 13f, Ui.ACCENT, true)
            skip.setPadding(dp(9), dp(10), dp(7), dp(10))
            skip.isClickable = true
            skip.isFocusable = true
            skip.setOnClickListener { step += 1; render() }
            row.addView(skip)
        }
        return row
    }

    private fun heading(body: LinearLayout, title: String, lede: String) {
        val titleView = Ui.label(this, title, 24f, Ui.TEXT, true)
        titleView.letterSpacing = -0.01f
        titleView.setLineSpacing(0f, 1.25f)
        body.addView(titleView)
        val text = Ui.label(this, lede, 13.5f, Ui.TEXT_2)
        text.setLineSpacing(0f, 1.55f)
        text.setPadding(0, dp(8), 0, 0)
        body.addView(text)
    }

    private fun note(text: String, warn: Boolean = false): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.TOP
        box.setPadding(dp(11), dp(10), dp(11), dp(10))
        box.background = Ui.rounded(
            if (warn) Ui.WARN_SOFT else Ui.SURFACE_2, 12, this,
            if (warn) Ui.WARN_LINE else Ui.LINE_SOFT,
        )
        box.addView(
            Ui.icon(this, if (warn) R.drawable.ic_shield_alert else R.drawable.ic_info, if (warn) Ui.WARN else Ui.TEXT_3, 15),
            LinearLayout.LayoutParams(dp(15), dp(15)).apply { rightMargin = dp(8); topMargin = dp(1) },
        )
        val label = Ui.label(this, text, 12f, if (warn) Ui.WARN else Ui.TEXT_2)
        label.setLineSpacing(0f, 1.45f)
        box.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return box
    }

    private fun welcome(body: LinearLayout) {
        body.gravity = Gravity.CENTER_HORIZONTAL
        val glyph = LinearLayout(this)
        glyph.background = Ui.rounded(Ui.ACCENT, 20, this)
        glyph.gravity = Gravity.CENTER
        glyph.addView(Ui.icon(this, R.drawable.ic_shield, Color.WHITE, 34))
        body.addView(glyph, LinearLayout.LayoutParams(dp(64), dp(64)).apply { bottomMargin = dp(20) })

        val title = Ui.label(this, "Kỷ luật", 32f, Ui.TEXT, true)
        title.letterSpacing = -0.02f
        body.addView(title)

        val lede = Ui.label(
            this,
            "Bạn không thiếu ý chí. Bạn thiếu một lớp cản giữa bạn và cơn thèm mở app.",
            14f, Ui.TEXT_2,
        )
        lede.setLineSpacing(0f, 1.6f)
        lede.setPadding(0, dp(10), 0, dp(6))
        body.addView(lede)

        val points = listOf(
            "Bạn chọn app, máy không tự đoán.",
            "Chạy trên máy bạn, không tài khoản, không máy chủ.",
            "Hai quyền cốt lõi là bắt buộc, phần còn lại bỏ qua được.",
        )
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, dp(14), 0, 0)
        points.forEach { text ->
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            line.gravity = Gravity.TOP
            line.setPadding(0, dp(5), 0, dp(5))
            val dot = LinearLayout(this)
            dot.background = Ui.rounded(Ui.ACCENT_SOFT, 999, this)
            dot.gravity = Gravity.CENTER
            dot.addView(Ui.icon(this, R.drawable.ic_check, Ui.ACCENT, 12))
            line.addView(dot, LinearLayout.LayoutParams(dp(18), dp(18)).apply { rightMargin = dp(10) })
            val label = Ui.label(this, text, 13f, Ui.TEXT_2)
            label.setLineSpacing(0f, 1.45f)
            line.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            box.addView(line)
        }
        body.addView(box)
    }

    private fun permissions(body: LinearLayout) {
        heading(
            body,
            "Cấp hai quyền\ncốt lõi",
            "Thiếu một trong hai, Kỷ luật không biết app nào đang mở và không phủ được màn khoá.",
        )
        val rows = LinearLayout(this)
        rows.orientation = LinearLayout.VERTICAL
        rows.setPadding(0, dp(14), 0, 0)
        rows.addView(permissionRow(
            R.drawable.ic_layers,
            "Hiển thị trên ứng dụng khác",
            "Để phủ màn khoá lên app đã chọn",
            overlayGranted(),
        ) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        rows.addView(permissionRow(
            R.drawable.ic_accessibility,
            "Trợ năng",
            "Chỉ đọc tên app đang mở, không đọc nội dung",
            accessibilityGranted(),
        ) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        rows.addView(permissionRow(
            R.drawable.ic_bell,
            "Thông báo (tuỳ chọn)",
            "Đồng hồ buổi tập",
            notificationsGranted(),
        ) {
            if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted()) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFY_REQUEST)
            } else {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                )
            }
        })
        body.addView(rows)
        addTop(body, note("Android luôn cho bạn thu hồi hai quyền này trong Cài đặt. Kỷ luật không đọc nội dung màn hình."), 14)
        if (!accessibilityGranted()) addTop(body, Ui.restrictedCard(this) { Apps.openAppInfo(this) }, 6)
    }

    private fun permissionRow(
        iconRes: Int,
        title: String,
        subtitle: String,
        granted: Boolean,
        onClick: () -> Unit,
    ): View {
        val row = Ui.row(this, Ui.tile(this, iconRes, if (granted) Tone.ACCENT else Tone.NEUTRAL))
        row.title(title)
        row.subtitle(subtitle)
        row.trailing(Ui.pill(this, if (granted) "Đã cấp" else "Cấp", if (granted) Tone.ACCENT else Tone.NEUTRAL, false), 8)
        row.setPadding(dp(13), dp(12), dp(13), dp(12))
        row.background = Ui.ripple(Ui.rounded(Ui.SURFACE, 15, this, Ui.LINE), 0x12000000)
        row.isClickable = true
        row.isFocusable = true
        row.setOnClickListener { onClick() }
        return row.withBottomMargin(9)
    }

    private fun appSelection(body: LinearLayout) {
        heading(
            body,
            "App nào đang lấy\nthời gian của bạn?",
            "Chỉ app bạn gạt mới bị khoá. App ngoài danh sách luôn mở bình thường.",
        )
        val search = Ui.field(this, "Tìm ứng dụng…")
        search.setText(appQuery)
        search.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0)
        search.compoundDrawablePadding = dp(8)
        search.compoundDrawableTintList = ColorStateList.valueOf(Ui.TEXT_3)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                appQuery = s?.toString() ?: ""
                renderAppRows()
            }
        })
        addTop(body, search, 13)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(2), dp(14), dp(2), dp(7))
        head.addView(
            Ui.eyebrow(this, "Tất cả ứng dụng"),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        appCountLabel = Ui.label(this, "", 11.5f, Ui.TEXT_3)
        head.addView(appCountLabel)
        body.addView(head)

        appRowsBox = LinearLayout(this)
        appRowsBox?.orientation = LinearLayout.VERTICAL
        appRowsBox?.let { body.addView(it) }
        renderAppRows()

        addTop(
            body,
            note(
                "Cuộc gọi, Tin nhắn và Cài đặt không bao giờ bị chặn. " +
                    "App ngoài nhóm mạng xã hội ghi Ứng dụng đã cài."
            ),
            5,
        )
    }

    private fun renderAppRows() {
        val box = appRowsBox ?: return
        box.removeAllViews()
        val current = apps
        if (current == null) {
            val loading = Ui.row(this, Ui.tile(this, R.drawable.ic_refresh, Tone.NEUTRAL))
            loading.title("Đang đọc danh sách ứng dụng…")
            loading.subtitle("Chỉ đọc tên app đã cài trên máy")
            loading.setPadding(dp(13), dp(14), dp(13), dp(14))
            loading.background = Ui.rounded(Ui.SURFACE, 13, this, Ui.LINE)
            box.addView(loading.withBottomMargin(7))
            refreshAppCount()
            return
        }
        val query = appQuery.trim().lowercase()
        val shown = current.filter {
            query.isEmpty() ||
                it.label.lowercase().contains(query) ||
                it.packageName.lowercase().contains(query)
        }
        if (shown.isEmpty()) {
            val empty = Ui.label(this, "Không có ứng dụng nào khớp.", 12.5f, Ui.TEXT_3)
            empty.gravity = Gravity.CENTER
            empty.setPadding(0, dp(18), 0, dp(18))
            box.addView(empty)
        } else {
            shown.forEach { box.addView(appRow(it)) }
        }
        refreshAppCount()
    }

    private fun refreshAppCount() {
        val count = prefs.socialPackages.size
        appCountLabel?.text = if (apps == null) "Đang đọc…" else "$count đang chọn"
        actionHint?.text = if (count == 0) {
            "Có thể bỏ qua bước này."
        } else {
            "$count app sẽ bị khoá theo mặc định."
        }
    }

    private fun appRow(entry: AppEntry): View {
        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.setPadding(0, 0, 0, dp(7))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(11), dp(9), dp(11), dp(9))

        val icon = ImageView(this)
        icon.scaleType = ImageView.ScaleType.FIT_CENTER
        val drawable = iconFor(entry.packageName)
        if (drawable == null) {
            icon.setPadding(dp(9), dp(9), dp(9), dp(9))
            icon.imageTintList = ColorStateList.valueOf(Ui.TEXT_3)
            icon.setImageResource(R.drawable.ic_grid)
        } else {
            icon.setImageDrawable(drawable)
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(38), dp(38)))

        val labels = LinearLayout(this)
        labels.orientation = LinearLayout.VERTICAL
        labels.setPadding(dp(11), 0, 0, 0)
        val title = Ui.label(this, entry.label, 14f, Ui.TEXT, true)
        title.isSingleLine = true
        title.ellipsize = TextUtils.TruncateAt.END
        labels.addView(title)
        val subtitleText = if (Prefs.SOCIAL_LABELS.containsKey(entry.packageName)) {
            Apps.category(entry.packageName)
        } else {
            "Ứng dụng đã cài"
        }
        val subtitle = Ui.label(this, subtitleText, 12f, Ui.TEXT_3)
        subtitle.isSingleLine = true
        subtitle.ellipsize = TextUtils.TruncateAt.END
        labels.addView(subtitle)
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val toggle = UiToggle(this)
        val initially = prefs.socialPackages.contains(entry.packageName)
        toggle.setChecked(initially)
        fun paint(on: Boolean) {
            row.background = Ui.ripple(
                Ui.rounded(Ui.SURFACE, 13, this, if (on) Ui.ACCENT_LINE else Ui.LINE),
                0x12000000,
            )
        }
        paint(initially)
        toggle.onChange = { checked ->
            // Bỏ chặn một app sau khi đã hoàn tất thiết lập là nới luật → phải qua cổng 60 giây.
            val loosening = !checked && prefs.onboardingDone
            if (loosening) {
                if (!prefs.rulesUnlocked(System.currentTimeMillis(), SystemClock.elapsedRealtime())) {
                    toggle.setChecked(true, animate = true)
                }
                requireUnlock {
                    toggleApp(entry.packageName, false)
                    render()
                }
            } else {
                paint(checked)
                toggleApp(entry.packageName, checked)
            }
        }
        row.addView(toggle, LinearLayout.LayoutParams(dp(46), dp(26)).apply { leftMargin = dp(10) })
        row.isClickable = true
        row.isFocusable = true
        row.setOnClickListener { toggle.setChecked(!toggle.checked, animate = true, notify = true) }

        wrap.addView(row)
        return wrap
    }

    private fun toggleApp(packageName: String, checked: Boolean) {
        if (Prefs.SOCIAL_LABELS.containsKey(packageName)) {
            prefs.setSocialPackage(packageName, checked)
        } else {
            prefs.setExtraPackage(packageName, checked)
        }
        refreshAppCount()
    }

    private fun quotaStep(body: LinearLayout) {
        heading(
            body,
            "Bao nhiêu phút lướt\nmạng xã hội mỗi ngày?",
            "Một quỹ dùng chung cho cả nhóm app. Mặc định 60 phút, đổi sau vẫn được.",
        )
        if (prefs.onboardingDone) {
            val row = Ui.row(this, Ui.tile(this, R.drawable.ic_clock, Tone.ACCENT))
            row.title("Quỹ mạng xã hội")
            row.subtitle("${quota.limitMinutes} phút mỗi ngày")
            row.status("Đổi", Ui.TEXT_3)
            row.trailing(Ui.chevron(this))
            row.setPadding(dp(13), dp(12), dp(13), dp(12))
            row.background = Ui.ripple(Ui.rounded(Ui.SURFACE, 15, this, Ui.LINE), 0x12000000)
            row.isClickable = true
            row.isFocusable = true
            row.setOnClickListener { startActivity(Intent(this, QuotaActivity::class.java)) }
            body.addView(row.withBottomMargin(11))
            body.addView(note("Đổi mức ở màn Quỹ để áp dụng đúng quy tắc tăng và giảm."))
        } else {
            val chips = LinearLayout(this)
            chips.orientation = LinearLayout.VERTICAL
            chips.setPadding(0, dp(14), 0, 0)
            for (rowIndex in 0 until 2) {
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                for (column in 0 until 4) {
                    val minutes = 15 + (rowIndex * 4 + column) * 15
                    row.addView(quotaChip(minutes))
                }
                chips.addView(row)
            }
            body.addView(chips)
            addTop(body, note("Tăng mức phải chờ 0:00 hôm sau; giảm mức có hiệu lực ngay. Quỹ làm mới lúc 0:00 theo ngày trên máy."), 10)
        }

        val gym = Ui.row(this, Ui.tile(this, R.drawable.ic_dumbbell, Tone.ACCENT))
        gym.title("App tập gym")
        gym.subtitle(Apps.label(this, prefs.gymPackage))
        gym.status("Đổi", Ui.TEXT_2)
        gym.trailing(Ui.chevron(this))
        gym.setPadding(dp(13), dp(12), dp(13), dp(12))
        gym.background = Ui.ripple(Ui.rounded(Ui.SURFACE, 15, this, Ui.LINE), 0x12000000)
        gym.isClickable = true
        gym.isFocusable = true
        gym.setOnClickListener { startActivity(Intent(this, GymPickerActivity::class.java)) }
        body.addView(gym.withTopMargin(12))

        val hint = Ui.label(this, "Giữ app tập trên màn hình đủ 10 phút liên tục mới tính một buổi.", 12f, Ui.TEXT_3)
        hint.setPadding(0, dp(8), 0, 0)
        body.addView(hint)
    }

    private fun quotaChip(minutes: Int): View {
        val selected = quotaChoice == minutes
        val chip = Ui.label(this, "$minutes phút", 13f, if (selected) Color.WHITE else Ui.TEXT, true)
        chip.gravity = Gravity.CENTER
        chip.background = Ui.ripple(
            Ui.rounded(if (selected) Ui.ACCENT else Ui.SURFACE, 12, this, if (selected) Ui.ACCENT else Ui.LINE),
            0x14000000,
        )
        chip.isClickable = true
        chip.isFocusable = true
        chip.setOnClickListener {
            quotaChoice = minutes
            quota.applyNow(minutes)
            render()
        }
        return chip.withLayoutParams(
            LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                leftMargin = dp(4)
                rightMargin = dp(4)
                bottomMargin = dp(8)
            }
        )
    }

    private fun optionalStep(body: LinearLayout) {
        heading(
            body,
            "Hai thứ nên bật,\nnhưng không bắt buộc",
            "Bạn có thể bật sau ở Cài đặt, không mất gì.",
        )
        val pair = secure.load() != null
        val pc = Ui.row(this, Ui.tile(this, R.drawable.ic_desktop, Tone.ACCENT))
        pc.title("Kết nối máy tính")
        pc.subtitle("Đồng bộ lịch học và phiên focus với máy tính · không bắt buộc")
        pc.status(if (pair) "Đã ghép" else "Chưa ghép", if (pair) Ui.ACCENT else Ui.TEXT_3)
        pc.trailing(Ui.chevron(this))
        pc.setPadding(dp(13), dp(12), dp(13), dp(12))
        pc.background = Ui.ripple(Ui.rounded(Ui.SURFACE, 15, this, Ui.LINE), 0x12000000)
        pc.isClickable = true
        pc.isFocusable = true
        pc.setOnClickListener { startActivity(Intent(this, SetupActivity::class.java)) }
        body.addView(pc.withTopMargin(14).withBottomMargin(9))

        val supported = Build.VERSION.SDK_INT >= 27
        val adult = Ui.row(this, Ui.tile(this, R.drawable.ic_shield_alert, Tone.WARN))
        adult.title("Chặn trang người lớn")
        adult.subtitle(
            if (!supported) "Cần Android 8.1 trở lên"
            else if (prefs.adultFilterEnabled) "Đang lọc từng truy vấn DNS của máy"
            else "Lọc DNS suốt ngày đêm · cần Android 8.1+"
        )
        if (supported) {
            val toggle = UiToggle(this)
            toggle.setChecked(prefs.adultFilterEnabled)
            toggle.onChange = { checked -> setAdult(checked) }
            adult.trailing(toggle, 10)
        } else {
            adult.status("Không hỗ trợ", Ui.TEXT_3)
        }
        adult.setPadding(dp(13), dp(12), dp(13), dp(12))
        adult.background = Ui.rounded(Ui.SURFACE, 15, this, Ui.LINE)
        body.addView(adult)

        if (prefs.adultFilterEnabled) {
            addTop(
                body,
                note(
                    "Android đã cấp quyền VPN một lần. Đây là đường hầm chỉ mang truy vấn DNS, " +
                        "không phải VPN đi web; tắt được bằng một chạm."
                ),
                10,
            )
        } else {
            addTop(body, note("Cả hai đều tắt được bất cứ lúc nào, không ảnh hưởng tới quỹ hay màn khoá."), 10)
        }
    }

    private fun setAdult(checked: Boolean) {
        if (!checked) {
            prefs.adultFilterEnabled = false
            stopService(Intent(this, AdultDnsVpnService::class.java))
            render()
            return
        }
        val consent = try {
            VpnService.prepare(this)
        } catch (error: Exception) {
            null
        }
        if (consent != null) startActivityForResult(consent, VPN_REQUEST) else enableAdult()
    }

    private fun enableAdult() {
        prefs.adultFilterEnabled = true
        try {
            startService(Intent(this, AdultDnsVpnService::class.java))
        } catch (error: Exception) {
            prefs.adultFilterEnabled = false
            Toast.makeText(this, "Không mở được bộ lọc trên máy này.", Toast.LENGTH_LONG).show()
        }
        render()
    }

    private fun summary(body: LinearLayout) {
        heading(body, "Sẵn sàng giữ luật?", "Kiểm lại một lần. Mọi thứ đổi được sau trong Cài đặt.")
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = Ui.rounded(Ui.SURFACE, 16, this, Ui.LINE)
        card.elevation = dp(1).toFloat()
        card.setPadding(dp(14), dp(3), dp(14), dp(3))

        val granted = listOf(overlayGranted(), accessibilityGranted(), notificationsGranted()).count { it }
        addSummaryRow(card, "Quyền", "$granted/3 đã cấp", false, 1)
        addSummaryRow(
            card, "App đang chặn",
            if (prefs.socialPackages.isEmpty()) "Chưa chọn" else "${prefs.socialPackages.size} app",
            prefs.socialPackages.isEmpty(), 2,
        )
        addSummaryRow(card, "Quỹ mỗi ngày", "${quota.limitMinutes} phút", false, 3)
        addSummaryRow(card, "App tập gym", Apps.label(this, prefs.gymPackage), false, 3)
        val pair = secure.load() != null
        addSummaryRow(
            card, "Máy tính",
            if (pair) "Đã ghép" else "Chưa ghép · chỉ để đồng bộ",
            false, 4,
        )
        addSummaryRow(card, "Lọc 18+", if (prefs.adultFilterEnabled) "Bật" else "Tắt", !prefs.adultFilterEnabled, 4)
        addTop(body, card, 16)

        if (prefs.socialPackages.isEmpty()) {
            addTop(body, note("Chưa có app nào bị khoá. App vẫn chạy, nhưng chưa có gì để giữ.", warn = true), 12)
        } else {
            addTop(body, note("Quyền và danh sách chặn nằm trên máy bạn. Không có tài khoản, không máy chủ."), 12)
        }
    }

    private fun addSummaryRow(
        card: LinearLayout,
        label: String,
        value: String,
        off: Boolean,
        target: Int,
    ) {
        if (card.childCount > 0) {
            val line = View(this)
            line.setBackgroundColor(Ui.LINE_SOFT)
            card.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        }
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(11), 0, dp(11))
        val left = Ui.label(this, label, 13f, Ui.TEXT_2)
        row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val right = Ui.label(this, value, 13f, if (off) Ui.TEXT_3 else Ui.TEXT, true)
        right.gravity = Gravity.END
        row.addView(right)
        val edit = Ui.label(this, "Sửa", 12f, Ui.ACCENT, true)
        edit.setPadding(dp(10), dp(8), dp(2), dp(8))
        edit.isClickable = true
        edit.isFocusable = true
        edit.setOnClickListener { step = target; render() }
        row.addView(edit)
        card.addView(row)
    }

    private fun done(body: LinearLayout) {
        body.gravity = Gravity.CENTER_HORIZONTAL
        val circle = LinearLayout(this)
        circle.background = Ui.rounded(Ui.ACCENT_SOFT, 999, this, Ui.ACCENT_LINE)
        circle.gravity = Gravity.CENTER
        circle.addView(Ui.icon(this, R.drawable.ic_check, Ui.ACCENT, 30))
        body.addView(circle, LinearLayout.LayoutParams(dp(64), dp(64)).apply { bottomMargin = dp(16) })

        val title = Ui.label(this, "Thiết lập xong", 28f, Ui.TEXT, true)
        title.letterSpacing = -0.01f
        title.gravity = Gravity.CENTER
        body.addView(title)

        val lede = Ui.label(
            this,
            "Bạn có thể đổi mọi thứ trong Cài đặt. Tắt bảo vệ cần đọc cảnh báo, gõ cam kết và chờ 60 giây.",
            13.5f, Ui.TEXT_2,
        )
        lede.setLineSpacing(0f, 1.55f)
        lede.gravity = Gravity.CENTER
        lede.setPadding(dp(22), dp(10), dp(22), dp(18))
        body.addView(lede)

        val chips = LinearLayout(this)
        chips.orientation = LinearLayout.HORIZONTAL
        chips.gravity = Gravity.CENTER
        chips.addView(Ui.pill(this, "${prefs.socialPackages.size} app bị khoá", Tone.ACCENT, false))
        chips.addView(Ui.pill(this, "${quota.limitMinutes} phút mỗi ngày", Tone.ACCENT, false), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = dp(6) })
        if (prefs.adultFilterEnabled) {
            chips.addView(Ui.pill(this, "18+ đang bật", Tone.ACCENT, false), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(6) })
        }
        body.addView(chips)
    }

    private fun actions(): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, dp(9), 0, 0)

        val line = View(this)
        line.setBackgroundColor(Ui.LINE_SOFT)
        box.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { bottomMargin = dp(9) })

        val label: String
        val hint: String
        val warn: Boolean
        val primary: () -> Unit
        when (step) {
            0 -> {
                label = "Bắt đầu"; hint = "Khoảng 2 phút · 5 bước"; warn = false
                primary = { step = 1; render() }
            }
            1 -> {
                val ready = overlayGranted() && accessibilityGranted()
                label = "Tiếp tục"
                hint = if (ready) "Đã đủ hai quyền cốt lõi." else "Cần cấp đủ Hiển thị trên app khác và Trợ năng."
                warn = !ready
                primary = { if (ready) { step = 2; render() } }
            }
            2 -> {
                label = "Tiếp tục"
                hint = if (prefs.socialPackages.isEmpty()) "Có thể bỏ qua bước này."
                else "${prefs.socialPackages.size} app sẽ bị khoá theo mặc định."
                warn = false
                primary = { step = 3; render() }
            }
            3 -> {
                label = "Tiếp tục"; hint = "Quỹ ${quota.limitMinutes} phút mỗi ngày."; warn = false
                primary = { step = 4; render() }
            }
            4 -> {
                label = "Tiếp tục"; hint = "Bỏ qua nếu bạn muốn thiết lập sau."; warn = false
                primary = { step = 5; render() }
            }
            5 -> {
                label = "Hoàn tất"; hint = "Có thể xem lại từ Cài đặt."; warn = false
                primary = {
                    prefs.onboardingDone = true
                    step = 6
                    render()
                }
            }
            else -> {
                label = "Vào ứng dụng"; hint = "Bước tiếp theo trong app là tab Hôm nay."; warn = false
                primary = { finish() }
            }
        }

        val button = Ui.button(this, label, Variant.PRIMARY, big = true)
        button.isEnabled = !(step == 1 && !(overlayGranted() && accessibilityGranted()))
        button.setOnClickListener { primary() }
        box.addView(button, Ui.match(this))

        actionHint = Ui.label(this, hint, 12f, if (warn) Ui.WARN else Ui.TEXT_3)
        actionHint?.gravity = Gravity.CENTER
        actionHint?.setPadding(0, dp(8), 0, 0)
        box.addView(actionHint)

        if (step == 6) {
            val review = Ui.button(this, "Xem lại thiết lập", Variant.GHOST)
            review.setOnClickListener { step = 5; render() }
            box.addView(review, Ui.match(this, topMarginDp = 8))
        }
        return box
    }

    private fun loadApps() {
        val manager = packageManager
        val skip = Apps.neverBlocked(this)
        executor.execute {
            val collected = mutableListOf<Pair<String, String>>()
            try {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val resolved = manager.queryIntentActivities(intent, 0)
                val seen = mutableSetOf<String>()
                for (info in resolved) {
                    val appPackage = info.activityInfo.packageName
                    if (appPackage in skip || !seen.add(appPackage)) continue
                    val label = try {
                        info.loadLabel(manager).toString()
                    } catch (error: Exception) {
                        appPackage
                    }
                    collected.add(appPackage to label)
                }
            } catch (error: Exception) {
            }
            val sorted = collected.sortedWith(
                compareByDescending<Pair<String, String>> { Prefs.SOCIAL_LABELS.containsKey(it.first) }
                    .thenBy { it.second.lowercase() }
            )
            runOnUiThread {
                if (destroyed) return@runOnUiThread
                apps = sorted.map { AppEntry(it.second, it.first) }
                render()
            }
        }
    }

    private fun iconFor(packageName: String): Drawable? {
        icons.get(packageName)?.let { return it.newDrawable(resources).mutate() }
        val drawable = Apps.icon(this, packageName) ?: return null
        drawable.constantState?.let { icons.put(packageName, it) }
        return drawable.mutate()
    }

    private fun overlayGranted(): Boolean = Settings.canDrawOverlays(this)

    private fun accessibilityGranted(): Boolean = Apps.accessibilityEnabled(this)

    private fun notificationsGranted(): Boolean =
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()

    private fun dp(value: Int): Int = Ui.dp(this, value)

    private fun addTop(parent: LinearLayout, child: View, margin: Int) {
        val params = child.layoutParams as? LinearLayout.LayoutParams
            ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.topMargin = dp(margin)
        parent.addView(child, params)
    }

    private fun View.withBottomMargin(value: Int): View {
        val params = layoutParams as? LinearLayout.LayoutParams ?: LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = dp(value)
        layoutParams = params
        return this
    }

    private fun View.withTopMargin(value: Int): View {
        val params = layoutParams as? LinearLayout.LayoutParams ?: LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = dp(value)
        layoutParams = params
        return this
    }

    private fun View.withLayoutParams(params: LinearLayout.LayoutParams): View {
        layoutParams = params
        return this
    }

    companion object {
        private const val VPN_REQUEST = 81
        private const val NOTIFY_REQUEST = 83
        private const val UNLOCK_REQUEST = 84
    }
}
