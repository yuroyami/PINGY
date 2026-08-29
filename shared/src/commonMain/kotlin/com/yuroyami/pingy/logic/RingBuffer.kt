package com.yuroyami.pingy.logic

import kotlin.concurrent.Volatile

/**
 * Fixed-capacity circular buffer used for ping history.
 *
 * Concurrency: single-writer / many-reader. The ping engine's coroutine is the
 * sole writer; the Compose render thread iterates via [forEachNewestFirst].
 * [writeIndex]/[readIndex]/[size] are [Volatile] so readers see a consistent
 * recent state — but [add] is NOT atomic, so a second concurrent writer would
 * race. That guarantee is enough for our one-engine-per-panel model.
 *
 * Readers walk backwards from the newest entry: that moves AWAY from the
 * writer's cursor (which overwrites the oldest slot), so a concurrent add can
 * never clobber an entry the reader is about to visit at the fresh end.
 *
 * @param capacity Maximum number of elements the buffer can hold
 * @param T The type of elements stored in the buffer (must be non-nullable)
 */
class RingBuffer<T : Any>(val capacity: Int) {

    /**
     * Internal storage array for buffer elements.
     * The unchecked cast is safe because we only write `T` values into the array
     * and read them back as `T?`.
     */
    @Suppress("UNCHECKED_CAST")
    val buffer: Array<T?> = arrayOfNulls<Any?>(capacity) as Array<T?>

    @Volatile
    var writeIndex = 0

    @Volatile
    var readIndex = 0

    @Volatile
    var size = 0

    fun add(element: T) {
        buffer[writeIndex] = element
        val newWriteIndex = (writeIndex + 1) % capacity

        if (size == capacity) {
            readIndex = (readIndex + 1) % capacity
        } else {
            size++
        }

        writeIndex = newWriteIndex
    }

    /**
     * Visit entries newest-to-oldest until [action] returns false. The element
     * store happens before the volatile [writeIndex] advance, so every slot
     * behind the observed cursor is fully published. Early exit is the point:
     * the caller stops at its time horizon instead of scanning the whole ring.
     */
    inline fun forEachNewestFirst(action: (T) -> Boolean) {
        val count = size
        var idx = (writeIndex + capacity - 1) % capacity
        for (i in 0 until count) {
            val element = buffer[idx] ?: return
            if (!action(element)) return
            idx = (idx + capacity - 1) % capacity
        }
    }

    /** Newest entry. Derives everything from one volatile [writeIndex] read:
     * the writer stores the element before advancing the cursor, so whatever
     * cursor a reader observes, the slot behind it is fully published.
     * (Reading `size` here instead would race: `size` is bumped before
     * `writeIndex`, so a reader could compute the slot from a stale cursor.) */
    fun last(): T? {
        val w = writeIndex
        if (w == 0 && size == 0) return null
        return buffer[(w + capacity - 1) % capacity]
    }

    fun first(): T? {
        if (size == 0) return null
        return buffer[readIndex]
    }

    fun isEmpty(): Boolean = size == 0
}
