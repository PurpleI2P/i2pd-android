package org.purplei2p.i2pd

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.purplei2p.i2pd.config.OptionGroup
import org.purplei2p.i2pd.daemon.api.ClientService
import org.purplei2p.i2pd.service.Lifecycle
import org.purplei2p.i2pd.service.RouterController
import org.purplei2p.i2pd.service.RouterState
import org.purplei2p.i2pd.service.RouterStateText
import org.purplei2p.i2pd.service.R as ServiceR
import org.purplei2p.i2pd.service.HistorySample

@Composable
fun StatusScreen(
    checks: SystemChecks,
    onOpenWebConsole: (String) -> Unit,
    onOpenSettings: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by RouterController.state(context).collectAsStateWithLifecycle()
    // Ticks for countdowns of graceful stop and automatic restart
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1000)
            value = System.currentTimeMillis()
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        RouterController.start(context)
    }
    val start = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // The router works without the permission, the notification is just hidden
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            RouterController.start(context)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppHeader()
        if (checks.batteryOptimized) {
            Banner(stringResource(R.string.banner_battery), stringResource(R.string.action_disable)) {
                requestIgnoreBatteryOptimizations(context)
            }
        }
        if (!checks.notificationsEnabled) {
            Banner(stringResource(R.string.banner_notifications), stringResource(R.string.action_open_settings)) {
                openNotificationSettings(context)
            }
        }
        RestartBanner()
        Controls(
            state = state,
            onStart = start,
            onWebConsole = state.status?.webConsoleUrl?.takeIf(::isLoopbackUrl)?.let { url -> { onOpenWebConsole(url) } },
        )
        StatusDetails(state, now)
        state.status?.let { ServiceChips(it.services, onOpenSettings) }
        val history by RouterController.history(context).collectAsStateWithLifecycle()
        if (state.status != null) {
            SpeedGraph(history)
            TunnelsGraph(history)
        }
    }
}

/** Logo, name and motto of the project. */
@Composable
fun AppHeader(modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(72.dp))
            Column(Modifier.padding(start = 16.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.app_description), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.app_description2),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Banner(text: String, action: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(action) }
        }
    }
}

/** What the first action of the panel does in the current state. */
private enum class MainAction(val icon: Int, val label: Int) {
    START(R.drawable.ic_action_start, ServiceR.string.action_start),
    STOP(R.drawable.ic_action_stop, ServiceR.string.action_stop),
    CANCEL_STOP(R.drawable.ic_action_start, ServiceR.string.action_cancel),
}

