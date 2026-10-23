package org.purplei2p.i2pd

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle as AndroidLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.purplei2p.i2pd.config.TunnelInfo
import org.purplei2p.i2pd.service.RouterController
import org.purplei2p.i2pd.service.RouterStateText
import org.purplei2p.i2pd.service.TorrentAdded
import org.purplei2p.i2pd.service.TorrentItem
import org.purplei2p.i2pd.service.TorrentStatus
import org.purplei2p.i2pd.service.TorrentsRpc
import org.purplei2p.i2pd.service.TorrentsStats
import java.io.IOException
import org.purplei2p.i2pd.service.R as ServiceR

private class TorrentFileTooLarge : IOException()

/** .torrent files larger than this are not real torrent metadata. */
private const val MAX_TORRENT_FILE = 10L * 1024 * 1024

/**
 * Torrents of a torrents tunnel through its RPC server: list with progress, adding by a magnet link or
 * a .torrent file, start, stop, reannounce and removal. Refreshed every two seconds while visible.
 */
@Composable
fun TorrentsScreen(tunnel: TunnelInfo, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rpc = remember(tunnel.values) { TorrentsRpc.forTunnel(tunnel) }
    val router by RouterController.state(context).collectAsStateWithLifecycle()
    val running = router.lifecycle.hasRouterProcess

    var torrents by remember { mutableStateOf<List<TorrentItem>?>(null) }
    var stats by remember { mutableStateOf<TorrentsStats?>(null) }
    var dhtNodes by remember { mutableStateOf<Int?>(null) }
    var unreachable by remember { mutableStateOf<String?>(null) }
    // Resource and its argument, turned into text when drawn (follows configuration changes)
    var message by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var magnet by rememberSaveable { mutableStateOf("") }
    var removing by remember { mutableStateOf<TorrentItem?>(null) }
    var exporting by remember { mutableStateOf<TorrentItem?>(null) }
    val exports by TorrentExports.state.collectAsStateWithLifecycle()

    suspend fun refresh() {
        val client = rpc ?: return
        try {
            torrents = client.torrents().sortedBy { it.name.lowercase() }
            stats = client.stats()
            dhtNodes = runCatching { client.dhtNodes() }.getOrNull()
            unreachable = null
        } catch (e: IOException) {
            unreachable = e.message ?: e.toString()
        }
    }

    /** Runs an RPC action, reports its error and refreshes the list. */
    fun action(block: suspend (TorrentsRpc) -> Unit) {
        val client = rpc ?: return
        scope.launch {
            try {
                block(client)
            } catch (e: TorrentFileTooLarge) {
                message = R.string.torrents_file_too_large to ""
            } catch (e: IOException) {
                message = R.string.torrents_failed to (e.message ?: e.toString())
            }
            refresh()
        }
    }

    fun reportAdded(added: TorrentAdded) {
        message = (if (added.duplicate) R.string.torrents_duplicate else R.string.torrents_added) to added.name
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        action { client ->
            val content = readTorrentFile(context, uri) ?: throw TorrentFileTooLarge()
            reportAdded(client.addTorrentFile(content))
        }
    }

    // "Save to…": the torrent is copied into the picked folder and keeps seeding where it is
    val pickTarget = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        val torrent = exporting ?: return@rememberLauncherForActivityResult
        exporting = null
        tree ?: return@rememberLauncherForActivityResult
        val dir = java.io.File(tunnel.values["torrentsdir"].orEmpty())
        action { client ->
            val files = client.files(torrent.id)
            if (files.isEmpty()) throw IOException(torrent.name)
            TorrentExports.start(context, TorrentExports.key(tunnel.name, torrent.name), dir, files, tree)
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(rpc, running) {
        if (!running) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(AndroidLifecycle.State.RESUMED) {
            while (true) {
                refresh()
                delay(2000)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            Text(tunnel.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // All downloads of the tunnel at once; each button only when it changes something
                    val all = torrents.orEmpty()
                    if (running && all.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = all.any { it.status == TorrentStatus.STOPPED },
                                onClick = { action { it.startAll() } },
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.torrents_start_all)) }
                            OutlinedButton(
                                enabled = all.any { it.status != TorrentStatus.STOPPED },
                                onClick = { action { it.stopAll() } },
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.torrents_stop_all)) }
                        }
                    }
                    OutlinedTextField(
                        value = magnet,
                        onValueChange = { magnet = it.trim() },
                        label = { Text(stringResource(R.string.torrents_magnet)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = running && magnet.startsWith("magnet:", ignoreCase = true),
                            onClick = {
                                val link = magnet
                                action { client ->
                                    reportAdded(client.addMagnet(link))
                                    magnet = ""
                                }
                            },
                        ) { Text(stringResource(R.string.torrents_add)) }
                        OutlinedButton(enabled = running, onClick = { pickFile.launch(arrayOf("application/x-bittorrent", "application/octet-stream", "*/*")) }) {
                            Text(stringResource(R.string.torrents_add_file))
                        }
                    }
                    message?.let { (text, argument) -> Text(stringResource(text, argument), style = MaterialTheme.typography.bodyMedium) }
                    when {
                        !running -> Text(stringResource(R.string.torrents_router_stopped), color = MaterialTheme.colorScheme.error)
                        unreachable != null -> Text(stringResource(R.string.torrents_unreachable, unreachable!!), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            val list = torrents
            if (running && list != null && list.isEmpty()) {
                item {
                    Text(stringResource(R.string.torrents_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
                }
            }
            if (running && list != null) {
                items(list, key = { it.id }) { torrent ->
                    TorrentCard(
                        torrent,
                        onStart = { action { it.start(listOf(torrent.id)) } },
                        onStop = { action { it.stop(listOf(torrent.id)) } },
                        onReannounce = { action { it.reannounce(listOf(torrent.id)) } },
                        onRemove = { removing = torrent },
                        onSave = {
                            exporting = torrent
                            pickTarget.launch(null)
                        },
                        export = exports[TorrentExports.key(tunnel.name, torrent.name)],
                        onCancelExport = { TorrentExports.cancel(TorrentExports.key(tunnel.name, torrent.name)) },
                        onDismissExport = { TorrentExports.dismiss(TorrentExports.key(tunnel.name, torrent.name)) },
                    )
                }
            }
            if (running) {
                stats?.let { s ->
                    item {
                        Column(Modifier.padding(top = 16.dp)) {
                            Text(
                                stringResource(
                                    R.string.torrents_stats, s.torrentCount, s.activeCount,
                                    RouterStateText.speed(context, s.downloadSpeed), RouterStateText.speed(context, s.uploadSpeed),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            dhtNodes?.let {
                                Text(stringResource(R.string.torrents_dht, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    removing?.let { torrent ->
        var deleteData by rememberSaveable { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { removing = null },
            text = {
                Column {
                    Text(stringResource(R.string.torrents_remove_confirm, torrent.name))
                    Row(
                        Modifier.fillMaxWidth().toggleable(deleteData, role = Role.Checkbox) { deleteData = it }.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = deleteData, onCheckedChange = null)
                        Text(stringResource(R.string.torrents_remove_data), Modifier.padding(start = 8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    val withData = deleteData
                    action { it.remove(listOf(torrent.id), withData) }
                }) { Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

@Composable
private fun TorrentCard(
    torrent: TorrentItem,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReannounce: () -> Unit,
    onRemove: () -> Unit,
    onSave: () -> Unit,
    export: TorrentExports.Progress?,
    onCancelExport: () -> Unit,
    onDismissExport: () -> Unit,
) {
    val context = LocalContext.current
    val gettingMetadata = torrent.metadataPercent < 1.0
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(torrent.name, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            LinearProgressIndicator(progress = { torrent.percentDone.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Text(
                listOf(
                    stringResource(if (gettingMetadata) R.string.torrents_metadata else torrent.status.label),
                    "${(torrent.percentDone * 100).toInt()}%",
                    RouterStateText.bytes(context, torrent.sizeWhenDone),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                buildList {
                    add("↓ ${RouterStateText.speed(context, torrent.rateDownload)}  ↑ ${RouterStateText.speed(context, torrent.rateUpload)}")
                    add(stringResource(R.string.torrents_peers, torrent.peersConnected, torrent.peersSendingToUs, torrent.peersGettingFromUs))
                    if (torrent.eta >= 0 && torrent.leftUntilDone > 0) add(stringResource(R.string.torrents_eta, RouterStateText.duration(torrent.eta)))
                    if (torrent.uploadRatio > 0) add(stringResource(R.string.torrents_ratio, torrent.uploadRatio))
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            torrent.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            export?.let { ExportProgress(it, onCancelExport, onDismissExport) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (torrent.status == TorrentStatus.STOPPED) {
                    TextButton(onClick = onStart) { Text(stringResource(ServiceR.string.action_start)) }
                } else {
                    TextButton(onClick = onStop) { Text(stringResource(ServiceR.string.action_stop)) }
                }
                TextButton(onClick = onReannounce) { Text(stringResource(R.string.torrents_reannounce)) }
                val finished = !gettingMetadata && torrent.sizeWhenDone > 0 && torrent.leftUntilDone == 0L
                if (finished && (export == null || export.done || export.error != null)) {
                    TextButton(onClick = onSave) { Text(stringResource(R.string.torrents_save_to)) }
                }
                TextButton(onClick = onRemove) { Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun ExportProgress(export: TorrentExports.Progress, onCancel: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sizes = "${RouterStateText.bytes(context, export.copied)} / ${RouterStateText.bytes(context, export.total)}"
    when {
        export.error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.torrents_save_failed, export.error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
        }
        export.done -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.torrents_saved), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
        }
        else -> {
            LinearProgressIndicator(
                progress = { if (export.total > 0) (export.copied.toFloat() / export.total).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.torrents_saving, sizes), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) }
            }
        }
    }
}

private val TorrentStatus.label: Int
    get() = when (this) {
        TorrentStatus.STOPPED -> R.string.torrent_status_stopped
        TorrentStatus.QUEUED_TO_VERIFY -> R.string.torrent_status_queued_verify
        TorrentStatus.VERIFYING -> R.string.torrent_status_verifying
        TorrentStatus.QUEUED_TO_DOWNLOAD -> R.string.torrent_status_queued_download
        TorrentStatus.DOWNLOADING -> R.string.torrent_status_downloading
        TorrentStatus.QUEUED_TO_SEED -> R.string.torrent_status_queued_seed
        TorrentStatus.SEEDING -> R.string.torrent_status_seeding
    }

/** Content of a picked .torrent file, null when it is larger than any torrent metadata. */
private suspend fun readTorrentFile(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
    val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot read $uri")
    input.use {
        val bytes = it.readNBytesCompat(MAX_TORRENT_FILE + 1)
        bytes.takeIf { b -> b.size <= MAX_TORRENT_FILE }
    }
}

private fun java.io.InputStream.readNBytesCompat(limit: Long): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (total < limit) {
        val read = read(buffer, 0, minOf(buffer.size.toLong(), limit - total).toInt())
        if (read < 0) break
        out.write(buffer, 0, read)
        total += read
    }
    return out.toByteArray()
}
