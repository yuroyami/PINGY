package com.yuroyami.pingy.utils

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

/**
 * Apply the PINGY-specific window chrome: edge-to-edge layout (content drawn
 * under the status and navigation bars), display-cutout tolerance on P+,
 * and FLAG_KEEP_SCREEN_ON so the live graph stays visible without the
 * screen timing out while the user is watching it.
 *
 * Must run before setContent in [ComponentActivity.onCreate] — later
 * insets already reference the decor state this sets up.
 */
fun ComponentActivity.applyActivityUiProperties() {
    // Edge-to-edge: tells the system to draw under the status/navigation
    // bars with transparent backgrounds and picks the right icon-contrast
    // for the current theme. Replaces the deprecated
    // `WindowCompat.setDecorFitsSystemWindows(window, false)` +
    // `window.statusBarColor = transparent` dance from before.
    enableEdgeToEdge()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}
