package com.cainghien.companion

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.Telephony

object Apps {
    private val known = mapOf(
        "com.google.android.apps.fitness" to "Google Fit",
        "com.hevyapp.hevy" to "Hevy",
        "io.strongapp.strong" to "Strong",
        "com.sec.android.app.shealth" to "Samsung Health",
        "com.strava" to "Strava",
        "com.jefit.jefit" to "JEFIT",
        "com.myfitnesspal.android" to "MyFitnessPal",
        "com.nike.ntc" to "Nike Training Club",
    )

    private val categories = mapOf(
        "com.zhiliaoapp.musically" to "Video ngắn",
        "com.google.android.youtube" to "Video",
        "com.facebook.katana" to "Mạng xã hội",
        "com.instagram.android" to "Mạng xã hội",
        "com.reddit.frontpage" to "Diễn đàn",
        "com.twitter.android" to "Mạng xã hội",
    )

    fun label(context: Context, packageName: String): String {
        return try {
            val manager = context.packageManager
            manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
        } catch (error: PackageManager.NameNotFoundException) {
            known[packageName] ?: "Ứng dụng đã chọn"
        }
    }

    fun category(packageName: String): String = categories[packageName] ?: "Mạng xã hội"

    fun icon(context: Context, packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (error: Exception) {
            null
        }
    }

    fun isInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (error: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun neverBlocked(context: Context): Set<String> {
        val set = SYSTEM_PACKAGES.toMutableSet()
        set.add(context.packageName)
        try {
            Telephony.Sms.getDefaultSmsPackage(context)?.let { set.add(it) }
        } catch (error: Exception) {
        }
        resolve(context, Intent(Intent.ACTION_DIAL))?.let { set.add(it) }
        resolve(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))?.let { set.add(it) }
        return set
    }

    private fun resolve(context: Context, intent: Intent): String? = try {
        context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
    } catch (error: Exception) {
        null
    }

    private val SYSTEM_PACKAGES = setOf(
        "com.android.systemui",
        "com.android.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.phone",
        "com.android.incallui",
        "com.android.dialer",
    )
}
