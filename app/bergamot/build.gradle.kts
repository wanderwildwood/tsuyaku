plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// The phone is arm64. x86_64 rides along so the same APK runs on the emulator the screenshots
// and tests are made on; `-Pabis=arm64-v8a` builds the phone's alone.
val abis = (findProperty("abis") as String? ?: "arm64-v8a,x86_64").split(',')

android {
    namespace = "dev.davidv.bergamot"
    compileSdk = 36
    ndkVersion = "28.0.12674087"

    defaultConfig {
        minSdk = 31
        consumerProguardFiles("consumer-rules.pro")
        ndk { abiFilters += abis }
        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17")
                // Translation is far too slow unoptimised to be any use, debug build or not.
                arguments += listOf("-DCMAKE_BUILD_TYPE=Release")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
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
}
