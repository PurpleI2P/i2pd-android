package org.purplei2p.i2pd

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.purplei2p.i2pd.nativelib.I2pdNative
import org.purplei2p.i2pd.service.AppSettingsStore
import org.purplei2p.i2pd.service.RouterController
import org.purplei2p.i2pd.service.ThemeMode

private enum class SettingsPage(val title: Int) {
    MAIN(R.string.tab_settings),
    ROUTER_BASIC(R.string.router_basic),
    ROUTER_ADVANCED(R.string.router_advanced),
    ROUTER_RAW(R.string.router_raw),
    STORAGE(R.string.data_storage),
    FILES(R.string.data_files),
}

/** Router (i2pd.conf) and application settings. */
@Composable
fun SettingsScreen(checks: SystemChecks, modifier: Modifier = Modifier) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    if (page == SettingsPage.MAIN) {
        SettingsMain(checks, onOpen = { page = it }, modifier = modifier)
        return
    }
    BackHandler { page = SettingsPage.MAIN }
    val session = rememberConfigSession()
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { page = SettingsPage.MAIN }) { Text(stringResource(R.string.action_back)) }
            Text(stringResource(page.title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        }
        when (page) {
            SettingsPage.ROUTER_BASIC -> RouterBasicScreen(session)
            SettingsPage.ROUTER_ADVANCED -> RouterAdvancedScreen(session)
            SettingsPage.ROUTER_RAW -> RawConfigScreen()
            SettingsPage.STORAGE -> StorageScreen()
            SettingsPage.FILES -> FilesScreen()
            SettingsPage.MAIN -> Unit
        }
    }
}

@Composable
private fun SettingsMain(checks: SystemChecks, onOpen: (SettingsPage) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val store = RouterController.settings(context)
    val settings by store.settings.collectAsStateWithLifecycle()
    var choosingTimeout by rememberSaveable { mutableStateOf(false) }
    var choosingTheme by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        RestartBanner()
        Section(R.string.settings_section_router)
        TextRow(stringResource(R.string.router_basic), stringResource(R.string.router_basic_summary)) { onOpen(SettingsPage.ROUTER_BASIC) }
        TextRow(stringResource(R.string.router_advanced), stringResource(R.string.router_advanced_summary)) { onOpen(SettingsPage.ROUTER_ADVANCED) }
        TextRow(stringResource(R.string.router_raw), stringResource(R.string.router_raw_summary)) { onOpen(SettingsPage.ROUTER_RAW) }

        Section(R.string.settings_section_data)
        TextRow(stringResource(R.string.data_storage), stringResource(R.string.data_storage_summary)) { onOpen(SettingsPage.STORAGE) }
        TextRow(stringResource(R.string.data_files), stringResource(R.string.data_files_summary)) { onOpen(SettingsPage.FILES) }

        Section(R.string.settings_section_start)
        SwitchRow(R.string.settings_autostart, R.string.settings_autostart_summary, settings.autostartOnBoot) { value ->
            store.update { it.copy(autostartOnBoot = value) }
        }
        SwitchRow(R.string.settings_auto_restart, R.string.settings_auto_restart_summary, settings.autoRestart) { value ->
            store.update { it.copy(autoRestart = value) }
        }

        Section(R.string.settings_section_stop)
        TextRow(
            title = stringResource(R.string.settings_graceful_timeout),
            summary = stringResource(R.string.settings_minutes, settings.gracefulTimeoutMinutes),
            onClick = { choosingTimeout = true },
        )

        Section(R.string.settings_section_background)
        SwitchRow(R.string.settings_keep_awake, R.string.settings_keep_awake_summary, settings.keepAwake) { value ->
            store.update { it.copy(keepAwake = value) }
        }
        TextRow(
            title = stringResource(R.string.settings_battery),
            summary = stringResource(if (checks.batteryOptimized) R.string.settings_battery_on else R.string.settings_battery_off),
            onClick = if (checks.batteryOptimized) ({ requestIgnoreBatteryOptimizations(context) }) else null,
        )

        Section(R.string.settings_section_interface)
        TextRow(stringResource(R.string.settings_theme), stringResource(settings.theme.label)) { choosingTheme = true }
        SwitchRow(R.string.settings_secure_screen, R.string.settings_secure_screen_summary, settings.secureScreen) { value ->
            store.update { it.copy(secureScreen = value) }
        }

        Section(R.string.settings_section_about)
        TextRow(
            title = stringResource(R.string.app_name),
            summary = stringResource(R.string.about_versions, BuildConfig.VERSION_NAME, I2pdNative.version(), I2pdNative.abi()),
        )
    }

    if (choosingTimeout) {
        ChoiceDialog(
            title = R.string.settings_graceful_timeout,
            choices = AppSettingsStore.GRACEFUL_TIMEOUT_CHOICES,
            selected = settings.gracefulTimeoutMinutes,
            label = { stringResource(R.string.settings_minutes, it) },
            onSelect = { minutes -> store.update { it.copy(gracefulTimeoutMinutes = minutes) } },
            onDismiss = { choosingTimeout = false },
        )
    }
    if (choosingTheme) {
        ChoiceDialog(
            title = R.string.settings_theme,
            choices = ThemeMode.entries,
            selected = settings.theme,
            label = { stringResource(it.label) },
            onSelect = { theme -> store.update { it.copy(theme = theme) } },
            onDismiss = { choosingTheme = false },
        )
    }
}

private val ThemeMode.label: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }

@Composable
private fun <T> ChoiceDialog(
    title: Int,
    choices: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                choices.forEach { choice ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = choice == selected, role = Role.RadioButton) {
                            onSelect(choice)
                            onDismiss()
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == selected, onClick = null)
                        Text(label(choice), Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun Section(title: Int) {
    HorizontalDivider()
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun SwitchRow(title: Int, summary: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun TextRow(title: String, summary: String, onClick: (() -> Unit)? = null) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(Modifier.fillMaxWidth().then(clickable).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
