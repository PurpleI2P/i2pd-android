/*
* Copyright (c) 2013-2026, The PurpleI2P Project
*
* This file is part of Purple i2pd project and licensed under BSD3
*
* See full license text in LICENSE file at top of project tree
*/

#include "log_buffer.h"

namespace i2p
{
namespace android
{
	void LogBuffer::Append (std::string&& line)
	{
		std::lock_guard<std::mutex> l(m_Mutex);
		m_Lines.push_back (std::move (line));
		while (m_Lines.size () > m_Capacity)
		{
			m_Lines.pop_front ();
			m_FirstSeq++;
		}
	}

	uint64_t LogBuffer::Read (uint64_t sinceSeq, size_t max, std::vector<std::string>& out) const
	{
		std::lock_guard<std::mutex> l(m_Mutex);
		uint64_t nextSeq = m_FirstSeq + m_Lines.size ();
		uint64_t from = sinceSeq + 1 > m_FirstSeq ? sinceSeq + 1 : m_FirstSeq; // older lines were dropped
		if (from >= nextSeq) return nextSeq;
		size_t index = from - m_FirstSeq;
		for (size_t i = index; i < m_Lines.size () && out.size () < max; i++)
			out.push_back (m_Lines[i]);
		return from;
	}

	void LogLineStreambuf::Put (char c)
	{
		if (c == '\n')
		{
			m_Buffer.Append (std::move (m_Line));
			m_Line.clear ();
		}
		else
			m_Line.push_back (c);
	}

	LogLineStreambuf::int_type LogLineStreambuf::overflow (int_type ch)
	{
		if (!traits_type::eq_int_type (ch, traits_type::eof ()))
			Put (traits_type::to_char_type (ch));
		return traits_type::not_eof (ch);
	}

	std::streamsize LogLineStreambuf::xsputn (const char * s, std::streamsize n)
	{
		for (std::streamsize i = 0; i < n; i++)
			Put (s[i]);
		return n;
	}
}
}
