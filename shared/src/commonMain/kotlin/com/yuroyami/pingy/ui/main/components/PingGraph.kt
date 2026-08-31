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
import com.yuroyami.pingy.logic.Ping
import com.yuroyami.pingy.logic.PingPanel
import com.yuroyami.pingy.logic.RingBuffer
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
 * dissolves — alpha sliding to zero so the panel's real texture shows through
 * it — and when the timeout verdict lands there is nothing left to remove.
 * (A color-lerp cannot do this: the background is a texture, and fading toward
 * any flat color paints a slab instead of revealing what is behind.)
 */
private val CHAMELEON_FADE_START_MS = PING_TIMEOUT_MS * 2f / 3f
private const val SCRUB_GLIDE_MS = 350f // frozen time glides back to now instead of teleporting
private const val PEAK_DECAY_PER_SEC = 0.22f // peak-hold line falls this canvas-fraction per second

/** Buffer entries are appended in COMPLETION order while their timestamps
 * are SEND moments, so a reaped loss can sit up to a timeout out of place.
 * Any age jump beyond this is the ring writer clobbering under us. */
private val REORDER_SLACK_MS = PING_TIMEOUT_MS + 1_000L

/** Oldest-first ordering for the visible window (timestamps ascending). */
private val PingTimeOrder = Comparator<Ping> { a, b -> a.timestamp.compareTo(b.timestamp) }

private val FizzleColor = Color(0xFFFF5252)
private val PeakLineColor = Color(0xFFE2E8EF)

// Control deck: dark instrument surfaces with dim chrome around glowing values.
// Settings sit on a slightly lifted shade so the mode flip registers without
// ever leaving the chassis.
private val StatsSurfaceColor = Color(0xFF14181E)
private val SettingsSurfaceColor = Color(0xFF1B212B)
private val StatsLabelColor = Color(0xFF7C8794)
// Was #5E6874 at 3.41:1 against the cockpit background, below the 4.5:1
// minimum for body text. #7C8794 measures 5.29:1.
private val StatsSubColor = Color(0xFF7C8794)
// Was #5A6470 at 3.21:1. #78838F measures 4.79:1.
private val StatsDimColor = Color(0xFF78838F)
private val SettingsTextColor = Color(0xFFC9D2DD)

