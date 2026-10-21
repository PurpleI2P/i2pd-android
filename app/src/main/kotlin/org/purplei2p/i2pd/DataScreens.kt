package org.purplei2p.i2pd

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.purplei2p.i2pd.config.CategorySize
import org.purplei2p.i2pd.config.DataCategory
import org.purplei2p.i2pd.service.DataEntry
import org.purplei2p.i2pd.service.OemAutostart
import org.purplei2p.i2pd.service.RouterController
import org.purplei2p.i2pd.service.RouterData
import org.purplei2p.i2pd.service.RouterStateText

fun categoryTitle(category: DataCategory): Int = when (category) {
    DataCategory.ROUTER_IDENTITY -> R.string.data_cat_router_identity
    DataCategory.DESTINATION_KEYS -> R.string.data_cat_destination_keys
    DataCategory.CONFIG -> R.string.data_cat_config
    DataCategory.ADDRESSBOOK -> R.string.data_cat_addressbook
    DataCategory.NETDB -> R.string.data_cat_netdb
    DataCategory.PROFILES -> R.string.data_cat_profiles
    DataCategory.TAGS -> R.string.data_cat_tags
    DataCategory.CERTIFICATES -> R.string.data_cat_certificates
    DataCategory.OTHER -> R.string.data_cat_other
}

/** Outcome of a data operation, turned into text when shown. */
private sealed interface OperationMessage {
    data class Text(val id: Int, val args: List<Any> = emptyList()) : OperationMessage
    data class Failed(val error: String) : OperationMessage
}

@Composable
private fun OperationMessage.text(): String = when (this) {
    is OperationMessage.Text -> stringResource(id, *args.toTypedArray())
    is OperationMessage.Failed -> stringResource(R.string.data_failed, error)
}

private fun done(id: Int = R.string.data_done, vararg args: Any) = OperationMessage.Text(id, args.toList())

/** Result of the last data operation; operations are serialized through [run]. */
private class DataOperation(private val context: Context, private val scope: CoroutineScope) {
    var message by mutableStateOf<OperationMessage?>(null)
    var running by mutableStateOf(false)
        private set
    var revision by mutableIntStateOf(0)
        private set

    fun run(block: suspend RouterData.() -> OperationMessage) {
        if (running) return
        running = true
        scope.launch {
            message = try {
                RouterController.data(context).block()
            } catch (e: Exception) {
                OperationMessage.Failed(e.message ?: e.toString())
            }
            running = false
            revision++
        }
    }
}

@Composable
private fun rememberDataOperation(): DataOperation {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember { DataOperation(context, scope) }
}

/** Picker of the folder with the data of the old app, opened at /sdcard/i2pd when possible. */
@Composable
fun rememberMigrationLauncher(onPicked: (Uri) -> Unit) =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(onPicked) }

val OLD_DATA_DIR: Uri? =
    if (Build.VERSION.SDK_INT >= 26) DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:i2pd") else null

