[![GitHub release](https://img.shields.io/github/release/PurpleI2P/i2pd-android.svg?label=latest%20release)](https://github.com/PurpleI2P/i2pd-android/releases/latest)
[![License](https://img.shields.io/github/license/PurpleI2P/i2pd-android.svg)](https://github.com/PurpleI2P/i2pd-android/blob/openssl/LICENSE)
[![Android CI](https://github.com/PurpleI2P/i2pd-android/actions/workflows/android.yml/badge.svg)](https://github.com/PurpleI2P/i2pd-android/actions/workflows/android.yml)

# i2pd android

This repository contains Android application sources of i2pd

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/org.purplei2p.i2pd/)

## How to build

### Install OpenJDK 11+, g++, rename (used for building modules), gradle 5.1+
```bash
sudo apt-get install g++ rename openjdk-11-jdk gradle
```

If your system provides gradle with version < 5.1, download it from gradle homepage:

https://gradle.org/install/

### Download and prepare Android SDK for building
Android SDK Available here:

https://developer.android.com/studio#downloads

Download Android SDK, unpack it to temporary directory `/tmp/anrdoid-sdk` and install it (in `/opt/android-sdk` for example) with required packages
```bash
mkdir /tmp/android-sdk
cd /tmp/android-sdk
wget https://dl.google.com/android/repository/commandlinetools-linux-8092744_latest.zip
unzip commandlinetools-linux-8092744_latest.zip
# install required tools
./cmdline-tools/bin/sdkmanager --sdk_root=/opt/android-sdk "build-tools;31.0.0" "cmake;3.18.1" "ndk;21.4.7075529"
```

### Clone repository with submodules
```bash
git clone --recurse-submodules https://github.com/PurpleI2P/i2pd-android.git
```

### Compile application
```bash
export ANDROID_SDK_ROOT=/opt/android-sdk
export ANDROID_NDK_HOME=$ANDROID_SDK_ROOT/ndk/21.4.7075529

pushd app/jni
./build_boost.sh
./build_openssl.sh
./build_miniupnpc.sh
popd

gradle clean assembleDebug
```

You will find APKs in `app/build/outputs/apk`
