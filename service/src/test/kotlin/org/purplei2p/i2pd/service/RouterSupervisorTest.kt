package org.purplei2p.i2pd.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.purplei2p.i2pd.daemon.api.RouterError
import org.purplei2p.i2pd.daemon.api.RouterStatus
import org.purplei2p.i2pd.daemon.api.StartParams
import org.purplei2p.i2pd.daemon.api.StatusSnapshot

@OptIn(ExperimentalCoroutinesApi::class)
class RouterSupervisorTest {

    private val params = StartParams("/data/i2pd", listOf("--datadir=/data/i2pd"), "english")
    private val config = SupervisorConfig()

    private class FakeSession(val events: DaemonEvents) : DaemonSession {
        val commands = mutableListOf<String>()
        var killed = false
        var closed = false

        override fun start(params: StartParams) {
            commands += "start"
        }

        override fun stop() {
            commands += "stop"
        }

        override fun setAcceptsTransit(accept: Boolean) {
            commands += "transit=$accept"
        }

        override fun reloadTunnels() {
            commands += "reload"
        }

        override fun setOnline(online: Boolean) {
            commands += "online=$online"
        }

        override fun kill() {
            killed = true
        }

        override fun close() {
            closed = true
        }
    }

    private class Fixture(val supervisor: RouterSupervisor, val sessions: List<FakeSession>, val wanted: List<Boolean>) {
        val lifecycle get() = supervisor.state.value.lifecycle
        val session get() = sessions.last()
    }

    private fun TestScope.fixture(
        scope: CoroutineScope = backgroundScope,
        prepare: suspend () -> StartParams = { params },
    ): Fixture {
        val sessions = mutableListOf<FakeSession>()
        val wanted = mutableListOf<Boolean>()
        val supervisor = RouterSupervisor(
            scope = scope,
            connector = { events -> FakeSession(events).also { sessions += it } },
            prepare = prepare,
            config = config,
            clock = { testScheduler.currentTime },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            onWantedChanged = { wanted += it },
        )
        return Fixture(supervisor, sessions, wanted)
    }

    private fun status(transit: Int = 0) = StatusSnapshot(
        statusV4 = RouterStatus.OK, statusV6 = RouterStatus.UNKNOWN,
        errorV4 = RouterError.NONE, errorV6 = RouterError.NONE,
        testingV4 = false, testingV6 = false,
        uptimeSeconds = 10, acceptsTransit = true, floodfill = false,
        bandwidthIn = 0, bandwidthOut = 0, bandwidthTransit = 0,
        totalReceived = 0, totalSent = 0, totalTransit = 0,
        tunnelsIn = 2, tunnelsOut = 2, tunnelsTransit = transit, tunnelSuccessRate = 50,
        routers = 100, floodfills = 10, leaseSets = 5,
        services = emptySet(), netId = 2, webConsoleUrl = null,
    )

    private fun TestScope.startRunning(f: Fixture, transit: Int = 0) {
        f.supervisor.start()
        runCurrent()
        f.session.events.onConnected()
        f.session.events.onStarted("3.0.0")
        f.session.events.onStatus(status(transit))
        runCurrent()
    }

    @Test
    fun `start binds router and runs after it reports success`() = runTest {
        val f = fixture()
        f.supervisor.start()
        runCurrent()
        assertEquals(Lifecycle.Starting, f.lifecycle)
        assertTrue("start before connection", f.session.commands.isEmpty())

        f.session.events.onConnected()
        runCurrent()
        assertEquals(listOf("start"), f.session.commands)

        f.session.events.onStarted("3.0.0")
        runCurrent()
        assertEquals(Lifecycle.Running, f.lifecycle)
        assertEquals("3.0.0", f.supervisor.state.value.version)
        assertEquals(listOf(true), f.wanted)
    }

    @Test
    fun `start error fails and gets rid of the process`() = runTest {
        val f = fixture()
        f.supervisor.start()
        runCurrent()
        f.session.events.onConnected()
        f.session.events.onStartFailed("Daemon init failed")
        runCurrent()

        assertEquals(Lifecycle.Failed(FailureReason.StartFailed("Daemon init failed")), f.lifecycle)
        assertTrue(f.session.killed)
        assertTrue(f.session.closed)
        assertFalse(f.supervisor.state.value.isActive)
        assertEquals(false, f.wanted.last())
    }