@Composable
fun StorageScreen() {
    val context = LocalContext.current
    val operation = rememberDataOperation()
    var sizes by remember { mutableStateOf<Map<DataCategory, CategorySize>>(emptyMap()) }
    LaunchedEffect(operation.revision) { sizes = RouterController.data(context).sizes() }

    // A category picker precedes backup, restore and migration
    var picking by remember { mutableStateOf<PendingTransfer?>(null) }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val categories = picking?.categories ?: return@rememberLauncherForActivityResult
        picking = null
        uri?.let { operation.run { done(R.string.data_done_backup, exportZip(it, categories)) } }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val categories = picking?.categories ?: return@rememberLauncherForActivityResult
        picking = null
        uri?.let { operation.run { importZip(it, categories).let { r -> done(R.string.data_done_import, r.imported.size, r.skipped.size) } } }
    }
    val migrate = rememberMigrationLauncher { uri ->
        val categories = picking?.categories ?: return@rememberMigrationLauncher
        picking = null
        operation.run { migrate(uri, categories).let { r -> done(R.string.data_done_import, r.imported.size, r.skipped.size) } }
    }
    var confirm by remember { mutableStateOf<StorageAction?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        operation.message?.let {
            Text(it.text(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.action_dismiss)) { operation.message = null }.padding(16.dp))
        }
        Text(
            stringResource(R.string.data_router_restarts),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(enabled = !operation.running, onClick = { picking = PendingTransfer(TransferKind.BACKUP, RouterData.BACKUP_DEFAULT) }) {
                Text(stringResource(R.string.data_backup))
            }
            TextButton(enabled = !operation.running, onClick = { picking = PendingTransfer(TransferKind.RESTORE, RouterData.BACKUP_DEFAULT) }) {
                Text(stringResource(R.string.data_restore))
            }
        }
        TextButton(
            enabled = !operation.running,
            onClick = { picking = PendingTransfer(TransferKind.MIGRATE, RouterData.MIGRATION_DEFAULT) },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) { Text(stringResource(R.string.data_migrate)) }
        Text(
            stringResource(R.string.data_migrate_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )

        DataCategory.entries.forEach { category ->
            HorizontalDivider()
            val size = sizes[category]
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(categoryTitle(category)), style = MaterialTheme.typography.bodyLarge)
                size?.let {
                    Text(
                        stringResource(R.string.data_size, RouterStateText.bytes(context, it.bytes), it.files),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row {
                    actionsFor(category).forEach { action ->
                        TextButton(
                            enabled = !operation.running && ((size?.files ?: 0) > 0 || action.kind == ActionKind.RESET),
                            onClick = { confirm = action },
                        ) {
                            Text(stringResource(action.label))
                        }
                    }
                }
            }
        }
    }

    picking?.let { pending ->
        if (pending.chosen) return@let
        CategoryDialog(
            initial = pending.categories,
            allowed = if (pending.kind == TransferKind.MIGRATE) DataCategory.entries.filter { it != DataCategory.OTHER } else DataCategory.entries,
            onDismiss = { picking = null },
            onConfirm = { categories ->
                picking = pending.copy(categories = categories, chosen = true)
                when (pending.kind) {
                    TransferKind.BACKUP -> backup.launch("i2pd-backup.zip")
                    TransferKind.RESTORE -> restore.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    TransferKind.MIGRATE -> migrate.launch(OLD_DATA_DIR)
                }
            },
        )
    }
    confirm?.let { action ->
        val title = stringResource(categoryTitle(action.category))
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if (action.kind == ActionKind.RESET) R.string.data_confirm_reset else R.string.data_confirm_clear, title))
                    when (action.category) {
                        DataCategory.NETDB -> Text(stringResource(R.string.data_confirm_netdb), style = MaterialTheme.typography.bodySmall)
                        DataCategory.ROUTER_IDENTITY -> Text(stringResource(R.string.data_confirm_identity), style = MaterialTheme.typography.bodySmall)
                        else -> Unit
                    }
                    Text(stringResource(R.string.data_router_restarts), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    operation.run {
                        if (action.kind == ActionKind.RESET) resetToBundled(action.category) else clear(setOf(action.category))
                        done()
                    }
                }) { Text(stringResource(action.label)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

private enum class TransferKind { BACKUP, RESTORE, MIGRATE }

private data class PendingTransfer(val kind: TransferKind, val categories: Set<DataCategory>, val chosen: Boolean = false)

private enum class ActionKind { CLEAR, RESET }

private data class StorageAction(val category: DataCategory, val kind: ActionKind, val label: Int)

private fun actionsFor(category: DataCategory): List<StorageAction> = when (category) {
    DataCategory.ROUTER_IDENTITY -> listOf(StorageAction(category, ActionKind.CLEAR, R.string.data_action_new_identity))
    DataCategory.CONFIG, DataCategory.CERTIFICATES -> listOf(StorageAction(category, ActionKind.RESET, R.string.data_action_reset))
    DataCategory.NETDB, DataCategory.PROFILES, DataCategory.TAGS, DataCategory.ADDRESSBOOK ->
        listOf(StorageAction(category, ActionKind.CLEAR, R.string.data_action_clear))
    // Keys and other files are handled one by one in the file browser
    DataCategory.DESTINATION_KEYS, DataCategory.OTHER -> emptyList()
}

@Composable
private fun CategoryDialog(initial: Set<DataCategory>, allowed: List<DataCategory>, onDismiss: () -> Unit, onConfirm: (Set<DataCategory>) -> Unit) {
    var chosen by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.data_choose_categories)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                allowed.forEach { category ->
                    Row(
                        Modifier.fillMaxWidth().toggleable(category in chosen, role = Role.Checkbox) { on -> chosen = if (on) chosen + category else chosen - category },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = category in chosen, onCheckedChange = null)
                        Text(stringResource(categoryTitle(category)), Modifier.padding(start = 8.dp, top = 12.dp, bottom = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = chosen.isNotEmpty(), onClick = { onConfirm(chosen) }) { Text(stringResource(R.string.data_continue)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
fun FilesScreen() {
    val context = LocalContext.current
    val operation = rememberDataOperation()
    var dir by rememberSaveable { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<DataEntry>>(emptyList()) }
    var selected by remember { mutableStateOf<DataEntry?>(null) }
    var deleting by remember { mutableStateOf<DataEntry?>(null) }
    LaunchedEffect(dir, operation.revision) { entries = RouterController.data(context).list(dir) }
    if (dir.isNotEmpty()) BackHandler { dir = dir.substringBeforeLast('/', "") }

    val upload = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { operation.run { done(R.string.files_uploaded, importFile(it, dir)) } }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val entry = selected ?: return@rememberLauncherForActivityResult
        selected = null
        uri?.let { operation.run { exportFile(entry.path, it).let { done() } } }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            operation.message?.let {
                Text(it.text(), modifier = Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.action_dismiss)) { operation.message = null }.padding(16.dp))
            }
            Text("/${dir}", fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Row(Modifier.padding(horizontal = 8.dp)) {
                TextButton(enabled = !operation.running, onClick = { upload.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.files_upload)) }
            }
            if (dir.isNotEmpty()) {
                Text(
                    stringResource(R.string.files_up),
                    modifier = Modifier.fillMaxWidth().clickable { dir = dir.substringBeforeLast('/', "") }.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            if (entries.isEmpty()) Text(stringResource(R.string.files_empty), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(entries, key = { it.path }) { entry ->
            Column(
                Modifier.fillMaxWidth()
                    .clickable { if (entry.isDirectory) dir = entry.path else selected = entry }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(if (entry.isDirectory) "${entry.name}/" else entry.name, fontFamily = FontFamily.Monospace)
                Text(RouterStateText.bytes(context, entry.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    selected?.let { entry ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(entry.name, fontFamily = FontFamily.Monospace) },
            text = { Text(RouterStateText.bytes(context, entry.size)) },
            confirmButton = { TextButton(onClick = { save.launch(entry.name) }) { Text(stringResource(R.string.files_save)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        deleting = entry
                        selected = null
                    }) { Text(stringResource(R.string.files_delete)) }
                    TextButton(onClick = { selected = null }) { Text(stringResource(android.R.string.cancel)) }
                }
            },
        )
    }
    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = {
                Column {
                    Text(stringResource(R.string.files_confirm_delete, entry.path))
                    Text(stringResource(R.string.data_router_restarts), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    operation.run {
                        delete(entry.path)
                        done()
                    }
                }) { Text(stringResource(R.string.files_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

/** First-run screen: notifications, battery optimization, migration from the old app, start. */
@Composable
fun OnboardingScreen(checks: SystemChecks, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val operation = rememberDataOperation()
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val migrate = rememberMigrationLauncher { uri ->
        operation.run { migrate(uri, RouterData.MIGRATION_DEFAULT).let { r -> done(R.string.data_done_import, r.imported.size, r.skipped.size) } }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AppHeader()
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.onboarding_intro))
        if (Build.VERSION.SDK_INT >= 33) {
            Step(stringResource(R.string.onboarding_notifications), done = checks.notificationsEnabled) {
                notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        Step(stringResource(R.string.onboarding_battery), done = !checks.batteryOptimized) { requestIgnoreBatteryOptimizations(context) }
        OemAutostart.current?.let { oem ->
            val store = RouterController.settings(context)
            val opened by store.settings.collectAsStateWithLifecycle()
            // Cannot be checked: "done" only means the screen was opened
            Step(stringResource(R.string.onboarding_oem_autostart, oem.displayName), done = opened.oemAutostartOpened) {
                oem.open(context)
                store.update { it.copy(oemAutostartOpened = true) }
            }
        }
        Step(stringResource(R.string.onboarding_migrate), done = false, action = R.string.onboarding_migrate_action, enabled = !operation.running) {
            migrate.launch(OLD_DATA_DIR)
        }
        operation.message?.let { Text(it.text(), style = MaterialTheme.typography.bodyMedium) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                RouterController.start(context)
                onDone()
            }) { Text(stringResource(R.string.onboarding_start)) }
            TextButton(onClick = onDone) { Text(stringResource(R.string.onboarding_skip)) }
        }
    }
}

@Composable
private fun Step(text: String, done: Boolean, action: Int = R.string.onboarding_allow, enabled: Boolean = true, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f))
        if (done) {
            Text(stringResource(R.string.onboarding_done_step), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
        } else {
            TextButton(enabled = enabled, onClick = onClick) { Text(stringResource(action)) }
        }
    }
}
