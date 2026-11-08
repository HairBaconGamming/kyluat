package com.cainghien.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Các hàm thuần của lớp điểm học và cổng 60 giây. Tách khỏi Context để kiểm thử trên JVM.
 */
class StudyTest {
    private fun at(hour: Int, minute: Int, second: Int = 0, dayOffset: Int = 0): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, dayOffset)
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, second)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

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
    fun `interaction from other apps counts, own overlay does not`() {
        val own = "com.cainghien.companion"
        assertTrue(isInteractionEvent("com.google.android.youtube", "com.google.SomeView", own))
        assertFalse(isInteractionEvent(own, "android.widget.FrameLayout", own))
        assertTrue(isInteractionEvent(own, "$own.MainActivity", own))
    }

    @Test
    fun `idle credit starts only after the threshold, counted once`() {
        val from = at(14, 0, 0)
        val to = at(14, 1, 0) // 60 s ban ngày
        // Vừa chạm ngay đầu khoảng, ngưỡng 10 s → chỉ được 50 s.
        val (idleA, aiA) = studyCreditSeconds(from, to, STUDY_IDLE, from, IDLE_THRESHOLD_SECONDS)
        assertEquals(50_000L, idleA)
        assertEquals(0L, aiA)
        // Đã không đụng từ lâu (trước cả khoảng) → trọn 60 s, không trừ lại ngưỡng.
        val (idleB, _) = studyCreditSeconds(from, to, STUDY_IDLE, from - 120_000, IDLE_THRESHOLD_SECONDS)
        assertEquals(60_000L, idleB)
    }

    @Test
    fun `ai credit is capped per settle`() {
        val from = at(14, 0, 0)
        val (_, aiShort) = studyCreditSeconds(from, from + 3_000, STUDY_AI, from, AI_IDLE_THRESHOLD_SECONDS)
        assertEquals(3_000L, aiShort)
        val (_, aiLong) = studyCreditSeconds(from, from + 60_000, STUDY_AI, from, AI_IDLE_THRESHOLD_SECONDS)
        assertEquals(6_000L, aiLong)
    }

    @Test
    fun `night window yields no credit`() {
        val from = at(23, 30, 0)
        val to = at(23, 31, 0)
        val (idle, ai) = studyCreditSeconds(from, to, STUDY_IDLE, from - 60_000, IDLE_THRESHOLD_SECONDS)
        assertEquals(0L, idle)
        assertEquals(0L, ai)
        assertEquals(60_000L, nightOverlapMs(from, to))
        // Ban ngày không có giờ đêm.
        assertEquals(0L, nightOverlapMs(at(14, 0), at(14, 30)))
    }

    @Test
    fun `night overlap spans midnight`() {
        val from = at(23, 0, 0)
        val to = at(1, 0, 0, dayOffset = 1) // 23:00 → 01:00 hôm sau = 2 giờ đêm
        assertEquals(2 * 3_600_000L, nightOverlapMs(from, to))
    }

    @Test
    fun `study mode rules`() {
        val social = setOf("com.google.android.youtube")
        val ai = setOf("com.openai.chatgpt")
        val own = "com.cainghien.companion"
        // Đang lướt mạng xã hội, màn hình sáng → không cộng điểm.
        assertEquals(
            STUDY_NONE,
            studyModeOf(true, true, false, false, "com.google.android.youtube", own, social, ai, 30, 60, 14),
        )
        // Màn hình tắt → tính không đụng điện thoại.
        assertEquals(
            STUDY_IDLE,
            studyModeOf(true, false, false, false, "com.google.android.youtube", own, social, ai, 30, 60, 14),
        )
        // Trong app AI còn thao tác (idle < 60) → điểm AI.
        assertEquals(
            STUDY_AI,
            studyModeOf(true, true, false, false, "com.openai.chatgpt", own, social, ai, 5, 60, 14),
        )
        // Trong app AI nhưng để yên quá 60 s → chuyển sang điểm không đụng.
        assertEquals(
            STUDY_IDLE,
            studyModeOf(true, true, false, false, "com.openai.chatgpt", own, social, ai, 90, 60, 14),
        )
        // Đang gọi → không tính.
        assertEquals(
            STUDY_NONE,
            studyModeOf(true, false, false, true, own, own, social, ai, 300, 60, 14),
        )
        // Giờ đêm → không tính.
        assertEquals(
            STUDY_NONE,
            studyModeOf(true, false, false, false, own, own, social, ai, 300, 60, 23),
        )
        // Bảo vệ tắt → không tính.
        assertEquals(
            STUDY_NONE,
            studyModeOf(false, false, false, false, own, own, social, ai, 300, 60, 14),
        )
        // App thường, màn hình sáng, đang mở → không tính (chống để video/game chạy nền).
        assertEquals(
            STUDY_NONE,
            studyModeOf(true, true, false, false, "com.some.game", own, social, ai, 300, 60, 14),
        )
    }

    @Test
    fun `points and bonus minutes`() {
        // 1 phút không đụng = 5 điểm; 5 điểm = 1 phút thưởng (mặc định).
        assertEquals(5, studyPoints(60, 0))
        assertEquals(60, bonusSeconds(60, 0, DEFAULT_POINTS_PER_MINUTE))
        // 1 phút trong app AI = 2 điểm.
        assertEquals(2, studyPoints(0, 60))
        // Trần 120 phút thưởng mỗi ngày.
        assertEquals(MAX_BONUS_MINUTES * 60, bonusSeconds(1_000_000, 0, DEFAULT_POINTS_PER_MINUTE))
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
