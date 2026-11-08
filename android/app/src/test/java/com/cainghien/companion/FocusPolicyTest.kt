package com.cainghien.companion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lịch trên máy tính chạy suốt ngày nên không được coi là phiên focus: nếu không thì điện thoại
 * khoá app và cấm dùng quỹ gần 24/24. Chỉ phiên thủ công mới khoá app và ngừng trừ quỹ.
 */
class FocusPolicyTest {
    @Test
    fun `scheduled window alone never enforces`() {
        assertFalse(manualFocusActive(protection = true, remoteManual = false, localActive = false))
    }

    @Test
    fun `manual session from the computer enforces`() {
        assertTrue(manualFocusActive(protection = true, remoteManual = true, localActive = false))
    }

    @Test
    fun `local session enforces without a computer`() {
        assertTrue(manualFocusActive(protection = true, remoteManual = false, localActive = true))
    }

    @Test
    fun `protection switch beats every session`() {
        assertFalse(manualFocusActive(protection = false, remoteManual = true, localActive = true))
    }
}
