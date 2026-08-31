package com.yuroyami.pingy.utils

import com.yuroyami.pingy.logic.MAX_PANELS
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO

/**
 * One bounded lane for every probe engine in the process.
 *
 * Each engine blocks a thread inside `poll(2)`, which is why they cannot share
 * a single-threaded dispatcher. Giving every engine its own
 * `Dispatchers.IO.limitedParallelism(1)` view would leave the total unbounded:
 * one blocked thread per panel, all of them taken from the shared IO pool that
 * DataStore, the resolver and file writes also draw on.
 *
 * One view sized to [MAX_PANELS] keeps the per-engine blocking behaviour while
 * capping what probing can take from that pool, so a cockpit full of panels
 * cannot starve the rest of the process.
 */
@OptIn(ExperimentalCoroutinesApi::class)
object PingDispatchers {

    /** Shared, bounded lane for engine loops. */
    val engine: CoroutineDispatcher by lazy {
        Dispatchers.IO.limitedParallelism(MAX_PANELS)
    }
}
