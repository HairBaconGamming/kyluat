package com.cainghien.companion

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.net.InetAddress
import java.util.concurrent.Executors

class SetupActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var secure: SecureStore
    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var container: LinearLayout
    private var step = 1

    private var codeInput: UiCodeInput? = null
    private var connectButton: UiButton? = null
    private var statusLine: TextView? = null
    private var hostField: EditText? = null
    private var discoveredGroup: UiGroup? = null
    private val found = mutableListOf<PcService>()
    private var selectedPc: PcService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        secure = SecureStore(this)
        Ui.chrome(this)

        val page = Ui.screen(this)
        container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        page.addView(container, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        setContentView(Ui.scroll(page))

        step = if (secure.load() != null) 2 else 1
        render()
        PcDiscovery.browse(
            this,
            onResult = { list ->
                found.clear()
                found.addAll(list)
                renderDiscovered()
            },
            onError = { message ->
                if (found.isEmpty()) renderDiscovered(message)
            },
        )
    }

    private fun render() {
        container.removeAllViews()
        when (step) {
            1 -> renderConnect()
            2 -> renderPermissions()
            else -> renderDone()
        }
    }

    private fun renderConnect(fallbackNote: String? = null) {
        container.addView(
            Ui.backHeader(this, "Kết nối máy tính", "Bước 1 / 3 · dùng chung Wi-Fi") { finish() }
        )
        container.addView(Ui.steps(this, 3, 1))

        val hero = Ui.hero(this)
        hero.gravity = Gravity.CENTER_HORIZONTAL
        val glyph = FrameLayout(this)
        glyph.background = Ui.rounded(Ui.ACCENT_SOFT, 999, this, Ui.ACCENT_LINE)
        glyph.addView(
            Ui.icon(this, R.drawable.ic_scan, Ui.ACCENT, 26),
            FrameLayout.LayoutParams(Ui.dp(this, 26), Ui.dp(this, 26), Gravity.CENTER)
        )
        hero.addView(glyph, LinearLayout.LayoutParams(Ui.dp(this, 58), Ui.dp(this, 58)))
        val title = Ui.label(this, "Quét mã trên máy tính", 17f, Ui.TEXT, true)
        title.gravity = Gravity.CENTER
        hero.addView(title, Ui.match(this, topMarginDp = 11))
        val sub = Ui.label(
            this,
            "Trên máy tính: mở app Kỷ luật → Bảo vệ → Tạo mã kết nối, rồi đưa camera vào mã QR.",
            12.5f,
            Ui.TEXT_3
        )
        sub.gravity = Gravity.CENTER
        hero.addView(sub, Ui.match(this, topMarginDp = 5))
        val scan = Ui.button(this, "Mở máy quét", Variant.PRIMARY, big = true)
        scan.setOnClickListener { startQr() }
        hero.addView(scan, Ui.match(this, topMarginDp = 13))
        container.addView(hero)

        container.addView(Ui.dividerText(this, "hoặc nhập mã"))

        val card = Ui.card(this)
        card.addView(Ui.eyebrow(this, "Mã 6 số hiện trên máy tính"))
        val input = Ui.codeInput(this, 6) { code ->
            connectButton?.isEnabled = code.length == 6
        }
        codeInput = input
        card.addView(input, Ui.match(this, topMarginDp = 11))
        card.addView(
            Ui.label(this, "Mã dùng một lần · hết hạn sau 5 phút", 11f, Ui.TEXT_3),
            Ui.match(this, topMarginDp = 8)
        )
        val status = Ui.label(this, fallbackNote ?: "", 12f, Ui.TEXT_2)
        statusLine = status
        card.addView(status, Ui.match(this, topMarginDp = 7))
        val connect = Ui.button(this, "Kết nối", Variant.PRIMARY, big = true)
        connect.isEnabled = false
        connect.setOnClickListener { connectWithCode() }
        connectButton = connect
        card.addView(connect, Ui.match(this, topMarginDp = 11))
        container.addView(card)

        container.addView(Ui.section(this, "Máy tính tìm thấy"))
        val group = Ui.group(this)
        discoveredGroup = group
        container.addView(group)
        renderDiscovered()

        container.addView(Ui.section(this, "Nâng cao"))
        val advanced = Ui.card(this)
        advanced.addView(Ui.label(this, "Địa chỉ máy tính, nếu không tự tìm thấy", 13f, Ui.TEXT_2))
        val field = Ui.field(this, "192.168.1.10")
        field.setText(secure.load()?.baseUrl?.removePrefix("http://")?.substringBefore(":") ?: "")
        hostField = field
        advanced.addView(field, Ui.match(this, topMarginDp = 9))
        advanced.addView(
            Ui.note(this, "Cổng mặc định 8766. Chỉ nhận địa chỉ trong mạng nội bộ."),
            Ui.match(this, topMarginDp = 9)
        )
        container.addView(advanced)

        val skip = Ui.button(this, "Bỏ qua, chỉ dùng trên điện thoại", Variant.QUIET)
        skip.setOnClickListener {
            step = 2
            render()
        }
        container.addView(skip, Ui.match(this, topMarginDp = 10))
    }

    private fun renderDiscovered(note: String? = null) {
        val group = discoveredGroup ?: return
        group.removeAllViews()
        if (found.isEmpty()) {
            val row = Ui.row(this, Ui.tile(this, R.drawable.ic_desktop, Tone.NEUTRAL))
            row.title(if (note == null) "Đang tìm trong mạng…" else "Không thấy máy tính")
            row.subtitle(note ?: "Điện thoại và máy tính phải dùng chung Wi-Fi")
            group.addView(row)
            return
        }
        found.forEach { service ->
            val chosen = selectedPc?.host == service.host && selectedPc?.port == service.port
            val row = Ui.row(this, Ui.tile(this, R.drawable.ic_desktop, if (chosen) Tone.ACCENT else Tone.NEUTRAL))
            row.title(service.name)
            row.subtitle("${service.host}:${service.port}")
            if (chosen) row.status("Đang chọn", Ui.ACCENT)
            row.trailing(Ui.chevron(this))
            row.makeClickable {
                selectedPc = service
                hostField?.setText("")
                statusLine?.text = "Sẽ kết nối tới ${service.host}"
                statusLine?.setTextColor(Ui.TEXT_2)
                renderDiscovered()
            }
            group.addView(row)
        }
    }

    private fun renderPermissions() {
        container.addView(
            Ui.backHeader(this, "Cấp quyền", "Bước 2 / 3 · cần 2 quyền bắt buộc") {
                step = 1
                render()
            }
        )
        container.addView(Ui.steps(this, 3, 2))

        val overlay = Settings.canDrawOverlays(this)
        val accessibility = accessibilityEnabled()
        val notifications = notificationsEnabled()

        val group = Ui.group(this)
        val overlayRow = Ui.row(this, Ui.tile(this, R.drawable.ic_layers, if (overlay) Tone.ACCENT else Tone.WARN))
        overlayRow.title("Hiển thị trên ứng dụng khác")
        overlayRow.subtitle("Phủ màn hình cảnh báo khi bạn mở app bị chặn")
        overlayRow.status(if (overlay) "Đã cấp" else "Cấp quyền", if (overlay) Ui.ACCENT else Ui.DANGER)
        overlayRow.trailing(Ui.chevron(this))
        overlayRow.makeClickable {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        group.addView(overlayRow)

        val accessRow = Ui.row(this, Ui.tile(this, R.drawable.ic_accessibility, if (accessibility) Tone.ACCENT else Tone.WARN))
        accessRow.title("Trợ năng")
        accessRow.subtitle("Chỉ đọc tên ứng dụng đang mở, không đọc nội dung")
        accessRow.status(if (accessibility) "Đã bật" else "Bật", if (accessibility) Ui.ACCENT else Ui.DANGER)
        accessRow.trailing(Ui.chevron(this))
        accessRow.makeClickable { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        group.addView(accessRow)

        val notifyRow = Ui.row(this, Ui.tile(this, R.drawable.ic_bell, Tone.NEUTRAL))
        notifyRow.title("Thông báo")
        notifyRow.subtitle("Đồng hồ buổi tập và giờ nghỉ")
        notifyRow.status(if (notifications) "Đã bật" else "Tùy chọn", if (notifications) Ui.ACCENT else Ui.TEXT_3)
        notifyRow.trailing(Ui.chevron(this))
        notifyRow.makeClickable {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 33)
            } else {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                )
            }
        }
        group.addView(notifyRow)
        container.addView(group)

        if (!accessibility) {
            container.addView(Ui.restrictedCard(this) { Apps.openAppInfo(this) })
        } else {
            val note = Ui.card(this, padDp = 13)
            note.addView(
                Ui.note(
                    this,
                    "Trợ năng chỉ đọc tên ứng dụng đang mở. Android luôn cho bạn tắt lại trong Cài đặt.",
                    R.drawable.ic_info,
                )
            )
            container.addView(note)
        }

        val next = Ui.button(this, "Tiếp tục", Variant.PRIMARY, big = true)
        next.isEnabled = overlay && accessibility
        next.setOnClickListener {
            step = 3
            render()
        }
        container.addView(next, Ui.match(this, topMarginDp = 4))

        val later = Ui.button(this, "Cấp sau, quay lại trang chủ", Variant.QUIET)
        later.setOnClickListener { finish() }
        container.addView(later, Ui.match(this, topMarginDp = 8))
    }

    private fun renderDone() {
        container.addView(
            Ui.backHeader(this, "Hoàn tất", "Bước 3 / 3 · Kỷ luật đã sẵn sàng") {
                step = 2
                render()
            }
        )
        container.addView(Ui.steps(this, 3, 3))

        val pair = secure.load()
        val hero = Ui.hero(this)
        hero.gravity = Gravity.CENTER_HORIZONTAL
        val glyph = FrameLayout(this)
        glyph.background = Ui.rounded(Ui.ACCENT_SOFT, 999, this, Ui.ACCENT_LINE)
        glyph.addView(
            Ui.icon(this, R.drawable.ic_check, Ui.ACCENT, 26),
            FrameLayout.LayoutParams(Ui.dp(this, 26), Ui.dp(this, 26), Gravity.CENTER)
        )
        hero.addView(glyph, LinearLayout.LayoutParams(Ui.dp(this, 58), Ui.dp(this, 58)))
        val title = Ui.label(
            this,
            if (pair == null) "Đã đủ quyền" else "Đã kết nối và đủ quyền",
            17f,
            Ui.TEXT,
            true,
        )
        title.gravity = Gravity.CENTER
        hero.addView(title, Ui.match(this, topMarginDp = 11))
        val sub = Ui.label(
            this,
            when {
                !prefs.protectionEnabled -> "Bảo vệ đang tắt. Bật lại trong Cài đặt khi bạn sẵn sàng."
                pair == null ->
                    "Quỹ ${QuotaManager(prefs).limitMinutes} phút và phiên focus chạy ngay trên điện thoại, không cần máy tính."
                else ->
                    "Quỹ ${QuotaManager(prefs).limitMinutes} phút và phiên focus đã hoạt động trên điện thoại này."
            },
            12.5f,
            Ui.TEXT_3
        )
        sub.gravity = Gravity.CENTER
        hero.addView(sub, Ui.match(this, topMarginDp = 5))
        container.addView(hero)

        val group = Ui.group(this)
        val pcRow = Ui.row(this, Ui.tile(this, R.drawable.ic_desktop, if (pair == null) Tone.NEUTRAL else Tone.ACCENT))
        pcRow.title("Máy tính")
        pcRow.subtitle(pair?.baseUrl?.removePrefix("http://") ?: "Chưa kết nối · chỉ cần khi đồng bộ lịch")
        pcRow.status(if (pair == null) "Tùy chọn" else "Đã nối", if (pair == null) Ui.TEXT_3 else Ui.ACCENT)
        group.addView(pcRow)
        val shieldRow = Ui.row(this, Ui.tile(this, R.drawable.ic_shield_check, Tone.ACCENT))
        shieldRow.title("Bảo vệ điện thoại")
        shieldRow.subtitle(if (prefs.protectionEnabled) "Đang bật" else "Đang tắt")
        shieldRow.status(
            if (prefs.protectionEnabled) "OK" else "Tắt",
            if (prefs.protectionEnabled) Ui.ACCENT else Ui.DANGER
        )
        group.addView(shieldRow)
        container.addView(group)

        val home = Ui.button(this, "Về trang hôm nay", Variant.PRIMARY, big = true)
        home.setOnClickListener { finish() }
        container.addView(home, Ui.match(this, topMarginDp = 4))

        val again = Ui.button(
            this,
            if (pair == null) "Kết nối máy tính để đồng bộ" else "Kết nối máy tính khác",
            Variant.QUIET,
        )
        again.setOnClickListener {
            if (pair != null) {
                secure.clear()
                found.clear()
                selectedPc = null
            }
            step = 1
            render()
        }
        container.addView(again, Ui.match(this, topMarginDp = 8))
    }

    private fun setStatus(text: String, error: Boolean) {
        statusLine?.text = text
        statusLine?.setTextColor(if (error) Ui.DANGER else Ui.TEXT_2)
    }

    private fun startQr() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 21)
            return
        }
        startActivityForResult(Intent(this, ScanActivity::class.java), ScanActivity.REQUEST)
    }

    private fun connectWithCode() {
        val code = codeInput?.code() ?: ""
        if (!code.matches(Regex("\\d{6}"))) {
            setStatus("Mã phải gồm đúng 6 chữ số.", true)
            return
        }
        val manual = hostField?.text?.toString()?.trim() ?: ""
        if (manual.isNotEmpty()) {
            val cleaned = manual.removePrefix("http://").removePrefix("https://").trimEnd('/')
            val host = cleaned.substringBefore(":").substringBefore("/")
            val portText = if (cleaned.contains(":")) cleaned.substringAfter(":").substringBefore("/") else "8766"
            pairWith(host, portText.toIntOrNull() ?: 8766, code)
            return
        }
        val picked = selectedPc
        if (picked != null) {
            pairWith(picked.host, picked.port, code)
            return
        }
        setStatus("Đang tìm máy tính trong mạng…", false)
        PcDiscovery.find(
            this,
            onFound = { host, port -> pairWith(host, port, code) },
            onError = { message -> setStatus(message, true) },
        )
    }

    private fun handleQr(contents: String) {
        try {
            val uri = Uri.parse(contents)
            if (uri.scheme != "kyluat" || uri.authority != "pair") {
                setStatus("Mã QR không phải của Kỷ luật.", true)
                return
            }
            val host = uri.getQueryParameter("host") ?: ""
            val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 8766
            val code = uri.getQueryParameter("code") ?: ""
            if (!code.matches(Regex("\\d{6}"))) {
                setStatus("Mã QR thiếu mã kết nối.", true)
                return
            }
            pairWith(host, port, code)
        } catch (error: Exception) {
            setStatus("Không đọc được mã QR.", true)
        }
    }

    private fun pairWith(host: String, port: Int, code: String) {
        if (!isPrivateHost(host)) {
            setStatus("Địa chỉ máy tính phải nằm trong mạng nội bộ.", true)
            return
        }
        setStatus("Đang kết nối tới $host…", false)
        executor.execute {
            try {
                val base = "http://$host:$port"
                val token = Api.pair(base, code, prefs.deviceName)
                val saved = secure.save(DevicePair(base, token))
                runOnUiThread {
                    if (saved) {
                        Toast.makeText(this, "Kết nối thành công", Toast.LENGTH_SHORT).show()
                        step = 2
                        render()
                    } else {
                        setStatus("Không lưu được kết nối an toàn trên máy này.", true)
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    setStatus("Không kết nối được: ${error.message ?: "kiểm tra Wi-Fi và mã"}", true)
                    codeInput?.clear()
                    connectButton?.isEnabled = false
                }
            }
        }
    }

    private fun isPrivateHost(host: String): Boolean {
        return try {
            val address = InetAddress.getByName(host)
            address.isSiteLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress
        } catch (error: Exception) {
            false
        }
    }

    private fun accessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val component = "$packageName/${FocusAccessibilityService::class.java.name}"
        return enabled.split(':').any { it.equals(component, true) }
    }

    private fun notificationsEnabled(): Boolean {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        return manager.areNotificationsEnabled()
    }

    override fun onResume() {
        super.onResume()
        if (step >= 2) render()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 21 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startQr()
        } else if (requestCode == 33 && step >= 2) {
            render()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != ScanActivity.REQUEST || resultCode != RESULT_OK) return
        val contents = data?.getStringExtra(ScanActivity.EXTRA_CONTENTS)
        if (contents.isNullOrEmpty()) return
        if (step != 1) {
            step = 1
            render()
        }
        handleQr(contents)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