/**
 * Start/Stop, Restart and Web console in one row. Stop is graceful; touching and holding it stops at
 * once, which is explained by a hint the first time Stop shows up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Controls(state: RouterState, onStart: () -> Unit, onWebConsole: (() -> Unit)?) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val store = RouterController.settings(context)
    val settings by store.settings.collectAsStateWithLifecycle()
    val lifecycle = state.lifecycle
    val autoRestarting = lifecycle is Lifecycle.Failed && state.autoRestartAt != null
    val main = when {
        lifecycle is Lifecycle.Draining -> MainAction.CANCEL_STOP
        lifecycle == Lifecycle.Stopped && !state.isActive -> MainAction.START
        lifecycle is Lifecycle.Failed && !autoRestarting -> MainAction.START
        else -> MainAction.STOP
    }
    val stopNow = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        RouterController.stopNow(context)
        store.update { it.copy(stopHintShown = true) }
    }
    val onMain = when (main) {
        MainAction.START -> onStart
        MainAction.CANCEL_STOP -> ({ RouterController.cancelGraceful(context) })
        // Graceful only for a running router: a starting one or a pending automatic restart stop at once
        MainAction.STOP -> if (lifecycle == Lifecycle.Running) ({ RouterController.stopGraceful(context) }) else stopNow
    }
    val holdToStop = main != MainAction.START

    val hint = rememberTooltipState(isPersistent = true)
    LaunchedEffect(main, settings.stopHintShown) {
        if (main == MainAction.STOP && lifecycle == Lifecycle.Running && !settings.stopHintShown) hint.show()
    }

    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(4.dp)) {
            // weight() on the tooltip box itself does not reach the row, so the box gets its own cell
            Box(Modifier.weight(1f)) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                    tooltip = {
                        RichTooltip(
                            action = {
                                TextButton(onClick = {
                                    hint.dismiss()
                                    store.update { it.copy(stopHintShown = true) }
                                }) { Text(stringResource(R.string.hint_got_it)) }
                            },
                        ) { Text(stringResource(R.string.hint_stop_now, stringResource(ServiceR.string.action_stop))) }
                    },
                    state = hint,
                    // The long press of the anchor is "stop now", not "show the tooltip"
                    enableUserInput = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ControlItem(
                        icon = main.icon,
                        label = stringResource(main.label),
                        highlighted = true,
                        onClick = onMain,
                        onLongClick = if (holdToStop) stopNow else null,
                        onLongClickLabel = stringResource(ServiceR.string.action_stop_now),
                        // The tooltip box does not stretch its content: fill the third of the row like the others
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            ControlItem(
                icon = R.drawable.ic_action_restart,
                label = stringResource(ServiceR.string.action_restart),
                enabled = lifecycle == Lifecycle.Running,
                onClick = { RouterController.restart(context) },
                modifier = Modifier.weight(1f),
            )
            ControlItem(
                icon = R.drawable.ic_action_web_console,
                label = stringResource(R.string.action_web_console),
                enabled = onWebConsole != null,
                onClick = { onWebConsole?.invoke() },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ControlItem(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        highlighted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClickLabel,
            )
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun StatusDetails(state: RouterState, now: Long) {
    val context = LocalContext.current
    fun speed(value: Long) = RouterStateText.speed(context, value)
    fun bytes(value: Long) = RouterStateText.bytes(context, value)
    val failed = state.lifecycle is Lifecycle.Failed && state.autoRestartAt == null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (failed) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    stringResource(R.string.status_state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.4f),
                )
                // Announced by TalkBack when the router state changes; the countdowns below are not
                Text(
                    RouterStateText.title(context, state),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(0.6f).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            // Countdowns and errors; the running summary repeats the rows below, so it is not shown
            if (state.lifecycle != Lifecycle.Running) {
                RouterStateText.summary(context, state, now)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            if (failed) {
                TextButton(onClick = { RouterController.dismiss(context) }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.action_dismiss))
                }
            }
            val status = state.status ?: return@Column
            StatusRow(R.string.status_network, RouterStateText.network(context, status))
            StatusRow(R.string.status_uptime, RouterStateText.duration(status.uptimeSeconds))
            StatusRow(
                R.string.status_speed,
                stringResource(R.string.status_speed_value, speed(status.bandwidthIn), speed(status.bandwidthOut), speed(status.bandwidthTransit)),
            )
            StatusRow(
                R.string.status_traffic,
                stringResource(R.string.status_traffic_value, bytes(status.totalReceived), bytes(status.totalSent), bytes(status.totalTransit)),
            )
            StatusRow(
                R.string.status_tunnels,
                stringResource(R.string.status_tunnels_value, status.tunnelsIn, status.tunnelsOut, status.tunnelsTransit, status.tunnelSuccessRate),
            )
            StatusRow(
                R.string.status_netdb,
                stringResource(R.string.status_netdb_value, status.routers, status.floodfills, status.leaseSets),
            )
            StatusRow(
                R.string.status_transit,
                stringResource(if (status.acceptsTransit) R.string.status_transit_accepted else R.string.status_transit_declined),
            )
        }
    }
}

/** Client services of the running router; a chip opens the settings of its service. */
@Composable
private fun ServiceChips(services: Set<ClientService>, onOpenSettings: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.status_services),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        if (services.isEmpty()) {
            Text(stringResource(R.string.status_services_none), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        // Two equal columns; an odd last chip takes the whole row, so the grid has no gaps
        ClientService.entries.filter { it in services }.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { service ->
                    AssistChip(
                        onClick = { onOpenSettings(service.settingsTarget()) },
                        label = { Text(stringResource(service.label()), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun ClientService.label(): Int = when (this) {
    ClientService.HTTP_PROXY -> R.string.service_http_proxy
    ClientService.SOCKS_PROXY -> R.string.service_socks_proxy
    ClientService.SAM -> R.string.service_sam
    ClientService.BOB -> R.string.service_bob
    ClientService.I2CP -> R.string.service_i2cp
    ClientService.WEB_CONSOLE -> R.string.service_web_console
}

private fun ClientService.settingsTarget(): String = when (this) {
    ClientService.HTTP_PROXY -> SettingsTarget.group(OptionGroup.HTTP_PROXY)
    ClientService.SOCKS_PROXY -> SettingsTarget.group(OptionGroup.SOCKS_PROXY)
    ClientService.SAM -> SettingsTarget.group(OptionGroup.SAM)
    ClientService.I2CP -> SettingsTarget.group(OptionGroup.I2CP)
    ClientService.WEB_CONSOLE -> SettingsTarget.group(OptionGroup.WEB_CONSOLE)
    ClientService.BOB -> SettingsTarget.group(OptionGroup.BOB)
}

@Composable
private fun StatusRow(label: Int, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
    }
}

/** One line of a [HistoryGraph]. */
private class Series(val color: Color, val label: String, val value: (HistorySample) -> Long)

/** Speeds of the last five minutes: download, upload and transit. */
@Composable
private fun SpeedGraph(samples: List<HistorySample>) {
    val context = LocalContext.current
    HistoryGraph(
        title = stringResource(R.string.status_graph_title),
        samples = samples,
        // At least 1 KB/s, so an idle router does not show noise as full-height peaks
        floor = 1024L,
        format = { RouterStateText.speed(context, it) },
        series = listOf(
            Series(MaterialTheme.colorScheme.primary, stringResource(R.string.status_graph_in)) { it.inbound },
            Series(MaterialTheme.colorScheme.tertiary, stringResource(R.string.status_graph_out)) { it.outbound },
            Series(MaterialTheme.colorScheme.secondary, stringResource(R.string.status_transit)) { it.transit },
        ),
    )
}

/** Tunnel counts of the last five minutes: inbound, outbound and transit. */
@Composable
private fun TunnelsGraph(samples: List<HistorySample>) {
    HistoryGraph(
        title = stringResource(R.string.status_graph_tunnels_title),
        samples = samples,
        floor = 10L,
        format = { it.toString() },
        series = listOf(
            Series(MaterialTheme.colorScheme.primary, stringResource(R.string.status_graph_tunnels_in)) { it.tunnelsIn.toLong() },
            Series(MaterialTheme.colorScheme.tertiary, stringResource(R.string.status_graph_tunnels_out)) { it.tunnelsOut.toLong() },
            Series(MaterialTheme.colorScheme.secondary, stringResource(R.string.status_transit)) { it.tunnelsTransit.toLong() },
        ),
    )
}

/** Lines of the last five minutes, the newest on the right, scaled to the largest value but at least [floor]. */
@Composable
private fun HistoryGraph(title: String, samples: List<HistorySample>, floor: Long, format: (Long) -> String, series: List<Series>) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = maxOf(floor, samples.maxOfOrNull { sample -> series.maxOf { it.value(sample) } } ?: 0L)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.status_graph_max, format(max)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                for (i in 0..3) {
                    val y = size.height * i / 3
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                if (samples.size < 2) return@Canvas
                val step = size.width / (HISTORY_SAMPLES - 1)
                // Older samples start further left; until five minutes pass the graph fills from the right
                val startX = size.width - step * (samples.size - 1)
                // Drawn in reverse, so the first series is on top
                series.asReversed().forEach { line ->
                    val path = Path()
                    samples.forEachIndexed { index, sample ->
                        val x = startX + step * index
                        val y = size.height * (1f - line.value(sample).toFloat() / max)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, line.color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            val last = samples.lastOrNull()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                series.forEach { line -> Legend(line.color, line.label, last?.let { format(line.value(it)) }) }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String, value: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        Text(
            value?.let { "$label $it" } ?: label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** One per second for five minutes, as kept by StatusHistory. */
private const val HISTORY_SAMPLES = 300