/** Drag-to-inspect freeze: ages render relative to [freezeMark] so the conveyor halts. */
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
    val textMeasurer = rememberTextMeasurer()
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
    val intervalVal by interval.collectAsState()
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
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { frameTick.longValue = it }
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
    // while still feeling live.
    var readoutValue by remember { mutableStateOf<Int?>(null) }
    var readoutLost by remember { mutableStateOf(false) }
    LaunchedEffect(this@PingGraphView) {
        while (true) {
            pings.last()?.let { last ->
                val v = last.value
                readoutLost = v == null || v < 0
                if (v != null && v >= 0) readoutValue = v
            }
            delay(150)
        }
    }

    // Windowed stats: computed over the SAME visible timeframe as the graph,
    // so the numbers describe what the eye currently sees instead of dragging
    // ancient losses around forever. Sampled at 2.5 Hz, far away from the
    // per-frame draw path and the recomposition path.
    var windowStats by remember { mutableStateOf(WindowStats.EMPTY) }
    LaunchedEffect(this@PingGraphView) {
        while (true) {
            val base = timeframeMs.value
            val effective = visibleWindowMs(base, viewmodel.panelLayout.value)
            windowStats = computeWindowStats(pings, effective)
            delay(400)
        }
    }

    var scrub by remember { mutableStateOf<ScrubFreeze?>(null) }

    // The readout's hue glides between samples; the text itself stays discrete.
    val readoutColor by animateColorAsState(
        targetValue = when {
            readoutLost -> FizzleColor
            else -> readoutValue?.let(::calcPingColor) ?: StatsDimColor
        },
        animationSpec = tween(durationMillis = 300),
    )

    val inter = Font(Res.font.Inter_Regular)
    val interFont = remember(inter) { FontFamily(inter) }
    val chipStyle = remember(interFont) {
        TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = interFont)
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
        // so they share corners, width, and outline — a single instrument.
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
            latest = readoutValue,
            lost = readoutLost,
            stats = windowStats,
            windowMs = visibleWindowMs(timeframeMsVal, layoutVal),
        )
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
                                    resetPreferences()
                                    viewmodel.notify("$ip settings reset")
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
                // are simply not drawn until release — when the leftover offset
                // eases out instead of teleporting the belt forward.
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

                // Celluloid cells are half as wide, so they show half the
                // window: pixel density per millisecond stays constant and
                // the stored preference is untouched.
                // A window narrower than the timeout can never contain a
                // timed-out probe: the loss lands at its send moment, 3s in the
                // past, already off the left edge. The grid used to halve the
                // 5s default to 2.5s and silently hide every timeout.
                val thresholdMs = visibleWindowMs(timeframeMsVal, layoutVal)
                val canvasW = size.width
                val canvasH = size.height
                val pxPerMs = canvasW / thresholdMs.toFloat()
                val minBarPx = 2.5.dp.toPx()

                // Collect the visible window newest-first. Walking backwards
                // from the freshest entry stops at the horizon instead of
                // scanning the whole ring, and moves AWAY from the writer's
                // cursor (which clobbers the oldest slot). Timestamps are
                // SEND moments while the buffer appends in COMPLETION order,
                // so ages may wobble by up to a timeout: only a jump past
                // REORDER_SLACK_MS means the writer caught us, and the walk
                // continues one slack past the window so a late-reaped loss
                // near the edge is not mistaken for the horizon.
                //
                // The window is then sorted oldest-first and folded: entries
                // denser than one pixel column collapse into each other, a
                // loss always surviving the fold, otherwise the worst RTT.
                // The entry just OLDER than the window rides along as the
                // left anchor of the slope/bar bridging into view; without
                // it that whole span would vanish the frame its anchor left.
                visibleBuf.clear()
                var evictedAnchor: Ping? = null
                var anchorAge = Long.MAX_VALUE
                run {
                    var prevAge = Long.MIN_VALUE
                    pings.forEachNewestFirst { p ->
                        val age = (frameNow - p.timestamp).inWholeMilliseconds - freezeOffsetMs
                        when {
                            age + REORDER_SLACK_MS < prevAge -> false
                            age > thresholdMs + REORDER_SLACK_MS -> false
                            else -> {
                                if (age > thresholdMs) {
                                    if (age < anchorAge) {
                                        anchorAge = age
                                        evictedAnchor = p
                                    }
                                } else if (age >= 0) {
                                    visibleBuf.add(p)
                                }
                                if (age > prevAge) prevAge = age
                                true
                            }
                        }
                    }
                }
                evictedAnchor?.let { visibleBuf.add(it) }
                visibleBuf.sortWith(PingTimeOrder)

                // Fold pass: collapse same-column neighbours in place. The
                // anchor (index 0, off-canvas column) never matches an
                // in-window column, so it survives untouched.
                var newestValidAge = Long.MIN_VALUE
                if (visibleBuf.size > 1) {
                    var write = 1
                    var keptCol = ((canvasW - ((frameNow - visibleBuf[0].timestamp).inWholeMilliseconds - freezeOffsetMs) * pxPerMs)).toInt()
                    for (read in 1 until visibleBuf.size) {
                        val p = visibleBuf[read]
                        val col = ((canvasW - ((frameNow - p.timestamp).inWholeMilliseconds - freezeOffsetMs) * pxPerMs)).toInt()
                        val kept = visibleBuf[write - 1]
                        if (col == keptCol) {
                            // Read once into locals: Ping.value has a custom
                            // getter, so it cannot be smart cast.
                            val keptV = kept.value
                            val pV = p.value
                            when {
                                // A loss survives the fold ahead of any reply, so
                                // a dropped packet can never be hidden by a
                                // neighbouring success sharing its pixel column.
                                keptV == null -> Unit
                                pV == null -> visibleBuf[write - 1] = p
                                pV > keptV -> visibleBuf[write - 1] = p
                            }
                        } else {
                            visibleBuf[write] = p
                            write++
                            keptCol = col
                        }
                    }
                    while (visibleBuf.size > write) visibleBuf.removeAt(visibleBuf.lastIndex)
                }
                for (i in visibleBuf.indices.reversed()) {
                    val v = visibleBuf[i].value
                    if (v != null && v >= 0) {
                        newestValidAge = (frameNow - visibleBuf[i].timestamp).inWholeMilliseconds - freezeOffsetMs
                        break
                    }
                }

                // Scrub bookkeeping: the cursor maps to an age, and the slot loop
                // records the geometry of whichever ping lands closest.
                val cursorAgeMs = activeScrub?.let { ((canvasW - it.cursorX) / pxPerMs).toLong() }
                var pickDiff = Long.MAX_VALUE
                var pickFound = false
                var pickLost = false
                var pickValue = 0
                var pickAgeMs = 0L
                var pickLeft = 0f
                var pickWidth = 0f
                var pickHeight = 0f

                var maxTopFraction = 0f
                var lastValidColor: Color? = null

                if (visibleBuf.isNotEmpty() && graphStyleVal == GraphStyle.MOUNTAIN_SLOPES) {
                    // MOUNTAIN-SLOPES: one continuous ridge instead of bars.
                    // Between consecutive valid points the crest runs as a
                    // straight diagonal — steep when the value jumped — and the
                    // fill below blends each point's color into the next, so
                    // every ping keeps its chameleon identity without owning a
                    // rectangle. Lost pings break the range: the massif ends,
                    // the honest gap shows, the next massif begins. Rendered as
                    // one 1px column per device pixel; no allocations.
                    val n = visibleBuf.size
                    val crestPx = 2.dp.toPx()
                    var mountainChameleon: Color? = null
                    var mountainPresence = 1f
                    var newestAge = 0L
                    var newestIsValid = false
                    var newestY = 0f
                    var newestColor = Color.White

                    var index = 0
                    while (index < n) {
                        val ping = visibleBuf[index]
                        val ageA = (frameNow - ping.timestamp).inWholeMilliseconds - freezeOffsetMs
                        val xA = canvasW - ageA * pxPerMs
                        val vA = ping.value
                        val lostA = vA == null || vA < 0
                        var yA = 0f
                        var cA = Color.White
                        if (!lostA) {
                            yA = calculatePingY(vA, canvasH, roofVal.toFloat(), angleOfAttackVal)
                                .coerceAtLeast(minBarPx)
                            cA = calcPingColor(vA)
                            if (activeScrub == null && ageA < BIRTH_MS) {
                                val life = ageA / BIRTH_MS
                                yA = (yA * (1f + BIRTH_OVERSHOOT * sin(PI * life).toFloat())).coerceAtMost(canvasH)
                                cA = lerp(cA, Color.White, BIRTH_BRIGHTEN * (1f - life))
                            }
                            lastValidColor = cA
                            if (yA / canvasH > maxTopFraction) maxTopFraction = yA / canvasH
                        }
                        if (cursorAgeMs != null) {
                            val diff = abs(ageA - cursorAgeMs)
                            if (diff < pickDiff) {
                                pickDiff = diff; pickFound = true; pickLost = lostA
                                pickAgeMs = ageA; pickLeft = xA - 2f; pickWidth = 4f
                                pickHeight = if (lostA) canvasH else yA
                                if (!lostA) pickValue = vA
                            }
                        }
                        // Only good replies advance the wedge anchor: with
                        // pipelined probing, lost verdicts stream in during an
                        // outage and would otherwise reset the ripening wall
                        // every watchdog tick.
                        if (!lostA) {
                            newestAge = ageA
                            newestIsValid = true
                            newestY = yA
                            newestColor = cA
                        }

                        // Fill the columns of the slope from this point to the next.
                        if (index + 1 < n && !lostA) {
                            val next = visibleBuf[index + 1]
                            val vB = next.value
                            if (vB != null && vB >= 0) {
                                val ageB = (frameNow - next.timestamp).inWholeMilliseconds - freezeOffsetMs
                                val xB = canvasW - ageB * pxPerMs
                                var yB = calculatePingY(vB, canvasH, roofVal.toFloat(), angleOfAttackVal)
                                    .coerceAtLeast(minBarPx)
                                var cB = calcPingColor(vB)
                                if (activeScrub == null && ageB < BIRTH_MS) {
                                    val life = ageB / BIRTH_MS
                                    yB = (yB * (1f + BIRTH_OVERSHOOT * sin(PI * life).toFloat())).coerceAtMost(canvasH)
                                    cB = lerp(cB, Color.White, BIRTH_BRIGHTEN * (1f - life))
                                }
                                if (xB > xA + 0.5f && xB > 0f) {
                                    // Integer-aligned unit columns: fractional
                                    // x positions leave an antialiased seam on
                                    // every column, which reads as shimmering
                                    // vertical curtain stripes across the range.
                                    var xi = kotlin.math.ceil(xA.coerceAtLeast(0f)).toInt()
                                    val xEnd = kotlin.math.ceil(xB.coerceAtMost(canvasW)).toInt()
                                    while (xi < xEnd) {
                                        val t = (((xi + 0.5f) - xA) / (xB - xA)).coerceIn(0f, 1f)
                                        val ridge = yA + (yB - yA) * t
                                        if (ridge >= 1f) {
                                            val col = lerp(cA, cB, t)
                                            drawRect(
                                                color = col,
                                                topLeft = Offset(xi.toFloat(), canvasH - ridge),
                                                size = Size(1f, ridge)
                                            )
                                            drawRect(
                                                color = lerp(col, Color.White, 0.5f),
                                                topLeft = Offset(xi.toFloat(), canvasH - ridge),
                                                size = Size(1f, ridge.coerceAtMost(crestPx))
                                            )
                                        }
                                        xi++
                                    }
                                }
                            }
                        }
                        index++
                    }

                    // The leading slope: a wedge climbing from the newest
                    // GOOD point toward the "now" edge, ripening with the
                    // silence since that reply and dissolving over its final
                    // stretch exactly like the pinglette wall does.
                    if (activeScrub == null) {
                        val inFlightMs = (newestAge - intervalVal).coerceAtLeast(0L)
                        if (inFlightMs > 0L) {
                            val colorMs = inFlightMs.coerceAtMost(PING_TIMEOUT_MS.toLong()).toInt()
                            if (colorMs > CHAMELEON_FADE_START_MS) {
                                mountainPresence = 1f - (
                                    (colorMs - CHAMELEON_FADE_START_MS) /
                                        (PING_TIMEOUT_MS - CHAMELEON_FADE_START_MS)
                                    ).coerceIn(0f, 1f)
                            }
                            val yNow = calculatePingY(colorMs, canvasH, roofVal.toFloat(), angleOfAttackVal)
                            val cNow = calcPingColor(colorMs)
                            val xStart = (canvasW - newestAge * pxPerMs).coerceAtLeast(0f)
                            val yStart = if (newestIsValid) newestY else 0f
                            val cStart = if (newestIsValid) newestColor else cNow
                            val span = canvasW - xStart
                            if (span >= 1f && mountainPresence > 0f) {
                                var xi = kotlin.math.ceil(xStart).toInt()
                                val xEnd = kotlin.math.ceil(canvasW).toInt()
                                while (xi < xEnd) {
                                    val t = (((xi + 0.5f) - xStart) / span).coerceIn(0f, 1f)
                                    val ridge = yStart + (yNow - yStart) * t
                                    if (ridge >= 1f) {
                                        drawRect(
                                            color = lerp(cStart, cNow, t),
                                            alpha = mountainPresence,
                                            topLeft = Offset(xi.toFloat(), canvasH - ridge),
                                            size = Size(1f, ridge)
                                        )
                                    }
                                    xi++
                                }
                            }
                            mountainChameleon = cNow
                            if (yNow / canvasH > maxTopFraction) maxTopFraction = yNow / canvasH
                        }

                        (mountainChameleon ?: lastValidColor)?.let { target ->
                            val chase = 1f - exp(-dtSec * 10f)
                            val smoothed = auraSmooth.color?.let { lerp(it, target, chase) } ?: target
                            auraSmooth.color = smoothed
                            val auraAlpha = 0.13f * (if (mountainChameleon != null) mountainPresence else 1f)
                            val auraW = 28.dp.toPx()
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Transparent,
                                    1f to smoothed.copy(alpha = auraAlpha),
                                    startX = canvasW - auraW,
                                    endX = canvasW,
                                ),
                                topLeft = Offset(canvasW - auraW, 0f),
                                size = Size(auraW, canvasH)
                            )
                        }
                    }
                }

                if (visibleBuf.isNotEmpty() && graphStyleVal == GraphStyle.PINGLETTES) {
                    // Slot-based drawing. Each valid bar tiles against its
                    // predecessor when that predecessor was also valid — the
                    // width in time is (this_ping.ts - prev_ping.ts), which
                    // keeps a long-RTT bar visibly wider because it ate more
                    // real time before the next probe could land.
                    //
                    // Void (null-valued) pings DRAW NOTHING but a short-lived
                    // fizzle burst. A timeout has no duration to represent, and
                    // the slot before the next valid bar is rendered as literal
                    // background — the gap itself carries the "lost packet"
                    // signal at a glance.
                    var prevAgeMs = 0L
                    // First iteration has no predecessor, treat as invalid
                    // so widthMs falls back to the ping's own RTT.
                    var prevWasInvalid = true

                    for (i in visibleBuf.indices) {
                        val ping = visibleBuf[i]
                        val ageMs = (frameNow - ping.timestamp).inWholeMilliseconds - freezeOffsetMs
                        val previousAgeMs = prevAgeMs
                        val predecessorWasInvalid = prevWasInvalid
                        prevAgeMs = ageMs

                        val value = ping.value
                        val isLost = value == null || value < 0
                        prevWasInvalid = isLost

                        val rightEdgePx = canvasW - ageMs * pxPerMs

                        if (isLost) {
                            // No death drawing: the chameleon already faded the
                            // wall into the background during its final stretch,
                            // so the honest gap it leaves is a seamless swap.
                            if (cursorAgeMs != null) {
                                val diff = abs(ageMs - cursorAgeMs)
                                if (diff < pickDiff) {
                                    pickDiff = diff; pickFound = true; pickLost = true
                                    pickAgeMs = ageMs; pickLeft = rightEdgePx - 2f
                                    pickWidth = 4f; pickHeight = canvasH
                                }
                            }
                            continue
                        }

                        if (rightEdgePx <= 0f) continue

                        val widthMs: Long = if (!predecessorWasInvalid) {
                            (previousAgeMs - ageMs).coerceAtLeast(1L)
                        } else {
                            // No valid predecessor → don't reach back over
                            // a gap, just paint this bar at its own duration.
                            value.toLong().coerceAtLeast(1L)
                        }
                        val widthPx = (widthMs.toFloat() * pxPerMs).coerceAtLeast(1f)
                        // No coerceAtLeast(0f) on the left edge: letting it
                        // go negative keeps the bar's full width intact as
                        // it exits the canvas. Compose clips off-canvas
                        // drawing for free.
                        val leftEdgePx = rightEdgePx - widthPx

                        var y = calculatePingY(value, canvasH, roofVal.toFloat(), angleOfAttackVal)
                            .coerceAtLeast(minBarPx)
                        var color = calcPingColor(value)

                        // Birth ritual, resumed after the chameleon: for its
                        // first BIRTH_MS the bar overshoots its true height and
                        // carries extra brightness, both easing back to truth.
                        // Skipped while frozen — a scrubbed past shouldn't wiggle.
                        if (activeScrub == null && ageMs < BIRTH_MS) {
                            val life = ageMs / BIRTH_MS
                            y = (y * (1f + BIRTH_OVERSHOOT * sin(PI * life).toFloat())).coerceAtMost(canvasH)
                            color = lerp(color, Color.White, BIRTH_BRIGHTEN * (1f - life))
                        }
                        lastValidColor = color

                        drawRect(
                            color = color,
                            topLeft = Offset(leftEdgePx, canvasH - y),
                            size = Size(widthPx, y)
                        )
                        // Tip highlight sells the "filled up to its level" read,
                        // but hairline bars at zero-interval rates can't show a
                        // tip, so they skip it.
                        if (widthPx >= 3f) {
                            drawRect(
                                color = lerp(color, Color.White, 0.45f),
                                topLeft = Offset(leftEdgePx, canvasH - y),
                                size = Size(widthPx, y.coerceAtMost(3f))
                            )
                        }

                        if (y / canvasH > maxTopFraction) maxTopFraction = y / canvasH

                        if (cursorAgeMs != null) {
                            val diff = abs(ageMs - cursorAgeMs)
                            if (diff < pickDiff) {
                                pickDiff = diff; pickFound = true; pickLost = false
                                pickValue = value; pickAgeMs = ageMs
                                pickLeft = leftEdgePx; pickWidth = widthPx; pickHeight = y
                            }
                        }
                    }

                    // Chameleon — the in-flight slot between the newest buffer
                    // entry and the present moment. Colorizes in real time with
                    // elapsed in-flight duration so the user watches the probe
                    // "ripen" on the way to its resolved RTT. Suppressed while
                    // scrubbing: frozen time has no present moment.
                    if (activeScrub == null) {
                        var chameleonColor: Color? = null
                        var chameleonPresence = 1f
                        // Silence-of-success drives the wall: with pipelined
                        // probing, lost verdicts keep arriving DURING an
                        // outage, so "age of the newest verdict" would reset
                        // the wall every watchdog tick. Time since the last
                        // GOOD reply is what actually ripens toward the void.
                        val newestAge = if (newestValidAge == Long.MIN_VALUE) 0L
                                        else newestValidAge.coerceAtLeast(0L)
                        val inFlightMs = (newestAge - intervalVal).coerceAtLeast(0L)
                        if (inFlightMs > 0L) {
                            val widthPx = inFlightMs.toFloat() * pxPerMs
                            val leftEdgePx = (canvasW - widthPx).coerceAtLeast(0f)
                            val drawW = canvasW - leftEdgePx
                            if (drawW >= 1f) {
                                val colorMs = inFlightMs.coerceAtMost(PING_TIMEOUT_MS.toLong()).toInt()
                                val y = calculatePingY(
                                    colorMs,
                                    canvasH,
                                    roofVal.toFloat(),
                                    angleOfAttackVal
                                )
                                // Past the fade threshold the wall dissolves in
                                // place — transparency, not a color shift, is the
                                // only way to become a textured background.
                                if (colorMs > CHAMELEON_FADE_START_MS) {
                                    chameleonPresence = 1f - (
                                        (colorMs - CHAMELEON_FADE_START_MS) /
                                            (PING_TIMEOUT_MS - CHAMELEON_FADE_START_MS)
                                        ).coerceIn(0f, 1f)
                                }
                                chameleonColor = calcPingColor(colorMs)
                                drawRect(
                                    color = chameleonColor,
                                    alpha = chameleonPresence,
                                    topLeft = Offset(leftEdgePx, canvasH - y),
                                    size = Size(drawW, y)
                                )
                                if (y / canvasH > maxTopFraction) maxTopFraction = y / canvasH
                            }
                        }

                        // Live-edge aura: a soft breath of the newest color
                        // hugging the right border. It chases its target color
                        // exponentially, so a teal→green flip breathes instead
                        // of snapping.
                        (chameleonColor ?: lastValidColor)?.let { target ->
                            val chase = 1f - exp(-dtSec * 10f)
                            val smoothed = auraSmooth.color?.let { lerp(it, target, chase) } ?: target
                            auraSmooth.color = smoothed
                            // The aura breathes out with a dissolving wall.
                            val auraAlpha = 0.13f * (if (chameleonColor != null) chameleonPresence else 1f)
                            val auraW = 28.dp.toPx()
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Transparent,
                                    1f to smoothed.copy(alpha = auraAlpha),
                                    startX = canvasW - auraW,
                                    endX = canvasW,
                                ),
                                topLeft = Offset(canvasW - auraW, 0f),
                                size = Size(auraW, canvasH)
                            )
                        }
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

                // Landmark labels
                for (y in landMarksVal) {
                    val h = calculatePingY(y.toInt(), canvasH, roofVal.toFloat(), angleOfAttackVal)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "${y.toInt()}",
                        topLeft = Offset(x = 20f, y = canvasH - h - 4f),
                        style = TextStyle(
                            fontSize = 7.sp,
                            color = Color(200, 200, 220, 160)
                        )
                    )
                }

                // Current-ping readout + target address on one HUD plate,
                // colored by the same scale as the bars. Throttled upstream so
                // it never strobes; its color tweens between samples instead
                // of snapping. A host that has only ever timed out still earns
                // its red ×.
                if (readoutLost || readoutValue != null) {
                    val neonColor = readoutColor
                    val neonText = if (readoutLost) "×" else "$readoutValue ms"
                    val neon = textMeasurer.measure(
                        buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    color = neonColor,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    shadow = Shadow(color = neonColor, blurRadius = 14f),
                                )
                            ) { append(neonText) }
                            withStyle(
                                SpanStyle(color = StatsLabelColor, fontSize = 11.sp)
                            ) { append("   $ip") }
                        },
                        TextStyle(fontFamily = interFont),
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
                    val chipText = if (pickLost) {
                        "timeout · ${formatShortAge(pickAgeMs)}"
                    } else {
                        "$pickValue ms · ${formatShortAge(pickAgeMs)}"
                    }
                    val chip = textMeasurer.measure(AnnotatedString(chipText), chipStyle)
                    val padX = 8.dp.toPx()
                    val padY = 5.dp.toPx()
                    val chipW = chip.size.width + padX * 2
                    val chipH = chip.size.height + padY * 2
                    val chipLeft = (cursorX - chipW / 2f).coerceIn(4f, (canvasW - chipW - 4f).coerceAtLeast(4f))
                    val chipTop = 44.dp.toPx().coerceAtMost(canvasH - chipH - 4f)
                    val chipColor = if (pickLost) FizzleColor else calcPingColor(pickValue)
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
                            "$ip: " + if (next == GraphStyle.PINGLETTES) "bars" else "ridge"
                        )
                    },
                ) {
                    // Previews the style a tap would switch this panel to.
                    Icon(
                        imageVector = if (graphStyleVal == GraphStyle.MOUNTAIN_SLOPES) Icons.Filled.BarChart
                                      else Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = "Switch this panel's graph style",
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
                        contentDescription = "Panel settings",
                        tint = if (showSettings && expanded) Paletting.SGN else Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(
                    // 48dp meets the Android and iOS minimum touch target; the icon
                    // inside stays 17dp so the visual density is unchanged.
                    modifier = Modifier.size(48.dp),
                    onClick = {
                        viewmodel.notify("$ip removed")
                        viewmodel.removePanel(this@PingGraphView)
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Remove $ip",
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        // The control deck: same body as the graph, joined by a hairline seam.
        // Stats and settings are both dark now — flipping modes changes the
        // instruments, never the chassis.
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

/**
 * Windowed instrument strip on ONE line, always. The whole strip is a single
 * annotated string with relative (em) span sizes, and auto-size shrinks the
 * base until it fits the panel's width — so a half-width celluloid cell just
 * renders the same line smaller instead of wrapping. Every number describes
 * the SAME slice of time the canvas shows.
 */
@Composable
private fun StatsSheet(stats: WindowStats, fontFamily: FontFamily) {
    val line = buildAnnotatedString {
        fun label(text: String) {
            withStyle(
                SpanStyle(
                    color = StatsLabelColor,
                    fontSize = 0.72.em,
                    letterSpacing = 0.09.em,
                    fontWeight = FontWeight.Medium,
                )
            ) { append(text) }
        }

        fun value(text: String, color: Color, glow: Boolean) {
            withStyle(
                SpanStyle(
                    color = color,
                    fontWeight = FontWeight.Bold,
                    shadow = if (glow) Shadow(color = color, blurRadius = 12f) else null,
                )
            ) { append(text) }
        }

        label("AVG ")
        value(
            stats.avg?.let { "${formatRtt(it)}ms" } ?: "—",
            stats.avg?.let { calcPingColor(it.roundToInt()) } ?: StatsDimColor,
            stats.avg != null,
        )
        // Mean absolute successive difference between consecutive replies. The
        // old label was "±", which advertises a symmetric interval this never
        // computed. Sub-millisecond values are now real rather than floored to
        // zero, because the RTT is no longer rounded before the statistics run.
        label("  JIT ")
        value(stats.jitter?.let { formatRtt(it) } ?: "—", SettingsTextColor, false)
        label("  LOSS ")
        run {
            val lossPercent = if (stats.count > 0) stats.lost * 100f / stats.count else null
            // Loss only earns color once packets actually die.
            val color = when {
                lossPercent == null -> StatsDimColor
                lossPercent <= 0.001f -> SettingsTextColor
                else -> lossColor(lossPercent)
            }
            value(
                lossPercent?.let { "${formatFloat1(it)}% (${stats.lost}/${stats.count})" } ?: "—",
                color,
                lossPercent != null && lossPercent > 0.001f,
            )
        }
        label("  GONE ")
        run {
            val gone = stats.gonePct
            // Time-based loss: how much of the window was actually dark.
            value(
                gone?.let { "${formatFloat1(it)}%" } ?: "—",
                when {
                    gone == null -> StatsDimColor
                    gone <= 0.05f -> SettingsTextColor
                    else -> lossColor(gone)
                },
                gone != null && gone > 0.05f,
            )
        }
        label("  RANGE ")
        value(
            if (stats.min != null && stats.max != null) "${stats.min}–${stats.max}ms" else "—",
            SettingsTextColor,
            false,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = line,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(fontFamily = fontFamily, fontSize = 15.sp, color = SettingsTextColor),
            autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = 15.sp, stepSize = 0.25.sp),
        )
    }
}

/** Packet loss severity on the shared color scale: 0% reads healthy teal, 5%+ reads magenta. */
private fun lossColor(percent: Float): Color = when {
    percent <= 0.05f -> calcPingColor(40)
    percent < 1f -> calcPingColor(300)
    percent < 5f -> calcPingColor(650)
    else -> calcPingColor(1_200)
}

/**
 * Settings deck: one slim row per dial — label, slider, live value — plus a
 * persist switch. Bound directly to the panel's StateFlows; everything
 * applies on-the-fly.
 */
@Composable
private fun PingPanel.SettingsSheet(fontFamily: FontFamily) {
    val viewmodel = LocalViewmodel.current
    val packetSizeVal by packetSize.collectAsState()
    val intervalVal by interval.collectAsState()
    val roofVal by roof.collectAsState()
    val angleOfAttackVal by angleOfAttack.collectAsState()
    val timeframeMsVal by timeframeMs.collectAsState()
    val canvasHeightFractionVal by canvasHeightFraction.collectAsState()
    val persistVal by persistAcrossSessions.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$ip · long-press the graph to reset",
                color = StatsSubColor,
                fontSize = 10.sp,
                fontFamily = fontFamily,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "REMEMBER",
                color = StatsLabelColor,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
                fontFamily = fontFamily,
            )
            Switch(
                checked = persistVal,
                onCheckedChange = { checked ->
                    persistAcrossSessions.value = checked
                    viewmodel.notify(
                        if (checked) "$ip will be there on next launch"
                        else "$ip will not come back on next launch"
                    )
                },
                modifier = Modifier.scale(0.62f),
                colors = SwitchDefaults.colors(checkedTrackColor = Paletting.SGN2),
            )
        }

        CompactSlider(
            label = "Interval",
            valueText = if (intervalVal <= 0L) "adaptive" else "${intervalVal}ms",
            value = intervalVal.toFloat(),
            range = 0f..2000f,
            steps = 39, // 50ms steps; 0 = fire-as-fast-as-replies-land
            fontFamily = fontFamily,
        ) { interval.value = it.toLong() }
        CompactSlider(
            label = "Packet",
            valueText = "${packetSizeVal} B",
            value = packetSizeVal.toFloat(),
            range = 16f..480f,
            steps = 28, // 16-byte steps
            fontFamily = fontFamily,
        ) { packetSize.value = it.toInt() }
        CompactSlider(
            label = "Attack",
            valueText = if (angleOfAttackVal <= 0.01f) "linear" else formatFloat1(angleOfAttackVal),
            value = angleOfAttackVal,
            range = 0f..20f,
            steps = 40,
            fontFamily = fontFamily,
        ) { angleOfAttack.value = it }
        CompactSlider(
            label = "Window",
            valueText = formatTimeframe(timeframeMsVal),
            value = timeframeMsVal.toFloat(),
            range = 1_000f..30_000f,
            steps = 28, // 1s steps
            fontFamily = fontFamily,
        ) { timeframeMs.value = it.toLong() }
        CompactSlider(
            label = "Roof",
            valueText = "${roofVal}ms",
            value = roofVal.toFloat(),
            range = 100f..2000f,
            steps = 18, // 100ms steps
            fontFamily = fontFamily,
        ) { roof.value = it.toInt() }
        CompactSlider(
            label = "Height",
            valueText = "${(canvasHeightFractionVal * 100).roundToInt()}%",
            value = canvasHeightFractionVal,
            range = 0.10f..0.45f,
            steps = 34,
            fontFamily = fontFamily,
        ) { canvasHeightFraction.value = it }
    }
}

