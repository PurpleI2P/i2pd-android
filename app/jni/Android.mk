LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := i2pd
LOCAL_CPP_FEATURES := rtti exceptions
LOCAL_C_INCLUDES += $(IFADDRS_PATH) $(LIB_SRC_PATH) $(LIB_CLIENT_SRC_PATH) $(LANG_SRC_PATH) $(DAEMON_SRC_PATH)
LOCAL_STATIC_LIBRARIES := \
	boost_system \
	boost_program_options \
	crypto \
	ssl \
	miniupnpc
LOCAL_LDLIBS := -lz

LOCAL_SRC_FILES := \
	DaemonAndroid.cpp \
	i2pd_android.cpp \
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

include $(BUILD_SHARED_LIBRARY)

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := boost_system
LOCAL_SRC_FILES := $(BOOST_PATH)/build/out/$(TARGET_ARCH_ABI)/lib/libboost_system.a
LOCAL_EXPORT_C_INCLUDES := $(BOOST_PATH)/build/out/$(TARGET_ARCH_ABI)/include
include $(PREBUILT_STATIC_LIBRARY)

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := boost_program_options
LOCAL_SRC_FILES := $(BOOST_PATH)/build/out/$(TARGET_ARCH_ABI)/lib/libboost_program_options.a
LOCAL_EXPORT_C_INCLUDES := $(BOOST_PATH)/build/out/$(TARGET_ARCH_ABI)/include
include $(PREBUILT_STATIC_LIBRARY)

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := crypto
LOCAL_SRC_FILES := $(OPENSSL_PATH)/out/$(TARGET_ARCH_ABI)/lib/libcrypto.a
LOCAL_EXPORT_C_INCLUDES := $(OPENSSL_PATH)/out/$(TARGET_ARCH_ABI)/include
include $(PREBUILT_STATIC_LIBRARY)

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := ssl
LOCAL_SRC_FILES := $(OPENSSL_PATH)/out/$(TARGET_ARCH_ABI)/lib/libssl.a
LOCAL_EXPORT_C_INCLUDES := $(OPENSSL_PATH)/out/$(TARGET_ARCH_ABI)/include
LOCAL_STATIC_LIBRARIES := crypto
include $(PREBUILT_STATIC_LIBRARY)

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := miniupnpc
LOCAL_SRC_FILES := $(MINIUPNP_PATH)/miniupnpc/out/$(TARGET_ARCH_ABI)/lib/libminiupnpc.a
LOCAL_EXPORT_C_INCLUDES := $(MINIUPNP_PATH)/miniupnpc/out/$(TARGET_ARCH_ABI)/include
include $(PREBUILT_STATIC_LIBRARY)
