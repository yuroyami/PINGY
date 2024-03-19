package com.yuroyami.pingy.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import java.io.IOException
import java.net.InetAddress


actual suspend fun pingIcmp(host: String, ttl: Int, packetSize: Int): Double? {
    return try {
        val pingCommand = "ping -c 1 -w 1 -s $packetSize $host"
        Runtime.getRuntime().exec(pingCommand).inputStream.use { inputStream ->
            val output = inputStream.reader().readText()
            if (output.isEmpty()) throw IOException()
            loggy("Runtime output: $output")
            when {
                output.contains("100% packet loss") -> null
                else -> output.substringAfter("time=").substringBefore(" ms").trim().toDouble()
            }
        }
    } catch (e: IOException) {
        loggy(e.stackTraceToString())
        val inet = InetAddress.getByName(host)
        val mono1 = System.nanoTime()
        val reachable = inet.isReachable(990)
        val mono2 = System.nanoTime()
        if (reachable) {
            return (mono2 - mono1).toDouble().div(1_000_000)
        }
        loggy("Not reachable")
        null
    } catch (e: Exception) {
        loggy(e.stackTraceToString())
        null
    }
}

actual fun generateTimestampMillis() = System.currentTimeMillis()


@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun getScreenSizeInfo(): ScreenSizeInfo {
    val density = LocalDensity.current
    val config = LocalWindowInfo.current.containerSize


    return remember(density, config) {
        ScreenSizeInfo(
            hPX = config.height,
            wPX = config.width,
            hDP = with(density) { config.height.toDp() },
            wDP = with(density) { config.width.toDp() }
        )
    }
}