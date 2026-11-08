import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

// Release signing is optional: pass -PALEX_APPS_STORE_PASSWORD (and optionally
// -PALEX_APPS_KEY_PASSWORD / -PALEX_APPS_KEYSTORE / -PALEX_APPS_KEY_ALIAS) or set
// the matching environment variables. Nothing secret is stored in the repo.
val releaseKeystoreFile = (findProperty("ALEX_APPS_KEYSTORE") as String?)
    ?: System.getenv("ALEX_APPS_KEYSTORE")
    ?: File(rootDir, "alex-app-manager.jks").absolutePath
val releaseKeyAlias = (findProperty("ALEX_APPS_KEY_ALIAS") as String?)
    ?: System.getenv("ALEX_APPS_KEY_ALIAS")
    ?: "alex-app-manager"
val releaseStorePassword = (findProperty("ALEX_APPS_STORE_PASSWORD") as String?)
    ?: System.getenv("ALEX_APPS_STORE_PASSWORD")
val releaseKeyPassword = (findProperty("ALEX_APPS_KEY_PASSWORD") as String?)
    ?: System.getenv("ALEX_APPS_KEY_PASSWORD")
    ?: releaseStorePassword
val releaseSigningReady = !releaseStorePassword.isNullOrEmpty() && File(releaseKeystoreFile).isFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.shahzadp.appmanager"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.shahzadp.appmanager"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "0.2.8"

        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(releaseKeystoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
                        if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                println("release signing not configured; building an unsigned release APK. " +
                    "Pass -PALEX_APPS_STORE_PASSWORD=... to sign.")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    // Compose 1.12 pairs with Material3 1.5, which is where the expressive
    // component set (ButtonGroup, LoadingIndicator, shapes, motion) lives.
    implementation("androidx.compose.ui:ui:1.12.0")
    implementation("androidx.compose.ui:ui-graphics:1.12.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.12.0")
    implementation("androidx.compose.foundation:foundation:1.12.0")
    implementation("androidx.compose.animation:animation:1.12.0")
    implementation("androidx.compose.material3:material3:1.5.0-rc01")
    implementation("androidx.compose.material3:material3-window-size-class:1.5.0-rc01")
    // Material 3 no longer pulls the icon set in transitively; the app uses a
    // handful of core icons (Settings, Refresh, Check, Close, Warning).
    implementation("androidx.compose.material:material-icons-core:1.7.8")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling:1.12.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.12.0")
    testImplementation("junit:junit:4.13.2")
}


















