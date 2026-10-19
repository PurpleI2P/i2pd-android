package org.purplei2p.i2pd.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.purplei2p.i2pd.daemon.api.LogChunk
import org.purplei2p.i2pd.daemon.api.StartParams
import org.purplei2p.i2pd.daemon.api.StatusSnapshot

/** Log of one router process; seq numbering starts anew in every process. */
data class SessionLog(val sessionId: Int, val chunk: LogChunk)

data class SupervisorConfig(
    val gracefulTimeoutMs: Long = 10 * 60 * 1000L,
    /** How long to wait for the router to stop by itself before killing its process. */
    val stopTimeoutMs: Long = 8_000L,
    /** How long to wait for the process death after kill before giving up on it. */
    val killTimeoutMs: Long = 2_000L,
    val startTimeoutMs: Long = 120_000L,
    val autoRestart: Boolean = true,
    /** Delays before automatic restarts after crashes; their count is the attempt limit within the window. */
    val autoRestartDelaysMs: List<Long> = listOf(2_000L, 10_000L, 60_000L),
    val autoRestartWindowMs: Long = 10 * 60 * 1000L,
)

/**
 * Router lifecycle state machine, see docs/DESIGN.md §6.
 *
 * All state lives on [scope], which must be single-threaded (Dispatchers.Main.immediate in the app).
 * Public methods may be called from any thread.
 */
