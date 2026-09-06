package com.yuroyami.pingy.logic

import com.yuroyami.pingy.GraphStyle
import com.yuroyami.pingy.utils.EngineFactory
import com.yuroyami.pingy.utils.PingEngine
import com.yuroyami.pingy.utils.ProbeEngine
import com.yuroyami.pingy.utils.loggye
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val engineFactory: EngineFactory = { host, packetSize, intervalMs ->
        PingEngine(host, packetSize, intervalMs)
    },
) {
    /** The whole collection of pings for this panel in a ring buffer, always
     * in send order. Not wrapped in a Flow: the buffer is a single long-lived
     * instance that mutates in place. The draw pass and the throttled samplers
     * read it directly every frame/tick. Probes still in the air when the
     * engine stops are settled as interrupted, not as loss: they left the
     * device, and we merely stopped being able to watch them. */
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

    /** The engine, owned entirely by [reconcile] and only touched under [lifecycle]. */
    @Volatile
    private var engine: ProbeEngine? = null

    /** What the owner last asked for. Reality is driven toward it, in order. */
    @Volatile
    private var desiredRunning = false

    @Volatile
    private var closed = false

    /** Serializes every start and stop, so a resume cannot land inside a stop. */
    private val lifecycle = Mutex()

    /** Panel-owned coroutine scope for preference observers and lifecycle work. */
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

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

    /** True while an engine is live. Only [reconcile] writes it. */
    val running: StateFlow<Boolean>
        field = MutableStateFlow(false)

    /**
     * Whether monitoring is wanted, regardless of whether it has taken effect
     * yet. A pause has to record intent, not the transient [running] flag, or
     * a panel the user paused by hand comes back on the next resume.
     */
    val wantsToRun: Boolean get() = desiredRunning

    fun startPinging() {
        desiredRunning = true
        scope.launch { reconcile() }
    }

    /** Stops pinging and releases the engine's socket. */
    fun stopPinging() {
        desiredRunning = false
        scope.launch { reconcile() }
    }

    /** Stops and waits until the socket is actually released. */
    suspend fun stopPingingAndJoin() {
        desiredRunning = false
        reconcile()
    }

    /**
     * Make reality match [desiredRunning].
     *
     * Serialized, and it re-reads the intent after every transition. That is
     * what makes a resume arriving during a slow stop win: the stop finishes,
     * the loop sees the newer intent and starts a fresh engine. It also keeps
     * exactly one engine writing into [pings] at a time.
     */
    private suspend fun reconcile() = lifecycle.withLock {
        while (true) {
            val want = desiredRunning && !closed
            val current = engine
            when {
                want && current == null -> {
                    engine = spawn()
                    running.value = true
                }
                !want && current != null -> {
                    current.stopAndJoin()
                    engine = null
                    running.value = false
                }
                else -> return@withLock
            }
        }
    }

    private fun spawn(): ProbeEngine {
        // One slot per probe, claimed at send time and filled by the verdict,
        // so the history stays in send order and nothing drawn ever shifts. A
        // fresh recorder per engine: its sequence numbers start over.
        val recorder = PingRecorder(pings)
        return engineFactory(ip, packetSize.value, interval.value).also { eng ->
            eng.start { event ->
                try {
                    recorder.accept(event)
                    when (event) {
                        is PingEvent.Fault -> fault.value = event.ping.fault
                        is PingEvent.Sent -> fault.value = null   // a probe left, so the socket works
                        is PingEvent.Resolved -> Unit
                    }
                } catch (e: Exception) {
                    loggye("PingPanel[$ip]: engine callback failed", e)
                }
            }
        }
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
        closed = true
        desiredRunning = false
        engine?.stop()
        engine = null
        running.value = false
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
