plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.purplei2p.i2pd.nativelib"
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }

        externalNativeBuild {
            ndkBuild {
                // deps.mk and prebuilt dependencies live in binary/jni, see binary/jni/build.sh
                arguments += "I2PD_DEPS_ROOT=${rootDir.absolutePath}/binary/jni"
                arguments += "-j${Runtime.getRuntime().availableProcessors()}"
            }
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("jni/Android.mk")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
