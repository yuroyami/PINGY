package com.yuroyami.pingy.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import cocoapods.SPLPing.SPLPing
import cocoapods.SPLPing.SPLPingConfiguration
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import kotlin.math.roundToLong


@OptIn(ExperimentalForeignApi::class)
actual suspend fun pingIcmp(host: String, ttl: Int, packetSize: Int): Double? {
    val future = CompletableDeferred<Double>()
    SPLPing.pingOnce(
        host = host,
        configuration = SPLPingConfiguration(
            pingInterval = 1.0, timeoutInterval = 1.0, timeToLive = ttl.toLong(), payloadSize = packetSize.toULong()
        )
    ) {
        it?.let { response ->
            future.complete(
                (response.duration * 1000.0)
            )
        }
    }
    return withTimeoutOrNull(1000) { future.await() }
}

actual fun generateTimestampMillis(): Long {
    return (NSDate().timeIntervalSince1970 * 1000).roundToLong()
}

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