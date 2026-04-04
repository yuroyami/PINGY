package com.yuroyami.pingy.logic

/**
 * A fixed-capacity circular buffer that overwrites the oldest elements when full.
 * Optimized for the ping graph use case: fast append, indexed access from newest to oldest,
 * and snapshot iteration without copying.
 */
class RingBuffer<T>(val capacity: Int) {
    private val buffer = arrayOfNulls<Any>(capacity)
    private var head = 0 // next write position
    private var _size = 0

    val size: Int get() = _size

    fun add(element: T) {
        buffer[head] = element
        head = (head + 1) % capacity
        if (_size < capacity) _size++
    }

    /** Get element by logical index (0 = oldest still in buffer, size-1 = newest). */
    @Suppress("UNCHECKED_CAST")
    operator fun get(index: Int): T {
        if (index < 0 || index >= _size) throw IndexOutOfBoundsException("Index $index, size $_size")
        val start = if (_size < capacity) 0 else head
        val realIndex = (start + index) % capacity
        return buffer[realIndex] as T
    }

    /** Get the most recently added element, or null if empty. */
    @Suppress("UNCHECKED_CAST")
    fun lastOrNull(): T? {
        if (_size == 0) return null
        val lastIndex = (head - 1 + capacity) % capacity
        return buffer[lastIndex] as T
    }

    /** Iterate over all elements from oldest to newest. */
    @Suppress("UNCHECKED_CAST")
    fun forEach(action: (T) -> Unit) {
        val start = if (_size < capacity) 0 else head
        for (i in 0 until _size) {
            action(buffer[(start + i) % capacity] as T)
        }
    }

    /** Returns elements as a new list (oldest to newest). */
    @Suppress("UNCHECKED_CAST")
    fun toList(): List<T> {
        val result = ArrayList<T>(_size)
        forEach { result.add(it) }
        return result
    }

    fun clear() {
        head = 0
        _size = 0
    }

    val isEmpty: Boolean get() = _size == 0
}
