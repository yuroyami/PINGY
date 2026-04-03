package com.yuroyami.pingy

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import com.yuroyami.pingy.logic.PingPanel
import com.yuroyami.pingy.ui.Screen

class PingyViewmodel: ViewModel() {

    val backstack = mutableStateListOf<Screen>(Screen.Main)

    /** Defines the operating PingGraph [com.yuroyami.pingy.logic.PingPanel]s but in a mutable state (so it can be observed) */
    val panels = mutableStateListOf<PingPanel>()

}