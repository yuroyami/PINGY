package com.yuroyami.pingy.utils

import com.yuroyami.pingy.logic.PingEvent

/**
 * What a panel needs from an engine.
 *
 * [PingEngine] is the real one. The interface exists so lifecycle tests can
 * drive a stop that takes as long as they like, which is where the interesting
 * ordering bugs are.
 */
interface ProbeEngine {
    fun start(onEvent: (PingEvent) -> Unit): Boolean
    fun stop()
    suspend fun stopAndJoin()
    fun updateInterval(intervalMs: Long)
    fun updatePacketSize(packetSize: Int)
}

/** How a panel builds its engine. Production wiring is [PingEngine]'s constructor. */
typealias EngineFactory = (host: String, packetSize: Int, intervalMs: Long) -> ProbeEngine
