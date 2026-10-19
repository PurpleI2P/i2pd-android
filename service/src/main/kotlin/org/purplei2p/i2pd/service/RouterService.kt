package org.purplei2p.i2pd.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the main process (and through the binding, the :router process)
 * alive while the router is active. It survives removal of the task from recents.
 */
class RouterService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notifications: RouterNotifications
    private var lastNotified: RouterState? = null
    private var lastNotifiedAt = 0L
    private var foreground = false

    /** Until the first command is handled an inactive state means "not started yet", not "stop". */
    private var commandHandled = false

    override fun onCreate() {
        super.onCreate()
        notifications = RouterNotifications(this)
        notifications.createChannels()
        val supervisor = RouterController.supervisor(this)
        scope.launch {
            supervisor.state.collect { state -> onState(state) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must be called promptly after startForegroundService, whatever the command is
        goForeground(RouterController.state(this).value)
        val supervisor = RouterController.supervisor(this)
        when (intent?.action) {
            null -> if (RouterController.isWanted(this)) supervisor.start() // restarted by the system
            ACTION_SYNC -> Unit
            ACTION_STOP_GRACEFUL -> supervisor.stopGraceful()
            ACTION_STOP_NOW -> supervisor.stopNow()
            ACTION_CANCEL_GRACEFUL -> supervisor.cancelGraceful()
            ACTION_RESTART -> supervisor.restart()
        }
        commandHandled = true
        if (!supervisor.state.value.isActive) stopSelfIfIdle(supervisor.state.value)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun onState(state: RouterState) {
        if (!state.isActive) {
            if (commandHandled) stopSelfIfIdle(state)
            return
        }
        val previous = lastNotified
        val now = SystemClock.elapsedRealtime()
        // Lifecycle changes are shown at once, status updates are throttled
        val significant = previous == null || previous.lifecycle != state.lifecycle ||
            previous.restartPending != state.restartPending || previous.autoRestartAt != state.autoRestartAt
        if (significant || now - lastNotifiedAt >= STATUS_UPDATE_INTERVAL_MS) {
            if (foreground) notifications.updateStatus(state) else goForeground(state)
            lastNotified = state
            lastNotifiedAt = now
        }
    }

    private fun goForeground(state: RouterState) {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        notifications.cancelFailure()
        ServiceCompat.startForeground(this, RouterNotifications.STATUS_ID, notifications.status(state), type)
        foreground = true
        lastNotified = state
        lastNotifiedAt = SystemClock.elapsedRealtime()
    }

    private fun stopSelfIfIdle(state: RouterState) {
        if (foreground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foreground = false
        }
        // A failure stays visible after the service is gone
        (state.lifecycle as? Lifecycle.Failed)?.let { notifications.showFailure(it.reason) }
        lastNotified = null
        stopSelf()
    }

    companion object {
        const val ACTION_SYNC = "org.purplei2p.i2pd.action.SYNC"
        const val ACTION_STOP_GRACEFUL = "org.purplei2p.i2pd.action.STOP_GRACEFUL"
        const val ACTION_STOP_NOW = "org.purplei2p.i2pd.action.STOP_NOW"
        const val ACTION_CANCEL_GRACEFUL = "org.purplei2p.i2pd.action.CANCEL_GRACEFUL"
        const val ACTION_RESTART = "org.purplei2p.i2pd.action.RESTART"

        private const val STATUS_UPDATE_INTERVAL_MS = 5_000L

        fun intent(context: Context, action: String = ACTION_SYNC): Intent =
            Intent(context, RouterService::class.java).setAction(action)
    }
}