    @Test
    fun `fatal error followed by process exit is reported and not restarted`() = runTest {
        val f = fixture()
        f.supervisor.start()
        runCurrent()
        f.session.events.onConnected()
        f.session.events.onFatal("Error while parsing config file: unrecognised option 'foo'")
        f.session.events.onDied()
        runCurrent()

        assertEquals(
            Lifecycle.Failed(FailureReason.Fatal("Error while parsing config file: unrecognised option 'foo'")),
            f.lifecycle,
        )
        assertNull(f.supervisor.state.value.autoRestartAt)
        advanceTimeBy(config.autoRestartDelaysMs.max() + 1)
        runCurrent()
        assertEquals(1, f.sessions.size)
    }

    @Test
    fun `graceful stop declines transit and stops when transit tunnels end`() = runTest {
        val f = fixture()
        startRunning(f, transit = 3)

        f.supervisor.stopGraceful()
        runCurrent()
        assertTrue(f.lifecycle is Lifecycle.Draining)
        assertEquals("transit=false", f.session.commands.last())

        f.session.events.onStatus(status(transit = 0))
        runCurrent()
        assertEquals(Lifecycle.Stopping, f.lifecycle)
        assertEquals("stop", f.session.commands.last())

        f.session.events.onDied()
        runCurrent()
        assertEquals(Lifecycle.Stopped, f.lifecycle)
        assertTrue(f.session.closed)
        assertNull(f.supervisor.state.value.status)
        assertEquals(false, f.wanted.last())
    }

    @Test
    fun `graceful stop ends at the deadline`() = runTest {
        val f = fixture()
        startRunning(f, transit = 3)
        f.supervisor.stopGraceful()
        runCurrent()
        assertEquals(Lifecycle.Draining(testScheduler.currentTime + config.gracefulTimeoutMs), f.lifecycle)

        advanceTimeBy(config.gracefulTimeoutMs + 1)
        runCurrent()
        assertEquals(Lifecycle.Stopping, f.lifecycle)
    }

    @Test
    fun `graceful stop can be cancelled`() = runTest {
        val f = fixture()
        startRunning(f, transit = 3)
        f.supervisor.stopGraceful()
        runCurrent()
        f.supervisor.cancelGraceful()
        runCurrent()

        assertEquals(Lifecycle.Running, f.lifecycle)
        assertEquals("transit=true", f.session.commands.last())
        advanceTimeBy(config.gracefulTimeoutMs + 1)
        runCurrent()
        assertEquals(Lifecycle.Running, f.lifecycle)
    }

    @Test
    fun `stop now kills the process if it does not exit in time`() = runTest {
        val f = fixture()
        startRunning(f, transit = 3)
        f.supervisor.stopNow()
        runCurrent()
        assertEquals("stop", f.session.commands.last())
        assertFalse(f.session.killed)

        advanceTimeBy(config.stopTimeoutMs + 1)
        runCurrent()
        assertTrue(f.session.killed)

        f.session.events.onDied()
        runCurrent()
        assertEquals(Lifecycle.Stopped, f.lifecycle)
    }

    @Test
    fun `stop completes even if the killed process never reports death`() = runTest {
        val f = fixture()
        startRunning(f)
        f.supervisor.stopNow()
        advanceTimeBy(config.stopTimeoutMs + config.killTimeoutMs + 1)
        runCurrent()
        assertEquals(Lifecycle.Stopped, f.lifecycle)
        assertTrue(f.session.closed)
    }

    @Test
    fun `restart uses a new process`() = runTest {
        val f = fixture()
        startRunning(f)
        f.supervisor.restart()
        runCurrent()
        assertEquals(Lifecycle.Stopping, f.lifecycle)
        assertTrue(f.supervisor.state.value.restartPending)
        assertTrue(f.supervisor.state.value.isActive)

        f.session.events.onDied()
        runCurrent()
        assertEquals(2, f.sessions.size)
        assertEquals(Lifecycle.Starting, f.lifecycle)
        assertFalse(f.supervisor.state.value.restartPending)

        f.session.events.onConnected()
        f.session.events.onStarted("3.0.0")
        runCurrent()
        assertEquals(Lifecycle.Running, f.lifecycle)
    }

