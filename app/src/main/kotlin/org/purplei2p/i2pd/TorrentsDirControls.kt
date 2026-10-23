package org.purplei2p.i2pd

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.purplei2p.i2pd.TorrentsStorage.Access

/**
 * Under the torrentsdir field: the app folder on each volume, any folder through the system picker,
 * and what writing there needs (all files access, the storage permission) with a way to grant it.
 */
@Composable
internal fun TorrentsDirControls(values: MutableMap<String, String>) {
    val context = LocalContext.current
    val path = values["torrentsdir"].orEmpty()
    // Permissions are granted in system screens: checked again when the app comes back
    var resumed by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumed++
        onPauseOrDispose { }
    }
    val volumes = remember(resumed) { TorrentsStorage.volumes(context) }
    val access = remember(path, resumed) { path.takeIf { it.isNotEmpty() }?.let { TorrentsStorage.access(context, it) } }
    val mediaDir = remember(path, resumed) { path.isNotEmpty() && TorrentsStorage.isMediaDir(context, path) }
    var unsupported by remember { mutableStateOf(false) }
    val needsAccess = access == Access.NEEDS_ALL_FILES || access == Access.NEEDS_STORAGE || access == Access.IMPOSSIBLE
    // A folder i2pd cannot write to yet: a dialog to grant access or to switch to an app folder
    var dialog by rememberSaveable { mutableStateOf(false) }
    // The user came back without granting
    var denied by rememberSaveable { mutableStateOf(false) }
    // Sent to the all files access screen, the answer is known when the app comes back
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(resumed) {
        if (asked) {
            asked = false
            if (needsAccess) denied = true
        }
    }
    LaunchedEffect(needsAccess) {
        if (!needsAccess) {
            dialog = false
            denied = false
        }
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        tree ?: return@rememberLauncherForActivityResult
        val picked = TorrentsStorage.pathOfTree(context, tree)
        unsupported = picked == null
        if (picked != null) {
            values["torrentsdir"] = picked
            denied = false
            dialog = TorrentsStorage.access(context, picked).let { it != Access.OWN && it != Access.GRANTED }
        }
    }
    val requestStorage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        resumed++
        if (!granted) denied = true
    }
    val grant = {
        if (access == Access.NEEDS_STORAGE) {
            requestStorage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            asked = true
            try {
                context.startActivity(TorrentsStorage.allFilesAccessIntent(context))
            } catch (_: ActivityNotFoundException) {
                // Some firmwares only have the list of all apps
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }
    val volumeLabel: @Composable (TorrentsStorage.Volume) -> String = { volume ->
        volume.description ?: stringResource(if (volume.removable) R.string.torrents_dir_sd else R.string.torrents_dir_internal)
    }

    // One full-width button per line on phones, a single row of equal buttons on wide screens
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val buttons: @Composable (Modifier) -> Unit = { modifier ->
            volumes.forEach { volume ->
                OutlinedButton(onClick = { values["torrentsdir"] = volume.dir.absolutePath }, modifier = modifier) { Text(volumeLabel(volume)) }
            }
            OutlinedButton(onClick = { pickFolder.launch(null) }, modifier = modifier) { Text(stringResource(R.string.torrents_dir_pick)) }
        }
        if (maxWidth < WIDE_BUTTONS) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { buttons(Modifier.fillMaxWidth()) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { buttons(Modifier.weight(1f)) }
        }
    }
    if (unsupported) Text(stringResource(R.string.torrents_dir_unsupported), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    if (mediaDir) {
        Text(stringResource(R.string.torrents_dir_media), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val accessText = when {
        access == Access.IMPOSSIBLE -> R.string.torrents_dir_impossible
        denied -> R.string.torrents_access_denied
        access == Access.NEEDS_ALL_FILES -> R.string.torrents_dir_needs_all_files
        else -> R.string.torrents_dir_needs_storage
    }
    if (needsAccess) {
        // Stays under the field after the dialog is closed, a typed path gets no dialog
        Text(stringResource(accessText), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = { dialog = true }) { Text(stringResource(R.string.torrents_access_open)) }
    }
    if (dialog && needsAccess) {
        val canGrant = access != Access.IMPOSSIBLE
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text(stringResource(R.string.torrents_access_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(accessText))
                    if (denied || !canGrant) {
                        volumes.forEach { volume ->
                            OutlinedButton(
                                onClick = {
                                    values["torrentsdir"] = volume.dir.absolutePath
                                    dialog = false
                                    denied = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(volumeLabel(volume)) }
                        }
                    }
                }
            },
            confirmButton = {
                if (canGrant) TextButton(onClick = grant) { Text(stringResource(R.string.torrents_dir_grant)) }
                else TextButton(onClick = { dialog = false }) { Text(stringResource(android.R.string.cancel)) }
            },
            dismissButton = if (canGrant) {
                { TextButton(onClick = { dialog = false }) { Text(stringResource(android.R.string.cancel)) } }
            } else {
                null
            },
        )
    }
}

/** From this width the folder buttons stand in one row. */
private val WIDE_BUTTONS = 600.dp
