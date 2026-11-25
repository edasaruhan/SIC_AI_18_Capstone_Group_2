import java.util.Properties

// Demo/test aracı: docs/demo_senaryosu.md'deki bildirimleri gönderir. Teslim APK'sına girmez.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release imzası PROJE DIŞINDA durur (Suzgec-yedekler/anahtar/keystore.properties; CLAUDE.md §0, §10).
// Dosya yoksa (ör. başka bilgisayar) release derlemesi imzasız çıkar; şifreler depoya girmez.
val releaseKeystore = Properties().also { props ->
    val path = providers.gradleProperty("suzgecKeystore").orNull ?: "${rootDir}/../Suzgec-yedekler/anahtar/keystore.properties"
    val f = file(path)
    if (f.exists()) f.inputStream().use { props.load(it) }
}

android {
    namespace = "com.suzgec.sender"
    compileSdk = 37

    signingConfigs {
        if (!releaseKeystore.isEmpty()) create("release") {
            storeFile = file(releaseKeystore.getProperty("storeFile"))
            storePassword = releaseKeystore.getProperty("storePassword")
            keyAlias = releaseKeystore.getProperty("keyAlias")
            keyPassword = releaseKeystore.getProperty("keyPassword")
        }
    }

    defaultConfig {
        applicationId = "com.suzgec.sender"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