/** One settings row: fixed label, elastic slider, fixed live value. */
@Composable
private fun CompactSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    fontFamily: FontFamily,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = StatsLabelColor,
            fontSize = 11.sp,
            fontFamily = fontFamily,
            maxLines = 1,
            modifier = Modifier.width(58.dp),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Paletting.A_MAIN_COLOR,
                activeTrackColor = Paletting.A_MAIN_COLOR,
                inactiveTrackColor = Paletting.A_MAIN_COLOR.copy(alpha = 0.25f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        Text(
            text = valueText,
            color = SettingsTextColor,
            fontSize = 11.sp,
            fontFamily = fontFamily,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.width(62.dp),
        )
    }
}

private fun formatTimeframe(ms: Long): String {
    val totalSec = ms / 1000
    return when {
        totalSec < 60 -> "${totalSec}s"
        totalSec < 3600 -> {
            val m = totalSec / 60
            val s = totalSec % 60
            if (s == 0L) "${m}m" else "${m}m ${s}s"
        }
        else -> "${totalSec / 3600}h"
    }
}

/** Fast start, gentle landing — the shape of every glide in this file. */
private fun easeOutCubic(t: Double): Double {
    val u = 1.0 - t.coerceIn(0.0, 1.0)
    return 1.0 - u * u * u
}

