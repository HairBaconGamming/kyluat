package com.cainghien.companion

/**
 * Điện thoại tự vẽ cửa sổ overlay của chính nó cũng phát ra TYPE_WINDOW_STATE_CHANGED.
 * Nếu coi đó là "đang mở app Kỷ luật", màn khoá sẽ tự gỡ sau một nhịp — đúng lỗi khoá rồi biến mất.
 * Activity thật của app luôn báo className bắt đầu bằng tên package, còn cửa sổ overlay thì không.
 */
internal fun isOwnOverlayEvent(pkg: String, className: String?, ownPackage: String): Boolean =
    pkg == ownPackage && !(className ?: "").startsWith("$ownPackage.")

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

/**
 * Phiên tập trung trên điện thoại chỉ được điểm khi chạy hết giờ. Xin dừng sớm đặt [stopAfterMs]
 * thành mốc mở khoá, nên mốc đó lớn hơn 0 nghĩa là phiên đã bị bỏ giữa chừng.
 */
internal fun localSessionCompleted(endMs: Long, stopAfterMs: Long, nowMs: Long): Boolean =
    endMs > 0L && stopAfterMs <= 0L && endMs <= nowMs

/**
 * Máy tính chỉ báo "có phiên đang chạy" kèm số phút còn lại, không nói phiên hết giờ hay bị dừng.
 * Chỉ thưởng khi lần cuối còn nhìn thấy phiên đã về 0–1 phút, tức nó chạy tới hết giờ; dừng sớm,
 * mất ghép nối hay tắt bảo vệ đều để lại số phút còn lại lớn hơn 1.
 */
internal fun remoteSessionCompleted(wasActive: Boolean, nowActive: Boolean, lastRemainingMinutes: Int): Boolean =
    wasActive && !nowActive && lastRemainingMinutes in 0..1

/** Quy đổi điểm học ra giây quỹ thưởng; chặn trần [MAX_BONUS_MINUTES] phút mỗi ngày. */
internal fun bonusSeconds(points: Int, pointsPerMinute: Int): Int {
    if (points <= 0 || pointsPerMinute <= 0) return 0
    val seconds = points.toLong() * 60L / pointsPerMinute
    return minOf(seconds, MAX_BONUS_MINUTES * 60L).toInt()
}

internal const val POINTS_PER_SESSION = 25
internal const val DEFAULT_POINTS_PER_MINUTE = 5
internal const val MAX_BONUS_MINUTES = 120
internal const val UNLOCK_MINUTES = 10

private val focusSettleLock = Any()

/**
 * Dọn phiên tập trung trên điện thoại đã kết thúc: phiên chạy hết giờ được [POINTS_PER_SESSION]
 * điểm học rồi mới xoá dấu vết, phiên bị dừng sớm thì chỉ xoá. Khoá chung để activity và service
 * không cộng hai lần cho cùng một phiên, và trả về đúng một lần `true`.
 */
internal fun settleLocalFocus(prefs: Prefs, quota: QuotaManager, nowMs: Long): Boolean =
    synchronized(focusSettleLock) {
        if (!localFocusFinished(prefs.localFocusEndMs, prefs.localFocusStopAfterMs, nowMs)) {
            return@synchronized false
        }
        val completed = prefs.protectionEnabled &&
            localSessionCompleted(prefs.localFocusEndMs, prefs.localFocusStopAfterMs, nowMs)
        if (completed) quota.awardSession(POINTS_PER_SESSION, nowMs)
        prefs.clearLocalFocus()
        completed
    }
