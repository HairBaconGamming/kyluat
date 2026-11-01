package com.cainghien.companion

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Summary(
    val focusActive: Boolean,
    val focusText: String,
    val focusLabel: String,
    val focusDetail: String,
    val date: String,
    val sessionLabel: String?,
    val sessionRemaining: Int,
    val studyToday: Int,
    val studyGoal: Int,
    val studyStreak: Int,
    val gymWeek: Int,
    val gymGoal: Int,
    val gymStreakWeeks: Int,
    val blocksToday: Int,
)

object Api {
    fun summary(base: String, token: String): Summary {
        val root = request(base, token, "/api/phone/summary", "GET", null)
        val focus = root.getJSONObject("focus")
        val habits = root.getJSONObject("habits")
        val study = habits.getJSONObject("study")
        val gym = habits.getJSONObject("gym")
        val blocks = root.getJSONObject("blocks")
        val session = root.optJSONObject("session")
        val label = focus.optString("text", "")
        val detail = focus.optString("detail", "")
        return Summary(
            focusActive = focus.getBoolean("active"),
            focusText = (label + " " + detail).trim(),
            focusLabel = label,
            focusDetail = detail,
            date = root.optString("date", ""),
            sessionLabel = session?.optString("label"),
            sessionRemaining = session?.optInt("remaining_minutes", 0) ?: 0,
            studyToday = study.getInt("today"),
            studyGoal = study.getInt("goal"),
            studyStreak = study.optInt("streak", 0),
            gymWeek = gym.getInt("week"),
            gymGoal = gym.getInt("goal"),
            gymStreakWeeks = gym.optInt("streak_weeks", 0),
            blocksToday = blocks.getInt("today"),
        )
    }

    fun session(base: String, token: String, action: String, minutes: Int, label: String): JSONObject {
        val body = JSONObject()
            .put("action", action)
            .put("minutes", minutes)
            .put("label", label)
        return request(base, token, "/api/phone/session", "POST", body.toString())
    }

    fun checkin(base: String, token: String, habit: String, minutes: Int, note: String) {
        val body = JSONObject()
            .put("habit", habit)
            .put("minutes", minutes)
            .put("note", note)
        request(base, token, "/api/phone/checkin", "POST", body.toString())
    }

    fun pair(base: String, code: String, device: String): String {
        val body = JSONObject().put("code", code).put("device", device)
        val root = request(base, null, "/api/phone/pair", "POST", body.toString())
        return root.getString("token")
    }

    private fun request(
        base: String,
        token: String?,
        path: String,
        method: String,
        body: String?,
    ): JSONObject {
        val connection = URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 6000
            connection.readTimeout = 8000
            connection.setRequestProperty("Content-Type", "application/json")
            if (!token.isNullOrEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code: $text")
            }
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }
}
