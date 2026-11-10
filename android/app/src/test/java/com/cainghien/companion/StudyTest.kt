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
    fun `focus points follow the length of the session`() {
        // Không hẹn giờ (phiên cũ trước khi nâng cấp) → không có điểm.
        assertEquals(0, focusPointsFor(0))
        assertEquals(0, focusPointsFor(20 * 60))
        // Đúng ba bậc người dùng chọn trên điện thoại.
        assertEquals(25, focusPointsFor(25 * 60))
        assertEquals(50, focusPointsFor(45 * 60))
        assertEquals(70, focusPointsFor(60 * 60))
        // Phiên máy tính: tính ở bậc cao nhất nó chạm tới, dài hơn 60 phút vẫn là 70.
        assertEquals(25, focusPointsFor(30 * 60))
        assertEquals(70, focusPointsFor(90 * 60))
    }

    @Test
    fun `gym points follow the continuous minutes`() {
        assertEquals(0, gymPointsFor(9 * 60 + 59))
        assertEquals(20, gymPointsFor(10 * 60))
        assertEquals(40, gymPointsFor(20 * 60))
        assertEquals(50, gymPointsFor(30 * 60))
        assertEquals(70, gymPointsFor(45 * 60))
        assertEquals(100, gymPointsFor(60 * 60))
        assertEquals(100, gymPointsFor(75 * 60))
    }

    @Test
    fun `gym pays only the difference between tiers`() {
        // Buổi 60 phút đi qua cả năm bậc nhưng tổng điểm phải đúng 100, không phải cộng dồn 280.
        var paid = 0
        for (minute in listOf(10, 20, 30, 45, 60)) {
            paid += gymTopUp(minute * 60, paid)
        }
        assertEquals(100, paid)
        // Cùng bậc thì không trả thêm, kể cả khi đồng hồ vẫn chạy.
        assertEquals(0, gymTopUp(15 * 60, 20))
        assertEquals(0, gymTopUp(10 * 60, 20))
        assertEquals(0, gymTopUp(60 * 60, 100))
        // Rơi từ bậc cao xuống (buổi mới đếm lại từ đầu) không được trừ điểm đã cộng.
        assertEquals(0, gymTopUp(10 * 60, 40))
    }

    @Test
    fun `gym pill counts down to the next tier`() {
        assertEquals(600, gymSecondsToNextTier(0))
        assertEquals(60, gymSecondsToNextTier(9 * 60))
        assertEquals(600, gymSecondsToNextTier(10 * 60))
        assertEquals(900, gymSecondsToNextTier(30 * 60))
        assertEquals(0, gymSecondsToNextTier(60 * 60))
    }

    @Test
    fun `points convert to bonus minutes, not seconds`() {
        // Mặc định 5 điểm = 1 phút → phiên 25 phút (25 điểm) thưởng 5 phút = 300 giây.
        assertEquals(300, bonusSeconds(focusPointsFor(25 * 60), DEFAULT_POINTS_PER_MINUTE))
        // Buổi tập 60 phút (100 điểm) thưởng 20 phút.
        assertEquals(1200, bonusSeconds(gymPointsFor(60 * 60), DEFAULT_POINTS_PER_MINUTE))
        assertEquals(60, bonusSeconds(5, DEFAULT_POINTS_PER_MINUTE))
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
