package org.purplei2p.i2pd

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.purplei2p.i2pd.service.TorrentFile
import java.io.File
import java.io.IOException

/**
 * Copies of finished torrents into folders picked through SAF ("Save to…"). The torrent stays where
 * i2pd downloaded it and keeps seeding from there. Copies go on while the app process lives, also
 * after the torrents screen is closed.
 */
object TorrentExports {

    data class Progress(val copied: Long, val total: Long, val error: String? = null, val done: Boolean = false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private val _state = MutableStateFlow<Map<String, Progress>>(emptyMap())

    /** Copies by key, see [key]. */
    val state: StateFlow<Map<String, Progress>> = _state.asStateFlow()

    fun key(tunnel: String, torrent: String) = "$tunnel\n$torrent"

    /** Copies [files] (relative to [dir], as the RPC names them) into the [tree] folder. */
    fun start(context: Context, key: String, dir: File, files: List<TorrentFile>, tree: Uri) {
        if (jobs[key]?.isActive == true) return
        val resolver = context.applicationContext.contentResolver
        val root = DocumentFile.fromTreeUri(context.applicationContext, tree) ?: return
        val total = files.sumOf { it.length }
        _state.update { it + (key to Progress(0, total)) }
        jobs[key] = scope.launch {
            var copied = 0L
            var current: DocumentFile? = null
            try {
                val dirs = mutableMapOf("" to root)
                for (file in files) {
                    val parts = file.name.split('/').filter { it.isNotEmpty() }
                    if (parts.isEmpty() || parts.any { it == "." || it == ".." }) throw IOException("Bad file name ${file.name}")
                    var parent = root
                    var prefix = ""
                    for (part in parts.dropLast(1)) {
                        prefix += "/$part"
                        parent = dirs.getOrPut(prefix) { parent.createDirectory(part) ?: throw IOException("Cannot create $part") }
                    }
                    val name = parts.last()
                    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
                    val target = parent.createFile(mime, name) ?: throw IOException("Cannot create $name")
                    current = target
                    File(dir, file.name).inputStream().use { input ->
                        (resolver.openOutputStream(target.uri) ?: throw IOException("Cannot write $name")).use { output ->
                            val buffer = ByteArray(1024 * 1024)
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                copied += read
                                _state.update { it + (key to Progress(copied, total)) }
                            }
                        }
                    }
                    current = null
                }
                _state.update { it + (key to Progress(copied, total, done = true)) }
            } catch (e: CancellationException) {
                current?.delete()
                _state.update { it - key }
                throw e
            } catch (e: Exception) {
                current?.delete()
                _state.update { it + (key to Progress(copied, total, error = e.message ?: e.toString())) }
            }
        }
    }

    fun cancel(key: String) {
        jobs.remove(key)?.cancel()
    }

    /** Forgets a finished or failed copy. */
    fun dismiss(key: String) {
        if (jobs[key]?.isActive != true) {
            jobs.remove(key)
            _state.update { it - key }
        }
    }
}
