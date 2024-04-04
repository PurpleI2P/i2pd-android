[![GitHub release](https://img.shields.io/github/release/PurpleI2P/i2pd-android.svg?label=latest%20release)](https://github.com/PurpleI2P/i2pd-android/releases/latest)
[![License](https://img.shields.io/github/license/PurpleI2P/i2pd-android.svg)](https://github.com/PurpleI2P/i2pd-android/blob/openssl/LICENSE)
[![Android CI](https://github.com/PurpleI2P/i2pd-android/actions/workflows/android.yml/badge.svg)](https://github.com/PurpleI2P/i2pd-android/actions/workflows/android.yml)

# i2pd android

This repository contains Android application sources of i2pd

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/org.purplei2p.i2pd/)

## How to build

### Install g++, OpenJDK 11+, gradle 5.1+

```bash
sudo apt-get install g++ openjdk-11-jdk gradle
```

If your system provides gradle with version &lt; 5.1, download it from gradle homepage:

https://gradle.org/install/

### Download and prepare Android SDK for building

Android SDK Available here:

https://developer.android.com/studio#downloads

Download Android SDK, unpack it to temporary directory `/tmp/android-sdk` and install it
(in `/opt/android-sdk` for example) with required packages
```bash
mkdir /tmp/android-sdk
cd /tmp/android-sdk
wget -t0 https://dl.google.com/android/repository/commandlinetools-linux-8092744_latest.zip
unzip commandlinetools-linux-8092744_latest.zip
# install required tools
./cmdline-tools/bin/sdkmanager --sdk_root=/opt/android-sdk "build-tools;33.0.1" "cmake;3.22.1" "ndk;23.2.8568313"
```

### Clone repository with submodules

```bash
git clone --recurse-submodules https://github.com/PurpleI2P/i2pd-android.git
```

### Compile application

```bash
# if you are not using Java 11 by default:
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64

export ANDROID_HOME=/opt/android-sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/23.2.8568313

pushd binary/jni
./build_all.sh
popd

gradle clean assembleDebug
```

You will find APKs in `app/build/outputs/apk`

### Building on Windows

For building on Windows you must use MSYS2 with `mingw64` or `ucrt64` shell and preinstalled `gcc` ( package `mingw-w64-x86_64-gcc` or `mingw-w64-ucrt-x86_64-gcc`).

Java 11 can be downloaded from [jdk.java.com](https://jdk.java.net/java-se-ri/11)

Download Android SDK command line tools for Windows, unpack and install it replacing `--sdk_root=` path.

`ANDROID_HOME` variable must point to SDK using linux-way path, like `/c/dev/android-sdk` when SDK installed to `C:\dev\android-sdk`.

Gradle can be called with `./gradlew` command inside project root, or you can install it using `pacman` and call `gradle` like on linux.

## Release signing

Current releases signed with certificate fingerprint (SHA-256):

`FC:C3:C7:34:9E:22:6A:77:B3:70:46:BB:00:FD:04:BB:A5:30:32:21:01:F8:62:F3:6D:8C:3D:B0:EB:B6:35:20`
