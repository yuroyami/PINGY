package com.yuroyami.pingy

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuroyami.pingy.i18n.EnStrings
import com.yuroyami.pingy.i18n.Strings
import com.yuroyami.pingy.logic.MAX_PANELS
import com.yuroyami.pingy.logic.PanelSpec
import com.yuroyami.pingy.logic.PingPanel
import com.yuroyami.pingy.logic.PingyStore
import com.yuroyami.pingy.logic.StoreLoad
import com.yuroyami.pingy.logic.canonicalTargetKey
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
import kotlin.concurrent.Volatile
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

/** One transient status line. [id] keys the auto-dismiss timer. */
data class Notice(val id: Long, val text: String)

/**
 * Where the cockpit is in its startup, so the UI can tell these apart instead
 * of rendering all of them as an identical blank screen.
 */
sealed interface CockpitState {
    /** Reading the store. */
    data object Loading : CockpitState

    /** First ever run. Nothing is monitored until the user asks. */
    data object FirstRun : CockpitState

    /** Store read fine. [panels] may be empty because the user emptied it. */
    data object Ready : CockpitState

    /** The store exists but could not be read. It is left untouched on disk. */
    data class LoadFailed(val message: String) : CockpitState
}

@OptIn(FlowPreview::class)
class PingyViewmodel : ViewModel() {

    val backstack = mutableStateListOf<Screen>(Screen.Main)

    /** Transient toast-line shown in the header's preallocated notice slot. */
    val notice = MutableStateFlow<Notice?>(null)
    private var noticeCounter = 0L

    fun notify(text: String) {
        notice.value = Notice(++noticeCounter, text)
    }

    /**
     * Strings for messages raised outside composition.
     *
     * The view model can produce user-facing text before any UI exists (a
     * failed store read happens during init), so it cannot read the
     * CompositionLocal. The UI pushes the active catalogue in; English is the
     * fallback until it does.
     */
    @Volatile
    var strings: Strings = EnStrings

    /** Operating [PingPanel]s in observable mutable state. */
    val panels = mutableStateListOf<PingPanel>()

    val cockpitState = MutableStateFlow<CockpitState>(CockpitState.Loading)

    /** App-wide graph rendering style; panels may override it individually. */
    val graphStyle = MutableStateFlow(GraphStyle.MOUNTAIN_SLOPES)

    /** Cockpit arrangement, cycled from the header. */
    val panelLayout = MutableStateFlow(PanelLayout.COLUMN)

    // replay = 1 so the first markDirty, which happens during init before the
    // debounce collector exists, is handed to the late collector instead of lost.
    private val saveSignal = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val watchers = mutableMapOf<PingPanel, Job>()

    /** Set when the store could not be read, so saving cannot clobber the file. */
    private var savingBlocked = false

    /** Panels that were live when monitoring was paused, to restore on resume. */
    private val pausedPanels = mutableSetOf<PingPanel>()

    init {
        PingyLifecycle.viewmodel = this
        viewModelScope.launch {
            when (val loaded = PingyStore.load()) {
                is StoreLoad.NotInitialized -> {
                    // First launch is network-inert. Previously this created and
                    // immediately started a 1.1.1.1 panel, so simply opening the
                    // app began an unattended stream to Cloudflare that no copy
                    // in the product ever mentioned.
                    cockpitState.value = CockpitState.FirstRun
                }

                is StoreLoad.Failed -> {
                    // Never overwrite a file we failed to read: the old behaviour
                    // turned a transient read error into permanent data loss on
                    // the next autosave.
                    savingBlocked = true
                    cockpitState.value = CockpitState.LoadFailed(loaded.message)
                    notify(strings.storeUnreadable)
                }

                is StoreLoad.Loaded -> {
                    loaded.graphStyle
                        ?.let { name -> GraphStyle.entries.firstOrNull { it.name == name } }
                        ?.let { graphStyle.value = it }
                    loaded.panelLayout
                        ?.let { name -> PanelLayout.entries.firstOrNull { it.name == name } }
                        ?.let { panelLayout.value = it }

                    loaded.panels.forEach { spec -> addPanel(spec.ip, spec) }
                    if (loaded.droppedRecords > 0) {
                        notify(strings.skippedRecords(loaded.droppedRecords))
                    }
                    cockpitState.value = CockpitState.Ready
                }
            }

            launch {
                merge(graphStyle.map { }, panelLayout.map { }).drop(2).collect { markDirty() }
            }
            launch {
                saveSignal.debounce(400).collect { persist() }
            }
        }
    }

