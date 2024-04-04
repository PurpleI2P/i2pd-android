# Android command line executable build instructions

1. Install ndk 23.2.8568313 into some directory, usually `ANDROID_SDK/ndk/23.2.8568313`.

2. Install cmake 3.19 or newer, e.g. for Unix:

```sh
wget -t0 https://github.com/Kitware/CMake/releases/download/v3.28.0/cmake-3.28.0.tar.gz && tar xzvf cmake-3.28.0.tar.gz && cd cmake-3.28.0 && ./configure && make -j$(nproc) && sudo make install
```

3. In the current working directory (the one with Android.mk and Application.mk), run:

```bash
# E.g, export ANDROID_NDK_HOME=/opt/android-sdk/ndk/23.2.8568313
export ANDROID_NDK_HOME=your_ndk_directory
./build_all.sh
```

Then, `../libs` will be populated with build executable files
(that are renamed to `*.so` for Gradle to put them into the APK).
