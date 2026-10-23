package org.purplei2p.i2pd

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.purplei2p.i2pd.config.ConfigEditor
import org.purplei2p.i2pd.config.ConfigIssue
import org.purplei2p.i2pd.config.IniDocument
import org.purplei2p.i2pd.config.OptionGroup
import org.purplei2p.i2pd.config.OptionLevel
import org.purplei2p.i2pd.config.OptionOverlay
import org.purplei2p.i2pd.config.OptionSpec
import org.purplei2p.i2pd.config.OptionType
import org.purplei2p.i2pd.config.OptionUi
import org.purplei2p.i2pd.config.OptionWidget
import org.purplei2p.i2pd.service.RouterConfig
import org.purplei2p.i2pd.service.RouterController

/** Loaded i2pd.conf with edit operations; every edit is saved to the file at once. */
class ConfigSession(private val config: RouterConfig, private val scope: CoroutineScope) {
    var editor by mutableStateOf<ConfigEditor?>(null)
        private set
    var error by mutableStateOf<Throwable?>(null)
        private set

    fun reload() = run { config.load() }

    fun set(name: String, value: String) = run { config.update { it.set(name, value) } }

    fun reset(name: String) = run { config.update { it.reset(name) } }

    fun removeEntry(entry: IniDocument.EntryRef, commentOut: Boolean) =
        run { config.update { it.document.remove(entry.section, entry.key, commentOut) } }

    private fun run(block: suspend () -> ConfigEditor) {
        scope.launch {
            try {
                editor = block()
                error = null
            } catch (e: Exception) {
                error = e
            }
        }
    }
}

@Composable
fun rememberConfigSession(): ConfigSession {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config = RouterController.config(context)
    val session = remember { ConfigSession(config, scope) }
    // Reload whenever the file is saved, also from elsewhere (revert, raw editor)
    LaunchedEffect(session) { config.currentText.collect { session.reload() } }
    return session
}

