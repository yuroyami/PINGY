package app.logic

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import app.logic.PingyUtils.minPing
import java.lang.Thread.sleep

/** Wrapper Model class for a single Ping graph panel and its current parameters */
class Panel(
    val ip: String, /* Address to ping */
             val h: Float, /* Screen height, in pixels */
             val w: Float, /* Scree, width, in pixels */) {

    /** List of all recent pings */
    val pings = mutableStateListOf<Int>()

    /** Last Received Ping */
    var lastping = mutableStateOf(0)

    /** Last Received Ping time */
    var lastpingms = System.currentTimeMillis()

    /** Pinging Parameters */
    var packetsize = 32 /* Packet size for the pinging (Minimum is 32, default is 64) */
    var interval = 50L /* Interval of pinging, in milliseconds, it is by default 50ms */
    var mode = 0 /* Mode of pinging */
    var isPinging = true /* Defines whether this panel is showing and doing pings or not */

    /** UI-related parameters */
    var pingWidth = mutableStateOf(3) /* Ping line width in pixels, default is 4 */
    var pingLimit = mutableStateOf(1000) /* The highest ping that the panel shows */
    var angleOfAttack = mutableStateOf(8.0f) /* How the panel zooms on smaller amounts, exponentially */
    var pingStock = mutableStateOf(if (h > w) h else w) /* Max pings that the panel can remember */
    var landMarks = mutableStateListOf(25f, 50f, 100f, 200f, 500f) /* Line marks on the panel */
    var expanded = mutableStateOf(true) /* Whether the panel is showing info */
    var panelHeight = mutableStateOf(h/5f) /* panel height, in px */

    /** For Statistics */
    var pingsSent = mutableStateOf(0) /* Overall sent pings */
    var pingsLost = mutableStateOf(0) /* Lost pings */
    var lowestPing = mutableStateOf(0) /* Lowest recorded ping */
    var highestPing = mutableStateOf(0) /* Highest recorded ping */

    init {
        /** Launching two threads for each panel is better than using coroutines, performance-wise */
        Thread {
            while (true) {
                if (isPinging) {
                    try {
                        val p = PingyUtils.pingIcmp(ip, packetsize).toInt()
                        lastpingms = System.currentTimeMillis()
                        lastping.value = p
                        pingsSent.value += 1
                        if (p < 0) pingsLost.value += 1;
                        lowestPing.value = pings.minPing()
                        highestPing.value = pings.max()
                    } catch (_: Exception) {
                        sleep(50)
                    }
                }
            }
        }.start()

        Thread {
            while (true) {
                if (isPinging) {
                    val p = if (System.currentTimeMillis() - lastpingms > 1000) {
                        -1
                    } else {
                        lastping.value
                    }
                    pings.add(p)
                    if (pings.size > pingStock.value) {
                        pings.removeFirst()
                    }
                    sleep(interval)
                }
            }
        }.start()
    }
}