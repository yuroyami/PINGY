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

    /** Two panels per row, laid out like frames on a film strip. */
    GRID,

    /** One panel per page, swiped horizontally. */
    PAGER,
}

/**
 * One status line. [id] keys the auto-dismiss timer.
 *
 * [actionLabel] and [action] make a notice actionable, which is what turns an
 * irreversible tap into a reversible one. Removing a panel and resetting its
 * settings both go through here, so neither is a dead end.
 */
data class Notice(
    val id: Long,
    val text: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
)

/**
 * Everything Undo needs to put a removed panel back the way it was.
 *
 * Not the persisted [PanelSpec]: that deliberately omits the Remember flag and
 * the panel's position, both of which a removal has to restore.
 */
class RemovedPanel internal constructor(
    internal val spec: PanelSpec,
    internal val remember: Boolean,
    internal val index: Int,
)

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

    fun notify(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        notice.value = Notice(++noticeCounter, text, actionLabel, action)
    }

    fun dismissNotice(id: Long) {
        if (notice.value?.id == id) notice.value = null
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
                    // First launch is network-inert: no panel, no socket, no
                    // packet. Seeding a default target here would mean opening
                    // the app quietly starts an unattended stream to someone
                    // else's resolver, which nothing on screen would admit to.
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
    fun addPanel(ip: String, spec: PanelSpec? = null, at: Int = panels.size): AddResult {
        if (panels.size >= MAX_PANELS) return AddResult.AtCapacity
        val key = canonicalTargetKey(ip)
        if (panels.any { canonicalTargetKey(it.ip) == key }) return AddResult.Duplicate

        val panel = PingPanel(ip = ip)
        spec?.let(panel::applySpec)
        panel.startPinging()
        panels.add(at.coerceIn(0, panels.size), panel)
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

    /**
     * Stops, forgets and un-persists a panel, keeping enough to put it back.
     *
     * The engine is genuinely torn down, because holding a socket open for a
     * panel the user just removed would be worse. The configuration comes back
     * as the return value, so the caller can offer an undo that rebuilds it.
     */
    fun removePanel(panel: PingPanel): RemovedPanel {
        val removed = RemovedPanel(
            spec = panel.toSpec(),
            remember = panel.persistAcrossSessions.value,
            index = panels.indexOf(panel).coerceAtLeast(0),
        )
        watchers.remove(panel)?.cancel()
        panels.remove(panel)
        panel.close()
        markDirty()
        return removed
    }

    /** Recreate a removed panel, for undoing a removal. */
    fun restorePanel(removed: RemovedPanel) {
        if (addPanel(removed.spec.ip, removed.spec, at = removed.index) != AddResult.Added) return
        // Remember is session state, not part of the persisted spec, so it has
        // to be put back by hand or Undo silently turns it on.
        panels.firstOrNull { it.ip == removed.spec.ip }?.persistAcrossSessions?.value = removed.remember
    }

    /**
     * Set the app-wide style.
     *
     * Per-panel overrides are preserved. Clearing them meant a single tap on
     * the header silently destroyed every deliberate per-panel choice, with no
     * warning and no way back. Panels that follow the app-wide setting change;
     * panels the user has set explicitly keep what they were given.
     */
    fun setGlobalStyle(style: GraphStyle) {
        graphStyle.value = style
    }

    /** How many panels are pinned to a style of their own. */
    fun panelsWithOwnStyle(): Int = panels.count { it.styleOverride.value != null }

    /** Drop every per-panel style so the whole cockpit follows the app setting. */
    fun clearStyleOverrides() {
        panels.forEach { it.styleOverride.value = null }
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
