package com.yuroyami.pingy.logic

import kotlin.concurrent.Volatile

/**
 * Fixed-capacity circular buffer for ping history.
 *
 * Concurrency: single writer, many readers. The engine's coroutine is the only
 * writer; Compose reads from the frame and sampler loops.
 *
 * [add] stores the element and *then* advances the volatile [writeIndex]. That
 * ordering is the publication guarantee: any reader that reads the cursor and
 * then reads the slot behind it is reading a fully constructed element.
 *
 * What it does NOT give you is an atomic (cursor, size) pair. [forEachNewestFirst]
 * therefore tolerates a torn read by stopping at the first empty slot, which
 * costs at most one element at the tail. Callers needing a stable view across
 * several passes should take a [snapshot].
 *
 * The internals are private on purpose. They used to be public, which let any
 * caller break the single-writer invariant the whole design rests on.
 */
class RingBuffer<T : Any>(val capacity: Int) {

    @Suppress("UNCHECKED_CAST")
    private val buffer: Array<T?> = arrayOfNulls<Any?>(capacity) as Array<T?>

    @Volatile private var writeIndex = 0
    @Volatile private var readIndex = 0
    @Volatile private var count = 0

    /** Number of elements currently held. */
    val size: Int get() = count

    fun add(element: T) {
        val w = writeIndex
        buffer[w] = element
        if (count == capacity) {
            readIndex = (readIndex + 1) % capacity
        } else {
            count++
        }
        writeIndex = (w + 1) % capacity   // publishes the element above
    }

    /**
     * Visit entries newest first until [action] returns false.
     *
     * Bounded to `capacity - 1` steps so a full traversal can never reach the
     * slot the writer is about to overwrite. Early exit is the point: callers
     * stop at their time horizon instead of walking the whole ring.
     */
    inline fun forEachNewestFirst(action: (T) -> Boolean) {
        val n = newestFirstCount()
        var idx = newestIndex()
        for (i in 0 until n) {
            val element = elementAt(idx) ?: return
            if (!action(element)) return
            idx = if (idx == 0) capacity - 1 else idx - 1
        }
    }

    /** Immutable oldest-first copy. Use when several passes must agree. */
    fun snapshot(): List<T> {
        val out = ArrayList<T>(size)
        val n = newestFirstCount()
        var idx = newestIndex()
        for (i in 0 until n) {
            out.add(elementAt(idx) ?: break)
            idx = if (idx == 0) capacity - 1 else idx - 1
        }
        out.reverse()
        return out
    }

    /** Newest entry, or null when empty. */
    fun last(): T? {
        if (count == 0) return null
        return buffer[newestIndex()]
    }

    /** Oldest retained entry, or null when empty. */
    fun first(): T? {
        if (count == 0) return null
        return buffer[readIndex]
    }

    fun isEmpty(): Boolean = count == 0

    fun clear() {
        count = 0
        readIndex = 0
        writeIndex = 0
        buffer.fill(null)
    }

    // Internal seams for the inline walk above. Not part of the public contract.

    @PublishedApi
    internal fun newestIndex(): Int = (writeIndex + capacity - 1) % capacity

    @PublishedApi
    internal fun newestFirstCount(): Int = minOf(count, capacity - 1)

    @PublishedApi
    internal fun elementAt(index: Int): T? = buffer[index]
}
