package com.yuroyami.pingy.ui.main.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import com.yuroyami.pingy.logic.PingPanel
import com.yuroyami.pingy.theme.Paletting
import com.yuroyami.pingy.ui.main.LocalPanelBackground
import org.jetbrains.compose.resources.Font
import pingy.shared.generated.resources.Inter_Regular
import pingy.shared.generated.resources.Res
import kotlin.math.pow
import kotlin.math.roundToInt


@Composable
fun PingPanel.PingGraphView(modifier: Modifier = Modifier) {
    val bg = LocalPanelBackground.current
    val textMeasurer = rememberTextMeasurer()
    val windowInfo = LocalWindowInfo.current
    val hDP by derivedStateOf { windowInfo.containerDpSize.height }

    val pings = pings.value
    val version by pingVersion.collectAsState()
    val expanded by expanded.collectAsState()
    val showSettings by showSettings.collectAsState()
    val pingsSentVal by pingsSent.collectAsState()
    val pingsLostVal by pingsLost.collectAsState()
    val lowestPingVal by lowestPing.collectAsState()
    val highestPingVal by highestPing.collectAsState()
    val widthetteVal by widthette.collectAsState()
    val roofVal by roof.collectAsState()
    val angleOfAttackVal by angleOfAttack.collectAsState()
    val landMarksVal by landMarks.collectAsState()
    val intervalVal by interval.collectAsState()

    val txtstyle = TextStyle(
        color = Color.DarkGray,
        fontSize = 14.sp,
        fontFamily = FontFamily(Font(Res.font.Inter_Regular)),
        shadow = Shadow(color = Color.LightGray, blurRadius = 3f)
    )

    Box(
        modifier = modifier.fillMaxWidth().padding(12.dp).wrapContentHeight(),
        contentAlignment = Alignment.TopCenter
    ) {

        AnimatedVisibility(
            modifier = modifier.fillMaxWidth(),
            visible = expanded,
            enter = slideInVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height((hDP / 5) - 12.dp))

                Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Card(
                        modifier = modifier
                            .fillMaxWidth(0.8f)
                            .wrapContentHeight(),
                        colors = CardDefaults.cardColors(),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            // Settings toggle button in top-right corner
                            IconButton(
                                modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
                                onClick = { this@PingGraphView.showSettings.value = !showSettings }
                            ) {
                                Icon(
                                    imageVector = if (showSettings) Icons.AutoMirrored.Filled.ShowChart else Icons.Filled.Settings,
                                    contentDescription = if (showSettings) "Show stats" else "Show settings",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            AnimatedContent(targetState = showSettings) { settings ->
                                if (!settings) {
                                    // Stats view
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp)
                                            .padding(top = 20.dp, bottom = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(text = "Pinging $ip", color = Color.Gray)
                                        // Use version to trigger recomposition when pings change
                                        val lastPing = remember(version) { pings.lastOrNull() }
                                        Text(text = "Current Ping: ${lastPing?.value ?: "No pings"} ", style = txtstyle)
                                        Text(text = "Pings sent: $pingsSentVal", style = txtstyle)

                                        val pingsLostPercentage = if (pingsSentVal > 0) {
                                            pingsLostVal * 100 / pingsSentVal
                                        } else 0
                                        Text(
                                            text = "Pings lost: $pingsLostVal (${pingsLostPercentage}%)",
                                            style = txtstyle
                                        )

                                        Text(
                                            text = "Min-Max: ${lowestPingVal ?: "0"} - ${highestPingVal ?: "0"}",
                                            style = txtstyle
                                        )
                                    }
                                } else {
                                    // Settings view
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp)
                                            .padding(top = 20.dp, bottom = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(text = "Settings — $ip", color = Color.Gray)

                                        // Interval slider
                                        Text(text = "Interval: ${intervalVal}ms", style = txtstyle)
                                        Slider(
                                            value = intervalVal.toFloat(),
                                            onValueChange = { interval.value = it.toLong() },
                                            valueRange = 50f..2000f,
                                            steps = 38, // 50ms steps
                                            colors = SliderDefaults.colors(
                                                thumbColor = Paletting.SGN,
                                                activeTrackColor = Paletting.SGN,
                                            ),
                                            modifier = Modifier.fillMaxWidth(0.9f)
                                        )

                                        // Roof (max displayed ping) slider
                                        Text(text = "Max display: ${roofVal}ms", style = txtstyle)
                                        Slider(
                                            value = roofVal.toFloat(),
                                            onValueChange = { roof.value = it.toInt() },
                                            valueRange = 100f..5000f,
                                            steps = 48,
                                            colors = SliderDefaults.colors(
                                                thumbColor = Paletting.A_LIGHT_COLOR,
                                                activeTrackColor = Paletting.A_LIGHT_COLOR,
                                            ),
                                            modifier = Modifier.fillMaxWidth(0.9f)
                                        )

                                        // Line width slider
                                        Text(text = "Line width: ${widthetteVal}px", style = txtstyle)
                                        Slider(
                                            value = widthetteVal.toFloat(),
                                            onValueChange = { widthette.value = it.toInt() },
                                            valueRange = 1f..10f,
                                            steps = 8,
                                            colors = SliderDefaults.colors(
                                                thumbColor = Paletting.A_MAIN_COLOR,
                                                activeTrackColor = Paletting.A_MAIN_COLOR,
                                            ),
                                            modifier = Modifier.fillMaxWidth(0.9f)
                                        )

                                        // Zoom factor slider
                                        Text(text = "Zoom: ${angleOfAttackVal}", style = txtstyle)
                                        Slider(
                                            value = angleOfAttackVal,
                                            onValueChange = { angleOfAttack.value = it },
                                            valueRange = 1f..20f,
                                            steps = 18,
                                            colors = SliderDefaults.colors(
                                                thumbColor = Paletting.SGN2,
                                                activeTrackColor = Paletting.SGN2,
                                            ),
                                            modifier = Modifier.fillMaxWidth(0.9f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(hDP / 5)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, color = Color.White, RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = Paletting.SGN)
                ) {
                    this@PingGraphView.expanded.value = !expanded
                }
        ) {

            drawImage(image = bg, dstSize = IntSize(size.width.toInt(), size.height.toInt()))

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

            /* Draw pings directly from the ring buffer — use version to trigger redraws */
            @Suppress("UNUSED_VARIABLE") val v = version
            pingStock.value = (size.width / widthetteVal).roundToInt()
            val showablePings = minOf(pingStock.value, pings.size)

            if (showablePings > 0) {
                val startIdx = pings.size - showablePings
                for (i in 0 until showablePings) {
                    try {
                        val x = size.width - (showablePings * widthetteVal) + (widthetteVal * i)
                        val p = pings[startIdx + i]
                        val y = calculatePingY(p.value ?: 0, size.height, roofVal.toFloat(), angleOfAttackVal)

                        drawLine(
                            end = Offset(x, size.height - y),
                            color = calcPingColor(p.value ?: 0),
                            strokeWidth = widthetteVal.toFloat(),
                            start = Offset(x, size.height),
                        )
                    } catch (_: IndexOutOfBoundsException) {
                        continue
                    }
                }
            }

            for (y in landMarksVal) {
                val h = calculatePingY(y.toInt(), size.height, roofVal.toFloat(), angleOfAttackVal)
                drawText(
                    textMeasurer = textMeasurer,
                    text = "${y.toInt()}",
                    topLeft = Offset(x = 20f, y = size.height - h - 4f),
                    style = TextStyle(
                        fontSize = 7.sp,
                        color = Color(200, 200, 220, 160)
                    )
                )
            }
        }

    }
}

/** Exponential scaling to emphasize low ping values in the graph.
 * Uses the formula: f * (1 - 2^(-x * zoomFactor / f))
 * @param x value to exponentialize.
 * @param f exponentialization factor (the asymptotic ceiling).
 * @param zoomFactor how aggressively to zoom on small values. */
private fun exponentialize(x: Float, f: Float, zoomFactor: Float): Double {
    if (x == f) return f.toDouble()
    return f.toDouble() * (1.0 - 2.0.pow((-x.toDouble() * zoomFactor / f.toDouble())))
}

/** Calculates a ping height on the current panel based on its value.
 * Calls [exponentialize] internally then linearizes it to the panel height. */
private fun calculatePingY(ping: Int, panelHeight: Float, pingMaxVal: Float, zoomFactor: Float): Float {
    return (exponentialize(ping.toFloat(), pingMaxVal, zoomFactor) * (panelHeight.toDouble() / pingMaxVal)).toFloat()
}

/** Calculates a ping's color based on its value using linear interpolation across tiers. */
private fun calcPingColor(ping: Int): Color {
    return when (ping) {
        in (0..20) -> Color(0, ping * 255 / 20, 255)
        in (21..50) -> Color(0, 255, 255 - ((ping - 20) * 155 / 30))
        in (51..100) -> Color(0, 255, (100 - (ping - 50) * 100 / 50))
        in (101..200) -> Color((ping - 100) * 255 / 100, 255 - ((ping - 100) * 100 / 100), 0)
        in (201..500) -> Color(255, 150 - ((ping - 200) * 150 / 300), 0)
        in (501..999) -> Color((255 - (ping - 500) * 200 / 500), 0, ((ping - 500) * 50 / 500))
        else -> Color(55, 0, 50)
    }
}
