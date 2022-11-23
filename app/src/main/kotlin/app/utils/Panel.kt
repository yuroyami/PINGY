package app.utils

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Wrapper Model class for a single Ping graph panel and its current parameters */
class Panel (var ip: String = "", scope: LifecycleCoroutineScope) {


    var packetsize: Int = 32 //Packet size for the pinging (Minimum is 32, default is 64)
    var interval: Long = 50L //Interval of pinging, in milliseconds, it is by default 50ms */
    var mode: Int = 0 //Mode of pinging

    /* UI-related */
    var pingWidth: Int = 4 //Ping line width in pixels, default is 4
    var pingLimit: Int = 1000 //The highest ping that the panel shows
    var angleOfAttack: Float = 8.0f //How the panel zooms on smaller amounts, expontentially
    var mxpc: Int = 1000 //Max pings that the panels can remember (defaulted to height or width of device
    val pings: SnapshotStateList<Int> = mutableStateListOf()//Pings that the panel holds (limited by mxpc var below)
    var landMarks: MutableList<Float> = mutableListOf(10f, 20f, 30f, 50f, 70f, 100f, 150f, 200f, 300f, 400f, 500f, 1000f)

    var isPinging = true //Defines whether this panel is showing and doing pings or not

    init {
        scope.launch(Dispatchers.IO) {
            while (true) {
                if (isPinging) {
                    try {
                        pings.add(PingyUtils.pingIcmp(ip, packetsize).toInt())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                delay(interval)
            }
        }
    }
}