package app.ui.compose

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.utils.Panel
import kotlin.math.pow

object PingGraph {

    /** Our custom Composable function (which is the equivalent of a custom view in XML)
     * @param modifier Our Composable will follow this modifier's measurements to draw itself,
     * think of it as layout parameters when it comes to XML.
     * @param panel Represents the instance of the panel that this composable will draw for.
     * @param img An [ImageBitmap] for the background JPG/PNG for the panel */
    @Composable
    fun PingGraphView(modifier: Modifier, panel: Panel, img: ImageBitmap) {
        val pings = remember { panel.pings }

        /* Creating a canvas inside which we draw */
        Canvas(modifier = modifier.clip(RoundedCornerShape(16.dp))) {

            /* In the furthest background comes our panel background */
            drawImage(image = img, dstSize = IntSize(size.width.toInt(), size.height.toInt()))

            /* Drawing line indicators for each ping value milestone (20, 50, 100, etc) */
            for (y in panel.landMarks) {
                val h = calculatePingY(y.toInt(), size.height, panel.mxpc.value.toFloat(), panel.angleOfAttack.value)
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
            val displayablePings = (if (maxPings > pings.size) pings.size-1 else maxPings).toInt()

            if (pings.size != 0) {
                for (i in (0 until displayablePings)) {
                    try {
                        val x = size.width - (displayablePings * panel.pingWidth.value) + (panel.pingWidth.value * i) //quick maths xD
                        val p = pings[pings.size - displayablePings + i]
                        val y = calculatePingY(p, size.height, panel.mxpc.value.toFloat(), panel.angleOfAttack.value)

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
                    val h = calculatePingY(y.toInt(), size.height, panel.mxpc.value.toFloat(), panel.angleOfAttack.value)
                    it.nativeCanvas.drawText(
                        "${y.toInt()}", 20f, size.height - h - 4f,
                        textPaint)
                }
            }

            /* Drawing a rectangle with round corners. Must be the last to draw (so it's on top of others */
            drawRoundRect(
                color = Color.LightGray,
                style = Stroke(12f),
                size = Size(size.width-2f, size.height-2f),
                cornerRadius = CornerRadius(48f, 48f) //dp x 3 altho idk why
            )
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
        return  f.toDouble() * ( 1.0 - 2.0.pow((   -x.toDouble() * zoomFactor /  f.toDouble() )))
    }

    /** Calculates a ping height on the current panel based on its value
     * This calls [exponentialize] internally then linearalize it according to our panel height */
    fun calculatePingY(ping: Int, panelHeight: Float, pingMaxVal: Float, zoomFactor: Float): Float {
        return (exponentialize(ping.toFloat(), pingMaxVal, zoomFactor) * (panelHeight.toDouble()/ pingMaxVal)).toFloat()
    }

    //
    /** Calculates a ping's color based on its value. Only a bit of linear maths is involved. */
    fun calcPingColor(ping: Int): Color {
        return when (ping) {
            in (0..20) ->  Color(0,  ping * 255 / 20, 255)
            in (21..50) -> Color(0, 255, 255 - ((ping - 20) * 155 / 30))
            in (51..100) -> Color(0, 255, (100 - (ping- 50) * 100 / 50))
            in (101..200) -> Color((ping-100) * 255 / 100, 255 - ((ping - 100) * 100 / 100), 0)
            in (201..500) -> Color(255, 150 - ((ping - 200) * 150 / 300), 0)
            in (501..999) -> Color((255 - (ping - 500) * 200 / 500), 0, ((ping-500)*50/500))
            else -> Color(55, 0, 50)
        }
    }
}