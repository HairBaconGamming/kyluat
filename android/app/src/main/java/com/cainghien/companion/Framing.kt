package com.cainghien.companion

object Framing {
    const val FEET_HIDDEN_CUE =
        "Bàn chân khuất sau bảng đếm. Đặt máy xa hơn hoặc nâng camera để thấy cả người."

    fun feetCue(ankleViewY: Float?, visibleBottom: Float): String? =
        if (ankleViewY != null && ankleViewY.isFinite() && ankleViewY > visibleBottom) FEET_HIDDEN_CUE else null
}