package com.cainghien.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Các hàm thuần của lớp điểm học và cổng 60 giây. Tách khỏi Context để kiểm thử trên JVM.
 */
class StudyTest {
    @Test
    fun `own overlay window is filtered but own activity is not`() {
        val own = "com.cainghien.companion"
        assertTrue(isOwnOverlayEvent(own, "android.widget.FrameLayout", own))
        assertTrue(isOwnOverlayEvent(own, "android.widget.LinearLayout", own))
        assertTrue(isOwnOverlayEvent(own, null, own))
        assertFalse(isOwnOverlayEvent(own, "$own.MainActivity", own))
        assertFalse(isOwnOverlayEvent("com.google.android.youtube", "android.widget.FrameLayout", own))
    }

    @Test
    fun `local session scores only when it runs to the end`() {
        val end = 1_000_000L
        // Chạy hết giờ, không xin dừng → có điểm.
        assertTrue(localSessionCompleted(end, 0L, end))
        assertTrue(localSessionCompleted(end, 0L, end + 60_000L))
        // Chưa tới giờ kết thúc → chưa có điểm.
        assertFalse(localSessionCompleted(end, 0L, end - 1))
        // Xin dừng sớm (có mốc mở khoá) → bỏ giữa chừng, không có điểm.
        assertFalse(localSessionCompleted(end, end - 60_000L, end))
        // Không có phiên nào.
        assertFalse(localSessionCompleted(0L, 0L, end))
    }

    @Test
    fun `remote session scores only when it ran down to the last minute`() {
        // Máy tính báo phiên biến mất khi còn 0–1 phút → coi như chạy hết giờ.
        assertTrue(remoteSessionCompleted(wasActive = true, nowActive = false, lastRemainingMinutes = 0))
        assertTrue(remoteSessionCompleted(wasActive = true, nowActive = false, lastRemainingMinutes = 1))
        // Dừng sớm: lần cuối còn thấy là 12 phút.
        assertFalse(remoteSessionCompleted(true, false, 12))
        // Chưa từng thấy phiên, hoặc phiên vẫn đang chạy.
        assertFalse(remoteSessionCompleted(false, false, 0))
        assertFalse(remoteSessionCompleted(true, true, 0))
    }

    @Test
    fun `points convert to bonus minutes, not seconds`() {
        // 1 phiên = 25 điểm; mặc định 5 điểm = 1 phút → 5 phút = 300 giây.
        assertEquals(25, POINTS_PER_SESSION)
        assertEquals(300, bonusSeconds(POINTS_PER_SESSION, DEFAULT_POINTS_PER_MINUTE))
        assertEquals(60, bonusSeconds(5, DEFAULT_POINTS_PER_MINUTE))
        // Đổi tỷ lệ: 25 điểm ở mức 25 điểm/phút = 1 phút.
        assertEquals(60, bonusSeconds(POINTS_PER_SESSION, 25))
        // Chưa đủ một phút thưởng vẫn ra giây lẻ (4 điểm = 48 giây), tỷ lệ hỏng hoặc điểm âm thì bằng 0.
        assertEquals(48, bonusSeconds(4, DEFAULT_POINTS_PER_MINUTE))
        assertEquals(0, bonusSeconds(25, 0))
        assertEquals(0, bonusSeconds(-25, DEFAULT_POINTS_PER_MINUTE))
        // Trần 120 phút thưởng mỗi ngày.
        assertEquals(MAX_BONUS_MINUTES * 60, bonusSeconds(100_000, DEFAULT_POINTS_PER_MINUTE))
    }

    @Test
    fun `unlock window opens once and closes on either clock`() {
        val wall = 1_000_000L
        val elapsed = 500_000L
        val openWall = wall + UNLOCK_MINUTES * 60_000L
        val openElapsed = elapsed + UNLOCK_MINUTES * 60_000L
        assertTrue(unlockWindowOpen(openWall, openElapsed, wall, elapsed))
        assertEquals(UNLOCK_MINUTES * 60, unlockRemainingSeconds(openWall, openElapsed, wall, elapsed))
        // Hết giờ máy → đóng.
        assertFalse(unlockWindowOpen(openWall, openElapsed, openWall + 1, elapsed))
        // Khởi động lại (elapsed vượt mốc) → đóng.
        assertFalse(unlockWindowOpen(openWall, openElapsed, wall, openElapsed + 1))
        // Đúng 10 phút sau, cả hai đồng hồ cùng chạy tới mốc → đóng.
        assertFalse(unlockWindowOpen(openWall, openElapsed, openWall, openElapsed))
    }
}