/** Sub-second-precision age for the scrub chip, e.g. "0.4s" / "2.3s" / "1m 5s". */
private fun formatShortAge(ms: Long): String = if (ms < 60_000) {
    val tenths = (ms / 100).coerceAtLeast(0)
    "${tenths / 10}.${tenths % 10}s"
} else {
    formatTimeframe(ms)
}

/**
 * Format an RTT for display. Sub-millisecond values keep two decimals so a LAN
 * target does not read as a flat "0ms"; anything above 10 ms rounds to a whole
 * millisecond, which is all the precision a reader can use.
 */
private fun formatRtt(ms: Double): String = when {
    ms < 1.0 -> {
        val hundredths = (ms * 100).roundToInt()
        "0.${(hundredths % 100).toString().padStart(2, '0')}"
    }
    ms < 10.0 -> formatFloat1(ms.toFloat())
    else -> ms.roundToInt().toString()
}

/** One-decimal formatter without depending on platform `Locale` / `String.format`. */
private fun formatFloat1(value: Float): String {
    val negative = value < 0f
    val absTenths = (abs(value) * 10f).roundToInt()
    val whole = absTenths / 10
    val frac = absTenths % 10
    val sign = if (negative && (whole != 0 || frac != 0)) "-" else ""
    return "$sign$whole.$frac"
}

