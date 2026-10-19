package org.purplei2p.i2pd.service

import org.purplei2p.i2pd.daemon.api.LogChunk
import org.purplei2p.i2pd.daemon.api.StartParams
import org.purplei2p.i2pd.daemon.api.StatusSnapshot

/** Binds to a fresh router process. Abstracted from Android for supervisor tests. */
fun interface DaemonConnector {
    /** Starts binding; [events] are delivered from any thread, [DaemonEvents.onConnected] first. */
    fun connect(events: DaemonEvents): DaemonSession
}

/** Connection to one router process. Commands before [DaemonEvents.onConnected] are ignored. */
interface DaemonSession {
    fun start(params: StartParams)

    /** Asks the router to stop; its process exits afterwards. */
    fun stop()

    fun setAcceptsTransit(accept: Boolean)

    fun reloadTunnels()

    fun setOnline(online: Boolean)

    /** Blocking call, null if the router process is not reachable. */
    fun readLog(sinceSeq: Long, max: Int): LogChunk?

    /** Kills the router process immediately. */
    fun kill()

    /** Unbinds. An unbound router process stops by itself. */
    fun close()
}

interface DaemonEvents {
    fun onConnected()

    fun onStarted(version: String)

    fun onStartFailed(message: String)

    fun onFatal(message: String)

    fun onStatus(status: StatusSnapshot)

    /** The router process is gone (exited, killed or crashed). Delivered at most once. */
    fun onDied()
}
