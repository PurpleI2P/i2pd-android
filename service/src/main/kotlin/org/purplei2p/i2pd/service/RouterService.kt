package org.purplei2p.i2pd.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var powerMonitor: PowerMonitor
    private var wakeLock: PowerManager.WakeLock? = null

    /** Until the first command is handled an inactive state means "not started yet", not "stop". */
    private var commandHandled = false

    override fun onCreate() {
        super.onCreate()
        notifications = RouterNotifications(this)
        notifications.createChannels()
        val supervisor = RouterController.supervisor(this)
        networkMonitor = NetworkMonitor(this) { online -> supervisor.setOnline(online) }
        networkMonitor.start()
        powerMonitor = PowerMonitor(this)
        powerMonitor.start()
        scope.launch {
            supervisor.state.collect { state -> onState(state) }
        }
        scope.launch {
            combine(
                supervisor.state,
                RouterController.settings(this@RouterService).settings,
                powerMonitor.plugged,
            ) { state, settings, plugged ->
                val wanted = when (settings.keepAwake) {
                    KeepAwake.OFF -> false
                    KeepAwake.ALWAYS -> true
                    KeepAwake.WHILE_CHARGING -> plugged
                }
                wanted && state.lifecycle.hasRouterProcess
            }.distinctUntilChanged().collect { keepAwake -> setWakeLock(keepAwake) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must be called promptly after startForegroundService, whatever the command is
        goForeground(RouterController.state(this).value)
        val supervisor = RouterController.supervisor(this)
        when (intent?.action) {
            null -> if (RouterController.isWanted(this)) supervisor.start() // restarted by the system
            ACTION_SYNC -> Unit
            ACTION_START -> supervisor.start()
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
        networkMonitor.stop()
        powerMonitor.stop()
        setWakeLock(false)
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

    // Without it the CPU sleeps in deep sleep: router timers stop and transit tunnels break
    @SuppressLint("WakelockTimeout", "Wakelock") // held exactly while the router runs, released on stop or destroy
    private fun setWakeLock(enabled: Boolean) {
        val current = wakeLock
        if (enabled && current == null) {
            val power = getSystemService(PowerManager::class.java)
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "i2pd:router").apply {
                setReferenceCounted(false)
                acquire()
            }
        } else if (!enabled && current != null) {
            if (current.isHeld) current.release()
            wakeLock = null
        }
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
        const val ACTION_START = "org.purplei2p.i2pd.action.START"
        const val ACTION_STOP_GRACEFUL = "org.purplei2p.i2pd.action.STOP_GRACEFUL"
        const val ACTION_STOP_NOW = "org.purplei2p.i2pd.action.STOP_NOW"
        const val ACTION_CANCEL_GRACEFUL = "org.purplei2p.i2pd.action.CANCEL_GRACEFUL"
        const val ACTION_RESTART = "org.purplei2p.i2pd.action.RESTART"

        private const val STATUS_UPDATE_INTERVAL_MS = 5_000L

        fun intent(context: Context, action: String = ACTION_SYNC): Intent =
            Intent(context, RouterService::class.java).setAction(action)
    }
}
