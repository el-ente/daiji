import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// CI passes the tag's version (`-PversionName=0.2.0`) when it builds a release.
val appVersion = providers.gradleProperty("versionName").getOrElse("0.1.0")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.elente.daiji"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.elente.daiji"
        minSdk = 26
        targetSdk = 35
        // 1.2.3 -> 10203, so every release installs over the one before it.
        val parts = appVersion.split('.').map { it.toIntOrNull() ?: -1 }
        require(parts.size == 3 && parts.all { it in 0..99 }) { "versionName must be major.minor.patch, each 0-99: $appVersion" }
        versionCode = parts.fold(0) { code, part -> code * 100 + part }
        versionName = appVersion
    }

    signingConfigs {
        // Development key committed on purpose so every CI build can update
        // the previous install. Replace it before publishing anywhere.
        create("dev") {
            storeFile = file("dev.keystore")
            storePassword = "daiji-dev"
            keyAlias = "daiji"
            keyPassword = "daiji-dev"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("dev")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("dev")
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

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.tv:tv-material:1.0.0")
}
