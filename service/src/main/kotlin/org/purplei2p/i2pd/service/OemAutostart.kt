package org.purplei2p.i2pd.service

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Firmwares with their own autostart manager on top of Android battery optimization: without the
 * permission they do not deliver BOOT_COMPLETED and kill the app in background even with a foreground
 * service. Whether the permission is granted cannot be read, so the app can only open the screen.
 */
enum class OemAutostart(
    /** Shown to the user, e.g. "Autostart (Xiaomi)". */
    val displayName: String,
    private val manufacturers: Set<String>,
    /** Known autostart screens (package to activity), tried in order: they differ between firmware versions. */
    private val screens: List<Pair<String, String>>,
) {
    XIAOMI(
        "Xiaomi", setOf("xiaomi", "redmi", "poco"),
        listOf("com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity"),
    ),
    HUAWEI(
        "Huawei", setOf("huawei"),
        listOf(
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        ),
    ),
    HONOR(
        "Honor", setOf("honor"),
        listOf(
            "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        ),
    ),
    OPPO(
        "OPPO", setOf("oppo", "realme", "oneplus"),
        listOf(
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
        ),
    ),
    VIVO(
        "vivo", setOf("vivo", "iqoo"),
        listOf(
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        ),
    ),
    ASUS(
        "ASUS", setOf("asus"),
        listOf("com.asus.mobilemanager" to "com.asus.mobilemanager.autostart.AutoStartActivity"),
    ),
    ;

    /**
     * Opens the autostart screen of the firmware; if none of the known ones exists on this version,
     * the system page of the app, where some firmwares keep the switch too.
     */
    fun open(context: Context) {
        for ((pkg, activity) in screens) {
            val intent = Intent().setComponent(ComponentName(pkg, activity)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return
            } catch (e: ActivityNotFoundException) {
                // Not on this firmware version, try the next one
            } catch (e: SecurityException) {
                // Not exported on this firmware version
            }
        }
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    companion object {
        /** The firmware of a device by its manufacturer and brand; null for firmwares without such a manager. */
        fun of(manufacturer: String, brand: String): OemAutostart? {
            val names = setOf(manufacturer.lowercase(), brand.lowercase())
            return entries.firstOrNull { oem -> oem.manufacturers.any { it in names } }
        }

        val current: OemAutostart? get() = of(Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty())
    }
}
