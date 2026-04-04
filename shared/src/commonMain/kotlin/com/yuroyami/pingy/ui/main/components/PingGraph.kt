package com.yuroyami.pingy.ui.main.components

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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.util.fastMapNotNull
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

    val pings = remember { pings }

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
            visible = remember { expanded }.value,
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
                        Column(
                            modifier = modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp)
                                .padding(top = 20.dp, bottom = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "Pinging $ip", color = Color.Gray)
                            Text(text = "Current Ping: ${pings.lastOrNull()?.value ?: "No pings"} ", style = txtstyle)
                            Text(text = "Pings sent: ${pingsSent.value}", style = txtstyle)

                            val pingsLostPercentage = if (remember { pingsSent }.value > 0) {
                                remember { pingsLost }.value * 100 / remember { pingsSent }.value
                            } else 0
                            Text(
                                text = "Pings lost: ${pingsLost.value} (${pingsLostPercentage}%)",
                                style = txtstyle
                            )

                            LaunchedEffect(pings.size) {
                                val fastMap = pings.fastMapNotNull { it.value }
                                lowestPing.value = fastMap.minOrNull()
                                highestPing.value = fastMap.maxOrNull()
                            }

                            Text(
                                text = "Min-Max: ${lowestPing.value ?: "0"} - ${highestPing.value ?: "0"}",
                                style = txtstyle
                            )
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
                    expanded.value = !expanded.value
                }
        ) {

            drawImage(image = bg, dstSize = IntSize(size.width.toInt(), size.height.toInt()))

            for (y in landMarks.value) {
                val h = calculatePingY(y.toInt(), size.height, roof.value.toFloat(), angleOfAttack.value)
                drawLine(
                    start = Offset(0f, size.height - h),
                    end = Offset(size.width, size.height - h),
                    strokeWidth = 1f,
                    color = Color.White,
                    alpha = 0.30f
                )
            }

            /* Draw pings directly from the real pings list */
            pingStock.value = (size.width / widthette.value).roundToInt()
            val showablePings = minOf(pingStock.value, pings.size)

            if (showablePings > 0) {
                for (i in 0 until showablePings) {
                    try {
                        val x = size.width - (showablePings * widthette.value) + (widthette.value * i)
                        val p = pings[pings.size - showablePings + i]
                        val y = calculatePingY(p.value ?: 0, size.height, roof.value.toFloat(), angleOfAttack.value)

                        drawLine(
                            end = Offset(x, size.height - y),
                            color = calcPingColor(p.value ?: 0),
                            strokeWidth = widthette.value.toFloat(),
                            start = Offset(x, size.height),
                        )
                    } catch (_: IndexOutOfBoundsException) {
                        continue
                    }
                }
            }

            for (y in landMarks.value) {
                val h = calculatePingY(y.toInt(), size.height, roof.value.toFloat(), angleOfAttack.value)
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
