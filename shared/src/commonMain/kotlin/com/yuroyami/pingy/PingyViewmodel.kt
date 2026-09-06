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
import com.yuroyami.pingy.logic.StoreApi
import com.yuroyami.pingy.logic.StoreLoad
import com.yuroyami.pingy.utils.EngineFactory
import com.yuroyami.pingy.utils.PingEngine
import com.yuroyami.pingy.logic.canonicalTargetKey
import com.yuroyami.pingy.ui.Screen
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
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

/** How many times a failed save is retried before the user is told. */
private const val MAX_SAVE_ATTEMPTS = 3

/** Grows with each attempt, so a full disk is not hammered. */
private const val SAVE_RETRY_STEP_MS = 500L

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
class PingyViewmodel(
    private val store: StoreApi = PingyStore,
    private val engineFactory: EngineFactory = { host, packetSize, intervalMs ->
        PingEngine(host, packetSize, intervalMs)
    },
) : ViewModel() {

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

    /**
     * The app's own reduce-motion switch, on top of whatever the platform says.
     *
     * Desktop reports no platform signal at all, so without this those users
     * have no way to stop a canvas that moves continuously.
     */
    val reduceMotionOverride = MutableStateFlow(false)

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

    /**
     * Whether the app is in the foreground.
     *
     * Kept independently of whether any panel exists yet. A cold start that is
     * backgrounded before the store finishes reading used to restore its panels
     * and start probing anyway, because the pause found nothing to record.
     */
    @Volatile
    private var foreground = true

    init {
        PingyLifecycle.viewmodel = this
        viewModelScope.launch {
            loadStore()

            launch {
                merge(
                    graphStyle.map { },
                    panelLayout.map { },
                    reduceMotionOverride.map { },
                ).drop(3).collect { markDirty() }
            }
            launch {
                saveSignal.debounce(400).collect { persist() }
            }
        }
    }

    /** Read the store and put the cockpit into the state it describes. */
    private suspend fun loadStore() {
        when (val loaded = store.load()) {
            is StoreLoad.NotInitialized -> {
                savingBlocked = false
                // First launch is network-inert: no panel, no socket, no packet.
                // Seeding a default target here would mean opening the app
                // quietly starts an unattended stream to someone else's
                // resolver, which nothing on screen would admit to.
                cockpitState.value = CockpitState.FirstRun
            }

            is StoreLoad.Failed -> {
                // Never overwrite a file we failed to read: the old behaviour
                // turned a transient read error into permanent data loss on the
                // next autosave.
                savingBlocked = true
                cockpitState.value = CockpitState.LoadFailed(loaded.message)
                notify(strings.storeUnreadable)
            }

            is StoreLoad.Loaded -> {
                savingBlocked = false
                loaded.graphStyle
                    ?.let { name -> GraphStyle.entries.firstOrNull { it.name == name } }
                    ?.let { graphStyle.value = it }
                loaded.panelLayout
                    ?.let { name -> PanelLayout.entries.firstOrNull { it.name == name } }
                    ?.let { panelLayout.value = it }
                reduceMotionOverride.value = loaded.reduceMotion

                // A duplicate or an over-capacity record is skipped just as
                // silently as an unreadable one, so count them the same way.
                var skipped = loaded.droppedRecords
                loaded.panels.forEach { spec ->
                    if (addPanel(spec.ip, spec) != AddResult.Added) skipped++
                }
                if (skipped > 0) {
                    notify(strings.skippedRecords(skipped))
                }
                cockpitState.value = CockpitState.Ready
            }
        }
    }

    /** Try reading the store again, for instance after fixing a permission. */
    fun retryLoad() {
        viewModelScope.launch { loadStore() }
    }

    /**
     * Move an unreadable store aside and start over.
     *
     * The old bytes are kept, never deleted: they are the only copy of whatever
     * the user had, and a failed read is not proof that the content is gone.
     */
    fun resetStore() {
        viewModelScope.launch {
            store.quarantine()?.let { notify(strings.storeQuarantined(it)) }
            savingBlocked = false
            loadStore()
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

        val panel = PingPanel(ip = ip, engineFactory = engineFactory)
        spec?.let(panel::applySpec)
        if (foreground) panel.startPinging() else pausedPanels += panel
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

    /** How many times the current revision has failed to reach the disk. */
    private var saveAttempt = 0

    private suspend fun persist() {
        if (savingBlocked) return
        val ok = store.save(
            panels = panels.filter { it.persistAcrossSessions.value }.map { it.toSpec() },
            graphStyle = graphStyle.value.name,
            panelLayout = panelLayout.value.name,
            reduceMotion = reduceMotionOverride.value,
        )
        if (ok) {
            saveAttempt = 0
            return
        }
        // A failed write used to end here, silently, and nothing tried again
        // until the user happened to edit something else. Retry the newest
        // state a few times, then say so and offer the retry by hand.
        saveAttempt++
        if (saveAttempt <= MAX_SAVE_ATTEMPTS) {
            delay(SAVE_RETRY_STEP_MS * saveAttempt)
            markDirty()
        } else {
            notify(strings.saveFailed, actionLabel = strings.retry) {
                saveAttempt = 0
                markDirty()
            }
        }
    }

    /**
     * Stop every live engine and release its socket.
     *
     * Called when the app leaves the foreground. Suspending until the sockets
     * are actually closed matters: returning early would leave descriptors open
     * across a background transition the platform may never resume.
     */
    suspend fun pauseMonitoring() {
        foreground = false
        pausedPanels.clear()
        panels.toList().forEach { panel ->
            // Intent, not the transient running flag: a panel mid-start is
            // still one we have to stop and put back afterwards.
            if (panel.wantsToRun) {
                pausedPanels += panel
                panel.stopPingingAndJoin()
            }
        }
    }

    /** Restart exactly the panels that [pauseMonitoring] stopped. */
    fun resumeMonitoring() {
        foreground = true
        if (pausedPanels.isEmpty()) return
        pausedPanels.forEach { it.startPinging() }
        pausedPanels.clear()
    }

    /**
     * Write the current state now and wait for the disk.
     *
     * Returns whether it landed. Callers that can wait, such as a desktop quit,
     * should use this; the debounced autosave has not fired yet at that point
     * and the process is about to take the edit with it.
     */
    suspend fun flushAndWait(): Boolean {
        if (savingBlocked) return true
        return store.save(
            panels = panels.filter { it.persistAcrossSessions.value }.map { it.toSpec() },
            graphStyle = graphStyle.value.name,
            panelLayout = panelLayout.value.name,
            reduceMotion = reduceMotionOverride.value,
        )
    }

    /** Fire and forget, for shells that cannot wait on a background transition. */
    fun flushNow() {
        viewModelScope.launch { flushAndWait() }
    }

    override fun onCleared() {
        super.onCleared()
        watchers.values.forEach { it.cancel() }
        watchers.clear()
        panels.forEach { it.close() }
        panels.clear()
    }
}
