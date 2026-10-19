plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.purplei2p.i2pd.daemon"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":daemon-api"))
    implementation(project(":native"))
}
