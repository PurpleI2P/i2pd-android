package org.purplei2p.i2pd.service

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Entry point for the UI and system triggers. Lives in the main process only.
 *
 * Whenever the router becomes active, [RouterService] is started to keep the process
 * in the foreground; the service stops itself once the router is inactive.
 */
object RouterController {
    @Volatile
    private var supervisor: RouterSupervisor? = null

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

    /** Whether the user left the router running; used to restore it after the app process was killed. */
    fun isWanted(context: Context): Boolean = prefs(context).getBoolean(KEY_WANTED, false)

    internal fun supervisor(context: Context): RouterSupervisor =
        supervisor ?: synchronized(this) {
            supervisor ?: create(context.applicationContext).also { supervisor = it }
        }

    private fun create(context: Context): RouterSupervisor {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val preparer = DataDirPreparer(context)
        val created = RouterSupervisor(
            scope = scope,
            connector = AndroidDaemonConnector(context),
            prepare = { withContext(Dispatchers.IO) { preparer.prepare() } },
            onWantedChanged = { wanted -> prefs(context).edit { putBoolean(KEY_WANTED, wanted) } },
        )
        scope.launch {
            created.state.map { it.isActive }.distinctUntilChanged().collect { active ->
                if (active) ContextCompat.startForegroundService(context, RouterService.intent(context))
            }
        }
        return created
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PREFS = "router"
    private const val KEY_WANTED = "wanted"
}
