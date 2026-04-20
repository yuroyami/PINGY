package com.yuroyami.pingy.logic

import kotlin.concurrent.Volatile

/**
 * Fixed-capacity circular buffer used for ping history.
 *
 * Concurrency: single-writer / many-reader. The ping engine's coroutine is the
 * sole writer; the Compose render thread iterates via [fastForEachWithIndex].
 * [writeIndex]/[readIndex]/[size] are [Volatile] so readers see a consistent
 * recent state — but [add] is NOT atomic, so a second concurrent writer would
 * race. That guarantee is enough for our one-engine-per-panel model.
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

    inline fun fastForEachWithIndex(crossinline action: (T?, Int) -> Unit) {
        val currentSize = size
        val currentReadIndex = readIndex
        if (currentSize == 0) return

        val remainingToEnd = capacity - currentReadIndex

        if (currentSize <= remainingToEnd) {
            for (i in 0 until currentSize) {
                action(buffer[currentReadIndex + i], currentReadIndex + i)
            }
        } else {
            for (i in 0 until remainingToEnd) {
                action(buffer[currentReadIndex + i], currentReadIndex + i)
            }
            val remainingCount = currentSize - remainingToEnd
            for (i in 0 until remainingCount) {
                action(buffer[i], i)
            }
        }
    }

    fun last(): T? {
        if (size == 0) return null
        val lastIndex = (writeIndex + capacity - 1) % capacity
        return buffer[lastIndex]
    }

    fun first(): T? {
        if (size == 0) return null
        return buffer[readIndex]
    }

    fun isEmpty(): Boolean = size == 0
}
