package org.purplei2p.i2pd.service

import android.content.Context
import android.content.res.AssetManager
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import org.purplei2p.i2pd.daemon.api.StartParams
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Prepares the private data directory (filesDir/i2pd) and builds i2pd start parameters.
 *
 * Bundled assets are copied when the app version changes: certificates are replaced,
 * configuration files are copied only if missing, so user changes are kept.
 */
class DataDirPreparer(private val context: Context) {

    val dataDir: File get() = File(context.filesDir, DATA_DIR_NAME)

    fun prepare(): StartParams {
        val root = dataDir
        if (!root.isDirectory && !root.mkdirs()) throw IOException("Cannot create $root")

        val stamp = File(root, ASSETS_STAMP)
        val version = appVersionCode().toString()
        if (stamp.takeIf { it.exists() }?.readText()?.trim() != version) {
            Log.i(TAG, "Updating bundled assets in $root")
            File(root, CERTIFICATES).deleteRecursively()
            for (asset in ASSETS) copyAsset(context.assets, asset, root)
            stamp.writeText(version)
        }

        return StartParams(
            dataDir = root.absolutePath,
            args = listOf(
                "--datadir=${root.absolutePath}",
                "--conf=${File(root, "i2pd.conf").absolutePath}",
                "--tunconf=${File(root, "tunnels.conf").absolutePath}",
                "--tunnelsdir=${File(root, "tunnels.d").absolutePath}",
                "--certsdir=${File(root, CERTIFICATES).absolutePath}",
            ),
            language = Locale.getDefault().getDisplayLanguage(Locale.ENGLISH).lowercase(Locale.ROOT),
        )
    }

    /**
     * Copies an asset file or directory; existing files are left untouched, and so are files
     * the user switched off (tunnel files renamed to *.disabled).
     */
    private fun copyAsset(assets: AssetManager, path: String, root: File) {
        val children = assets.list(path).orEmpty()
        val target = File(root, path)
        if (children.isEmpty()) {
            if (target.exists() || File(target.path + DISABLED_SUFFIX).exists()) return
            target.parentFile?.mkdirs()
            val tmp = File(target.path + ".tmp")
            assets.open(path).use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(target)) throw IOException("Cannot create $target")
        } else {
            target.mkdirs()
            for (child in children) copyAsset(assets, "$path/$child", root)
        }
    }

    @Suppress("DEPRECATION")
    private fun appVersionCode(): Long {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        return PackageInfoCompat.getLongVersionCode(info)
    }

    /** Makes the next [prepare] copy bundled files again (missing ones only, certificates replaced). */
    fun forgetAssetsVersion() {
        File(dataDir, ASSETS_STAMP).delete()
    }

    private companion object {
        const val TAG = "i2pd-datadir"
        const val DATA_DIR_NAME = "i2pd"
        const val ASSETS_STAMP = ".assets-version"
        const val CERTIFICATES = "certificates"
        const val DISABLED_SUFFIX = ".disabled"
        val ASSETS = listOf("i2pd.conf", "tunnels.conf", "tunnels.d", "addressbook", CERTIFICATES)
    }
}
