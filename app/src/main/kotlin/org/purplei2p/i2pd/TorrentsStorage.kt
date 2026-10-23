package org.purplei2p.i2pd

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import org.purplei2p.i2pd.service.RouterController
import java.io.File

/**
 * Where torrents tunnels download. i2pd writes with plain file access, so a folder must be one the
 * app may write without SAF: its media folder on each volume (Android/media/<package>, no permission,
 * seen by the user and file managers) or, with all files access (Android 11+) or the storage
 * permission (Android 6-10, internal shared storage only), any other folder.
 */
object TorrentsStorage {

    /** The torrents folder in the media folder of the app on one volume. */
    class Volume(val dir: File, val removable: Boolean, val description: String?)

    enum class Access {
        /** A folder of the app: no permission needed. */
        OWN,
        GRANTED,
        /** Android 11+: all files access is not granted. */
        NEEDS_ALL_FILES,
        /** Android 6-10: the storage permission is not granted. */
        NEEDS_STORAGE,
        /** Android 6-10: only app folders of a removable volume are writable. */
        IMPOSSIBLE,
    }

    fun volumes(context: Context): List<Volume> {
        val storage = context.getSystemService(StorageManager::class.java)
        return context.externalMediaDirs.filterNotNull()
            .filter { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }
            .map { dir ->
                val description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) storage.getStorageVolume(dir)?.getDescription(context) else null
                Volume(File(dir, DIR), Environment.isExternalStorageRemovable(dir), description)
            }
    }

    /** Default folder of a new torrents tunnel: the media folder on a removable volume if there is one, flash memory lasts longer then. */
    fun defaultDir(context: Context): String {
        val volumes = volumes(context)
        val volume = volumes.firstOrNull { it.removable } ?: volumes.firstOrNull()
        return volume?.dir?.absolutePath ?: File(RouterController.data(context).root, "torrents/downloads").absolutePath
    }

    /** True for a folder inside the media folder of the app, removed with the app data. */
    fun isMediaDir(context: Context, path: String): Boolean =
        context.externalMediaDirs.filterNotNull().any { isInside(path, it) }

    fun access(context: Context, path: String): Access {
        val own = listOf(File(context.applicationInfo.dataDir)) + context.externalMediaDirs.filterNotNull() + context.getExternalFilesDirs(null).filterNotNull()
        if (own.any { isInside(path, it) }) return Access.OWN
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return if (Environment.isExternalStorageManager()) Access.GRANTED else Access.NEEDS_ALL_FILES
        }
        @Suppress("DEPRECATION")
        if (!isInside(path, Environment.getExternalStorageDirectory())) return Access.IMPOSSIBLE
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        return if (granted) Access.GRANTED else Access.NEEDS_STORAGE
    }

    /** Settings screen of all files access for the app. */
    @RequiresApi(Build.VERSION_CODES.R)
    fun allFilesAccessIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:${context.packageName}".toUri())

    /**
     * Path of a folder picked with ACTION_OPEN_DOCUMENT_TREE, null when the provider is not backed
     * by a volume (cloud storage and the like). Document ids of the external storage provider are
     * "<volume>:<relative path>", "primary" being the internal shared storage.
     */
    fun pathOfTree(context: Context, tree: Uri): String? {
        val id = DocumentsContract.getTreeDocumentId(tree)
        return when (tree.authority) {
            EXTERNAL_STORAGE_PROVIDER -> {
                val volume = id.substringBefore(':')
                val relative = id.substringAfter(':', "")
                @Suppress("DEPRECATION")
                val root = when (volume) {
                    "primary" -> Environment.getExternalStorageDirectory()
                    "home" -> File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOCUMENTS)
                    else -> volumeRoot(context, volume) ?: return null
                }
                (if (relative.isEmpty()) root else File(root, relative)).absolutePath
            }
            DOWNLOADS_PROVIDER -> id.takeIf { it.startsWith(RAW) }?.removePrefix(RAW)
            else -> null
        }
    }

    /** Root of a removable volume by its id (file system UUID), found through the media folders of the app. */
    private fun volumeRoot(context: Context, volume: String): File? =
        context.externalMediaDirs.filterNotNull()
            .map { generateSequence(it) { dir -> dir.parentFile }.firstOrNull { dir -> dir.name == volume } }
            .firstOrNull { it != null }
            ?: File("/storage", volume).takeIf { it.isDirectory }

    private fun isInside(path: String, dir: File): Boolean {
        val target = canonical(File(path))
        val base = canonical(dir)
        return target == base || target.startsWith(base + File.separator)
    }

    private fun canonical(file: File): String = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)

    private const val DIR = "torrents"
    private const val EXTERNAL_STORAGE_PROVIDER = "com.android.externalstorage.documents"
    private const val DOWNLOADS_PROVIDER = "com.android.providers.downloads.documents"
    private const val RAW = "raw:"
}
