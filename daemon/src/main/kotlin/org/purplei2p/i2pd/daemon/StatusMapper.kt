package org.purplei2p.i2pd.daemon

import org.purplei2p.i2pd.daemon.api.ClientService
import org.purplei2p.i2pd.daemon.api.RouterError
import org.purplei2p.i2pd.daemon.api.RouterStatus
import org.purplei2p.i2pd.daemon.api.StatusSnapshot
import org.purplei2p.i2pd.nativelib.I2pdNative.Status

/** Converts the raw array filled by I2pdNative.status into [StatusSnapshot]. */
internal object StatusMapper {
    private val serviceBits = listOf(
        Status.SERVICE_HTTP_PROXY to ClientService.HTTP_PROXY,
        Status.SERVICE_SOCKS_PROXY to ClientService.SOCKS_PROXY,
        Status.SERVICE_SAM to ClientService.SAM,
        Status.SERVICE_BOB to ClientService.BOB,
        Status.SERVICE_I2CP to ClientService.I2CP,
        Status.SERVICE_WEB_CONSOLE to ClientService.WEB_CONSOLE,
    )

    fun map(raw: LongArray, webConsoleUrl: String?): StatusSnapshot {
        val services = serviceBits.filter { (bit, _) -> raw[Status.SERVICES] and bit != 0L }
            .mapTo(mutableSetOf()) { (_, service) -> service }
        return StatusSnapshot(
            statusV4 = RouterStatus.fromCode(raw[Status.STATUS_V4]),
            statusV6 = RouterStatus.fromCode(raw[Status.STATUS_V6]),
            errorV4 = RouterError.fromCode(raw[Status.ERROR_V4]),
            errorV6 = RouterError.fromCode(raw[Status.ERROR_V6]),
            testingV4 = raw[Status.TESTING_V4] != 0L,
            testingV6 = raw[Status.TESTING_V6] != 0L,
            uptimeSeconds = raw[Status.UPTIME],
            acceptsTransit = raw[Status.ACCEPTS_TRANSIT] != 0L,
            floodfill = raw[Status.FLOODFILL] != 0L,
            bandwidthIn = raw[Status.BANDWIDTH_IN],
            bandwidthOut = raw[Status.BANDWIDTH_OUT],
            bandwidthTransit = raw[Status.BANDWIDTH_TRANSIT],
            totalReceived = raw[Status.TOTAL_RECEIVED],
            totalSent = raw[Status.TOTAL_SENT],
            totalTransit = raw[Status.TOTAL_TRANSIT],
            tunnelsIn = raw[Status.TUNNELS_IN].toInt(),
            tunnelsOut = raw[Status.TUNNELS_OUT].toInt(),
            tunnelsTransit = raw[Status.TUNNELS_TRANSIT].toInt(),
            tunnelSuccessRate = raw[Status.TUNNEL_SUCCESS_RATE].toInt(),
            routers = raw[Status.ROUTERS].toInt(),
            floodfills = raw[Status.FLOODFILLS].toInt(),
            leaseSets = raw[Status.LEASE_SETS].toInt(),
            services = services,
            netId = raw[Status.NET_ID].toInt(),
            webConsoleUrl = webConsoleUrl.takeIf { ClientService.WEB_CONSOLE in services },
        )
    }
}
