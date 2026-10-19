package org.purplei2p.i2pd

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.purplei2p.i2pd.daemon.api.ClientService
import org.purplei2p.i2pd.daemon.api.StatusSnapshot
import org.purplei2p.i2pd.service.Lifecycle
import org.purplei2p.i2pd.service.RouterController
import org.purplei2p.i2pd.service.RouterState
import org.purplei2p.i2pd.service.RouterStateText
import org.purplei2p.i2pd.service.R as ServiceR

@Composable
fun StatusScreen(checks: SystemChecks, onOpenWebConsole: (String) -> Unit, modifier: Modifier = Modifier) {
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
        StateCard(state, now)
        Controls(
            state = state,
            onStart = start,
            onStop = { RouterController.stopGraceful(context) },
            onStopNow = { RouterController.stopNow(context) },
            onRestart = { RouterController.restart(context) },
            onCancel = { RouterController.cancelGraceful(context) },
            onDismiss = { RouterController.dismiss(context) },
        )
        state.status?.webConsoleUrl?.takeIf(::isLoopbackUrl)?.let { url ->
            OutlinedButton(onClick = { onOpenWebConsole(url) }) { Text(stringResource(R.string.action_web_console)) }
        }
        state.status?.let { StatusDetails(it) }
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

@Composable
private fun StateCard(state: RouterState, now: Long) {
    val context = LocalContext.current
    val failed = state.lifecycle is Lifecycle.Failed && state.autoRestartAt == null
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (failed) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge)
            Text(RouterStateText.title(context, state), style = MaterialTheme.typography.headlineSmall)
            RouterStateText.summary(context, state, now)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Controls(
    state: RouterState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onStopNow: () -> Unit,
    onRestart: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state.lifecycle) {
            Lifecycle.Stopped -> if (!state.isActive) {
                Button(onClick = onStart) { Text(stringResource(ServiceR.string.action_start)) }
            }
            Lifecycle.Preparing, Lifecycle.Starting, Lifecycle.Stopping ->
                OutlinedButton(onClick = onStopNow) { Text(stringResource(ServiceR.string.action_stop_now)) }
            Lifecycle.Running -> {
                Button(onClick = onStop) { Text(stringResource(ServiceR.string.action_stop)) }
                OutlinedButton(onClick = onStopNow) { Text(stringResource(ServiceR.string.action_stop_now)) }
                OutlinedButton(onClick = onRestart) { Text(stringResource(ServiceR.string.action_restart)) }
            }
            is Lifecycle.Draining -> {
                Button(onClick = onCancel) { Text(stringResource(ServiceR.string.action_cancel)) }
                OutlinedButton(onClick = onStopNow) { Text(stringResource(ServiceR.string.action_stop_now)) }
            }
            is Lifecycle.Failed -> {
                Button(onClick = onStart) { Text(stringResource(ServiceR.string.action_start)) }
                OutlinedButton(onClick = if (state.autoRestartAt != null) onStopNow else onDismiss) {
                    Text(stringResource(if (state.autoRestartAt != null) ServiceR.string.action_stop_now else R.string.action_dismiss))
                }
            }
        }
    }
}

@Composable
private fun StatusDetails(status: StatusSnapshot) {
    val context = LocalContext.current
    fun speed(value: Long) = RouterStateText.speed(context, value)
    fun bytes(value: Long) = RouterStateText.bytes(context, value)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            StatusRow(R.string.status_services, servicesText(status.services))
        }
    }
}

@Composable
private fun servicesText(services: Set<ClientService>): String {
    if (services.isEmpty()) return stringResource(R.string.status_services_none)
    return ClientService.entries.filter { it in services }.map {
        stringResource(
            when (it) {
                ClientService.HTTP_PROXY -> R.string.service_http_proxy
                ClientService.SOCKS_PROXY -> R.string.service_socks_proxy
                ClientService.SAM -> R.string.service_sam
                ClientService.BOB -> R.string.service_bob
                ClientService.I2CP -> R.string.service_i2cp
                ClientService.WEB_CONSOLE -> R.string.service_web_console
            },
        )
    }.joinToString(", ")
}

@Composable
private fun StatusRow(label: Int, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
    }
}
