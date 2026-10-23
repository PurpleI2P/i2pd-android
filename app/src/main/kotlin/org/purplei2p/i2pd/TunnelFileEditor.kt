package org.purplei2p.i2pd

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.purplei2p.i2pd.config.TunnelFile
import org.purplei2p.i2pd.config.TunnelIssue

@Composable
internal fun TunnelFileEditor(session: TunnelsSession, path: String, onClose: () -> Unit) {
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
