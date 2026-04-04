package com.yuroyami.pingy.logic

import com.yuroyami.pingy.utils.PingEngine
import com.yuroyami.pingy.utils.loggy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/** Maximum number of pings retained per panel to prevent unbounded memory growth. */
private const val MAX_PINGS = 2000

/** Wrapper Model class for a single Ping graph panel and its current parameters */
class PingPanel(
    val ip: String,
) {
    /** The whole collection of pings for this panel in a ring buffer */
    val pings = MutableStateFlow(RingBuffer<Ping>(MAX_PINGS))

    /** Pinging Parameters */
    val isPinging = MutableStateFlow(true)
    val packetSize = MutableStateFlow(32)
    val interval = MutableStateFlow(200L) /* Interval in ms. Default 200ms (5 pings/sec) */

    /** UI-related parameters */
    val widthette = MutableStateFlow(3)
    val roof = MutableStateFlow(1000)
    val angleOfAttack = MutableStateFlow(8.0f)
    val pingStock = MutableStateFlow(200)
    val landMarks = MutableStateFlow(listOf(25f, 50f, 100f, 200f, 500f))
    val expanded = MutableStateFlow(true)

    /** Controls whether the panel shows stats or settings */
    val showSettings = MutableStateFlow(false)

    /** For Statistics */
    val pingsSent = MutableStateFlow(0)
    val pingsLost = MutableStateFlow(0)
    val lowestPing = MutableStateFlow<Int?>(null)
    val highestPing = MutableStateFlow<Int?>(null)

    /** The platform-specific ping engine tied to this panel's lifecycle */
    private var engine: PingEngine? = null

    /** A version counter that bumps on every ping addition, used to trigger recomposition. */
    val pingVersion = MutableStateFlow(0L)

    fun startPinging() {
        if (engine != null) return

        engine = PingEngine(
            host = ip,
            packetSize = packetSize.value,
            intervalMs = interval.value,
        ).also { eng ->
            eng.start { rttMs ->
                try {
                    val value = rttMs?.roundToInt()
                    val ping = Ping(
                        value = value,
                        timestamp = TimeSource.Monotonic.markNow()
                    )

                    pings.value.add(ping)
                    pingVersion.update { it + 1 }

                    pingsSent.update { it + 1 }
                    if (ping.value == null || ping.value < 0) {
                        pingsLost.update { it + 1 }
                    }

                    // Update min/max incrementally
                    val v = ping.value
                    if (v != null && v >= 0) {
                        lowestPing.update { current -> if (current == null || v < current) v else current }
                        highestPing.update { current -> if (current == null || v > current) v else current }
                    }
                } catch (e: Exception) {
                    loggy(e.stackTraceToString())
                }
            }
        }
    }

    /** Stops pinging and releases the engine resources. */
    fun stopPinging() {
        engine?.stop()
        engine = null
    }

    /** Call when removing this panel entirely. */
    fun close() {
        stopPinging()
    }
}
