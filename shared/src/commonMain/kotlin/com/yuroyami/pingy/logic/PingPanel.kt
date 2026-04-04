package com.yuroyami.pingy.logic

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.yuroyami.pingy.utils.PingEngine
import com.yuroyami.pingy.utils.loggy
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/** Maximum number of pings retained per panel to prevent unbounded memory growth. */
private const val MAX_PINGS = 2000

/** Wrapper Model class for a single Ping graph panel and its current parameters */
class PingPanel(
    val ip: String,
) {
    /** The whole collection of pings for this panel (bounded to [MAX_PINGS]) */
    val pings = mutableStateListOf<Ping>()

    /** Pinging Parameters */
    var isPinging = mutableStateOf(true)
    private var packetsize = mutableStateOf(32)
    var interval = mutableStateOf(200L) /* Interval in ms. Default 200ms (5 pings/sec) */

    /** UI-related parameters */
    var widthette = mutableStateOf(3)
    var roof = mutableStateOf(1000)
    var angleOfAttack = mutableStateOf(8.0f)
    val pingStock = mutableStateOf(200)
    var landMarks = mutableStateOf(listOf(25f, 50f, 100f, 200f, 500f))
    var expanded = mutableStateOf(true)

    /** For Statistics */
    var pingsSent = mutableStateOf(0)
    var pingsLost = mutableStateOf(0)
    var lowestPing = mutableStateOf<Int?>(null)
    var highestPing = mutableStateOf<Int?>(null)

    /** The platform-specific ping engine tied to this panel's lifecycle */
    private var engine: PingEngine? = null

    fun startPinging() {
        if (engine != null) return

        engine = PingEngine(
            host = ip,
            packetSize = packetsize.value,
            intervalMs = interval.value,
        ).also { eng ->
            eng.start { rttMs ->
                try {
                    val value = rttMs?.roundToInt()
                    val ping = Ping(
                        value = value,
                        timestamp = TimeSource.Monotonic.markNow()
                    )

                    pings.add(ping)
                    // Prune oldest pings when over capacity
                    while (pings.size > MAX_PINGS) {
                        pings.removeAt(0)
                    }

                    pingsSent.value++
                    if (ping.value == null || ping.value < 0) pingsLost.value++
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
