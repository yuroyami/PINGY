package com.yuroyami.pingy

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import com.yuroyami.pingy.logic.PingPanel
import com.yuroyami.pingy.ui.Screen
import kotlinx.coroutines.flow.MutableStateFlow

/** The two artistic renderings of a ping graph. One dataset, two geometries. */
enum class GraphStyle {
    /** Discrete color-coded bars; each ping is its own rectangle. */
    PINGLETTES,

    /** One continuous ridge: diagonal slopes between ping points, color blending along the range. */
    MOUNTAIN_SLOPES,
}

class PingyViewmodel: ViewModel() {

    val backstack = mutableStateListOf<Screen>(Screen.Main)

    /** Operating [PingPanel]s in observable mutable state */
    val panels = mutableStateListOf<PingPanel>()

    /** App-wide graph rendering style, toggled from the top bar. */
    val graphStyle = MutableStateFlow(GraphStyle.PINGLETTES)

    override fun onCleared() {
        super.onCleared()
        panels.forEach { it.close() }
        panels.clear()
    }
}
