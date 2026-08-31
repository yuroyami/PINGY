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
 * a single-threaded dispatcher. Previously each engine took its own
 * `Dispatchers.IO.limitedParallelism(1)` view, and nothing bounded the total:
 * the aggregate was however many panels existed, and those threads came out of
 * the shared IO pool that DataStore, the resolver and file writes also use.
 *
 * A single view sized to [MAX_PANELS] keeps the per-engine blocking behaviour
 * while capping what probing can take from the pool, so a cockpit full of
 * panels can no longer starve everything else in the process.
 */
@OptIn(ExperimentalCoroutinesApi::class)
object PingDispatchers {

    /** Shared, bounded lane for engine loops. */
    val engine: CoroutineDispatcher by lazy {
        Dispatchers.IO.limitedParallelism(MAX_PANELS)
    }
}
