# APP_ABI and APP_PLATFORM are provided by Android Gradle Plugin (abiFilters, minSdk)

APP_STL := c++_static

# i2pd requires C++20 or newer
APP_CPPFLAGS += -std=c++20 -fexceptions -frtti
APP_CPPFLAGS += -DANDROID -D__ANDROID__ -DUSE_UPNP -Wno-deprecated-declarations
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
APP_CPPFLAGS += -DANDROID_ARM7A
endif

# Export only JNI_OnLoad, let the linker drop unused code
APP_CFLAGS += -fvisibility=hidden -ffunction-sections -fdata-sections
APP_CPPFLAGS += -fvisibility-inlines-hidden
APP_LDFLAGS += -Wl,--gc-sections -Wl,--exclude-libs,ALL -Wl,-z,max-page-size=16384
