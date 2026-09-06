package com.yuroyami.pingy.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.yuroyami.pingy.ui.adam.LocalViewmodel

/**
 * Whether the platform has been asked to reduce motion.
 *
 * Read live: the value used to be captured once, so turning the setting on
 * while the app was open changed nothing until it was killed and reopened.
 */
@Composable
expect fun platformReduceMotion(): Boolean

/**
 * The platform setting, or the app's own switch.
 *
 * Desktop exposes no portable signal at all, which left those users with no way
 * to stop a canvas that moves continuously. Motion sensitivity is an
 * accessibility need, so the app carries its own answer as well.
 */
@Composable
fun reduceMotion(): Boolean {
    val override by LocalViewmodel.current.reduceMotionOverride.collectAsState()
    return override || platformReduceMotion()
}
