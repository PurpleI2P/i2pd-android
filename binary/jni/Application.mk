APP_ABI := armeabi-v7a arm64-v8a x86 x86_64

APP_PLATFORM := android-23

APP_STL := c++_static

# i2pd requires C++20 or newer
APP_CPPFLAGS += -std=c++20 -fexceptions -frtti

APP_CPPFLAGS += -DANDROID_BINARY -DANDROID -D__ANDROID__ -DUSE_UPNP -Wno-deprecated-declarations
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
APP_CPPFLAGS += -DANDROID_ARM7A
endif

APP_LDFLAGS += -fPIE -Wl,-z,max-page-size=16384
