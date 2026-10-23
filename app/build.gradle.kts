import com.android.build.api.variant.FilterConfiguration
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is configured outside of VCS: keystore.properties in the project root
// with storeFile, storePassword, keyAlias and keyPassword.
val keystoreProperties = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}

android {
    namespace = "org.purplei2p.i2pd"
    compileSdk = libs.versions.compileSdk.get().toInt()
    // Needed to strip native libraries: without it AGP looks for its default NDK and silently packages them unstripped
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        applicationId = "org.purplei2p.i2pd"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 3000000
        versionName = "3.0.0"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    signingConfigs {
        if (keystoreProperties != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // R8 and resource shrinking: dex 23.6 -> 2.6 MB. JNI names are kept by native/consumer-rules.pro
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Native libraries compressed in the APK (extracted on install, as in the old app): arm64 APK
    // 14.6 -> 6.9 MB at the cost of the extracted copy on the device
    packaging {
        jniLibs { useLegacyPackaging = true }
    }

    // The dependency block is encrypted for Google Play with a random key: without it the APK
    // is byte-for-byte reproducible (and F-Droid asks to leave it out anyway)
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    androidResources {
        // Per-app language picker of Android 13+; the locales are taken from res/values-*
        generateLocaleConfig = true
        // Only the languages of the app; without it every AndroidX translation is packaged
        localeFilters += listOf("en", "cs", "eo", "es", "fa", "fil", "fr", "hi", "it", "pl", "pt", "ro", "ru", "tr", "uk", "uz", "zh-rCN")
    }
}

base {
    archivesName = "i2pd-${android.defaultConfig.versionName}"
}

// Per-ABI version codes: universal APK keeps the base code, split APKs get +1..+4
val abiCodes = mapOf("armeabi-v7a" to 1, "x86" to 2, "arm64-v8a" to 3, "x86_64" to 4)

androidComponents {
    onVariants { variant ->
        val baseVersionCode = android.defaultConfig.versionCode ?: 0
        variant.outputs.forEach { output ->
            val abi = output.filters.find { it.filterType == FilterConfiguration.FilterType.ABI }?.identifier
            val abiCode = abiCodes[abi] ?: return@forEach
            output.versionCode.set(baseVersionCode + abiCode)
        }
    }
}

dependencies {
    implementation(project(":service"))
    implementation(project(":config"))
    implementation(project(":native"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.documentfile)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
