package com.cainghien.companion

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class OffActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var container: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
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
        scaffold("Tắt bảo vệ?", "Bước 1 / 3 · đọc kỹ trước khi quyết định", 1) { finish() }

        container.addView(Ui.warnBox(
            this,
            "Khi tắt, app ngừng chặn ứng dụng, ngừng trừ quỹ mạng xã hội và cảnh báo không còn tác dụng cho đến khi bạn bật lại.",
        ))

        val group = Ui.group(this)
        val blockRow = Ui.row(this, Ui.tile(this, R.drawable.ic_shield, Tone.DANGER))
        blockRow.title("Ngừng chặn app")
        blockRow.subtitle("Không còn cảnh báo khi mở app gây xao nhãng.")
        group.addView(blockRow)
        val quotaRow = Ui.row(this, Ui.tile(this, R.drawable.ic_clock, Tone.NEUTRAL))
        quotaRow.title("Hủy quỹ hôm nay")
        quotaRow.subtitle("${(QuotaManager(prefs).remainingSeconds() + 59) / 60} phút mạng xã hội còn lại sẽ mất.")
        group.addView(quotaRow)
        val streakRow = Ui.row(this, Ui.tile(this, R.drawable.ic_flame, Tone.WARN))
        streakRow.title("Chuỗi ngày gián đoạn")
        streakRow.subtitle("Bật lại được, nhưng hôm nay không còn được bảo vệ.")
        group.addView(streakRow)
        container.addView(group)

        val card = Ui.card(this)
        card.addView(Ui.eyebrow(this, "Thời gian suy nghĩ"))
        readLabel = Ui.label(this, "Còn $readLeft giây", 24f, Ui.TEXT, true).apply {
            setPadding(0, Ui.dp(this@OffActivity, 6), 0, 0)
        }
        card.addView(readLabel)
        card.addView(Ui.label(
            this,
            "Đọc hết ba điều ở trên rồi chờ hết đồng hồ. Nút tắt sẽ sáng lên khi bạn vẫn muốn tiếp tục.",
            12f,
            Ui.TEXT_3,
        ).apply { setPadding(0, Ui.dp(this@OffActivity, 4), 0, 0) })
        container.addView(card)

        val stay = Ui.button(this, "Ở lại bảo vệ", Variant.PRIMARY, big = true)
        stay.setOnClickListener { finish() }
        container.addView(stay, Ui.match(this))

        val next = Ui.button(this, "Tôi vẫn muốn tắt", Variant.DANGER)
        next.isEnabled = readLeft <= 0
        next.setOnClickListener { showParagraph() }
        continueButton = next
        container.addView(next, Ui.match(this, topMarginDp = 7))

        container.addView(Ui.note(
            this,
            "Khẩn cấp? Cuộc gọi, Tin nhắn và Cài đặt không bao giờ bị chặn. Tắt quyền Trợ năng là dừng ngay.",
            R.drawable.ic_info,
        ).apply { setPadding(Ui.dp(this@OffActivity, 2), Ui.dp(this@OffActivity, 10), 0, 0) })
    }

    private fun showParagraph() {
        step = 1
        scaffold("Nhập đoạn cam kết", "Bước 2 / 3 · gõ tay, không hỗ trợ dán", 2) { showWarning() }

        container.addView(Ui.section(this, "Gõ lại đúng từng chữ"))
        val quote = Ui.quote(this, PARAGRAPH)
        container.addView(quote, Ui.match(this, bottomMarginDp = 9))

        val field = NoPasteEdit(this)
        paragraphField = field
        container.addView(field, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 150)
        ))

        counter = Ui.label(this, "0 / ${PARAGRAPH.length}", 11.5f, Ui.TEXT_3).apply {
            gravity = Gravity.END
            setPadding(0, Ui.dp(this@OffActivity, 5), Ui.dp(this@OffActivity, 2), 0)
        }
        container.addView(counter, Ui.match(this, bottomMarginDp = 11))

        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString()?.trim() ?: ""
                counter?.text = "${text.length} / ${PARAGRAPH.length}"
                counter?.setTextColor(if (text == PARAGRAPH) Ui.ACCENT else Ui.TEXT_3)
                paragraphButton?.isEnabled = text == PARAGRAPH
            }
        })

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val back = Ui.button(this, "Quay lại", Variant.GHOST)
        back.setOnClickListener { showWarning() }
        row.addView(back, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val next = Ui.button(this, "Tắt bảo vệ", Variant.DANGER)
        next.isEnabled = false
        next.setOnClickListener { showFinal() }
        paragraphButton = next
        row.addView(next, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = Ui.dp(this@OffActivity, 8)
        })
        container.addView(row, Ui.match(this))

        container.addView(Ui.note(
            this,
            "Sai một chữ cũng phải gõ lại. Sau khi nhập đúng, bạn vẫn còn $FINAL_SECONDS giây để đổi ý.",
            R.drawable.ic_info,
        ).apply { setPadding(Ui.dp(this@OffActivity, 2), Ui.dp(this@OffActivity, 10), 0, 0) })
    }

    private fun showFinal() {
        step = 2
        finalLeft = FINAL_SECONDS
        scaffold("Xác nhận lần cuối", "Bước 3 / 3 · bảo vệ tắt khi hết giờ", 3) { showParagraph() }

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
            topMargin = Ui.dp(this@OffActivity, 13)
            bottomMargin = Ui.dp(this@OffActivity, 11)
        })
        hero.addView(Ui.label(
            this,
            "Hết đồng hồ thì bảo vệ tắt. Bạn vẫn có thể bật lại trong Cài đặt.",
            12.5f,
            Ui.TEXT_2,
        ).apply { gravity = Gravity.CENTER })
        container.addView(hero)

        val cancel = Ui.button(this, "Đổi ý, giữ bảo vệ", Variant.PRIMARY, big = true)
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
                    finishOff()
                    return
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun finishOff() {
        if (finished) return
        finished = true
        prefs.protectionEnabled = false
        QuotaManager(prefs).stopBreak()
        Toast.makeText(this, "Đã tắt bảo vệ. Bạn có thể bật lại trong Cài đặt.", Toast.LENGTH_LONG).show()
        setResult(RESULT_OK)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val READ_SECONDS = 60
        private const val FINAL_SECONDS = 60

        const val PARAGRAPH =
            "Tôi hiểu rằng tắt Kỷ luật là tự mình phá bỏ ranh giới đã đặt ra. " +
                "Tôi đang chọn sự thoải mái trước mắt thay vì mục tiêu dài hạn, và tôi chấp nhận mất chuỗi ngày, " +
                "mất quỹ mạng xã hội hôm nay, và mất lớp bảo vệ mà tôi đã dựng nên. Tôi vẫn muốn tiếp tục."
    }
}
