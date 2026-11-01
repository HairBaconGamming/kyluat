package com.cainghien.companion

import android.app.Application
import android.os.Build
import android.util.Log
import java.io.File

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val version = try {
                    val info = packageManager.getPackageInfo(packageName, 0)
                    info.versionName ?: "?"
                } catch (error: Exception) {
                    "?"
                }
                val header = "Kỷ luật $version · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}\n\n"
                val file = File(getExternalFilesDir(null) ?: filesDir, "crash.txt")
                file.writeText(header + Log.getStackTraceString(throwable))
            } catch (error: Exception) {
                // không để bộ ghi log làm crash thêm
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
