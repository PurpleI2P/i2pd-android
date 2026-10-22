package org.purplei2p.i2pd.service

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.purplei2p.i2pd.config.ConfigEditor
import org.purplei2p.i2pd.config.DestinationUser
import org.purplei2p.i2pd.config.I2pAddress
import org.purplei2p.i2pd.config.SharedDestinations
import org.purplei2p.i2pd.config.TunnelFile
import org.purplei2p.i2pd.config.TunnelIssue
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Address of a destination; [b33] is set when it publishes an encrypted lease set. */
data class DestinationAddress(val b32: String, val b33: String?)

/** All tunnel files with what the main config contributes to them. */
data class TunnelSet(
    val files: List<TunnelFile>,
    /** Keys of the main config proxies (httpproxy, socksproxy), they share destinations with tunnels. */
    val proxyKeys: Map<String, String>,
    /** Local ports of enabled main config services, tunnels must not listen on them. */
    val servicePorts: Map<String, Int>,
) {
    val sharedDestinations: Map<String, List<DestinationUser>> by lazy { SharedDestinations.of(files, proxyKeys) }

    val issues: List<TunnelIssue> by lazy {
        files.flatMap { it.validate() } + TunnelFile.portConflicts(files, servicePorts)
    }

    fun file(path: String): TunnelFile? = files.firstOrNull { it.path == path }
}

/**
 * Tunnel files of the private data directory: tunnels.conf and tunnels.d/ (names ending with
 * ".conf", or ".conf.disabled" for switched off files, which i2pd skips). Main process only.
 */
class RouterTunnels private constructor(context: Context) {

    private val preparer = DataDirPreparer(context)
    private val config = RouterConfig.get(context)
    private val mutex = Mutex()
    private val root: File get() = preparer.dataDir

    private val _revision = MutableStateFlow(0)

    /** Changes on every save; screens reload when it changes. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val changedSinceApply = MutableStateFlow(false)

    /** Enabled tunnel files (path to text) as the router read them: at its start or the last apply. */
    @Volatile
    private var applied: Map<String, String>? = null

    /** Enabled tunnel files as they are now, refreshed after every change. */
    @Volatile
    private var current: Map<String, String> = emptyMap()

    /** True while saved tunnel changes are not applied to the running router. */
    fun pendingApply(state: Flow<RouterState>): Flow<Boolean> =
        combine(state, changedSinceApply) { router, changed -> changed && router.lifecycle.hasRouterProcess }

    suspend fun load(): TunnelSet = mutex.withLock {
        val editor = config.load()
        withContext(Dispatchers.IO) {
            if (!File(root, MAIN_FILE).exists() && !File(root, DIR).exists()) preparer.prepare()
            TunnelSet(readFiles(), proxyKeys(editor), servicePorts(editor))
        }
    }

    /** Saves the document of [file] to its path. */
    suspend fun save(file: TunnelFile) = mutex.withLock {
        withContext(Dispatchers.IO) { write(resolve(file.path), file.document.toString()) }
        changed()
    }

    /** Saves raw [text] as the file at [path]. */
    suspend fun saveText(path: String, text: String) = mutex.withLock {
        withContext(Dispatchers.IO) { write(resolve(path), text) }
        changed()
    }

    /**
     * Creates tunnels.d/<name>.conf with one tunnel. Returns the path of the new file.
     * The file name is derived from the tunnel name and made unique.
     */
    suspend fun create(name: String, values: List<Pair<String, String>>): String = mutex.withLock {
        val path = withContext(Dispatchers.IO) {
            val dir = File(root, DIR).apply { mkdirs() }
            val base = name.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('.').ifEmpty { "tunnel" }
            var candidate = base
            var n = 2
            while (File(dir, "$candidate.conf").exists() || File(dir, "$candidate.conf$DISABLED").exists()) candidate = "$base-${n++}"
            val file = TunnelFile.parse("$DIR/$candidate.conf", "")
            file.document.addSection(name, values)
            write(File(dir, "$candidate.conf"), file.document.toString())
            file.path
        }
        changed()
        path
    }

