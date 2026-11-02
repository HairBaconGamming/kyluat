package com.cainghien.companion

import android.content.Context
import android.os.Build

class Prefs(context: Context) {
    private val store = context.getSharedPreferences("cainghien", Context.MODE_PRIVATE)

    var protectionEnabled: Boolean
        get() = store.getBoolean("protection_enabled", true)
        set(value) = store.edit().putBoolean("protection_enabled", value).apply()

    var adultFilterEnabled: Boolean
        get() = store.getBoolean("adult_filter", false)
        set(value) = store.edit().putBoolean("adult_filter", value).apply()

    var gymPackage: String
        get() = store.getString("gym_package", DEFAULT_GYM) ?: DEFAULT_GYM
        set(value) = store.edit().putString("gym_package", value).apply()

    var socialPackages: Set<String>
        get() = store.getStringSet("social_packages", DEFAULT_SOCIAL)?.toSet() ?: DEFAULT_SOCIAL
        set(value) = store.edit().putStringSet("social_packages", value).apply()

    var extraPackages: Set<String>
        get() = store.getStringSet("extra_packages", emptySet())?.toSet() ?: emptySet()
        set(value) = store.edit().putStringSet("extra_packages", value).apply()

    var quotaDate: String
        get() = store.getString("quota_date", "") ?: ""
        set(value) = store.edit().putString("quota_date", value).apply()

    var quotaUsedSeconds: Int
        get() = store.getInt("quota_used_seconds", 0)
        set(value) = store.edit().putInt("quota_used_seconds", value).apply()

    var quotaMinutes: Int
        get() = store.getInt("quota_minutes", DEFAULT_QUOTA_MINUTES)
        set(value) = store.edit().putInt("quota_minutes", value).apply()

    var quotaPendingMinutes: Int
        get() = store.getInt("quota_pending_minutes", 0)
        set(value) = store.edit().putInt("quota_pending_minutes", value).apply()

    var breakUntilMs: Long
        get() = store.getLong("break_until_ms", 0L)
        set(value) = store.edit().putLong("break_until_ms", value).apply()

    var pendingGymCheckin: Boolean
        get() = store.getBoolean("pending_gym_checkin", false)
        set(value) = store.edit().putBoolean("pending_gym_checkin", value).apply()

    var autoUpdate: Boolean
        get() = store.getBoolean("auto_update", true)
        set(value) = store.edit().putBoolean("auto_update", value).apply()

    var updateLastCheckMs: Long
        get() = store.getLong("update_last_check", 0L)
        set(value) = store.edit().putLong("update_last_check", value).apply()

    var updatePromptedVersion: String
        get() = store.getString("update_prompted", "") ?: ""
        set(value) = store.edit().putString("update_prompted", value).apply()

    var uninstallGuard: Boolean
        get() = store.getBoolean("uninstall_guard", false)
        set(value) = store.edit().putBoolean("uninstall_guard", value).apply()

    var onboardingDone: Boolean
        get() = store.getBoolean("onboarding_done", false)
        set(value) = store.edit().putBoolean("onboarding_done", value).apply()

    var onboardingMigrated: Boolean
        get() = store.getBoolean("onboarding_migrated", false)
        set(value) = store.edit().putBoolean("onboarding_migrated", value).apply()

    /** Chưa ai chạm vào danh sách chặn, quỹ hay ghép nối: coi như bản cài mới. */
    fun looksUntouched(): Boolean =
        quotaDate.isEmpty() && extraPackages.isEmpty() && socialPackages == DEFAULT_SOCIAL

    var deviceName: String
        get() = store.getString("device_name", Build.MODEL) ?: Build.MODEL
        set(value) = store.edit().putString("device_name", value).apply()

    fun setSocialPackage(packageName: String, enabled: Boolean) {
        val current = socialPackages.toMutableSet()
        if (enabled) current.add(packageName) else current.remove(packageName)
        socialPackages = current
    }

    fun setExtraPackage(packageName: String, added: Boolean) {
        val extras = extraPackages.toMutableSet()
        val blocked = socialPackages.toMutableSet()
        if (added) {
            extras.add(packageName)
            blocked.add(packageName)
        } else {
            extras.remove(packageName)
            blocked.remove(packageName)
        }
        extraPackages = extras
        socialPackages = blocked
    }

    companion object {
        const val DEFAULT_GYM = "com.google.android.apps.fitness"
        const val DEFAULT_QUOTA_MINUTES = 60
        val DEFAULT_SOCIAL = setOf(
            "com.zhiliaoapp.musically",
            "com.google.android.youtube",
            "com.facebook.katana",
            "com.instagram.android",
            "com.reddit.frontpage",
            "com.twitter.android",
        )
        val SOCIAL_LABELS = mapOf(
            "com.zhiliaoapp.musically" to "TikTok",
            "com.google.android.youtube" to "YouTube",
            "com.facebook.katana" to "Facebook",
            "com.instagram.android" to "Instagram",
            "com.reddit.frontpage" to "Reddit",
            "com.twitter.android" to "X (Twitter)",
        )
    }
}
