package org.purplei2p.i2pd.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.purplei2p.i2pd.daemon.api.RouterError
import org.purplei2p.i2pd.daemon.api.RouterStatus
import org.purplei2p.i2pd.daemon.api.StatusSnapshot

@OptIn(ExperimentalCoroutinesApi::class)
class StatusHistoryTest {

    private fun status(inbound: Long, uptime: Long) = StatusSnapshot(
        statusV4 = RouterStatus.OK, statusV6 = RouterStatus.UNKNOWN,
        errorV4 = RouterError.NONE, errorV6 = RouterError.NONE,
        testingV4 = false, testingV6 = false,
        uptimeSeconds = uptime, acceptsTransit = true, floodfill = false,
        bandwidthIn = inbound, bandwidthOut = inbound / 2, bandwidthTransit = 0,
        totalReceived = 0, totalSent = 0, totalTransit = 0,
        tunnelsIn = uptime.toInt(), tunnelsOut = 2, tunnelsTransit = 0, tunnelSuccessRate = 50,
        routers = 100, floodfills = 10, leaseSets = 5,
        services = emptySet(), netId = 2, webConsoleUrl = null,
    )

    @Test
    fun `keeps the last samples and clears on stop`() = runTest {
        val source = MutableStateFlow<StatusSnapshot?>(null)
        val history = StatusHistory(backgroundScope, source, capacity = 3)
        history.start()
        runCurrent()

        for (second in 1L..5L) {
            source.value = status(inbound = second * 100, uptime = second)
            runCurrent()
        }
        assertEquals(listOf(300L, 400L, 500L), history.samples.value.map { it.inbound })
        assertEquals(250L, history.samples.value.last().outbound)
        assertEquals(listOf(3, 4, 5), history.samples.value.map { it.tunnelsIn })

        source.value = null
        runCurrent()
        assertEquals(emptyList<HistorySample>(), history.samples.value)
    }
}
