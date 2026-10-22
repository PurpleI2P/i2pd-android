package org.purplei2p.i2pd

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.purplei2p.i2pd.config.CommentedTunnel
import org.purplei2p.i2pd.config.TunnelFile
import org.purplei2p.i2pd.config.TunnelInfo
import org.purplei2p.i2pd.config.TunnelIssue
import org.purplei2p.i2pd.config.TunnelParam
import org.purplei2p.i2pd.config.TunnelParamKind
import org.purplei2p.i2pd.config.TunnelParams
import org.purplei2p.i2pd.config.TunnelType
import org.purplei2p.i2pd.service.DestinationAddress
import org.purplei2p.i2pd.service.RouterController
import org.purplei2p.i2pd.service.RouterTunnels
import org.purplei2p.i2pd.service.TunnelSet

/** Where the tunnels tab is: the list, a tunnel form (existing or new) or a raw file editor. */
private data class TunnelsPage(val kind: Kind, val path: String = "", val name: String = "", val type: String = "") {
    enum class Kind { LIST, EDIT, NEW, RAW }

    companion object {
        val LIST = TunnelsPage(Kind.LIST)
        val Saver = listSaver<TunnelsPage, String>(
            save = { listOf(it.kind.name, it.path, it.name, it.type) },
            restore = { TunnelsPage(Kind.valueOf(it[0]), it[1], it[2], it[3]) },
        )
    }
}

/** Loaded tunnel files, reloaded after every save. */
private class TunnelsSession(val tunnels: RouterTunnels, private val scope: CoroutineScope) {
    var set by mutableStateOf<TunnelSet?>(null)
        private set
    var error by mutableStateOf<String?>(null)

    suspend fun reload() {
        try {
            set = tunnels.load()
            error = null
        } catch (e: Exception) {
            error = e.message ?: e.toString()
        }
    }

    /** Runs a change and reports errors; screens reload by the repository revision. */
    fun run(block: suspend () -> Unit, onDone: () -> Unit = {}) {
        scope.launch {
            try {
                block()
                onDone()
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            }
        }
    }
}

@Composable
fun TunnelsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tunnels = RouterController.tunnels(context)
    val session = remember { TunnelsSession(tunnels, scope) }
    val revision by tunnels.revision.collectAsStateWithLifecycle()
    LaunchedEffect(revision) { session.reload() }
    var page by rememberSaveable(stateSaver = TunnelsPage.Saver) { mutableStateOf(TunnelsPage.LIST) }

    if (page.kind != TunnelsPage.Kind.LIST) BackHandler { page = TunnelsPage.LIST }

    Column(modifier.fillMaxSize()) {
        session.error?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.action_dismiss)) { session.error = null }.padding(16.dp),
            )
        }
        val set = session.set
        if (set == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.config_loading)) }
            return@Column
        }
        when (page.kind) {
            TunnelsPage.Kind.LIST -> TunnelList(set, session, onOpen = { page = it })
            TunnelsPage.Kind.EDIT -> {
                val file = set.file(page.path)
                val info = file?.get(page.name)
                if (file == null || info == null) {
                    LaunchedEffect(Unit) { page = TunnelsPage.LIST }
                } else {
                    TunnelForm(set, session, file, info, info.type ?: TunnelType.CLIENT, onClose = { page = TunnelsPage.LIST })
                }
            }
            TunnelsPage.Kind.NEW -> TunnelForm(
                set, session, null, null, TunnelType.of(page.type) ?: TunnelType.CLIENT,
                onClose = { page = TunnelsPage.LIST },
                // Empty path: a new file in tunnels.d
                target = page.path.takeIf { it.isNotEmpty() }?.let { set.file(it) },
            )
            TunnelsPage.Kind.RAW -> TunnelFileEditor(session, page.path, onClose = { page = TunnelsPage.LIST })
        }
    }
}

@Composable
private fun ApplyBanner() {
    val context = LocalContext.current
    val flow = remember { RouterController.tunnelsPendingApply(context) }
    val pending by flow.collectAsStateWithLifecycle(false)
    if (!pending) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(stringResource(R.string.tunnels_apply_banner), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { RouterController.reloadTunnels(context) }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.tunnels_apply))
            }
        }
    }
}