/** Exponential scaling to emphasize low ping values in the graph.
 * Uses the normalized formula: f * (1 - 2^(-x*z/f)) / (1 - 2^(-z))
 *
 * Normalizing by (1 - 2^(-z)) pins the roof to full height for EVERY zoom
 * factor and makes the family continuous: as z shrinks toward 0 the curve
 * settles onto the linear 1:1 line instead of collapsing toward zero height
 * (the old un-normalized formula squashed the whole graph to about a third
 * of its height at z=0.5, one notch away from linear). Higher factors push
 * low pings further up, which is what gamers care about.
 */
private fun exponentialize(x: Float, f: Float, zoomFactor: Float): Double {
    val xc = x.toDouble().coerceIn(0.0, f.toDouble())
    if (zoomFactor <= 0.001f) {
        // Pure linear mapping.
        return xc
    }
    val z = zoomFactor.toDouble()
    val curved = 1.0 - 2.0.pow(-xc * z / f.toDouble())
    val fullScale = 1.0 - 2.0.pow(-z)
    return f.toDouble() * (curved / fullScale)
}

/**
 * Spoken equivalent of the graph.
 *
 * Everything the picture conveys, in one sentence: what is being monitored, the
 * newest reading, the window it covers, and the same aggregates rendered beside
 * it. Local faults are named rather than folded into loss, matching what the
 * numbers now do.
 */
