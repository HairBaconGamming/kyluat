package com.cainghien.companion

import android.os.SystemClock
import java.util.Calendar

/**
 * Điện thoại tự vẽ cửa sổ overlay của chính nó cũng phát ra TYPE_WINDOW_STATE_CHANGED.
 * Nếu coi đó là "đang mở app Kỷ luật", màn khoá sẽ tự gỡ sau một nhịp — đúng lỗi khoá rồi biến mất.
 * Activity thật của app luôn báo className bắt đầu bằng tên package, còn cửa sổ overlay thì không.
 */
internal fun isOwnOverlayEvent(pkg: String, className: String?, ownPackage: String): Boolean =
    pkg == ownPackage && !(className ?: "").startsWith("$ownPackage.")

/** Chỉ sự kiện từ cửa sổ overlay của chính app bị bỏ qua; mọi chạm thật ở app khác vẫn tính. */
internal fun isInteractionEvent(pkg: String, className: String?, ownPackage: String): Boolean =
    !isOwnOverlayEvent(pkg, className, ownPackage)

/**
 * Cổng 60 giây mở trong [UNLOCK_MINUTES] phút sau một lần cam kết, đủ để chỉnh một loạt luật
 * thay vì mỗi luật phải cam kết lại. Giữ cả mốc giờ máy và mốc từ lúc mở máy: đổi giờ hệ thống
 * về quá khứ không kéo dài cổng, còn khởi động lại thì cổng đóng sớm (an toàn hơn).
 */
internal fun unlockWindowOpen(unlockWallMs: Long, unlockElapsedMs: Long, nowWallMs: Long, nowElapsedMs: Long): Boolean {
    val wallLeft = unlockWallMs - nowWallMs
    if (wallLeft <= 0L) return false
    if (unlockElapsedMs <= 0L) return false
    val elapsedLeft = unlockElapsedMs - nowElapsedMs
    return elapsedLeft > 0L && elapsedLeft <= UNLOCK_MINUTES * 60_000L
}

internal fun unlockRemainingSeconds(unlockWallMs: Long, unlockElapsedMs: Long, nowWallMs: Long, nowElapsedMs: Long): Int {
    if (!unlockWindowOpen(unlockWallMs, unlockElapsedMs, nowWallMs, nowElapsedMs)) return 0
    val wall = (unlockWallMs - nowWallMs) / 1000
    val elapsed = (unlockElapsedMs - nowElapsedMs) / 1000
    return minOf(wall, elapsed).toInt().coerceAtLeast(0)
}

/** Số giây ban đêm (23:00–06:00) nằm trong khoảng [fromMs, toMs); để ngủ không tự đẻ ra điểm học. */
internal fun nightOverlapMs(fromMs: Long, toMs: Long, nightStartHour: Int = NIGHT_START_HOUR, nightEndHour: Int = NIGHT_END_HOUR): Long {
    if (toMs <= fromMs) return 0L
    var total = 0L
    // Cửa sổ đêm vắt qua nửa đêm: bắt đầu 23:00 ngày d, kết thúc 06:00 ngày d+1.
    for (dayOffset in -1..2) {
        val start = nightBoundary(fromMs, dayOffset, nightStartHour)
        val end = nightBoundary(fromMs, dayOffset + 1, nightEndHour)
        val overlap = minOf(toMs, end) - maxOf(fromMs, start)
        if (overlap > 0) total += overlap
    }
    return total
}

private fun nightBoundary(baseMs: Long, dayOffset: Int, hour: Int): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = baseMs
    calendar.add(Calendar.DAY_OF_YEAR, dayOffset)
    calendar.set(Calendar.HOUR_OF_DAY, hour)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

/**
 * Chốt điểm học cho khoảng [fromMs, toMs].
 * - Không đụng điện thoại: sau [idleThresholdSeconds] giây kể từ lần chạm cuối mới bắt đầu cộng
 *   điểm không đụng (5 điểm/phút). Ngưỡng chỉ trừ một lần, không trừ lại mỗi nhịp.
 * - Đang mở app AI và còn thao tác: cộng điểm AI (2 điểm/phút), trần [AI_MAX_CREDIT_MS] mỗi nhịp
 *   để một lần trễ dài không đẻ ra cả khối điểm.
 * Giờ đêm (23:00–06:00) bị trừ ra; app phát video hay game không gửi sự kiện chạm thì màn hình
 * sáng không được tính là "không đụng điện thoại" (chỉ tính khi máy khoá hoặc đang ở app AI/Kỷ luật).
 */
internal fun studyCreditSeconds(
    fromMs: Long,
    toMs: Long,
    mode: Int,
    lastInteractionMs: Long,
    idleThresholdSeconds: Int,
): Pair<Long, Long> {
    val span = toMs - fromMs
    if (span <= 0L || mode == STUDY_NONE) return 0L to 0L
    val credit = (span - nightOverlapMs(fromMs, toMs)).coerceAtLeast(0L)
    if (credit <= 0L) return 0L to 0L
    if (mode == STUDY_AI) return 0L to minOf(credit, AI_MAX_CREDIT_MS)
    val creditStart = lastInteractionMs + idleThresholdSeconds * 1000L
    val idle = (toMs - maxOf(fromMs, creditStart)).coerceAtLeast(0L)
    return minOf(idle, credit) to 0L
}

/** Điểm học hôm nay: 1 phút không đụng điện thoại = 5 điểm, 1 phút trong app AI = 2 điểm. */
internal fun studyPoints(idleSeconds: Int, aiSeconds: Int, idleRate: Int = IDLE_POINTS_PER_MINUTE, aiRate: Int = AI_POINTS_PER_MINUTE): Int =
    ((idleSeconds.toLong() * idleRate + aiSeconds.toLong() * aiRate) / 60).toInt()