    @Test
    fun `events of a previous process are ignored`() = runTest {
        val f = fixture()
        startRunning(f)
        val old = f.session
        f.supervisor.restart()
        runCurrent()
        old.events.onDied()
        runCurrent()

        old.events.onStatus(status(transit = 42))
        old.events.onStarted("old")
        runCurrent()
        assertEquals(Lifecycle.Starting, f.lifecycle)
        assertNull(f.supervisor.state.value.status)
    }

    @Test
    fun `crash is restarted automatically with backoff until the limit`() = runTest {
        val f = fixture()
        startRunning(f)

        config.autoRestartDelaysMs.forEachIndexed { attempt, delayMs ->
            f.session.events.onDied()
            runCurrent()
            assertEquals(Lifecycle.Failed(FailureReason.Crashed), f.lifecycle)
            assertEquals(testScheduler.currentTime + delayMs, f.supervisor.state.value.autoRestartAt)
            assertTrue(f.supervisor.state.value.isActive)

            advanceTimeBy(delayMs + 1)
            runCurrent()
            assertEquals(attempt + 2, f.sessions.size)
            f.session.events.onConnected()
            f.session.events.onStarted("3.0.0")
            runCurrent()
            assertEquals(Lifecycle.Running, f.lifecycle)
        }

        f.session.events.onDied()
        runCurrent()
        assertEquals(Lifecycle.Failed(FailureReason.Crashed), f.lifecycle)
        assertNull(f.supervisor.state.value.autoRestartAt)
        assertFalse(f.supervisor.state.value.isActive)
    }

    @Test
    fun `stop during preparation does not bind the router`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val f = fixture(prepare = {
            gate.await()
            params
        })
        f.supervisor.start()
        runCurrent()
        assertEquals(Lifecycle.Preparing, f.lifecycle)

        f.supervisor.stopNow()
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(Lifecycle.Stopped, f.lifecycle)
        assertTrue(f.sessions.isEmpty())
    }

    @Test
    fun `prepare error fails the start`() = runTest {
        val f = fixture(prepare = { throw java.io.IOException("No space left on device") })
        f.supervisor.start()
        runCurrent()
        assertEquals(Lifecycle.Failed(FailureReason.PrepareFailed("No space left on device")), f.lifecycle)
    }

    @Test
    fun `router that does not report start in time is killed`() = runTest {
        val f = fixture()
        f.supervisor.start()
        runCurrent()
        f.session.events.onConnected()
        advanceTimeBy(config.startTimeoutMs + 1)
        runCurrent()
        assertEquals(Lifecycle.Failed(FailureReason.StartTimeout), f.lifecycle)
        assertTrue(f.session.killed)
    }

    @Test
    fun `operation on a running router stops it, runs and starts it again`() = runTest {
        val f = fixture()
        startRunning(f)
        var lifecycleDuringOperation: Lifecycle? = null
        val result = f.supervisor.runWithRouterStopped { lifecycleDuringOperation = f.lifecycle }
        runCurrent()
        assertEquals(Lifecycle.Stopping, f.lifecycle)
        assertTrue(f.supervisor.state.value.isActive)

        f.session.events.onDied()
        runCurrent()
        assertEquals(Lifecycle.Stopped, lifecycleDuringOperation)
        assertTrue(result.await().isSuccess)
        assertEquals(2, f.sessions.size)
        assertEquals(Lifecycle.Starting, f.lifecycle)
    }

    @Test
    fun `operation on a stopped router keeps it stopped and reports errors`() = runTest {
        val f = fixture()
        val result = f.supervisor.runWithRouterStopped { error("broken") }
        runCurrent()
        assertTrue(result.await().isFailure)
        assertEquals(Lifecycle.Stopped, f.lifecycle)
        assertFalse(f.supervisor.state.value.operationInProgress)
        assertTrue(f.sessions.isEmpty())
    }

    @Test
    fun `start during an operation waits for it`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val f = fixture()
        f.supervisor.runWithRouterStopped { gate.await() }
        runCurrent()
        f.supervisor.start()
        runCurrent()
        assertTrue(f.sessions.isEmpty())
        assertTrue(f.supervisor.state.value.isActive)

        gate.complete(Unit)
        runCurrent()
        assertEquals(Lifecycle.Starting, f.lifecycle)
    }
}
