package com.yuroyami.pingy.ui

import androidx.compose.runtime.mutableStateListOf
import com.yuroyami.pingy.logic.PingPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

//TODO: Use an MVVM library like moko-mvvm
class ComposeViewmodel {

    /** Defines the operating PingGraph [PingPanel]s but in a mutable state (so it can be observed) */
    val panels = mutableStateListOf<PingPanel>()

    val mainScope = CoroutineScope(Dispatchers.Main)
    val ioScope = CoroutineScope(Dispatchers.Default)

}