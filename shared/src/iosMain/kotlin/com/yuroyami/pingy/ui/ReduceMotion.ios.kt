package com.yuroyami.pingy.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

/** iOS: Settings, Accessibility, Motion, Reduce Motion. */
@Composable
actual fun reduceMotion(): Boolean = remember { UIAccessibilityIsReduceMotionEnabled() }
