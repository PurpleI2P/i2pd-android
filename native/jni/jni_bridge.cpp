/*
* Copyright (c) 2013-2026, The PurpleI2P Project
*
* This file is part of Purple i2pd project and licensed under BSD3
*
* See full license text in LICENSE file at top of project tree
*/

#include <jni.h>
#include <atomic>
#include <cstdint>
#include <exception>
#include <memory>
#include <mutex>
#include <sstream>
#include <string>
#include <type_traits>
#include <vector>
#include <boost/exception/diagnostic_information.hpp>
#include <boost/program_options.hpp>
#include "version.h"
#include "Daemon.h"
#include "Config.h"
#include "Log.h"
#include "I18N.h"
#include "RouterContext.h"
#include "Transports.h"
#include "Tunnel.h"
#include "NetDb.hpp"
#include "ClientContext.h"
#include "log_buffer.h"

namespace i2p
{
namespace config
{
	// Defined in Config.cpp, not declared in Config.h
	extern boost::program_options::options_description m_OptionsDesc;
}
}

// Keep in sync with org.purplei2p.i2pd.nativelib.I2pdNative
namespace
{
	const char * const kNativeClass = "org/purplei2p/i2pd/nativelib/I2pdNative";
	const size_t kLogCapacity = 5000;

	// Indexes of status() array, see I2pdNative.Status
	enum StatusIndex
	{
		eStatusV4 = 0, eStatusV6, eErrorV4, eErrorV6, eTestingV4, eTestingV6,
		eUptime, eAcceptsTransit, eFloodfill,
		eBandwidthIn, eBandwidthOut, eBandwidthTransit,
		eTotalReceived, eTotalSent, eTotalTransit,
		eTunnelsIn, eTunnelsOut, eTunnelsTransit, eTunnelSuccessRate,
		eRouters, eFloodfills, eLeaseSets,
		eServices, eNetId,
		eStatusSize
	};

	// Bits of eServices
	enum ServiceBit
	{
		eServiceHttpProxy = 1 << 0,
		eServiceSocksProxy = 1 << 1,
		eServiceSam = 1 << 2,
		eServiceBob = 1 << 3,
		eServiceI2cp = 1 << 4,
		eServiceWebConsole = 1 << 5
	};

	JavaVM * g_Vm = nullptr;
	std::mutex g_FatalHandlerMutex;
	jobject g_FatalHandler = nullptr; // global ref
	jmethodID g_OnFatal = nullptr;

	i2p::android::LogBuffer g_LogBuffer (kLogCapacity);
	std::atomic<bool> g_Initialized { false };
	// config::Init () fills global option descriptions and may run only once per process
	std::mutex g_ConfigInitMutex;
	bool g_ConfigInitCalled = false;
	std::atomic<bool> g_Running { false };

	std::string ToString (JNIEnv * env, jstring s)
	{
		if (!s) return {};
		const char * chars = env->GetStringUTFChars (s, nullptr);
		std::string result (chars ? chars : "");
		if (chars) env->ReleaseStringUTFChars (s, chars);
		return result;
	}

	jstring ToJString (JNIEnv * env, const std::string& s)
	{
		return env->NewStringUTF (s.c_str ());
	}

	template<typename F>
	jstring CatchAll (JNIEnv * env, F&& f)
	{
		try
		{
			std::string error = f ();
			return error.empty () ? nullptr : ToJString (env, error);
		}
		catch (boost::exception& ex)
		{
			return ToJString (env, boost::diagnostic_information (ex));
		}
		catch (std::exception& ex)
		{
			return ToJString (env, ex.what ());
		}
		catch (...)
		{
			return ToJString (env, "unknown exception");
		}
	}

