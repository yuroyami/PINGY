package com.yuroyami.pingy.ui.main.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.yuroyami.pingy.GraphStyle
import com.yuroyami.pingy.PanelLayout
import com.yuroyami.pingy.i18n.Strings
import com.yuroyami.pingy.ui.reduceMotion
import com.yuroyami.pingy.i18n.strings
import com.yuroyami.pingy.logic.Ping
import com.yuroyami.pingy.logic.LocalFault
import com.yuroyami.pingy.logic.PingKind
import com.yuroyami.pingy.logic.PingPanel
import com.yuroyami.pingy.theme.Paletting
import com.yuroyami.pingy.theme.pingColor
import com.yuroyami.pingy.ui.adam.LocalViewmodel
import com.yuroyami.pingy.utils.PING_TIMEOUT_MS
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.resources.Font
import pingy.shared.generated.resources.Inter_Regular
import pingy.shared.generated.resources.Res
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.TimeSource

// ---- Juice tuning. Everything is a pure function of a ping's age, so the same
// ---- math reads as a per-bar "pop" at 1 ping/sec and as a living ripple
// ---- wavefront at 200 pings/sec. No animation objects, no per-bar state.
private const val BIRTH_MS = 220f       // how long a newborn bar springs and glows
private const val BIRTH_OVERSHOOT = 0.06f
private const val BIRTH_BRIGHTEN = 0.35f
/**
 * There is no death animation. The chameleon's one transition, running since
 * birth, simply continues: over its final stretch before the deadline the wall
 * dissolves, alpha sliding to zero so the panel's real texture shows through
 * it, and when the timeout verdict lands there is nothing left to remove.
 * (A color-lerp cannot do this: the background is a texture, and fading toward
 * any flat color paints a slab instead of revealing what is behind.)
 */
private val CHAMELEON_FADE_START_MS = PING_TIMEOUT_MS * 2f / 3f
private const val SCRUB_GLIDE_MS = 350f // frozen time glides back to now instead of teleporting
private const val PEAK_DECAY_PER_SEC = 0.22f // peak-hold line falls this canvas-fraction per second

/**
 * Ceiling on how far text drawn *inside* the canvas follows the user's font
 * scale.
 *
 * Everything outside the instrument scales without limit. The canvas cannot:
 * it has a fixed height and its labels sit at fixed anchors, so at 200% the
 * readout plate grew until it covered the graph it was annotating. Capping the
 * in-canvas scale keeps the instrument legible while the rest of the app
 * honours the setting in full.
 */
private const val MAX_CANVAS_FONT_SCALE = 1.35f

/** Redraw cadence when the platform asks for reduced motion. */
private const val REDUCED_MOTION_TICK_MS = 250L

/** Width of the floating control row: three 48dp touch targets. */
private const val CONTROL_ROW_WIDTH_DP = 144




/**
 * Drag-to-inspect freeze: ages render relative to [freezeMark] so the conveyor
 * halts, and the statistics and readout are computed against the same moment.
 *
 * The live ring keeps filling underneath. A hold long enough to wrap it loses
 * the oldest frozen samples, which is the one thing this does not preserve.
 */
private data class ScrubFreeze(val freezeMark: TimeSource.Monotonic.ValueTimeMark, val cursorX: Float)

// Gesture verdicts for the manual tap / long-press / scrub state machine.
private const val GESTURE_SCROLL = 0
private const val GESTURE_TAP = 1
private const val GESTURE_SCRUB = 3

/**
 * Full graph panel view:
 *
 * - A canvas at the top drawing the ping history. Tapping it collapses or
 *   expands the deck below; a motionless long-press resets tuning
 *   preferences; a horizontal drag freezes the conveyor and scrubs
 *   bar-by-bar with an exact readout chip.
 * - Newborn bars overshoot and glow briefly, and a slowly-falling peak-hold
 *   line marks the recent maximum like a VU meter.
 * - The readout plate carries the live value AND the target address,
 *   throttled to stay readable at zero-interval probe rates.
 * - Floating controls on the canvas' top-right corner: per-panel graph
 *   style, the settings deck, and panel discard.
 * - The deck below shows windowed stats or the tuning dials, all reactive
 *   via the panel's StateFlows.
 */
