package org.purplei2p.i2pd.service

import android.content.Context
import android.text.format.Formatter
import org.purplei2p.i2pd.daemon.api.RouterError
import org.purplei2p.i2pd.daemon.api.RouterStatus
import org.purplei2p.i2pd.daemon.api.StatusSnapshot

/** Human readable texts for [RouterState], shared by the notification and the UI. */
object RouterStateText {

    fun title(context: Context, state: RouterState): String {
        if (state.restartPending) return context.getString(R.string.state_restarting)
        if (state.operationInProgress) return context.getString(R.string.state_maintenance)
        return context.getString(
            when (state.lifecycle) {
                Lifecycle.Stopped -> R.string.state_stopped
                Lifecycle.Preparing, Lifecycle.Starting -> R.string.state_starting
                Lifecycle.Running -> R.string.state_running
                is Lifecycle.Draining -> R.string.state_draining
                Lifecycle.Stopping -> R.string.state_stopping
                is Lifecycle.Failed -> R.string.state_failed
            },
        )
    }

    /** One line summary for the notification, null if there is nothing to add to [title]. */
    fun summary(context: Context, state: RouterState, now: Long = System.currentTimeMillis()): String? {
        val status = state.status
        return when (val lifecycle = state.lifecycle) {
            Lifecycle.Running -> status?.let {
                context.getString(
                    R.string.summary_running,
                    network(context, it),
                    speed(context, it.bandwidthIn),
                    speed(context, it.bandwidthOut),
                    it.tunnelsTransit,
                )
            }
            is Lifecycle.Draining -> context.getString(
                R.string.summary_draining,
                status?.tunnelsTransit ?: 0,
                duration((lifecycle.deadline - now) / 1000),
            )
            is Lifecycle.Failed -> {
                val restartAt = state.autoRestartAt
                if (restartAt != null) {
                    context.getString(R.string.summary_auto_restart, duration((restartAt - now) / 1000))
                } else {
                    failure(context, lifecycle.reason)
                }
            }
            else -> null
        }
    }

    fun failure(context: Context, reason: FailureReason): String = when (reason) {
        is FailureReason.PrepareFailed -> context.getString(R.string.failure_prepare, reason.message)
        is FailureReason.StartFailed -> context.getString(R.string.failure_start, reason.message)
        is FailureReason.Fatal -> context.getString(R.string.failure_fatal, reason.message)
        FailureReason.Crashed -> context.getString(R.string.failure_crashed)
        FailureReason.StartTimeout -> context.getString(R.string.failure_start_timeout)
    }

    /** Network status of IPv4 (and IPv6 if it differs), e.g. "OK" or "Firewalled / OK". */
    fun network(context: Context, status: StatusSnapshot): String {
        val v4 = network(context, status.statusV4, status.errorV4, status.testingV4)
        val v6 = network(context, status.statusV6, status.errorV6, status.testingV6)
        return if (v4 == v6) v4 else "$v4 / $v6"
    }

    fun network(context: Context, status: RouterStatus, error: RouterError, testing: Boolean): String {
        val base = context.getString(
            when (status) {
                RouterStatus.OK -> R.string.network_ok
                RouterStatus.FIREWALLED -> R.string.network_firewalled
                RouterStatus.UNKNOWN -> R.string.network_unknown
                RouterStatus.PROXY -> R.string.network_proxy
                RouterStatus.MESH -> R.string.network_mesh
                RouterStatus.STAN -> R.string.network_stan
            },
        )
        val errorText = when (error) {
            RouterError.NONE -> null
            RouterError.CLOCK_SKEW -> R.string.network_error_clock_skew
            RouterError.OFFLINE -> R.string.network_error_offline
            RouterError.SYMMETRIC_NAT -> R.string.network_error_symmetric_nat
            RouterError.FULL_CONE_NAT -> R.string.network_error_full_cone_nat
            RouterError.NO_DESCRIPTORS -> R.string.network_error_no_descriptors
        }?.let(context::getString)
        val testingText = if (testing) context.getString(R.string.network_testing) else null
        return listOfNotNull(base, testingText, errorText).joinToString(", ")
    }

    fun speed(context: Context, bytesPerSecond: Long): String =
        context.getString(R.string.speed, Formatter.formatShortFileSize(context, bytesPerSecond))

    fun bytes(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

    /** Seconds as h:mm:ss or m:ss. */
    fun duration(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val hours = s / 3600
        val minutes = s % 3600 / 60
        val secs = s % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%d:%02d".format(minutes, secs)
    }
}
