package org.purplei2p.i2pd.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** i2pd log levels, from the most to the least important. */
enum class LogLevel {
    NONE, CRITICAL, ERROR, WARN, INFO, DEBUG;

    companion object {
        fun fromName(name: String): LogLevel? = when (name.lowercase()) {
            "none" -> NONE
            "critical" -> CRITICAL
            "error" -> ERROR
            "warn", "warning" -> WARN
            "info" -> INFO
            "debug" -> DEBUG
            else -> null
        }
    }
}

sealed interface LogEntry {
    /** Unique within the collector, for list keys. */
    val id: Long

    data class Line(override val id: Long, val time: String?, val level: LogLevel?, val text: String) : LogEntry

    /** A new router process started; its log begins after this entry. */
    data class RouterRestarted(override val id: Long) : LogEntry

    /** Lines dropped by the router's ring buffer before they were collected. */
    data class Skipped(override val id: Long, val count: Long) : LogEntry
}

/**
 * Copies the router log into the main process, so it survives the router process
 * (the last lines before a crash stay visible).
 */
class LogCollector(
    private val scope: CoroutineScope,
    private val source: suspend (sinceSeq: Long, max: Int) -> SessionLog?,
    /** True while a router process may have a log to read. */
    private val routerPresent: Flow<Boolean>,
    private val capacity: Int = 5000,
    private val intervalMs: Long = 1000,
    private val batch: Int = 500,
) {
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val buffer = ArrayDeque<LogEntry>()
    private var nextId = 1L
    private var sessionId: Int? = null
    private var lastSeq = 0L

    fun start() {
        scope.launch {
            routerPresent.distinctUntilChanged().collectLatest { present ->
                while (present && isActive) {
                    poll()
                    delay(intervalMs)
                }
            }
        }
    }

    /** Reads everything new; public for tests. */
    suspend fun poll() {
        var changed = false
        while (true) {
            val log = source(lastSeq, batch) ?: break
            if (log.sessionId != sessionId) {
                if (sessionId != null) {
                    add(LogEntry.RouterRestarted(nextId++))
                    changed = true
                }
                sessionId = log.sessionId
                if (lastSeq != 0L) {
                    // The cursor belonged to the previous process: read the new one from its start
                    lastSeq = 0
                    continue
                }
            }
            val chunk = log.chunk
            if (chunk.lines.isNotEmpty()) {
                val skipped = chunk.firstSeq - lastSeq - 1
                if (skipped > 0) add(LogEntry.Skipped(nextId++, skipped))
                for (line in chunk.lines) add(parse(line))
                lastSeq = chunk.lastSeq
                changed = true
            }
            if (chunk.lines.size < batch) break
        }
        if (changed) _entries.value = buffer.toList()
    }

    fun clear() = scope.launch {
        buffer.clear()
        _entries.value = emptyList()
    }

    private fun add(entry: LogEntry) {
        buffer.addLast(entry)
        while (buffer.size > capacity) buffer.removeFirst()
    }

    private fun parse(line: String): LogEntry.Line {
        val match = LINE.matchEntire(line)
        return if (match != null) {
            LogEntry.Line(nextId++, match.groupValues[1], LogLevel.fromName(match.groupValues[2]), match.groupValues[3])
        } else {
            LogEntry.Line(nextId++, null, null, line)
        }
    }

    private companion object {
        // i2pd format: "HH:MM:SS@tid/level - text"
        val LINE = Regex("""^(\S+)@\d+/(\w+) - (.*)$""")
    }
}
