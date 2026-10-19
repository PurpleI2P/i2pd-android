package org.purplei2p.i2pd.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Status notification of the foreground service and the failure notification. */
class RouterNotifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val status = NotificationChannel(
            CHANNEL_STATUS,
            context.getString(R.string.channel_status),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        val failures = NotificationChannel(
            CHANNEL_FAILURES,
            context.getString(R.string.channel_failures),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(status, failures))
    }

    fun status(state: RouterState): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentTitle(RouterStateText.title(context, state))
            .setContentText(RouterStateText.summary(context, state))
            .setContentIntent(openApp())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        when (state.lifecycle) {
            Lifecycle.Running -> {
                builder.addAction(0, context.getString(R.string.action_stop), action(RouterService.ACTION_STOP_GRACEFUL))
                builder.addAction(0, context.getString(R.string.action_stop_now), action(RouterService.ACTION_STOP_NOW))
                builder.addAction(0, context.getString(R.string.action_restart), action(RouterService.ACTION_RESTART))
            }
            is Lifecycle.Draining -> {
                builder.addAction(0, context.getString(R.string.action_cancel), action(RouterService.ACTION_CANCEL_GRACEFUL))
                builder.addAction(0, context.getString(R.string.action_stop_now), action(RouterService.ACTION_STOP_NOW))
            }
            Lifecycle.Preparing, Lifecycle.Starting, Lifecycle.Stopping, is Lifecycle.Failed ->
                builder.addAction(0, context.getString(R.string.action_stop_now), action(RouterService.ACTION_STOP_NOW))
            Lifecycle.Stopped -> Unit
        }
        return builder.build()
    }

    @SuppressLint("MissingPermission") // checked by canPost()
    fun updateStatus(state: RouterState) {
        if (canPost()) manager.notify(STATUS_ID, status(state))
    }

    @SuppressLint("MissingPermission") // checked by canPost()
    fun showFailure(reason: FailureReason) {
        if (!canPost()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_FAILURES)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentTitle(context.getString(R.string.state_failed))
            .setContentText(RouterStateText.failure(context, reason))
            .setStyle(NotificationCompat.BigTextStyle().bigText(RouterStateText.failure(context, reason)))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        manager.notify(FAILURE_ID, notification)
    }

    fun cancelFailure() = manager.cancel(FAILURE_ID)

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return manager.areNotificationsEnabled()
    }

    private fun openApp(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun action(action: String): PendingIntent {
        val intent = RouterService.intent(context, action)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        // Distinct request codes keep the actions apart
        return if (Build.VERSION.SDK_INT >= 26) {
            PendingIntent.getForegroundService(context, action.hashCode(), intent, flags)
        } else {
            PendingIntent.getService(context, action.hashCode(), intent, flags)
        }
    }

    companion object {
        const val STATUS_ID = 1
        const val FAILURE_ID = 2
        private const val CHANNEL_STATUS = "router_status"
        private const val CHANNEL_FAILURES = "router_failures"
    }
}
