package com.yuroyami.pingy

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuroyami.pingy.logic.PanelSpec
import com.yuroyami.pingy.logic.PingPanel
import com.yuroyami.pingy.logic.PingyStore
import com.yuroyami.pingy.ui.Screen
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/** The two artistic renderings of a ping graph. One dataset, two geometries. */
enum class GraphStyle {
    /** Discrete color-coded bars; each ping is its own rectangle. */
    PINGLETTES,

    /** One continuous ridge: diagonal slopes between ping points, color blending along the range. */
    MOUNTAIN_SLOPES,
}

/** How the cockpit arranges its panels. */
enum class PanelLayout {
    /** Classic single-column stack. */
    COLUMN,

    /** Two panels per row, celluloid-style. */
    GRID,

    /** One panel per page, swiped horizontally. */
    PAGER,
}

/** One transient status line. [id] keys the auto-dismiss timer so a fresh
 * notice restarts the clock instead of dying on the old one's schedule. */
data class Notice(val id: Long, val text: String)

@OptIn(FlowPreview::class)
class PingyViewmodel : ViewModel() {

    val backstack = mutableStateListOf<Screen>(Screen.Main)

    /** Transient toast-line shown in the header's preallocated notice slot. */
    val notice = MutableStateFlow<Notice?>(null)
    private var noticeCounter = 0L

    fun notify(text: String) {
        notice.value = Notice(++noticeCounter, text)
    }

    /** Operating [PingPanel]s in observable mutable state */
    val panels = mutableStateListOf<PingPanel>()

    /** App-wide graph rendering style; panels may override it individually. */
    val graphStyle = MutableStateFlow(GraphStyle.MOUNTAIN_SLOPES)

    /** Cockpit arrangement, cycled from the header. */
    val panelLayout = MutableStateFlow(PanelLayout.COLUMN)

    // replay = 1: the first markDirty fires during init, before the debounce
    // collector below exists; replay hands that pending signal to the late
    // collector instead of dropping it (a fresh session would otherwise never
    // write the store until the user changed something).
    private val saveSignal = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val watchers = mutableMapOf<PingPanel, Job>()

    init {
        viewModelScope.launch {
            val state = PingyStore.load()
            state.graphStyle
                ?.let { name -> GraphStyle.entries.firstOrNull { it.name == name } }
                ?.let { graphStyle.value = it }
            state.panelLayout
                ?.let { name -> PanelLayout.entries.firstOrNull { it.name == name } }
                ?.let { panelLayout.value = it }

            if (state.panels.isEmpty()) {
                addPanel("1.1.1.1")
            } else {
                state.panels.forEach { spec -> addPanel(spec.ip, spec) }
            }

            // Persist follows every relevant change from here on.
            launch {
                merge(graphStyle.map { }, panelLayout.map { }).drop(2).collect { markDirty() }
            }
            launch {
                saveSignal.debounce(400).collect { persist() }
            }
        }
    }

    /** Adds and starts a panel. Returns false when the target already exists. */
    fun addPanel(ip: String, spec: PanelSpec? = null): Boolean {
        if (panels.any { it.ip == ip }) return false
        val panel = PingPanel(ip = ip)
        spec?.let(panel::applySpec)
        panel.startPinging()
        panels.add(panel)
        watchers[panel] = viewModelScope.launch {
            val flows = listOf(
                panel.interval, panel.packetSize, panel.roof, panel.angleOfAttack,
                panel.timeframeMs, panel.canvasHeightFraction, panel.styleOverride,
                panel.persistAcrossSessions,
            ).map { flow -> flow.map { } }
            merge(*flows.toTypedArray()).drop(flows.size).collect { markDirty() }
        }
        markDirty()
        return true
    }

    /** Stops, forgets, and un-persists a panel. */
    fun removePanel(panel: PingPanel) {
        watchers.remove(panel)?.cancel()
        panel.close()
        panels.remove(panel)
        markDirty()
    }

    /** Sets the app-wide style and clears per-panel overrides, so the global
     * toggle always visibly rules the whole cockpit. */
    fun setGlobalStyle(style: GraphStyle) {
        panels.forEach { it.styleOverride.value = null }
        graphStyle.value = style
    }

    private fun markDirty() {
        saveSignal.tryEmit(Unit)
    }

    private suspend fun persist() {
        PingyStore.save(
            panels = panels.filter { it.persistAcrossSessions.value }.map { it.toSpec() },
            graphStyle = graphStyle.value.name,
            panelLayout = panelLayout.value.name,
        )
    }

    override fun onCleared() {
        super.onCleared()
        watchers.values.forEach { it.cancel() }
        watchers.clear()
        panels.forEach { it.close() }
        panels.clear()
    }
}
