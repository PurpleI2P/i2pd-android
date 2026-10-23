package org.purplei2p.i2pd

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.purplei2p.i2pd.service.RouterController

private enum class Tab(val label: Int, val icon: Int) {
    STATUS(R.string.tab_status, R.drawable.ic_nav_status),
    TUNNELS(R.string.tab_tunnels, R.drawable.ic_nav_tunnels),
    LOGS(R.string.tab_logs, R.drawable.ic_nav_logs),
    SETTINGS(R.string.tab_settings, R.drawable.ic_nav_settings),
}

@Composable
fun AppScaffold() {
    var tab by rememberSaveable { mutableStateOf(Tab.STATUS) }
    var webConsoleUrl by rememberSaveable { mutableStateOf<String?>(null) }
    // Where the settings tab should open next, see SettingsTarget
    var settingsTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val openSettings = { target: String ->
        settingsTarget = target
        tab = Tab.SETTINGS
    }
    val settingsOpened = { settingsTarget = null }
    // Where the tunnels tab should open next, see TunnelsTarget
    var tunnelsTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val openTunnels = { target: String ->
        tunnelsTarget = target
        tab = Tab.TUNNELS
    }
    val tunnelsOpened = { tunnelsTarget = null }
    val checks = rememberSystemChecks()
    val context = LocalContext.current
    val settingsStore = RouterController.settings(context)
    val settings by settingsStore.settings.collectAsStateWithLifecycle()

    if (!settings.onboardingDone) {
        Scaffold { padding ->
            OnboardingScreen(checks, onDone = { settingsStore.update { it.copy(onboardingDone = true) } }, modifier = Modifier.padding(padding))
        }
        return
    }

    webConsoleUrl?.let { url ->
        BackHandler { webConsoleUrl = null }
        WebConsoleScreen(url = url, onBack = { webConsoleUrl = null })
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Tablets and landscape phones: navigation rail at the side instead of the bottom bar
        if (maxWidth >= WIDE_SCREEN) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    Spacer(Modifier.weight(1f))
                    Tab.entries.forEach { item ->
                        NavigationRailItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(painterResource(item.icon), contentDescription = null) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
                Scaffold(
                    modifier = Modifier.weight(1f),
                    contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End),
                ) { padding -> TabContent(tab, checks, { webConsoleUrl = it }, openSettings, settingsTarget, settingsOpened, openTunnels, tunnelsTarget, tunnelsOpened, padding) }
            }
        } else {
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
            ) { padding -> TabContent(tab, checks, { webConsoleUrl = it }, openSettings, settingsTarget, settingsOpened, openTunnels, tunnelsTarget, tunnelsOpened, padding) }
        }
    }
}

private val WIDE_SCREEN = 600.dp

/** Lists and forms get unreadable when stretched over a whole tablet, logs use all the width. */
private val CONTENT_MAX_WIDTH = 840.dp

@Composable
private fun TabContent(
    tab: Tab,
    checks: SystemChecks,
    onOpenWebConsole: (String) -> Unit,
    onOpenSettings: (String) -> Unit,
    settingsTarget: String?,
    onSettingsOpened: () -> Unit,
    onOpenTunnels: (String) -> Unit,
    tunnelsTarget: String?,
    onTunnelsOpened: () -> Unit,
    padding: PaddingValues,
) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        val modifier = if (tab == Tab.LOGS) Modifier else Modifier.widthIn(max = CONTENT_MAX_WIDTH)
        when (tab) {
            Tab.STATUS -> StatusScreen(checks = checks, onOpenWebConsole = onOpenWebConsole, onOpenSettings = onOpenSettings, onOpenTunnels = onOpenTunnels, modifier = modifier)
            Tab.TUNNELS -> TunnelsScreen(target = tunnelsTarget, onTargetOpened = onTunnelsOpened, modifier = modifier)
            Tab.LOGS -> LogsScreen(modifier = modifier)
            Tab.SETTINGS -> SettingsScreen(checks = checks, target = settingsTarget, onTargetOpened = onSettingsOpened, modifier = modifier)
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