/** Quy đổi điểm ra phút lướt mạng; chặn trần [MAX_BONUS_MINUTES] phút mỗi ngày. */
internal fun bonusSeconds(idleSeconds: Int, aiSeconds: Int, pointsPerMinute: Int): Int {
    if (pointsPerMinute <= 0) return 0
    val points = idleSeconds.toLong() * IDLE_POINTS_PER_MINUTE + aiSeconds.toLong() * AI_POINTS_PER_MINUTE
    val bonus = points / pointsPerMinute
    return minOf(bonus, MAX_BONUS_MINUTES * 60L).toInt().coerceAtLeast(0)
}

internal const val STUDY_NONE = 0
internal const val STUDY_IDLE = 1
internal const val STUDY_AI = 2

internal const val IDLE_POINTS_PER_MINUTE = 5
internal const val AI_POINTS_PER_MINUTE = 2
internal const val DEFAULT_POINTS_PER_MINUTE = 5
internal const val MAX_BONUS_MINUTES = 120
internal const val IDLE_THRESHOLD_SECONDS = 10
internal const val AI_IDLE_THRESHOLD_SECONDS = 60
internal const val NIGHT_START_HOUR = 22
internal const val NIGHT_END_HOUR = 7
internal const val UNLOCK_MINUTES = 10

private const val AI_MAX_CREDIT_MS = 6_000L

/**
 * Trạng thái tính điểm, đọc từ Runtime và prefs. Tách thành hàm thuần để kiểm thử được
 * mà không cần Context.
 */
internal fun studyModeOf(
    protectionEnabled: Boolean,
    screenOn: Boolean,
    locked: Boolean,
    inCall: Boolean,
    foreground: String,
    ownPackage: String,
    socialPackages: Set<String>,
    aiPackages: Set<String>,
    idleSeconds: Int,
    aiIdleSeconds: Int,
    hourOfDay: Int,
): Int {
    if (!protectionEnabled || inCall) return STUDY_NONE
    val night = hourOfDay >= NIGHT_START_HOUR || hourOfDay < NIGHT_END_HOUR
    if (night) return STUDY_NONE
    if (!screenOn || locked) return STUDY_IDLE
    return when {
        foreground in socialPackages -> STUDY_NONE
        // Trong app AI: còn thao tác thì tính điểm AI; để yên quá 1 phút thì tính như không đụng điện thoại.
        foreground in aiPackages -> if (idleSeconds < aiIdleSeconds) STUDY_AI else STUDY_IDLE
        foreground == ownPackage && idleSeconds >= IDLE_THRESHOLD_SECONDS -> STUDY_IDLE
        else -> STUDY_NONE
    }
}

/**
 * Đếm điểm học và quy ra quỹ cộng thêm. Mọi lỗi đều bỏ qua: thà không cộng điểm
 * còn hơn làm treo lớp bảo vệ.
 */
class StudyTracker(
    private val prefs: Prefs,
    private val quota: QuotaManager,
    private val ownPackage: String,
) {
    private var lastSettleElapsed = 0L

    fun settle(nowElapsed: Long = SystemClock.elapsedRealtime(), nowWallMs: Long = System.currentTimeMillis()) {
        val start = if (lastSettleElapsed == 0L) nowElapsed else lastSettleElapsed
        lastSettleElapsed = nowElapsed
        val span = nowElapsed - start
        if (span <= 0L) return
        quota.resetIfNeeded(nowWallMs)
        val idleElapsed = (nowElapsed - Runtime.lastInteractionElapsed).coerceAtLeast(0L)
        val idleSeconds = (idleElapsed / 1000).toInt()
        val lastInteractionWallMs = nowWallMs - idleElapsed
        val hour = Calendar.getInstance().apply { timeInMillis = nowWallMs }.get(Calendar.HOUR_OF_DAY)
        val mode = studyModeOf(
            protectionEnabled = prefs.protectionEnabled,
            screenOn = Runtime.screenOn,
            locked = Runtime.keyguardLocked,
            inCall = Runtime.inCall,
            foreground = Runtime.currentPackage,
            ownPackage = ownPackage,
            socialPackages = prefs.socialPackages,
            aiPackages = prefs.aiPackages,
            idleSeconds = idleSeconds,
            aiIdleSeconds = AI_IDLE_THRESHOLD_SECONDS,
            hourOfDay = hour,
        )
        Runtime.studyMode = mode
        if (mode == STUDY_NONE) return
        // Mốc đêm tính theo giờ máy, còn khoảng chốt tính theo đồng hồ không đổi của hệ thống.
        val fromWall = nowWallMs - span
        val (idleMs, aiMs) = studyCreditSeconds(
            fromMs = fromWall,
            toMs = nowWallMs,
            mode = mode,
            lastInteractionMs = lastInteractionWallMs,
            idleThresholdSeconds = if (mode == STUDY_AI) AI_IDLE_THRESHOLD_SECONDS else IDLE_THRESHOLD_SECONDS,
        )
        if (idleMs > 0L) prefs.studyIdleSeconds = prefs.studyIdleSeconds + (idleMs / 1000).toInt()
        if (aiMs > 0L) prefs.studyAiSeconds = prefs.studyAiSeconds + (aiMs / 1000).toInt()
    }

    fun stop() {
        Runtime.studyMode = STUDY_NONE
        lastSettleElapsed = 0L
    }
}
