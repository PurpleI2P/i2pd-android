package org.purplei2p.i2pd.service

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.purplei2p.i2pd.config.ConfigEditor
import org.purplei2p.i2pd.config.IniDocument
import org.purplei2p.i2pd.config.OptionChange
import org.purplei2p.i2pd.config.OptionSchema
import org.purplei2p.i2pd.nativelib.I2pdNative
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * i2pd.conf of the private data directory and the option schema of the bundled i2pd.
 * Lives in the main process only: loading the schema initializes i2pd options there (see I2pdNative.describeOptions).
 */
class RouterConfig private constructor(private val context: Context) {

    private val preparer = DataDirPreparer(context)
    private val file: File get() = File(preparer.dataDir, CONFIG_FILE)
    private val mutex = Mutex()
    private var schema: OptionSchema? = null
    private var backedUp = false

    /** Config text the running router was started with. */
    private val startedText = MutableStateFlow<String?>(null)

    /** Config text as last loaded or saved here; screens reload when it changes. */
    private val _currentText = MutableStateFlow<String?>(null)
    val currentText: StateFlow<String?> = _currentText.asStateFlow()

    /**
     * Options changed after the running router was started, compared by effective values:
     * reformatting or writing a default value is not a change. Empty when the router does not run.
     */
    fun pendingChanges(state: Flow<RouterState>): Flow<List<OptionChange>> =
        combine(state.map { it.lifecycle.hasRouterProcess }.distinctUntilChanged(), startedText, _currentText) { running, started, current ->
            Triple(running, started, current)
        }.map { (running, started, current) ->
            if (!running || started == null || current == null || started == current) {
                emptyList()
            } else {
                val schema = schema()
                ConfigEditor(IniDocument.parse(current), schema).changesSince(ConfigEditor(IniDocument.parse(started), schema))
            }
        }

    /** Puts back the config the running router was started with. */
    suspend fun revertToStarted() {
        val started = startedText.value ?: return
        save(started)
    }

    suspend fun schema(): OptionSchema = mutex.withLock { loadSchema() }

    /** Current config; copies the bundled one first if the router has never been started. */
    suspend fun load(): ConfigEditor = mutex.withLock {
        val schema = loadSchema()
        val text = withContext(Dispatchers.IO) {
            if (!file.exists()) preparer.prepare()
            file.readText()
        }
        _currentText.value = text
        ConfigEditor(IniDocument.parse(text), schema)
    }

    /** Raw text of i2pd.conf. */
    suspend fun loadText(): String = load().document.toString()

    /** Applies [change] to the current file content and saves it. Returns the saved editor. */
    suspend fun update(change: (ConfigEditor) -> Unit): ConfigEditor {
        val editor = load()
        change(editor)
        save(editor.document.toString())
        return editor
    }

    /** Saves [text] as i2pd.conf atomically; the first save in a session keeps the previous file as i2pd.conf.bak. */
    suspend fun save(text: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val target = file
            if (!backedUp && target.exists()) {
                target.copyTo(File(target.parentFile, "$CONFIG_FILE.bak"), overwrite = true)
                backedUp = true
            }
            val tmp = File(target.parentFile, "$CONFIG_FILE.tmp")
            FileOutputStream(tmp).use { out ->
                out.write(text.toByteArray())
                out.fd.sync()
            }
            if (!tmp.renameTo(target)) throw IOException("Cannot replace $target")
        }
        _currentText.value = text
    }

    /** Called when the router is being started with the current file. */
    internal fun onRouterStarting() {
        val text = file.takeIf { it.exists() }?.readText()
        startedText.value = text
        if (text != null) _currentText.value = text
    }

    private suspend fun loadSchema(): OptionSchema {
        schema?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            // Cached per i2pd version: loading it initializes i2pd options in this process
            val cache = File(context.filesDir, "schema-${I2pdNative.version()}.tsv")
            val text = cache.takeIf { it.exists() }?.readText()
                ?: (I2pdNative.describeOptions() ?: throw IOException("Cannot read i2pd options")).also { cache.writeText(it) }
            OptionSchema.parse(text)
        }
        schema = loaded
        return loaded
    }

    companion object {
        const val CONFIG_FILE = "i2pd.conf"

        @SuppressLint("StaticFieldLeak") // holds the application context only
        @Volatile
        private var instance: RouterConfig? = null

        fun get(context: Context): RouterConfig =
            instance ?: synchronized(this) {
                instance ?: RouterConfig(context.applicationContext).also { instance = it }
            }
    }
}
