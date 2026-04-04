package com.yuroyami.pingy.logic

import kotlin.concurrent.Volatile

/**
 * A thread-safe circular buffer optimized for streaming data and visualization.
 *
 * @param capacity Maximum number of elements the buffer can hold
 * @param T The type of elements stored in the buffer (must be non‑nullable)
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

    @Volatile
    var sampleCount = 0F

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

    fun clear() {
        buffer.fill(null)
        writeIndex = 0
        readIndex = 0
        size = 0
        sampleCount = 0f
    }

    fun isEmpty(): Boolean = size == 0

    companion object {
        /**
         * Factory method to create a generic [RingBuffer] with the specified capacity.
         * The type parameter can be inferred or specified explicitly.
         */
        inline fun <reified T : Any> allocate(size: Int) = RingBuffer<T>(size)
    }
}
