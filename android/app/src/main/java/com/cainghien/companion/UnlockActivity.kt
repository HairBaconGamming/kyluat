package com.cainghien.companion

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Cổng 60 giây hợp nhất: mọi luật "nới" (tắt bảo vệ, tăng quỹ, bỏ chặn app, hạ tỷ lệ quy đổi điểm)
 * đều đi qua đúng một lần cam kết. Qua cổng một lần thì được chỉnh tất cả trong
 * [UNLOCK_MINUTES] phút, khỏi phải cam kết lại cho từng mục.
 *
 * purpose = OFF: sau khi qua cổng thì tắt hẳn bảo vệ.
 * purpose = UNLOCK: chỉ mở khoá để chỉnh các luật khác.
 */
class UnlockActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var container: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private var purpose = PURPOSE_UNLOCK
    private var step = 0
    private var readLeft = READ_SECONDS
    private var finalLeft = FINAL_SECONDS
    private var finished = false

    private var readLabel: TextView? = null
    private var continueButton: UiButton? = null
    private var paragraphField: EditText? = null
    private var paragraphButton: UiButton? = null
    private var counter: TextView? = null
    private var finalRing: RingStack? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        purpose = intent.getStringExtra(EXTRA_PURPOSE) ?: PURPOSE_UNLOCK
        Ui.chrome(this)
        val page = Ui.screen(this)
        container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        page.addView(container)
        setContentView(Ui.scroll(page))
        showWarning()
        handler.post(ticker)
    }

    private fun scaffold(title: String, subtitle: String, done: Int, onBack: () -> Unit) {
        container.removeAllViews()
        container.addView(Ui.backHeader(this, title, subtitle, onBack))
        container.addView(Ui.steps(this, 3, done))
    }

    private fun showWarning() {
        step = 0
        val off = purpose == PURPOSE_OFF
        scaffold(
            if (off) "Tắt bảo vệ?" else "Mở khoá chỉnh luật?",
            "Bước 1 / 3 · đọc kỹ trước khi quyết định",
            1,
        ) { finish() }

        container.addView(Ui.warnBox(
            this,
            if (off) {
                "Khi tắt, app ngừng chặn ứng dụng, ngừng trừ quỹ mạng xã hội và ngừng cộng điểm học " +
                    "cho đến khi bạn bật lại."
            } else {
                "Qua cổng một lần, bạn được chỉnh mọi luật nới trong $UNLOCK_MINUTES phút: " +
                    "tăng quỹ, bỏ chặn app, hạ tỷ lệ quy đổi điểm. Hết giờ Kỷ luật tự khoá lại."
            },
        ))

        val group = Ui.group(this)
        if (off) {
            val blockRow = Ui.row(this, Ui.tile(this, R.drawable.ic_shield, Tone.DANGER))
            blockRow.title("Ngừng chặn app")
            blockRow.subtitle("Không còn trừ quỹ khi mở app gây xao nhãng.")
            group.addView(blockRow)
            val quotaRow = Ui.row(this, Ui.tile(this, R.drawable.ic_clock, Tone.NEUTRAL))
            quotaRow.title("Ngừng tính quỹ và điểm học")
            quotaRow.subtitle("Hôm nay không còn được bảo vệ cho đến khi bật lại.")
            group.addView(quotaRow)
            val streakRow = Ui.row(this, Ui.tile(this, R.drawable.ic_flame, Tone.WARN))
            streakRow.title("Chuỗi ngày gián đoạn")
            streakRow.subtitle("Bật lại được, nhưng hôm nay không còn được bảo vệ.")
            group.addView(streakRow)
        } else {
            val editRow = Ui.row(this, Ui.tile(this, R.drawable.ic_gear, Tone.WARN))
            editRow.title("Chỉnh luật một lần cho tất cả")
            editRow.subtitle("Không phải cam kết lại cho từng mục riêng lẻ.")
            group.addView(editRow)
            val lockRow = Ui.row(this, Ui.tile(this, R.drawable.ic_clock, Tone.NEUTRAL))
            lockRow.title("Tự khoá lại sau $UNLOCK_MINUTES phút")
            lockRow.subtitle("Hết thời gian này, muốn nới tiếp lại phải qua cổng.")
            group.addView(lockRow)
            val safeRow = Ui.row(this, Ui.tile(this, R.drawable.ic_shield_check, Tone.ACCENT))
            safeRow.title("Siết luật thì luôn miễn phí")
            safeRow.subtitle("Giảm quỹ, chặn thêm app, tăng tỷ lệ điểm không cần qua cổng.")
            group.addView(safeRow)
        }
        container.addView(group)

        val card = Ui.card(this)
        card.addView(Ui.eyebrow(this, "Thời gian suy nghĩ"))
        readLabel = Ui.label(this, "Còn $readLeft giây", 24f, Ui.TEXT, true).apply {
            setPadding(0, Ui.dp(this@UnlockActivity, 6), 0, 0)
        }
        card.addView(readLabel)
        card.addView(Ui.label(
            this,
            "Đọc hết các điều ở trên rồi chờ hết đồng hồ. Nút tiếp tục sẽ sáng lên khi bạn vẫn muốn đi tiếp.",
            12f,
            Ui.TEXT_3,
        ).apply { setPadding(0, Ui.dp(this@UnlockActivity, 4), 0, 0) })
        container.addView(card)

        val stay = Ui.button(this, if (off) "Ở lại bảo vệ" else "Để lại, không chỉnh", Variant.PRIMARY, big = true)
        stay.setOnClickListener { finish() }
        container.addView(stay, Ui.match(this))

        val next = Ui.button(this, if (off) "Tôi vẫn muốn tắt" else "Tôi vẫn muốn mở khoá", Variant.DANGER)
        next.isEnabled = readLeft <= 0
        next.setOnClickListener { showParagraph() }
        continueButton = next
        container.addView(next, Ui.match(this, topMarginDp = 7))

        container.addView(Ui.note(
            this,
            "Khẩn cấp? Cuộc gọi, Tin nhắn và Cài đặt không bao giờ bị chặn. Tắt quyền Trợ năng là dừng ngay.",
            R.drawable.ic_info,
        ).apply { setPadding(Ui.dp(this@UnlockActivity, 2), Ui.dp(this@UnlockActivity, 10), 0, 0) })
    }

    private fun paragraph(): String =
        if (purpose == PURPOSE_OFF) PARAGRAPH_OFF else PARAGRAPH_UNLOCK

    private fun showParagraph() {
        step = 1
        val text = paragraph()
        scaffold("Nhập đoạn cam kết", "Bước 2 / 3 · gõ tay, không hỗ trợ dán", 2) { showWarning() }

        container.addView(Ui.section(this, "Gõ lại đúng từng chữ"))
        container.addView(Ui.quote(this, text), Ui.match(this, bottomMarginDp = 9))

        val field = NoPasteEdit(this)
        paragraphField = field
        container.addView(field, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 150)
        ))

        counter = Ui.label(this, "0 / ${text.length}", 11.5f, Ui.TEXT_3).apply {
            gravity = Gravity.END
            setPadding(0, Ui.dp(this@UnlockActivity, 5), Ui.dp(this@UnlockActivity, 2), 0)
        }
        container.addView(counter, Ui.match(this, bottomMarginDp = 11))

        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val typed = s?.toString()?.trim() ?: ""
                counter?.text = "${typed.length} / ${text.length}"
                counter?.setTextColor(if (typed == text) Ui.ACCENT else Ui.TEXT_3)
                paragraphButton?.isEnabled = typed == text
            }
        })

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val back = Ui.button(this, "Quay lại", Variant.GHOST)
        back.setOnClickListener { showWarning() }
        row.addView(back, Ui.weight(this, 1f))
        val next = Ui.button(
            this,
            if (purpose == PURPOSE_OFF) "Tắt bảo vệ" else "Mở khoá $UNLOCK_MINUTES phút",
            Variant.DANGER,
        )
        next.isEnabled = false
        next.setOnClickListener { showFinal() }
        paragraphButton = next
        row.addView(next, Ui.weight(this, 1f, leftMarginDp = 8))
        container.addView(row, Ui.match(this))

        container.addView(Ui.note(
            this,
            "Sai một chữ cũng phải gõ lại. Sau khi nhập đúng, bạn vẫn còn $FINAL_SECONDS giây để đổi ý.",
            R.drawable.ic_info,
        ).apply { setPadding(Ui.dp(this@UnlockActivity, 2), Ui.dp(this@UnlockActivity, 10), 0, 0) })
    }

    private fun showFinal() {
        step = 2
        finalLeft = FINAL_SECONDS
        val off = purpose == PURPOSE_OFF
        scaffold(
            "Xác nhận lần cuối",
            if (off) "Bước 3 / 3 · bảo vệ tắt khi hết giờ" else "Bước 3 / 3 · mở khoá khi hết giờ",
            3,
        ) { showParagraph() }

        val hero = Ui.hero(this)
        hero.gravity = Gravity.CENTER_HORIZONTAL
        hero.addView(Ui.eyebrow(this, "Đang chờ"))
        finalRing = Ui.ringStack(this, 148, 9f, Ui.DANGER).apply {
            setValueSize(38f)
            value.text = finalLeft.toString()
            caption.text = "giây"
            setProgress(1f)
        }
        hero.addView(finalRing, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = Ui.dp(this@UnlockActivity, 13)
            bottomMargin = Ui.dp(this@UnlockActivity, 11)
        })
        hero.addView(Ui.label(
            this,
            if (off) {
                "Hết đồng hồ thì bảo vệ tắt. Bạn vẫn có thể bật lại trong Cài đặt."
            } else {
                "Hết đồng hồ thì luật được mở khoá $UNLOCK_MINUTES phút để bạn chỉnh."
            },
            12.5f,
            Ui.TEXT_2,
        ).apply { gravity = Gravity.CENTER })
        container.addView(hero)

        val cancel = Ui.button(
            this,
            if (off) "Đổi ý, giữ bảo vệ" else "Đổi ý, giữ luật",
            Variant.PRIMARY,
            big = true,
        )
        cancel.setOnClickListener { finish() }
        container.addView(cancel, Ui.match(this))
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (step == 0) {
                readLeft -= 1
                if (readLeft <= 0) {
                    readLeft = 0
                    readLabel?.text = "Bạn có thể tiếp tục"
                    readLabel?.setTextColor(Ui.TEXT_2)
                    continueButton?.isEnabled = true
                } else {
                    readLabel?.text = "Còn $readLeft giây"
                }
            } else if (step == 2) {
                finalLeft -= 1
                val shown = finalLeft.coerceAtLeast(0)
                finalRing?.value?.text = shown.toString()
                finalRing?.setProgress(shown.toFloat() / FINAL_SECONDS)
                if (finalLeft <= 0) {
                    finishGate()
                    return
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun finishGate() {
        if (finished) return
        finished = true
        prefs.grantUnlock(System.currentTimeMillis(), SystemClock.elapsedRealtime())
        if (purpose == PURPOSE_OFF) {
            turnOffProtection(prefs)
            Toast.makeText(this, "Đã tắt bảo vệ. Bạn có thể bật lại trong Cài đặt.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Đã mở khoá chỉnh luật trong $UNLOCK_MINUTES phút.", Toast.LENGTH_LONG).show()
        }
        setResult(RESULT_OK)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PURPOSE = "purpose"
        const val PURPOSE_OFF = "off"
        const val PURPOSE_UNLOCK = "unlock"
        private const val READ_SECONDS = 60
        private const val FINAL_SECONDS = 60

        const val PARAGRAPH_OFF =
            "Tôi hiểu rằng tắt Kỷ luật là tự mình phá bỏ ranh giới đã đặt ra. " +
                "Tôi đang chọn sự thoải mái trước mắt thay vì mục tiêu dài hạn, và tôi chấp nhận mất chuỗi ngày, " +
                "mất quỹ mạng xã hội hôm nay, và mất lớp bảo vệ mà tôi đã dựng nên. Tôi vẫn muốn tiếp tục."

        const val PARAGRAPH_UNLOCK =
            "Tôi hiểu rằng nới luật là làm yếu ranh giới tôi đã tự đặt ra. " +
                "Tôi chỉ chỉnh trong ít phút này rồi để Kỷ luật khoá lại, không mượn cớ để lướt nhiều hơn."
    }
}

/**
 * Tắt bảo vệ là dừng hẳn: không để lớp cản gỡ nằm lại khi người dùng đã chọn dừng.
 * Dùng chung cho cổng 60 giây và hộp thoại xác nhận trong Cài đặt.
 */
internal fun Context.turnOffProtection(prefs: Prefs) {
    prefs.protectionEnabled = false
    prefs.adultFilterEnabled = false
    prefs.clearLocalFocus()
    prefs.lockRules()
    Runtime.remoteManualFocus = false
    Runtime.focusActive = false
    try {
        stopService(Intent(this, AdultDnsVpnService::class.java))
    } catch (error: Exception) {
    }
    try {
        val manager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        manager.removeActiveAdmin(ComponentName(this, GuardAdminReceiver::class.java))
    } catch (error: Exception) {
    }
    prefs.uninstallGuard = false
}
