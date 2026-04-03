package com.yuroyami.pingy.utils

import cocoapods.SPLPing.SPLPing
import cocoapods.SPLPing.SPLPingConfiguration
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

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