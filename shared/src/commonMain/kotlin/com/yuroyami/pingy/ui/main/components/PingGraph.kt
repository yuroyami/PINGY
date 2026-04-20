package com.yuroyami.pingy.ui.main.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.pingy.logic.Ping
import com.yuroyami.pingy.logic.PingPanel
import com.yuroyami.pingy.theme.Paletting
import com.yuroyami.pingy.ui.main.LocalPanelBackground
import com.yuroyami.pingy.utils.PING_TIMEOUT_MS
import org.jetbrains.compose.resources.Font
import pingy.shared.generated.resources.Inter_Regular
import pingy.shared.generated.resources.Res
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Full graph panel view:
 *
 * - A canvas at the top drawing the ping history. Clicking the canvas switches
 *   the sheet below between **statistics** (default) and **settings** modes.
 * - A standalone minimize/expand [IconButton] overlaid on the canvas' top-right
 *   corner. It intercepts clicks in its own area and toggles the sheet's
 *   expanded/collapsed state, independently from the mode switch.
 * - A bottom sheet containing either stats or tuning sliders (packet size,
 *   angle of attack, canvas height, timeframe, roof) — all reactive via the
 *   panel's StateFlows, so changes take effect instantly.
 */
@Composable
fun PingPanel.PingGraphView(modifier: Modifier = Modifier) {
    val bg = LocalPanelBackground.current
    val textMeasurer = rememberTextMeasurer()
    val windowInfo = LocalWindowInfo.current
    val windowHeightDp by derivedStateOf { windowInfo.containerDpSize.height }

    val pings = this.pings
    val version by pingVersion.collectAsState()
    val expanded by expanded.collectAsState()
    val showSettings by showSettings.collectAsState()

    val pingsSentVal by pingsSent.collectAsState()
    val pingsLostVal by pingsLost.collectAsState()
    val lowestPingVal by lowestPing.collectAsState()
    val highestPingVal by highestPing.collectAsState()

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

    val txtstyle = TextStyle(
        color = Color.DarkGray,
        fontSize = 14.sp,
        fontFamily = FontFamily(Font(Res.font.Inter_Regular)),
        shadow = Shadow(color = Color.LightGray, blurRadius = 3f)
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .wrapContentHeight()
    ) {
        // Graph canvas + overlaid minimize toggle
        Box(modifier = Modifier.fillMaxWidth().height(canvasHeight)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, color = Color.White, RoundedCornerShape(16.dp))
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = Paletting.SGN),
                        onClick = {
                            // Click on the graph body switches mode between stats and settings.
                            this@PingGraphView.showSettings.value = !showSettings
                        },
                        onLongClick = {
                            // Long-press on the graph resets all tuning preferences to defaults.
                            resetPreferences()
                        }
                    )
            ) {
                drawImage(
                    image = bg,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                )

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
                @Suppress("UNUSED_VARIABLE") val t = frameTick.longValue

                // Collect the subset of pings within the user-chosen timeframe.
                // Reuses the outer `visibleBuf` to avoid a per-frame allocation.
                val thresholdMs = timeframeMsVal
                visibleBuf.clear()
                pings.fastForEachWithIndex { p, _ ->
                    if (p != null && p.timestamp.elapsedNow().inWholeMilliseconds <= thresholdMs) {
                        visibleBuf.add(p)
                    }
                }

                val canvasW = size.width
                val canvasH = size.height

                if (visibleBuf.isNotEmpty()) {
                    // Slot-based drawing. Each valid bar tiles against its
                    // predecessor when that predecessor was also valid — the
                    // width in time is (this_ping.ts - prev_ping.ts), which
                    // keeps a long-RTT bar visibly wider because it ate more
                    // real time before the next probe could land.
                    //
                    // Void (null-valued) pings DRAW NOTHING. A timeout has
                    // no duration to represent, and the slot before the next
                    // valid bar is rendered as literal background — the gap
                    // itself carries the "lost packet" signal at a glance.
                    // The next valid bar then falls back to its own RTT for
                    // width, so the timeline doesn't swallow the gap by
                    // widening the recovery bar.
                    val pxPerMs = canvasW / thresholdMs.toFloat()

                    var prevAgeMs = 0L
                    var lastAgeMs = 0L
                    // First iteration has no predecessor, treat as invalid
                    // so widthMs falls back to the ping's own RTT.
                    var prevWasInvalid = true

                    for (i in visibleBuf.indices) {
                        val ping = visibleBuf[i]
                        val ageMs = ping.timestamp.elapsedNow().inWholeMilliseconds
                        val previousAgeMs = prevAgeMs
                        val predecessorWasInvalid = prevWasInvalid
                        prevAgeMs = ageMs
                        lastAgeMs = ageMs

                        val v = ping.value
                        val isLost = v == null || v < 0
                        // Update for the next iteration BEFORE the skip so
                        // the next ping knows its predecessor was void.
                        prevWasInvalid = isLost
                        if (isLost) continue

                        val rightEdgePx = canvasW - ageMs * pxPerMs
                        if (rightEdgePx <= 0f) continue

                        val widthMs: Long = if (!predecessorWasInvalid) {
                            (previousAgeMs - ageMs).coerceAtLeast(1L)
                        } else {
                            // No valid predecessor → don't reach back over
                            // a gap, just paint this bar at its own duration.
                            v.toLong().coerceAtLeast(1L)
                        }
                        val widthPx = (widthMs.toFloat() * pxPerMs).coerceAtLeast(1f)
                        // No coerceAtLeast(0f) on the left edge: letting it
                        // go negative keeps the bar's full width intact as
                        // it exits the canvas. Compose clips off-canvas
                        // drawing for free, and the previous clamped version
                        // made the oldest visible bar "ghost" into a
                        // shrinking sliver against the left border — a bar
                        // with a defined value shouldn't visually degrade
                        // just because it reached the edge.
                        val leftEdgePx = rightEdgePx - widthPx

                        val y = calculatePingY(
                            v,
                            canvasH,
                            roofVal.toFloat(),
                            angleOfAttackVal
                        )
                        drawRect(
                            color = calcPingColor(v),
                            topLeft = Offset(leftEdgePx, canvasH - y),
                            size = Size(widthPx, y)
                        )
                    }

                    // Chameleon — the in-flight slot between the newest
                    // buffer entry and the present moment. Colorizes in
                    // real time with elapsed in-flight duration so the
                    // user watches the probe "ripen" on the way to its
                    // resolved RTT. No alpha fade: when the result lands
                    // the chameleon is replaced instantly by either the
                    // new valid bar (matching color, continuous read) or
                    // by nothing at all if the probe was deemed void —
                    // the visual must reflect actuality the moment it's
                    // known, no lingering ghost.
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
                            drawRect(
                                color = calcPingColor(colorMs),
                                topLeft = Offset(leftEdgePx, canvasH - y),
                                size = Size(drawW, y)
                            )
                        }
                    }
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
            }

            // Standalone minimize toggle: small button in the top-right of the graph.
            // Sits above the canvas so it intercepts clicks in its small area
            // before the canvas' clickable can fire.
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

        // Sheet below: statistics or settings, depending on `showSettings`.
        AnimatedVisibility(
            visible = expanded,
            enter = slideInVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Small indicator of the current mode (not clickable — mode switching
                        // happens exclusively via the graph canvas click).
                        Icon(
                            imageVector = if (showSettings) Icons.Filled.Settings
                                          else Icons.AutoMirrored.Filled.ShowChart,
                            contentDescription = null,
                            tint = Color.Gray.copy(alpha = 0.7f),
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
                                    lastPing = remember(version) { pings.last() },
                                    pingsSent = pingsSentVal,
                                    pingsLost = pingsLostVal,
                                    lowestPing = lowestPingVal,
                                    highestPing = highestPingVal,
                                    txtstyle = txtstyle
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsSheet(
    ip: String,
    lastPing: Ping?,
    pingsSent: Int,
    pingsLost: Int,
    lowestPing: Int?,
    highestPing: Int?,
    txtstyle: TextStyle,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(top = 16.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Pinging $ip", color = Color.Gray)
        Text(
            text = "Current Ping: ${lastPing?.value ?: "No pings"}",
            style = txtstyle
        )
        Text(text = "Pings sent: $pingsSent", style = txtstyle)

        val lostPct = if (pingsSent > 0) pingsLost * 100 / pingsSent else 0
        Text(text = "Pings lost: $pingsLost (${lostPct}%)", style = txtstyle)

        Text(
            text = "Min-Max: ${lowestPing ?: "0"} - ${highestPing ?: "0"} ms",
            style = txtstyle
        )
    }
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
        Text(text = "Settings — $ip", color = Color.Gray)
        Text(
            text = "(long-press graph to reset settings)",
            color = Color.Gray.copy(alpha = 0.7f),
            style = txtstyle.copy(fontSize = 11.sp)
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

/**
 * Maps a ping RTT (ms) to a color that carries meaning at a glance.
 *
 * Anchor points (all interpolated in HSV, log-scale on ping):
 *
 *     ≤ 10ms → deep ocean blue    (baseline — relaxing, "instant")
 *     ~40ms  → bright teal        (healthy)
 *     ~200ms → yellow-green       (warning)
 *     ~650ms → orange             (bad)
 *     ~1s    → magenta            (terrible)
 *     ≥ 2s   → deep purple        (catastrophic)
 *
 * Two deliberate shaping choices:
 *
 *  1. Log-scale ping → hue. Perceived latency quality is roughly logarithmic
 *     (10→40ms feels like 100→400ms), so colors are positioned on log(ping).
 *     The hue sweep is piecewise so most of the arc sits in the "good" zone
 *     (small RTT shifts still read as color shifts) and passes quickly through
 *     yellow — yellow shouldn't be the dominant color of a mediocre link.
 *
 *  2. V (brightness) curve with a peak around yellow/orange. A flat V makes
 *     yellow render as olive/brown, because the eye expects yellow to be the
 *     brightest color on the wheel. So V rises into the warning zone and
 *     falls off toward both ends: ocean blue stays deep, purple stays ominous.
 *     This is why the earlier constant-L OkLCh version made 40ms look muddy.
 */
private fun calcPingColor(ping: Int): Color {
    val clamped = ping.coerceAtLeast(0).coerceAtMost(2000)
    val t = if (clamped <= 1) 0.0
            else (log10(clamped.toDouble()) / log10(2000.0)).coerceIn(0.0, 1.0)

    // Piecewise hue (HSV degrees). 0°=red, 60°=yellow, 120°=green, 180°=cyan,
    // 240°=blue, 300°=magenta. 225° is ocean blue; the final −60° wraps to 300°.
    val hueDeg = piecewiseLinear(
        t,
        0.00, 225.0,   // ocean blue
        0.50, 165.0,   // teal
        0.70,  95.0,   // yellow-green
        0.85,  25.0,   // orange
        1.00, -60.0,   // deep purple
    ).let { ((it % 360.0) + 360.0) % 360.0 }

    // Brightness: ramp up into the yellow/orange peak, taper down toward purple.
    val v = if (t <= 0.70) 0.65 + (t / 0.70) * 0.35
            else           1.00 - ((t - 0.70) / 0.30) * 0.50

    return hsvColor(hueDeg, 0.88, v)
}

/** Piecewise-linear interpolation through (x, y) anchor pairs sorted by x. */
private fun piecewiseLinear(x: Double, vararg xy: Double): Double {
    val n = xy.size / 2
    if (x <= xy[0]) return xy[1]
    if (x >= xy[(n - 1) * 2]) return xy[(n - 1) * 2 + 1]
    for (i in 0 until n - 1) {
        val x0 = xy[i * 2]; val y0 = xy[i * 2 + 1]
        val x1 = xy[(i + 1) * 2]; val y1 = xy[(i + 1) * 2 + 1]
        if (x in x0..x1) {
            val f = (x - x0) / (x1 - x0)
            return y0 + f * (y1 - y0)
        }
    }
    return xy[(n - 1) * 2 + 1]
}

/** HSV → sRGB. h in degrees [0,360), s/v in [0,1]. */
private fun hsvColor(h: Double, s: Double, v: Double): Color {
    val c = v * s
    val hh = (h / 60.0)
    val x = c * (1.0 - abs((hh % 2.0) - 1.0))
    val m = v - c
    val (r0, g0, b0) = when {
        hh < 1.0 -> Triple(c, x, 0.0)
        hh < 2.0 -> Triple(x, c, 0.0)
        hh < 3.0 -> Triple(0.0, c, x)
        hh < 4.0 -> Triple(0.0, x, c)
        hh < 5.0 -> Triple(x, 0.0, c)
        else     -> Triple(c, 0.0, x)
    }
    return Color(
        red = (r0 + m).toFloat().coerceIn(0f, 1f),
        green = (g0 + m).toFloat().coerceIn(0f, 1f),
        blue = (b0 + m).toFloat().coerceIn(0f, 1f),
    )
}
