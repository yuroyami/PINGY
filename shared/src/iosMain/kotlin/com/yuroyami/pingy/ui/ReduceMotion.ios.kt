package com.yuroyami.pingy.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification

/** iOS: Settings, Accessibility, Motion, Reduce Motion. */
@Composable
actual fun platformReduceMotion(): Boolean {
    val state = remember { mutableStateOf(UIAccessibilityIsReduceMotionEnabled()) }
    // The system posts this when the switch is flipped, so the app follows it
    // without being restarted.
    DisposableEffect(Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIAccessibilityReduceMotionStatusDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> state.value = UIAccessibilityIsReduceMotionEnabled() }
        onDispose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
    }
    return state.value
}
