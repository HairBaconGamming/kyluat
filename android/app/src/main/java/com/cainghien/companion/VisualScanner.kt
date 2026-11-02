package com.cainghien.companion

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import androidx.annotation.RequiresApi
import java.util.concurrent.Executors

/**
 * Toàn bộ mã API 30 nằm riêng ở đây; máy Android cũ không bao giờ nạp lớp này.
 * Lỗi ở đây chỉ bỏ qua lần quét, không đụng tới lớp chặn app hay DNS.
 */
@RequiresApi(30)
class VisualScanner(
    private val service: AccessibilityService,
    private val onBlocked: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var detector: NsfwDetector? = null
    private var busy = false
    private var lastScan = 0L
    private var cooldownUntil = 0L
    private var pausedUntil = 0L
    private var failures = 0

    @Volatile
    private var closed = false

    fun maybeScan(stillNeeded: () -> Boolean) {
        if (closed || busy) return
        val now = SystemClock.elapsedRealtime()
        if (now < cooldownUntil || now < pausedUntil) return
        if (now - lastScan < SCAN_INTERVAL_MS) return
        busy = true
        lastScan = now
        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val hardware = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        result.hardwareBuffer.close()
                        if (hardware == null) {
                            busy = false
                            return
                        }
                        if (closed) {
                            hardware.recycle()
                            busy = false
                            return
                        }
                        try {
                            worker.execute { scan(hardware, stillNeeded) }
                        } catch (error: Exception) {
                            hardware.recycle()
                            busy = false
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        busy = false
                        failures += 1
                        if (failures >= 4) {
                            pausedUntil = SystemClock.elapsedRealtime() + PAUSE_MS
                            failures = 0
                        }
                    }
                },
            )
        } catch (error: Exception) {
            busy = false
            pausedUntil = now + PAUSE_MS
        }
    }

    private fun scan(hardware: Bitmap, stillNeeded: () -> Boolean) {
        var software: Bitmap? = null
        try {
            if (closed) return
            software = hardware.copy(Bitmap.Config.ARGB_8888, false)
            if (software == null) {
                handler.post { busy = false }
                return
            }
            val model = detector ?: NsfwDetector(service.applicationContext).also { detector = it }
            val hit = model.scan(software) >= NsfwDetector.THRESHOLD && stillNeeded()
            handler.post {
                busy = false
                failures = 0
                if (!closed && hit && stillNeeded()) {
                    cooldownUntil = SystemClock.elapsedRealtime() + COOLDOWN_MS
                    onBlocked()
                }
            }
        } catch (error: Exception) {
            handler.post { failed() }
        } finally {
            hardware.recycle()
            software?.recycle()
        }
    }

    private fun failed() {
        busy = false
        failures += 1
        if (failures >= 3) {
            pausedUntil = SystemClock.elapsedRealtime() + PAUSE_MS
            failures = 0
        }
    }

    fun close() {
        closed = true
        busy = false
        try {
            worker.execute {
                detector?.close()
                detector = null
            }
        } catch (error: Exception) {
        }
        worker.shutdown()
    }

    companion object {
        private const val SCAN_INTERVAL_MS = 4500L
        private const val COOLDOWN_MS = 10000L
        private const val PAUSE_MS = 5 * 60 * 1000L
    }
}
