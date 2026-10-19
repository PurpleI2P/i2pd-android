package org.purplei2p.i2pd

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect

private enum class Tab(val label: Int, val icon: Int) {
    STATUS(R.string.tab_status, R.drawable.ic_nav_status),
    LOGS(R.string.tab_logs, R.drawable.ic_nav_logs),
    SETTINGS(R.string.tab_settings, R.drawable.ic_nav_settings),
}

@Composable
fun AppScaffold() {
    var tab by rememberSaveable { mutableStateOf(Tab.STATUS) }
    var webConsoleUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val checks = rememberSystemChecks()

    webConsoleUrl?.let { url ->
        BackHandler { webConsoleUrl = null }
        WebConsoleScreen(url = url, onBack = { webConsoleUrl = null })
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(painterResource(item.icon), contentDescription = null) },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            Tab.STATUS -> StatusScreen(checks = checks, onOpenWebConsole = { webConsoleUrl = it }, modifier = modifier)
            Tab.LOGS -> LogsScreen(modifier = modifier)
            Tab.SETTINGS -> SettingsScreen(checks = checks, modifier = modifier)
        }
    }
}

/** System conditions that affect background work, refreshed when the app comes back to front. */
class SystemChecks(val batteryOptimized: Boolean, val notificationsEnabled: Boolean)

@Composable
private fun rememberSystemChecks(): SystemChecks {
    val context = LocalContext.current
    var checks by remember { mutableStateOf(readChecks(context)) }
    LifecycleResumeEffect(Unit) {
        checks = readChecks(context)
        onPauseOrDispose { }
    }
    return checks
}

private fun readChecks(context: Context) = SystemChecks(
    batteryOptimized = !context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
    notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
)

/** Asks the user to exempt the app from battery optimization. */
@SuppressLint("BatteryLife")
fun requestIgnoreBatteryOptimizations(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())
    try {
        context.startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= 26) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
    }
    context.startActivity(intent)
}
