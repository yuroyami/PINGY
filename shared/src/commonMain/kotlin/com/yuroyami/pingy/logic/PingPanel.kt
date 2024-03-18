package com.yuroyami.pingy.logic

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.yuroyami.pingy.ui.viewmodel
import com.yuroyami.pingy.utils.generateTimestampMillis
import com.yuroyami.pingy.utils.loggy
import com.yuroyami.pingy.utils.pingIcmp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** Wrapper Model class for a single Ping graph panel and its current parameters */
data class PingPanel(
    val ip: String /* Address to ping */
) {

    /** The whole collection of pings for this panel */
    val pings = mutableStateListOf<Ping>()

    /** Pinging Parameters */
    private var isPinging = mutableStateOf(true) /* Defines whether this panel is showing and doing pings or not */
    private var packetsize = mutableStateOf(32) /* Packet size for pinging (Minimum is 32, default is 64) */
    private var interval = mutableStateOf(50L) /* Interval of pinging, in milliseconds, default is 50ms */

    /** UI-related parameters */
    var widthette = mutableStateOf(3) /* Ping line width in pixels, default is 4 */
    var roof = mutableStateOf(1000) /* The highest ping that the panel shows */
    var angleOfAttack = mutableStateOf(8.0f) /* How the panel zooms on smaller amounts, exponentially */
    val pingStock = mutableStateOf(200)
    var landMarks = mutableStateOf(listOf(25f, 50f, 100f, 200f, 500f)) /* Line marks on the panel */
    var expanded = mutableStateOf(true) /* Whether the panel is showing info */

    /** For Statistics */
    var pingsSent = mutableStateOf(0) /* Overall sent pings */
    var pingsLost = mutableStateOf(0) /* Lost pings */
    var lowestPing = mutableStateOf<Int?>(0) /* Lowest recorded ping */
    var highestPing = mutableStateOf<Int?>(0) /* Highest recorded ping */

    init {
        viewmodel.ioScope.launch {
            launch { ping() }
        }
    }

    /**
     * Perform ping operations and emit Ping objects to the shared flow.
     */
    suspend fun ping() {
        while (true) {
            if (isPinging.value) {
                try {
                    val p = withTimeoutOrNull(timeMillis = interval.value * 2) {
                        pingIcmp(host = ip, packetSize = packetsize.value)?.roundToInt()
                    }

                    val ping = Ping(
                        value = p,
                        timestamp = generateTimestampMillis()
                    )

                    pings.add(ping)

                    pingsSent.value++
                    if (ping.value == null || ping.value < 0) pingsLost.value++
                } catch (e: Exception) {
                    loggy(e.stackTraceToString())
                }
            }
            delay(50)
        }
    }
}
