plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.alwaysmu.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alwaysmu.app"
        minSdk = 26
        targetSdk = 35
        // Su GitHub Actions il numero di build diventa la versione, così ogni APK aggiorna il precedente.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.0.$build"
    }

    signingConfigs {
        // Chiave fissa nella repo: tutti gli APK (locali e CI) hanno la stessa firma
        // e si possono installare come aggiornamento senza disinstallare.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    // standard: senza accesso alle notifiche, si installa senza blocchi di Play Protect.
    // completa: con accesso alle notifiche (play più preciso), Play Protect può bloccarla.
    flavorDimensions += "edition"
    productFlavors {
        create("standard") {
            dimension = "edition"
            buildConfigField("boolean", "HAS_MEDIA_LISTENER", "false")
        }
        create("completa") {
            dimension = "edition"
            versionNameSuffix = "-completa"
            buildConfigField("boolean", "HAS_MEDIA_LISTENER", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    testImplementation("junit:junit:4.13.2")
}