/** Shows [content] once the config is loaded, a progress or error text otherwise. */
@Composable
private fun WithEditor(session: ConfigSession, content: @Composable (ConfigEditor) -> Unit) {
    val editor = session.editor
    when {
        editor != null -> content(editor)
        session.error != null -> CenteredText(stringResource(R.string.config_load_failed, errorText(session.error!!)))
        else -> CenteredText(stringResource(R.string.config_loading))
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Shown while i2pd.conf differs from the config of the running router: lists changes, reverts or restarts. */
@Composable
fun RestartBanner() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flow = remember { RouterController.pendingChanges(context) }
    val changes by flow.collectAsStateWithLifecycle(emptyList())
    if (changes.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(stringResource(R.string.banner_restart), style = MaterialTheme.typography.bodyMedium)
            changes.take(MAX_SHOWN_CHANGES).forEach { change ->
                Text(
                    "${change.name}: ${optionValueText(change.name, change.before, false)} → ${optionValueText(change.name, change.after, false)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            if (changes.size > MAX_SHOWN_CHANGES) {
                val more = changes.size - MAX_SHOWN_CHANGES
                Text(pluralStringResource(R.plurals.changes_more, more, more), style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = { scope.launch { RouterController.config(context).revertToStarted() } }) {
                    Text(stringResource(R.string.action_revert_changes))
                }
                TextButton(onClick = { RouterController.restart(context) }) {
                    Text(stringResource(org.purplei2p.i2pd.service.R.string.action_restart))
                }
            }
        }
    }
}

private const val MAX_SHOWN_CHANGES = 5

@Composable
fun RouterBasicScreen(session: ConfigSession, focusGroup: OptionGroup? = null) {
    WithEditor(session) { editor ->
        val options = remember(editor) { OptionOverlay.basicOptions(editor.schema) }
        val scroll = rememberScrollState()
        // Group title offsets, to scroll to the group a service chip of the status screen asked for
        val offsets = remember { mutableStateMapOf<OptionGroup, Int>() }
        var focused by rememberSaveable { mutableStateOf(false) }
        val focusOffset = focusGroup?.let { offsets[it] }
        LaunchedEffect(focusOffset) {
            if (focusOffset != null && !focused) {
                scroll.animateScrollTo(focusOffset)
                focused = true
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            RestartBanner()
            options.groupBy { it.group }.forEach { (group, items) ->
                group?.let {
                    SectionTitle(
                        stringResource(groupTitle(it)),
                        Modifier.onGloballyPositioned { coordinates -> offsets[group] = coordinates.positionInParent().y.toInt() },
                    )
                }
                items.forEach { ui -> OptionRow(session, editor, editor.schema[ui.name]!!, ui, showName = false) }
            }
        }
    }
}

@Composable
fun RouterAdvancedScreen(session: ConfigSession) {
    WithEditor(session) { editor ->
        var query by rememberSaveable { mutableStateOf("") }
        val issues = remember(editor) { editor.validate().filter { it !is ConfigIssue.UnknownOption } }
        val unknown = remember(editor) { editor.unknownEntries() }
        val problems = issues.size + unknown.size
        val sections = remember(editor) {
            editor.schema.options
                .map { it to OptionOverlay.uiFor(it) }
                .filter { (_, ui) -> ui.level != OptionLevel.HIDDEN }
                .groupBy { (spec, _) -> spec.section }
        }
        val found = remember(sections, query) {
            sections.mapValues { (_, options) ->
                options.filter { (spec, _) -> spec.name.contains(query, true) || spec.description.contains(query, true) }
            }.filterValues { it.isNotEmpty() }
        }
        // Tabs: problems first while there are any, then the sections of i2pd.conf in schema order
        val tabs = remember(sections, problems > 0) { listOfNotNull(PROBLEMS_TAB.takeIf { problems > 0 }) + sections.keys }
        var selected by rememberSaveable { mutableStateOf(tabs.first()) }
        if (selected !in tabs) selected = tabs.first()
        val searching = query.isNotBlank()

        // The banner stays outside the lazy list: an item growing from zero height would be scrolled out of view
        Column(Modifier.fillMaxSize()) {
            RestartBanner()
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.logs_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            // While searching, results of all sections are shown together and the tabs are hidden
            if (!searching) {
                PrimaryScrollableTabRow(selectedTabIndex = tabs.indexOf(selected), edgePadding = 16.dp) {
                    tabs.forEach { tab ->
                        Tab(
                            selected = tab == selected,
                            onClick = { selected = tab },
                            text = { Text(sectionTabTitle(tab, problems)) },
                        )
                    }
                }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    searching -> found.forEach { (section, options) ->
                        item(key = "section-$section") { SectionTitle(sectionTitle(section)) }
                        items(options, key = { it.first.name }) { (spec, ui) -> OptionRow(session, editor, spec, ui, showName = true) }
                    }
                    selected == PROBLEMS_TAB -> {
                        if (unknown.isNotEmpty()) {
                            item { SectionTitle(stringResource(R.string.config_unknown_options, org.purplei2p.i2pd.nativelib.I2pdNative.version())) }
                            items(unknown, key = { "unknown-${it.line}" }) { entry -> UnknownEntryRow(session, entry) }
                        }
                        if (issues.isNotEmpty()) {
                            item {
                                SectionTitle(stringResource(R.string.config_issues))
                                issues.forEach { issue ->
                                    Text(
                                        issueText(issue),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                    else -> items(sections[selected].orEmpty(), key = { it.first.name }) { (spec, ui) ->
                        OptionRow(session, editor, spec, ui, showName = true)
                    }
                }
            }
        }
    }
}

/** Not a section name of i2pd.conf: those are lower case words without spaces. */
private const val PROBLEMS_TAB = " problems"

@Composable
private fun sectionTitle(section: String): String = if (section.isEmpty()) stringResource(R.string.section_general) else "[$section]"

@Composable
private fun sectionTabTitle(tab: String, problems: Int): String = when (tab) {
    PROBLEMS_TAB -> stringResource(R.string.config_problems_tab, problems)
    "" -> stringResource(R.string.section_general)
    else -> tab
}

@Composable
private fun UnknownEntryRow(session: ConfigSession, entry: IniDocument.EntryRef) {
    val name = if (entry.section.isEmpty()) entry.key else "${entry.section}.${entry.key}"
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text("$name = ${entry.value}", color = MaterialTheme.colorScheme.error, fontFamily = FontFamily.Monospace)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { session.removeEntry(entry, commentOut = true) }) { Text(stringResource(R.string.action_comment_out)) }
            TextButton(onClick = { session.removeEntry(entry, commentOut = false) }) { Text(stringResource(R.string.action_remove)) }
        }
    }
}

@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    HorizontalDivider(modifier)
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun OptionRow(session: ConfigSession, editor: ConfigEditor, spec: OptionSpec, ui: OptionUi, showName: Boolean) {
    var editing by rememberSaveable(spec.name) { mutableStateOf(false) }
    val isSet = editor.isSet(spec.name)
    val value = editor.effectiveValue(spec.name)
    val switch = ui.widget == OptionWidget.Switch && spec.type == OptionType.BOOL
    val checked = ConfigEditor.parseBool(value)

    Row(
        Modifier.fillMaxWidth()
            .then(
                // One focusable element for TalkBack: the row toggles, the switch only shows the state
                if (switch) Modifier.toggleable(checked, role = Role.Switch) { session.set(spec.name, it.toString()) }
                else Modifier.clickable { editing = true },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (showName) spec.name else optionLabel(spec.name), style = MaterialTheme.typography.bodyLarge)
            if (!switch || !isSet) {
                Text(
                    optionValueText(spec.name, value, isDefault = !isSet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showName && spec.description.isNotBlank()) {
                Text(spec.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (switch) {
            Switch(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 16.dp))
        }
    }

    if (editing) {
        OptionDialog(
            spec = spec,
            ui = ui,
            initial = editor.valueOf(spec.name) ?: spec.default.orEmpty(),
            onDismiss = { editing = false },
            onSave = {
                session.set(spec.name, it)
                editing = false
            },
            onReset = {
                session.reset(spec.name)
                editing = false
            },
        )
    }
}

@Composable
private fun OptionDialog(
    spec: OptionSpec,
    ui: OptionUi,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val error = ConfigEditor.validateValue(spec, ui.widget, text)
    val numeric = ui.widget is OptionWidget.Port || ui.widget is OptionWidget.RouterPort || ui.widget is OptionWidget.Number

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(optionLabel(spec.name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                val widget = ui.widget
                if (widget is OptionWidget.Choice) {
                    widget.values.forEach { choice ->
                        Row(
                            Modifier.fillMaxWidth().selectable(selected = text == choice, role = Role.RadioButton) { text = choice }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = text == choice, onClick = null)
                            Text(
                                (if (spec.name == "bandwidth") bandwidthText(choice) else null) ?: choice,
                                Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }
                if (widget !is OptionWidget.Choice || widget.allowCustom) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.trim() },
                        label = { if (widget is OptionWidget.Choice) Text(stringResource(R.string.value_custom)) },
                        singleLine = true,
                        isError = error != null && !error.warning,
                        supportingText = { if (error != null && !error.warning) Text(valueErrorText(spec.name, error)) },
                        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
                    )
                }
                if (spec.description.isNotBlank()) {
                    Text(spec.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = error == null || error.warning,
                onClick = { if (error?.warning == true) confirming = true else onSave(text) },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text(stringResource(R.string.action_reset_default)) }
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            }
        },
    )

    if (confirming && error != null) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.warning_title)) },
            text = { Text(valueErrorText(spec.name, error)) },
            confirmButton = { TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.action_save_anyway)) } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

@Composable
fun RawConfigScreen() {
    val context = LocalContext.current
    val config = RouterController.config(context)
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf<String?>(null) }
    var issues by remember { mutableStateOf<List<ConfigIssue>>(emptyList()) }
    var error by remember { mutableStateOf<Throwable?>(null) }

    suspend fun load() {
        try {
            text = config.loadText()
        } catch (e: Exception) {
            error = e
        }
    }
    LaunchedEffect(Unit) {
        if (text == null) load()
        // A save elsewhere (e.g. revert in the banner) replaces the edited text
        config.currentText.collect { saved -> if (saved != null && saved != text) text = saved }
    }

    val current = text
    if (current == null) {
        CenteredText(error?.let { stringResource(R.string.config_load_failed, errorText(it)) } ?: stringResource(R.string.config_loading))
        return
    }
    Column(Modifier.fillMaxSize()) {
        RestartBanner()
        Row(Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = {
                scope.launch {
                    try {
                        val found = ConfigEditor(IniDocument.parse(current), config.schema()).validate()
                        if (found.isEmpty()) config.save(current) else issues = found
                    } catch (e: Exception) {
                        error = e
                    }
                }
            }) { Text(stringResource(R.string.action_save)) }
            TextButton(onClick = { scope.launch { load() } }) { Text(stringResource(R.string.action_revert)) }
        }
        // A failed save: before, the error only showed while the file was not loaded yet
        error?.let { failure ->
            Text(
                errorText(failure),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.action_dismiss)) { error = null }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        OutlinedTextField(
            value = current,
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
                    Text(stringResource(R.string.config_issues_on_save))
                    issues.forEach { Text("• " + issueText(it), style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            config.save(current)
                        } catch (e: Exception) {
                            error = e
                        }
                    }
                    issues = emptyList()
                }) { Text(stringResource(R.string.action_save_anyway)) }
            },
            dismissButton = { TextButton(onClick = { issues = emptyList() }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}
