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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UnfoldLessDouble
import androidx.compose.material.icons.filled.UnfoldMoreDouble
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.pingy.GraphStyle
import com.yuroyami.pingy.logic.Ping
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
import kotlin.time.TimeMark
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

private val FizzleColor = Color(0xFFFF5252)
private val PeakLineColor = Color(0xFFE2E8EF)

// Control deck: dark instrument surfaces with dim chrome around glowing values.
// Settings sit on a slightly lifted shade so the mode flip registers without
// ever leaving the chassis.
private val StatsSurfaceColor = Color(0xFF14181E)
private val SettingsSurfaceColor = Color(0xFF1B212B)
private val StatsLabelColor = Color(0xFF7C8794)
private val StatsSubColor = Color(0xFF5E6874)
private val StatsDimColor = Color(0xFF5A6470)
private val SettingsTextColor = Color(0xFFC9D2DD)

/** Drag-to-inspect freeze: ages render relative to [freezeMark] so the conveyor halts. */
private data class ScrubFreeze(val freezeMark: TimeMark, val cursorX: Float)

// Gesture verdicts for the manual tap / long-press / scrub state machine.
private const val GESTURE_SCROLL = 0
private const val GESTURE_TAP = 1
private const val GESTURE_SCRUB = 3

/**
 * Full graph panel view:
 *
 * - A canvas at the top drawing the ping history. Tapping the canvas switches
 *   the sheet below between **statistics** (default) and **settings** modes;
 *   a motionless long-press resets tuning preferences; a horizontal drag
 *   freezes the conveyor and scrubs bar-by-bar with an exact readout chip.
 * - Newborn bars overshoot and glow briefly, timeouts burn a short red fizzle
 *   at the start of their gap, and a slowly-falling peak-hold line marks the
 *   recent maximum like a VU meter.
 * - A neon current-ping readout sits on the canvas, throttled to stay readable
 *   at zero-interval probe rates.
 * - A standalone minimize/expand [IconButton] overlaid on the canvas' top-right
 *   corner toggles the sheet's expanded/collapsed state.
 * - The bottom sheet contains either stats or tuning sliders, all reactive via
 *   the panel's StateFlows.
 */
