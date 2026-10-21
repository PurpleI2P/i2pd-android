package org.purplei2p.i2pd.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DataStorageTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun dataDir(): File = tmp.newFolder("data").apply {
        mapOf(
            "router.keys" to "rk", "router.info" to "ri", "ntcp2.keys" to "n", "ssu2.keys" to "s",
            "proxy-keys.dat" to "pk", "destinations/abc.4.dat" to "d",
            "i2pd.conf" to "conf", "tunnels.conf" to "t", "tunnels.d/a.conf" to "a",
            "addressbook/addresses.csv" to "csv", "netDb/rA/routerInfo-x.dat" to "netdb-entry",
            "peerProfiles/pA/profile-x.txt" to "p", "tags/x.tags" to "tg", "certificates/reseed/a.crt" to "crt",
            "i2pd.conf.bak" to "bak",
        ).forEach { (path, text) -> File(this, path).apply { parentFile.mkdirs() }.writeText(text) }
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    @Test
    fun `files of i2pd are put into categories`() {
        val expected = mapOf(
            "router.keys" to DataCategory.ROUTER_IDENTITY,
            "ssu2.keys" to DataCategory.ROUTER_IDENTITY,
            "family/x.key" to DataCategory.ROUTER_IDENTITY,
            "proxy-keys.dat" to DataCategory.DESTINATION_KEYS,
            "destinations/abc.4.dat" to DataCategory.DESTINATION_KEYS,
            "i2pd.conf" to DataCategory.CONFIG,
            "tunnels.conf.disabled" to DataCategory.CONFIG,
            "tunnels.d/x.conf" to DataCategory.CONFIG,
            "addressbook/addresses.csv" to DataCategory.ADDRESSBOOK,
            "hosts.txt" to DataCategory.ADDRESSBOOK,
            "netDb/rA/routerInfo-x.dat" to DataCategory.NETDB,
            "peerProfiles/pA/profile-x.txt" to DataCategory.PROFILES,
            "tags/x.tags" to DataCategory.TAGS,
            "certificates/reseed/a.crt" to DataCategory.CERTIFICATES,
            "i2pd.conf.bak" to DataCategory.OTHER,
        )
        expected.forEach { (path, category) -> assertEquals(path, category, DataLayout.categoryOf(path)) }
    }

    @Test
    fun `sizes and clearing work per category`() {
        val root = dataDir()
        val sizes = DataLayout.sizes(root)
        assertEquals(CategorySize(11, 1), sizes[DataCategory.NETDB])
        assertEquals(4, sizes.getValue(DataCategory.ROUTER_IDENTITY).files)

        assertEquals(2, DataLayout.clear(root, setOf(DataCategory.NETDB, DataCategory.PROFILES)))
        assertFalse(File(root, "netDb").exists())
        assertFalse(File(root, "peerProfiles").exists())
        assertTrue(File(root, "router.keys").exists())
    }

    @Test
    fun `unsafe paths are rejected`() {
        assertEquals("tunnels.d/a.conf", DataLayout.safePath("./tunnels.d//a.conf"))
        assertEquals("i2pd/x", DataLayout.safePath("i2pd\\x"))
        listOf("../x", "a/../../x", "/etc/passwd", "C:/x", "", "/").forEach { assertNull(it, DataLayout.safePath(it)) }
    }

    @Test
    fun `export and import restore selected categories`() {
        val source = dataDir()
        val bytes = ByteArrayOutputStream()
        val written = DataTransfer.exportZip(source, setOf(DataCategory.ROUTER_IDENTITY, DataCategory.CONFIG), bytes)
        assertEquals(listOf("i2pd.conf", "ntcp2.keys", "router.info", "router.keys", "ssu2.keys", "tunnels.conf", "tunnels.d/a.conf"), written)

        val target = tmp.newFolder("target")
        File(target, "router.keys").writeText("old")
        val result = DataTransfer.import(
            DataTransfer.zipEntries(ByteArrayInputStream(bytes.toByteArray())),
            target,
            setOf(DataCategory.ROUTER_IDENTITY),
            File(tmp.root, "staging"),
        )
        assertEquals(listOf("ntcp2.keys", "router.info", "router.keys", "ssu2.keys"), result.imported.sorted())
        assertEquals(listOf("i2pd.conf", "tunnels.conf", "tunnels.d/a.conf"), result.skipped.sorted())
        assertEquals("rk", File(target, "router.keys").readText())
        assertFalse(File(target, "i2pd.conf").exists())
        assertFalse(File(tmp.root, "staging").exists())
    }

    @Test
    fun `archive entries leaving the data directory are skipped`() {
        val target = tmp.newFolder("target")
        val archive = zip("../evil.dat" to "x".toByteArray(), "/abs.dat" to "x".toByteArray(), "ok.dat" to "ok".toByteArray())
        val result = DataTransfer.import(DataTransfer.zipEntries(ByteArrayInputStream(archive)), target, DataCategory.entries.toSet(), File(tmp.root, "s"))
        assertEquals(listOf("ok.dat"), result.imported)
        assertEquals(listOf("../evil.dat", "/abs.dat"), result.skipped)
        assertFalse(File(tmp.root, "evil.dat").exists())
    }

    @Test
    fun `broken archive leaves the data directory untouched`() {
        val target = tmp.newFolder("target")
        File(target, "router.keys").writeText("current")
        val archive = zip("router.keys" to "new".toByteArray()).copyOf(40) // cut in the middle
        try {
            DataTransfer.import(DataTransfer.zipEntries(ByteArrayInputStream(archive)), target, DataCategory.entries.toSet(), File(tmp.root, "s"))
        } catch (e: IOException) {
            // expected for a truncated archive
        }
        assertEquals("current", File(target, "router.keys").readText())
    }

    @Test
    fun `files over the size limit are skipped`() {
        val target = tmp.newFolder("target")
        val big = object : java.io.InputStream() {
            var left = DataTransfer.MAX_FILE_BYTES + 1
            override fun read(): Int = if (left-- > 0) 0 else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (left <= 0) return -1
                val n = minOf(len.toLong(), left).toInt()
                left -= n
                return n
            }
        }
        val result = DataTransfer.import(sequenceOf(ImportEntry("huge.dat") { big }), target, DataCategory.entries.toSet(), File(tmp.root, "s"))
        assertEquals(listOf("huge.dat"), result.skipped)
        assertFalse(File(target, "huge.dat").exists())
    }

    @Test
    fun `imported tunnel file replaces its twin in the other state`() {
        val target = tmp.newFolder("target")
        listOf("tunnels.conf.disabled", "tunnels.d/a.conf.disabled", "tunnels.d/b.conf", "i2pd.conf.disabled").forEach {
            File(target, it).apply { parentFile.mkdirs() }.writeText("local")
        }
        val archive = zip(
            "tunnels.conf" to "old".toByteArray(),
            "tunnels.d/a.conf" to "old".toByteArray(),
            "tunnels.d/b.conf.disabled" to "old".toByteArray(),
            "i2pd.conf" to "old".toByteArray(),
        )
        DataTransfer.import(DataTransfer.zipEntries(ByteArrayInputStream(archive)), target, DataCategory.entries.toSet(), File(tmp.root, "s"))

        assertEquals(
            listOf("i2pd.conf", "i2pd.conf.disabled", "tunnels.conf", "tunnels.d/a.conf", "tunnels.d/b.conf.disabled"),
            DataLayout.files(target),
        )
        assertTrue(DataLayout.files(target).filter { it != "i2pd.conf.disabled" }.all { File(target, it).readText() == "old" })
    }

    @Test
    fun `only tunnel files have twins`() {
        assertEquals("tunnels.conf.disabled", DataTransfer.twinOf("tunnels.conf"))
        assertEquals("tunnels.d/x.conf", DataTransfer.twinOf("tunnels.d/x.conf.disabled"))
        assertNull(DataTransfer.twinOf("i2pd.conf"))
        assertNull(DataTransfer.twinOf("tunnels.d/README"))
        assertNull(DataTransfer.twinOf("tunnels.d/sub/x.conf"))
    }
}
