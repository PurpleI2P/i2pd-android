package org.purplei2p.i2pd.daemon.api;

import org.purplei2p.i2pd.daemon.api.StatusSnapshot;

/** Implemented by the supervisor in the main process. */
interface IDaemonCallback {
    oneway void onStarted(String version);

    oneway void onStartFailed(String message);

    /**
     * Fatal error reported by i2pd. Synchronous on purpose: i2pd may call exit() right after,
     * and the supervisor must learn the reason before it sees the binder death.
     */
    void onFatal(String message);

    /** Sent about once per second while the router is running. */
    oneway void onStatus(in StatusSnapshot status);
}
