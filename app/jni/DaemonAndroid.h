/*
* Copyright (c) 2013-2022, The PurpleI2P Project
*
* This file is part of Purple i2pd project and licensed under BSD3
*
* See full license text in LICENSE file at top of project tree
*/

#ifndef DAEMON_ANDROID_H
#define DAEMON_ANDROID_H

#include <string>

namespace i2p
{
namespace android
{
	class DaemonAndroidImpl
	{
	public:

		DaemonAndroidImpl ();
		~DaemonAndroidImpl ();

		/**
		 * @return success
		 */
		bool init (int argc, char* argv[]);
		void start ();
		void stop ();
		void restart ();

		void setDataDir (std::string path);
	};

	/**
	 * returns "ok" if daemon init failed
	 * returns errinfo if daemon initialized and started okay
	 */
	std::string start ();

	void stop ();

	// set datadir received from jni
	void SetDataDir (std::string jdataDir);
	// get datadir
	std::string GetDataDir (void);
	// set webconsole language
	void SetLanguage (std::string jlanguage);
}
}

#endif // DAEMON_ANDROID_H
