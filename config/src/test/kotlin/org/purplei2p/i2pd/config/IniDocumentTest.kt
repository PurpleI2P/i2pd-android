package org.purplei2p.i2pd.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class IniDocumentTest {

    private fun roundTrip(text: String) = assertEquals(text, IniDocument.parse(text).toString())

    @Test
    fun `real configs are written back unchanged`() {
        for (path in listOf("../app/src/main/assets/i2pd.conf", "../binary/jni/i2pd/contrib/i2pd.conf", "../app/src/main/assets/tunnels.conf")) {
            roundTrip(File(path).readText())
        }
    }

    @Test
    fun `line endings, BOM and missing final newline are kept`() {
        roundTrip("﻿a = 1\r\n[x]\r\nb = 2\r\n")
        roundTrip("a = 1\n# comment\n\n[x]\nb=2")
        roundTrip("")
    }

    @Test
    fun `entries know their sections and comments are not values`() {
        val doc = IniDocument.parse("loglevel = warn # quiet\n#ipv6 = true\n[http]\nport = 7070\n  address=127.0.0.1\n")
        assertEquals("warn", doc.get("", "loglevel"))
        assertNull(doc.get("", "ipv6"))
        assertEquals("7070", doc.get("http", "port"))
        assertEquals("127.0.0.1", doc.get("http", "address"))
        assertEquals(listOf("http"), doc.sections)
    }

    @Test
    fun `changing a value rewrites only that line keeping indent and comment`() {
        val doc = IniDocument.parse("# head\n[http]\n  port = 7070   # console\nenabled = true\n")
        doc.set("http", "port", "7071")
        assertEquals("# head\n[http]\n  port = 7071   # console\nenabled = true\n", doc.toString())
    }

    @Test
    fun `new key goes after the last entry of its section`() {
        val doc = IniDocument.parse("a = 1\n\n[http]\nport = 7070\n\n# proxy\n[httpproxy]\nport = 4444\n")
        doc.set("http", "enabled", "false")
        assertEquals("a = 1\n\n[http]\nport = 7070\nenabled = false\n\n# proxy\n[httpproxy]\nport = 4444\n", doc.toString())
    }

    @Test
    fun `new global key goes after global entries, before sections`() {
        val doc = IniDocument.parse("# comment\n\n[http]\nport = 7070\n")
        doc.set("", "ipv6", "true")
        assertEquals("# comment\n\nipv6 = true\n[http]\nport = 7070\n", doc.toString())

        val withGlobals = IniDocument.parse("ipv4 = true\n\n[http]\nport = 7070\n")
        withGlobals.set("", "ipv6", "true")
        assertEquals("ipv4 = true\nipv6 = true\n\n[http]\nport = 7070\n", withGlobals.toString())
    }

    @Test
    fun `missing section is appended at the end`() {
        val doc = IniDocument.parse("a = 1\n")
        doc.set("sam", "enabled", "true")
        assertEquals("a = 1\n\n[sam]\nenabled = true\n", doc.toString())
    }

    @Test
    fun `remove deletes or comments out entries`() {
        val doc = IniDocument.parse("[http]\nport = 7070 # web\nenabled = true\n")
        assertEquals(1, doc.remove("http", "enabled"))
        assertEquals(1, doc.remove("http", "port", commentOut = true))
        assertEquals("[http]\n# port = 7070 # web\n", doc.toString())
        assertEquals(emptyList<IniDocument.EntryRef>(), doc.entries)
    }

    @Test
    fun `lines that are not entries are kept as they are`() {
        val text = "garbage line\n[broken\nkey with spaces = 1\n=value\n"
        val doc = IniDocument.parse(text)
        assertEquals(text, doc.toString())
        assertEquals(emptyList<IniDocument.EntryRef>(), doc.entries)
    }

    @Test
    fun `sections added to an empty document start at the first line`() {
        val doc = IniDocument.parse("")
        doc.addSection("a", listOf("type" to "server"))
        assertEquals("[a]\ntype = server\n", doc.toString())
    }
}
