package org.purplei2p.i2pd.daemon.api

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** Everything the :router process needs to start i2pd. */
@Parcelize
data class StartParams(
    val dataDir: String,
    /** Command line arguments without argv[0], e.g. "--conf=/path/i2pd.conf". */
    val args: List<String>,
    /** Web console language, e.g. "english". */
    val language: String,
) : Parcelable

/** Network status of the router, i2p::RouterStatus. */
enum class RouterStatus {
    OK, FIREWALLED, UNKNOWN, PROXY, MESH, STAN;

    companion object {
        fun fromCode(code: Long): RouterStatus = entries.getOrElse(code.toInt()) { UNKNOWN }
    }
}

/** Network error of the router, i2p::RouterError. */
enum class RouterError {
    NONE, CLOCK_SKEW, OFFLINE, SYMMETRIC_NAT, FULL_CONE_NAT, NO_DESCRIPTORS;

    companion object {
        fun fromCode(code: Long): RouterError = entries.getOrElse(code.toInt()) { NONE }
    }
}

/** Client services that may be running inside the router. */
enum class ClientService {
    HTTP_PROXY, SOCKS_PROXY, SAM, BOB, I2CP, WEB_CONSOLE,
}

/** Snapshot of the running router, taken about once per second. */
@Parcelize
data class StatusSnapshot(
    val statusV4: RouterStatus,
    val statusV6: RouterStatus,
    val errorV4: RouterError,
    val errorV6: RouterError,
    val testingV4: Boolean,
    val testingV6: Boolean,
    val uptimeSeconds: Long,
    val acceptsTransit: Boolean,
    val floodfill: Boolean,
    /** Bytes per second, averaged over 15 seconds. */
    val bandwidthIn: Long,
    val bandwidthOut: Long,
    val bandwidthTransit: Long,
    /** Bytes since start. */
    val totalReceived: Long,
    val totalSent: Long,
    val totalTransit: Long,
    val tunnelsIn: Int,
    val tunnelsOut: Int,
    val tunnelsTransit: Int,
    /** Percent. */
    val tunnelSuccessRate: Int,
    val routers: Int,
    val floodfills: Int,
    val leaseSets: Int,
    val services: Set<ClientService>,
    val netId: Int,
    /** Null if the web console is disabled. */
    val webConsoleUrl: String?,
) : Parcelable

/** Log lines starting at [firstSeq]; the next read should ask for lines after [lastSeq]. */
@Parcelize
data class LogChunk(
    val firstSeq: Long,
    val lines: List<String>,
) : Parcelable {
    val lastSeq: Long get() = firstSeq + lines.size - 1
}
