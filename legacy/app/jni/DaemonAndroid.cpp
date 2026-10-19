/*
* Copyright (c) 2013-2022, The PurpleI2P Project
*
* This file is part of Purple i2pd project and licensed under BSD3
*
* See full license text in LICENSE file at top of project tree
*/

#include <iostream>
#include <chrono>
#include <thread>
#include <exception>
#include <boost/exception/diagnostic_information.hpp>
#include <boost/exception_ptr.hpp>
//#include "mainwindow.h"
#include "FS.h"
#include "DaemonAndroid.h"
#include "Daemon.h"
#include "I18N.h"

namespace i2p
{
namespace android
{
	std::string dataDir = "";
	std::string language = "";

	DaemonAndroidImpl::DaemonAndroidImpl ()
	{
	}

	DaemonAndroidImpl::~DaemonAndroidImpl ()
	{
	}

	bool DaemonAndroidImpl::init(int argc, char* argv[])
	{
		return Daemon.init(argc, argv);
	}

	void DaemonAndroidImpl::start()
	{
		Daemon.start();
	}

	void DaemonAndroidImpl::stop()
	{
		Daemon.stop();
	}

	void DaemonAndroidImpl::restart()
	{
		stop();
		start();
	}

	void DaemonAndroidImpl::setDataDir(std::string path)
	{
		Daemon.setDataDir(path);
	}

	static DaemonAndroidImpl daemon;
	static char* argv[1]={strdup("tmp")};
	/**
	 * returns error details if failed
	 * returns "ok" if daemon initialized and started okay
	 */
	std::string start(/*int argc, char* argv[]*/)
	{
		try
		{
			{
				// make sure assets are ready before proceed
				i2p::fs::DetectDataDir(dataDir, false);
				int numAttempts = 0;
				do
				{
					if (i2p::fs::Exists (i2p::fs::DataDirPath("assets.ready"))) break; // assets ready
					numAttempts++;
					std::this_thread::sleep_for (std::chrono::seconds(1)); // otherwise wait for 1 more second
				}
				while (numAttempts <= 10); // 10 seconds max

				// Set application directory
				daemon.setDataDir(dataDir);

				bool daemonInitSuccess = daemon.init(1, argv);
				if(!daemonInitSuccess)
				{
					return "Daemon init failed";
				}

				// Set webconsole language from application
				i2p::i18n::SetLanguage(language);

				daemon.start();
			}
		}
		catch (boost::exception& ex)
		{
			std::stringstream ss;
			ss << boost::diagnostic_information(ex);
			return ss.str();
		}
		catch (std::exception& ex)
		{
			std::stringstream ss;
			ss << ex.what();
			return ss.str();
		}
		catch(...)
		{
			return "unknown exception";
		}
		return "ok";
	}

	void stop()
	{
		daemon.stop();
	}

	void SetDataDir(std::string jdataDir)
	{
		dataDir = jdataDir;
	}

	std::string GetDataDir(void)
	{
		return dataDir;
	}

	void SetLanguage(std::string jlanguage)
	{
		language = jlanguage;
	}
}
}
