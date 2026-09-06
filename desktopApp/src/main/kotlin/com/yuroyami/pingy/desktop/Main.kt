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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** Longest a quit will wait for the preferences write before giving up on it. */
private const val QUIT_FLUSH_TIMEOUT_MS = 3_000L

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

    // Wait for the write before the process goes away. The old code launched
    // it and exited immediately, so the very flush meant to protect a recent
    // edit was the one most likely to lose it. Bounded, because a stuck disk
    // must not turn quitting into hanging.
    val quit: () -> Unit = {
        runBlocking { withTimeoutOrNull(QUIT_FLUSH_TIMEOUT_MS) { PingyLifecycle.flushPendingWrites() } }
        exitApplication()
    }

    Window(
        onCloseRequest = quit,
        title = "Pingy",
        state = windowState,
        onKeyEvent = { event ->
            val isQuit = event.type == KeyEventType.KeyDown &&
                event.key == Key.Q &&
                (event.isMetaPressed || event.isCtrlPressed)
            if (isQuit) {
                quit()
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
