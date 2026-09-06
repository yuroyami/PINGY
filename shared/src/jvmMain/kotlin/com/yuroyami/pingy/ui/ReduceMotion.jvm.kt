package com.yuroyami.pingy.ui

import androidx.compose.runtime.Composable

/**
 * Desktop exposes no portable reduced-motion signal, so there is nothing to
 * read here. The app's own switch, in About, is what desktop users get.
 */
@Composable
actual fun platformReduceMotion(): Boolean = false
