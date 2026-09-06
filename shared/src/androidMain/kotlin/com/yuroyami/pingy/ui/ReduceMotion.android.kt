package com.yuroyami.pingy.ui

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android exposes this as the global animator duration scale. Zero means the
 * user turned animations off, in accessibility settings or developer options.
 */
private fun animationsOff(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
}.getOrDefault(false)

@Composable
actual fun platformReduceMotion(): Boolean {
    val context = LocalContext.current
    val state = remember(context) { mutableStateOf(animationsOff(context)) }
    // Observed rather than read once: changing the setting used to have no
    // effect until the process was killed and started again.
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                state.value = animationsOff(context)
            }
        }
        runCatching {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer,
            )
        }
        onDispose { runCatching { context.contentResolver.unregisterContentObserver(observer) } }
    }
    return state.value
}
