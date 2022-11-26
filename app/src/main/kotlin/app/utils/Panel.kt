package app.utils

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import java.lang.Thread.sleep

/** Wrapper Model class for a single Ping graph panel and its current parameters */
class Panel (var ip: String) {

    var packetsize = 32 //Packet size for the pinging (Minimum is 32, default is 64)
    var interval = 50L //Interval of pinging, in milliseconds, it is by default 50ms */
    var mode = 0 //Mode of pinging
    /* UI-related */
    var pingWidth = mutableStateOf(3) //Ping line width in pixels, default is 4
    var pingLimit = mutableStateOf(1000) //The highest ping that the panel shows
    var angleOfAttack = mutableStateOf(8.0f) //How the panel zooms on smaller amounts, expontentially
    var mxpc = mutableStateOf(1000) //Max pings that the panels can remember (defaulted to height or width of device
    val pings = mutableStateListOf<Int>()//Pings that the panel holds (limited by mxpc var below)
    var landMarks = mutableStateListOf(25f, 50f, 100f, 200f, 500f)

    var isPinging = true //Defines whether this panel is showing and doing pings or not

    init {
        Thread {
            while (true) {
                if (isPinging) {
                    try {
                        pings.add(PingyUtils.pingIcmp(ip, packetsize).toInt())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                sleep(interval)
            }
        }.start()
    }
}