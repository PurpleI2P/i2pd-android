/*
* Copyright (c) 2013-2026, The PurpleI2P Project
*
* This file is part of Purple i2pd project and licensed under BSD3
*
* See full license text in LICENSE file at top of project tree
*/

#ifndef I2PD_ANDROID_LOG_BUFFER_H__
#define I2PD_ANDROID_LOG_BUFFER_H__

#include <cstdint>
#include <deque>
#include <mutex>
#include <ostream>
#include <streambuf>
#include <string>
#include <vector>

namespace i2p
{
namespace android
{
	/**
	 * Ring buffer of log lines with monotonically increasing sequence numbers.
	 * The first line ever written has seq 1.
	 */
	class LogBuffer
	{
		public:

			explicit LogBuffer (size_t capacity): m_Capacity (capacity) {}

			void Append (std::string&& line);

			/**
			 * Copies up to max lines with seq > sinceSeq.
			 * @return seq of the first copied line, or the next seq if nothing was copied
			 */
			uint64_t Read (uint64_t sinceSeq, size_t max, std::vector<std::string>& out) const;

		private:

			mutable std::mutex m_Mutex;
			std::deque<std::string> m_Lines;
			uint64_t m_FirstSeq = 1; // seq of m_Lines.front ()
			size_t m_Capacity;
	};

	/** streambuf that splits written text into lines and appends them to LogBuffer */
	class LogLineStreambuf: public std::streambuf
	{
		public:

			explicit LogLineStreambuf (LogBuffer& buffer): m_Buffer (buffer) {}

		protected:

			int_type overflow (int_type ch) override;
			std::streamsize xsputn (const char * s, std::streamsize n) override;

		private:

			void Put (char c);

			LogBuffer& m_Buffer;
			std::string m_Line;
	};

	/** ostream owning its LogLineStreambuf, for Daemon.init (..., logstream) */
	class LogStream: public std::ostream
	{
		public:

			explicit LogStream (LogBuffer& buffer): std::ostream (nullptr), m_Streambuf (buffer) { rdbuf (&m_Streambuf); }

		private:

			LogLineStreambuf m_Streambuf;
	};
}
}

#endif