@Composable
private fun TunnelList(set: TunnelSet, session: TunnelsSession, onOpen: (TunnelsPage) -> Unit) {
    // Type dialog for a new tunnel: "" — new file in tunnels.d, otherwise the file to append it to
    var choosingTypeFor by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    // First tunnel of a shared destination asked to be switched off: (file, tunnel)
    var disablingPrimary by remember { mutableStateOf<Pair<String, TunnelInfo>?>(null) }
    val issues = set.issues

    // Banners stay outside the lazy list: an item growing from zero height would be scrolled out of view
    Column(Modifier.fillMaxSize()) {
        ApplyBanner()
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            if (set.files.all { it.tunnels.isEmpty() }) {
                item { Text(stringResource(R.string.tunnels_empty), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(set.files, key = { it.path }) { file ->
                val fileIssues = issues.filter { it.file == file.path }
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Row(
                            Modifier.fillMaxWidth()
                                .toggleable(file.enabled, role = Role.Switch) { on -> session.run({ session.tunnels.setEnabled(file.path, on) }) }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(file.path.removeSuffix(RouterTunnels.DISABLED), fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                            Switch(checked = file.enabled, onCheckedChange = null)
                        }
                        if (!file.enabled) {
                            Text(
                                stringResource(R.string.tunnels_file_disabled),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        fileIssues.filter { it.tunnel == null }.forEach { IssueText(it) }
                        // Working and commented out tunnels in the order of the file
                        val rows = file.tunnels.map { it.line to it } + file.commentedTunnels.map { it.line to it }
                        rows.sortedBy { it.first }.forEach { (_, tunnel) ->
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            when (tunnel) {
                                is TunnelInfo -> TunnelRow(
                                    set, file, tunnel, fileIssues.filter { it.tunnel == tunnel.name },
                                    onClick = { onOpen(TunnelsPage(TunnelsPage.Kind.EDIT, file.path, tunnel.name)) },
                                    onDisable = {
                                        if (tunnel.dependents.isNotEmpty()) disablingPrimary = file.path to tunnel
                                        else session.run({ session.tunnels.setTunnelEnabled(file.path, tunnel.name, false) })
                                    },
                                )
                                is CommentedTunnel -> CommentedTunnelRow(tunnel) {
                                    session.run({ session.tunnels.setTunnelEnabled(file.path, tunnel.name, true) })
                                }
                            }
                        }
                        FlowRow(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(onClick = { choosingTypeFor = file.path }) { Text(stringResource(R.string.tunnels_add)) }
                            TextButton(onClick = { onOpen(TunnelsPage(TunnelsPage.Kind.RAW, file.path)) }) { Text(stringResource(R.string.tunnels_edit_file)) }
                            if (file.path.removeSuffix(RouterTunnels.DISABLED) != RouterTunnels.MAIN_FILE) {
                                TextButton(onClick = { deleting = file.path }) {
                                    Text(stringResource(R.string.tunnels_delete_file), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Button(onClick = { choosingTypeFor = "" }, modifier = Modifier.padding(16.dp)) { Text(stringResource(R.string.tunnels_add_file)) }
            }
        }
    }

    choosingTypeFor?.let { target ->
        AlertDialog(
            onDismissRequest = { choosingTypeFor = null },
            title = { Text(stringResource(R.string.tunnels_choose_type)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TunnelType.entries.filter { it.creatable }.forEach { type ->
                        Text(
                            stringResource(tunnelTypeLabel(type)),
                            modifier = Modifier.fillMaxWidth().clickable {
                                choosingTypeFor = null
                                onOpen(TunnelsPage(TunnelsPage.Kind.NEW, path = target, type = type.id))
                            }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingTypeFor = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    disablingPrimary?.let { (path, info) ->
        AlertDialog(
            onDismissRequest = { disablingPrimary = null },
            text = { Text(stringResource(R.string.tunnels_disable_primary, info.name, info.dependents.joinToString(", "))) },
            confirmButton = {
                TextButton(onClick = {
                    disablingPrimary = null
                    session.run({ session.tunnels.setTunnelEnabled(path, info.name, false) })
                }) { Text(stringResource(R.string.action_disable)) }
            },
            dismissButton = { TextButton(onClick = { disablingPrimary = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    deleting?.let { path ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.tunnels_delete_file_confirm, path)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    session.run({ session.tunnels.deleteFile(path) })
                }) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

/** A tunnel commented out in its file: shown where it is, switched on by removing the comments. */
@Composable
private fun CommentedTunnelRow(tunnel: CommentedTunnel, onEnable: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(tunnel.name, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                listOf(
                    tunnel.type?.let { stringResource(tunnelTypeLabel(it)) } ?: tunnel.values["type"].orEmpty(),
                    stringResource(R.string.tunnels_commented),
                ).filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = false, onCheckedChange = { onEnable() }, modifier = Modifier.semantics { contentDescription = tunnel.name })
    }
}

@Composable
private fun TunnelRow(set: TunnelSet, file: TunnelFile, info: TunnelInfo, issues: List<TunnelIssue>, onClick: () -> Unit, onDisable: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(info.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            // Switches only this tunnel off, by commenting out its section
            Switch(
                checked = true,
                onCheckedChange = { onDisable() },
                modifier = Modifier.semantics { contentDescription = info.name },
            )
        }
        Text(
            listOf(tunnelTypeText(info), tunnelSummary(info)).filter { it.isNotEmpty() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SharedDestinationNote(set, info, short = true)
        info.sharesDestinationWith?.let {
            Text(stringResource(R.string.tunnels_i2cp_from, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        issues.forEach { IssueText(it, padding = false) }
    }
}

@Composable
private fun SharedDestinationNote(set: TunnelSet, info: TunnelInfo, short: Boolean = false) {
    val keys = info.keys ?: return
    val users = set.sharedDestinations[keys] ?: return
    Text(
        if (short) {
            pluralStringResource(R.plurals.tunnels_shared_short, users.size, keys, users.size)
        } else {
            stringResource(R.string.tunnels_shared_destination, keys, users.map { destinationUserText(it) }.joinToString(", "))
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.tertiary,
    )
}

@Composable
private fun IssueText(issue: TunnelIssue, padding: Boolean = true) {
    Text(
        tunnelIssueText(issue),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = if (padding) Modifier.padding(horizontal = 16.dp) else Modifier,
    )
}

/** Form for a tunnel of [file], or for a new tunnel when [info] is null. */
@Composable
private fun TunnelForm(
    set: TunnelSet,
    session: TunnelsSession,
    file: TunnelFile?,
    info: TunnelInfo?,
    type: TunnelType,
    onClose: () -> Unit,
    /** File a new tunnel is appended to; null for a new file of its own. */
    target: TunnelFile? = null,
) {
    val context = LocalContext.current
    val formKeys = remember(type) { TunnelParams.formKeys(type) }
    var name by rememberSaveable { mutableStateOf(info?.name ?: "") }
    val values = remember {
        mutableStateMapOf<String, String>().apply {
            info?.values?.forEach { (k, v) -> if (k in formKeys) put(k, v) }
            // A new torrents tunnel downloads into the private data directory: no storage permission needed
            if (info == null && type == TunnelType.TORRENTS) put("torrentsdir", torrentsDirDefault(context))
        }
    }
    val others = remember { mutableStateListOf<Pair<String, String>>().apply { info?.values?.forEach { (k, v) -> if (k !in formKeys) add(k to v) } } }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val nameError = when {
        name.isBlank() || name.any { it == '[' || it == ']' || it == '\n' } -> R.string.tunnels_name_invalid
        file != null && name != info?.name && name in file.allNames -> R.string.tunnels_name_taken
        target != null && name in target.allNames -> R.string.tunnels_name_taken
        else -> null
    }
    val mainParams = TunnelParams.main(type)
    val allParams = TunnelParams.params(type) + TunnelParams.i2cp
    val paramErrors = allParams.associate { param ->
        val value = values[param.key].orEmpty()
        param.key to when {
            value.isEmpty() -> if (param.required) R.string.tissue_missing_param else null
            TunnelFile.validateParam(param, value) != null -> R.string.error_out_of_range
            else -> null
        }
    }
    val valid = nameError == null && paramErrors.values.all { it == null }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_back)) }
            Text(stringResource(tunnelTypeLabel(type)), style = MaterialTheme.typography.titleMedium)
        }
        if (info != null) {
            Text(
                "${file!!.path} · ${stringResource(R.string.tunnels_type_readonly)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        target?.let {
            Text(
                stringResource(R.string.tunnels_append_to, it.path.removeSuffix(RouterTunnels.DISABLED)) +
                    if (it.enabled) "" else " " + stringResource(R.string.tunnels_file_disabled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (type == TunnelType.WEBSOCKS) Text(stringResource(R.string.tunnels_websocks_deprecated), color = MaterialTheme.colorScheme.error)
        if (type == TunnelType.TORRENTS) {
            Text(stringResource(R.string.tunnels_torrents_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.tunnels_name)) },
            singleLine = true,
            isError = nameError != null,
            supportingText = { nameError?.let { Text(stringResource(it)) } },
            modifier = Modifier.fillMaxWidth(),
        )
        mainParams.forEach { param -> ParamField(type, param, values, paramErrors[param.key]) }

        val keys = values["keys"].orEmpty()
        if (keys.isNotEmpty() && TunnelFile.isSharedKeys(keys)) {
            val authType = others.firstOrNull { it.first == "i2cp.leaseSetAuthType" }?.second?.toIntOrNull() ?: 0
            AddressRow(keys, encrypted = values["i2cp.leaseSetType"] == "5", clientAuth = authType > 0)
        }
        info?.let {
            SharedDestinationNote(set, it.copy(values = it.values + ("keys" to keys)))
            set.sharedDestinations[keys]?.let { Text(stringResource(R.string.tunnels_shared_warning), style = MaterialTheme.typography.bodySmall) }
        }

        // Fine-tuning: collapsed, opened after "I know what I'm doing" (once per form for both groups)
        var expertConfirmed by rememberSaveable { mutableStateOf(false) }
        var pendingOpen by remember { mutableStateOf<(() -> Unit)?>(null) }
        val confirm: (() -> Unit) -> Unit = { open -> if (expertConfirmed) open() else pendingOpen = open }
        ParamGroup(R.string.tunnels_more_params, TunnelParams.advanced(type), type, values, paramErrors, confirm)
        val i2cpFrom = info?.sharesDestinationWith
        if (i2cpFrom != null) {
            Text(stringResource(R.string.tunnels_i2cp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.tunnels_i2cp_from, i2cpFrom), style = MaterialTheme.typography.bodySmall)
        } else {
            ParamGroup(R.string.tunnels_i2cp, TunnelParams.i2cp, type, values, paramErrors, confirm)
        }
        pendingOpen?.let { open ->
            AlertDialog(
                onDismissRequest = { pendingOpen = null },
                title = { Text(stringResource(R.string.tunnels_expert_title)) },
                text = { Text(stringResource(R.string.tunnels_expert_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        expertConfirmed = true
                        pendingOpen = null
                        open()
                    }) { Text(stringResource(R.string.tunnels_expert_confirm)) }
                },
                dismissButton = { TextButton(onClick = { pendingOpen = null }) { Text(stringResource(android.R.string.cancel)) } },
            )
        }

        Text(stringResource(R.string.tunnels_other), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
        others.toList().forEachIndexed { index, (key, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$key = $value", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { others.removeAt(index) }) { Text(stringResource(R.string.action_remove)) }
            }
        }
        AddParamRow(onAdd = { key, value -> others.removeAll { it.first == key }; others += key to value })

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = valid, onClick = {
                val form = allParams.map { it.key to values[it.key].orEmpty() }
                session.run({
                    // i2pd only reads .torrent files from the directory, it does not create it
                    if (type == TunnelType.TORRENTS) createTorrentsDir(values["torrentsdir"].orEmpty())
                    if (file == null || info == null) {
                        val newValues = listOf("type" to type.id) + form.filter { it.second.isNotEmpty() } + others
                        if (target != null) session.tunnels.append(target.path, name, newValues)
                        else session.tunnels.create(name, newValues)
                    } else {
                        saveTunnel(session.tunnels, file.path, info, name, form, others.toList(), formKeys)
                    }
                }, onDone = onClose)
            }) { Text(stringResource(R.string.action_save)) }
            if (file != null && info != null) {
                OutlinedButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.tunnels_delete)) }
            }
        }
    }

    if (confirmDelete && file != null && info != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = {
                Column {
                    Text(stringResource(R.string.tunnels_delete_confirm, info.name))
                    if (info.dependents.isNotEmpty()) {
                        Text(stringResource(R.string.tunnels_delete_primary, info.dependents.joinToString(", ")), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    session.run({ session.tunnels.deleteTunnel(file.path, info.name) }, onDone = onClose)
                }) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

/** Writes the form into the file: values set, cleared values removed, other parameters synced. */
private suspend fun saveTunnel(
    tunnels: RouterTunnels,
    path: String,
    info: TunnelInfo,
    name: String,
    form: List<Pair<String, String>>,
    others: List<Pair<String, String>>,
    formKeys: Set<String>,
) {
    val file = tunnels.load().file(path) ?: return
    if (name != info.name) file.document.renameSection(info.name, name)
    for ((key, value) in form) {
        if (value.isEmpty()) file.remove(name, key) else if (file[name]?.values?.get(key) != value) file.set(name, key, value)
    }
    val current = file[name]?.values.orEmpty().filterKeys { it !in formKeys }
    current.keys.filter { key -> others.none { it.first == key } }.forEach { file.remove(name, it) }
    for ((key, value) in others) if (current[key] != value) file.set(name, key, value)
    tunnels.save(file)
}

/**
 * Collapsed part of the form with fine-tuning parameters. Opening goes through [confirm] ("I know what
 * I'm doing"); it is open at once when one of its parameters is set or has an error, so nothing set stays hidden.
 */
@Composable
private fun ParamGroup(
    title: Int,
    params: List<TunnelParam>,
    type: TunnelType,
    values: MutableMap<String, String>,
    errors: Map<String, Int?>,
    confirm: (open: () -> Unit) -> Unit,
) {
    if (params.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(params.any { !values[it.key].isNullOrEmpty() || errors[it.key] != null }) }
    TextButton(onClick = { if (open) open = false else confirm { open = true } }) {
        Text((if (open) "▾ " else "▸ ") + stringResource(title))
    }
    if (open) params.forEach { param -> ParamField(type, param, values, errors[param.key]) }
}

@Composable
private fun ParamField(type: TunnelType, param: TunnelParam, values: MutableMap<String, String>, error: Int?) {
    val label = tunnelParamLabel(param.key, type)
    if (param.kind == TunnelParamKind.BOOL) {
        val checked = (values[param.key] ?: param.defaultFor(type)).let { it == "true" || it == "1" }
        Row(
            Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch) { values[param.key] = it.toString() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = null)
        }
        return
    }
    val numeric = param.kind == TunnelParamKind.PORT || param.kind == TunnelParamKind.NUMBER
    OutlinedTextField(
        value = values[param.key].orEmpty(),
        onValueChange = { values[param.key] = it.trim() },
        label = { Text(if (param.required) "$label *" else label) },
        placeholder = { param.defaultFor(type)?.takeIf { it.isNotEmpty() }?.let { Text(it) } },
        singleLine = true,
        isError = error != null,
        supportingText = { error?.let { if (it == R.string.tissue_missing_param) Text("*") else Text(stringResource(it)) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AddParamRow(onAdd: (String, String) -> Unit) {
    var key by rememberSaveable { mutableStateOf("") }
    var value by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(key, { key = it.trim() }, label = { Text(stringResource(R.string.tunnels_param_key)) }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(value, { value = it }, label = { Text(stringResource(R.string.tunnels_param_value)) }, singleLine = true, modifier = Modifier.weight(1f))
    }
    TextButton(enabled = key.isNotEmpty() && '=' !in key && ' ' !in key, onClick = {
        onAdd(key, value.trim())
        key = ""
        value = ""
    }) { Text(stringResource(R.string.tunnels_add_param)) }
}

@Composable
private fun AddressRow(keys: String, encrypted: Boolean, clientAuth: Boolean) {
    val context = LocalContext.current
    val address by produceState<DestinationAddress?>(null, keys, encrypted, clientAuth) {
        value = RouterController.tunnels(context).address(keys, encrypted, clientAuth)
    }
    Column {
        Text(stringResource(R.string.tunnels_address), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
        val current = address
        if (current == null) {
            Text(stringResource(R.string.tunnels_address_pending), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(current.b32, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { copyToClipboard(context, current.b32) }) { Text(stringResource(R.string.tunnels_copy)) }
            current.b33?.let { b33 ->
                Text(stringResource(R.string.tunnels_address_b33), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(b33, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { copyToClipboard(context, b33) }) { Text(stringResource(R.string.tunnels_copy)) }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("I2P address", text))
    Toast.makeText(context, R.string.tunnels_copied, Toast.LENGTH_SHORT).show()
}

@Composable
private fun TunnelFileEditor(session: TunnelsSession, path: String, onClose: () -> Unit) {
    val original = session.set?.file(path)?.document?.toString()
    var text by rememberSaveable(path) { mutableStateOf(original ?: "") }
    var issues by remember { mutableStateOf<List<TunnelIssue>>(emptyList()) }

    fun save() = session.run({ session.tunnels.saveText(path, text) }, onDone = onClose)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_back)) }
            Text(path, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                val found = TunnelFile.parse(path, text).validate()
                if (found.isEmpty()) save() else issues = found
            }) { Text(stringResource(R.string.action_save)) }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        )
    }
    if (issues.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { issues = emptyList() },
            title = { Text(stringResource(R.string.config_issues)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    issues.forEach { Text("• " + tunnelIssueText(it), style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    issues = emptyList()
                    save()
                }) { Text(stringResource(R.string.action_save_anyway)) }
            },
            dismissButton = { TextButton(onClick = { issues = emptyList() }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

/** Default download directory of a new torrents tunnel: torrents/downloads in the data directory, next to torrents/web. */
private fun torrentsDirDefault(context: android.content.Context): String =
    java.io.File(RouterController.data(context).root, "torrents/downloads").absolutePath

private fun createTorrentsDir(path: String) {
    val dir = java.io.File(path)
    if (!dir.isDirectory && !dir.mkdirs()) throw java.io.IOException("Cannot create $path")
}
