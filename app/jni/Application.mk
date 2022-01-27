NDK_TOOLCHAIN_VERSION := clang
APP_STL := c++_static

# Enable c++17 extensions in source code
APP_CPPFLAGS += -std=c++17 -fexceptions -frtti

APP_CPPFLAGS += -DANDROID -D__ANDROID__ -DUSE_UPNP
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
APP_CPPFLAGS += -DANDROID_ARM7A
endif

IFADDRS_PATH  = $(NDK_MODULE_PATH)/android-ifaddrs
BOOST_PATH    = $(NDK_MODULE_PATH)/boost
MINIUPNP_PATH = $(NDK_MODULE_PATH)/miniupnp
OPENSSL_PATH  = $(NDK_MODULE_PATH)/openssl

# don't change me
I2PD_SRC_PATH = $(NDK_MODULE_PATH)/i2pd

LIB_SRC_PATH        = $(I2PD_SRC_PATH)/libi2pd
LIB_CLIENT_SRC_PATH = $(I2PD_SRC_PATH)/libi2pd_client
LANG_SRC_PATH       = $(I2PD_SRC_PATH)/i18n
DAEMON_SRC_PATH     = $(I2PD_SRC_PATH)/daemon
