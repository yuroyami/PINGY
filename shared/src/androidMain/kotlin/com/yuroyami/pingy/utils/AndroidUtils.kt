package com.yuroyami.pingy.utils

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat

@Suppress("DEPRECATION")
fun ComponentActivity.applyActivityUiProperties() {
    window.attributes = window.attributes.apply {
        flags = flags and WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS.inv()
    }
    window.statusBarColor = Color.Transparent.toArgb()
    window.navigationBarColor = Color.Transparent.toArgb()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    /** Telling Android that it should keep the screen on */
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    WindowCompat.setDecorFitsSystemWindows(window, false)
}