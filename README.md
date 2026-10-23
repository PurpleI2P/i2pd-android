[![GitHub release](https://img.shields.io/github/release/PurpleI2P/i2pd-android.svg?label=latest%20release)](https://github.com/PurpleI2P/i2pd-android/releases/latest)
[![License](https://img.shields.io/github/license/PurpleI2P/i2pd-android.svg)](https://github.com/PurpleI2P/i2pd-android/blob/openssl/LICENSE)
[![Android CI](https://github.com/PurpleI2P/i2pd-android/actions/workflows/android.yml/badge.svg)](https://github.com/PurpleI2P/i2pd-android/actions/workflows/android.yml)
[![Downloads](https://img.shields.io/github/downloads/PurpleI2P/i2pd-android/total.svg)]()

# i2pd android

This repository contains Android application sources of i2pd

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/org.purplei2p.i2pd/)

## Bounties &amp; Donations for this app

* Send XMR to 89E1cHc7dkGKTcLxTMQti1W94NdYUxGokhGav5ubLP9jAmE7DmigfoYYx5b64LnTaaFcdczvpAVEsbtx4FFjPm2yJgkEvy2 ; if you want to include bounty/donation notes, create an issue at https://github.com/PurpleI2P/i2pd-android/issues or send an e-mail to hypnosis@i2pmail.org ; include the XMR amount data into your notes.

## Team

* Lead dev: Hypnosis aka nonlin-lin etc
* Releases, merging into PurpleI2P: R4SAS
* Sometimes, fixes & improvements: WipedLifePotato
* Sometimes, merging into PurpleI2P: orignal

## How to build

### Install build tools

OpenJDK 17+, `make`, `cmake`, `perl`, `curl` and `zip` are required.

```bash
sudo apt-get install openjdk-17-jdk make cmake perl curl zip
```

Gradle itself is provided by the wrapper (`./gradlew`).

### Download and prepare Android SDK for building

Android SDK command line tools are available here:

https://developer.android.com/studio#command-line-tools-only

Install them (to `/opt/android-sdk` for example) together with required packages. The platform must match `compileSdk` and the NDK version must match `ndk` in `gradle/libs.versions.toml`. Build-tools version is the default one of Android Gradle Plugin:

```bash
./cmdline-tools/bin/sdkmanager --sdk_root=/opt/android-sdk "platforms;android-37.0" "build-tools;36.0.0" "ndk;29.0.14206865"
```

### Clone repository with submodules

```bash
git clone --recurse-submodules https://github.com/PurpleI2P/i2pd-android.git
cd i2pd-android
```

### Build native dependencies

Release archives of Boost, OpenSSL and miniupnpc are downloaded into `binary/jni/deps` and checked against sha256 sums from `binary/jni/fetch_deps.sh` (for an offline build put the archives there beforehand). OpenSSL is built with its own `Configure`, Boost libraries and miniupnpc are compiled by ndk-build together with i2pd. Results are cached, so the script only rebuilds what changed:

```bash
export ANDROID_HOME=/opt/android-sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/29.0.14206865

binary/jni/build.sh
```

### Compile application

```bash
./gradlew assembleDebug
```

You will find APKs in `app/build/outputs/apk`.

For signed release builds create `keystore.properties` in the project root with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`, then run `./gradlew assembleRelease`.

### Standalone binaries

`binary/jni/build.sh -b` builds the i2pd executable for all ABIs into `binary/libs`, `contrib/binary_pack/build-archive` packs them into a zip.

### Building on Windows

For building on Windows you must use MSYS2 with `mingw64` or `ucrt64` shell and preinstalled `make`, `perl` and `curl`.

Download Android SDK command line tools for Windows, unpack and install it replacing `--sdk_root=` path.

`ANDROID_HOME` variable must point to SDK using linux-way path, like `/c/dev/android-sdk` when SDK installed to `C:\dev\android-sdk`.

## Release signing

Current releases signed with certificate fingerprint (SHA-256):

`FC:C3:C7:34:9E:22:6A:77:B3:70:46:BB:00:FD:04:BB:A5:30:32:21:01:F8:62:F3:6D:8C:3D:B0:EB:B6:35:20`
