package org.purplei2p.i2pd.config

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Parts of the i2pd data directory, see docs/DESIGN.md §10.9. */
enum class DataCategory {
    /** router.keys, router.info and transport keys bound to the router identity. */
    ROUTER_IDENTITY,

    /** Key files of tunnels and proxies (*.dat in the root) and lease set encryption keys (destinations/). */
    DESTINATION_KEYS,
    CONFIG,
    ADDRESSBOOK,
    NETDB,
    PROFILES,
    TAGS,
    CERTIFICATES,
    OTHER,
}

data class CategorySize(val bytes: Long, val files: Int)

/**
 * Categories the running router writes to disk (and saves again on stop): changing them needs the
 * router stopped, otherwise its shutdown would write the old state back. Others are only read by it.
 */
val ROUTER_WRITTEN_CATEGORIES = setOf(
    DataCategory.ROUTER_IDENTITY,
    DataCategory.DESTINATION_KEYS,
    DataCategory.ADDRESSBOOK,
    DataCategory.NETDB,
    DataCategory.PROFILES,
    DataCategory.TAGS,
)

/** Layout of the i2pd data directory: which category a file belongs to. */
object DataLayout {
    private val ROUTER_IDENTITY_FILES = setOf("router.keys", "router.info", "ntcp2.keys", "ssu2.keys")
    private val CONFIG_FILES = setOf("i2pd.conf", "tunnels.conf", "tunnels.conf.disabled")
    private val ADDRESSBOOK_FILES = setOf("hosts.txt", "subscriptions.txt")

    /** Category of [path] relative to the data directory, "/" separated. */
    fun categoryOf(path: String): DataCategory {
        val top = path.substringBefore('/')
        val inRoot = '/' !in path
        return when {
            inRoot && path in ROUTER_IDENTITY_FILES -> DataCategory.ROUTER_IDENTITY
            top == "family" -> DataCategory.ROUTER_IDENTITY
            inRoot && path.endsWith(".dat") -> DataCategory.DESTINATION_KEYS
            top == "destinations" -> DataCategory.DESTINATION_KEYS
            inRoot && path in CONFIG_FILES -> DataCategory.CONFIG
            top == "tunnels.d" -> DataCategory.CONFIG
            top == "addressbook" || (inRoot && path in ADDRESSBOOK_FILES) -> DataCategory.ADDRESSBOOK
            top == "netDb" -> DataCategory.NETDB
            top == "peerProfiles" -> DataCategory.PROFILES
            top == "tags" -> DataCategory.TAGS
            top == "certificates" -> DataCategory.CERTIFICATES
            else -> DataCategory.OTHER
        }
    }

    /** All regular files under [root] as relative "/" separated paths. */
    fun files(root: File): List<String> =
        root.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).invariantSeparatorsPath }.sorted().toList()

    fun sizes(root: File): Map<DataCategory, CategorySize> {
        val result = DataCategory.entries.associateWith { CategorySize(0, 0) }.toMutableMap()
        for (path in files(root)) {
            val category = categoryOf(path)
            val size = File(root, path).length()
            result[category] = result.getValue(category).let { CategorySize(it.bytes + size, it.files + 1) }
        }
        return result
    }

    /** Deletes all files of [categories]; empty directories left behind are removed too. */
    fun clear(root: File, categories: Set<DataCategory>): Int {
        var removed = 0
        for (path in files(root)) {
            if (categoryOf(path) !in categories) continue
            if (File(root, path).delete()) removed++ else throw IOException("Cannot delete $path")
        }
        root.walkBottomUp().filter { it != root && it.isDirectory && it.list().isNullOrEmpty() }
            .filter { categoryOf(it.relativeTo(root).invariantSeparatorsPath + "/x") in categories }
            .forEach { it.delete() }
        return removed
    }

    /**
     * Normalizes a path coming from outside (zip entry, picked folder): "/" separators, no empty,
     * "." or ".." parts, not absolute. Null if the path would leave the data directory.
     */
    fun safePath(path: String): String? {
        val parts = path.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." } || path.startsWith("/") || path.contains(':')) return null
        return parts.joinToString("/")
    }
}

