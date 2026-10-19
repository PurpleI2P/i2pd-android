/*
* Copyright (c) 2013-2026, The PurpleI2P Project
*
* This file is part of Purple i2pd project and licensed under BSD3
*
* See full license text in LICENSE file at top of project tree
*/

#include <jni.h>
#include "version.h"

namespace
{
	const char * const kNativeClass = "org/purplei2p/i2pd/nativelib/I2pdNative";

	jstring Version (JNIEnv * env, jclass)
	{
		return env->NewStringUTF (I2PD_VERSION);
	}

	jstring Abi (JNIEnv * env, jclass)
	{
#if defined(__aarch64__)
		const char * abi = "arm64-v8a";
#elif defined(__arm__)
		const char * abi = "armeabi-v7a";
#elif defined(__x86_64__)
		const char * abi = "x86_64";
#elif defined(__i386__)
		const char * abi = "x86";
#else
		const char * abi = "unknown";
#endif
		return env->NewStringUTF (abi);
	}

	const JNINativeMethod kMethods[] =
	{
		{ "version", "()Ljava/lang/String;", reinterpret_cast<void *>(Version) },
		{ "abi",     "()Ljava/lang/String;", reinterpret_cast<void *>(Abi) },
	};
}

JNIEXPORT jint JNI_OnLoad (JavaVM * vm, void *)
{
	JNIEnv * env = nullptr;
	if (vm->GetEnv (reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK)
		return JNI_ERR;

	jclass clazz = env->FindClass (kNativeClass);
	if (!clazz)
		return JNI_ERR;

	jint rc = env->RegisterNatives (clazz, kMethods, sizeof (kMethods) / sizeof (kMethods[0]));
	env->DeleteLocalRef (clazz);
	return rc == JNI_OK ? JNI_VERSION_1_6 : JNI_ERR;
}
