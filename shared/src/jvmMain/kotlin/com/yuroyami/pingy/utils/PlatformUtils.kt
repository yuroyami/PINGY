package com.yuroyami.pingy.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import java.io.IOException

actual suspend fun pingIcmp(host: String, ttl: Int, packetSize: Int): Double? {
    return try {
        val pingCommand = "/system/bin/ping -c 1 -w 1 -T $ttl -s $packetSize $host"
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