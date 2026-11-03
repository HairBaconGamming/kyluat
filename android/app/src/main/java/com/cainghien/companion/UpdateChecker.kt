package com.cainghien.companion

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Đọc bản phát hành mới nhất từ GitHub. Không gửi gì ngoài một yêu cầu HTTP đọc công khai. */
object UpdateChecker {
    private const val API = "https://api.github.com/repos/HairBaconGamming/kyluat/releases/latest"
    private const val DOWNLOAD_PREFIX = "https://github.com/HairBaconGamming/kyluat/releases/download/"
    private const val MAX_BYTES = 200L * 1024 * 1024
    private val APK_NAME = Regex("^kyluat-android-v(\\d+(?:\\.\\d+)*)\\.apk$")

    data class Release(val version: String, val url: String)

    fun latest(): Release? {
        val body = request(API) ?: return null
        val assets = try {
            JSONObject(body).optJSONArray("assets")
        } catch (error: Exception) {
            null
        } ?: return null
        val candidates = mutableListOf<Pair<String, String>>()
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            candidates.add(asset.optString("name") to asset.optString("browser_download_url"))
        }
        return pick(candidates)
    }

    /**
     * Một release có thể mang theo nhiều file APK (các bản cũ không xoá đi). Chọn bản có số lớn
     * nhất thay vì file đầu tiên, nếu không máy luôn báo bản cũ nhất trong danh sách.
     */
    fun pick(candidates: List<Pair<String, String>>): Release? {
        var best: Release? = null
        for ((name, url) in candidates) {
            val version = APK_NAME.find(name)?.groupValues?.get(1) ?: continue
            if (!url.startsWith(DOWNLOAD_PREFIX)) continue
            val current = best
            if (current == null || isNewer(version, current.version)) best = Release(version, url)
        }
        return best
    }

    fun isNewer(remote: String, current: String): Boolean {
        val left = remote.split('.').map { it.toIntOrNull() ?: 0 }
        val right = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    fun download(url: String, target: File): Boolean {
        if (!url.startsWith(DOWNLOAD_PREFIX)) return false
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "Kyluat")
            if (connection.responseCode !in 200..299) return false
            if (connection.contentLengthLong > MAX_BYTES) return false
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 16)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) return false
                        output.write(buffer, 0, read)
                    }
                }
            }
            true
        } catch (error: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    /** Chỉ cài khi file đúng là Kỷ luật và mang số bản mới hơn bản đang chạy. */
    fun verified(context: Context, file: File, currentVersion: String): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        val info = try {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        } catch (error: Exception) {
            null
        } ?: return false
        if (info.packageName != context.packageName) return false
        @Suppress("DEPRECATION")
        val version = info.versionName ?: return false
        return isNewer(version, currentVersion)
    }

    private fun request(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Kyluat")
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (error: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
