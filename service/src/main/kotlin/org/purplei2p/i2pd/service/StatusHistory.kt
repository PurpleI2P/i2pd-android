package org.purplei2p.i2pd.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.purplei2p.i2pd.daemon.api.StatusSnapshot

/** Values of one status update shown on the graphs: speeds in bytes per second and tunnel counts. */
data class HistorySample(
    val inbound: Long,
    val outbound: Long,
    val transit: Long,
    val tunnelsIn: Int,
    val tunnelsOut: Int,
    val tunnelsTransit: Int,
)

/**
 * The last [capacity] status updates of the running router (one per second, so five minutes by
 * default) for the graphs on the status screen. Cleared when the router stops.
 */
class StatusHistory(
    private val scope: CoroutineScope,
    private val status: Flow<StatusSnapshot?>,
    private val capacity: Int = 300,
) {
    private val _samples = MutableStateFlow<List<HistorySample>>(emptyList())
    val samples: StateFlow<List<HistorySample>> = _samples.asStateFlow()

    fun start() {
        scope.launch {
            var last: StatusSnapshot? = null
            status.collect { snapshot ->
                when {
                    snapshot == null -> _samples.value = emptyList()
                    // The same snapshot again, e.g. the state changed for another reason
                    snapshot === last -> Unit
                    else -> _samples.value = (_samples.value + snapshot.toSample()).takeLast(capacity)
                }
                last = snapshot
            }
        }
    }

    private fun StatusSnapshot.toSample() =
        HistorySample(bandwidthIn, bandwidthOut, bandwidthTransit, tunnelsIn, tunnelsOut, tunnelsTransit)
}
