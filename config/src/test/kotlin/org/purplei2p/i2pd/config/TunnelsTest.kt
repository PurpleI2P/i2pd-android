package org.purplei2p.i2pd.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TunnelsTest {

    private val assets = "../app/src/main/assets"
    private fun asset(path: String) = TunnelFile.parse(path, File("$assets/$path").readText())

    @Test
    fun `bundled tunnel files are written back unchanged`() {
        for (path in listOf("tunnels.conf", "tunnels.d/Telegram.conf", "tunnels.d/IRC-Ilita.conf", "tunnels.d/IRC-Irc2P.conf")) {
            val text = File("$assets/$path").readText()
            assertEquals(path, text, TunnelFile.parse(path, text).document.toString())
        }
    }

    @Test
    fun `hash inside a line is part of the value in tunnel files`() {
        val file = TunnelFile.parse("tunnels.d/a.conf", "[a]\ntype = client\ndestination = x.i2p # not a comment\n; comment\n")
        assertEquals("x.i2p # not a comment", file["a"]!!.values["destination"])
        file.set("a", "port", "8080")
        assertEquals("[a]\ntype = client\ndestination = x.i2p # not a comment\nport = 8080\n; comment\n", file.document.toString())
    }

    @Test
    fun `tunnels with the same keys in one file follow the first one`() {
        val telegram = asset("tunnels.d/Telegram.conf")
        val tunnels = telegram.tunnels
        assertTrue(tunnels.size >= 5)
        assertTrue(tunnels.all { it.type == TunnelType.CLIENT && it.keys == "proxy-keys.dat" })
        assertNull(tunnels.first().sharesDestinationWith)
        assertEquals(tunnels.drop(1).map { it.name }, tunnels.first().dependents)
        assertTrue(tunnels.drop(1).all { it.sharesDestinationWith == tunnels.first().name })
        assertEquals(emptyList<TunnelIssue>(), telegram.validate())
    }

    @Test
    fun `destinations are shared across files and with proxies of the main config`() {
        val a = TunnelFile.parse("tunnels.d/b.conf", "[b1]\ntype = client\ndestination = x.i2p\nport = 1\nkeys = shared.dat\n")
        val main = TunnelFile.parse("tunnels.conf", "[m]\ntype = server\nhost = 127.0.0.1\nport = 80\nkeys = shared.dat\n[t]\ntype = client\ndestination = y.i2p\nport = 2\n")
        val off = TunnelFile.parse("tunnels.d/off.conf.disabled", "[o]\ntype = client\ndestination = z.i2p\nport = 3\nkeys = shared.dat\n")
        val groups = SharedDestinations.of(listOf(a, main, off), mapOf("httpproxy" to "shared.dat", "socksproxy" to "transient"))
        assertEquals(
            mapOf(
                "shared.dat" to listOf(
                    DestinationUser(null, "httpproxy"),
                    DestinationUser("tunnels.conf", "m"),
                    DestinationUser("tunnels.d/b.conf", "b1"),
                ),
            ),
            groups,
        )
    }

    @Test
    fun `disabled files are recognized by suffix`() {
        assertTrue(TunnelFile.parse("tunnels.d/a.conf", "").enabled)
        assertFalse(TunnelFile.parse("tunnels.d/a.conf.disabled", "").enabled)
    }

    @Test
    fun `problems i2pd would report are found`() {
        val file = TunnelFile.parse(
            "tunnels.d/bad.conf",
            listOf(
                "stray = 1",
                "[noType]",
                "port = 1",
                "[weird]",
                "type = magic",
                "[client]",
                "type = client",
                "port = 70000",
                "port = 2",
                "inbound.length = 9",
                "not a key value line",
                "[server]",
                "type = server",
                "host = 127.0.0.1",
                "[client]",
                "type = client",
            ).joinToString("\n"),
        )
        val issues = file.validate()
        val path = "tunnels.d/bad.conf"
        listOf(
            TunnelIssue.EntryOutsideSection(path, "stray"),
            TunnelIssue.MissingType(path, "noType"),
            TunnelIssue.UnknownType(path, "weird", "magic"),
            TunnelIssue.MissingParam(path, "client", "destination"),
            TunnelIssue.InvalidValue(path, "client", "port", "70000", ValueError.OUT_OF_RANGE),
            TunnelIssue.DuplicateKey(path, "client", "port"),
            TunnelIssue.InvalidValue(path, "client", "inbound.length", "9", ValueError.OUT_OF_RANGE),
            TunnelIssue.InvalidLine(path, 10, "not a key value line"),
            TunnelIssue.MissingParam(path, "server", "port"),
            TunnelIssue.MissingParam(path, "server", "keys"),
            TunnelIssue.DuplicateSection(path, "client"),
        ).forEach { assertTrue("missing $it in $issues", it in issues) }
    }

    @Test
    fun `local ports of enabled client tunnels must not collide`() {
        val a = TunnelFile.parse("tunnels.d/a.conf", "[a]\ntype = client\ndestination = x.i2p\nport = 4444\n[b]\ntype = socks\nport = 5000\n")
        val c = TunnelFile.parse("tunnels.d/c.conf", "[c]\ntype = client\ndestination = y.i2p\nport = 5000\n")
        val off = TunnelFile.parse("tunnels.d/off.conf.disabled", "[d]\ntype = client\ndestination = z.i2p\nport = 5000\n")
        val conflicts = TunnelFile.portConflicts(listOf(a, c, off), mapOf("httpproxy" to 4444))
        assertEquals(
            setOf(
                TunnelIssue.PortConflict("tunnels.d/a.conf", "a", 4444, listOf("httpproxy")),
                TunnelIssue.PortConflict("tunnels.d/a.conf", "b", 5000, listOf("c")),
                TunnelIssue.PortConflict("tunnels.d/c.conf", "c", 5000, listOf("b")),
            ),
            conflicts.toSet(),
        )
    }

    @Test
    fun `torrents RPC port counts as a local port, a switched off RPC does not`() {
        val t = TunnelFile.parse(
            "tunnels.d/t.conf",
            "[t]\ntype = torrents\ntorrentsdir = /d\nrpcport = 7070\n[quiet]\ntype = torrents\ntorrentsdir = /e\nrpcport = 0\n",
        )
        assertEquals(
            listOf(TunnelIssue.PortConflict("tunnels.d/t.conf", "t", 7070, listOf("http"))),
            TunnelFile.portConflicts(listOf(t), mapOf("http" to 7070, "socksproxy" to 0)),
        )
        assertTrue(TunnelType.TORRENTS.creatable)
    }

    @Test
    fun `commented sections are found up to a blank line or a real section`() {
        val file = TunnelFile.parse(
            "tunnels.d/irc.conf",
            "[a]\ntype = client\nport = 1\n\n# IRC network\n#[irc]\n#type = client\n# the address\n;port = 6668\n\n#[empty]\n# no entries\n\n[b]\ntype = socks\n",
        )
        assertEquals(listOf("a", "b"), file.tunnels.map { it.name })
        val irc = file.commentedTunnels.single()
        assertEquals("irc", irc.name)
        assertEquals(mapOf("type" to "client", "port" to "6668"), irc.values)
        assertEquals(TunnelType.CLIENT, irc.type)
        assertEquals(5, irc.line)
        assertEquals(setOf("a", "b", "irc"), file.allNames)
    }

    @Test
    fun `switching a tunnel off and on gives the same text`() {
        val text = "[first]\ntype = client\nkeys = shared.dat\n# keep 2 hops\n\ninbound.length = 2\n\n[second]\ntype = client\nkeys = shared.dat\n"
        val file = TunnelFile.parse("tunnels.d/t.conf", text)
        assertTrue(file.commentOut("first"))
        assertEquals(
            "#[first]\n#type = client\n#keys = shared.dat\n## keep 2 hops\n#\n#inbound.length = 2\n\n[second]\ntype = client\nkeys = shared.dat\n",
            file.document.toString(),
        )
        assertEquals(listOf("second"), file.tunnels.map { it.name })
        assertEquals(listOf("first"), file.commentedTunnels.map { it.name })
        assertEquals("2", file.commentedTunnels.single().values["inbound.length"])

        assertTrue(file.uncomment("first"))
        assertEquals(text, file.document.toString())
        assertEquals(listOf("first", "second"), file.tunnels.map { it.name })
    }

    @Test
    fun `every tunnel of the bundled Telegram file switches off and on without changes`() {
        val asset = java.io.File("../app/src/main/assets/tunnels.d/Telegram.conf")
        org.junit.Assume.assumeTrue(asset.isFile)
        val text = asset.readText()
        val names = TunnelFile.parse("tunnels.d/Telegram.conf", text).tunnels.map { it.name }
        assertTrue(names.size > 1)
        for (name in names) {
            val file = TunnelFile.parse("tunnels.d/Telegram.conf", text)
            assertTrue(file.commentOut(name))
            assertEquals(names - name, file.tunnels.map { it.name })
            assertEquals(listOf(name), file.commentedTunnels.map { it.name })
            assertTrue(file.uncomment(name))
            assertEquals(name, text, file.document.toString())
        }
    }

    @Test
    fun `links with "=" in comments are not taken for entries`() {
        val file = TunnelFile.parse("tunnels.conf", "#[tg]\n#type = client\n#port = 3447\n# https://t.me/proxy?server=127.0.0.1&port=3447\n")
        assertEquals(mapOf("type" to "client", "port" to "3447"), file.commentedTunnels.single().values)
        assertTrue(file.uncomment("tg"))
        assertEquals("[tg]\ntype = client\nport = 3447\n# https://t.me/proxy?server=127.0.0.1&port=3447\n", file.document.toString())
    }

    @Test
    fun `plain text comments stay comments when a hand written block is switched on`() {
        val file = TunnelFile.parse("tunnels.conf", "#[irc]\n# IRC client\n#type = client\n#  port = 6668\n")
        assertTrue(file.uncomment("irc"))
        assertEquals("[irc]\n# IRC client\ntype = client\nport = 6668\n", file.document.toString())
        assertEquals(emptyList<Int>(), file.document.invalidLines())
    }

    @Test
    fun `sections can be added, renamed and removed`() {
        val file = TunnelFile.parse("tunnels.conf", "[a]\ntype = client\n# about a\n\n[b]\ntype = server\n")
        file.document.renameSection("a", "first")
        file.document.addSection("c", listOf("type" to "socks", "port" to "1080"))
        assertTrue(file.document.removeSection("b"))
        assertEquals("[first]\ntype = client\n# about a\n\n[c]\ntype = socks\nport = 1080\n", file.document.toString())
        assertTrue(file.document.removeSection("c"))
        assertEquals("[first]\ntype = client\n# about a\n", file.document.toString())
    }

    @Test
    fun `b32 address comes from the identity part of a key file`() {
        // 256 + 128 bytes of keys, key certificate (type 5, length 4) and some private key bytes after the identity
        val identity = ByteArray(256) { it.toByte() } + ByteArray(128) + byteArrayOf(5, 0, 4, 0, 7, 0, 4)
        val keys = identity + ByteArray(64)
        assertEquals("jy5i5aczuwihdid6ykrxz4q22zjevxuccsszl4zc2kqcpkoamsqq.b32.i2p", I2pAddress.b32FromKeys(keys))
        assertNull(I2pAddress.b32FromKeys(ByteArray(100)))
    }

    /** Identities generated by i2pd 3.0.0 for signature types 0..11, addresses as shown by the router. */
    private val identities: List<Triple<Int, ByteArray, String>> =
        javaClass.getResourceAsStream("/identities.txt")!!.bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val (type, hex, address) = line.split(" ")
                Triple(type.toInt(), hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray(), address)
            }

    @Test
    fun `b32 matches the router for every signature type`() {
        assertEquals(12, identities.size)
        for ((type, identity, address) in identities) {
            // Private key bytes follow the identity in a key file; they do not affect the address
            assertEquals("signature type $type", address, I2pAddress.b32FromKeys(identity + ByteArray(100)))
        }
    }

    @Test
    fun `b33 of encrypted lease sets matches the router`() {
        val expected = mapOf(
            7 to "r23r6ljrfjwffcowoq25uaiqlb5uwdjrznprx7okxndeftgdbxoyuypi.b32.i2p",
            11 to "a2zwhgvbkgqkvegbq4gz3nrvb6fyyvwrwshjqm6jeex6av53fbhqmeuv.b32.i2p",
        )
        for ((type, identity, _) in identities) {
            assertEquals("signature type $type", expected[type], I2pAddress.b33FromKeys(identity))
        }
    }

    @Test
    fun `signature types i2pd cannot generate are reported`() {
        val file = TunnelFile.parse("tunnels.d/s.conf", "[a]\ntype = server\nhost = h\nport = 1\nkeys = a.dat\nsignaturetype = 4\n[b]\ntype = server\nhost = h\nport = 2\nkeys = b.dat\nsignaturetype = 11\n")
        assertEquals(listOf(TunnelIssue.InvalidValue("tunnels.d/s.conf", "a", "signaturetype", "4", ValueError.NOT_ALLOWED)), file.validate())
    }
}
