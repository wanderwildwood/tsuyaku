import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// The phone is arm64. x86_64 rides along so the same APK runs on the emulator the screenshots
// and tests are made on; `-Pabis=arm64-v8a` builds the phone's alone.
val abis = (findProperty("abis") as String? ?: "arm64-v8a,x86_64").split(',')

android {
    namespace = "com.wanderwildwood.tsuyaku"
    compileSdk = 36
    ndkVersion = "28.0.12674087"

    defaultConfig {
        applicationId = "com.wanderwildwood.tsuyaku"
        // The Kompakt runs Android 12 (API 31); nothing here needs anything newer.
        minSdk = 31
        targetSdk = 31
        versionCode = 31
        versionName = "0.9.0"
        ndk { abiFilters += abis }
    }

    // A real keystore in signing/ signs every build type when it is present, so the
    // very first install is already release-signed and a later update can never hit
    // INSTALL_FAILED_UPDATE_INCOMPATIBLE. It is gitignored, and there is no fallback:
    // a fresh clone builds an unsigned release APK, which will not install anywhere.
    val signingPropertiesFile = rootProject.file("signing/signing.properties")
    val realSigningConfig = if (signingPropertiesFile.isFile) {
        val signingProperties = Properties().apply {
            signingPropertiesFile.inputStream().use(::load)
        }
        signingConfigs.create("real") {
            storeFile = rootProject.file("signing/signing.keystore")
            storePassword = signingProperties.getProperty("STORE_PASSWORD")
            keyAlias = signingProperties.getProperty("KEY_ALIAS")
            keyPassword = signingProperties.getProperty("KEY_PASSWORD")
        }
    } else {
        null
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            realSigningConfig?.let { signingConfig = it }
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            realSigningConfig?.let { signingConfig = it }

            // AGP stamps the git revision into META-INF. The build box works from an rsync
            // with no .git and writes NO_SUPPORTED_VCS_FOUND there, while a CI runner writes
            // the real commit -- so with this on, the same version built in the two places
            // has different contents. Off, so neither happens.
            vcsInfo {
                include = false
            }
        }
    }

    lint {
        // Sideloaded onto a Kompakt, not going to Google Play, whose API-33 floor this
        // otherwise trips. Targeting the OS the device actually runs is deliberate.
        disable += "ExpiredTargetSdkVersion"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        // The About dialog shows the version it is actually running.
        buildConfig = true
    }

    sourceSets {
        named("main") { kotlin.srcDir("src/main/kotlin") }
        named("test") { kotlin.srcDir("src/test/kotlin") }
    }
}

dependencies {
    implementation(project(":app:bergamot"))
    implementation(libs.tesseract4android)
    implementation(libs.androidx.exifinterface)
    implementation(libs.coroutines.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.mmd)

    testImplementation(libs.junit)
    // The JSON parser Android ships, so the catalog can be read in a plain JVM test.
    testImplementation(libs.orgjson)
}
