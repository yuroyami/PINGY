package app.ui

import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.R
import app.logic.Panel
import kotlin.math.pow

object PingGraph {

    /** Our custom Composable function (which is the equivalent of a custom view in XML)
     * @param modifier Our Composable will follow this modifier's measurements to draw itself,
     * think of it as layout parameters when it comes to XML.
     * @param panel Represents the instance of the panel that this composable will draw for.
     * @param img An [ImageBitmap] for the background JPG/PNG for the panel */
    @Composable
    fun PingGraphView(modifier: Modifier = Modifier, panel: Panel, img: ImageBitmap) {
        val pings = remember { panel.pings }
        val height = remember { panel.panelHeight }
        val expanded = remember { panel.expanded }

        /* Text Style */
        val txtstyle = TextStyle(
            color = Color.DarkGray,
            fontSize = 14.sp,
            fontFamily = FontFamily(Font(R.font.inter)),
            shadow = Shadow(color = Color.LightGray, blurRadius = 3f)
        )

        /* Creating a canvas inside which we draw */
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(12.dp)
                .wrapContentHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            AnimatedVisibility(
                modifier = modifier.fillMaxWidth(),
                visible = expanded.value,
                enter = slideInVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = modifier.fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.height((with(LocalDensity.current) { height.value.toDp() } - 12.dp)))

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
                                Text(text = "Pinging ${panel.ip}", color = Color.Gray)

                                val pingLast = remember { panel.lastping }
                                Text(text = "Current Ping: ${pingLast.value}", style = txtstyle)

                                val pingsSent = remember { panel.pingsSent }
                                Text(text = "Pings sent: ${pingsSent.value}", style = txtstyle)

                                val pingsLost = remember { panel.pingsLost }
                                val pingsLostPercentage = if (pingsSent.value > 0) {
                                    pingsLost.value * 100 / pingsSent.value
                                } else 0
                                Text(text = "Pings lost: ${pingsLost.value} (${pingsLostPercentage}%)",
                                    style = txtstyle)

                                val minPing = remember { panel.lowestPing }
                                val maxPing = remember { panel.highestPing }
                                Text(text = "Min-Max: ${minPing.value} - ${maxPing.value}",
                                    style = txtstyle)
                            }
                        }
                    }
                }
            }

            Canvas(
                modifier = modifier
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { height.value.toDp() })
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(color = Paletting.SGN)
                    ) {
                        panel.expanded.value = !panel.expanded.value
                    }
            ) {

                /* In the furthest background comes our panel background */
                drawImage(image = img, dstSize = IntSize(size.width.toInt(), size.height.toInt()))

                /* Drawing line indicators for each ping value milestone (20, 50, 100, etc) */
                for (y in panel.landMarks) {
                    val h = calculatePingY(y.toInt(), size.height, panel.pingLimit.value.toFloat(), panel.angleOfAttack.value)
                    drawLine(
                        start = Offset(0f, size.height - h),
                        end = Offset(size.width, size.height - h),
                        strokeWidth = 1f,
                        color = Color.White,
                        alpha = 0.30f
                    )
                }

                /* Drawing all pings */
                val maxPings = size.width / panel.pingWidth.value
                val displayablePings = (if (maxPings > pings.size) pings.size - 1 else maxPings).toInt()

                if (pings.size != 0) {
                    for (i in (0 until displayablePings)) {
                        try {
                            val x = size.width - (displayablePings * panel.pingWidth.value) + (panel.pingWidth.value * i) //quick maths xD
                            val p = pings[pings.size - displayablePings + i]
                            val y = calculatePingY(p, size.height, panel.pingLimit.value.toFloat(), panel.angleOfAttack.value)

                            drawLine(
                                end = Offset(x, size.height - y),
                                color = calcPingColor(p),
                                strokeWidth = panel.pingWidth.value.toFloat(),
                                start = Offset(x, size.height),
                            )
                        } catch (e: IndexOutOfBoundsException) {
                            e.printStackTrace()
                            continue
                        }
                    }
                }

                /* Drawing line indicator texts (must be declared here to be drawn above pings) */
                val textPaint = Paint().asFrameworkPaint().apply {
                    isAntiAlias = true
                    textSize = 7.sp.toPx()
                    color = Color(200, 200, 220, 160).toArgb()
                    typeface = Typeface.create("inter", Typeface.NORMAL)
                }

                drawIntoCanvas {
                    for (y in panel.landMarks) {
                        val h = calculatePingY(y.toInt(), size.height, panel.pingLimit.value.toFloat(), panel.angleOfAttack.value)
                        it.nativeCanvas.drawText(
                            "${y.toInt()}", 20f, size.height - h - 4f,
                            textPaint
                        )
                    }
                }

                /* Drawing a rectangle with round corners. Must be the last to draw (so it's on top of others */
                drawRoundRect(
                    color = Color.LightGray,
                    style = Stroke(12f),
                    size = Size(size.width - 2f, size.height - 2f),
                    cornerRadius = CornerRadius(48f, 48f) //dp x 3 altho idk why
                )
            }

        }
    }

    /** Our app focuses mostly on the smaller ping values while not giving much importance
     * to the higher values (especially beyond 200), so in this case, we wanna draw expontentially.
     * Which means, zooming on the smallest values taking up most of the graph Y axis.
     * To achieve this, we can use a mathematical power functional looking like this: a(1-2^(x/-(a/10)))
     * where a is basically the end goal of the expontential.
     * @param [x] value to expontentialize.
     * @param [f] expontentialization factor.
     * */
    fun exponentialize(x: Float, f: Float, zoomFactor: Float): Double {
        if (x == f) return f.toDouble()
        return f.toDouble() * (1.0 - 2.0.pow((-x.toDouble() * zoomFactor / f.toDouble())))
    }

    /** Calculates a ping height on the current panel based on its value
     * This calls [exponentialize] internally then linearalize it according to our panel height */
    fun calculatePingY(ping: Int, panelHeight: Float, pingMaxVal: Float, zoomFactor: Float): Float {
        return (exponentialize(ping.toFloat(), pingMaxVal, zoomFactor) * (panelHeight.toDouble() / pingMaxVal)).toFloat()
    }

    //
    /** Calculates a ping's color based on its value. Only a bit of linear maths is involved. */
    fun calcPingColor(ping: Int): Color {
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
}