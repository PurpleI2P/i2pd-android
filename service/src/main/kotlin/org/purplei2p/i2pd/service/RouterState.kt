package org.purplei2p.i2pd.service

import org.purplei2p.i2pd.daemon.api.StatusSnapshot

/** Lifecycle of the router as seen by the supervisor, see docs/DESIGN.md §6. */
sealed interface Lifecycle {
    data object Stopped : Lifecycle

    /** Preparing the data directory, the router process is not bound yet. */
    data object Preparing : Lifecycle

    /** The :router process is bound and i2pd is initializing. */
    data object Starting : Lifecycle

    data object Running : Lifecycle

    /** Graceful stop: transit tunnels are declined, waiting until they end or until [deadline]. */
    data class Draining(val deadline: Long) : Lifecycle

    data object Stopping : Lifecycle

    data class Failed(val reason: FailureReason) : Lifecycle

    /** A router process may exist and be talked to. */
    val hasRouterProcess: Boolean
        get() = this == Starting || this == Running || this is Draining || this == Stopping
}

sealed interface FailureReason {
    /** Data directory could not be prepared. */
    data class PrepareFailed(val message: String) : FailureReason

    /** i2pd init or start returned an error. */
    data class StartFailed(val message: String) : FailureReason

    /** i2pd reported a fatal error (usually a broken config) and its process exited. */
    data class Fatal(val message: String) : FailureReason

    /** The router process died unexpectedly. */
    data object Crashed : FailureReason

    /** i2pd did not report start result in time. */
    data object StartTimeout : FailureReason
}

data class RouterState(
    val lifecycle: Lifecycle = Lifecycle.Stopped,
    /** The router will be started again once the current stop completes. */
    val restartPending: Boolean = false,
    /** Last status of the running router, null when it is not running. */
    val status: StatusSnapshot? = null,
    /** i2pd version reported by the running router. */
    val version: String? = null,
    /** Time of the scheduled automatic restart after a crash, null if none. */
    val autoRestartAt: Long? = null,
    /** An operation that needs the router stopped is running (docs/DESIGN.md §6.6). */
    val operationInProgress: Boolean = false,
    /** The router was stopped for such operations and will be started after them. */
    val resumeAfterOperations: Boolean = false,
) {
    /** True while the router is running or about to run: the foreground service must be kept. */
    val isActive: Boolean
        get() = when (lifecycle) {
            Lifecycle.Stopped -> restartPending || resumeAfterOperations
            is Lifecycle.Failed -> autoRestartAt != null || resumeAfterOperations
            else -> true
        }
}