@Composable
fun PingPanel.PingGraphView(modifier: Modifier = Modifier) {
    val s = strings
    val textMeasurer = rememberTextMeasurer()

    // Multiplier that holds in-canvas text at or below MAX_CANVAS_FONT_SCALE
    // while leaving smaller scales untouched.
    val canvasSp = (MAX_CANVAS_FONT_SCALE / LocalDensity.current.fontScale).coerceAtMost(1f)
    val windowInfo = LocalWindowInfo.current
    val windowHeightDp by remember(windowInfo) { derivedStateOf { windowInfo.containerDpSize.height } }

    val pings = this.pings
    val viewmodel = LocalViewmodel.current
    val globalStyle by viewmodel.graphStyle.collectAsState()
    val styleOverrideVal by styleOverride.collectAsState()
    val graphStyleVal = styleOverrideVal ?: globalStyle
    val layoutVal by viewmodel.panelLayout.collectAsState()
    val expanded by expanded.collectAsState()
    val showSettings by showSettings.collectAsState()

    val roofVal by roof.collectAsState()
    val angleOfAttackVal by angleOfAttack.collectAsState()
    val landMarksVal by landMarks.collectAsState()
    val timeframeMsVal by timeframeMs.collectAsState()
    val canvasHeightFractionVal by canvasHeightFraction.collectAsState()

    val canvasHeight = windowHeightDp * canvasHeightFractionVal // Dp

    // Reusable scratch buffer for the per-frame visible-pings pass. Remembered
    // across frames so the draw loop allocates nothing when the RingBuffer
    // hasn't grown: clear() keeps the underlying array, and we only grow on
    // the first few frames as the history fills up.
    val visibleBuf = remember { ArrayList<Ping>(256) }

    // Per-frame ticker so the canvas scrolls smoothly between ping arrivals,
    // giving the illusion of a continuous left-moving conveyor belt instead
    // of bars popping into place only when a new ping lands. Especially
    // important in adaptive (RTT-duration) mode where long-RTT bars would
    // otherwise flash in at full width and feel jittery.
    val frameTick = remember { mutableLongStateOf(0L) }
    val isRunning by running.collectAsState()
    val animateGraph = !reduceMotion()

    // Three loops, keyed on whether the panel is probing and on the system's
    // reduce-motion setting. Running them for every composed panel regardless
    // would burn frames on panels that have nothing to animate.
    //
    // With motion reduced the canvas still updates, just on new data rather than
    // on every frame, so the graph stays truthful without the conveyor effect.
    LaunchedEffect(isRunning, animateGraph) {
        if (!isRunning) return@LaunchedEffect
        if (animateGraph) {
            while (true) withFrameMillis { frameTick.longValue = it }
        } else {
            while (true) {
                frameTick.longValue += 1
                delay(REDUCED_MOTION_TICK_MS)
            }
        }
    }

    // Peak-hold marker. A plain holder, not Compose state: the canvas already
    // redraws every frame, so invalidation bookkeeping would be pure overhead.
    val peakHold = remember { object { var fraction = 0f; var lastFrameMs = 0L } }

    // More per-frame holders in the same spirit: the aura color chases its
    // target instead of snapping, and a released scrub glides back to now.
    val auraSmooth = remember { object { var color: Color? = null } }
    val glide = remember { object { var startMark: TimeSource.Monotonic.ValueTimeMark? = null; var fromOffsetMs = 0L } }

    // Neon readout, sampled at ~7 Hz. At interval 0 a per-ping readout would
    // strobe hundreds of times a second; a throttled sample stays readable
    // while still feeling live. It shows the newest VERDICT in send order:
    // probes still in the air are skipped, so a late loss cannot flip the
    // number a fresher reply already earned.
    // Declared before the samplers below, which freeze along with it.
    var scrub by remember { mutableStateOf<ScrubFreeze?>(null) }

    var readoutValue by remember { mutableStateOf<Int?>(null) }
    var readoutKind by remember { mutableStateOf<PingKind?>(null) }
    LaunchedEffect(this@PingGraphView, isRunning) {
        if (!isRunning) return@LaunchedEffect
        while (true) {
            var latest: Ping? = null
            pings.forEachNewestFirst { p ->
                if (p.isPending) true else { latest = p; false }
            }
            // Held still while inspecting: a reading that keeps moving under a
            // frozen graph describes a different moment from the one on screen.
            if (scrub == null) {
                latest?.let { last ->
                    // Keep the outcome type, not just "is there a number". A
                    // null RTT covers a timeout, a DNS failure and a lost
                    // socket alike.
                    readoutKind = last.kind
                    if (last.kind == PingKind.REPLY) last.value?.let { readoutValue = it }
                }
            }
            delay(150)
        }
    }

    // Windowed stats: computed over the SAME visible timeframe as the graph,
    // so the numbers describe what the eye currently sees instead of dragging
    // ancient losses around forever. Sampled at 2.5 Hz, far away from the
    // per-frame draw path and the recomposition path.
    var windowStats by remember { mutableStateOf(WindowStats.EMPTY) }
    LaunchedEffect(this@PingGraphView, isRunning) {
        if (!isRunning) return@LaunchedEffect
        while (true) {
            val base = timeframeMs.value
            val effective = visibleWindowMs(base, viewmodel.panelLayout.value)
            // Anchored to the freeze while inspecting, so the numbers describe
            // the span the canvas is showing rather than the live edge.
            windowStats = computeWindowStats(
                pings,
                effective,
                scrub?.freezeMark ?: TimeSource.Monotonic.markNow(),
            )
            delay(400)
        }
    }

    // The readout's hue glides between samples; the text itself stays discrete.
    val readoutColor by animateColorAsState(
        targetValue = when (readoutKind) {
            PingKind.REPLY -> readoutValue?.let(::readablePingTextColor) ?: StatsDimColor
            PingKind.TIMEOUT, PingKind.LOCAL_FAULT -> FizzleColor
            else -> StatsDimColor
        },
        animationSpec = tween(durationMillis = 300),
    )

    // What the plate says. A local fault gets its own mark: nothing was
    // measured, so a red cross meaning "the target did not answer" would lie.
    val readoutText = when (readoutKind) {
        PingKind.REPLY -> readoutValue?.let { "$it ms" }
        PingKind.TIMEOUT -> "×"
        PingKind.LOCAL_FAULT -> "!"
        PingKind.INTERRUPTED, PingKind.UNOBSERVED -> "?"
        PingKind.PENDING, null -> null
    }

    val inter = Font(Res.font.Inter_Regular)
    val interFont = remember(inter) { FontFamily(inter) }
    val chipStyle = remember(interFont, canvasSp) {
        TextStyle(color = Color.White, fontSize = (12 * canvasSp).sp, fontFamily = interFont)
    }
    val peakDash = remember { PathEffect.dashPathEffect(floatArrayOf(12f, 8f)) }
    // Ambient clock for the procedural backdrop (time grid + sweep).
    val panelEpoch = remember { TimeSource.Monotonic.markNow() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .wrapContentHeight()
    ) {
        // One chassis. The clip and border wrap the graph AND its deck below,
        // so they share corners, width, and outline: a single instrument.
        // When the deck folds away, the body seals its own bottom corners.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, color = Color.White, RoundedCornerShape(16.dp))
        ) {
        // Graph canvas + overlaid minimize toggle
        // The graph is a Canvas, so it exposes nothing to a screen reader on its
        // own. This summary is the accessible equivalent of the picture: current
        // reading, window, and the same statistics shown beside it. It is a live
        // region so a reader hears it update instead of having to re-navigate.
        val a11ySummary = buildGraphSummary(
            ip = ip,
            latestKind = readoutKind,
            latestRtt = readoutValue,
            stats = windowStats,
            windowMs = visibleWindowMs(timeframeMsVal, layoutVal),
            s = s,
        )
        val faultVal by fault.collectAsState()
        val faultCodeVal by faultCode.collectAsState()
        faultVal?.let { f ->
            Text(
                text = if (faultCodeVal > 0) "${f.message(s)} (errno $faultCodeVal)" else f.message(s),
                color = Color(0xFFFF8A80),
                fontSize = 11.sp,
                fontFamily = interFont,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x33FF5252))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(canvasHeight)
                .semantics(mergeDescendants = true) {
                    contentDescription = a11ySummary
                    liveRegion = LiveRegionMode.Polite
                }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    // Manual gesture FSM instead of combinedClickable: tap folds
                    // or unfolds the deck, motionless long-press resets
                    // preferences, and a horizontal drag scrubs the frozen
                    // timeline. Vertical drags are left unconsumed so the outer
                    // list keeps scrolling from the graph surface.
                    .pointerInput(this@PingGraphView) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val slop = viewConfiguration.touchSlop
                            var travel = Offset.Zero
                            val verdict = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                    if (!change.pressed) return@withTimeoutOrNull GESTURE_TAP
                                    travel += change.positionChange()
                                    if (travel.getDistance() > slop) {
                                        return@withTimeoutOrNull if (abs(travel.x) >= abs(travel.y)) {
                                            change.consume()
                                            scrub = ScrubFreeze(TimeSource.Monotonic.markNow(), change.position.x)
                                            GESTURE_SCRUB
                                        } else {
                                            GESTURE_SCROLL
                                        }
                                    }
                                }
                                @Suppress("UNREACHABLE_CODE") GESTURE_SCROLL
                            }
                            when (verdict) {
                                null -> {
                                    // Held still past the long-press timeout.
                                    // Reversible: a hidden gesture that silently
                                    // discards every slider the user set is not
                                    // something to do without a way back.
                                    val before = preferenceSnapshot()
                                    resetPreferences()
                                    viewmodel.notify(
                                        text = s.settingsWereReset,
                                        actionLabel = s.undo,
                                        action = { restorePreferences(before) },
                                    )
                                }
                                GESTURE_TAP -> this@PingGraphView.expanded.update { !it }
                                GESTURE_SCRUB -> try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        change.consume()
                                        scrub = scrub?.copy(cursorX = change.position.x)
                                    }
                                } finally {
                                    // Hand the frozen offset to the glide so the
                                    // conveyor eases back to now instead of jumping.
                                    scrub?.let { frozen ->
                                        glide.fromOffsetMs = frozen.freezeMark.elapsedNow().inWholeMilliseconds
                                        glide.startMark = TimeSource.Monotonic.markNow()
                                    }
                                    scrub = null
                                }
                                else -> Unit // vertical intent: let the list scroll
                            }
                        }
                    }
            ) {
                // Procedural instrument-bay backdrop: no bitmap, no decode,
                // a handful of cached-gradient rects. The vertical time grid
                // scrolls with real seconds; neutral chrome so the data owns
                // every drop of color on this screen.
                val ambientMs = panelEpoch.elapsedNow().inWholeMilliseconds
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color(0xFF161C2A),
                        1f to Color(0xFF0A0D13),
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        0f to Color.Transparent,
                        1f to Color(0x59000000),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.width * 0.72f,
                    )
                )
                run {
                    val tickPxPerMs = size.width / timeframeMsVal.toFloat()
                    val minor = timeframeMsVal <= 10_000L
                    var tick = ambientMs % 1_000L
                    var major = true
                    val step = if (minor) 500L else 1_000L
                    if (minor && tick >= 500L) { tick -= 500L; major = false }
                    while (true) {
                        val x = size.width - tick * tickPxPerMs
                        if (x < 0f) break
                        drawLine(
                            color = Color.White,
                            alpha = if (major) 0.08f else 0.035f,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1f,
                        )
                        tick += step
                        if (minor) major = !major
                    }
                }

                // Horizontal ping-level landmark lines
                for (y in landMarksVal) {
                    val h = calculatePingY(y.toInt(), size.height, roofVal.toFloat(), angleOfAttackVal)
                    drawLine(
                        start = Offset(0f, size.height - h),
                        end = Offset(size.width, size.height - h),
                        strokeWidth = 1f,
                        color = Color.White,
                        alpha = 0.30f
                    )
                }

                // Force redraw every frame for smooth scrolling: reading the
                // ticker state invalidates draw without recomposition. One
                // clock read serves every age computed in this pass.
                val nowFrameMs = frameTick.longValue
                val frameNow = TimeSource.Monotonic.markNow()
                val dtSec = ((nowFrameMs - peakHold.lastFrameMs).coerceIn(0L, 100L)) / 1000f
                peakHold.lastFrameMs = nowFrameMs

                // While scrubbing, every age is shifted back by the time elapsed
                // since the freeze, which halts the conveyor without copying the
                // buffer. Pings born after the freeze land in negative ages and
                // are simply not drawn until release, at which point the
                // leftover offset eases out instead of teleporting the belt
                // forward.
                val activeScrub = scrub
                val freezeOffsetMs = when {
                    activeScrub != null -> activeScrub.freezeMark.elapsedNow().inWholeMilliseconds
                    else -> glide.startMark?.let { start ->
                        val progress = start.elapsedNow().inWholeMilliseconds / SCRUB_GLIDE_MS
                        if (progress >= 1f) {
                            glide.startMark = null
                            0L
                        } else {
                            (glide.fromOffsetMs * (1.0 - easeOutCubic(progress.toDouble()))).toLong()
                        }
                    } ?: 0L
                }

                // The window the user chose, in every layout. A window
                // narrower than the timeout can never contain a timed-out
                // probe, because the loss lands at its send moment, 3s in the
                // past and already off the left edge, so the value is floored.
                val thresholdMs = visibleWindowMs(timeframeMsVal, layoutVal)
                val canvasW = size.width
                val canvasH = size.height
                val pxPerMs = canvasW / thresholdMs.toFloat()
                val minBarPx = 2.5.dp.toPx()

                // The ring is in send order and every probe already owns a
                // slot from the moment it leaves, so the window is one walk,
                // no sort, and nothing already drawn ever shifts. Entries
                // denser than one pixel column fold into each other, the
                // worst news surviving.
                gatherVisible(pings, frameNow, freezeOffsetMs, thresholdMs, visibleBuf)
                foldColumns(visibleBuf) { p ->
                    (canvasW - ((frameNow - p.timestamp).inWholeMilliseconds - freezeOffsetMs) * pxPerMs).toInt()
                }

                fun ageOf(p: Ping): Long = (frameNow - p.timestamp).inWholeMilliseconds - freezeOffsetMs

                // What a slot shows. A reply shows its RTT. A probe still in
                // the air is the chameleon: it shows how long it has been
                // waiting, ripening through the RTT scale in real time, and
                // over its final stretch before the deadline it dissolves so
                // the panel texture shows through. When the timeout verdict
                // lands there is nothing left to remove. A loss or a fault
                // shows nothing: the gap is the signal.
                fun levelOf(p: Ping, age: Long): Int? = when (p.kind) {
                    PingKind.REPLY -> p.value
                    PingKind.PENDING -> age.coerceIn(0L, PING_TIMEOUT_MS.toLong()).toInt()
                    PingKind.TIMEOUT, PingKind.LOCAL_FAULT,
                    PingKind.INTERRUPTED, PingKind.UNOBSERVED -> null
                }
                fun presenceOf(p: Ping, age: Long): Float {
                    if (p.kind != PingKind.PENDING) return 1f
                    val ms = age.coerceAtMost(PING_TIMEOUT_MS.toLong()).toFloat()
                    if (ms <= CHAMELEON_FADE_START_MS) return 1f
                    return (1f - (ms - CHAMELEON_FADE_START_MS) / (PING_TIMEOUT_MS - CHAMELEON_FADE_START_MS))
                        .coerceIn(0f, 1f)
                }

                // Scrub bookkeeping: the cursor maps to an age, and the slot loop
                // records the geometry of whichever ping lands closest.
                val cursorAgeMs = activeScrub?.let { ((canvasW - it.cursorX) / pxPerMs).toLong() }
                var pickDiff = Long.MAX_VALUE
                var pickFound = false
                var pickLost = false
                var pickPending = false
                var pickKind = PingKind.REPLY
                var pickFault: LocalFault? = null
                var pickValue = 0
                var pickAgeMs = 0L
                var pickLeft = 0f
                var pickWidth = 0f
                var pickHeight = 0f

                var maxTopFraction = 0f
                // The live-edge aura follows the newest slot that shows anything.
                var auraColor: Color? = null
                var auraPresence = 1f

                if (visibleBuf.isNotEmpty() && graphStyleVal == GraphStyle.MOUNTAIN_SLOPES) {
                    // MOUNTAIN-SLOPES: one continuous ridge instead of bars.
                    // Between consecutive showing slots the crest runs as a
                    // straight diagonal (steep when the value jumped), and the
                    // fill below blends each point's color into the next, so
                    // every ping keeps its chameleon identity without owning a
                    // rectangle. A loss breaks the range: the massif ends, the
                    // honest gap shows, the next massif begins. A slot with no
                    // showing neighbour, the newest included, runs level across
                    // its own span. Rendered as one 1px column per device
                    // pixel; no allocations.
                    val n = visibleBuf.size
                    val crestPx = 2.dp.toPx()

                    fun ridge(xFrom: Float, xTo: Float, yFrom: Float, yTo: Float, cFrom: Color, cTo: Color, alpha: Float) {
                        if (alpha <= 0f || xTo <= xFrom + 0.5f || xTo <= 0f) return
                        // Integer-aligned unit columns: fractional x positions
                        // leave an antialiased seam on every column, which reads
                        // as shimmering vertical curtain stripes across the range.
                        var xi = kotlin.math.ceil(xFrom.coerceAtLeast(0f)).toInt()
                        val xEnd = kotlin.math.ceil(xTo.coerceAtMost(canvasW)).toInt()
                        while (xi < xEnd) {
                            val t = (((xi + 0.5f) - xFrom) / (xTo - xFrom)).coerceIn(0f, 1f)
                            val h = yFrom + (yTo - yFrom) * t
                            if (h >= 1f) {
                                val col = lerp(cFrom, cTo, t)
                                drawRect(
                                    color = col,
                                    alpha = alpha,
                                    topLeft = Offset(xi.toFloat(), canvasH - h),
                                    size = Size(1f, h)
                                )
                                drawRect(
                                    color = lerp(col, Color.White, 0.5f),
                                    alpha = alpha,
                                    topLeft = Offset(xi.toFloat(), canvasH - h),
                                    size = Size(1f, h.coerceAtMost(crestPx))
                                )
                            }
                            xi++
                        }
                    }

                    var prevShows = false
                    var prevX = 0f
                    var prevY = 0f
                    var prevC = Color.White
                    var prevPresence = 1f

                    for (index in 0 until n) {
                        val ping = visibleBuf[index]
                        val age = ageOf(ping)
                        val x = canvasW - age * pxPerMs
                        val level = levelOf(ping, age)

                        if (level == null) {
                            if (cursorAgeMs != null) {
                                val diff = abs(age - cursorAgeMs)
                                if (diff < pickDiff) {
                                    pickDiff = diff; pickFound = true; pickLost = true; pickPending = false
                                    pickKind = ping.kind; pickFault = ping.fault
                                    pickAgeMs = age; pickLeft = x - 2f; pickWidth = 4f; pickHeight = canvasH
                                }
                            }
                            prevShows = false
                            continue
                        }

                        val presence = presenceOf(ping, age)
                        var y = calculatePingY(level, canvasH, roofVal.toFloat(), angleOfAttackVal)
                            .coerceAtLeast(minBarPx)
                        var c = calcPingColor(level)
                        if (ping.kind == PingKind.REPLY && activeScrub == null && age < BIRTH_MS) {
                            val life = age / BIRTH_MS
                            y = (y * (1f + BIRTH_OVERSHOOT * sin(PI * life).toFloat())).coerceAtMost(canvasH)
                            c = lerp(c, Color.White, BIRTH_BRIGHTEN * (1f - life))
                        }

                        if (prevShows) ridge(prevX, x, prevY, y, prevC, c, minOf(prevPresence, presence))
                        // No showing neighbour to slope into: paint this slot
                        // level across its own span, so a lone reply between
                        // two losses still leaves its mark.
                        if (fillsOwnSlot(visibleBuf, index) { p -> levelOf(p, ageOf(p)) != null }) {
                            val xEnd = canvasW - slotEndAgeMs(visibleBuf, index) { ageOf(it) } * pxPerMs
                            ridge(x, xEnd, y, y, c, c, presence)
                        }

                        if (presence > 0f && y / canvasH > maxTopFraction) maxTopFraction = y / canvasH
                        if (cursorAgeMs != null) {
                            val diff = abs(age - cursorAgeMs)
                            if (diff < pickDiff) {
                                pickDiff = diff; pickFound = true; pickLost = false
                                pickPending = ping.kind == PingKind.PENDING
                                pickKind = ping.kind; pickFault = null
                                pickAgeMs = age; pickLeft = x - 2f; pickWidth = 4f
                                pickHeight = y; pickValue = level
                            }
                        }
                        auraColor = c
                        auraPresence = presence

                        prevShows = true
                        prevX = x
                        prevY = y
                        prevC = c
                        prevPresence = presence
                    }
                }

                if (visibleBuf.isNotEmpty() && graphStyleVal == GraphStyle.PINGLETTES) {
                    // Slot-based drawing. Every probe owns the stretch from its
                    // send to the next send; the newest owns up to the "now"
                    // edge. A reply fills its slot at its RTT colour, a probe in
                    // the air fills it as the ripening chameleon, and a loss
                    // leaves its slot as literal background: the gap itself
                    // carries the "lost packet" signal at a glance. Because the
                    // slot was claimed at send time, a verdict never changes
                    // any width already on screen.
                    val n = visibleBuf.size
                    for (i in 0 until n) {
                        val ping = visibleBuf[i]
                        val age = ageOf(ping)
                        val level = levelOf(ping, age)
                        val rightAge = slotEndAgeMs(visibleBuf, i) { ageOf(it) }
                        // No coerceAtLeast(0f) on the left edge: letting it go
                        // negative keeps the bar's full width intact as it
                        // exits the canvas. Compose clips off-canvas drawing.
                        val leftEdgePx = canvasW - age * pxPerMs
                        val rightEdgePx = canvasW - rightAge * pxPerMs

                        if (level == null) {
                            if (cursorAgeMs != null) {
                                val diff = abs(age - cursorAgeMs)
                                if (diff < pickDiff) {
                                    pickDiff = diff; pickFound = true; pickLost = true; pickPending = false
                                    pickKind = ping.kind; pickFault = ping.fault
                                    pickAgeMs = age; pickLeft = leftEdgePx - 2f
                                    pickWidth = 4f; pickHeight = canvasH
                                }
                            }
                            continue
                        }
                        if (rightEdgePx <= 0f) continue

                        val presence = presenceOf(ping, age)
                        val widthPx = (rightEdgePx - leftEdgePx).coerceAtLeast(1f)
                        var y = calculatePingY(level, canvasH, roofVal.toFloat(), angleOfAttackVal)
                            .coerceAtLeast(minBarPx)
                        var color = calcPingColor(level)

                        // Birth ritual: for its first BIRTH_MS a fresh reply
                        // overshoots its true height and carries extra
                        // brightness, both easing back to truth. Skipped while
                        // frozen: a scrubbed past shouldn't wiggle.
                        if (ping.kind == PingKind.REPLY && activeScrub == null && age < BIRTH_MS) {
                            val life = age / BIRTH_MS
                            y = (y * (1f + BIRTH_OVERSHOOT * sin(PI * life).toFloat())).coerceAtMost(canvasH)
                            color = lerp(color, Color.White, BIRTH_BRIGHTEN * (1f - life))
                        }

                        if (presence > 0f) {
                            drawRect(
                                color = color,
                                alpha = presence,
                                topLeft = Offset(leftEdgePx, canvasH - y),
                                size = Size(widthPx, y)
                            )
                            // Tip highlight sells the "filled up to its level"
                            // read. Hairline bars at zero-interval rates can't
                            // show a tip, and a probe still in the air has no
                            // level to have reached yet.
                            if (ping.kind == PingKind.REPLY && widthPx >= 3f) {
                                drawRect(
                                    color = lerp(color, Color.White, 0.45f),
                                    topLeft = Offset(leftEdgePx, canvasH - y),
                                    size = Size(widthPx, y.coerceAtMost(3f))
                                )
                            }
                            if (y / canvasH > maxTopFraction) maxTopFraction = y / canvasH
                        }

                        if (cursorAgeMs != null) {
                            val diff = abs(age - cursorAgeMs)
                            if (diff < pickDiff) {
                                pickDiff = diff; pickFound = true; pickLost = false
                                pickPending = ping.kind == PingKind.PENDING
                                pickKind = ping.kind; pickFault = null
                                pickValue = level; pickAgeMs = age
                                pickLeft = leftEdgePx; pickWidth = widthPx; pickHeight = y
                            }
                        }
                        auraColor = color
                        auraPresence = presence
                    }
                }

                // Live-edge aura: a soft breath of the newest colour hugging
                // the right border. It chases its target exponentially, so a
                // teal-to-green flip breathes instead of snapping, and it
                // breathes out with a dissolving chameleon. Suppressed while
                // scrubbing: frozen time has no live edge.
                if (activeScrub == null) {
                    auraColor?.let { target ->
                        val chase = 1f - exp(-dtSec * 10f)
                        val smoothed = auraSmooth.color?.let { lerp(it, target, chase) } ?: target
                        auraSmooth.color = smoothed
                        val auraW = 28.dp.toPx()
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to Color.Transparent,
                                1f to smoothed.copy(alpha = 0.13f * auraPresence),
                                startX = canvasW - auraW,
                                endX = canvasW,
                            ),
                            topLeft = Offset(canvasW - auraW, 0f),
                            size = Size(auraW, canvasH)
                        )
                    }
                }

                // Peak-hold: rises instantly to the tallest visible bar, then
                // falls at a steady rate like a VU meter needle at rest.
                if (activeScrub == null) {
                    peakHold.fraction = maxOf(
                        maxTopFraction,
                        peakHold.fraction - PEAK_DECAY_PER_SEC * dtSec,
                    )
                }
                if (peakHold.fraction > 0.02f) {
                    val peakY = canvasH - peakHold.fraction * canvasH
                    // Fades in over its first stretch of height instead of popping.
                    val presence = ((peakHold.fraction - 0.02f) / 0.05f).coerceIn(0f, 1f)
                    drawLine(
                        color = PeakLineColor,
                        alpha = 0.75f * presence,
                        start = Offset(0f, peakY),
                        end = Offset(canvasW, peakY),
                        strokeWidth = 2f,
                        pathEffect = peakDash,
                    )
                }

                // Landmark labels, right-aligned.
                //
                // The left side is where the readout plate sits, and at large
                // text scales that plate grows over anything parked there. The
                // right edge stays clear at every scale, and the x is measured
                // rather than assumed.
                for (y in landMarksVal) {
                    // Above the ceiling every landmark clamps to the same top
                    // pixel, so they stack into an unreadable pile.
                    if (y > roofVal) continue
                    val h = calculatePingY(y.toInt(), canvasH, roofVal.toFloat(), angleOfAttackVal)
                    val axisStyle = TextStyle(fontSize = (8 * canvasSp).sp, color = Color(200, 200, 220, 170))
                    val measured = textMeasurer.measure(AnnotatedString(y.toInt().toString()), axisStyle)
                    drawText(
                        textLayoutResult = measured,
                        topLeft = Offset(
                            x = canvasW - measured.size.width - 6.dp.toPx(),
                            // Clamped: a landmark near the roof otherwise draws
                            // half off the top of the canvas.
                            y = (canvasH - h - measured.size.height / 2f)
                                .coerceIn(0f, canvasH - measured.size.height),
                        ),
                    )
                }

                // Current-ping readout + target address on one HUD plate,
                // colored by the same scale as the bars. Throttled upstream so
                // it never strobes; its color tweens between samples instead
                // of snapping. A host that has only ever timed out still earns
                // its red ×.
                if (readoutText != null) {
                    val neonColor = readoutColor
                    val neonText = readoutText
                    // Keep the plate clear of the three controls in the top
                    // right. A long host used to draw straight through them and
                    // off the edge; the full name is in the settings sheet and
                    // in the spoken summary.
                    val plateMaxWidth = (canvasW - CONTROL_ROW_WIDTH_DP.dp.toPx() - 20.dp.toPx())
                        .coerceAtLeast(48.dp.toPx())
                    val neon = textMeasurer.measure(
                        buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    color = neonColor,
                                    fontSize = (17 * canvasSp).sp,
                                    fontWeight = FontWeight.Bold,
                                    shadow = Shadow(color = neonColor, blurRadius = 14f),
                                )
                            ) { append(neonText) }
                            withStyle(
                                SpanStyle(color = StatsLabelColor, fontSize = (11 * canvasSp).sp)
                            ) { append("   $ip") }
                        },
                        style = TextStyle(fontFamily = interFont),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        constraints = Constraints(maxWidth = plateMaxWidth.toInt()),
                    )
                    // Dark HUD plate so the number stays readable when bars of
                    // the same color rise behind it.
                    val plateX = 10.dp.toPx()
                    val plateY = 6.dp.toPx()
                    val padX = 7.dp.toPx()
                    val padY = 3.dp.toPx()
                    drawRoundRect(
                        color = Color(0xB3101010),
                        topLeft = Offset(plateX, plateY),
                        size = Size(neon.size.width + padX * 2, neon.size.height + padY * 2),
                        cornerRadius = CornerRadius(9.dp.toPx())
                    )
                    drawText(neon, topLeft = Offset(plateX + padX, plateY + padY))
                }

                // Scrub overlay: cursor line, picked-bar highlight, readout chip.
                if (activeScrub != null && pickFound) {
                    val cursorX = activeScrub.cursorX.coerceIn(0f, canvasW)
                    drawRect(
                        color = Color.White,
                        alpha = if (pickLost) 0.20f else 0.28f,
                        topLeft = Offset(pickLeft, canvasH - pickHeight),
                        size = Size(pickWidth, pickHeight)
                    )
                    drawLine(
                        color = Color.White,
                        alpha = 0.85f,
                        start = Offset(cursorX, 0f),
                        end = Offset(cursorX, canvasH),
                        strokeWidth = 1.5f,
                    )
                    // Name the real outcome. Calling a DNS failure or a dead
                    // socket a "timeout" blames the target for our own trouble.
                    val age = formatShortAge(pickAgeMs)
                    val chipText = when (pickKind) {
                        PingKind.REPLY -> "$pickValue ms · $age"
                        PingKind.PENDING -> "${s.inspectInFlight} · $age"
                        PingKind.TIMEOUT -> "${s.inspectTimeout} · $age"
                        PingKind.INTERRUPTED -> "${s.inspectInterrupted} · $age"
                        PingKind.LOCAL_FAULT -> "${pickFault?.message(s) ?: s.inspectInterrupted} · $age"
                        PingKind.UNOBSERVED -> "${s.inspectUnobserved} · $age"
                    }
                    val chip = textMeasurer.measure(AnnotatedString(chipText), chipStyle)
                    val padX = 8.dp.toPx()
                    val padY = 5.dp.toPx()
                    val chipW = chip.size.width + padX * 2
                    val chipH = chip.size.height + padY * 2
                    val chipLeft = (cursorX - chipW / 2f).coerceIn(4f, (canvasW - chipW - 4f).coerceAtLeast(4f))
                    val chipTop = 44.dp.toPx().coerceAtMost(canvasH - chipH - 4f)
                    val chipColor = if (pickLost) FizzleColor else readablePingTextColor(pickValue)
                    drawRoundRect(
                        color = Color(0xE6141414),
                        topLeft = Offset(chipLeft, chipTop),
                        size = Size(chipW, chipH),
                        cornerRadius = CornerRadius(8.dp.toPx())
                    )
                    drawRoundRect(
                        color = chipColor,
                        topLeft = Offset(chipLeft, chipTop),
                        size = Size(chipW, chipH),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
                    )
                    drawText(chip, topLeft = Offset(chipLeft + padX, chipTop + padY))
                }
            }

            // Panel controls, floating over the canvas' top-right corner so
            // they intercept taps before the graph gestures can fire:
            // per-panel style flip, settings deck, and discard.
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    // 48dp meets the Android and iOS minimum touch target; the icon
                    // inside stays 17dp so the visual density is unchanged.
                    modifier = Modifier.size(48.dp),
                    onClick = {
                        val next = when (graphStyleVal) {
                            GraphStyle.PINGLETTES -> GraphStyle.MOUNTAIN_SLOPES
                            GraphStyle.MOUNTAIN_SLOPES -> GraphStyle.PINGLETTES
                        }
                        styleOverride.value = next
                        viewmodel.notify(
                            if (next == GraphStyle.PINGLETTES) s.allPanelsBars else s.allPanelsRidge
                        )
                    },
                ) {
                    // Previews the style a tap would switch this panel to.
                    Icon(
                        imageVector = if (graphStyleVal == GraphStyle.MOUNTAIN_SLOPES) Icons.Filled.BarChart
                                      else Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = s.switchPanelStyle,
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(
                    // 48dp meets the Android and iOS minimum touch target; the icon
                    // inside stays 17dp so the visual density is unchanged.
                    modifier = Modifier.size(48.dp),
                    onClick = {
                        if (!expanded) {
                            this@PingGraphView.expanded.value = true
                            this@PingGraphView.showSettings.value = true
                        } else {
                            this@PingGraphView.showSettings.update { !it }
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = s.panelSettings,
                        tint = if (showSettings && expanded) Paletting.SGN else Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(
                    // 48dp meets the Android and iOS minimum touch target; the icon
                    // inside stays 17dp so the visual density is unchanged.
                    modifier = Modifier.size(48.dp),
                    onClick = {
                        val removed = viewmodel.removePanel(this@PingGraphView)
                        viewmodel.notify(
                            text = s.panelRemoved(ip),
                            actionLabel = s.undo,
                            action = { viewmodel.restorePanel(removed) },
                        )
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = s.removeTarget(ip),
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        // The control deck: same body as the graph, joined by a hairline seam.
        // Stats and settings are both dark, so flipping modes changes the
        // instruments and never the chassis.
        AnimatedVisibility(
            visible = expanded,
            enter = slideInVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            val deckColor by animateColorAsState(
                if (showSettings) SettingsSurfaceColor else StatsSurfaceColor
            )
            Column(Modifier.fillMaxWidth().background(deckColor)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.20f))
                )
                AnimatedContent(targetState = showSettings) { isSettings ->
                    if (isSettings) {
                        SettingsSheet(fontFamily = interFont)
                    } else {
                        StatsSheet(stats = windowStats, fontFamily = interFont)
                    }
                }
            }
        }
        }
    }
}