	/** Called by i2pd on fatal errors, possibly right before exit(), from any thread */
	void OnFatal (const std::string& message)
	{
		LogPrint (eLogCritical, "Fatal: ", message);
		std::lock_guard<std::mutex> l(g_FatalHandlerMutex);
		if (!g_Vm || !g_FatalHandler) return;
		JNIEnv * env = nullptr;
		bool attached = false;
		if (g_Vm->GetEnv (reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_EDETACHED)
		{
			if (g_Vm->AttachCurrentThread (&env, nullptr) != JNI_OK) return;
			attached = true;
		}
		jstring jmessage = ToJString (env, message);
		env->CallVoidMethod (g_FatalHandler, g_OnFatal, jmessage);
		if (env->ExceptionCheck ()) env->ExceptionClear ();
		env->DeleteLocalRef (jmessage);
		if (attached) g_Vm->DetachCurrentThread ();
	}

	jstring Version (JNIEnv * env, jclass)
	{
		return ToJString (env, I2PD_VERSION);
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
		return ToJString (env, abi);
	}

	void SetFatalHandler (JNIEnv * env, jclass, jobject handler)
	{
		std::lock_guard<std::mutex> l(g_FatalHandlerMutex);
		if (g_FatalHandler)
		{
			env->DeleteGlobalRef (g_FatalHandler);
			g_FatalHandler = nullptr;
		}
		if (handler)
		{
			g_FatalHandler = env->NewGlobalRef (handler);
			jclass clazz = env->GetObjectClass (handler);
			g_OnFatal = env->GetMethodID (clazz, "onFatal", "(Ljava/lang/String;)V");
			env->DeleteLocalRef (clazz);
		}
	}

	jstring Init (JNIEnv * env, jclass, jstring jdataDir, jobjectArray jargs, jstring jlanguage)
	{
		std::string dataDir = ToString (env, jdataDir);
		std::string language = ToString (env, jlanguage);
		std::vector<std::string> args { "i2pd" };
		jsize count = jargs ? env->GetArrayLength (jargs) : 0;
		for (jsize i = 0; i < count; i++)
		{
			auto jarg = static_cast<jstring>(env->GetObjectArrayElement (jargs, i));
			args.push_back (ToString (env, jarg));
			env->DeleteLocalRef (jarg);
		}

		return CatchAll (env, [&]() -> std::string
		{
			if (g_Initialized.exchange (true))
				return "Daemon is already initialized in this process";
			{
				std::lock_guard<std::mutex> l(g_ConfigInitMutex);
				if (g_ConfigInitCalled)
					return "Options schema was loaded in this process, the daemon cannot be initialized here";
				g_ConfigInitCalled = true; // Daemon.init calls config::Init
			}

			i2p::log::SetThrowFunction (OnFatal);
			std::vector<char *> argv;
			for (auto& arg: args) argv.push_back (arg.data ());
			argv.push_back (nullptr);

			Daemon.setDataDir (dataDir);
			auto logStream = std::make_shared<i2p::android::LogStream>(g_LogBuffer);
			if (!Daemon.init (static_cast<int>(args.size ()), argv.data (), logStream))
				return "Daemon init failed";
			if (!language.empty ())
				i2p::i18n::SetLanguage (language);
			return {};
		});
	}

	jstring Start (JNIEnv * env, jclass)
	{
		return CatchAll (env, []() -> std::string
		{
			if (!g_Initialized) return "Daemon is not initialized";
			if (!Daemon.start ()) return "Daemon start failed";
			g_Running = true;
			return {};
		});
	}

	void Stop (JNIEnv * env, jclass)
	{
		if (!g_Running.exchange (false)) return;
		try
		{
			Daemon.stop ();
		}
		catch (...)
		{
			// the process is going to exit anyway
		}
	}

	jboolean Status (JNIEnv * env, jclass, jlongArray jout)
	{
		if (!g_Running || !jout || env->GetArrayLength (jout) < eStatusSize) return JNI_FALSE;

		jlong s[eStatusSize] = {};
		s[eStatusV4] = i2p::context.GetStatus ();
		s[eStatusV6] = i2p::context.GetStatusV6 ();
		s[eErrorV4] = i2p::context.GetError ();
		s[eErrorV6] = i2p::context.GetErrorV6 ();
		s[eTestingV4] = i2p::context.GetTesting ();
		s[eTestingV6] = i2p::context.GetTestingV6 ();
		s[eUptime] = i2p::context.GetUptime ();
		s[eAcceptsTransit] = i2p::context.AcceptsTunnels ();
		s[eFloodfill] = i2p::context.IsFloodfill ();
		s[eBandwidthIn] = i2p::transport::transports.GetInBandwidth15s ();
		s[eBandwidthOut] = i2p::transport::transports.GetOutBandwidth15s ();
		s[eBandwidthTransit] = i2p::transport::transports.GetTransitBandwidth15s ();
		s[eTotalReceived] = i2p::transport::transports.GetTotalReceivedBytes ();
		s[eTotalSent] = i2p::transport::transports.GetTotalSentBytes ();
		s[eTotalTransit] = i2p::transport::transports.GetTotalTransitTransmittedBytes ();
		s[eTunnelsIn] = i2p::tunnel::tunnels.CountInboundTunnels ();
		s[eTunnelsOut] = i2p::tunnel::tunnels.CountOutboundTunnels ();
		s[eTunnelsTransit] = i2p::tunnel::tunnels.CountTransitTunnels ();
		s[eTunnelSuccessRate] = i2p::tunnel::tunnels.GetTunnelCreationSuccessRate ();
		s[eRouters] = i2p::data::netdb.GetNumRouters ();
		s[eFloodfills] = i2p::data::netdb.GetNumFloodfills ();
		s[eLeaseSets] = i2p::data::netdb.GetNumLeaseSets ();

		jlong services = 0;
		if (i2p::client::context.GetHttpProxy ()) services |= eServiceHttpProxy;
		if (i2p::client::context.GetSocksProxy ()) services |= eServiceSocksProxy;
		if (i2p::client::context.GetSAMBridge ()) services |= eServiceSam;
		if (i2p::client::context.GetBOBCommandChannel ()) services |= eServiceBob;
		if (i2p::client::context.GetI2CPServer ()) services |= eServiceI2cp;
		bool webConsole = false; i2p::config::GetOption ("http.enabled", webConsole);
		if (webConsole) services |= eServiceWebConsole;
		s[eServices] = services;
		s[eNetId] = i2p::context.GetNetID ();

		env->SetLongArrayRegion (jout, 0, eStatusSize, s);
		return JNI_TRUE;
	}

	void SetAcceptsTransit (JNIEnv *, jclass, jboolean accept)
	{
		if (g_Running) i2p::context.SetAcceptsTunnels (accept);
	}

	void ReloadTunnels (JNIEnv *, jclass)
	{
		if (g_Running) i2p::client::context.ReloadConfig ();
	}

	void SetOnline (JNIEnv *, jclass, jboolean online)
	{
		if (g_Running) i2p::transport::transports.SetOnline (online);
	}

	/** Value of an initialized option as string, null if unknown */
	jstring Option (JNIEnv * env, jclass, jstring jname)
	{
		if (!g_Initialized) return nullptr;
		std::string name = ToString (env, jname);
		auto it = i2p::config::m_Options.find (name);
		if (it == i2p::config::m_Options.end ()) return nullptr;
		const auto& value = it->second.value ();
		std::ostringstream ss;
		if (auto v = boost::any_cast<std::string>(&value)) ss << *v;
		else if (auto v = boost::any_cast<bool>(&value)) ss << (*v ? "true" : "false");
		else if (auto v = boost::any_cast<uint16_t>(&value)) ss << *v;
		else if (auto v = boost::any_cast<uint32_t>(&value)) ss << *v;
		else if (auto v = boost::any_cast<uint64_t>(&value)) ss << *v;
		else if (auto v = boost::any_cast<int>(&value)) ss << *v;
		else if (auto v = boost::any_cast<double>(&value)) ss << *v;
		else return nullptr;
		return ToJString (env, ss.str ());
	}

	void Escape (std::ostringstream& out, const std::string& s)
	{
		for (char c: s)
		{
			switch (c)
			{
				case '\\': out << "\\\\"; break;
				case '\t': out << "\\t"; break;
				case '\n': out << "\\n"; break;
				case '\r': break;
				default: out << c;
			}
		}
	}

	template<typename T>
	bool DescribeType (const boost::program_options::value_semantic * semantic, const char * typeName,
		std::string& type, std::string& defaultValue, bool& hasDefault)
	{
		if (!dynamic_cast<const boost::program_options::typed_value<T> *>(semantic)) return false;
		type = typeName;
		boost::any value;
		hasDefault = semantic->apply_default (value);
		if (hasDefault)
		{
			std::ostringstream ss;
			if constexpr (std::is_same_v<T, bool>)
				ss << (boost::any_cast<bool>(value) ? "true" : "false");
			else
				ss << boost::any_cast<T>(value);
			defaultValue = ss.str ();
		}
		return true;
	}

	/**
	 * All i2pd options, one per line: name, type, has default (0/1), default, description.
	 * Fields are separated by tabs; backslash, tab and newline inside fields are escaped.
	 * Must not be called in a process that runs the daemon before Init: it calls config::Init itself.
	 */
	jstring DescribeOptions (JNIEnv * env, jclass)
	{
		try
		{
			{
				std::lock_guard<std::mutex> l(g_ConfigInitMutex);
				if (!g_ConfigInitCalled)
				{
					g_ConfigInitCalled = true;
					i2p::config::Init ();
				}
			}
			std::ostringstream out;
			// Group captions are not public in boost, sections come from option name prefixes instead
			{
				for (const auto& option: i2p::config::m_OptionsDesc.options ())
				{
					const auto * semantic = option->semantic ().get ();
					std::string type = "string", defaultValue;
					bool hasDefault = false;
					DescribeType<bool>(semantic, "bool", type, defaultValue, hasDefault) ||
					DescribeType<std::string>(semantic, "string", type, defaultValue, hasDefault) ||
					DescribeType<uint16_t>(semantic, "uint16", type, defaultValue, hasDefault) ||
					DescribeType<uint32_t>(semantic, "uint32", type, defaultValue, hasDefault) ||
					DescribeType<uint64_t>(semantic, "uint64", type, defaultValue, hasDefault) ||
					DescribeType<int>(semantic, "int", type, defaultValue, hasDefault) ||
					DescribeType<double>(semantic, "double", type, defaultValue, hasDefault);
					Escape (out, option->long_name ()); out << '\t';
					out << type << '\t' << (hasDefault ? '1' : '0') << '\t';
					Escape (out, defaultValue); out << '\t';
					Escape (out, option->description ()); out << '\n';
				}
			}
			return ToJString (env, out.str ());
		}
		catch (...)
		{
			return nullptr;
		}
	}

	jobjectArray ReadLog (JNIEnv * env, jclass, jlong sinceSeq, jint max, jlongArray joutFirstSeq)
	{
		std::vector<std::string> lines;
		uint64_t firstSeq = g_LogBuffer.Read (sinceSeq < 0 ? 0 : sinceSeq, max < 0 ? 0 : max, lines);
		if (joutFirstSeq && env->GetArrayLength (joutFirstSeq) > 0)
		{
			jlong value = static_cast<jlong>(firstSeq);
			env->SetLongArrayRegion (joutFirstSeq, 0, 1, &value);
		}
		jclass stringClass = env->FindClass ("java/lang/String");
		jobjectArray result = env->NewObjectArray (static_cast<jsize>(lines.size ()), stringClass, nullptr);
		env->DeleteLocalRef (stringClass);
		for (size_t i = 0; i < lines.size (); i++)
		{
			jstring line = ToJString (env, lines[i]);
			env->SetObjectArrayElement (result, static_cast<jsize>(i), line);
			env->DeleteLocalRef (line);
		}
		return result;
	}

	const JNINativeMethod kMethods[] =
	{
		{ "version", "()Ljava/lang/String;", reinterpret_cast<void *>(Version) },
		{ "abi", "()Ljava/lang/String;", reinterpret_cast<void *>(Abi) },
		{ "setFatalHandler", "(Lorg/purplei2p/i2pd/nativelib/FatalHandler;)V", reinterpret_cast<void *>(SetFatalHandler) },
		{ "init", "(Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", reinterpret_cast<void *>(Init) },
		{ "start", "()Ljava/lang/String;", reinterpret_cast<void *>(Start) },
		{ "stop", "()V", reinterpret_cast<void *>(Stop) },
		{ "status", "([J)Z", reinterpret_cast<void *>(Status) },
		{ "setAcceptsTransit", "(Z)V", reinterpret_cast<void *>(SetAcceptsTransit) },
		{ "reloadTunnels", "()V", reinterpret_cast<void *>(ReloadTunnels) },
		{ "setOnline", "(Z)V", reinterpret_cast<void *>(SetOnline) },
		{ "option", "(Ljava/lang/String;)Ljava/lang/String;", reinterpret_cast<void *>(Option) },
		{ "readLog", "(JI[J)[Ljava/lang/String;", reinterpret_cast<void *>(ReadLog) },
		{ "describeOptions", "()Ljava/lang/String;", reinterpret_cast<void *>(DescribeOptions) },
	};
}

JNIEXPORT jint JNI_OnLoad (JavaVM * vm, void *)
{
	g_Vm = vm;
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
