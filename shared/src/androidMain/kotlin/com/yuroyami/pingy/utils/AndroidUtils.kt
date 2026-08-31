package com.yuroyami.pingy.utils

import android.graphics.Color
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * Apply the PINGY-specific window chrome: edge-to-edge layout (content drawn
 * under the status and navigation bars) and display-cutout tolerance on P+.
 *
 * The screen is deliberately NOT forced awake. FLAG_KEEP_SCREEN_ON used to be
 * set unconditionally for the whole app lifetime, so leaving Pingy open drained
 * the battery and heated the device with no control and no disclosure. Keeping
 * the display on is now opt-in per session via [setKeepScreenOn].
 *
 * Must run before setContent in [ComponentActivity.onCreate] — later
 * insets already reference the decor state this sets up.
 */
fun ComponentActivity.applyActivityUiProperties() {
    // Edge-to-edge, with the bar style stated explicitly.
    //
    // The default `enableEdgeToEdge()` picks icon contrast from the SYSTEM
    // theme, not from what the app actually paints. Pingy's cockpit is always
    // near-black, so on a phone set to light mode the system drew black status
    // icons on a black background and the clock and signal bars vanished.
    // Forcing the dark-scrim style ties the icons to the app's own surface.
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

}

/**
 * Hold the screen awake only while the user has asked for it. Always paired
 * with a clear on pause, so backgrounding the app can never leave the flag set.
 */
fun ComponentActivity.setKeepScreenOn(enabled: Boolean) {
    if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}
