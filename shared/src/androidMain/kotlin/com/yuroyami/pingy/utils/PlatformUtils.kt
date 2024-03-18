package com.yuroyami.pingy.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.io.IOException

actual suspend fun pingIcmp(host: String, ttl: Int, packetSize: Int): Double? {
    return try {
        val pingCommand = "/system/bin/ping -c 1 -w 1 -t $ttl -s $packetSize $host"
        Runtime.getRuntime().exec(pingCommand).inputStream.use { inputStream ->
            val output = inputStream.reader().readText()
            when {
                output.contains("100% packet loss") -> null
                else -> output.substringAfter("time=").substringBefore(" ms").trim().toDouble()
            }
        }
    } catch (e: IOException) {
        loggy(e.stackTraceToString())
        null
    }
}

actual fun generateTimestampMillis() = System.currentTimeMillis()

@Composable
actual fun getScreenSizeInfo(): ScreenSizeInfo {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val hDp = config.screenHeightDp.dp
    val wDp = config.screenWidthDp.dp

    return remember(density, config) {
        ScreenSizeInfo(
            hPX = with(density) { hDp.roundToPx() },
            wPX = with(density) { wDp.roundToPx() },
            hDP = hDp,
            wDP = wDp
        )
    }
}