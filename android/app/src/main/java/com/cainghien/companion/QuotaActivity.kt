package com.cainghien.companion

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class QuotaActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var quota: QuotaManager
    private lateinit var container: LinearLayout
    private val handler = Handler(Looper.getMainLooper())

    private var step = 0
    private var chosen = 0
    private var waitLeft = WAIT_SECONDS
    private var applied = false

    private var previewLabel: TextView? = null
    private var pickButton: UiButton? = null
    private var counter: TextView? = null
    private var commitButton: UiButton? = null
    private var waitRing: RingStack? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        quota = QuotaManager(prefs)
        quota.resetIfNeeded()
        chosen = quota.limitMinutes
        Ui.chrome(this)
        val page = Ui.screen(this)
        container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        page.addView(container)
        setContentView(Ui.scroll(page))
        showPicker()
        handler.post(ticker)
    }

    private fun scaffold(title: String, subtitle: String, done: Int, onBack: () -> Unit) {
        container.removeAllViews()
        container.addView(Ui.backHeader(this, title, subtitle, onBack))
        container.addView(Ui.steps(this, 3, done))
    }

    private fun showPicker() {
        step = 0
        val current = quota.limitMinutes
        val pending = quota.pendingMinutes
        scaffold("Quỹ mạng xã hội", "Bước 1 / 3 · chọn mức dùng mỗi ngày", 1) { finish() }

        val hero = Ui.hero(this)
        hero.addView(Ui.eyebrow(this, "Mức đang áp dụng"))
        val value = Ui.label(this, "$current phút", 34f, Ui.TEXT, true)
        value.letterSpacing = -0.03f
        value.setPadding(0, Ui.dp(this, 4), 0, 0)
        hero.addView(value)
        hero.addView(Ui.label(this, "dùng chung cho cả nhóm app đã chặn", 12f, Ui.TEXT_3)
            .apply { setPadding(0, Ui.dp(this@QuotaActivity, 2), 0, 0) })
        if (pending > 0 && pending != current) {
            hero.addView(Ui.label(
                this,
                "Đã hẹn mức $pending phút từ 0:00 hôm sau.",
                12f,
                Ui.WARN,
                true,
            ).apply { setPadding(0, Ui.dp(this@QuotaActivity, 9), 0, 0) })
            val cancelPending = Ui.button(this, "Hủy lịch đổi", Variant.GHOST)
            cancelPending.setOnClickListener {
                quota.cancelPending()
                Toast.makeText(this, "Đã hủy lịch đổi quỹ", Toast.LENGTH_SHORT).show()
                showPicker()
            }
            hero.addView(cancelPending, Ui.match(this, topMarginDp = 9))
        }
        container.addView(hero, Ui.match(this, bottomMarginDp = 11))

        container.addView(Ui.section(this, "Chọn mức mới"))
        val card = Ui.card(this)
        card.addView(Ui.eyebrow(this, "Phút mỗi ngày"))
        val labels = QuotaManager.PRESETS.map { it.toString() }
        val segmented = Ui.segmented(this, labels, QuotaManager.PRESETS.indexOf(chosen)) { index ->
            chosen = QuotaManager.PRESETS[index]
            previewLabel?.text = previewText(current, chosen)
            pickButton?.text = if (chosen == current) "Đây là mức hiện tại" else "Tiếp tục · $chosen phút"
            pickButton?.isEnabled = chosen != current
        }
        card.addView(segmented, Ui.match(this, topMarginDp = 8))
        previewLabel = Ui.label(this, previewText(current, chosen), 12f, Ui.TEXT_2).apply {
            setLineSpacing(0f, 1.35f)
        }
        card.addView(previewLabel, Ui.match(this, topMarginDp = 11))
        container.addView(card, Ui.match(this, bottomMarginDp = 11))

        val next = Ui.button(this, if (chosen == current) "Đây là mức hiện tại" else "Tiếp tục · $chosen phút",
            Variant.PRIMARY, big = true)
        next.isEnabled = chosen != current
        next.setOnClickListener { showParagraph() }
        pickButton = next
        container.addView(next, Ui.match(this))

        container.addView(Ui.note(
            this,
            "Mọi lần đổi mức đều phải gõ lại đoạn cam kết và chờ 30 giây.",
            R.drawable.ic_info,
        ).apply { setPadding(Ui.dp(this@QuotaActivity, 2), Ui.dp(this@QuotaActivity, 10), 0, 0) })
    }

    private fun previewText(current: Int, target: Int): String {
        if (target == current) return "Đây là mức đang áp dụng, không có gì thay đổi."
        val usedMinutes = quota.usedSeconds() / 60
        val left = quota.remainingSecondsFor(target) / 60
        return if (target > current) {
            "Tăng quỹ chỉ có hiệu lực từ 0:00 hôm sau. Hôm nay vẫn là $current phút."
        } else if (left == 0) {
            "Giảm xuống $target phút có hiệu lực ngay. Hôm nay đã dùng $usedMinutes phút nên quỹ khoá lại."
        } else {
            "Giảm xuống $target phút có hiệu lực ngay. Hôm nay còn $left phút."
        }
    }

    private fun showParagraph() {
        step = 1
        val paragraph = commitment(chosen)
        scaffold("Nhập đoạn cam kết", "Bước 2 / 3 · gõ tay, không hỗ trợ dán", 2) { showPicker() }

        container.addView(Ui.section(this, "Gõ lại đúng từng chữ"))
        container.addView(Ui.quote(this, paragraph), Ui.match(this, bottomMarginDp = 9))

        val field = NoPasteEdit(this)
        container.addView(field, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 130)
        ))

        counter = Ui.label(this, "0 / ${paragraph.length}", 11.5f, Ui.TEXT_3).apply {
            gravity = Gravity.END
            setPadding(0, Ui.dp(this@QuotaActivity, 5), Ui.dp(this@QuotaActivity, 2), 0)
        }
        container.addView(counter, Ui.match(this, bottomMarginDp = 11))

        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString()?.trim() ?: ""
                counter?.text = "${text.length} / ${paragraph.length}"
                counter?.setTextColor(if (text == paragraph) Ui.ACCENT else Ui.TEXT_3)
                commitButton?.isEnabled = text == paragraph
            }
        })

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val back = Ui.button(this, "Quay lại", Variant.GHOST)
        back.setOnClickListener { showPicker() }
        row.addView(back, Ui.weight(this, 1f))
        val next = Ui.button(this, "Xác nhận mức $chosen phút", Variant.PRIMARY)
        next.isEnabled = false
        next.setOnClickListener { showWait() }
        commitButton = next
        row.addView(next, Ui.weight(this, 1f, leftMarginDp = 8))
        container.addView(row, Ui.match(this))

        container.addView(Ui.note(
            this,
            "Sai một chữ cũng phải gõ lại. Sau khi nhập đúng, bạn vẫn còn $WAIT_SECONDS giây để đổi ý.",
            R.drawable.ic_info,
        ).apply { setPadding(Ui.dp(this@QuotaActivity, 2), Ui.dp(this@QuotaActivity, 10), 0, 0) })
    }

    private fun showWait() {
        step = 2
        waitLeft = WAIT_SECONDS
        val current = quota.limitMinutes
        scaffold("Xác nhận lần cuối", "Bước 3 / 3 · mức mới ghi nhận khi hết giờ", 3) { showParagraph() }

        val hero = Ui.hero(this)
        hero.gravity = Gravity.CENTER_HORIZONTAL
        hero.addView(Ui.eyebrow(this, "Đang chờ"))
        waitRing = Ui.ringStack(this, 148, 9f).apply {
            setValueSize(38f)
            value.text = waitLeft.toString()
            caption.text = "giây"
            setProgress(1f)
        }
        hero.addView(waitRing, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = Ui.dp(this@QuotaActivity, 13)
            bottomMargin = Ui.dp(this@QuotaActivity, 11)
        })
        hero.addView(Ui.label(
            this,
            if (chosen > current) {
                "Hết đồng hồ thì mức $chosen phút có hiệu lực từ 0:00 hôm sau."
            } else {
                "Hết đồng hồ thì mức $chosen phút áp dụng luôn cho hôm nay."
            },
            12.5f,
            Ui.TEXT_2,
        ).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.35f)
        })
        container.addView(hero)

        val cancel = Ui.button(this, "Đổi ý, giữ mức $current phút", Variant.GHOST, big = true)
        cancel.setOnClickListener { finish() }
        container.addView(cancel, Ui.match(this))
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (step == 2) {
                waitLeft -= 1
                val shown = waitLeft.coerceAtLeast(0)
                waitRing?.value?.text = shown.toString()
                waitRing?.setProgress(shown.toFloat() / WAIT_SECONDS)
                if (waitLeft <= 0) {
                    applyChoice()
                    return
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun applyChoice() {
        if (applied) return
        applied = true
        val current = quota.limitMinutes
        if (chosen > current) {
            quota.schedule(chosen)
            Toast.makeText(this, "Đã hẹn mức $chosen phút từ 0:00 hôm sau.", Toast.LENGTH_LONG).show()
        } else {
            quota.applyNow(chosen)
            Toast.makeText(this, "Quỹ hôm nay là $chosen phút.", Toast.LENGTH_LONG).show()
        }
        setResult(RESULT_OK)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val WAIT_SECONDS = 30

        fun commitment(minutes: Int): String =
            "Tôi tự chọn mức $minutes phút mỗi ngày. Quỹ này để giữ giờ học, " +
                "không phải để nới ra mỗi khi hết giờ."
    }
}
