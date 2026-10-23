package org.purplei2p.i2pd

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.purplei2p.i2pd.config.CommentedTunnel
import org.purplei2p.i2pd.config.TunnelFile
import org.purplei2p.i2pd.config.TunnelInfo
import org.purplei2p.i2pd.config.TunnelIssue
import org.purplei2p.i2pd.config.TunnelType
import org.purplei2p.i2pd.service.RouterController
import org.purplei2p.i2pd.service.RouterTunnels
import org.purplei2p.i2pd.service.TunnelSet

/** Where the tunnels tab is: the list, a tunnel form (existing or new) or a raw file editor. */
internal data class TunnelsPage(val kind: Kind, val path: String = "", val name: String = "", val type: String = "") {
    enum class Kind { LIST, EDIT, NEW, RAW, TORRENTS }

    companion object {
        val LIST = TunnelsPage(Kind.LIST)
        val Saver = listSaver<TunnelsPage, String>(
            save = { listOf(it.kind.name, it.path, it.name, it.type) },
            restore = { TunnelsPage(Kind.valueOf(it[0]), it[1], it[2], it[3]) },
        )
    }
}

/** Pages of the tunnels tab that other tabs open, see TunnelsScreen. */
object TunnelsTarget {
    /** The torrents client of a tunnel. */
    fun torrents(path: String, name: String) = listOf(TunnelsPage.Kind.TORRENTS.name, path, name).joinToString("\n")

    /** The form of a tunnel. */
    fun edit(path: String, name: String) = listOf(TunnelsPage.Kind.EDIT.name, path, name).joinToString("\n")

    internal fun pageOf(target: String): TunnelsPage? {
        val parts = target.split('\n')
        if (parts.size != 3) return null
        val kind = TunnelsPage.Kind.entries.firstOrNull { it.name == parts[0] } ?: return null
        return TunnelsPage(kind, parts[1], parts[2])
    }
}

/** Loaded tunnel files, reloaded after every save. */
internal class TunnelsSession(val tunnels: RouterTunnels, private val scope: CoroutineScope) {
    var set by mutableStateOf<TunnelSet?>(null)
        private set
    var error by mutableStateOf<Throwable?>(null)

    suspend fun reload() {
        try {
            set = tunnels.load()
            error = null
        } catch (e: Exception) {
            error = e
        }
    }

    /** Runs a change and reports errors; screens reload by the repository revision. */
    fun run(block: suspend () -> Unit, onDone: () -> Unit = {}) {
        scope.launch {
            try {
                block()
                onDone()
            } catch (e: Exception) {
                error = e
            }
        }
    }
}

@Composable
fun TunnelsScreen(target: String?, onTargetOpened: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tunnels = RouterController.tunnels(context)
    val session = remember { TunnelsSession(tunnels, scope) }
    val revision by tunnels.revision.collectAsStateWithLifecycle()
    LaunchedEffect(revision) { session.reload() }
    var page by rememberSaveable(stateSaver = TunnelsPage.Saver) { mutableStateOf(TunnelsPage.LIST) }
    LaunchedEffect(target) {
        target?.let(TunnelsTarget::pageOf)?.let { page = it }
        if (target != null) onTargetOpened()
    }

    if (page.kind != TunnelsPage.Kind.LIST) BackHandler { page = TunnelsPage.LIST }

    Column(modifier.fillMaxSize()) {
        session.error?.let { failure ->
            Text(
                errorText(failure),
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
            TunnelsPage.Kind.TORRENTS -> {
                val info = set.file(page.path)?.get(page.name)
                if (info == null) LaunchedEffect(Unit) { page = TunnelsPage.LIST }
                else TorrentsScreen(info, onBack = { page = TunnelsPage.LIST })
            }
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
                                    onTorrents = { onOpen(TunnelsPage(TunnelsPage.Kind.TORRENTS, file.path, tunnel.name)) },
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
private fun TunnelRow(
    set: TunnelSet,
    file: TunnelFile,
    info: TunnelInfo,
    issues: List<TunnelIssue>,
    onClick: () -> Unit,
    onDisable: () -> Unit,
    onTorrents: () -> Unit,
) {
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
        // The client needs the RPC server: only with a port
        if (info.type == TunnelType.TORRENTS && (info.values["rpcport"]?.toIntOrNull() ?: 0) > 0) {
            Button(onClick = onTorrents, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.torrents_open)) }
        }
    }
}

@Composable
internal fun SharedDestinationNote(set: TunnelSet, info: TunnelInfo, short: Boolean = false) {
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
