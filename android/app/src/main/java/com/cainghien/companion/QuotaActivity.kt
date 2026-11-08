package com.cainghien.companion

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Đổi mức quỹ mỗi ngày. Tăng quỹ là nới luật nên phải qua cổng 60 giây (một lần cho mọi luật);
 * giảm quỹ là siết luật nên áp dụng ngay, miễn phí. Tăng vẫn chỉ có hiệu lực từ 0:00 hôm sau.
 */
class QuotaActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var quota: QuotaManager
    private lateinit var container: LinearLayout

    private var chosen = 0
    private var previewLabel: TextView? = null
    private var bonusLabel: TextView? = null
    private var pickButton: UiButton? = null
    private var pendingAction: (() -> Unit)? = null

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
    }

    private fun showPicker() {
        val current = quota.limitMinutes
        val pending = quota.pendingMinutes
        container.removeAllViews()
        container.addView(Ui.backHeader(this, "Quỹ mạng xã hội", "Chọn mức dùng mỗi ngày") { finish() })

        val hero = Ui.hero(this)
        hero.addView(Ui.eyebrow(this, "Mức đang áp dụng"))
        val value = Ui.label(this, "$current phút", 34f, Ui.TEXT, true)
        value.letterSpacing = -0.03f
        value.setPadding(0, Ui.dp(this, 4), 0, 0)
        hero.addView(value)
        hero.addView(Ui.label(this, "dùng chung cho cả nhóm app đã chặn", 12f, Ui.TEXT_3)
            .apply { setPadding(0, Ui.dp(this@QuotaActivity, 2), 0, 0) })
        bonusLabel = Ui.label(this, bonusText(), 12f, Ui.ACCENT, true).apply {
            setPadding(0, Ui.dp(this@QuotaActivity, 8), 0, 0)
            setLineSpacing(0f, 1.35f)
        }
        hero.addView(bonusLabel)
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
            updatePickButton(current)
        }
        card.addView(segmented, Ui.match(this, topMarginDp = 8))
        previewLabel = Ui.label(this, previewText(current, chosen), 12f, Ui.TEXT_2).apply {
            setLineSpacing(0f, 1.35f)
        }
        card.addView(previewLabel, Ui.match(this, topMarginDp = 11))
        container.addView(card, Ui.match(this, bottomMarginDp = 11))

        val next = Ui.button(this, "", Variant.PRIMARY, big = true)
        next.setOnClickListener { onPick(current) }
        pickButton = next
        container.addView(next, Ui.match(this))
        updatePickButton(current)

        container.addView(Ui.note(
            this,
            "Tăng quỹ phải qua cổng cam kết 60 giây và chờ đến 0:00 hôm sau. " +
                "Giảm quỹ áp dụng ngay, không cần cam kết.",
            R.drawable.ic_info,
        ).apply { setPadding(Ui.dp(this@QuotaActivity, 2), Ui.dp(this@QuotaActivity, 10), 0, 0) })
    }

    private fun bonusText(): String {
        val bonus = quota.bonusMinutes()
        val points = quota.pointsToday()
        return if (bonus > 0) {
            "Học tập hôm nay: $points điểm · thưởng thêm $bonus phút quỹ"
        } else {
            "Học tập hôm nay: $points điểm · chưa đủ quy đổi phút thưởng"
        }
    }

    private fun updatePickButton(current: Int) {
        val button = pickButton ?: return
        when {
            chosen == current -> {
                button.text = "Đây là mức hiện tại"
                button.isEnabled = false
            }
            chosen > current -> {
                button.text = "Tăng lên $chosen phút · cần cam kết"
                button.isEnabled = true
            }
            else -> {
                button.text = "Giảm xuống $chosen phút · áp dụng ngay"
                button.isEnabled = true
            }
        }
    }

    private fun previewText(current: Int, target: Int): String {
        if (target == current) return "Đây là mức đang áp dụng, không có gì thay đổi."
        val usedMinutes = quota.usedSeconds() / 60
        val left = quota.remainingSecondsFor(target) / 60
        return when {
            target > current ->
                "Tăng quỹ chỉ có hiệu lực từ 0:00 hôm sau và phải qua cổng cam kết. Hôm nay vẫn là $current phút."
            left == 0 ->
                "Giảm xuống $target phút có hiệu lực ngay. Hôm nay đã dùng $usedMinutes phút nên quỹ khoá lại."
            else ->
                "Giảm xuống $target phút có hiệu lực ngay, không cần cam kết. Hôm nay còn $left phút."
        }
    }

    private fun onPick(current: Int) {
        when {
            chosen == current -> return
            chosen > current -> requireUnlock {
                quota.schedule(chosen)
                Toast.makeText(this, "Đã hẹn mức $chosen phút từ 0:00 hôm sau.", Toast.LENGTH_LONG).show()
                setResult(RESULT_OK)
                finish()
            }
            else -> {
                quota.applyNow(chosen)
                Toast.makeText(this, "Quỹ hôm nay là $chosen phút.", Toast.LENGTH_LONG).show()
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    private fun requireUnlock(then: () -> Unit) {
        if (prefs.rulesUnlocked(System.currentTimeMillis(), SystemClock.elapsedRealtime())) {
            then()
            return
        }
        pendingAction = then
        startActivityForResult(
            Intent(this, UnlockActivity::class.java)
                .putExtra(UnlockActivity.EXTRA_PURPOSE, UnlockActivity.PURPOSE_UNLOCK),
            UNLOCK_REQUEST,
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == UNLOCK_REQUEST) {
            if (resultCode == RESULT_OK) {
                pendingAction?.invoke()
            } else {
                showPicker()
            }
            pendingAction = null
        }
    }

    companion object {
        private const val UNLOCK_REQUEST = 91
    }
}