private fun buildGraphSummary(
    ip: String,
    latest: Int?,
    lost: Boolean,
    stats: WindowStats,
    windowMs: Long,
): String = buildString {
    append(ip)
    append(". ")
    when {
        lost -> append("Latest probe timed out. ")
        latest != null -> append("Latest round trip ").append(latest).append(" milliseconds. ")
        else -> append("No reading yet. ")
    }
    append("Over the last ").append(windowMs / 1000).append(" seconds: ")
    if (stats.count == 0) {
        append("no probes sent")
    } else {
        append(stats.count).append(" sent, ").append(stats.lost).append(" lost")
        stats.avg?.let { append(", average ").append(it.roundToInt()).append(" milliseconds") }
        stats.min?.let { append(", best ").append(it.roundToInt()) }
        stats.max?.let { append(", worst ").append(it.roundToInt()) }
    }
    if (stats.localFaults > 0) {
        append(". ").append(stats.localFaults)
        append(" probe attempts could not leave this device and are excluded")
    }
    append(".")
}

/**
 * The window actually drawn, in milliseconds.
 *
 * Grid cells are half as wide, so they show half the window to keep pixels per
 * millisecond constant. That is fine right up until the result drops below
 * [PING_TIMEOUT_MS], at which point a timeout can never be rendered at all, and
 * the graph quietly disagrees with the loss counter beside it.
 */
