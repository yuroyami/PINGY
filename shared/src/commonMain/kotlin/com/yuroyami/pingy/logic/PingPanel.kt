package com.yuroyami.pingy.logic

import com.yuroyami.pingy.utils.PingEngine
import com.yuroyami.pingy.utils.loggye
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/** Maximum number of pings retained per panel to prevent unbounded memory growth. */
private const val MAX_PINGS = 2000

/** Wrapper Model class for a single Ping graph panel and its current parameters.
 *
 * All preferences are exposed as [MutableStateFlow]s so UI sliders (or any observer)
 * can read and write them reactively. Changes to [interval] are propagated to the
 * live [PingEngine] via [PingEngine.updateInterval] so adjustments take effect
 * on-the-fly without restarting the panel. */
@OptIn(FlowPreview::class)
class PingPanel(
    val ip: String,
) {
    /** The whole collection of pings for this panel in a ring buffer.
     * Not wrapped in a Flow: the buffer is a single long-lived instance that
     * mutates in place, so nothing ever re-emits. Observers instead watch
     * [pingVersion], which ticks on every insert. */
    val pings = RingBuffer<Ping>(MAX_PINGS)

    /** Pinging Parameters */
    val isPinging = MutableStateFlow(true)
    val packetSize = MutableStateFlow(DEFAULT_PACKET_SIZE)

    /** Interval between pings in ms. Default 200ms (5 pings/sec).
     * A value of 0 means "fire the next ping as soon as the previous one returns",
     * honored on both platforms via spawn-per-ping on Android and no-delay on iOS. */
    val interval = MutableStateFlow(DEFAULT_INTERVAL_MS)

    /** UI-related graph parameters */
    val roof = MutableStateFlow(DEFAULT_ROOF)                  // max displayed ping value
    val angleOfAttack = MutableStateFlow(DEFAULT_ANGLE_OF_ATTACK) // exponential zoom factor; 0 = linear 1:1
    val landMarks = MutableStateFlow(listOf(25f, 50f, 100f, 200f, 500f))

    /** Sheet state: expanded (visible) or collapsed. Toggled by minimize button. */
    val expanded = MutableStateFlow(true)

    /** Sheet content mode: stats (false) or settings (true). Toggled by clicking the graph. */
    val showSettings = MutableStateFlow(false)

    /** Time window (ms) of pings to keep visible on the canvas. */
    val timeframeMs = MutableStateFlow(DEFAULT_TIMEFRAME_MS)

    /** Canvas height as a fraction of the window's height (0.1 = 10%, 0.4 = 40%). */
    val canvasHeightFraction = MutableStateFlow(DEFAULT_CANVAS_HEIGHT_FRACTION)

    /** For Statistics */
    val pingsSent = MutableStateFlow(0)
    val pingsLost = MutableStateFlow(0)
    val lowestPing = MutableStateFlow<Int?>(null)
    val highestPing = MutableStateFlow<Int?>(null)

    /** The platform-specific ping engine tied to this panel's lifecycle */
    private var engine: PingEngine? = null

    /** A version counter that bumps on every ping addition, used to trigger recomposition. */
    val pingVersion = MutableStateFlow(0L)

    /** Panel-owned coroutine scope for observing preference changes. */
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        // Whenever the interval StateFlow changes (after initial emit), push the new
        // value to the running engine. Debounced so rapid slider drags don't restart
        // the loop's sleep more often than the user actually meant.
        scope.launch {
            interval.drop(1).debounce(150).collect { newInterval ->
                engine?.updateInterval(newInterval)
            }
        }
        // Packet size changes apply to the very next probe, no debounce needed.
        scope.launch {
            packetSize.drop(1).collect { newSize ->
                engine?.updatePacketSize(newSize)
            }
        }
    }

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

                    pings.add(ping)
                    pingVersion.update { it + 1 }

                    pingsSent.update { it + 1 }
                    if (ping.value == null || ping.value < 0) {
                        pingsLost.update { it + 1 }
                    }

                    val v = ping.value
                    if (v != null && v >= 0) {
                        lowestPing.update { current -> if (current == null || v < current) v else current }
                        highestPing.update { current -> if (current == null || v > current) v else current }
                    }
                } catch (e: Exception) {
                    loggye("PingPanel[$ip]: result callback failed", e)
                }
            }
        }
    }

    /** Stops pinging and releases the engine resources. */
    fun stopPinging() {
        engine?.stop()
        engine = null
    }

    /** Restore all user-tunable preferences to their factory defaults.
     * Does not affect the pinging state nor the recorded history. */
    fun resetPreferences() {
        packetSize.value = DEFAULT_PACKET_SIZE
        interval.value = DEFAULT_INTERVAL_MS
        roof.value = DEFAULT_ROOF
        angleOfAttack.value = DEFAULT_ANGLE_OF_ATTACK
        timeframeMs.value = DEFAULT_TIMEFRAME_MS
        canvasHeightFraction.value = DEFAULT_CANVAS_HEIGHT_FRACTION
    }

    /** Call when removing this panel entirely. */
    fun close() {
        stopPinging()
        scope.cancel() // cancels intervalObserverJob too
    }

    companion object {
        const val DEFAULT_PACKET_SIZE = 32
        const val DEFAULT_INTERVAL_MS = 0L
        const val DEFAULT_ROOF = 1000
        const val DEFAULT_ANGLE_OF_ATTACK = 15.0f
        const val DEFAULT_TIMEFRAME_MS = 5_000L
        const val DEFAULT_CANVAS_HEIGHT_FRACTION = 0.20f
    }
}
