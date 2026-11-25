import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release imzası PROJE DIŞINDA durur (Suzgec-yedekler/anahtar/keystore.properties; CLAUDE.md §0, §10).
// Dosya yoksa (ör. başka bilgisayar) release derlemesi imzasız çıkar; şifreler depoya girmez.
val releaseKeystore = Properties().also { props ->
    val path = providers.gradleProperty("suzgecKeystore").orNull ?: "${rootDir}/../Suzgec-yedekler/anahtar/keystore.properties"
    val f = file(path)
    if (f.exists()) f.inputStream().use { props.load(it) }
}

android {
    namespace = "com.suzgec.app"
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
        applicationId = "com.suzgec.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 10
        versionName = "1.0"
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
        buildConfig = true
    }
    androidResources {
        // model JSON'ları sıkıştırılmasın (hızlı okuma)
        noCompress += "json"
    }
}

ksp {
    arg("room.generateKotlin", "true")
    // Şema dosyaları (migration testi bunları okur): app/schemas/
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    // Kutuda sürükleyerek sıralama (CLAUDE.md §7)
    implementation(libs.reorderable)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    // Android stub org.json yerine gerçek uygulama (rules.json ayrıştırma testleri için)
    testImplementation(libs.org.json)
    testImplementation(libs.androidx.sqlite.bundled.jvm)
}
