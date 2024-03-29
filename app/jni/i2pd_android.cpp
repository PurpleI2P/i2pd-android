/*
* Copyright (c) 2013-2022, The PurpleI2P Project
*
* This file is part of Purple i2pd project and licensed under BSD3
*
* See full license text in LICENSE file at top of project tree
*/

#include <jni.h>
#include "org_purplei2p_i2pd_I2PD_JNI.h"
#include "DaemonAndroid.h"
#include "Config.h"
#include "RouterContext.h"
#include "ClientContext.h"
#include "Transports.h"
#include "Tunnel.h"

JNIEXPORT jstring JNICALL Java_org_purplei2p_i2pd_I2PD_1JNI_getABICompiledWith
	(JNIEnv *env, jclass clazz) {
#if defined(__arm__)
	#if defined(__ARM_ARCH_7A__)
		#if defined(__ARM_NEON__)
			#if defined(__ARM_PCS_VFP)
				#define ABI "armeabi-v7a/NEON (hard-float)"
				#else
				#define ABI "armeabi-v7a/NEON"
			#endif
			#else
			#if defined(__ARM_PCS_VFP)
				#define ABI "armeabi-v7a (hard-float)"
				#else
				#define ABI "armeabi-v7a"
			#endif
		#endif
		#else
		#define ABI "armeabi"
	#endif
	#elif defined(__i386__)
	#define ABI "x86"
	#elif defined(__x86_64__)
	#define ABI "x86_64"
	#elif defined(__mips64)  /* mips64el-* toolchain defines __mips__ too */
	#define ABI "mips64"
	#elif defined(__mips__)
	#define ABI "mips"
	#elif defined(__aarch64__)
	#define ABI "arm64-v8a"
	#else
	#define ABI "unknown"
#endif

	return env->NewStringUTF(ABI);
}

JNIEXPORT jstring JNICALL Java_org_purplei2p_i2pd_I2PD_1JNI_startDaemon
	(JNIEnv *env, jclass clazz) {
	return env->NewStringUTF(i2p::android::start().c_str());
}

JNIEXPORT jstring JNICALL Java_org_purplei2p_i2pd_I2PD_1JNI_getDataDir
	(JNIEnv *env, jclass clazz) {
	return env->NewStringUTF(i2p::android::GetDataDir().c_str());
}

JNIEXPORT void JNICALL Java_org_purplei2p_i2pd_I2PD_1JNI_stopDaemon
	(JNIEnv *env, jclass clazz) {
	i2p::android::stop();
}

JNIEXPORT void JNICALL Java_org_purplei2p_i2pd_I2PD_1JNI_stopAcceptingTunnels
	(JNIEnv *env, jclass clazz) {
	i2p::context.SetAcceptsTunnels (false);
}

JNIEXPORT void JNICALL Java_org_purplei2p_i2pd_I2PD_1JNI_startAcceptingTunnels
	(JNIEnv *env, jclass clazz) {
	i2p::context.SetAcceptsTunnels (true);
}

JNIEXPORT void JNICALL Java_org_purplei2p_i2pd_I2PD_1JNI_reloadTunnelsConfigs
	(JNIEnv *env, jclass clazz) {
	i2p::client::context.ReloadConfig();
}

JNIEXPORT void JNICALL Java_org_purplei2p_i2pd_I2PD_1JNI_onNetworkStateChanged
	(JNIEnv *env, jclass clazz, jboolean isConnected) {
	bool isConnectedBool = (bool) isConnected;
	i2p::transport::transports.SetOnline (isConnectedBool);
}

JNIEXPORT void JNICALL Java_org_purplei2p_i2pd_I2PD_1JNI_setDataDir
	(JNIEnv *env, jclass clazz, jstring jdataDir) {
	auto dataDir = env->GetStringUTFChars(jdataDir, NULL);
	i2p::android::SetDataDir(dataDir);
	env->ReleaseStringUTFChars(jdataDir, dataDir);
}

JNIEXPORT jint JNICALL Java_org_purplei2p_i2pd_I2PD_1JNI_getTransitTunnelsCount
	(JNIEnv *env, jclass clazz) {
	return i2p::tunnel::tunnels.CountTransitTunnels();
}

JNIEXPORT jstring JNICALL Java_org_purplei2p_i2pd_I2PD_1JNI_getWebConsAddr
	(JNIEnv *env, jclass clazz) {
	std::string httpAddr; i2p::config::GetOption("http.address", httpAddr);
	uint16_t    httpPort; i2p::config::GetOption("http.port", httpPort);
	std::string result = "http://" + httpAddr + ":" + std::to_string(httpPort) + "/";
	return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL Java_org_purplei2p_i2pd_I2PD_1JNI_setLanguage
	(JNIEnv *env, jclass clazz, jstring jlanguage) {
	auto language = env->GetStringUTFChars(jlanguage, NULL);
	i2p::android::SetLanguage(language);
	env->ReleaseStringUTFChars(jlanguage, language);
}

JNIEXPORT jboolean JNICALL Java_org_purplei2p_i2pd_I2PD_1JNI_getHTTPProxyState
	(JNIEnv *, jclass) {
	return i2p::client::context.GetHttpProxy () ? true : false;
}

JNIEXPORT jboolean JNICALL Java_org_purplei2p_i2pd_I2PD_1JNI_getSOCKSProxyState
	(JNIEnv *, jclass) {
	return i2p::client::context.GetSocksProxy() ? true : false;
}

JNIEXPORT jboolean JNICALL Java_org_purplei2p_i2pd_I2PD_1JNI_getBOBState
	(JNIEnv *, jclass) {
	return i2p::client::context.GetBOBCommandChannel() ? true : false;
}

JNIEXPORT jboolean JNICALL Java_org_purplei2p_i2pd_I2PD_1JNI_getSAMState
	(JNIEnv *, jclass) {
	return i2p::client::context.GetSAMBridge() ? true : false;
}

JNIEXPORT jboolean JNICALL Java_org_purplei2p_i2pd_I2PD_1JNI_getI2CPState
	(JNIEnv *, jclass) {
	return i2p::client::context.GetI2CPServer() ? true : false;
}
