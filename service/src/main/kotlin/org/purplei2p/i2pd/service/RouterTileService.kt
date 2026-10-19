package org.purplei2p.i2pd.service

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick settings tile: shows the router state, starts it or stops it gracefully. Tiles exist since Android 7. */
@RequiresApi(24)
class RouterTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listening = scope.launch {
            RouterController.state(this@RouterTileService).collect { update(it) }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        val state = RouterController.state(this).value
        when {
            state.lifecycle is Lifecycle.Draining -> RouterController.cancelGraceful(this)
            state.isActive -> RouterController.stopGraceful(this)
            // Starting a foreground service from a tile is not always allowed: go through an activity then
            !RouterController.startService(this, RouterService.ACTION_START) -> startViaActivity()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun startViaActivity() {
        val intent = Intent(this, StartRouterActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun update(state: RouterState) {
        val tile = qsTile ?: return
        tile.state = when {
            state.lifecycle is Lifecycle.Failed && state.autoRestartAt == null -> Tile.STATE_INACTIVE
            state.isActive -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_notification_icon)
        tile.label = getString(R.string.tile_label)
        val title = RouterStateText.title(this, state)
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = title
        // Custom tiles do not expose on/off to accessibility by themselves
        if (Build.VERSION.SDK_INT >= 30) tile.stateDescription = title
        tile.updateTile()
    }
}

/** Invisible activity that starts the router: activities in front may start foreground services. */
class StartRouterActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Started here, while this activity is in front, not later from a coroutine
        RouterController.startService(this, RouterService.ACTION_START)
        finish()
    }
}
