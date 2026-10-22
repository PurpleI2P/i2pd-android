APP_ABI := armeabi-v7a arm64-v8a x86 x86_64

APP_PLATFORM := android-23

APP_STL := c++_static

# i2pd requires C++20 or newer
APP_CPPFLAGS += -std=c++20 -fexceptions -frtti

APP_CPPFLAGS += -DANDROID_BINARY -DANDROID -D__ANDROID__ -DUSE_UPNP -Wno-deprecated-declarations
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
APP_CPPFLAGS += -DANDROID_ARM7A
endif

# Reproducible builds: source paths compiled in (Boost.Asio error locations) and the paths in the debug info,
# which change the build ID even after stripping, do not depend on where the repository and the NDK are
APP_CFLAGS += -ffile-prefix-map=$(abspath $(NDK_PROJECT_PATH)/..)=. -ffile-prefix-map=$(NDK_ROOT)=/ndk -fdebug-compilation-dir=.

APP_LDFLAGS += -fPIE -Wl,-z,max-page-size=16384
