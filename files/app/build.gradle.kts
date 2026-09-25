import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Diyanet Awqat Salah API hesabı (isteğe bağlı).
// local.properties içine veya ortam değişkeni olarak:
//   DIYANET_EMAIL=...
//   DIYANET_PASSWORD=...
// Boş bırakılırsa uygulama aracı API'ye, o da olmazsa hesaplamaya düşer.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(key: String): String =
    (localProps.getProperty(key) ?: System.getenv(key) ?: "").replace("\"", "\\\"")

android {
    namespace = "com.oguz.ezanvakti"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.oguz.ezanvakti"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "DIYANET_EMAIL", "\"${secret("DIYANET_EMAIL")}\"")
        buildConfigField("String", "DIYANET_PASSWORD", "\"${secret("DIYANET_PASSWORD")}\"")
    }

    signingConfigs {
        create("sabit") {
            // Sabit anahtar: güncellemeler eski sürümün üstüne kurulabilsin diye.
            storeFile = rootProject.file("keystore/ezan.jks")
            storePassword = "ezanvakti"
            keyAlias = "ezan"
            keyPassword = "ezanvakti"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("sabit")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sabit")
        }
    }

    lint {
        // Derleme lint uyarıları yüzünden durmasın
        abortOnError = false
        checkReleaseBuilds = false
    }

    buildFeatures {
        buildConfig = true
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
    // Bilerek harici kütüphane yok: sadece Android SDK + Kotlin stdlib.
}
