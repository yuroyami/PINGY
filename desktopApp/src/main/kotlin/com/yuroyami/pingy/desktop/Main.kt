package com.yuroyami.pingy.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.yuroyami.pingy.ui.adam.AdamScreenUI

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Pingy",
        state = rememberWindowState(width = 520.dp, height = 940.dp),
    ) {
        AdamScreenUI()
    }
}
