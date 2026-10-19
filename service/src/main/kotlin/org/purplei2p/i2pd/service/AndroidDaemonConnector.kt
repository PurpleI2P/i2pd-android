package org.purplei2p.i2pd.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Process
import android.os.RemoteException
import android.util.Log
import org.purplei2p.i2pd.daemon.DaemonHostService
import org.purplei2p.i2pd.daemon.api.IDaemonCallback
import org.purplei2p.i2pd.daemon.api.IDaemonHost
import org.purplei2p.i2pd.daemon.api.StartParams
import org.purplei2p.i2pd.daemon.api.StatusSnapshot
import java.util.concurrent.atomic.AtomicBoolean

/** Binds [DaemonHostService] running in the :router process. */
class AndroidDaemonConnector(private val context: Context) : DaemonConnector {
    override fun connect(events: DaemonEvents): DaemonSession =
        AndroidDaemonSession(context.applicationContext, events).also { it.bind() }
}

private class AndroidDaemonSession(
    private val context: Context,
    private val events: DaemonEvents,
) : DaemonSession, ServiceConnection, IBinder.DeathRecipient {

    @Volatile
    private var host: IDaemonHost? = null

    @Volatile
    private var pid = 0

    private val dead = AtomicBoolean(false)
    private val bound = AtomicBoolean(false)

    private val callback = object : IDaemonCallback.Stub() {
        override fun onStarted(version: String) = events.onStarted(version)

        override fun onStartFailed(message: String) = events.onStartFailed(message)

        override fun onFatal(message: String) = events.onFatal(message)

        override fun onStatus(status: StatusSnapshot) = events.onStatus(status)
    }

    fun bind() {
        val intent = Intent(context, DaemonHostService::class.java)
        // The router process inherits the importance of this (foreground service) process
        val flags = Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
        if (context.bindService(intent, this, flags)) {
            bound.set(true)
        } else {
            Log.e(TAG, "bindService failed")
            try {
                context.unbindService(this) // still required after a failed bind
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Service was not bound", e)
            }
            died()
        }
    }

    override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
        try {
            binder.linkToDeath(this, 0)
            val connected = IDaemonHost.Stub.asInterface(binder)
            pid = connected.pid
            connected.registerCallback(callback)
            host = connected
            events.onConnected()
        } catch (e: RemoteException) {
            Log.w(TAG, "Router process died while connecting", e)
            died()
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) = died()

    override fun onBindingDied(name: ComponentName?) = died()

    override fun onNullBinding(name: ComponentName?) = died()

    override fun binderDied() = died()

    private fun died() {
        host = null
        if (dead.compareAndSet(false, true)) events.onDied()
    }

    private inline fun call(block: (IDaemonHost) -> Unit) {
        val current = host ?: return
        try {
            block(current)
        } catch (e: RemoteException) {
            Log.w(TAG, "Router call failed", e)
        }
    }

    override fun start(params: StartParams) = call { it.start(params) }

    override fun stop() = call { it.stop() }

    override fun setAcceptsTransit(accept: Boolean) = call { it.setAcceptsTransit(accept) }

    override fun reloadTunnels() = call { it.reloadTunnels() }

    override fun setOnline(online: Boolean) = call { it.setOnline(online) }

    override fun kill() {
        val target = pid
        if (target > 0 && target != Process.myPid()) {
            Log.w(TAG, "Killing router process $target")
            Process.killProcess(target)
        }
    }

    override fun close() {
        if (bound.compareAndSet(true, false)) {
            try {
                context.unbindService(this)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Service was not bound", e)
            }
        }
    }

    private companion object {
        const val TAG = "i2pd-connector"
    }
}
