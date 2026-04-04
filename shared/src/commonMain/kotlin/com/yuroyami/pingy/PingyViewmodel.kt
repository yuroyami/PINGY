package com.yuroyami.pingy

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import com.yuroyami.pingy.logic.PingPanel
import com.yuroyami.pingy.ui.Screen

class PingyViewmodel: ViewModel() {

    val backstack = mutableStateListOf<Screen>(Screen.Main)

    /** Operating [PingPanel]s in observable mutable state */
    val panels = mutableStateListOf<PingPanel>()

    override fun onCleared() {
        super.onCleared()
        panels.forEach { it.close() }
        panels.clear()
    }
}
