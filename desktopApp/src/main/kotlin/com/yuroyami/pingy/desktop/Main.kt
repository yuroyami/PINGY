package com.yuroyami.pingy.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.yuroyami.pingy.PingyLifecycle
import com.yuroyami.pingy.ui.adam.AdamScreenUI

/**
 * Desktop shell.
 *
 * The UI is shared with mobile, which is the point, but a desktop window still
 * has conventions a touch layout does not supply on its own: a sensible minimum
 * size so the cockpit cannot be dragged into uselessness, a close that flushes
 * pending work rather than losing it, and the standard quit shortcut.
 */
fun main() = application {
    val windowState = rememberWindowState(
        width = 560.dp,
        height = 940.dp,
    )

    Window(
        onCloseRequest = {
            // The process is about to end; a debounced save that has not fired
            // yet would simply be lost.
            PingyLifecycle.flushPendingWrites()
            exitApplication()
        },
        title = "Pingy",
        state = windowState,
        onKeyEvent = { event ->
            val quit = event.type == KeyEventType.KeyDown &&
                event.key == Key.Q &&
                (event.isMetaPressed || event.isCtrlPressed)
            if (quit) {
                PingyLifecycle.flushPendingWrites()
                exitApplication()
                true
            } else {
                false
            }
        },
    ) {
        // Below roughly this width the two-column grid and the settings sheet
        // stop being usable. The adaptive grid handles everything above it.
        window.minimumSize = java.awt.Dimension(420, 520)
        AdamScreenUI()
    }
}
