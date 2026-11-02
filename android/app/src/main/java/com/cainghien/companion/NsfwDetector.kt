package com.cainghien.companion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Mô hình MobileNetV2 224×224 của Déchaîner (Apache-2.0), chạy hoàn toàn trong bộ nhớ máy.
 * Ảnh màn hình chỉ được vẽ vào buffer tạm, không ghi ra tệp và không gửi đi đâu.
 */
class NsfwDetector(context: Context) {

    companion object {
        private const val MODEL_PATH = "models/nsfw_model.tflite"
        const val INPUT_SIZE = 224
        const val THRESHOLD = 0.60f
        val LABELS = listOf("drawings", "hentai", "neutral", "porn", "sexy")
        private const val HENTAI = 1
        private const val PORN = 3

        /** Tâm 70% cộng bốn góc phần tư: ảnh nằm giữa hay lệch góc đều có một ô phủ. */
        val TILE_FRACTIONS: List<FloatArray> = listOf(
            floatArrayOf(0.15f, 0.15f, 0.85f, 0.85f),
            floatArrayOf(0.0f, 0.0f, 0.5f, 0.5f),
            floatArrayOf(0.5f, 0.0f, 1.0f, 0.5f),
            floatArrayOf(0.0f, 0.5f, 0.5f, 1.0f),
            floatArrayOf(0.5f, 0.5f, 1.0f, 1.0f),
        )

        fun unsafeScore(scores: FloatArray): Float =
            scores.getOrElse(HENTAI) { 0f } + scores.getOrElse(PORN) { 0f }
    }

    private val interpreter = Interpreter(loadModel(context), Interpreter.Options().setNumThreads(2))
    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4).order(ByteOrder.nativeOrder())
    private val output = Array(1) { FloatArray(LABELS.size) }
    private val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
    private val scaled = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(scaled)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private fun loadModel(context: Context): MappedByteBuffer =
        context.assets.openFd(MODEL_PATH).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { stream ->
                stream.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    descriptor.startOffset,
                    descriptor.declaredLength,
                )
            }
        }

    /** Trả về điểm nguy hiểm cao nhất trong các vùng; gọi trên luồng nền, không gọi đồng thời. */
    @Synchronized
    fun scan(source: Bitmap): Float {
        if (source.config == Bitmap.Config.HARDWARE) return 0f
        var worst = 0f
        for (fraction in TILE_FRACTIONS) {
            val src = Rect(
                (source.width * fraction[0]).toInt(),
                (source.height * fraction[1]).toInt(),
                (source.width * fraction[2]).toInt(),
                (source.height * fraction[3]).toInt(),
            )
            if (src.width() < 2 || src.height() < 2) continue
            canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(source, src, Rect(0, 0, INPUT_SIZE, INPUT_SIZE), paint)
            scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
            inputBuffer.rewind()
            for (pixel in pixels) {
                inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
                inputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
                inputBuffer.putFloat((pixel and 0xFF) / 255f)
            }
            interpreter.run(inputBuffer, output)
            worst = maxOf(worst, unsafeScore(output[0]))
        }
        return worst
    }

    @Synchronized
    fun close() {
        try {
            interpreter.close()
        } catch (error: Exception) {
        }
        scaled.recycle()
    }
}