internal fun visibleWindowMs(timeframeMs: Long, layout: PanelLayout): Long {
    val scaled = if (layout == PanelLayout.GRID) timeframeMs / 2 else timeframeMs
    return scaled.coerceAtLeast(PING_TIMEOUT_MS.toLong())
}

/** Calculates a ping height on the current panel based on its value. */
private fun calculatePingY(ping: Int, panelHeight: Float, pingMaxVal: Float, zoomFactor: Float): Float {
    return (exponentialize(ping.toFloat(), pingMaxVal, zoomFactor) * (panelHeight.toDouble() / pingMaxVal)).toFloat()
}

/** The app-wide RTT color scale lives in the theme; the graph just speaks it. */
private fun calcPingColor(ping: Int): Color = pingColor(ping)

/**
 * Stats over the visible timeframe.
 *
 * [count] counts probes that actually left the device, so it is the honest
 * denominator for [lost]. [localFaults] is reported separately because a DNS or
 * socket failure says nothing about the target.
 *
 * [gonePct] is time-based loss over [coveredMs], the span actually observed,
 * rather than over the whole window. Values stay in milliseconds as doubles;
 * rounding happens only at the point of display.
 */
private data class WindowStats(
    val count: Int,
    val lost: Int,
    val localFaults: Int,
    val avg: Double?,
    val min: Double?,
    val max: Double?,
    /** Mean absolute successive difference between consecutive replies. */
    val jitter: Double?,
    val gonePct: Float?,
    val coveredMs: Long,
) {
    companion object {
        val EMPTY = WindowStats(0, 0, 0, null, null, null, null, null, 0L)
    }
}

