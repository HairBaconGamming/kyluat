package com.cainghien.companion

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceCoach(context: Context, private val onReady: (Boolean) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var lastText: String? = null
    private var lastAt = 0L
    var available: Boolean = false
        private set
    var enabled: Boolean = true

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val language = if (status == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale("vi", "VN")) ?: TextToSpeech.LANG_NOT_SUPPORTED
            } else TextToSpeech.LANG_NOT_SUPPORTED
            available = status == TextToSpeech.SUCCESS && language >= TextToSpeech.LANG_AVAILABLE
            main.post { onReady(available) }
        }
    }

    fun speakCorrection(text: String) {
        if (!enabled || !available) return
        val now = SystemClock.elapsedRealtime()
        if (text == lastText && now - lastAt < REPEAT_MS) return
        lastText = text
        lastAt = now
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "posture")
    }

    fun reset() {
        lastText = null
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        available = false
    }

    companion object {
        private const val REPEAT_MS = 6_000L

        fun installVoiceIntent(): Intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
    }
}