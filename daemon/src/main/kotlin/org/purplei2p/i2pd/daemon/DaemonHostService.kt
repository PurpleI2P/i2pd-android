package org.purplei2p.i2pd.daemon

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import android.os.RemoteException
import android.util.Log
import org.purplei2p.i2pd.daemon.api.IDaemonCallback
import org.purplei2p.i2pd.daemon.api.IDaemonHost
import org.purplei2p.i2pd.daemon.api.LogChunk
import org.purplei2p.i2pd.daemon.api.StartParams
import org.purplei2p.i2pd.daemon.api.StatusSnapshot
import org.purplei2p.i2pd.nativelib.I2pdNative
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Hosts i2pd in the :router process.
 *
 * The router may be started once per process. [IDaemonHost.stop] stops it and ends the process,
 * so the supervisor sees a binder death and binds again for the next start.
 */
class DaemonHostService : Service() {

    // Native calls are serialized on this thread: init/start/stop must never overlap
    private val control = Executors.newSingleThreadExecutor { Thread(it, "i2pd-control") }
    private val ticker = Executors.newSingleThreadScheduledExecutor { Thread(it, "i2pd-status") }
    private var statusTask: ScheduledFuture<*>? = null

    @Volatile
    private var callback: IDaemonCallback? = null

    @Volatile
    private var startRequested = false

    @Volatile
    private var webConsoleUrl: String? = null

    private val binder = object : IDaemonHost.Stub() {
        override fun getPid(): Int = Process.myPid()

        override fun registerCallback(callback: IDaemonCallback?) {
            this@DaemonHostService.callback = callback
        }

        override fun start(params: StartParams) {
            if (startRequested) return
            startRequested = true
            control.execute { startRouter(params) }
        }

        override fun stop() {
            control.execute { stopRouterAndExit() }
        }

        override fun setAcceptsTransit(accept: Boolean) {
            control.execute { I2pdNative.setAcceptsTransit(accept) }
        }

        override fun reloadTunnels() {
            control.execute { I2pdNative.reloadTunnels() }
        }

        override fun setOnline(online: Boolean) {
            control.execute { I2pdNative.setOnline(online) }
        }

        override fun getStatus(): StatusSnapshot? = readStatus()

        override fun readLog(sinceSeq: Long, max: Int): LogChunk {
            val firstSeq = LongArray(1)
            val lines = I2pdNative.readLog(sinceSeq, max, firstSeq)
            return LogChunk(firstSeq[0], lines.toList())
        }
    }

    override fun onCreate() {
        super.onCreate()
        // The default handler blocks the crashed thread on the system crash dialog while i2pd keeps
        // running unnoticed; exit at once instead, so the supervisor sees the death and restarts the router
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            Log.e(TAG, "Uncaught exception in thread ${thread.name}, exiting", error)
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onUnbind(intent: Intent?): Boolean {
        // The supervisor is gone: nobody can stop the router properly anymore
        Log.w(TAG, "Supervisor unbound, stopping router")
        control.execute { stopRouterAndExit() }
        return false
    }

    private fun startRouter(params: StartParams) {
        val error = try {
            I2pdNative.setFatalHandler { message -> notifyFatal(message) }
            I2pdNative.init(params.dataDir, params.args.toTypedArray(), params.language)
                ?: I2pdNative.start()
        } catch (t: Throwable) {
            Log.e(TAG, "Router start failed", t)
            t.toString()
        }

        if (error != null) {
            Log.e(TAG, "Router start failed: $error")
            notify { it.onStartFailed(error) }
            return
        }

        webConsoleUrl = readWebConsoleUrl()
        notify { it.onStarted(I2pdNative.version()) }
        statusTask = ticker.scheduleWithFixedDelay(::sendStatus, 0, STATUS_INTERVAL_MS, TimeUnit.MILLISECONDS)
    }

    private fun stopRouterAndExit() {
        statusTask?.cancel(false)
        try {
            I2pdNative.stop()
        } catch (t: Throwable) {
            Log.e(TAG, "Router stop failed", t)
        }
        Log.i(TAG, "Router stopped, exiting process")
        Process.killProcess(Process.myPid())
    }

    private fun sendStatus() {
        val status = readStatus() ?: return
        notify { it.onStatus(status) }
    }

    private fun notifyFatal(message: String) {
        Log.e(TAG, "Fatal: $message")
        notify { it.onFatal(message) }
    }

    private inline fun notify(block: (IDaemonCallback) -> Unit) {
        val cb = callback ?: return
        try {
            block(cb)
        } catch (e: RemoteException) {
            Log.w(TAG, "Supervisor is not reachable", e)
        }
    }

    private fun readStatus(): StatusSnapshot? {
        val raw = LongArray(I2pdNative.Status.SIZE)
        if (!I2pdNative.status(raw)) return null
        return StatusMapper.map(raw, webConsoleUrl)
    }

    private fun readWebConsoleUrl(): String? {
        if (I2pdNative.option("http.enabled") != "true") return null
        val address = I2pdNative.option("http.address") ?: return null
        val port = I2pdNative.option("http.port") ?: return null
        val host = if (address.contains(':')) "[$address]" else address
        return "http://$host:$port/"
    }

    private companion object {
        const val TAG = "i2pd-host"
        const val STATUS_INTERVAL_MS = 1000L
    }
}
