package com.cainghien.companion

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

internal fun hasManualSession(sessionLabel: String?): Boolean = !sessionLabel.isNullOrBlank()

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var quota: QuotaManager
    private lateinit var secure: SecureStore
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val updateExecutor = Executors.newSingleThreadExecutor()

    private lateinit var nav: UiNav
    private val pages = mutableListOf<ScrollView>()
    private var summary: Summary? = null
    private var selectedMinutes = 25
    private var sessionEndElapsed = 0L
    private var sessionTotalSeconds = 0
    private var tickCount = 0

    private lateinit var setupCard: LinearLayout
    private lateinit var setupText: TextView
    private lateinit var setupButton: UiButton
    private lateinit var dateLabel: TextView
    private lateinit var statusPill: UiPill
    private lateinit var heroEyebrow: TextView
    private lateinit var heroTime: TextView
    private lateinit var heroCaption: TextView
    private lateinit var heroRing: RingStack
    private lateinit var idleBox: LinearLayout
    private lateinit var activeBox: LinearLayout
    private lateinit var sessionBar: UiBar
    private lateinit var sessionButtons: LinearLayout
    private lateinit var sessionNote: TextView
    private lateinit var startButton: UiButton
    private lateinit var stopButton: UiButton
    private lateinit var cancelButton: UiButton
    private lateinit var quotaRing: RingStack
    private lateinit var quotaSub: TextView
    private lateinit var quotaPending: TextView
    private lateinit var quotaBonus: TextView
    private lateinit var studyTile: UiStat
    private lateinit var gymTile: UiStat
    private lateinit var streakTile: UiStat
    private lateinit var studyRow: UiRow
    private lateinit var gymRow: UiRow
    private lateinit var blockRow: UiRow
    private lateinit var protectQuotaRow: UiRow

    private lateinit var protectCount: UiPill
    private lateinit var protectGroup: UiGroup
    private lateinit var adultRow: UiRow
    private lateinit var adultToggle: UiToggle
    private lateinit var visualRow: UiRow
    private lateinit var visualToggle: UiToggle
    private val socialToggles = mutableMapOf<String, UiToggle>()
    private val protectPackages = mutableListOf<String>()
    private var protectSignature = ""

    private lateinit var settingsPill: UiPill
    private lateinit var pcRow: UiRow
    private lateinit var syncRow: UiRow
    private lateinit var overlayRow: UiRow
    private lateinit var accessibilityRow: UiRow
    private var restrictedCard: View? = null
    private lateinit var notificationRow: UiRow
    private lateinit var gymSettingRow: UiRow
    private lateinit var quotaSettingRow: UiRow
    private lateinit var studyRateRow: UiRow
    private lateinit var rulesRow: UiRow
    private lateinit var protectionRow: UiRow
    private lateinit var protectionToggle: UiToggle
    private lateinit var guardRow: UiRow
    private lateinit var guardToggle: UiToggle
    private lateinit var autoUpdateRow: UiRow
    private lateinit var autoUpdateToggle: UiToggle
    private lateinit var checkUpdateRow: UiRow
    private lateinit var versionRow: UiRow
    private var updateBusy = false
    private var pendingRelease: UpdateChecker.Release? = null
    private var pendingUnlockAction: (() -> Unit)? = null
    private val offerUpdateRunnable = Runnable { maybeOfferUpdate() }
    private val appVersion: String by lazy {
        try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (error: Exception) {
            "?"
        }
    }
    private val appVersionLabel: String by lazy {
        val code = try {
            val info = packageManager.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        } catch (error: Exception) {
            0L
        }
        "$appVersion ($code)"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        quota = QuotaManager(prefs)
        secure = SecureStore(this)
        Ui.chrome(this)

        val untouched = prefs.looksUntouched() && secure.load() == null
        if (!prefs.onboardingMigrated) {
            // Bản cài đã dùng từ trước 3.9 không bị đẩy vào wizard sau khi cập nhật.
            prefs.onboardingMigrated = true
            if (!untouched) prefs.onboardingDone = true
        }
        val showSetup = !prefs.onboardingDone

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)
        val content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        pages.add(buildHome())
        pages.add(buildProtect())
        pages.add(buildSettings())
        pages.forEach { content.addView(it) }
        nav = Ui.nav(
            this,
            listOf(
                "Hôm nay" to R.drawable.ic_home,
                "Bảo vệ" to R.drawable.ic_shield,
                "Cài đặt" to R.drawable.ic_gear,
            ),
            0,
        ) { index -> selectTab(index) }
        root.addView(nav, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        setContentView(root)
        selectTab(0)

        if (showSetup) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        } else if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 51)
        }
        handler.post(ticker)
        refreshNetwork()
        refreshUpdateRows()
        handler.postDelayed({ showCrashLog() }, 700)
        handler.postDelayed({ maybeCheckUpdate() }, 2500)
    }

    private fun selectTab(index: Int) {
        pages.forEachIndexed { position, page ->
            page.visibility = if (position == index) View.VISIBLE else View.GONE
        }
        nav.highlight(index)
        refreshAll()
    }

    private fun buildHome(): ScrollView {
        val page = Ui.screen(this)
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 12))
        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        dateLabel = Ui.eyebrow(this, todayLabel())
        titles.addView(dateLabel)
        val title = Ui.label(this, "Hôm nay", 23f, Ui.TEXT, true)
        title.letterSpacing = -0.01f
        title.setPadding(0, Ui.dp(this, 3), 0, 0)
        titles.addView(title)
        head.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        statusPill = Ui.pill(this, "Đang kiểm tra…", Tone.NEUTRAL)
        head.addView(statusPill)
        page.addView(head)

        setupCard = LinearLayout(this)
        setupCard.orientation = LinearLayout.VERTICAL
        setupCard.background = Ui.rounded(Ui.WARN_SOFT, 14, this, Ui.WARN_LINE)
        setupCard.setPadding(Ui.dp(this, 13), Ui.dp(this, 12), Ui.dp(this, 13), Ui.dp(this, 12))
        val setupHead = LinearLayout(this)
        setupHead.orientation = LinearLayout.HORIZONTAL
        setupHead.addView(
            Ui.icon(this, R.drawable.ic_shield_alert, Ui.WARN, 18),
            LinearLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18)).apply {
                rightMargin = Ui.dp(this@MainActivity, 9)
                topMargin = Ui.dp(this@MainActivity, 1)
            }
        )
        setupText = Ui.label(this, "", 13f, Ui.TEXT, true)
        setupHead.addView(setupText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        setupCard.addView(setupHead)
        setupButton = Ui.button(this, "Hoàn tất thiết lập", Variant.PRIMARY)
        setupButton.setOnClickListener {
            if (prefs.protectionEnabled || permissionsReady()) {
                startActivity(Intent(this, SetupActivity::class.java))
            } else {
                prefs.protectionEnabled = true
                Toast.makeText(this, "Đã bật bảo vệ", Toast.LENGTH_SHORT).show()
                refreshAll()
            }
        }
        setupCard.addView(setupButton, Ui.match(this, topMarginDp = 10))
        page.addView(setupCard, Ui.match(this, bottomMarginDp = 11))

        val hero = Ui.hero(this)
        heroRing = Ui.ringStack(this, 74, 7f)
        heroRing.setValueSize(16f)
        val heroTop = LinearLayout(this)
        heroTop.orientation = LinearLayout.HORIZONTAL
        val heroText = LinearLayout(this)
        heroText.orientation = LinearLayout.VERTICAL
        heroEyebrow = Ui.eyebrow(this, "Tập trung")
        heroText.addView(heroEyebrow)
        heroTime = Ui.label(this, "25:00", 44f, Ui.TEXT, true)
        heroTime.letterSpacing = -0.04f
        heroTime.setPadding(0, Ui.dp(this, 5), 0, 0)
        heroText.addView(heroTime)
        heroCaption = Ui.label(this, "", 12f, Ui.TEXT_3)
        heroCaption.setPadding(0, Ui.dp(this, 4), 0, 0)
        heroText.addView(heroCaption)
        heroTop.addView(heroText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        heroTop.addView(heroRing)
        heroTop.gravity = Gravity.CENTER_VERTICAL
        hero.addView(heroTop)

        idleBox = LinearLayout(this)
        idleBox.orientation = LinearLayout.VERTICAL
        val segmented = Ui.segmented(this, listOf("25 phút", "45 phút", "60 phút"), 0) { index ->
            selectedMinutes = listOf(25, 45, 60)[index]
            refreshAll()
        }
        idleBox.addView(segmented, Ui.match(this, topMarginDp = 13))
        startButton = Ui.button(this, "Bắt đầu phiên", Variant.PRIMARY, big = true)
        startButton.setOnClickListener { startSession() }
        idleBox.addView(startButton, Ui.match(this, topMarginDp = 10))
        hero.addView(idleBox)

        activeBox = LinearLayout(this)
        activeBox.orientation = LinearLayout.VERTICAL
        activeBox.visibility = View.GONE
        sessionBar = UiBar(this, 6)
        activeBox.addView(sessionBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 6)
        ).apply { topMargin = Ui.dp(this@MainActivity, 14) })
        sessionButtons = LinearLayout(this)
        sessionButtons.orientation = LinearLayout.HORIZONTAL
        stopButton = Ui.button(this, "Dừng sớm", Variant.GHOST)
        stopButton.setOnClickListener { sessionAction("stop") }
        cancelButton = Ui.button(this, "Hủy yêu cầu", Variant.QUIET)
        cancelButton.setOnClickListener { sessionAction("cancel_stop") }
        sessionButtons.addView(stopButton, Ui.weight(this, 1f))
        sessionButtons.addView(cancelButton, Ui.weight(this, 1f, leftMarginDp = 8))
        activeBox.addView(sessionButtons, Ui.match(this, topMarginDp = 11))
        sessionNote = Ui.label(this, "Dừng sớm vẫn khóa đến hết thời gian chờ đã cam kết.", 11f, Ui.TEXT_3)
        sessionNote.gravity = Gravity.CENTER
        activeBox.addView(sessionNote, Ui.match(this, topMarginDp = 8))
        hero.addView(activeBox)
        page.addView(hero)

        val quotaCard = Ui.card(this)
        val quotaRow = LinearLayout(this)
        quotaRow.orientation = LinearLayout.HORIZONTAL
        quotaRow.gravity = Gravity.CENTER_VERTICAL
        quotaRing = Ui.ringStack(this, 74, 7f)
        quotaRing.setValueSize(19f)
        quotaRow.addView(quotaRing)
        val quotaText = LinearLayout(this)
        quotaText.orientation = LinearLayout.VERTICAL
        quotaText.setPadding(Ui.dp(this, 13), 0, 0, 0)
        quotaText.addView(Ui.label(this, "Quỹ mạng xã hội", 14f, Ui.TEXT, true))
        quotaSub = Ui.label(this, "", 12f, Ui.TEXT_3)
        quotaSub.setPadding(0, Ui.dp(this, 2), 0, 0)
        quotaText.addView(quotaSub)
        quotaPending = Ui.label(this, "", 11.5f, Ui.WARN, true)
        quotaPending.setPadding(0, Ui.dp(this, 4), 0, 0)
        quotaPending.visibility = View.GONE
        quotaText.addView(quotaPending)
        quotaBonus = Ui.label(this, "", 11.5f, Ui.ACCENT, true)
        quotaBonus.setPadding(0, Ui.dp(this, 4), 0, 0)
        quotaBonus.visibility = View.GONE
        quotaText.addView(quotaBonus)
        val quotaChange = Ui.button(this, "Đổi mức quỹ", Variant.GHOST)
        quotaChange.setOnClickListener { startActivity(Intent(this, QuotaActivity::class.java)) }
        quotaText.addView(quotaChange, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(this@MainActivity, 9) })
        quotaRow.addView(quotaText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        quotaCard.addView(quotaRow)
        page.addView(quotaCard)

        studyTile = Ui.stat(this, "Học hôm nay")
        gymTile = Ui.stat(this, "Gym tuần")
        streakTile = Ui.stat(this, "Chuỗi ngày")
        page.addView(statRow(listOf(studyTile, gymTile, streakTile)))

        val activity = Ui.group(this)
        studyRow = Ui.row(this, Ui.tile(this, R.drawable.ic_flash, Tone.ACCENT))
        studyRow.title("Điểm học hôm nay")
        studyRow.subtitle("Hoàn thành một phiên tập trung để có điểm")
        studyRow.trailing(Ui.chevron(this))
        studyRow.makeClickable { selectTab(2) }
        activity.addView(studyRow)
        gymRow = Ui.row(this, Ui.tile(this, R.drawable.ic_dumbbell, Tone.NEUTRAL))
        gymRow.title("Buổi tập")
        gymRow.subtitle("Đủ 10 phút liên tục trong app tập")
        gymRow.trailing(Ui.chevron(this))
        gymRow.makeClickable { startActivity(Intent(this, GymActivity::class.java)) }
        activity.addView(gymRow)
        blockRow = Ui.row(this, Ui.tile(this, R.drawable.ic_block, Tone.ACCENT))
        blockRow.title("Đã chặn hôm nay")
        blockRow.subtitle("App và web gây xao nhãng")
        activity.addView(blockRow)
        page.addView(activity)

        page.addView(Ui.note(
            this,
            "Quỹ là của cả ngày: mở app mạng xã hội là bắt đầu trừ. Học kiên trì sẽ cộng thêm phút thưởng. " +
                "Quỹ làm mới lúc 0:00. Không can thiệp hệ thống, tắt quyền Trợ năng là dừng ngay.",
        ))
        return Ui.scroll(page)
    }

    private fun statRow(tiles: List<UiStat>): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        tiles.forEachIndexed { index, tile ->
            row.addView(tile, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index > 0) leftMargin = Ui.dp(this@MainActivity, 8)
            })
        }
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = Ui.dp(this, 11)
        row.layoutParams = params
        return row
    }

    private fun buildProtect(): ScrollView {
        val page = Ui.screen(this)
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 12))
        val title = Ui.label(this, "Bảo vệ", 23f, Ui.TEXT, true)
        title.letterSpacing = -0.01f
        head.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        protectCount = Ui.pill(this, "0 app", Tone.NEUTRAL, dot = false)
        head.addView(protectCount)
        page.addView(head)

        val explainer = Ui.group(this)
        protectQuotaRow = Ui.row(this, Ui.tile(this, R.drawable.ic_shield_check, Tone.ACCENT))
        protectQuotaRow.title("Mở app là bắt đầu trừ quỹ")
        protectQuotaRow.subtitle("Quỹ của cả ngày, còn quỹ thì dùng bình thường")
        explainer.addView(protectQuotaRow)
        page.addView(explainer)

        page.addView(Ui.section(this, "Nội dung 18+"))
        val adultGroup = Ui.group(this)
        adultRow = Ui.row(this, Ui.tile(this, R.drawable.ic_shield_alert, Tone.WARN))
        adultRow.title("Chặn trang người lớn")
        adultRow.subtitle("Đang kiểm tra…")
        adultToggle = UiToggle(this)
        adultToggle.onChange = { checked -> setAdultFilter(checked) }
        adultRow.trailing(adultToggle, leftMarginDp = 10)
        adultGroup.addView(adultRow)

        visualRow = Ui.row(this, Ui.tile(this, R.drawable.ic_scan, Tone.ACCENT))
        visualRow.title("Chặn ảnh 18+ trên màn hình")
        visualRow.subtitle("Đang kiểm tra…")
        visualToggle = UiToggle(this)
        visualToggle.onChange = { checked -> setVisualFilter(checked) }
        visualRow.trailing(visualToggle, leftMarginDp = 10)
        adultGroup.addView(visualRow)
        page.addView(adultGroup)
        page.addView(
            Ui.note(
                this,
                "Chặn suốt ngày đêm, không cần giờ tập trung. " +
                    "App nào tự tra DNS riêng thì đi đường khác, ví dụ Chrome bật DoH.",
                R.drawable.ic_info,
            )
        )

        page.addView(Ui.section(this, "App bị chặn"))
        protectGroup = Ui.group(this)
        page.addView(protectGroup)
        rebuildProtectRows()

        val safety = Ui.card(this, padDp = 13)
        safety.addView(
            Ui.note(
                this,
                "Kỷ luật không tự đoán app: bạn chọn cái nào, chặn đúng cái đó. " +
                    "Cuộc gọi, Tin nhắn, Cài đặt, màn hình chính và app hệ thống không bao giờ bị chặn.",
                R.drawable.ic_info,
            )
        )
        page.addView(safety)
        return Ui.scroll(page)
    }

    private fun rebuildProtectRows() {
        protectGroup.removeAllViews()
        socialToggles.clear()
        protectPackages.clear()

        val entries = LinkedHashMap<String, String>()
        Prefs.SOCIAL_LABELS.forEach { (packageName, label) -> entries[packageName] = label }
        prefs.extraPackages.sorted().forEach { packageName ->
            entries[packageName] = Apps.label(this, packageName)
        }

        entries.forEach { (packageName, label) ->
            val custom = !Prefs.SOCIAL_LABELS.containsKey(packageName)
            protectPackages.add(packageName)

            val tile = Ui.tile(this, R.drawable.ic_grid, Tone.NEUTRAL)
            Apps.icon(this, packageName)?.let { tile.setDrawable(it) }
            val row = Ui.row(this, tile)
            row.title(label)
            val installed = Apps.isInstalled(this, packageName)
            row.subtitle(
                when {
                    custom && !installed -> "Đã gỡ cài đặt · chạm dấu × để xóa"
                    custom -> "App bạn tự thêm"
                    installed -> Apps.category(packageName)
                    else -> "Chưa cài trên máy này"
                }
            )
            if (!installed) row.status("—", Ui.TEXT_3)

            if (custom) {
                val remove = FrameLayout(this)
                remove.background = Ui.ripple(Ui.rounded(Color.TRANSPARENT, 8, this), 0x14000000)
                val pad = Ui.dp(this, 7)
                remove.setPadding(pad, pad, pad, pad)
                remove.addView(Ui.icon(this, R.drawable.ic_close, Ui.TEXT_3, 15))
                remove.setOnClickListener {
                    // Bỏ một app khỏi danh sách chặn là nới luật, phải qua cổng 60 giây.
                    requireUnlock {
                        prefs.setExtraPackage(packageName, false)
                        Toast.makeText(this, "Đã xóa $label khỏi danh sách", Toast.LENGTH_SHORT).show()
                        rebuildProtectRows()
                        refreshAll()
                    }
                }
                row.trailing(remove, leftMarginDp = 2)
            }

            val toggle = UiToggle(this)
            toggle.setChecked(prefs.socialPackages.contains(packageName))
            toggle.onChange = { checked ->
                if (checked) {
                    prefs.setSocialPackage(packageName, true)
                    refreshAll()
                } else {
                    // Tắt chặn một app là nới luật; giữ công tắc bật cho tới khi qua cổng.
                    if (!prefs.rulesUnlocked(System.currentTimeMillis(), SystemClock.elapsedRealtime())) {
                        toggle.setChecked(true, animate = true)
                    }
                    requireUnlock {
                        prefs.setSocialPackage(packageName, false)
                        refreshAll()
                    }
                }
            }
            socialToggles[packageName] = toggle
            row.trailing(toggle, leftMarginDp = if (custom) 6 else 10)
            protectGroup.addView(row)
        }

        val addRow = Ui.row(this, Ui.tile(this, R.drawable.ic_plus, Tone.ACCENT))
        addRow.title("Thêm app khác")
        addRow.subtitle("Tự chọn từ app đã cài trên máy")
        addRow.trailing(Ui.chevron(this))
        addRow.makeClickable { startActivity(Intent(this, BlockPickerActivity::class.java)) }
        protectGroup.addView(addRow)

        protectSignature = signature()
    }

    private fun signature(): String = prefs.extraPackages.toList().sorted().joinToString(",")

    private fun setAdultFilter(checked: Boolean) {
        if (!checked) {
            prefs.adultFilterEnabled = false
            stopService(Intent(this, AdultDnsVpnService::class.java))
            refreshAdult()
            // Nếu onDestroy của service bị kẹt, ép nó tự đóng đường hầm qua ACTION_STOP.
            handler.postDelayed({
                if (!prefs.adultFilterEnabled && Runtime.adultFilter) {
                    try {
                        startService(
                            Intent(this, AdultDnsVpnService::class.java)
                                .setAction(AdultDnsVpnService.ACTION_STOP)
                        )
                    } catch (error: Exception) {
                        Runtime.adultFilter = false
                    }
                }
            }, 1500)
            return
        }
        val consent = try {
            VpnService.prepare(this)
        } catch (error: Exception) {
            null
        }
        if (consent != null) startActivityForResult(consent, ADULT_REQUEST) else startAdultFilter()
    }

    private fun startAdultFilter() {
        prefs.adultFilterEnabled = true
        wakeAdultFilter()
        refreshAdult()
    }

    /** Công tắc bám ý định người dùng; Runtime chỉ dùng để mô tả đường hầm đã mở hay chưa. */
    private fun refreshAdult() {
        // setBlocking(false) cần API 27. Dưới 8.1 thì đường hầm chỉ nhận DNS sẽ thành VPN chiếm
        // toàn bộ traffic, nên ẩn công tắc thay vì để người dùng bấm vào thứ máy không làm được.
        val supported = Build.VERSION.SDK_INT >= 27
        val wanted = prefs.adultFilterEnabled
        adultToggle.visibility = if (supported) View.VISIBLE else View.GONE
        adultToggle.setChecked(supported && wanted)
        adultRow.subtitle(
            when {
                !supported -> "Cần Android 8.1 trở lên để lọc DNS mà không mất mạng"
                wanted && Runtime.adultFilter -> "Đang lọc từng truy vấn DNS của máy"
                wanted -> "Đang khởi động…"
                Runtime.adultFilter -> "Đang tắt…"
                else -> "Tắt · bật để chặn suốt ngày đêm"
            }
        )
        refreshVisual()
    }

    private fun setVisualFilter(checked: Boolean) {
        if (!checked) {
            prefs.visualFilterEnabled = false
            refreshVisual()
            return
        }
        if (Build.VERSION.SDK_INT < 30) {
            visualToggle.setChecked(false)
            return
        }
        if (!accessibilityEnabled()) {
            visualToggle.setChecked(false)
            AlertDialog.Builder(this)
                .setTitle("Cần bật Trợ năng")
                .setMessage(
                    "Android chỉ cho Kỷ luật đọc màn hình khi quyền Trợ năng đang bật. " +
                        "Bật xong, quay lại đây gạt công tắc."
                )
                .setPositiveButton("Mở Cài đặt") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("Để sau", null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Chặn ảnh 18+?")
            .setMessage(
                "Kỷ luật sẽ đọc màn hình khoảng 5 giây một lần khi bạn đang ở trình duyệt, " +
                    "YouTube hoặc app mạng xã hội đã chọn, rồi chạy mô hình ngay trên máy. " +
                    "Ảnh chỉ nằm trong bộ nhớ, không lưu, không gửi đi. Khi thấy nội dung 18+, " +
                    "app đưa bạn về màn hình chính.\n\n" +
                    "Có thể nhận nhầm ảnh bơi lội hoặc em bé. Tắt công tắc này bất cứ lúc nào."
            )
            .setPositiveButton("Bật") { _, _ ->
                prefs.visualFilterEnabled = true
                refreshVisual()
            }
            .setNegativeButton("Để sau") { _, _ -> visualToggle.setChecked(false) }
            .setOnCancelListener { visualToggle.setChecked(false) }
            .show()
    }

    private fun refreshVisual() {
        val supported = Build.VERSION.SDK_INT >= 30
        visualRow.visibility = if (supported) View.VISIBLE else View.GONE
        if (!supported) return
        val wanted = prefs.visualFilterEnabled
        val access = accessibilityEnabled()
        visualToggle.setChecked(wanted)
        visualToggle.alpha = if (access) 1f else 0.55f
        visualRow.subtitle(
            when {
                !wanted -> "Tắt · nhận diện ảnh ngay trên máy"
                !access -> "Cần bật Trợ năng để đọc màn hình"
                else -> "Đang quét trình duyệt và app mạng xã hội"
            }
        )
    }

    private fun buildSettings(): ScrollView {
        val page = Ui.screen(this)
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 12))
        val title = Ui.label(this, "Cài đặt", 23f, Ui.TEXT, true)
        title.letterSpacing = -0.01f
        head.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        settingsPill = Ui.pill(this, "Đang kiểm tra…", Tone.NEUTRAL, dot = false)
        head.addView(settingsPill)
        page.addView(head)

        page.addView(Ui.section(this, "Kết nối"))
        val connection = Ui.group(this)
        pcRow = Ui.row(this, Ui.tile(this, R.drawable.ic_desktop, Tone.ACCENT))
        pcRow.title("Máy tính")
        pcRow.subtitle("Chưa kết nối")
        pcRow.trailing(Ui.chevron(this))
        pcRow.makeClickable { startActivity(Intent(this, SetupActivity::class.java)) }
        connection.addView(pcRow)
        syncRow = Ui.row(this, Ui.tile(this, R.drawable.ic_refresh, Tone.NEUTRAL))
        syncRow.title("Đồng bộ")
        syncRow.subtitle("Chưa có dữ liệu")
        connection.addView(syncRow)
        page.addView(connection)

        page.addView(Ui.section(this, "Quyền"))
        val permissions = Ui.group(this)
        overlayRow = Ui.row(this, Ui.tile(this, R.drawable.ic_layers, Tone.ACCENT))
        overlayRow.title("Hiển thị trên ứng dụng khác")
        overlayRow.subtitle("Để phủ màn hình cảnh báo")
        overlayRow.trailing(Ui.chevron(this))
        overlayRow.makeClickable {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        permissions.addView(overlayRow)
        accessibilityRow = Ui.row(this, Ui.tile(this, R.drawable.ic_accessibility, Tone.ACCENT))
        accessibilityRow.title("Trợ năng")
        accessibilityRow.subtitle("Chỉ đọc tên ứng dụng đang mở")
        accessibilityRow.trailing(Ui.chevron(this))
        accessibilityRow.makeClickable { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        permissions.addView(accessibilityRow)
        notificationRow = Ui.row(this, Ui.tile(this, R.drawable.ic_bell, Tone.NEUTRAL))
        notificationRow.title("Thông báo")
        notificationRow.subtitle("Đồng hồ buổi tập")
        notificationRow.trailing(Ui.chevron(this))
        notificationRow.makeClickable {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 51)
            } else {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                )
            }
        }
        permissions.addView(notificationRow)
        page.addView(permissions)

        val restricted = Ui.restrictedCard(this) { Apps.openAppInfo(this) }
        restricted.visibility = View.GONE
        restrictedCard = restricted
        page.addView(restricted)

        page.addView(Ui.section(this, "Mục tiêu"))
        val goals = Ui.group(this)
        quotaSettingRow = Ui.row(this, Ui.tile(this, R.drawable.ic_clock, Tone.ACCENT))
        quotaSettingRow.title("Quỹ mạng xã hội")
        quotaSettingRow.subtitle("${quota.limitMinutes} phút mỗi ngày")
        quotaSettingRow.status("Đổi", Ui.TEXT_3)
        quotaSettingRow.trailing(Ui.chevron(this))
        quotaSettingRow.makeClickable { startActivity(Intent(this, QuotaActivity::class.java)) }
        goals.addView(quotaSettingRow)
        gymSettingRow = Ui.row(this, Ui.tile(this, R.drawable.ic_dumbbell, Tone.NEUTRAL))
        gymSettingRow.title("App tập gym")
        gymSettingRow.subtitle(Apps.label(this, prefs.gymPackage))
        gymSettingRow.status("Đổi", Ui.TEXT_3)
        gymSettingRow.trailing(Ui.chevron(this))
        gymSettingRow.makeClickable { startActivity(Intent(this, GymPickerActivity::class.java)) }
        goals.addView(gymSettingRow)
        page.addView(goals)

        page.addView(Ui.section(this, "Điểm học"))
        val studyGroup = Ui.group(this)
        studyRateRow = Ui.row(this, Ui.tile(this, R.drawable.ic_flash, Tone.ACCENT))
        studyRateRow.title("Quy đổi điểm")
        studyRateRow.subtitle("${prefs.pointsPerMinute} điểm = 1 phút lướt")
        studyRateRow.status("Đổi", Ui.TEXT_3)
        studyRateRow.trailing(Ui.chevron(this))
        studyRateRow.makeClickable { showRateDialog() }
        studyGroup.addView(studyRateRow)
        page.addView(studyGroup)
        page.addView(Ui.note(
            this,
            "Hoàn thành 1 phiên tập trung = $POINTS_PER_SESSION điểm học, cộng cả phiên chạy trên máy tính. " +
                "Dừng phiên giữa chừng thì không có điểm. Điểm về 0 lúc 0:00, trần ${MAX_BONUS_MINUTES} phút thưởng mỗi ngày.",
            R.drawable.ic_info,
        ))

        page.addView(Ui.section(this, "An toàn"))
        val safety = Ui.group(this)
        rulesRow = Ui.row(this, Ui.tile(this, R.drawable.ic_gear, Tone.WARN))
        rulesRow.title("Chỉnh luật")
        rulesRow.subtitle("Một lần cam kết 60 giây mở khoá mọi chỉnh sửa nới luật")
        rulesRow.trailing(Ui.chevron(this))
        rulesRow.makeClickable { onRulesRow() }
        safety.addView(rulesRow)
        protectionRow = Ui.row(this, Ui.tile(this, R.drawable.ic_power, Tone.WARN))
        protectionRow.title("Bảo vệ")
        protectionRow.subtitle("Đang chặn app trong danh sách · tắt phải xác nhận 3 bước")
        protectionToggle = UiToggle(this)
        protectionToggle.onChange = { checked -> onProtectionToggle(checked) }
        protectionRow.trailing(protectionToggle, leftMarginDp = 10)
        protectionRow.makeClickable { protectionToggle.setChecked(!protectionToggle.checked, notify = true) }
        safety.addView(protectionRow)
        guardRow = Ui.row(this, Ui.tile(this, R.drawable.ic_shield_alert, Tone.WARN))
        guardRow.title("Chặn gỡ cài đặt")
        guardRow.subtitle("Tắt · thêm bước xác nhận khi gỡ app")
        guardToggle = UiToggle(this)
        guardToggle.onChange = { checked -> onUninstallGuard(checked) }
        guardRow.trailing(guardToggle, leftMarginDp = 10)
        safety.addView(guardRow)
        val setupRow = Ui.row(this, Ui.tile(this, R.drawable.ic_grid, Tone.NEUTRAL))
        setupRow.title("Chạy lại thiết lập")
        setupRow.subtitle("Xem lại quyền, app chặn, quỹ và ghép nối")
        setupRow.trailing(Ui.chevron(this))
        setupRow.makeClickable { startActivity(Intent(this, OnboardingActivity::class.java)) }
        safety.addView(setupRow)
        page.addView(safety)

        page.addView(Ui.section(this, "Cập nhật"))
        val updates = Ui.group(this)
        autoUpdateRow = Ui.row(this, Ui.tile(this, R.drawable.ic_refresh, Tone.ACCENT))
        autoUpdateRow.title("Tự kiểm tra bản mới")
        autoUpdateRow.subtitle("Hỏi GitHub 12 giờ một lần")
        autoUpdateToggle = UiToggle(this)
        autoUpdateToggle.onChange = { checked ->
            prefs.autoUpdate = checked
            refreshUpdateRows()
            if (checked) checkUpdate(manual = false)
        }
        autoUpdateRow.trailing(autoUpdateToggle, leftMarginDp = 10)
        updates.addView(autoUpdateRow)
        checkUpdateRow = Ui.row(this, Ui.tile(this, R.drawable.ic_search, Tone.NEUTRAL))
        checkUpdateRow.title("Kiểm tra ngay")
        checkUpdateRow.subtitle("Chưa kiểm tra lần nào")
        checkUpdateRow.trailing(Ui.chevron(this))
        checkUpdateRow.makeClickable {
            if (prefs.updateAvailableVersion.isNotBlank()) maybeOfferUpdate() else checkUpdate(manual = true)
        }
        updates.addView(checkUpdateRow)
        page.addView(updates)

        page.addView(Ui.section(this, "Thông tin app"))
        val about = Ui.group(this)
        versionRow = Ui.row(this, Ui.tile(this, R.drawable.ic_shield, Tone.ACCENT))
        versionRow.title("Kỷ luật")
        versionRow.subtitle("Máy chặn xao nhãng, chạy hoàn toàn trên máy bạn")
        about.addView(versionRow)
        val sourceRow = Ui.row(this, Ui.tile(this, R.drawable.ic_grid, Tone.NEUTRAL))
        sourceRow.title("Mã nguồn")
        sourceRow.subtitle("github.com/HairBaconGamming/kyluat")
        sourceRow.trailing(Ui.chevron(this))
        sourceRow.makeClickable { openUrl("https://github.com/HairBaconGamming/kyluat") }
        about.addView(sourceRow)
        val siteRow = Ui.row(this, Ui.tile(this, R.drawable.ic_desktop, Tone.NEUTRAL))
        siteRow.title("Hướng dẫn và tải về")
        siteRow.subtitle("kyluat.vercel.app")
        siteRow.trailing(Ui.chevron(this))
        siteRow.makeClickable { openUrl("https://kyluat.vercel.app") }
        about.addView(siteRow)
        page.addView(about)

        page.addView(
            Ui.note(
                this,
                "Mã kết nối được mã hoá trong Android Keystore, chỉ dùng trong mạng nội bộ. " +
                    "Kỷ luật không đọc nội dung màn hình.",
                R.drawable.ic_info,
            )
        )
        page.addView(
            Ui.note(
                this,
                "Dự án cá nhân, không bán, không bảo hành. Bản mới chỉ được cài khi bạn bấm qua trình cài đặt của Android.",
                R.drawable.ic_info,
            )
        )
        return Ui.scroll(page)
    }

    private fun todayLabel(): String {
        val calendar = java.util.Calendar.getInstance()
        val weekdays = arrayOf("Chủ nhật", "Thứ hai", "Thứ ba", "Thứ tư", "Thứ năm", "Thứ sáu", "Thứ bảy")
        val format = SimpleDateFormat("dd/MM", Locale.US)
        return "${weekdays[calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1]} · ${format.format(Date(calendar.timeInMillis))}"
    }

    private val ticker = object : Runnable {
        override fun run() {
            refreshAll()
            tickCount += 1
            if (tickCount % 15 == 0) refreshNetwork()
            handler.postDelayed(this, 1000)
        }
    }

    private fun sessionRemainingSeconds(): Int {
        if (sessionEndElapsed == 0L) return (summary?.sessionRemaining ?: 0) * 60
        return ((sessionEndElapsed - SystemClock.elapsedRealtime()) / 1000).toInt().coerceAtLeast(0)
    }

    private fun isLocalFocusActive(): Boolean = localFocusActive(
        prefs.localFocusEndMs,
        prefs.localFocusStopAfterMs,
        System.currentTimeMillis(),
    )

    private fun manualRemainingSeconds(): Int = if (isLocalFocusActive()) {
        localFocusRemainingSeconds(
            prefs.localFocusEndMs,
            prefs.localFocusStopAfterMs,
            System.currentTimeMillis(),
        )
    } else {
        sessionRemainingSeconds()
    }

    private fun updateRuntimeFocus() {
        val local = isLocalFocusActive()
        if (local && !Runtime.remoteManualFocus) {
            Runtime.focusText = localFocusText(
                prefs.localFocusEndMs,
                prefs.localFocusStopAfterMs,
                System.currentTimeMillis(),
            )
        }
        Runtime.focusActive = manualFocusActive(prefs.protectionEnabled, Runtime.remoteManualFocus, local)
    }

    private fun maybeExpireLocalFocus() {
        // Phiên chạy hết giờ thì cộng điểm học ở đây, để mở app đúng lúc hết phiên vẫn thấy điểm.
        if (settleLocalFocus(prefs, quota, System.currentTimeMillis())) {
            val bonus = quota.bonusMinutes()
            val tail = if (bonus > 0) " · quỹ hôm nay +$bonus phút" else ""
            Toast.makeText(this, "Xong phiên tập trung · +$POINTS_PER_SESSION điểm học$tail", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshAll() {
        quota.resetIfNeeded()
        refreshAdult()
        maybeExpireLocalFocus()
        updateRuntimeFocus()
        val paired = secure.load() != null
        val overlay = Settings.canDrawOverlays(this)
        val accessibility = accessibilityEnabled()
        val notifications = notificationsEnabled()
        val ready = listOf(overlay, accessibility).count { it }
        val remaining = quota.remainingSeconds()
        val protectionOn = prefs.protectionEnabled

        setupCard.visibility = if (ready == 2 && protectionOn) View.GONE else View.VISIBLE
        setupButton.text = if (!protectionOn && ready == 2) "Bật bảo vệ" else "Hoàn tất thiết lập"
        setupText.text = when {
            !protectionOn -> "Bảo vệ đang tắt — bật lại để tiếp tục giữ cam kết."
            !overlay -> "Cần quyền Hiển thị trên ứng dụng khác để phủ cảnh báo."
            else -> "Cần bật Trợ năng để nhận biết ứng dụng đang mở."
        }

        val localSessionActive = protectionOn && isLocalFocusActive()
        val remoteSessionActive = protectionOn && hasManualSession(summary?.sessionLabel)
        val focusActive = localSessionActive || remoteSessionActive
        val scheduled = summary?.scheduledActive == true
        if (focusActive) {
            idleBox.visibility = View.GONE
            activeBox.visibility = View.VISIBLE
            val left = manualRemainingSeconds()
            heroEyebrow.text = "Đang tập trung"
            heroTime.text = Ui.clockText(left)
            heroCaption.text = if (localSessionActive) {
                localFocusText(
                    prefs.localFocusEndMs,
                    prefs.localFocusStopAfterMs,
                    System.currentTimeMillis(),
                ).ifBlank { Runtime.focusText.ifBlank { "Phiên đang chạy trên điện thoại." } }
            } else {
                summary?.let {
                    listOf(it.sessionLabel ?: it.focusLabel, it.focusDetail)
                        .filter { part -> part.isNotBlank() }
                        .distinct()
                        .joinToString(" · ")
                }?.ifBlank { Runtime.focusText } ?: Runtime.focusText.ifBlank { "Phiên đang chạy trên máy tính." }
            }
            val total = if (localSessionActive) {
                prefs.localFocusTotalSeconds.coerceAtLeast(1)
            } else {
                if (sessionTotalSeconds > 0) sessionTotalSeconds else (left + 1)
            }
            sessionBar.setProgress(1f - (left.toFloat() / total), Ui.ACCENT)
            sessionBar.visibility = View.VISIBLE
            sessionButtons.visibility = View.VISIBLE
            sessionNote.text = if (localSessionActive) {
                "Dừng sớm không mở khóa ngay; bạn vẫn phải chờ hết hạn dừng đã hẹn."
            } else {
                "Dừng sớm không mở khóa ngay; bạn vẫn phải chờ như đã cam kết."
            }
        } else {
            heroEyebrow.text = "Tập trung"
            heroTime.text = Ui.clockText(selectedMinutes * 60)
            heroCaption.text = if (scheduled) {
                val detail = listOf(summary?.focusLabel, summary?.focusDetail)
                    .filterNotNull()
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                if (detail.isBlank()) {
                    "Đang trong khung giờ trên máy tính · điện thoại không bị khoá"
                } else {
                    "$detail · lịch máy tính, điện thoại không bị khoá"
                }
            } else if (paired) {
                "Khóa app và web gây xao nhãng trên cả hai thiết bị"
            } else {
                "Bắt đầu phiên trực tiếp trên điện thoại, không cần máy tính"
            }
            idleBox.visibility = View.VISIBLE
            activeBox.visibility = View.GONE
            startButton.isEnabled = protectionOn && overlay && accessibility
        }

        val studyDone = summary?.studyToday ?: 0
        val studyGoal = (summary?.studyGoal ?: 0).coerceAtLeast(1)
        heroRing.value.text = "$studyDone′"
        heroRing.caption.text = "phút học"
        heroRing.setProgress(studyDone.toFloat() / studyGoal)

        val total = quota.totalLimitSeconds()
        val bonusMin = quota.bonusMinutes()
        quotaRing.value.text = "${(remaining + 59) / 60}"
        quotaRing.caption.text = "phút còn"
        quotaRing.setProgress(if (total > 0) remaining.toFloat() / total else 0f)
        quotaRing.setArcColor(
            when {
                remaining <= 0 -> Ui.DANGER
                remaining < 15 * 60 -> Ui.WARN
                else -> Ui.ACCENT
            }
        )
        quotaSub.text = "Đã dùng ${quota.usedSeconds() / 60} / ${total / 60} phút · làm mới 0:00"
        val pending = quota.pendingMinutes
        val hasPending = pending > 0 && pending != quota.limitMinutes
        quotaPending.text = if (hasPending) "Đã hẹn $pending phút từ 0:00 hôm sau" else ""
        quotaPending.visibility = if (hasPending) View.VISIBLE else View.GONE
        quotaBonus.text = if (bonusMin > 0) "+$bonusMin phút thưởng từ ${quota.pointsToday()} điểm học" else ""
        quotaBonus.visibility = if (bonusMin > 0) View.VISIBLE else View.GONE

        studyRow.subtitle(studyHomeSubtitle())
        studyRow.status(
            if (bonusMin > 0) "+$bonusMin phút" else "${quota.pointsToday()} điểm",
            if (bonusMin > 0) Ui.ACCENT else Ui.TEXT_3,
        )

        studyTile.value.text = if (summary == null) "—" else "$studyDone/$studyGoal"
        studyTile.setBar(studyDone.toFloat() / studyGoal)
        val gymDone = summary?.gymWeek ?: 0
        val gymGoal = (summary?.gymGoal ?: 0).coerceAtLeast(1)
        gymTile.value.text = if (summary == null) "—" else "$gymDone/$gymGoal"
        gymTile.setBar(gymDone.toFloat() / gymGoal)
        val streak = summary?.studyStreak ?: 0
        streakTile.value.text = if (summary == null) "—" else "$streak"
        streakTile.setBar((streak.coerceAtMost(30)) / 30f)

        gymRow.subtitle(
            when {
                Runtime.gymStartElapsed > 0L -> "Đang đếm · ${Ui.clockText(gymSeconds())}"
                Runtime.lastGymAwarded -> "Đã tính 1 buổi · ${Ui.clockText(Runtime.lastGymSeconds)}"
                Runtime.lastGymSeconds > 0 -> "Lần trước ${Ui.clockText(Runtime.lastGymSeconds)} · chưa đủ 10 phút"
                else -> "Đủ 10 phút liên tục trong app tập"
            }
        )
        gymRow.status(if (Runtime.gymStartElapsed > 0L) "Đang chạy" else "Mở", if (Runtime.gymStartElapsed > 0L) Ui.ACCENT else Ui.TEXT_3)
        blockRow.status(if (summary == null) "—" else "${summary?.blocksToday ?: 0} lần", Ui.TEXT_3)

        statusPill.set(
            when {
                !protectionOn -> "Bảo vệ đang tắt"
                !overlay || !accessibility -> "Cần cấp quyền"
                focusActive -> "Đang tập trung"
                else -> "Đang bảo vệ"
            },
            when {
                !protectionOn -> Tone.DANGER
                !overlay || !accessibility -> Tone.WARN
                else -> Tone.ACCENT
            },
        )

        if (signature() != protectSignature) rebuildProtectRows()
        protectCount.set(
            "${protectPackages.count { prefs.socialPackages.contains(it) }} app đang chặn",
            Tone.NEUTRAL,
            showDot = false,
        )

        settingsPill.set(
            if (ready == 2) "Sẵn sàng 2/2" else "Cần $ready/2",
            if (ready == 2) Tone.ACCENT else Tone.WARN,
            showDot = false,
        )
        val pair = secure.load()
        pcRow.status(
            if (pair == null) "Không bắt buộc" else "Đã nối",
            if (pair == null) Ui.TEXT_3 else Ui.ACCENT,
        )
        pcRow.subtitle(pair?.baseUrl?.removePrefix("http://") ?: "Chạm để kết nối với máy tính")
        syncRow.subtitle(
            if (Runtime.lastSyncElapsed == 0L) "Chưa có dữ liệu" else "Lần cuối ${sinceSync()} · ${summary?.date ?: ""}".trim()
        )
        syncRow.status(if (summary == null) "" else "OK", Ui.ACCENT)

        overlayRow.status(if (overlay) "Đã cấp" else "Cấp quyền", if (overlay) Ui.ACCENT else Ui.DANGER)
        accessibilityRow.status(if (accessibility) "Đã bật" else "Bật", if (accessibility) Ui.ACCENT else Ui.DANGER)
        restrictedCard?.visibility = if (accessibility) View.GONE else View.VISIBLE
        notificationRow.status(if (notifications) "Đã bật" else "Tắt", if (notifications) Ui.ACCENT else Ui.TEXT_3)
        gymSettingRow.subtitle(Apps.label(this, prefs.gymPackage))
        quotaSettingRow.subtitle(
            if (hasPending) "$pending phút có hiệu lực từ 0:00 hôm sau"
            else if (bonusMin > 0) "${quota.limitMinutes} phút + $bonusMin phút thưởng mỗi ngày"
            else "${quota.limitMinutes} phút mỗi ngày · chỉnh được"
        )
        protectQuotaRow.subtitle(
            if (bonusMin > 0) "Quỹ ${quota.limitMinutes} phút + $bonusMin phút thưởng mỗi ngày"
            else "Quỹ ${quota.limitMinutes} phút mỗi ngày, mở app là trừ dần"
        )
        studyRateRow.subtitle("${prefs.pointsPerMinute} điểm = 1 phút lướt")

        val unlocked = prefs.rulesUnlocked(System.currentTimeMillis(), SystemClock.elapsedRealtime())
        when {
            !protectionOn -> {
                rulesRow.status("Bảo vệ tắt", Ui.TEXT_3)
                rulesRow.subtitle("Bảo vệ đang tắt nên chỉnh luật nào cũng được")
            }
            unlocked -> {
                val left = unlockRemainingSeconds(
                    prefs.unlockWallMs, prefs.unlockElapsedMs,
                    System.currentTimeMillis(), SystemClock.elapsedRealtime(),
                )
                rulesRow.status("Còn ${(left + 59) / 60}′", Ui.ACCENT)
                rulesRow.subtitle("Đang mở khoá · chạm để khoá lại sớm")
            }
            else -> {
                rulesRow.status("Đang khoá", Ui.TEXT_3)
                rulesRow.subtitle("Một lần cam kết 60 giây mở khoá mọi chỉnh sửa nới luật")
            }
        }

        if (protectionToggle.checked != protectionOn) protectionToggle.setChecked(protectionOn)
        protectionRow.subtitle(
            if (protectionOn) "Đang chặn app trong danh sách · tắt phải qua cổng cam kết 60 giây"
            else "Đang tắt do bạn chủ động dừng · bật lại khi sẵn sàng"
        )

        socialToggles.forEach { (pkg, toggle) ->
            val checked = prefs.socialPackages.contains(pkg)
            if (toggle.checked != checked) toggle.setChecked(checked)
        }
    }

    private fun studyHomeSubtitle(): String =
        "Hoàn thành một phiên tập trung để có $POINTS_PER_SESSION điểm"

    private fun gymSeconds(): Int =
        if (Runtime.gymStartElapsed == 0L) 0 else ((SystemClock.elapsedRealtime() - Runtime.gymStartElapsed) / 1000).toInt()

    private fun sinceSync(): String {
        val seconds = ((SystemClock.elapsedRealtime() - Runtime.lastSyncElapsed) / 1000).toInt()
        return when {
            seconds < 5 -> "vừa xong"
            seconds < 60 -> "$seconds giây trước"
            seconds < 3600 -> "${seconds / 60} phút trước"
            else -> "${seconds / 3600} giờ trước"
        }
    }

    private fun accessibilityEnabled(): Boolean = Apps.accessibilityEnabled(this)

    private fun permissionsReady(): Boolean = Settings.canDrawOverlays(this) && accessibilityEnabled()

    private fun notificationsEnabled(): Boolean {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        return manager.areNotificationsEnabled()
    }

    private fun refreshNetwork() {
        val pair = secure.load() ?: run {
            summary = null
            Runtime.remoteManualFocus = false
            updateRuntimeFocus()
            sessionEndElapsed = 0L
            return
        }
        executor.execute {
            try {
                val result = Api.summary(pair.baseUrl, pair.token)
                Runtime.lastSyncElapsed = SystemClock.elapsedRealtime()
                runOnUiThread {
                    summary = result
                    Runtime.remoteManualFocus = hasManualSession(result.sessionLabel)
                    if (result.focusActive) Runtime.focusText = result.focusText
                    updateRuntimeFocus()
                    if (hasManualSession(result.sessionLabel)) {
                        val minutes = result.sessionRemaining.coerceAtLeast(1)
                        sessionTotalSeconds = minutes * 60
                        sessionEndElapsed = SystemClock.elapsedRealtime() + minutes * 60_000L
                    } else {
                        sessionEndElapsed = 0L
                        sessionTotalSeconds = 0
                    }
                    refreshAll()
                }
            } catch (error: Exception) {
                // Giữ dữ liệu cũ; quỹ và chặn cục bộ vẫn hoạt động.
                runOnUiThread { updateRuntimeFocus(); refreshAll() }
            }
        }
    }

    private fun startSession() {
        if (!prefs.protectionEnabled || !Settings.canDrawOverlays(this) || !accessibilityEnabled()) {
            startActivity(Intent(this, SetupActivity::class.java))
            return
        }
        if (isLocalFocusActive() || hasManualSession(summary?.sessionLabel)) {
            Toast.makeText(this, "Đang có phiên focus chạy.", Toast.LENGTH_SHORT).show()
            refreshAll()
            return
        }
        val now = System.currentTimeMillis()
        prefs.localFocusEndMs = now + selectedMinutes * 60_000L
        prefs.localFocusStopAfterMs = 0L
        prefs.localFocusTotalSeconds = selectedMinutes * 60
        updateRuntimeFocus()
        refreshAll()
        Toast.makeText(this, "Đã bắt đầu phiên $selectedMinutes phút trên điện thoại", Toast.LENGTH_SHORT).show()

        val pair = secure.load() ?: return
        executor.execute {
            try {
                Api.session(pair.baseUrl, pair.token, "start", selectedMinutes, "Phone focus")
                runOnUiThread { handler.postDelayed({ refreshNetwork() }, 1200) }
            } catch (error: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Phiên vẫn chạy trên điện thoại; chưa đồng bộ được máy tính.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun sessionAction(action: String) {
        maybeExpireLocalFocus()
        val local = isLocalFocusActive()
        val remote = hasManualSession(summary?.sessionLabel)
        if (!local && !remote) {
            Toast.makeText(this, "Không có phiên thủ công đang chạy để thực hiện thao tác này.", Toast.LENGTH_SHORT).show()
            refreshNetwork()
            return
        }

        if (local) {
            val now = System.currentTimeMillis()
            when (action) {
                "stop" -> if (prefs.localFocusStopAfterMs == 0L) {
                    val delay = (summary?.sessionStopDelayMinutes ?: 10).coerceAtLeast(1)
                    prefs.localFocusStopAfterMs = now + delay * 60_000L
                }
                "cancel_stop" -> prefs.localFocusStopAfterMs = 0L
            }
            updateRuntimeFocus()
            refreshAll()
        }

        if (!remote) {
            Toast.makeText(
                this,
                if (action == "stop") "Đã hẹn dừng phiên trên điện thoại" else "Đã hủy hẹn dừng phiên",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        val pair = secure.load() ?: return
        executor.execute {
            try {
                Api.session(pair.baseUrl, pair.token, action, selectedMinutes, "Phone focus")
                runOnUiThread {
                    Toast.makeText(
                        this,
                        if (action == "stop") {
                            if (local) "Đã hẹn dừng · đồng bộ với máy tính" else "Đã yêu cầu dừng (có trễ)"
                        } else {
                            if (local) "Đã hủy hẹn dừng · đồng bộ với máy tính" else "Đã hủy yêu cầu dừng"
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
                    handler.postDelayed({ refreshNetwork() }, 1200)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (local) {
                        Toast.makeText(
                            this,
                            "Thao tác đã áp dụng trên điện thoại; chưa đồng bộ được máy tính.",
                            Toast.LENGTH_LONG,
                        ).show()
                    } else {
                        Toast.makeText(this, "Không thực hiện được: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    /** Chạy [then] ngay nếu cổng đang mở; nếu không thì mở cổng 60 giây rồi chạy khi qua cổng. */
    private fun requireUnlock(then: () -> Unit) {
        if (prefs.rulesUnlocked(System.currentTimeMillis(), SystemClock.elapsedRealtime())) {
            then()
            return
        }
        startUnlock(UnlockActivity.PURPOSE_UNLOCK, then)
    }

    private fun startUnlock(purpose: String, then: (() -> Unit)?) {
        pendingUnlockAction = then
        startActivityForResult(
            Intent(this, UnlockActivity::class.java).putExtra(UnlockActivity.EXTRA_PURPOSE, purpose),
            UNLOCK_REQUEST,
        )
    }

    private fun onRulesRow() {
        if (!prefs.protectionEnabled) {
            Toast.makeText(this, "Bảo vệ đang tắt nên chỉnh luật nào cũng được.", Toast.LENGTH_SHORT).show()
            return
        }
        if (prefs.rulesUnlocked(System.currentTimeMillis(), SystemClock.elapsedRealtime())) {
            val left = unlockRemainingSeconds(
                prefs.unlockWallMs, prefs.unlockElapsedMs,
                System.currentTimeMillis(), SystemClock.elapsedRealtime(),
            )
            AlertDialog.Builder(this)
                .setTitle("Đang mở khoá")
                .setMessage("Còn ${(left + 59) / 60} phút nữa Kỷ luật tự khoá lại. Trong lúc này bạn chỉnh được mọi luật nới: tăng quỹ, bỏ chặn app, hạ tỷ lệ điểm, tắt bảo vệ.")
                .setPositiveButton("Khoá lại ngay") { _, _ ->
                    prefs.lockRules()
                    Toast.makeText(this, "Đã khoá lại các luật.", Toast.LENGTH_SHORT).show()
                    refreshAll()
                }
                .setNegativeButton("Để nguyên", null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Mở khoá chỉnh luật?")
            .setMessage("Qua cổng cam kết 60 giây một lần, bạn được chỉnh mọi luật nới trong $UNLOCK_MINUTES phút: tăng quỹ, bỏ chặn app, hạ tỷ lệ quy đổi điểm, hoặc tắt bảo vệ. Siết luật thì luôn miễn phí.")
            .setPositiveButton("Bắt đầu cam kết") { _, _ -> startUnlock(UnlockActivity.PURPOSE_UNLOCK, null) }
            .setNegativeButton("Để sau", null)
            .show()
    }

    private fun showRateDialog() {
        val rates = intArrayOf(3, 5, 10, 15, 30)
        val labels = rates.map { "$it điểm = 1 phút" }.toTypedArray()
        val checkedIndex = rates.indexOf(prefs.pointsPerMinute).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Quy đổi điểm học")
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                dialog.dismiss()
                applyRate(rates[which])
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun applyRate(chosen: Int) {
        val current = prefs.pointsPerMinute
        if (chosen == current) return
        if (chosen < current) {
            // Hạ tỷ lệ = nới luật (ít điểm hơn vẫn ra phút thưởng) → phải qua cổng.
            requireUnlock {
                prefs.pointsPerMinute = chosen
                Toast.makeText(this, "Đã đổi: $chosen điểm = 1 phút.", Toast.LENGTH_SHORT).show()
                refreshAll()
            }
        } else {
            prefs.pointsPerMinute = chosen
            Toast.makeText(this, "Đã đổi: $chosen điểm = 1 phút.", Toast.LENGTH_SHORT).show()
            refreshAll()
        }
    }

    private fun onProtectionToggle(checked: Boolean) {
        if (checked) {
            prefs.protectionEnabled = true
            Toast.makeText(this, "Đã bật bảo vệ", Toast.LENGTH_SHORT).show()
            refreshAll()
            return
        }
        // Tắt bảo vệ là nới luật mạnh nhất: giữ công tắc bật cho tới khi xác nhận xong.
        protectionToggle.setChecked(true, animate = true)
        if (prefs.rulesUnlocked(System.currentTimeMillis(), SystemClock.elapsedRealtime())) {
            confirmTurnOffProtection()
        } else {
            startUnlock(UnlockActivity.PURPOSE_OFF, null)
        }
    }

    private fun confirmTurnOffProtection() {
        AlertDialog.Builder(this)
            .setTitle("Tắt bảo vệ ngay?")
            .setMessage("Cổng chỉnh luật đang mở. Tắt bây giờ sẽ ngừng chặn app, ngừng trừ quỹ và ngừng cộng điểm học cho đến khi bạn bật lại.")
            .setPositiveButton("Tắt bảo vệ") { _, _ ->
                turnOffProtection(prefs)
                Toast.makeText(this, "Đã tắt bảo vệ. Bạn có thể bật lại trong Cài đặt.", Toast.LENGTH_LONG).show()
                refreshAll()
                refreshUpdateRows()
            }
            .setNegativeButton("Giữ bảo vệ", null)
            .show()
    }

    private fun showCrashLog() {
        val file = File(getExternalFilesDir(null) ?: filesDir, "crash.txt")
        if (!file.exists()) return
        val text = try {
            file.readText().takeLast(6000)
        } catch (error: Exception) {
            return
        }
        if (text.isBlank()) {
            file.delete()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Log lỗi gần nhất")
            .setMessage(text)
            .setPositiveButton("Chia sẻ") { _, _ ->
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(share, "Gửi log Kỷ luật"))
                file.delete()
            }
            .setNegativeButton("Đóng") { _, _ -> file.delete() }
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ADULT_REQUEST) {
            if (resultCode == RESULT_OK) startAdultFilter() else prefs.adultFilterEnabled = false
            refreshAdult()
        }
        if (requestCode == UNLOCK_REQUEST) {
            if (resultCode == RESULT_OK) pendingUnlockAction?.invoke()
            pendingUnlockAction = null
            refreshAll()
            refreshUpdateRows()
        }
    }

    /** Hệ thống có thể thu hồi đường hầm khi đổi mạng hoặc hết pin. Mở Kỷ luật là lúc duy nhất
     *  hợp lý để bật lại; nếu lỗi thật thì service tự ghi tắt nên không thành vòng lặp. */
    private fun wakeAdultFilter() {
        if (!prefs.adultFilterEnabled || Runtime.adultFilter || Build.VERSION.SDK_INT < 27) return
        try {
            startService(Intent(this, AdultDnsVpnService::class.java))
        } catch (error: Exception) {
            prefs.adultFilterEnabled = false
        }
    }

    override fun onResume() {
        super.onResume()
        wakeAdultFilter()
        refreshAll()
        refreshNetwork()
        refreshUpdateRows()
        handler.removeCallbacks(offerUpdateRunnable)
        handler.postDelayed(offerUpdateRunnable, 600)
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (error: Exception) {
            Toast.makeText(this, "Không mở được trình duyệt", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshUpdateRows() {
        if (::autoUpdateToggle.isInitialized) {
            autoUpdateToggle.setChecked(prefs.autoUpdate)
            autoUpdateRow.subtitle(
                if (prefs.autoUpdate) "Hỏi GitHub 12 giờ một lần, không có telemetry"
                else "Tắt · chỉ kiểm tra khi bạn bấm"
            )
            val available = prefs.updateAvailableVersion
            checkUpdateRow.subtitle(
                when {
                    available.isNotBlank() -> "Có bản mới $available · chạm để tải"
                    prefs.updateLastCheckMs == 0L -> "Chưa kiểm tra lần nào · đang dùng $appVersion"
                    else -> "Lần cuối ${timeAgo(prefs.updateLastCheckMs)} · đang dùng $appVersion"
                }
            )
        }
        if (::versionRow.isInitialized) {
            versionRow.status(appVersionLabel, Ui.TEXT_3)
        }
        if (::guardRow.isInitialized) {
            val active = guardActive()
            if (guardToggle.checked != active) guardToggle.setChecked(active)
            if (prefs.uninstallGuard != active) prefs.uninstallGuard = active
            guardRow.subtitle(
                if (active) "Đang bật · gỡ app phải tắt quyền quản lý thiết bị trước"
                else "Tắt · thêm bước xác nhận khi gỡ app"
            )
        }
    }

    private fun timeAgo(wallClockMs: Long): String {
        val seconds = ((System.currentTimeMillis() - wallClockMs) / 1000).toInt().coerceAtLeast(0)
        return when {
            seconds < 60 -> "vừa xong"
            seconds < 3600 -> "${seconds / 60} phút trước"
            seconds < 86400 -> "${seconds / 3600} giờ trước"
            else -> "${seconds / 86400} ngày trước"
        }
    }

    private fun guardActive(): Boolean = try {
        dpm().isAdminActive(ComponentName(this, GuardAdminReceiver::class.java))
    } catch (error: Exception) {
        false
    }

    private fun dpm(): DevicePolicyManager =
        getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private fun onUninstallGuard(enabled: Boolean) {
        val component = ComponentName(this, GuardAdminReceiver::class.java)
        if (enabled && !prefs.protectionEnabled) {
            Toast.makeText(this, "Hãy bật bảo vệ trước khi dùng bước cản gỡ.", Toast.LENGTH_LONG).show()
            refreshUpdateRows()
            return
        }
        if (enabled) {
            AlertDialog.Builder(this)
                .setTitle("Chặn gỡ cài đặt?")
                .setMessage(
                    "Kỷ luật sẽ được đăng ký làm trình quản lý thiết bị để Android thêm bước xác nhận khi gỡ. " +
                        "App không khoá máy, không xoá dữ liệu, và bạn luôn tắt được quyền này trong Cài đặt hệ thống, " +
                        "kể cả khi app gặp lỗi. Android không cho app tự đặt thời gian chờ ở hộp thoại gỡ; " +
                        "đây là bước cản duy nhất hệ thống cho phép."
                )
                .setPositiveButton("Bật") { _, _ ->
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                        putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Kỷ luật chỉ dùng quyền này để Android hỏi lại khi gỡ app. " +
                                "Không khoá máy, không xoá dữ liệu."
                        )
                    }
                    try {
                        startActivityForResult(intent, GUARD_REQUEST)
                    } catch (error: Exception) {
                        Toast.makeText(this, "Máy không mở được màn hình quản lý thiết bị.", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Để sau") { _, _ -> refreshUpdateRows() }
                .setOnCancelListener { refreshUpdateRows() }
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Tắt bước cản gỡ?")
            .setMessage("Sau khi tắt, Android cho gỡ Kỷ luật như mọi app khác. Việc chặn app và bộ lọc 18+ giữ nguyên.")
            .setPositiveButton("Tắt") { _, _ ->
                try {
                    dpm().removeActiveAdmin(component)
                } catch (error: Exception) {
                    Toast.makeText(this, "Không tắt được: ${error.message}", Toast.LENGTH_LONG).show()
                }
                prefs.uninstallGuard = false
                refreshUpdateRows()
            }
            .setNegativeButton("Giữ") { _, _ -> refreshUpdateRows() }
            .setOnCancelListener { refreshUpdateRows() }
            .show()
    }

    private fun maybeCheckUpdate() {
        if (!prefs.autoUpdate) return
        if (System.currentTimeMillis() - prefs.updateLastCheckMs < UPDATE_INTERVAL_MS) return
        checkUpdate(manual = false)
    }

    private fun checkUpdate(manual: Boolean) {
        if (updateBusy) return
        updateBusy = true
        if (manual) Toast.makeText(this, "Đang kiểm tra bản mới…", Toast.LENGTH_SHORT).show()
        updateExecutor.execute {
            val release = try {
                UpdateChecker.latest()
            } catch (error: Exception) {
                null
            }
            val newer = release != null && UpdateChecker.isNewer(release.version, appVersion)
            if (release != null) {
                // Chỉ ghi mốc khi lấy được dữ liệu; mất mạng thì lần mở sau được thử lại ngay.
                prefs.updateLastCheckMs = System.currentTimeMillis()
                prefs.updateAvailableVersion = if (newer) release.version else ""
            }
            val offer = if (newer) release else null
            runOnUiThread {
                updateBusy = false
                pendingRelease = offer
                refreshUpdateRows()
                when {
                    offer != null -> offerUpdate(offer)
                    release == null -> if (manual) {
                        Toast.makeText(this, "Không kiểm tra được, thử lại khi có mạng.", Toast.LENGTH_LONG).show()
                    }
                    manual -> AlertDialog.Builder(this)
                        .setTitle("Bản mới nhất")
                        .setMessage("Bạn đang dùng bản $appVersion.")
                        .setPositiveButton("Đóng", null)
                        .show()
                }
            }
        }
    }

    /** Notification từ service chỉ mang số bản; mở app là lúc duy nhất hợp lý để hỏi và tải. */
    private fun maybeOfferUpdate() {
        val version = prefs.updateAvailableVersion
        if (version.isBlank()) return
        if (!UpdateChecker.isNewer(version, appVersion)) {
            prefs.updateAvailableVersion = ""
            refreshUpdateRows()
            return
        }
        val release = pendingRelease?.takeIf { it.version == version }
        if (release == null) checkUpdate(manual = false) else offerUpdate(release)
    }

    private fun offerUpdate(release: UpdateChecker.Release) {
        AlertDialog.Builder(this)
            .setTitle("Đã có bản ${release.version}")
            .setMessage("Bản đang dùng: $appVersion. Tải bản mới rồi mở trình cài đặt? Dữ liệu và mã kết nối được giữ nguyên.")
            .setPositiveButton("Cập nhật") { _, _ -> downloadUpdate(release) }
            .setNegativeButton("Để sau") { _, _ ->
                prefs.updateAvailableVersion = ""
                refreshUpdateRows()
            }
            .show()
    }

    private fun downloadUpdate(release: UpdateChecker.Release) {
        if (!packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(this)
                .setTitle("Cần cho phép cài đặt")
                .setMessage("Android chặn cài app ngoài cửa hàng cho tới khi bạn bật mục cho phép cài đặt cho Kỷ luật.")
                .setPositiveButton("Mở cài đặt") { _, _ ->
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
                .setNegativeButton("Để sau", null)
                .show()
            return
        }
        val progress = AlertDialog.Builder(this)
            .setTitle("Đang tải bản ${release.version}")
            .setMessage("Đang tải file cài đặt…")
            .setCancelable(false)
            .create()
        progress.show()
        updateExecutor.execute {
            val file = File(cacheDir, "kyluat-update.apk")
            file.delete()
            val ok = UpdateChecker.download(release.url, file) &&
                UpdateChecker.verified(this, file, appVersion)
            runOnUiThread {
                try {
                    progress.dismiss()
                } catch (error: Exception) {
                }
                if (ok) {
                    installUpdate(file)
                } else {
                    file.delete()
                    Toast.makeText(this, "Tải bản mới thất bại, thử lại khi có mạng.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun installUpdate(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.updates", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (error: Exception) {
            Toast.makeText(this, "Không mở được trình cài đặt: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        updateExecutor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val ADULT_REQUEST = 71
        private const val GUARD_REQUEST = 72
        private const val UPDATE_INTERVAL_MS = 12L * 60 * 60 * 1000
        private const val UNLOCK_REQUEST = 73
    }
}
