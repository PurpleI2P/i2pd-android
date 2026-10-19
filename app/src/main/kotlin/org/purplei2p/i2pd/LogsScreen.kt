package org.purplei2p.i2pd

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.purplei2p.i2pd.service.LogEntry
import org.purplei2p.i2pd.service.LogLevel
import org.purplei2p.i2pd.service.RouterController
import java.io.File

private val FILTER_LEVELS = listOf(
    LogLevel.ERROR to R.string.logs_level_error,
    LogLevel.WARN to R.string.logs_level_warn,
    LogLevel.INFO to R.string.logs_level_info,
    LogLevel.DEBUG to R.string.logs_level_debug,
)

@Composable
fun LogsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val collector = RouterController.logs(context)
    val entries by collector.entries.collectAsStateWithLifecycle()
    var maxLevel by rememberSaveable { mutableStateOf(LogLevel.DEBUG) }
    var query by rememberSaveable { mutableStateOf("") }

    val shown = remember(entries, maxLevel, query) {
        entries.filter { entry ->
            when (entry) {
                is LogEntry.Line -> (entry.level?.let { it <= maxLevel } ?: true) &&
                    (query.isBlank() || entry.text.contains(query, ignoreCase = true))
                else -> query.isBlank()
            }
        }
    }

    val listState = rememberLazyListState()
    // Follow new lines while the list is scrolled to its end
    val atEnd = !listState.canScrollForward
    LaunchedEffect(shown.size) {
        if (atEnd && shown.isNotEmpty()) listState.scrollToItem(shown.lastIndex)
    }

    Column(modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FILTER_LEVELS.forEach { (level, label) ->
                FilterChip(selected = maxLevel == level, onClick = { maxLevel = level }, label = { Text(stringResource(label)) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.logs_search)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { shareLog(context, shown) }, enabled = shown.isNotEmpty()) {
                Text(stringResource(R.string.action_share))
            }
            TextButton(onClick = { collector.clear() }, enabled = entries.isNotEmpty()) {
                Text(stringResource(R.string.action_clear))
            }
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.logs_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(shown, key = { it.id }) { LogRow(it) }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    when (entry) {
        is LogEntry.Line -> Text(
            text = listOfNotNull(entry.time, entry.text).joinToString(" "),
            style = style,
            color = levelColor(entry.level),
            modifier = Modifier.padding(vertical = 1.dp),
        )
        is LogEntry.RouterRestarted -> Marker(stringResource(R.string.logs_router_restarted))
        is LogEntry.Skipped -> Marker(pluralStringResource(R.plurals.logs_skipped, entry.count.toInt(), entry.count))
    }
}

@Composable
private fun Marker(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun levelColor(level: LogLevel?): Color = when (level) {
    LogLevel.NONE, LogLevel.CRITICAL, LogLevel.ERROR -> MaterialTheme.colorScheme.error
    LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
    LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    LogLevel.INFO, null -> MaterialTheme.colorScheme.onSurface
}

/** Writes the shown lines to a cache file and offers it to other apps. */
private fun shareLog(context: Context, entries: List<LogEntry>) {
    val dir = File(context.cacheDir, "logs").apply { mkdirs() }
    val file = File(dir, "i2pd-log.txt")
    file.bufferedWriter().use { out ->
        entries.forEach { entry ->
            when (entry) {
                is LogEntry.Line -> out.appendLine(listOfNotNull(entry.time, entry.level?.name?.lowercase(), entry.text).joinToString(" "))
                is LogEntry.RouterRestarted -> out.appendLine(context.getString(R.string.logs_router_restarted))
                is LogEntry.Skipped -> out.appendLine(context.resources.getQuantityString(R.plurals.logs_skipped, entry.count.toInt(), entry.count))
            }
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.logs_share_subject))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}
