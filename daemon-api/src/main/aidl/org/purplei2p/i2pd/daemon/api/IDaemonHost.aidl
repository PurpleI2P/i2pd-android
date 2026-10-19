package org.purplei2p.i2pd.daemon.api;

import org.purplei2p.i2pd.daemon.api.IDaemonCallback;
import org.purplei2p.i2pd.daemon.api.LogChunk;
import org.purplei2p.i2pd.daemon.api.StartParams;
import org.purplei2p.i2pd.daemon.api.StatusSnapshot;

/**
 * Implemented by DaemonHostService in the :router process.
 * The router can be started only once per process: stop() ends the process.
 */
interface IDaemonHost {
    int getPid();

    void registerCallback(IDaemonCallback callback);

    oneway void start(in StartParams params);

    /** Stops the router and exits the process. */
    oneway void stop();

    oneway void setAcceptsTransit(boolean accept);

    oneway void reloadTunnels();

    oneway void setOnline(boolean online);

    /** Null if the router is not running. */
    StatusSnapshot getStatus();

    LogChunk readLog(long sinceSeq, int max);
}
