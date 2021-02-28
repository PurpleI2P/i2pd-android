# i2pd android

### Install OpenJDK, gradle 6.8.3, download Android SDK and NDK r21e
https://gradle.org/install/

https://developer.android.com/studio#downloads

https://developer.android.com/ndk/

### Clone repository with submodules
    git clone --recurse-submodules https://github.com/PurpleI2P/i2pd-android.git

### Compile application
    export ANDROID_SDK_ROOT=/opt/android-sdk
    export ANDROID_NDK_HOME=/opt/android-ndk-r21e
    
    gradle clean assembleDebug

You will find APKs in `app/build/outputs/apk`