@Composable
fun PingPanel.PingGraphView(modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val windowInfo = LocalWindowInfo.current
    val windowHeightDp by derivedStateOf { windowInfo.containerDpSize.height }

    val pings = this.pings
    val graphStyleVal by LocalViewmodel.current.graphStyle.collectAsState()
    val version by pingVersion.collectAsState()
    val expanded by expanded.collectAsState()
    val showSettings by showSettings.collectAsState()

    val pingsSentVal by pingsSent.collectAsState()
    val pingsLostVal by pingsLost.collectAsState()
    val lowestPingVal by lowestPing.collectAsState()
    val highestPingVal by highestPing.collectAsState()
    val averagePingVal by averagePing.collectAsState()
    val jitterVal by jitter.collectAsState()

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
    val glide = remember { object { var startMark: TimeMark? = null; var fromOffsetMs = 0L } }

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

    var scrub by remember { mutableStateOf<ScrubFreeze?>(null) }

    // The readout's hue glides between samples; the text itself stays discrete.
    val readoutColor by animateColorAsState(
        targetValue = when {
            readoutLost -> FizzleColor
            else -> readoutValue?.let(::calcPingColor) ?: StatsDimColor
        },
        animationSpec = tween(durationMillis = 300),
    )

    val interFont = FontFamily(Font(Res.font.Inter_Regular))
    val txtstyle = TextStyle(
        color = SettingsTextColor,
        fontSize = 14.sp,
        fontFamily = interFont,
        shadow = Shadow(color = Color.Black, blurRadius = 4f)
    )
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
        Box(modifier = Modifier.fillMaxWidth().height(canvasHeight)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    // Manual gesture FSM instead of combinedClickable: tap flips
                    // stats/settings, motionless long-press resets preferences
                    // (both as before), and a horizontal drag scrubs the frozen
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
                                null -> resetPreferences() // held still past the long-press timeout
                                GESTURE_TAP -> this@PingGraphView.showSettings.update { !it }
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

                // Force redraw every frame for smooth scrolling. Reading pingVersion
                // as well keeps the callback-driven invalidation path alive.
                @Suppress("UNUSED_VARIABLE") val v = version
                val nowFrameMs = frameTick.longValue
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

                // Collect the subset of pings within the user-chosen timeframe.
                // Reuses the outer `visibleBuf` to avoid a per-frame allocation.
                // The newest ALREADY-EVICTED ping rides along as index 0: it is
                // the left anchor of the slope (and the width donor of the bar)
                // that bridges into the window. Without it, that whole span
                // vanishes the frame its anchor leaves, punching a gap as wide
                // as the ping lasted instead of sliding out under the border.
                val thresholdMs = timeframeMsVal
                visibleBuf.clear()
                var evictedAnchor: Ping? = null
                pings.fastForEachWithIndex { p, _ ->
                    if (p != null) {
                        val age = p.timestamp.elapsedNow().inWholeMilliseconds - freezeOffsetMs
                        if (age > thresholdMs) {
                            evictedAnchor = p
                        } else if (age >= 0) {
                            if (visibleBuf.isEmpty()) evictedAnchor?.let { visibleBuf.add(it) }
                            visibleBuf.add(p)
                        }
                    }
                }

                val canvasW = size.width
                val canvasH = size.height
                val pxPerMs = canvasW / thresholdMs.toFloat()

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
                        val ageA = ping.timestamp.elapsedNow().inWholeMilliseconds - freezeOffsetMs
                        val xA = canvasW - ageA * pxPerMs
                        val vA = ping.value
                        val lostA = vA == null || vA < 0
                        var yA = 0f
                        var cA = Color.White
                        if (!lostA) {
                            yA = calculatePingY(vA, canvasH, roofVal.toFloat(), angleOfAttackVal)
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
                        newestAge = ageA
                        newestIsValid = !lostA
                        if (!lostA) { newestY = yA; newestColor = cA }

                        // Fill the columns of the slope from this point to the next.
                        if (index + 1 < n && !lostA) {
                            val next = visibleBuf[index + 1]
                            val vB = next.value
                            if (vB != null && vB >= 0) {
                                val ageB = next.timestamp.elapsedNow().inWholeMilliseconds - freezeOffsetMs
                                val xB = canvasW - ageB * pxPerMs
                                var yB = calculatePingY(vB, canvasH, roofVal.toFloat(), angleOfAttackVal)
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

                    // The in-flight probe is the range's leading slope: a wedge
                    // climbing from the newest point toward the "now" edge,
                    // ripening in color and dissolving over its final stretch
                    // exactly like the pinglette wall does.
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
                    var lastAgeMs = 0L
                    // First iteration has no predecessor, treat as invalid
                    // so widthMs falls back to the ping's own RTT.
                    var prevWasInvalid = true

                    for (i in visibleBuf.indices) {
                        val ping = visibleBuf[i]
                        val ageMs = ping.timestamp.elapsedNow().inWholeMilliseconds - freezeOffsetMs
                        val previousAgeMs = prevAgeMs
                        val predecessorWasInvalid = prevWasInvalid
                        prevAgeMs = ageMs
                        lastAgeMs = ageMs

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
                        val newestAge = lastAgeMs.coerceAtLeast(0L)
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

                // Neon current-ping readout, colored by the same scale as the
                // bars. Throttled upstream so it never strobes; its color tweens
                // between samples instead of snapping. A host that has only ever
                // timed out still earns its red ×.
                if (readoutLost || readoutValue != null) {
                    val neonColor = readoutColor
                    val neonText = if (readoutLost) "×" else "$readoutValue ms"
                    val neon = textMeasurer.measure(
                        AnnotatedString(neonText),
                        TextStyle(
                            color = neonColor,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = interFont,
                            shadow = Shadow(color = neonColor, blurRadius = 22f),
                        ),
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

            // Standalone minimize toggle: small button in the top-right of the graph.
            // Sits above the canvas so it intercepts clicks in its small area
            // before the canvas' gestures can fire.
            IconButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(32.dp),
                onClick = { this@PingGraphView.expanded.value = !expanded }
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.UnfoldLessDouble
                                  else Icons.Filled.UnfoldMoreDouble,
                    contentDescription = if (expanded) "Minimize sheet" else "Expand sheet",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
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
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Small indicator of the current mode (not clickable — mode switching
                    // happens exclusively via the graph canvas click).
                    Icon(
                        imageVector = if (showSettings) Icons.Filled.Settings
                                      else Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(18.dp)
                    )

                    AnimatedContent(targetState = showSettings) { isSettings ->
                        if (isSettings) {
                            SettingsSheet(txtstyle = txtstyle)
                        } else {
                            StatsSheet(
                                ip = ip,
                                average = averagePingVal,
                                jitterMs = jitterVal,
                                sent = pingsSentVal,
                                lost = pingsLostVal,
                                lowest = lowestPingVal,
                                highest = highestPingVal,
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

/**
 * Instrument-cluster stats: a 2×2 grid of glowing readouts on a dark surface.
 * Every value carries the same RTT color language as the graph above it, so a
 * glance at the hues tells the story before the numbers do. The sample size
 * lives inside the LOSS cell as its denominator instead of wasting a line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsSheet(
    ip: String,
    average: Int?,
    jitterMs: Int?,
    sent: Int,
    lost: Int,
    lowest: Int?,
    highest: Int?,
) {
    val interFont = FontFamily(Font(Res.font.Inter_Regular))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            InlineStat(
                label = "PINGING",
                value = ip,
                valueColor = SettingsTextColor,
                fontFamily = interFont,
                glow = false,
            )
            InlineStat(
                label = "AVG",
                value = average?.let { "${it}ms" } ?: "—",
                valueColor = average?.let(::calcPingColor) ?: StatsDimColor,
                fontFamily = interFont,
            )
            InlineStat(
                label = "JIT",
                value = jitterMs?.let { "±$it" } ?: "—",
                valueColor = SettingsTextColor,
                fontFamily = interFont,
                glow = false,
            )
            run {
                val lossPercent = if (sent > 0) lost * 100f / sent else null
                InlineStat(
                    label = "LOSS",
                    value = lossPercent?.let { "${formatFloat1(it)}% ($lost/$sent)" } ?: "—",
                    // Loss only earns color once packets actually die.
                    valueColor = when {
                        lossPercent == null -> StatsDimColor
                        lossPercent <= 0.001f -> SettingsTextColor
                        else -> lossColor(lossPercent)
                    },
                    fontFamily = interFont,
                    glow = lossPercent != null && lossPercent > 0.001f,
                )
            }
            InlineStat(
                label = "RANGE",
                value = if (lowest != null && highest != null) "$lowest–${highest}ms" else "—",
                valueColor = SettingsTextColor,
                fontFamily = interFont,
                glow = false,
            )
        }
    }
}

/** One strip element: dim label sitting beside its glowing value. */
@Composable
private fun InlineStat(
    label: String,
    value: String,
    valueColor: Color,
    fontFamily: FontFamily,
    glow: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = 11.sp,
            letterSpacing = 1.2.sp,
            color = StatsLabelColor,
            fontFamily = fontFamily,
        )
        Text(
            text = value,
            modifier = Modifier.padding(start = 7.dp),
            style = TextStyle(
                color = valueColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                shadow = if (glow) Shadow(color = valueColor, blurRadius = 12f) else null,
            ),
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
 * Settings sheet with sliders bound directly to the panel's StateFlows.
 * All changes take effect on-the-fly.
 */
@Composable
private fun PingPanel.SettingsSheet(txtstyle: TextStyle) {
    val packetSizeVal by packetSize.collectAsState()
    val roofVal by roof.collectAsState()
    val angleOfAttackVal by angleOfAttack.collectAsState()
    val timeframeMsVal by timeframeMs.collectAsState()
    val canvasHeightFractionVal by canvasHeightFraction.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(top = 16.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Settings — $ip", color = StatsLabelColor)
        Text(
            text = "(long-press graph to reset settings)",
            color = StatsSubColor,
            style = txtstyle.copy(fontSize = 11.sp, color = StatsSubColor)
        )

        // --- Packet size (ICMP payload bytes) — live-applied to the next probe. ---
        SliderBlock(
            label = "Packet size: ${packetSizeVal} bytes",
            value = packetSizeVal.toFloat(),
            range = 16f..480f,
            steps = 28, // 16-byte steps
            onValueChange = { packetSize.value = it.toInt() },
            txtstyle = txtstyle
        )

        // --- Angle of attack (0 = linear 1:1, higher = more emphasis on low pings) ---
        SliderBlock(
            label = if (angleOfAttackVal <= 0.01f) "Angle of attack: linear (1:1)"
                    else "Angle of attack: ${formatFloat1(angleOfAttackVal)}",
            value = angleOfAttackVal,
            range = 0f..20f,
            steps = 40,
            onValueChange = { angleOfAttack.value = it },
            txtstyle = txtstyle
        )

        // --- Canvas height (fraction of window height) ---
        SliderBlock(
            label = "Canvas height: ${(canvasHeightFractionVal * 100).roundToInt()}%",
            value = canvasHeightFractionVal,
            range = 0.10f..0.45f,
            steps = 34,
            onValueChange = { canvasHeightFraction.value = it },
            txtstyle = txtstyle
        )

        // --- Timeframe (history duration) ---
        SliderBlock(
            label = "Timeframe: ${formatTimeframe(timeframeMsVal)}",
            value = timeframeMsVal.toFloat(),
            range = 1_000f..30_000f, // 1s .. 30s
            steps = 28,              // 1s steps
            onValueChange = { timeframeMs.value = it.toLong() },
            txtstyle = txtstyle
        )

        // --- Max displayed ping (roof) — capped at 2s since RTT beyond that is unrealistic. ---
        SliderBlock(
            label = "Maximum Possible Value: ${roofVal} ms",
            value = roofVal.toFloat(),
            range = 100f..2000f,
            steps = 18, // 100ms steps
            onValueChange = { roof.value = it.toInt() },
            txtstyle = txtstyle
        )
    }
}

@Composable
private fun SliderBlock(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    txtstyle: TextStyle,
) {
    Column(
        modifier = Modifier.fillMaxWidth(0.9f).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(text = label, style = txtstyle)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Paletting.A_MAIN_COLOR,
                activeTrackColor = Paletting.A_MAIN_COLOR,
                inactiveTrackColor = Paletting.A_MAIN_COLOR.copy(alpha = 0.25f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth()
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
 * Uses the formula: f * (1 - 2^(-x * zoomFactor / f))
 *
 * When [zoomFactor] is 0, the mapping is exactly linear (1:1) — the graph
 * shows y-proportional-to-x. Higher zoom factors push low pings further up,
 * which is useful for gamers who care most about small RTT differences.
 */
private fun exponentialize(x: Float, f: Float, zoomFactor: Float): Double {
    if (zoomFactor <= 0.001f) {
        // Pure linear mapping.
        return x.toDouble().coerceIn(0.0, f.toDouble())
    }
    if (x == f) return f.toDouble()
    return f.toDouble() * (1.0 - 2.0.pow((-x.toDouble() * zoomFactor / f.toDouble())))
}

/** Calculates a ping height on the current panel based on its value. */
private fun calculatePingY(ping: Int, panelHeight: Float, pingMaxVal: Float, zoomFactor: Float): Float {
    return (exponentialize(ping.toFloat(), pingMaxVal, zoomFactor) * (panelHeight.toDouble() / pingMaxVal)).toFloat()
}

/** The app-wide RTT color scale lives in the theme; the graph just speaks it. */
private fun calcPingColor(ping: Int): Color = pingColor(ping)

