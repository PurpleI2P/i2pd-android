package org.purplei2p.i2pd.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Starts the router after boot (if enabled in settings) and after an app update
 * (if it was running before). Both broadcasts allow starting a foreground service from background.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val start = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> RouterController.settings(context).settings.value.autostartOnBoot
            Intent.ACTION_MY_PACKAGE_REPLACED -> RouterController.isWanted(context)
            else -> false
        }
        Log.i(TAG, "${intent.action}: start=$start")
        if (start) RouterController.startService(context, RouterService.ACTION_START)
    }

    private companion object {
        const val TAG = "i2pd-boot"
    }
}