/** One pass over the ring, newest-first, stopping at the window's horizon.
 *
 * Jitter pairs only consecutive valid samples — a loss breaks adjacency, so
 * the wobble number never spans a gap.
 *
 * GONE uses completion-gap attribution: each verdict owns the time span
 * back to the previous verdict (the oldest one owns its span back to the
 * window edge), and a span counts as gone when its verdict was a loss.
 * With pipelined probing this is honest in both directions: during a real
 * outage lost verdicts stream at the watchdog cadence and their spans tile
 * the whole dark stretch, while a single dropped packet resolves BETWEEN
 * two healthy replies and owns only milliseconds. The still-unresolved
 * stretch between the newest verdict and "now" belongs to nobody. */
private fun computeWindowStats(pings: RingBuffer<Ping>, windowMs: Long): WindowStats {
    val now = TimeSource.Monotonic.markNow()

    // Gather the window, tolerant of send-time entries appended in
    // completion order (a reaped loss sits up to a timeout out of place),
    // then sort oldest-first so spans and jitter pair true time-neighbours.
    val window = ArrayList<Ping>(256)
    var prevAge = Long.MIN_VALUE
    pings.forEachNewestFirst { p ->
        val age = (now - p.timestamp).inWholeMilliseconds
        when {
            age + REORDER_SLACK_MS < prevAge -> return@forEachNewestFirst false
            age > windowMs + REORDER_SLACK_MS -> return@forEachNewestFirst false
            else -> {
                if (age in 0..windowMs) window.add(p)
                if (age > prevAge) prevAge = age
                true
            }
        }
    }
    window.sortWith(PingTimeOrder)

    var count = 0
    var lost = 0
    var localFaults = 0
    var sum = 0.0
    var valid = 0
    var min = Double.MAX_VALUE
    var max = -Double.MAX_VALUE
    var jitterSum = 0.0
    var jitterCount = 0
    var olderValue = 0.0
    var olderWasValid = false
    var olderAge = Long.MIN_VALUE
    var spanTotal = 0L
    var spanGone = 0L

    // The oldest sample in the window may be the oldest we HAVE, not the oldest
    // there was. Attributing everything back to the window edge invented outage
    // time that was never observed: two failures a second apart could report
    // 100% gone across a five second window the app had not even been running
    // for. Coverage starts at the oldest sample we actually hold.
    val oldestAge = window.firstOrNull()?.let { (now - it.timestamp).inWholeMilliseconds }
    val coveredMs = oldestAge?.coerceAtMost(windowMs) ?: 0L

    for (p in window) {
        val age = (now - p.timestamp).inWholeMilliseconds

        // A local fault means no probe ever left this device. It is not evidence
        // about the target, so it enters neither the sent count, the loss count,
        // nor the time-based outage share.
        if (p.isLocalFault) {
            localFaults++
            continue
        }

        count++
        val v = p.rttMs
        val isLost = p.isLoss

        // Each verdict owns the span back to its predecessor. The oldest owns
        // only back to the start of observed coverage.
        val span = if (olderAge == Long.MIN_VALUE) coveredMs - age else olderAge - age
        if (span > 0) {
            spanTotal += span
            if (isLost) spanGone += span
        }
        olderAge = age
        if (isLost || v == null) {
            lost++
            olderWasValid = false
        } else {
            sum += v
            valid++
            if (v < min) min = v
            if (v > max) max = v
            if (olderWasValid) {
                jitterSum += abs(olderValue - v)
                jitterCount++
            }
            olderValue = v
            olderWasValid = true
        }
    }
    return WindowStats(
        count = count,
        lost = lost,
        localFaults = localFaults,
        avg = if (valid > 0) sum / valid else null,
        min = if (valid > 0) min else null,
        max = if (valid > 0) max else null,
        jitter = if (jitterCount > 0) jitterSum / jitterCount else null,
        gonePct = if (spanTotal > 0) spanGone * 100f / spanTotal else null,
        coveredMs = coveredMs,
    )
}