class RouterSupervisor(
    private val scope: CoroutineScope,
    private val connector: DaemonConnector,
    private val prepare: suspend () -> StartParams,
    /** Read on every use, so changed app settings apply without recreating the supervisor. */
    private val config: () -> SupervisorConfig = { SupervisorConfig() },
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** Whether the user wants the router running; persisted to restore it after the app process is killed. */
    private val onWantedChanged: (Boolean) -> Unit = {},
) {
    private val _state = MutableStateFlow(RouterState())
    val state: StateFlow<RouterState> = _state.asStateFlow()

    // Volatile: readLog uses them from other threads
    @Volatile
    private var session: DaemonSession? = null

    @Volatile
    private var sessionId = 0
    private var connected = false
    private var pendingParams: StartParams? = null
    private var lastFatal: String? = null

    /** Last known network state, applied to every started router. */
    private var online: Boolean? = null

    private var prepareJob: Job? = null
    private var timerJob: Job? = null
    private var autoRestartJob: Job? = null
    private val crashTimes = ArrayDeque<Long>()

    /** Sessions given up on (killed and unbound) whose process death is still awaited. */
    private val retiring = mutableMapOf<Int, CompletableDeferred<Unit>>()

    private class StoppedOperation(val block: suspend () -> Unit, val result: CompletableDeferred<Result<Unit>>)

    private val stoppedOperations = ArrayDeque<StoppedOperation>()
    private var operationsRunning = false

    private val lifecycle: Lifecycle get() = _state.value.lifecycle

    fun start() = post { startInternal() }

    fun stopGraceful() = post {
        onWantedChanged(false)
        update { it.copy(restartPending = false) }
        when (lifecycle) {
            Lifecycle.Running -> beginDrain()
            Lifecycle.Preparing, Lifecycle.Starting -> stopNowInternal()
            is Lifecycle.Failed -> dismissInternal()
            else -> Unit
        }
    }

    fun stopNow() = post {
        onWantedChanged(false)
        update { it.copy(restartPending = false, resumeAfterOperations = false) }
        if (lifecycle is Lifecycle.Failed) dismissInternal() else stopNowInternal()
    }

    fun cancelGraceful() = post {
        if (lifecycle !is Lifecycle.Draining) return@post
        timerJob?.cancel()
        session?.setAcceptsTransit(true)
        onWantedChanged(true)
        update { it.copy(lifecycle = Lifecycle.Running, restartPending = false) }
    }

    fun restart(graceful: Boolean = false) = post {
        when (lifecycle) {
            Lifecycle.Running -> {
                onWantedChanged(true)
                update { it.copy(restartPending = true) }
                if (graceful) beginDrain() else stopNowInternal()
            }
            is Lifecycle.Draining, Lifecycle.Starting -> {
                onWantedChanged(true)
                update { it.copy(restartPending = true) }
                stopNowInternal()
            }
            Lifecycle.Stopping -> {
                onWantedChanged(true)
                update { it.copy(restartPending = true) }
            }
            Lifecycle.Stopped, is Lifecycle.Failed -> startInternal()
            Lifecycle.Preparing -> Unit
        }
    }

    /** Leaves the Failed state. */
    fun dismiss() = post { if (lifecycle is Lifecycle.Failed) dismissInternal() }

    fun reloadTunnels() = post { if (isRouterUp()) session?.reloadTunnels() }

    fun setOnline(online: Boolean) = post {
        this.online = online
        if (isRouterUp()) session?.setOnline(online)
    }

    /** Log lines of the current router process with seq > [sinceSeq], null if no router process. */
    suspend fun readLog(sinceSeq: Long, max: Int): SessionLog? {
        val id = sessionId
        val current = session ?: return null
        val chunk = withContext(ioDispatcher) { current.readLog(sinceSeq, max) } ?: return null
        return SessionLog(id, chunk)
    }

    /**
     * Runs [block] on the IO dispatcher while the router is stopped (docs/DESIGN.md §6.6).
     * A running router is stopped first and started again afterwards.
     */
    fun runWithRouterStopped(block: suspend () -> Unit): Deferred<Result<Unit>> {
        val result = CompletableDeferred<Result<Unit>>()
        post {
            stoppedOperations.addLast(StoppedOperation(block, result))
            when (lifecycle) {
                Lifecycle.Stopped -> runOperations()
                is Lifecycle.Failed -> {
                    if (_state.value.autoRestartAt != null) {
                        autoRestartJob?.cancel()
                        update { it.copy(autoRestartAt = null, resumeAfterOperations = true) }
                    }
                    runOperations()
                }
                Lifecycle.Preparing, Lifecycle.Starting, Lifecycle.Running, is Lifecycle.Draining -> {
                    update { it.copy(resumeAfterOperations = true) }
                    stopNowInternal()
                }
                // finishStop runs the queue; a pending restart becomes a resume
                Lifecycle.Stopping -> Unit
            }
        }
        return result
    }

    private fun post(block: () -> Unit) {
        scope.launch { block() }
    }

    private inline fun update(transform: (RouterState) -> RouterState) {
        _state.value = transform(_state.value)
    }

    private fun isRouterUp() = connected && (lifecycle == Lifecycle.Running || lifecycle is Lifecycle.Draining)

    private fun startInternal() {
        when (lifecycle) {
            Lifecycle.Stopped, is Lifecycle.Failed -> {
                autoRestartJob?.cancel()
                onWantedChanged(true)
                if (operationsRunning) {
                    update { it.copy(autoRestartAt = null, resumeAfterOperations = true) }
                } else {
                    begin()
                }
            }
            is Lifecycle.Draining -> cancelGraceful()
            Lifecycle.Stopping -> {
                onWantedChanged(true)
                update { it.copy(restartPending = true) }
            }
            else -> Unit
        }
    }

    private fun begin() {
        lastFatal = null
        update {
            it.copy(
                lifecycle = Lifecycle.Preparing,
                restartPending = false,
                status = null,
                version = null,
                autoRestartAt = null,
            )
        }
        prepareJob = scope.launch {
            val params = try {
                // A previous process must be gone, otherwise the new bind would reuse it
                retiring.values.toList().forEach { withTimeoutOrNull(config().killTimeoutMs) { it.await() } }
                withContext(ioDispatcher) { prepare() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                prepareJob = null
                enterFailed(FailureReason.PrepareFailed(e.message ?: e.toString()))
                return@launch
            }
            prepareJob = null
            connect(params)
        }
    }

    private fun connect(params: StartParams) {
        val id = ++sessionId
        connected = false
        pendingParams = params
        update { it.copy(lifecycle = Lifecycle.Starting) }
        session = connector.connect(EventsForSession(id))
        timerJob = scope.launch {
            delay(config().startTimeoutMs)
            failWithLiveSession(FailureReason.StartTimeout)
        }
    }

    private fun beginDrain() {
        session?.setAcceptsTransit(false)
        update { it.copy(lifecycle = Lifecycle.Draining(clock() + config().gracefulTimeoutMs)) }
        timerJob?.cancel()
        timerJob = scope.launch {
            delay(config().gracefulTimeoutMs)
            stopNowInternal()
        }
        if (_state.value.status?.tunnelsTransit == 0) stopNowInternal()
    }

    private fun stopNowInternal() {
        when (lifecycle) {
            Lifecycle.Preparing -> {
                prepareJob?.cancel()
                prepareJob = null
                finishStop()
            }
            Lifecycle.Starting, Lifecycle.Running, is Lifecycle.Draining -> {
                timerJob?.cancel()
                update { it.copy(lifecycle = Lifecycle.Stopping) }
                val current = session
                if (current == null) {
                    finishStop()
                } else if (!connected) {
                    retireSession()
                    finishStop()
                } else {
                    current.stop()
                    val id = sessionId
                    timerJob = scope.launch {
                        delay(config().stopTimeoutMs)
                        current.kill()
                        delay(config().killTimeoutMs)
                        if (id == sessionId && lifecycle == Lifecycle.Stopping) {
                            retireSession()
                            finishStop()
                        }
                    }
                }
            }
            // Second "stop now" while stopping: do not wait for i2pd anymore
            Lifecycle.Stopping -> session?.kill()
            else -> Unit
        }
    }

    private fun finishStop() {
        timerJob?.cancel()
        closeSession()
        update { it.copy(lifecycle = Lifecycle.Stopped, status = null, version = null) }
        if (stoppedOperations.isNotEmpty()) {
            if (_state.value.restartPending) update { it.copy(restartPending = false, resumeAfterOperations = true) }
            runOperations()
        } else if (_state.value.restartPending) {
            begin()
        }
    }

    private fun runOperations() {
        if (operationsRunning) return
        operationsRunning = true
        update { it.copy(operationInProgress = true) }
        scope.launch {
            while (stoppedOperations.isNotEmpty()) {
                val operation = stoppedOperations.removeFirst()
                val result = try {
                    withContext(ioDispatcher) { operation.block() }
                    Result.success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
                operation.result.complete(result)
            }
            operationsRunning = false
            val resume = _state.value.resumeAfterOperations
            update { it.copy(operationInProgress = false, resumeAfterOperations = false) }
            if (resume && (lifecycle == Lifecycle.Stopped || lifecycle is Lifecycle.Failed)) {
                onWantedChanged(true)
                begin()
            }
        }
    }

    private fun dismissInternal() {
        autoRestartJob?.cancel()
        onWantedChanged(false)
        update { it.copy(lifecycle = Lifecycle.Stopped, autoRestartAt = null) }
    }

    /** Failure while the router process is still alive: get rid of it without waiting. */
    private fun failWithLiveSession(reason: FailureReason) {
        retireSession()
        enterFailed(reason)
    }

    private fun enterFailed(reason: FailureReason) {
        timerJob?.cancel()
        closeSession()
        update { it.copy(lifecycle = Lifecycle.Failed(reason), status = null, restartPending = false) }
        if (reason == FailureReason.Crashed && config().autoRestart && scheduleAutoRestart()) return
        if (stoppedOperations.isNotEmpty()) {
            runOperations()
        } else if (!_state.value.resumeAfterOperations) {
            onWantedChanged(false)
        }
    }

    private fun scheduleAutoRestart(): Boolean {
        val now = clock()
        while (crashTimes.isNotEmpty() && now - crashTimes.first() > config().autoRestartWindowMs) crashTimes.removeFirst()
        val attempt = crashTimes.size
        if (attempt >= config().autoRestartDelaysMs.size) return false
        val delayMs = config().autoRestartDelaysMs[attempt]
        crashTimes.addLast(now)
        update { it.copy(autoRestartAt = now + delayMs) }
        autoRestartJob = scope.launch {
            delay(delayMs)
            if (lifecycle is Lifecycle.Failed && !operationsRunning) begin()
        }
        return true
    }

    private fun retireSession() {
        val current = session ?: return
        retiring[sessionId] = CompletableDeferred()
        current.kill()
        closeSession()
    }

    private fun closeSession() {
        session?.close()
        session = null
        connected = false
        pendingParams = null
    }

    private fun onConnected() {
        connected = true
        when (lifecycle) {
            Lifecycle.Starting -> pendingParams?.let { session?.start(it) }
            Lifecycle.Stopping -> session?.stop()
            else -> Unit
        }
    }

    private fun onStarted(version: String) {
        if (lifecycle != Lifecycle.Starting) return
        timerJob?.cancel()
        online?.let { session?.setOnline(it) }
        update { it.copy(lifecycle = Lifecycle.Running, version = version) }
    }

    private fun onStartFailed(message: String) {
        if (lifecycle == Lifecycle.Starting) failWithLiveSession(FailureReason.StartFailed(message))
    }

    private fun onStatus(status: StatusSnapshot) {
        val current = lifecycle
        if (current != Lifecycle.Running && current !is Lifecycle.Draining) return
        update { it.copy(status = status) }
        if (current is Lifecycle.Draining && status.tunnelsTransit == 0) stopNowInternal()
    }

    private fun onDied() {
        when (val current = lifecycle) {
            Lifecycle.Stopping -> finishStop()
            Lifecycle.Starting, Lifecycle.Running, is Lifecycle.Draining -> {
                val reason = lastFatal?.let { FailureReason.Fatal(it) }
                    ?: if (current == Lifecycle.Starting) {
                        FailureReason.StartFailed("Router process exited during start")
                    } else {
                        FailureReason.Crashed
                    }
                enterFailed(reason)
            }
            else -> closeSession()
        }
    }

    /** Routes events of one session; events of older sessions only complete their retirement. */
    private inner class EventsForSession(private val id: Int) : DaemonEvents {
        private inline fun ifCurrent(crossinline block: () -> Unit) = post { if (id == sessionId && session != null) block() }

        override fun onConnected() = ifCurrent { this@RouterSupervisor.onConnected() }

        override fun onStarted(version: String) = ifCurrent { this@RouterSupervisor.onStarted(version) }

        override fun onStartFailed(message: String) = ifCurrent { this@RouterSupervisor.onStartFailed(message) }

        override fun onFatal(message: String) = ifCurrent { lastFatal = message }

        override fun onStatus(status: StatusSnapshot) = ifCurrent { this@RouterSupervisor.onStatus(status) }

        override fun onDied() = post {
            retiring.remove(id)?.complete(Unit)
            if (id == sessionId && session != null) this@RouterSupervisor.onDied()
        }
    }
}