    /** Appends tunnel [name] at the end of the file at [path]: earlier sections and their order stay as they are. */
    suspend fun append(path: String, name: String, values: List<Pair<String, String>>) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val target = resolve(path)
            val file = TunnelFile.parse(path, target.readText())
            if (name in file.allNames) throw IOException("$path already has a tunnel $name")
            file.document.addSection(name, values)
            write(target, file.document.toString())
        }
        changed()
    }

    /** Switches one tunnel on or off by commenting out its section; the rest of the file is not touched. */
    suspend fun setTunnelEnabled(path: String, name: String, enabled: Boolean) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val target = resolve(path)
            val file = TunnelFile.parse(path, target.readText())
            val changed = if (enabled) file.uncomment(name) else file.commentOut(name)
            if (!changed) throw IOException("No tunnel $name in $path")
            write(target, file.document.toString())
        }
        changed()
    }

    /** Switches a whole file on or off by its ".disabled" suffix. Returns the new path. */
    suspend fun setEnabled(path: String, enabled: Boolean): String = mutex.withLock {
        val newPath = if (enabled) path.removeSuffix(DISABLED) else if (path.endsWith(DISABLED)) path else path + DISABLED
        if (newPath != path) {
            withContext(Dispatchers.IO) {
                val target = resolve(newPath)
                if (target.exists()) throw IOException("$newPath already exists")
                if (!resolve(path).renameTo(target)) throw IOException("Cannot rename $path")
            }
            changed()
        }
        newPath
    }

    /** Removes a tunnel; a file of tunnels.d left without tunnels is deleted. */
    suspend fun deleteTunnel(path: String, name: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val target = resolve(path)
            val file = TunnelFile.parse(path, target.readText())
            file.document.removeSection(name)
            if (path.startsWith("$DIR/") && file.document.sections.isEmpty() && file.document.entries.isEmpty()) {
                if (!target.delete()) throw IOException("Cannot delete $path")
            } else {
                write(target, file.document.toString())
            }
        }
        changed()
    }

    suspend fun deleteFile(path: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (path.removeSuffix(DISABLED) == MAIN_FILE) throw IOException("$MAIN_FILE cannot be deleted")
            if (!resolve(path).delete()) throw IOException("Cannot delete $path")
        }
        changed()
    }

    /**
     * Addresses of the destination in key file [keys] (relative to the data dir), null until i2pd creates the file.
     * The b33 address is given for an [encrypted] lease set, with [clientAuth] when authorization is on.
     */
    suspend fun address(keys: String, encrypted: Boolean, clientAuth: Boolean): DestinationAddress? = withContext(Dispatchers.IO) {
        val file = File(keys).takeIf { it.isAbsolute } ?: File(root, keys)
        val bytes = file.takeIf { it.isFile }?.readBytes() ?: return@withContext null
        val b32 = I2pAddress.b32FromKeys(bytes) ?: return@withContext null
        DestinationAddress(b32, if (encrypted) I2pAddress.b33FromKeys(bytes, clientAuth) else null)
    }

    /** Applies saved changes to the running router without restarting it. */
    fun markApplied() {
        applied = current
        changedSinceApply.value = false
    }

    /** The router reads tunnels anew when it starts. Called on the IO thread. */
    internal fun onRouterStarting() {
        current = enabledFiles()
        applied = current
        changedSinceApply.value = false
    }

    /** Files were changed outside of this class (import, reset): screens reload. */
    suspend fun refresh() = changed()

    /**
     * Compares the enabled files with what the router read, so that undoing a change (deleting a
     * tunnel just added, switching a file off and on again) clears the apply banner.
     */
    private suspend fun changed() {
        current = withContext(Dispatchers.IO) { enabledFiles() }
        changedSinceApply.value = applied?.let { it != current } ?: false
        _revision.value++
    }

    /** What i2pd reads: tunnels.conf and the enabled files of tunnels.d. */
    private fun enabledFiles(): Map<String, String> = readFiles().filter { it.enabled }.associate { it.path to it.document.toString() }

    private fun readFiles(): List<TunnelFile> {
        val result = mutableListOf<TunnelFile>()
        for (name in listOf(MAIN_FILE, MAIN_FILE + DISABLED)) {
            File(root, name).takeIf { it.isFile }?.let { result += TunnelFile.parse(name, it.readText()) }
        }
        File(root, DIR).listFiles { f -> f.isFile && (f.name.endsWith(".conf") || f.name.endsWith(".conf$DISABLED")) }
            .orEmpty()
            .sortedBy { it.name.lowercase() }
            .forEach { result += TunnelFile.parse("$DIR/${it.name}", it.readText()) }
        return result
    }

    /** Only tunnels.conf (also switched off) and plain files of tunnels.d. */
    private fun resolve(path: String): File {
        val file = File(root, path).canonicalFile
        val dir = File(root, DIR).canonicalFile
        val main = listOf(MAIN_FILE, MAIN_FILE + DISABLED).map { File(root, it).canonicalFile }
        if (file !in main && file.parentFile != dir) throw IOException("Not a tunnel file: $path")
        return file
    }

    private fun write(target: File, text: String) {
        val tmp = File(target.parentFile, ".${target.name}.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray())
            out.fd.sync()
        }
        if (!tmp.renameTo(target)) throw IOException("Cannot write $target")
    }

    private fun proxyKeys(editor: ConfigEditor): Map<String, String> = buildMap {
        for (proxy in listOf("httpproxy", "socksproxy")) {
            if (!ConfigEditor.parseBool(editor.effectiveValue("$proxy.enabled"))) continue
            editor.effectiveValue("$proxy.keys")?.let { put(proxy, it) }
        }
    }

    private fun servicePorts(editor: ConfigEditor): Map<String, Int> = buildMap {
        for (service in listOf("http", "httpproxy", "socksproxy", "sam", "bob", "i2cp", "i2pcontrol")) {
            if (!ConfigEditor.parseBool(editor.effectiveValue("$service.enabled"))) continue
            editor.effectiveValue("$service.port")?.toIntOrNull()?.let { put(service, it) }
        }
    }

    companion object {
        const val MAIN_FILE = "tunnels.conf"
        const val DIR = "tunnels.d"
        const val DISABLED = ".disabled"

        @SuppressLint("StaticFieldLeak") // holds the application context only
        @Volatile
        private var instance: RouterTunnels? = null

        fun get(context: Context): RouterTunnels =
            instance ?: synchronized(this) {
                instance ?: RouterTunnels(context.applicationContext).also { instance = it }
            }
    }
}
