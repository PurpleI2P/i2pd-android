package org.purplei2p.i2pd.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigEditorTest {

    // Fragment of the real describeOptions output format
    private val schema = OptionSchema.parse(
        listOf(
            "loglevel\tstring\t1\twarn\tLevel of logging (debug, info, warn, error, none)",
            "ipv6\tbool\t1\tfalse\tEnable communication through ipv6",
            "port\tuint16\t1\t0\tPort to listen for incoming connections",
            "bandwidth\tstring\t1\t\tTransit traffic bandwidth limit: 'L'-32KBs, 'O'-256KBs, 'P'-2048KBs, 'X'-unlimited",
            "share\tint\t1\t100\tLimit of transit traffic from max bandwidth in percents",
            "ntcp2.port\tuint16\t1\t0\tPort to listen for incoming NTCP2 connections (default: auto)",
            "http.enabled\tbool\t1\ttrue\tEnable or disable webconsole",
            "http.port\tuint16\t1\t7070\tWebconsole listen port",
            "httpproxy.enabled\tbool\t1\ttrue\tEnable or disable HTTP Proxy",
            "httpproxy.port\tuint16\t1\t4444\tHTTP Proxy listen port",
            "sam.enabled\tbool\t1\ttrue\tEnable or disable SAM Application bridge",
            "sam.port\tuint16\t1\t7656\tSAM listen port",
            "limits.transittunnels\tuint32\t1\t10000\tMaximum active transit tunnels",
            "conf\tstring\t1\t\tPath to main i2pd config file\\twith\\\\escapes\\nand lines",
        ).joinToString("\n"),
    )

    private fun editor(text: String) = ConfigEditor(IniDocument.parse(text), schema)

    @Test
    fun `schema parsing handles types, defaults and escapes`() {
        assertEquals(14, schema.options.size)
        assertEquals(OptionType.UINT16, schema["http.port"]!!.type)
        assertEquals("7070", schema["http.port"]!!.default)
        assertEquals("http", schema["http.port"]!!.section)
        assertEquals("port", schema["http.port"]!!.key)
        assertEquals("", schema["loglevel"]!!.section)
        assertEquals("Path to main i2pd config file\twith\\escapes\nand lines", schema["conf"]!!.description)
        assertEquals("", schema["bandwidth"]!!.default)
    }

    @Test
    fun `options are found in sections and as dotted global keys`() {
        val e = editor("http.port = 7071\n[httpproxy]\nport = 4445\n")
        assertEquals("7071", e.valueOf("http.port"))
        assertEquals("4445", e.valueOf("httpproxy.port"))
        assertEquals("true", e.effectiveValue("http.enabled"))
        assertFalse(e.isSet("http.enabled"))
    }

    @Test
    fun `set and reset edit the file in place`() {
        val e = editor("loglevel = warn\n\n[http]\nport = 7070\n")
        e.set("http.port", "7080")
        e.set("http.enabled", "false")
        e.set("ipv6", "true")
        e.set("sam.port", "7657")
        e.reset("loglevel")
        assertEquals("ipv6 = true\n\n[http]\nport = 7080\nenabled = false\n\n[sam]\nport = 7657\n", e.document.toString())
        assertEquals("warn", e.effectiveValue("loglevel"))
    }

    @Test
    fun `unknown options and duplicates are reported`() {
        val e = editor("loglevel = warn\nbogus = 1\n[http]\nport = 7070\nfoo = bar\n[ipv6]\n")
        val issues = e.validate()
        assertTrue(ConfigIssue.UnknownOption("bogus", 1) in issues)
        assertTrue(ConfigIssue.UnknownOption("http.foo", 4) in issues)
        assertEquals(listOf("bogus", "http.foo"), e.unknownEntries().map { if (it.section.isEmpty()) it.key else "${it.section}.${it.key}" })

        val duplicate = editor("http.port = 7070\n[http]\nport = 7071\n").validate()
        assertTrue(ConfigIssue.Duplicate("http.port") in duplicate)
    }

    @Test
    fun `values are checked against types and widgets`() {
        val issues = editor("ipv6 = maybe\nshare = 150\nport = 0\nlimits.transittunnels = -1\n[http]\nport = 0\n").validate()
        assertTrue(ConfigIssue.InvalidValue("ipv6", "maybe", ValueError.NOT_BOOLEAN) in issues)
        assertTrue(ConfigIssue.InvalidValue("share", "150", ValueError.OUT_OF_RANGE) in issues)
        assertTrue(ConfigIssue.InvalidValue("limits.transittunnels", "-1", ValueError.OUT_OF_RANGE) in issues)
        assertTrue("service port 0", ConfigIssue.InvalidValue("http.port", "0", ValueError.OUT_OF_RANGE) in issues)
        assertFalse("router port 0 means auto", issues.any { it is ConfigIssue.InvalidValue && it.name == "port" })

        val routerPorts = editor("port = 4567\n[ntcp2]\nport = 10000\n").validate()
        assertTrue(ConfigIssue.InvalidValue("port", "4567", ValueError.OUTSIDE_RECOMMENDED_RANGE) in routerPorts)
        assertTrue("outside the range is only a warning", routerPorts.all { (it as ConfigIssue.InvalidValue).reason.warning })
        assertTrue(ConfigIssue.InvalidValue("ntcp2.port", "10000", ValueError.RESERVED_PORT) in routerPorts)
        assertEquals(emptyList<ConfigIssue>(), editor("port = 12345\n[ntcp2]\nport = 30776\n").validate())
        assertEquals(
            listOf(ConfigIssue.InvalidValue("port", "70000", ValueError.OUT_OF_RANGE)),
            editor("port = 70000\n").validate(),
        )

        val ok = editor("ipv6 = yes\nbandwidth = 2048\nloglevel = info\n").validate()
        assertEquals(emptyList<ConfigIssue>(), ok)
        val badChoice = editor("loglevel = verbose\n").validate()
        assertEquals(listOf(ConfigIssue.InvalidValue("loglevel", "verbose", ValueError.NOT_ALLOWED)), badChoice)
    }

    @Test
    fun `enabled services on one port conflict`() {
        val conflict = editor("[httpproxy]\nport = 7070\n").validate()
        assertEquals(listOf(ConfigIssue.PortConflict(7070, listOf("http.port", "httpproxy.port"))), conflict)

        val disabled = editor("[httpproxy]\nport = 7070\nenabled = false\n").validate()
        assertTrue(disabled.isEmpty())
    }

    @Test
    fun `overlay hides app managed options and keeps the rest editable`() {
        assertEquals(OptionLevel.HIDDEN, OptionOverlay.uiFor(schema["conf"]!!).level)
        assertEquals(OptionLevel.BASIC, OptionOverlay.uiFor(schema["http.port"]!!).level)
        assertEquals(OptionWidget.Port, OptionOverlay.uiFor(schema["sam.port"]!!).widget)
        assertEquals(OptionWidget.RouterPort, OptionOverlay.uiFor(schema["ntcp2.port"]!!).widget)
        assertEquals(OptionWidget.RouterPort, OptionOverlay.uiFor(schema["port"]!!).widget)
        assertEquals(OptionLevel.ADVANCED, OptionOverlay.uiFor(OptionSpec("reseed.urls", OptionType.STRING, "", "")).level)
        assertNull(OptionOverlay.basicOptions(schema).find { it.name == "ntcp2.enabled" })
    }

    @Test
    fun `changes are compared by effective values`() {
        val started = editor("loglevel = warn\n[http]\nport = 7070\n")
        assertEquals(
            "comments, formatting and written defaults are not changes",
            emptyList<OptionChange>(),
            editor("# edited\nloglevel=warn\nipv6 = no\n[http]\nport = 7070 # console\nenabled = true\n").changesSince(started),
        )
        assertEquals(
            listOf(
                OptionChange("ipv6", "false", "true"),
                OptionChange("port", "0", "12345"),
                OptionChange("http.port", "7070", "7071"),
                OptionChange("bogus", null, "1"),
            ),
            editor("loglevel = warn\nipv6 = true\nport = 12345\nbogus = 1\n[http]\nport = 7071\n").changesSince(started),
        )
    }
}
