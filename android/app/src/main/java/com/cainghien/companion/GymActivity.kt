package com.cainghien.companion

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class GymActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var secure: SecureStore
    private lateinit var statusPill: UiPill
    private lateinit var heroRing: RingStack
    private lateinit var ringCaption: TextView
    private lateinit var dots: List<View>
    private lateinit var dotsBox: LinearLayout
    private lateinit var watchRow: UiRow
    private lateinit var weekTile: UiStat
    private lateinit var streakTile: UiStat
    private lateinit var lastTile: UiStat
    private lateinit var openButton: UiButton
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var gymWeek = 0
    private var gymGoal = 0
    private var streakWeeks = 0
    private var paired = false
    private var dotsDone = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        secure = SecureStore(this)
        Ui.chrome(this)
        setContentView(Ui.scroll(buildPage()))
        handler.post(updater)
        refresh()
    }

    private fun buildPage(): LinearLayout {
        val page = Ui.screen(this)
        page.addView(Ui.backHeader(this, "Buổi tập", "10 phút liên tục = 20 điểm · 60 phút = 100 điểm") { finish() })

        val hero = Ui.hero(this)
        statusPill = Ui.pill(this, "Chưa bắt đầu", Tone.NEUTRAL)
        hero.addView(statusPill, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL })

        heroRing = Ui.ringStack(this, 186, 10f)
        heroRing.setValueSize(36f)
        heroRing.value.text = "0:00"
        heroRing.caption.text = "còn 10:00"
        hero.addView(heroRing, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = Ui.dp(this@GymActivity, 16)
            bottomMargin = Ui.dp(this@GymActivity, 12)
        })

        ringCaption = Ui.label(this, "Bấm “Mở app tập” để bắt đầu đếm.", 13.5f, Ui.TEXT_2)
        ringCaption.gravity = Gravity.CENTER
        hero.addView(ringCaption)

        dotsBox = LinearLayout(this)
        dotsBox.orientation = LinearLayout.HORIZONTAL
        dotsBox.gravity = Gravity.CENTER
        val dotsRow = mutableListOf<View>()
        repeat(GYM_TIERS.size) { index ->
            val dot = View(this)
            dot.background = Ui.rounded(Ui.STEP_OFF, 2, this)
            dotsRow.add(dot)
            dotsBox.addView(dot, LinearLayout.LayoutParams(Ui.dp(this, 15), Ui.dp(this, 4)).apply {
                if (index < GYM_TIERS.size - 1) rightMargin = Ui.dp(this@GymActivity, 4)
            })
        }
        dots = dotsRow
        hero.addView(dotsBox, Ui.match(this, topMarginDp = 12))

        openButton = Ui.button(this, "Mở app tập", Variant.PRIMARY, big = true)
        openButton.setOnClickListener { openGym() }
        hero.addView(openButton, Ui.match(this, topMarginDp = 15))

        val change = Ui.button(this, "Đổi app tập", Variant.GHOST)
        change.setOnClickListener { startActivity(Intent(this, GymPickerActivity::class.java)) }
        hero.addView(change, Ui.match(this, topMarginDp = 7))
        page.addView(hero)

        page.addView(Ui.section(this, "Tập với camera"))
        val exercises = Ui.group(this)
        val exerciseRow = Ui.row(this, Ui.tile(this, R.drawable.ic_dumbbell, Tone.ACCENT))
        exerciseRow.title("Chống đẩy · Squat · Plank")
        exerciseRow.subtitle("Tự đếm động tác và cộng điểm học trên điện thoại")
        exerciseRow.trailing(Ui.chevron(this))
        exerciseRow.makeClickable { startActivity(Intent(this, ExerciseActivity::class.java)) }
        exercises.addView(exerciseRow)
        page.addView(exercises)

        page.addView(Ui.section(this, "Đang theo dõi"))
        val group = Ui.group(this)
        watchRow = Ui.row(this, Ui.tile(this, R.drawable.ic_dumbbell, Tone.ACCENT))
        watchRow.title("App tập")
        watchRow.subtitle(Apps.label(this, prefs.gymPackage))
        watchRow.trailing(Ui.chevron(this))
        watchRow.makeClickable { startActivity(Intent(this, GymPickerActivity::class.java)) }
        group.addView(watchRow)
        page.addView(group)

        weekTile = Ui.stat(this, "Tuần này")
        streakTile = Ui.stat(this, "Chuỗi tuần")
        lastTile = Ui.stat(this, "Lần trước")
        streakTile.hideBar()
        lastTile.hideBar()
        page.addView(statRow(listOf(weekTile, streakTile, lastTile)))

        page.addView(Ui.note(
            this,
            "Rời app tập hoặc tắt màn hình sẽ đếm lại từ đầu. " +
                "Mỗi bậc thời gian chỉ được cộng một lần trong buổi tập.",
            R.drawable.ic_clock,
        ))
        return page
    }

    private fun statRow(tiles: List<UiStat>): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        tiles.forEachIndexed { index, tile ->
            row.addView(tile, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index > 0) leftMargin = Ui.dp(this@GymActivity, 8)
            })
        }
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = Ui.dp(this, 11)
        row.layoutParams = params
        return row
    }

    private val updater = object : Runnable {
        override fun run() {
            renderTimer()
            handler.postDelayed(this, 1000)
        }
    }

    private fun renderTimer() {
        val running = Runtime.gymStartElapsed > 0L
        val seconds = if (running) {
            ((SystemClock.elapsedRealtime() - Runtime.gymStartElapsed) / 1000).toInt().coerceIn(0, MAX_SECONDS)
        } else if (Runtime.lastGymSeconds > 0) {
            Runtime.lastGymSeconds.coerceAtMost(MAX_SECONDS)
        } else {
            0
        }
        val points = gymPointsFor(seconds)
        val next = gymSecondsToNextTier(seconds)

        heroRing.value.text = Ui.clockText(seconds)
        heroRing.caption.text = if (next == 0) "trọn 60 phút" else "còn ${Ui.clockText(next)}"
        heroRing.setProgress(seconds.toFloat() / MAX_SECONDS)
        heroRing.setArcColor(if (points > 0) Ui.ACCENT else Ui.TEXT_2)

        val done = GYM_TIERS.count { seconds >= it.first }
        if (done != dotsDone) {
            dotsDone = done
            dots.forEachIndexed { index, dot ->
                dot.background = Ui.rounded(if (index < done) Ui.ACCENT else Ui.STEP_OFF, 2, this)
            }
        }

        when {
            running -> {
                statusPill.set(if (points > 0) "$points điểm" else "Đang đếm", Tone.ACCENT)
                ringCaption.text = if (next == 0) {
                    "Buổi tập dài nhất được tính · 100 điểm."
                } else {
                    "Giữ app tập trên màn hình · ${Ui.clockText(next)} nữa lên ${gymPointsFor(seconds + next)} điểm."
                }
                ringCaption.setTextColor(Ui.TEXT_2)
            }
            Runtime.lastGymAwarded -> {
                statusPill.set("Đã tính 1 buổi", Tone.ACCENT)
                ringCaption.text = "Buổi tập gần nhất ${Ui.clockText(seconds)} · ${points} điểm."
                ringCaption.setTextColor(Ui.ACCENT)
            }
            seconds > 0 -> {
                statusPill.set("Chưa đủ 10 phút", Tone.WARN)
                ringCaption.text = "Lần trước bị gián đoạn nên chưa tính buổi."
                ringCaption.setTextColor(Ui.TEXT_2)
            }
            else -> {
                statusPill.set("Chưa bắt đầu", Tone.NEUTRAL)
                ringCaption.text = "Bấm “Mở app tập” để bắt đầu đếm."
                ringCaption.setTextColor(Ui.TEXT_2)
            }
        }

        openButton.text = if (running) "Đang mở app tập" else "Mở app tập"
        watchRow.status(
            if (!paired) "Chưa nối máy tính" else if (running) "Đang đếm" else "Sẵn sàng",
            if (!paired) Ui.TEXT_3 else if (running) Ui.ACCENT else Ui.TEXT_3,
        )
    }

    private fun renderStats() {
        lastTile.value.text = if (Runtime.lastGymSeconds > 0) Ui.clockText(Runtime.lastGymSeconds) else "—"
        weekTile.value.text = if (paired) "$gymWeek/$gymGoal" else "—"
        weekTile.setBar(if (gymGoal > 0) gymWeek.toFloat() / gymGoal else 0f)
        streakTile.value.text = if (paired) "$streakWeeks" else "—"
        watchRow.subtitle(Apps.label(this, prefs.gymPackage))
    }

    private fun openGym() {
        val intent = packageManager.getLaunchIntentForPackage(prefs.gymPackage)
        if (intent == null) {
            Toast.makeText(this, "Chưa cài app này. Hãy chọn app tập khác.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, GymPickerActivity::class.java))
        } else {
            startActivity(intent)
        }
    }

    private fun refresh() {
        val pair = secure.load()
        paired = pair != null
        renderTimer()
        renderStats()
        if (pair == null) return
        executor.execute {
            try {
                val summary = Api.summary(pair.baseUrl, pair.token)
                runOnUiThread {
                    gymWeek = summary.gymWeek
                    gymGoal = summary.gymGoal.coerceAtLeast(1)
                    streakWeeks = summary.gymStreakWeeks
                    paired = true
                    renderTimer()
                    renderStats()
                }
            } catch (error: Exception) {
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val MAX_SECONDS = 3600
    }
}
