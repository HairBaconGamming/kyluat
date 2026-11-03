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

// Model nằm một chỗ cho cả Windows lẫn Android; Gradle chép vào APK lúc build.
val nsfwModel = rootProject.file("../enforcer/data/nsfw_model.tflite")
val nsfwNotice = rootProject.file("../enforcer/data/NOTICE-nsfw-model.txt")
check(nsfwModel.exists()) { "Thiếu ${nsfwModel.path}. Chép nsfw_model.tflite vào enforcer/data/." }
check(nsfwNotice.exists()) { "Thiếu ${nsfwNotice.path}." }

val syncNsfwModel = tasks.register<Copy>("syncNsfwModel") {
    into(layout.buildDirectory.dir("generated/nsfwAssets"))
    from(nsfwModel) { into("models") }
    from(nsfwNotice)
}

android {
    namespace = "com.cainghien.companion"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cainghien.companion"
        minSdk = 26
        targetSdk = 34
        versionCode = 19
        versionName = "3.14"
        // Chỉ máy thật ARM; bỏ x86 để APK không mang thêm thư viện không ai dùng.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/nsfwAssets"))

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
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    testImplementation("junit:junit:4.13.2")
}

tasks.named("preBuild") { dependsOn(syncNsfwModel) }

