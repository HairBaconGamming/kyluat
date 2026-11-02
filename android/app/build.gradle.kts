import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Key ký bản phát hành nằm ngoài repo, đường dẫn và mật khẩu đọc từ android/local.properties
// (file đã gitignore) hoặc biến môi trường KYLUAT_* khi build trên máy khác.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun secret(name: String): String? =
    System.getenv("KYLUAT_${name.uppercase()}") ?: localProps.getProperty("kyluat.$name")

val storePath = secret("storeFile")

android {
    namespace = "com.cainghien.companion"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cainghien.companion"
        minSdk = 26
        targetSdk = 34
        versionCode = 14
        versionName = "3.9"
    }

    signingConfigs {
        if (storePath != null) {
            create("release") {
                storeFile = file(storePath)
                storePassword = secret("storePassword")
                keyAlias = secret("keyAlias")
                keyPassword = secret("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (storePath == null) {
                val wantsRelease = gradle.startParameter.taskNames.any { it.contains("Release") }
                if (wantsRelease) {
                    throw GradleException(
                        "Thiếu kyluat.storeFile trong android/local.properties. " +
                            "Release không ký thì người dùng nhận đúng loại bản debug mà Play Protect chặn."
                    )
                }
            } else {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    testImplementation("junit:junit:4.13.2")
}

