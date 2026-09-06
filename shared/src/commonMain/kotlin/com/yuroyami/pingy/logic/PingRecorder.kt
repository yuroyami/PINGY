package com.yuroyami.pingy.logic

/**
 * Writes engine events into a panel's history: one slot per probe, claimed at
 * send time and filled in place by the verdict.
 *
 * Engine-thread only, like the ring it writes. One instance per engine run, so
 * sequence numbers from a restarted engine can never meet a stale slot.
 */
internal class PingRecorder(private val pings: RingBuffer<Ping>) {

    private class Claim(val slot: Int, val pending: Ping)

    /** Probes still in the air, keyed by the engine's sequence number. */
    private val claims = HashMap<Int, Claim>()

    fun accept(event: PingEvent) {
        when (event) {
            is PingEvent.Sent -> claims[event.seq] = Claim(pings.add(event.ping), event.ping)
            is PingEvent.Resolved -> claims.remove(event.seq)?.let { claim ->
                pings.replace(claim.slot, claim.pending, event.ping)
            }
            is PingEvent.Fault -> pings.add(event.ping)
            is PingEvent.Endpoint -> Unit   // not a probe, nothing to record
        }
    }
}
