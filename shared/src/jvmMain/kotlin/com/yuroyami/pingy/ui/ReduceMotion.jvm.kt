package com.yuroyami.pingy.ui

import androidx.compose.runtime.Composable

/**
 * Desktop exposes no portable reduced-motion signal, so animation stays on.
 * Wire this to the platform setting if one becomes available.
 */
@Composable
actual fun reduceMotion(): Boolean = false
