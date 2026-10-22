package org.purplei2p.i2pd.config

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Compares TunnelParams with the parameters ClientContext::ReadTunnels and ReadI2CPOptions read in the
 * i2pd sources of the submodule: a parameter added to or removed from i2pd fails this test until the
 * tunnel forms follow. Skipped when the submodule is not checked out.
 */
class TunnelSourcesTest {

    private val i2pd = File("../binary/jni/i2pd")

    /** `const char NAME[] = "value";` of the given headers. */
    private fun constants(vararg headers: String): Map<String, String> {
        val pattern = Regex("""const\s+char\s+(\w+)\s*\[\]\s*=\s*"([^"]*)"""")
        return headers.flatMap { pattern.findAll(File(i2pd, it).readText()).map { m -> m.groupValues[1] to m.groupValues[2] }.toList() }.toMap()
    }

    /** Body of the function whose definition starts with [signature], up to the next function of ClientContext. */
    private fun body(source: String, signature: String): String {
        val start = source.indexOf(signature).also { check(it >= 0) { "$signature not found" } }
        val next = Regex("""\n\t(void|bool|static|template|std::)[^\n]*ClientContext::|\n\tstatic size_t""").find(source, start + signature.length)
        return source.substring(start, next?.range?.first ?: source.length)
    }

    /** Keys read by name or constant in [code], resolved through [names]. */
    private fun keys(code: String, readers: List<Regex>, names: Map<String, String>): Set<String> =
        readers.flatMap { reader ->
            reader.findAll(code).map { m ->
                val token = m.groupValues[1]
                if (token.startsWith("\"")) token.trim('"') else names[token] ?: error("Unknown constant $token")
            }.toList()
        }.toSet()

    @Test
    fun `tunnel parameters match the i2pd sources`() {
        assumeTrue("i2pd submodule is not checked out", File(i2pd, "libi2pd_client/ClientContext.cpp").isFile)
        val names = constants("libi2pd_client/ClientContext.h", "libi2pd/Destination.h")
        val source = File(i2pd, "libi2pd_client/ClientContext.cpp").readText()

        // section.second.get<T> (KEY, ...), section.second.get (path_type (KEY, '/'), ...), section.get<T> (KEY, ...)
        val sectionReaders = listOf(
            Regex("""section(?:\.second)?\.get\s*(?:<[^>]+>)?\s*\(\s*(?:boost::property_tree::ptree::path_type\s*\(\s*)?(\w+|"[^"]+")"""),
        )
        val tunnels = keys(body(source, "void ClientContext::ReadTunnels (const std::string& tunConf"), sectionReaders, names) +
            keys(body(source, "static size_t GetUDPTunnelMaxWindow"), sectionReaders, names)

        val i2cpReaders = listOf(
            Regex("""GetI2CP(?:String)?Option\s*\(\s*section\s*,\s*(\w+)"""),
            Regex("""ReadI2CPOptionsGroup\s*\(\s*section\s*,\s*(\w+)"""),
        )
        val i2cp = keys(body(source, "void ClientContext::ReadI2CPOptions (const Section& section"), i2cpReaders, names)

        // "type" selects the branch and is not a parameter of the form
        val read = (tunnels + i2cp) - "type"
        assertEquals("Read by i2pd, unknown to the app", emptySet<String>(), read - TunnelParams.allKeys)
        assertEquals("Known to the app, not read by i2pd", emptySet<String>(), TunnelParams.allKeys - "type" - read)
    }
}
