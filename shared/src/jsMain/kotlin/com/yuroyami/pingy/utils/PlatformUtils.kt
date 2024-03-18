package com.yuroyami.pingy.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import org.w3c.xhr.XMLHttpRequest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Date
import kotlin.math.roundToLong

actual suspend fun pingIcmp(host: String, ttl: Int, packetSize: Int): Double? {
    return try {
        val pingCommand = "ping -c 1 -w 1 -T $ttl -s $packetSize $host"
        val result = suspendCoroutine<String?> { continuation ->
            val xhr = XMLHttpRequest()
            xhr.open("GET", "/ping?cmd=$pingCommand")
            xhr.onreadystatechange = {
                if (xhr.readyState == 4.toShort()) {
                    if (xhr.status == 200.toShort()) {
                        continuation.resume(xhr.responseText)
                    } else {
                        continuation.resumeWithException(Exception("Failed to perform ping"))
                    }
                }
            }
            xhr.send()
        }
        if (result != null) {
            if (result.contains("100% packet loss")) {
                null
            } else {
                result.substringAfter("time=").substringBefore(" ms").trim().toDoubleOrNull()
            }
        } else {
            null
        }
    } catch (e: dynamic) {
        loggy(e.stackTrace?.toString() ?: e.toString())
        null
    }
}


actual fun generateTimestampMillis() = Date().getTime().roundToLong()

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