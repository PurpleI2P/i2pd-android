package org.purplei2p.i2pd.service

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.purplei2p.i2pd.config.CategorySize
import org.purplei2p.i2pd.config.DataCategory
import org.purplei2p.i2pd.config.DataLayout
import org.purplei2p.i2pd.config.DataTransfer
import org.purplei2p.i2pd.config.ImportEntry
import org.purplei2p.i2pd.config.ImportResult
import org.purplei2p.i2pd.config.ROUTER_WRITTEN_CATEGORIES
import java.io.File
import java.io.IOException

/** A file or directory of the data directory for the file browser. */
data class DataEntry(val path: String, val name: String, val isDirectory: Boolean, val size: Long)

/**
 * Storage management of the private data directory (docs/DESIGN.md §10.9). Changes run while the router
 * is stopped (it is started again afterwards if it was running); reading and export work at any time.
 */
class RouterData private constructor(private val context: Context) {

    private val preparer = DataDirPreparer(context)
    val root: File get() = preparer.dataDir

    suspend fun sizes(): Map<DataCategory, CategorySize> = withContext(Dispatchers.IO) { DataLayout.sizes(root) }

    /** Files and directories directly in [dir] ("" for the root). */
    suspend fun list(dir: String): List<DataEntry> = withContext(Dispatchers.IO) {
        val base = resolve(dir)
        // Relative to the canonical root: filesDir may be reached through a symlink (/data/user/0 -> /data/data)
        val canonicalRoot = root.canonicalFile
        base.listFiles().orEmpty().map {
            val path = it.relativeTo(canonicalRoot).invariantSeparatorsPath
            DataEntry(path, it.name, it.isDirectory, if (it.isDirectory) it.walkTopDown().filter(File::isFile).sumOf(File::length) else it.length())
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    suspend fun clear(categories: Set<DataCategory>) = change(categories) {
        DataLayout.clear(root, categories)
    }

    /**
     * Puts bundled configuration or certificates back: the files of [category] are deleted and copied
     * from the APK again. Other categories cannot be reset.
     */
    suspend fun resetToBundled(category: DataCategory) = change(setOf(category)) {
        DataLayout.clear(root, setOf(category))
        when (category) {
            DataCategory.CONFIG -> preparer.restoreConfiguration()
            DataCategory.CERTIFICATES -> preparer.replaceCertificates()
            else -> throw IllegalArgumentException("$category cannot be reset")
        }
    }

    suspend fun exportZip(target: Uri, categories: Set<DataCategory>): Int = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(target, "wt") ?: throw IOException("Cannot write $target")
        out.use { DataTransfer.exportZip(root, categories, it).size }
    }

    suspend fun importZip(source: Uri, categories: Set<DataCategory>): ImportResult = change(categories) {
        val input = context.contentResolver.openInputStream(source) ?: throw IOException("Cannot read $source")
        input.use { DataTransfer.import(DataTransfer.zipEntries(it), root, categories, staging()) }
    }

    /** Copies data of the old app version from a folder picked by the user (usually /sdcard/i2pd). */
    suspend fun migrate(tree: Uri, categories: Set<DataCategory>): ImportResult = change(categories) {
        val folder = DocumentFile.fromTreeUri(context, tree) ?: throw IOException("Cannot open $tree")
        DataTransfer.import(documentEntries(folder, ""), root, categories, staging())
    }

    /** Copies a file picked by the user into [dir] of the data directory under its own name. */
    suspend fun importFile(source: Uri, dir: String): String {
        val name = displayName(source) ?: throw IOException("No file name for $source")
        val path = DataLayout.safePath(if (dir.isEmpty()) name else "$dir/$name") ?: throw IOException("Bad file name $name")
        return change(setOf(DataLayout.categoryOf(path))) { copyIn(source, path, name) }
    }

    private fun copyIn(source: Uri, path: String, name: String): String {
        val input = context.contentResolver.openInputStream(source) ?: throw IOException("Cannot read $source")
        val result = input.use {
            DataTransfer.import(sequenceOf(ImportEntry(path) { it }), root, DataCategory.entries.toSet(), staging())
        }
        if (result.imported.isEmpty()) throw IOException("$name was not imported (too large?)")
        return path
    }

    suspend fun exportFile(path: String, target: Uri) = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(target, "wt") ?: throw IOException("Cannot write $target")
        out.use { output -> resolve(path).inputStream().use { it.copyTo(output) } }
    }

    suspend fun delete(path: String) {
        val file = resolve(path)
        if (file == root.canonicalFile) throw IOException("Cannot delete the data directory")
        val canonicalRoot = root.canonicalFile
        val categories = file.walkTopDown().filter { it.isFile }.map { DataLayout.categoryOf(it.relativeTo(canonicalRoot).invariantSeparatorsPath) }.toSet()
        change(categories) { if (!file.deleteRecursively()) throw IOException("Cannot delete $path") }
    }

    /**
     * Runs [block] on IO; with the router stopped only if [categories] include files the router writes
     * (see ROUTER_WRITTEN_CATEGORIES). Afterwards screens reread the files: changed configuration shows
     * its restart or apply banner.
     */
    private suspend fun <T> change(categories: Set<DataCategory>, block: () -> T): T {
        val result: T = if (categories.any { it in ROUTER_WRITTEN_CATEGORIES }) {
            var outcome: Result<T>? = null
            RouterController.runWithRouterStopped(context) { outcome = runCatching(block) }.await().getOrThrow()
            outcome!!.getOrThrow()
        } else {
            withContext(Dispatchers.IO) { block() }
        }
        RouterController.config(context).load()
        RouterController.tunnels(context).refresh()
        return result
    }

    /**
     * Next to the data directory, not in the cache: renamed files keep their group, and files from the
     * cache directory would be counted as cache of the app.
     */
    private fun staging() = File(root.parentFile, ".import")

    private fun resolve(path: String): File {
        val base = root.canonicalFile
        if (path.isEmpty()) return base
        val safe = DataLayout.safePath(path) ?: throw IOException("Bad path $path")
        return File(base, safe).canonicalFile.also {
            if (!it.path.startsWith(base.path + File.separator)) throw IOException("Bad path $path")
        }
    }

    private fun documentEntries(dir: DocumentFile, prefix: String): Sequence<ImportEntry> = sequence {
        for (child in dir.listFiles()) {
            val name = child.name ?: continue
            val path = if (prefix.isEmpty()) name else "$prefix/$name"
            if (child.isDirectory) {
                yieldAll(documentEntries(child, path))
            } else if (child.isFile) {
                yield(ImportEntry(path) { context.contentResolver.openInputStream(child.uri) ?: throw IOException("Cannot read $path") })
            }
        }
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/')

    companion object {
        /** What a migration from the old app takes by default: identity, keys, configuration and address book. */
        val MIGRATION_DEFAULT = setOf(DataCategory.ROUTER_IDENTITY, DataCategory.DESTINATION_KEYS, DataCategory.CONFIG, DataCategory.ADDRESSBOOK)

        /** What a backup holds by default: everything that cannot be downloaded from the network again. */
        val BACKUP_DEFAULT = MIGRATION_DEFAULT

        @SuppressLint("StaticFieldLeak") // holds the application context only
        @Volatile
        private var instance: RouterData? = null

        fun get(context: Context): RouterData =
            instance ?: synchronized(this) {
                instance ?: RouterData(context.applicationContext).also { instance = it }
            }
    }
}
