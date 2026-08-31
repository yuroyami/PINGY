package com.yuroyami.pingy.ui

import androidx.compose.runtime.Composable

/**
 * Whether the platform has been asked to reduce motion.
 *
 * Every visible graph animated continuously regardless of this setting, which
 * is exactly the kind of persistent movement the setting exists to stop. Motion
 * sensitivity is an accessibility need, not a preference.
 */
@Composable
expect fun reduceMotion(): Boolean
