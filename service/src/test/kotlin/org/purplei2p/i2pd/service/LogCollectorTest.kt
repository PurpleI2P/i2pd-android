package org.purplei2p.i2pd.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.purplei2p.i2pd.daemon.api.LogChunk

@OptIn(ExperimentalCoroutinesApi::class)
class LogCollectorTest {

    /** Fake router log: lines of the current process, numbered from 1, with an optional ring buffer limit. */
    private class FakeRouterLog(private val keep: Int = Int.MAX_VALUE) {
        var sessionId = 1
        private val lines = mutableListOf<String>()
        private var firstSeq = 1L

        fun write(vararg text: String) {
            lines += text
            while (lines.size > keep) {
                lines.removeAt(0)
                firstSeq++
            }
        }

        fun newProcess() {
            sessionId++
            lines.clear()
            firstSeq = 1
        }

        fun read(sinceSeq: Long, max: Int): SessionLog {
            val from = maxOf(sinceSeq + 1, firstSeq)
            val start = (from - firstSeq).toInt().coerceAtMost(lines.size)
            val slice = lines.subList(start, minOf(lines.size, start + max)).toList()
            return SessionLog(sessionId, LogChunk(if (slice.isEmpty()) firstSeq + lines.size else from, slice))
        }
    }

    private fun TestScope.collector(log: FakeRouterLog, capacity: Int = 100, batch: Int = 500) = LogCollector(
        scope = backgroundScope,
        source = { since, max -> log.read(since, max) },
        routerPresent = MutableStateFlow(true),
        capacity = capacity,
        batch = batch,
    )

    private fun LogCollector.texts() = entries.value.map {
        when (it) {
            is LogEntry.Line -> it.text
            is LogEntry.RouterRestarted -> "<restarted>"
            is LogEntry.Skipped -> "<skipped ${it.count}>"
        }
    }

    @Test
    fun `parses i2pd log lines`() = runTest {
        val log = FakeRouterLog()
        log.write("12:00:01@123/warn - Transports: No network", "not an i2pd line")
        val c = collector(log)
        c.poll()

        val first = c.entries.value[0] as LogEntry.Line
        assertEquals("12:00:01", first.time)
        assertEquals(LogLevel.WARN, first.level)
        assertEquals("Transports: No network", first.text)
        val second = c.entries.value[1] as LogEntry.Line
        assertEquals(null, second.level)
        assertEquals("not an i2pd line", second.text)
    }

    @Test
    fun `reads only new lines and in batches`() = runTest {
        val log = FakeRouterLog()
        log.write("a", "b", "c")
        val c = collector(log, batch = 2)
        c.poll()
        log.write("d")
        c.poll()
        assertEquals(listOf("a", "b", "c", "d"), c.texts())
    }

    @Test
    fun `new router process is read from its beginning after a marker`() = runTest {
        val log = FakeRouterLog()
        log.write("a", "b", "c")
        val c = collector(log)
        c.poll()
        log.newProcess()
        log.write("x")
        c.poll()
        assertEquals(listOf("a", "b", "c", "<restarted>", "x"), c.texts())
    }

    @Test
    fun `lines dropped by the router buffer are marked`() = runTest {
        val log = FakeRouterLog(keep = 2)
        log.write("a")
        val c = collector(log)
        c.poll()
        log.write("b", "c", "d", "e")
        c.poll()
        assertEquals(listOf("a", "<skipped 2>", "d", "e"), c.texts())
    }

    @Test
    fun `keeps at most capacity entries`() = runTest {
        val log = FakeRouterLog()
        log.write(*Array(10) { "line $it" })
        val c = collector(log, capacity = 3)
        c.poll()
        assertEquals(listOf("line 7", "line 8", "line 9"), c.texts())
    }

    @Test
    fun `polls periodically while the router is present and can be cleared`() = runTest {
        val log = FakeRouterLog()
        val present = MutableStateFlow(true)
        val c = LogCollector(backgroundScope, { since, max -> log.read(since, max) }, present, intervalMs = 1000)
        c.start()
        log.write("a")
        runCurrent()
        assertEquals(listOf("a"), c.texts())

        log.write("b")
        advanceTimeBy(1001)
        assertEquals(listOf("a", "b"), c.texts())

        present.value = false
        runCurrent()
        log.write("c")
        advanceTimeBy(5000)
        assertEquals(listOf("a", "b"), c.texts())

        c.clear()
        runCurrent()
        assertTrue(c.entries.value.isEmpty())
    }
}
