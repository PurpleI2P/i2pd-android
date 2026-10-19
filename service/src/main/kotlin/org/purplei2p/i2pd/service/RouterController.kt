package org.purplei2p.i2pd.service

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.purplei2p.i2pd.config.OptionChange

/**
 * Entry point for the UI and system triggers. Lives in the main process only.
 *
 * Whenever the router becomes active, [RouterService] is started to keep the process
 * in the foreground; the service stops itself once the router is inactive.
 */
object RouterController {
    private class Runtime(val supervisor: RouterSupervisor, val logs: LogCollector)

    @Volatile
    private var runtime: Runtime? = null

    fun state(context: Context): StateFlow<RouterState> = supervisor(context).state

    fun start(context: Context) = supervisor(context).start()

    fun stopGraceful(context: Context) = supervisor(context).stopGraceful()

    fun stopNow(context: Context) = supervisor(context).stopNow()

    fun cancelGraceful(context: Context) = supervisor(context).cancelGraceful()

    fun restart(context: Context, graceful: Boolean = false) = supervisor(context).restart(graceful)

    fun dismiss(context: Context) = supervisor(context).dismiss()

    fun reloadTunnels(context: Context) = supervisor(context).reloadTunnels()

    /** See [RouterSupervisor.runWithRouterStopped]. */
    fun runWithRouterStopped(context: Context, block: suspend () -> Unit): Deferred<Result<Unit>> =
        supervisor(context).runWithRouterStopped(block)

    fun logs(context: Context): LogCollector = runtime(context).logs

    fun settings(context: Context): AppSettingsStore = AppSettingsStore.get(context)

    fun config(context: Context): RouterConfig = RouterConfig.get(context)

    /** Options changed in i2pd.conf after the running router was started; a restart applies them. */
    fun pendingChanges(context: Context): Flow<List<OptionChange>> = config(context).pendingChanges(state(context))

    /** Whether the user left the router running; used to restore it after the app process was killed. */
    fun isWanted(context: Context): Boolean = prefs(context).getBoolean(KEY_WANTED, false)

    internal fun supervisor(context: Context): RouterSupervisor = runtime(context).supervisor

    private fun runtime(context: Context): Runtime =
        runtime ?: synchronized(this) {
            runtime ?: create(context.applicationContext).also { runtime = it }
        }

    private fun create(context: Context): Runtime {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val preparer = DataDirPreparer(context)
        val settings = AppSettingsStore.get(context).settings
        val supervisor = RouterSupervisor(
            scope = scope,
            connector = AndroidDaemonConnector(context),
            prepare = {
                withContext(Dispatchers.IO) {
                    preparer.prepare().also { RouterConfig.get(context).onRouterStarting() }
                }
            },
            config = {
                val current = settings.value
                SupervisorConfig(
                    gracefulTimeoutMs = current.gracefulTimeoutMinutes * 60_000L,
                    autoRestart = current.autoRestart,
                )
            },
            onWantedChanged = { wanted -> prefs(context).edit { putBoolean(KEY_WANTED, wanted) } },
        )
        scope.launch {
            supervisor.state.map { it.isActive }.distinctUntilChanged().collect { active ->
                if (active) startService(context)
            }
        }
        val logs = LogCollector(
            scope = scope,
            source = supervisor::readLog,
            routerPresent = supervisor.state.map { it.lifecycle.hasRouterProcess },
        )
        logs.start()
        return Runtime(supervisor, logs)
    }

    /** Starts [RouterService]; false if the system does not allow it right now (app in background). */
    internal fun startService(context: Context, action: String = RouterService.ACTION_SYNC): Boolean =
        try {
            ContextCompat.startForegroundService(context, RouterService.intent(context, action))
            true
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException on Android 12+
            Log.w("i2pd-controller", "Cannot start router service now", e)
            false
        }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PREFS = "router"
    private const val KEY_WANTED = "wanted"
}
