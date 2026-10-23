package org.purplei2p.i2pd

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.purplei2p.i2pd.config.TorrentTrackers
import org.purplei2p.i2pd.config.TunnelFile
import org.purplei2p.i2pd.config.TunnelInfo
import org.purplei2p.i2pd.config.TunnelParam
import org.purplei2p.i2pd.config.TunnelParamKind
import org.purplei2p.i2pd.config.TunnelParams
import org.purplei2p.i2pd.config.TunnelType
import org.purplei2p.i2pd.service.DestinationAddress
import org.purplei2p.i2pd.service.RouterController
import org.purplei2p.i2pd.service.RouterTunnels
import org.purplei2p.i2pd.service.TunnelSet

/** Form for a tunnel of [file], or for a new tunnel when [info] is null. */
@Composable
internal fun TunnelForm(
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
            // A new torrents tunnel downloads into the media folder of the app, on a memory card if there is one
            if (info == null && type == TunnelType.TORRENTS) {
                put("torrentsdir", TorrentsStorage.defaultDir(context))
            }
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
        mainParams.forEach { param ->
            ParamField(type, param, values, paramErrors[param.key])
            if (type == TunnelType.TORRENTS && param.key == "torrentsdir") TorrentsDirControls(values)
            if (type == TunnelType.TORRENTS && param.key == "trackers") TrackerButtons(values)
            if (type == TunnelType.TORRENTS && param.key == "rpcport") RpcPortHint(values)
        }

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

/** Without an RPC port the torrents screen cannot connect: says so and offers 9191 with a tap. */
@Composable
private fun RpcPortHint(values: MutableMap<String, String>) {
    if ((values["rpcport"]?.toIntOrNull() ?: 0) > 0) return
    Text(stringResource(R.string.tunnels_rpcport_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.tunnels_rpcport_suggest), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { values["rpcport"] = SUGGESTED_RPC_PORT }) { Text(SUGGESTED_RPC_PORT) }
    }
}

private const val SUGGESTED_RPC_PORT = "9191"

/** One button per well-known tracker not in the list yet; a tap appends it ("a,b,c", no spaces). */
@Composable
private fun TrackerButtons(values: MutableMap<String, String>) {
    val missing = TorrentTrackers.missing(values["trackers"].orEmpty())
    if (missing.isEmpty()) return
    Text(stringResource(R.string.tunnels_add_tracker), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        missing.forEach { (name, url) ->
            AssistChip(
                onClick = { values["trackers"] = TorrentTrackers.add(values["trackers"].orEmpty(), url) },
                label = { Text("+ $name") },
            )
        }
    }
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

private fun createTorrentsDir(path: String) {
    val dir = java.io.File(path)
    if (!dir.isDirectory && !dir.mkdirs()) throw java.io.IOException("Cannot create $path")
}
