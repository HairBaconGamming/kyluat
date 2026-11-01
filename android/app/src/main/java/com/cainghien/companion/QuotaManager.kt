package com.cainghien.companion

import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class QuotaManager(private val prefs: Prefs) {
    private val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    val limitMinutes: Int
        @Synchronized get() = prefs.quotaMinutes.coerceIn(MIN_MINUTES, MAX_MINUTES)

    val limitSeconds: Int get() = limitMinutes * 60

    val pendingMinutes: Int
        @Synchronized get() = prefs.quotaPendingMinutes.coerceIn(0, MAX_MINUTES)

    @Synchronized
    fun resetIfNeeded(now: Long = System.currentTimeMillis()) {
        val today = formatter.format(Date(now))
        if (prefs.quotaDate != today) {
            prefs.quotaDate = today
            prefs.quotaUsedSeconds = 0
            prefs.breakUntilMs = 0L
            val pending = prefs.quotaPendingMinutes
            if (pending > 0) {
                prefs.quotaMinutes = pending.coerceIn(MIN_MINUTES, MAX_MINUTES)
                prefs.quotaPendingMinutes = 0
            }
        }
    }

    @Synchronized
    fun usedSeconds(now: Long = System.currentTimeMillis()): Int {
        resetIfNeeded(now)
        return prefs.quotaUsedSeconds.coerceIn(0, limitSeconds)
    }

    @Synchronized
    fun remainingSeconds(now: Long = System.currentTimeMillis()): Int =
        max(0, limitSeconds - usedSeconds(now))

    @Synchronized
    fun remainingSecondsFor(minutes: Int, now: Long = System.currentTimeMillis()): Int =
        max(0, minutes * 60 - usedSeconds(now))

    @Synchronized
    fun isExhausted(now: Long = System.currentTimeMillis()): Boolean = remainingSeconds(now) <= 0

    @Synchronized
    fun isBreakActive(now: Long = System.currentTimeMillis()): Boolean =
        prefs.breakUntilMs > now && !isExhausted(now)

    @Synchronized
    fun breakRemainingSeconds(now: Long = System.currentTimeMillis()): Int =
        max(0, (prefs.breakUntilMs - now) / 1000).toInt()

    @Synchronized
    fun startBreak(minutes: Int, now: Long = System.currentTimeMillis()): Boolean {
        if (!prefs.protectionEnabled || isExhausted(now)) return false
        val remainingMs = remainingSeconds(now) * 1000L
        val requestedMs = minutes.coerceIn(1, 60) * 60_000L
        prefs.breakUntilMs = now + min(remainingMs, requestedMs)
        return true
    }

    @Synchronized
    fun stopBreak() {
        prefs.breakUntilMs = 0L
    }

    /** Mức mới có hiệu lực ngay, đồng thời hủy lịch đổi đang hẹn. */
    @Synchronized
    fun applyNow(minutes: Int) {
        prefs.quotaMinutes = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
        prefs.quotaPendingMinutes = 0
    }

    /** Mức cao hơn chỉ được ghi nhận từ lần làm mới 0:00 kế tiếp. */
    @Synchronized
    fun schedule(minutes: Int) {
        prefs.quotaPendingMinutes = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
    }

    @Synchronized
    fun cancelPending() {
        prefs.quotaPendingMinutes = 0
    }

    @Synchronized
    fun consume(seconds: Int, now: Long = System.currentTimeMillis()): Boolean {
        if (seconds <= 0) return isExhausted(now)
        resetIfNeeded(now)
        val used = (prefs.quotaUsedSeconds + seconds).coerceAtMost(limitSeconds)
        prefs.quotaUsedSeconds = used
        if (used >= limitSeconds) {
            prefs.breakUntilMs = 0L
        }
        return isExhausted(now)
    }

    fun secondsUntilReset(now: Long = System.currentTimeMillis()): Int {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = now
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return max(0, ((calendar.timeInMillis - now) / 1000).toInt())
    }

    companion object {
        const val MIN_MINUTES = 15
        const val MAX_MINUTES = 120
        const val STEP_MINUTES = 15
        val PRESETS = (MIN_MINUTES..MAX_MINUTES step STEP_MINUTES).toList()
    }
}

object Runtime {
    @Volatile var focusActive = false
    @Volatile var focusText = ""
    @Volatile var lastSyncElapsed = 0L
    @Volatile var currentPackage = ""
    @Volatile var screenOn = true
    @Volatile var gymStartElapsed = 0L
    @Volatile var gymAwarded = false
    @Volatile var lastGymSeconds = 0
    @Volatile var lastGymAwarded = false

    fun resetGym() {
        gymStartElapsed = 0L
        gymAwarded = false
    }

    fun finishGym() {
        if (gymStartElapsed > 0L) {
            lastGymSeconds = ((SystemClock.elapsedRealtime() - gymStartElapsed) / 1000).toInt()
            lastGymAwarded = gymAwarded
        }
        resetGym()
    }
}