/** One file to import, read lazily. */
class ImportEntry(val path: String, val open: () -> InputStream)

data class ImportResult(val imported: List<String>, val skipped: List<String>)

/** Backup and import of the data directory. */
object DataTransfer {
    const val MAX_FILE_BYTES = 64L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 512L * 1024 * 1024

    /** Writes files of [categories] from [root] as a zip archive with paths relative to [root]. */
    fun exportZip(root: File, categories: Set<DataCategory>, out: OutputStream): List<String> {
        val written = mutableListOf<String>()
        ZipOutputStream(out).use { zip ->
            for (path in DataLayout.files(root)) {
                if (DataLayout.categoryOf(path) !in categories) continue
                zip.putNextEntry(ZipEntry(path))
                File(root, path).inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                written += path
            }
        }
        return written
    }

    /** Entries of a zip archive; directories are skipped. The stream is consumed in order. */
    fun zipEntries(input: InputStream): Sequence<ImportEntry> = sequence {
        val zip = ZipInputStream(input)
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.isDirectory) continue
            yield(ImportEntry(entry.name) { NonClosingInputStream(zip) })
        }
        zip.close()
    }

    /**
     * Copies [entries] of [categories] into [root]: everything goes to [staging] first, then replaces
     * the files in [root], so a broken archive leaves [root] untouched. Paths leaving the data directory,
     * other categories and files over the size limits are skipped.
     */
    fun import(entries: Sequence<ImportEntry>, root: File, categories: Set<DataCategory>, staging: File): ImportResult {
        staging.deleteRecursively()
        staging.mkdirs()
        val imported = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        var total = 0L
        try {
            for (entry in entries) {
                val path = DataLayout.safePath(entry.path)
                if (path == null || DataLayout.categoryOf(path) !in categories) {
                    skipped += entry.path
                    continue
                }
                val target = File(staging, path)
                target.parentFile?.mkdirs()
                val size = entry.open().use { input -> target.outputStream().use { copyLimited(input, it, MAX_FILE_BYTES) } }
                if (size < 0) {
                    target.delete()
                    skipped += entry.path
                    continue
                }
                total += size
                if (total > MAX_TOTAL_BYTES) throw IOException("Import is larger than ${MAX_TOTAL_BYTES / 1024 / 1024} MB")
                imported += path
            }
            for (path in imported) {
                val source = File(staging, path)
                val target = File(root, path)
                target.parentFile?.mkdirs()
                if (target.exists() && !target.delete()) throw IOException("Cannot replace $path")
                if (!source.renameTo(target)) source.copyTo(target, overwrite = true)
                // The imported state of a tunnel file wins: drop its switched off (or switched on) twin
                twinOf(path)?.takeIf { it !in imported }?.let { File(root, it).delete() }
            }
        } finally {
            staging.deleteRecursively()
        }
        return ImportResult(imported, skipped)
    }

    private const val DISABLED = ".disabled"

    /**
     * For a tunnel file (tunnels.conf or tunnels.d/name.conf) the same file in the other state:
     * "x.conf" <-> "x.conf.disabled". Null for other files, where the suffix means nothing.
     */
    fun twinOf(path: String): String? {
        val enabled = path.removeSuffix(DISABLED)
        val isTunnelFile = enabled == "tunnels.conf" ||
            (enabled.startsWith("tunnels.d/") && enabled.count { it == '/' } == 1 && enabled.endsWith(".conf"))
        if (!isTunnelFile) return null
        return if (path.endsWith(DISABLED)) enabled else enabled + DISABLED
    }

    /** Copies at most [limit] bytes; returns the copied size or -1 if the input is longer. */
    private fun copyLimited(input: InputStream, output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return total
            total += read
            if (total > limit) return -1
            output.write(buffer, 0, read)
        }
    }

    /** Lets a zip entry be read with use {} without closing the whole archive. */
    private class NonClosingInputStream(private val input: InputStream) : InputStream() {
        override fun read(): Int = input.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = input.read(b, off, len)
        override fun close() = Unit
    }
}