    /**
     * Adds and starts a panel.
     *
     * Duplicate detection uses [canonicalTargetKey], not the raw spelling. Two
     * spellings of one address would otherwise open two sockets to the same
     * peer, which is exactly the situation reply identity has to defend against.
     */
    fun addPanel(ip: String, spec: PanelSpec? = null): AddResult {
        if (panels.size >= MAX_PANELS) return AddResult.AtCapacity
        val key = canonicalTargetKey(ip)
        if (panels.any { canonicalTargetKey(it.ip) == key }) return AddResult.Duplicate

        val panel = PingPanel(ip = ip)
        spec?.let(panel::applySpec)
        panel.startPinging()
        panels.add(panel)
        if (cockpitState.value is CockpitState.FirstRun) cockpitState.value = CockpitState.Ready

        watchers[panel] = viewModelScope.launch {
            val flows = listOf(
                panel.interval, panel.packetSize, panel.roof, panel.angleOfAttack,
                panel.timeframeMs, panel.canvasHeightFraction, panel.styleOverride,
                panel.persistAcrossSessions,
            ).map { flow -> flow.map { } }
            merge(*flows.toTypedArray()).drop(flows.size).collect { markDirty() }
        }
        markDirty()
        return AddResult.Added
    }

    /** Why an add did or did not happen, so the UI can say something useful. */
    enum class AddResult { Added, Duplicate, AtCapacity }

    /** Stops, forgets, and un-persists a panel. */
    fun removePanel(panel: PingPanel) {
        watchers.remove(panel)?.cancel()
        panels.remove(panel)
        panel.close()
        markDirty()
    }

    /** Sets the app-wide style and clears per-panel overrides. */
    fun setGlobalStyle(style: GraphStyle) {
        panels.forEach { it.styleOverride.value = null }
        graphStyle.value = style
    }

    private fun markDirty() {
        saveSignal.tryEmit(Unit)
    }

    private suspend fun persist() {
        if (savingBlocked) return
        PingyStore.save(
            panels = panels.filter { it.persistAcrossSessions.value }.map { it.toSpec() },
            graphStyle = graphStyle.value.name,
            panelLayout = panelLayout.value.name,
        )
    }

    /**
     * Stop every live engine and release its socket.
     *
     * Called when the app leaves the foreground. Suspending until the sockets
     * are actually closed matters: returning early would leave descriptors open
     * across a background transition the platform may never resume.
     */
    suspend fun pauseMonitoring() {
        pausedPanels.clear()
        panels.forEach { panel ->
            if (panel.running.value) {
                pausedPanels += panel
                panel.stopPingingAndJoin()
            }
        }
    }

    /** Restart exactly the panels that [pauseMonitoring] stopped. */
    fun resumeMonitoring() {
        if (pausedPanels.isEmpty()) return
        pausedPanels.forEach { it.startPinging() }
        pausedPanels.clear()
    }

    /** Flush pending edits now, for example when the host is going away. */
    fun flushNow() {
        viewModelScope.launch { persist() }
    }

    override fun onCleared() {
        super.onCleared()
        watchers.values.forEach { it.cancel() }
        watchers.clear()
        panels.forEach { it.close() }
        panels.clear()
    }
}
