# Shared ndk-build definitions for i2pd and its dependencies.
# Used by the standalone binary (binary/jni/Android.mk) and by the app library (native/jni/Android.mk).
#
# I2PD_DEPS_ROOT must point to this directory (binary/jni).

ifeq ($(I2PD_DEPS_ROOT),)
$(error I2PD_DEPS_ROOT is not set)
endif

IFADDRS_PATH  := $(I2PD_DEPS_ROOT)/ifaddrs
BOOST_PATH    := $(I2PD_DEPS_ROOT)/deps/boost
MINIUPNP_PATH := $(I2PD_DEPS_ROOT)/deps/miniupnpc
OPENSSL_PATH  := $(I2PD_DEPS_ROOT)/deps/openssl
I2PD_SRC_PATH := $(I2PD_DEPS_ROOT)/i2pd

LIB_SRC_PATH        := $(I2PD_SRC_PATH)/libi2pd
LIB_CLIENT_SRC_PATH := $(I2PD_SRC_PATH)/libi2pd_client
LANG_SRC_PATH       := $(I2PD_SRC_PATH)/i18n
DAEMON_SRC_PATH     := $(I2PD_SRC_PATH)/daemon

ifeq ($(wildcard $(BOOST_PATH)/.version),)
$(error Boost sources not found in $(BOOST_PATH). Run binary/jni/build.sh first)
endif
ifeq ($(wildcard $(MINIUPNP_PATH)/.version),)
$(error miniupnpc sources not found in $(MINIUPNP_PATH). Run binary/jni/build.sh first)
endif
ifeq ($(wildcard $(OPENSSL_PATH)/out/$(TARGET_ARCH_ABI)/lib/libcrypto.a),)
$(error OpenSSL for $(TARGET_ARCH_ABI) not found. Run binary/jni/build.sh first)
endif

# Common sources and includes of i2pd, shared by the binary and the library
I2PD_C_INCLUDES := $(IFADDRS_PATH) $(LIB_SRC_PATH) $(LIB_CLIENT_SRC_PATH) $(LANG_SRC_PATH) $(DAEMON_SRC_PATH)
I2PD_SRC_FILES := \
	$(IFADDRS_PATH)/ifaddrs.cpp \
	$(IFADDRS_PATH)/bionic_netlink.cpp \
	$(wildcard $(LIB_SRC_PATH)/*.cpp) \
	$(wildcard $(LIB_CLIENT_SRC_PATH)/*.cpp) \
	$(wildcard $(LANG_SRC_PATH)/*.cpp) \
	$(DAEMON_SRC_PATH)/Daemon.cpp \
	$(DAEMON_SRC_PATH)/UPnP.cpp \
	$(DAEMON_SRC_PATH)/HTTPServer.cpp \
	$(DAEMON_SRC_PATH)/I2PControl.cpp \
	$(DAEMON_SRC_PATH)/I2PControlHandlers.cpp
I2PD_STATIC_LIBRARIES := boost_program_options boost_json boost_url ssl crypto miniupnpc

DEPS_LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_PATH := $(DEPS_LOCAL_PATH)
LOCAL_MODULE := boost_program_options
LOCAL_CPP_FEATURES := rtti exceptions
LOCAL_SRC_FILES := $(wildcard $(BOOST_PATH)/libs/program_options/src/*.cpp)
LOCAL_C_INCLUDES := $(BOOST_PATH)
LOCAL_EXPORT_C_INCLUDES := $(BOOST_PATH)
include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_PATH := $(DEPS_LOCAL_PATH)
LOCAL_MODULE := boost_json
LOCAL_CPP_FEATURES := rtti exceptions
LOCAL_SRC_FILES := $(BOOST_PATH)/libs/json/src/src.cpp
LOCAL_C_INCLUDES := $(BOOST_PATH)
LOCAL_CPPFLAGS := -DBOOST_JSON_SOURCE -DBOOST_JSON_STATIC_LINK=1
LOCAL_EXPORT_C_INCLUDES := $(BOOST_PATH)
LOCAL_EXPORT_CPPFLAGS := -DBOOST_JSON_STATIC_LINK=1
include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_PATH := $(DEPS_LOCAL_PATH)
LOCAL_MODULE := boost_url
LOCAL_CPP_FEATURES := rtti exceptions
LOCAL_SRC_FILES := $(wildcard \
	$(BOOST_PATH)/libs/url/src/*.cpp \
	$(BOOST_PATH)/libs/url/src/*/*.cpp \
	$(BOOST_PATH)/libs/url/src/*/*/*.cpp)
LOCAL_C_INCLUDES := $(BOOST_PATH)
LOCAL_CPPFLAGS := -DBOOST_URL_SOURCE -DBOOST_URL_STATIC_LINK=1
LOCAL_EXPORT_C_INCLUDES := $(BOOST_PATH)
LOCAL_EXPORT_CPPFLAGS := -DBOOST_URL_STATIC_LINK=1
include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_PATH := $(DEPS_LOCAL_PATH)
LOCAL_MODULE := crypto
LOCAL_SRC_FILES := $(OPENSSL_PATH)/out/$(TARGET_ARCH_ABI)/lib/libcrypto.a
LOCAL_EXPORT_C_INCLUDES := $(OPENSSL_PATH)/out/$(TARGET_ARCH_ABI)/include
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_PATH := $(DEPS_LOCAL_PATH)
LOCAL_MODULE := ssl
LOCAL_SRC_FILES := $(OPENSSL_PATH)/out/$(TARGET_ARCH_ABI)/lib/libssl.a
LOCAL_EXPORT_C_INCLUDES := $(OPENSSL_PATH)/out/$(TARGET_ARCH_ABI)/include
LOCAL_STATIC_LIBRARIES := crypto
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_PATH := $(DEPS_LOCAL_PATH)
LOCAL_MODULE := miniupnpc
# The library sources and definitions of its CMakeLists.txt (Linux-like, static)
LOCAL_SRC_FILES := $(addprefix $(MINIUPNP_PATH)/src/, \
	igd_desc_parse.c miniupnpc.c minixml.c minisoap.c minissdpc.c miniwget.c upnpcommands.c upnpdev.c \
	upnpreplyparse.c upnperrors.c connecthostport.c portlistingparse.c receivedata.c addr_is_reserved.c)
LOCAL_C_INCLUDES := $(MINIUPNP_PATH)/include $(MINIUPNP_PATH)/generated
LOCAL_CFLAGS := -DMINIUPNPC_SET_SOCKET_TIMEOUT -DMINIUPNPC_GET_SRC_ADDR -D_BSD_SOURCE -D_DEFAULT_SOURCE \
	-D_XOPEN_SOURCE=600 -DMINIUPNP_STATICLIB
LOCAL_EXPORT_C_INCLUDES := $(MINIUPNP_PATH)/export
LOCAL_EXPORT_CFLAGS := -DMINIUPNP_STATICLIB
include $(BUILD_STATIC_LIBRARY)
