package com.yuroyami.pingy.logic

import com.yuroyami.pingy.GraphStyle
import com.yuroyami.pingy.utils.PingEngine
import com.yuroyami.pingy.utils.loggye
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Maximum number of pings retained per panel. Sized for zero-interval LAN
 * rates (roughly a thousand samples/sec) so the buffer still spans seconds
 * of history at full speed, and minutes at normal internet cadence. */
private const val MAX_PINGS = 6000

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
     * mutates in place. The draw pass and the throttled samplers read it
     * directly every frame/tick. */
    val pings = RingBuffer<Ping>(MAX_PINGS)

    /** Pinging Parameters */
    val packetSize = MutableStateFlow(DEFAULT_PACKET_SIZE)

    /** Interval between pings in ms. 0 means "fire the next ping as soon as
     * the previous one returns" (adaptive mode). */
    val interval = MutableStateFlow(DEFAULT_INTERVAL_MS)

    /** UI-related graph parameters */
    val roof = MutableStateFlow(DEFAULT_ROOF)                  // max displayed ping value
    val angleOfAttack = MutableStateFlow(DEFAULT_ANGLE_OF_ATTACK) // low-ping emphasis; 0 = linear 1:1
    val landMarks = MutableStateFlow(listOf(25f, 50f, 100f, 200f, 500f))

    /** Deck state: expanded (visible) or collapsed. Toggled by tapping the graph. */
    val expanded = MutableStateFlow(true)

    /** Deck content mode: stats (false) or settings (true). Toggled by the gear button. */
    val showSettings = MutableStateFlow(false)

    /** Time window (ms) of pings to keep visible on the canvas. */
    val timeframeMs = MutableStateFlow(DEFAULT_TIMEFRAME_MS)

    /** Canvas height as a fraction of the window's height (0.1 = 10%, 0.4 = 40%). */
    val canvasHeightFraction = MutableStateFlow(DEFAULT_CANVAS_HEIGHT_FRACTION)

    /** Per-panel graph style. null = follow the app-wide choice. */
    val styleOverride = MutableStateFlow<GraphStyle?>(null)

    /** Whether this panel is saved and restored on the next launch. */
    val persistAcrossSessions = MutableStateFlow(true)

    /** The platform-specific ping engine tied to this panel's lifecycle.
     * Volatile: written on Main, read from the preference observers below. */
    @Volatile
    private var engine: PingEngine? = null

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

    /** Most recent local failure, or null while probing is healthy. */
    val fault = MutableStateFlow<LocalFault?>(null)

    /** True once [startPinging] has run and the engine is live. */
    val running = MutableStateFlow(false)

    fun startPinging() {
        if (engine != null) return

        engine = PingEngine(
            host = ip,
            packetSize = packetSize.value,
            intervalMs = interval.value,
        ).also { eng ->
            // Anchored at SEND time: sends are evenly scheduled, so the x axis
            // stays even and a reaped loss lands where its probe actually flew.
            eng.start { ping ->
                try {
                    pings.add(ping)
                    fault.value = ping.fault
                } catch (e: Exception) {
                    loggye("PingPanel[$ip]: result callback failed", e)
                }
            }
        }
        running.value = true
    }

    /** Stops pinging and releases the engine's socket. */
    fun stopPinging() {
        engine?.stop()
        engine = null
        running.value = false
    }

    /** Stops and waits until the socket is actually released. */
    suspend fun stopPingingAndJoin() {
        engine?.stopAndJoin()
        engine = null
        running.value = false
    }

    /** Snapshot of the tunable preferences, for undoing a reset. */
    fun preferenceSnapshot(): PanelSpec = toSpec()

    /** Put back a snapshot taken by [preferenceSnapshot]. */
    fun restorePreferences(spec: PanelSpec) = applySpec(spec)

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

    /** Snapshot of everything worth remembering across sessions. */
    fun toSpec() = PanelSpec(
        ip = ip,
        intervalMs = interval.value,
        packetSize = packetSize.value,
        roof = roof.value,
        angleOfAttack = angleOfAttack.value,
        timeframeMs = timeframeMs.value,
        canvasHeightFraction = canvasHeightFraction.value,
        style = styleOverride.value?.name,
    )

    /**
     * Applies a restored snapshot. Call before [startPinging].
     *
     * Runs [PanelSpec.validated] again rather than trusting the caller: this is
     * the last point before the values reach engine arithmetic.
     */
    fun applySpec(spec: PanelSpec) {
        val safe = spec.validated() ?: return
        interval.value = safe.intervalMs
        packetSize.value = safe.packetSize
        roof.value = safe.roof
        angleOfAttack.value = safe.angleOfAttack
        timeframeMs.value = safe.timeframeMs
        canvasHeightFraction.value = safe.canvasHeightFraction
        styleOverride.value = safe.style?.let { name -> GraphStyle.entries.firstOrNull { it.name == name } }
    }

    /** Call when removing this panel entirely. */
    fun close() {
        stopPinging()
        scope.cancel()
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
