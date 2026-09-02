package com.yuroyami.pingy.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RingBufferTest {

    @Test
    fun empty_buffer_reports_nothing() {
        val ring = RingBuffer<Int>(8)
        assertTrue(ring.isEmpty())
        assertNull(ring.last())
        assertNull(ring.first())
        assertEquals(emptyList(), ring.snapshot())
    }

    @Test
    fun newest_first_walk_is_ordered() {
        val ring = RingBuffer<Int>(8)
        (1..5).forEach(ring::add)
        val seen = buildList { ring.forEachNewestFirst { add(it); true } }
        assertEquals(listOf(5, 4, 3, 2, 1), seen)
    }

    @Test
    fun early_exit_stops_the_walk() {
        val ring = RingBuffer<Int>(8)
        (1..5).forEach(ring::add)
        val seen = buildList { ring.forEachNewestFirst { add(it); size < 2 } }
        assertEquals(listOf(5, 4), seen)
    }

    @Test
    fun snapshot_is_oldest_first_and_detached() {
        val ring = RingBuffer<Int>(8)
        (1..3).forEach(ring::add)
        val snap = ring.snapshot()
        assertEquals(listOf(1, 2, 3), snap)
        ring.add(4)
        assertEquals(listOf(1, 2, 3), snap, "snapshot must not observe later writes")
    }

    @Test
    fun overwrites_oldest_once_full() {
        val ring = RingBuffer<Int>(4)
        (1..6).forEach(ring::add)
        assertEquals(6, ring.last())
        assertEquals(4, ring.size)
        // Bounded to capacity - 1 so a walk never reaches the writer's slot.
        val seen = buildList { ring.forEachNewestFirst { add(it); true } }
        assertEquals(listOf(6, 5, 4), seen)
    }

    @Test
    fun walking_a_full_ring_never_reaches_the_writers_slot() {
        // A full-length backwards walk lands exactly on the slot the writer
        // overwrites next, which would hand back a just-written newest entry
        // dressed up as the oldest one. The walk has to stop one short.
        val cap = 16
        val ring = RingBuffer<Int>(cap)
        repeat(cap * 3) { ring.add(it) }
        val seen = buildList { ring.forEachNewestFirst { add(it); true } }
        assertTrue(seen.size <= cap - 1, "walk visited ${seen.size} of $cap slots")
        assertEquals(seen.sortedDescending(), seen, "entries must stay ordered")
    }

    @Test
    fun clear_resets_everything() {
        val ring = RingBuffer<Int>(4)
        (1..4).forEach(ring::add)
        ring.clear()
        assertTrue(ring.isEmpty())
        assertNull(ring.last())
        assertEquals(0, ring.size)
    }
}

class RingBufferSlotTest {

    @Test
    fun add_returns_the_slot_it_wrote() {
        val ring = RingBuffer<Int>(4)
        assertEquals(0, ring.add(10))
        assertEquals(1, ring.add(11))
        assertEquals(2, ring.add(12))
        assertEquals(3, ring.add(13))
        // Wraps back onto the oldest slot once full.
        assertEquals(0, ring.add(14))
    }

    @Test
    fun replace_swaps_an_entry_in_place() {
        val ring = RingBuffer<Int>(8)
        ring.add(1)
        val slot = ring.add(2)
        ring.add(3)
        assertTrue(ring.replace(slot, expected = 2, replacement = 20))
        assertEquals(listOf(1, 20, 3), ring.snapshot())
        val seen = buildList { ring.forEachNewestFirst { add(it); true } }
        assertEquals(listOf(3, 20, 1), seen)
    }

    @Test
    fun replace_refuses_a_recycled_slot() {
        // The slot was handed out for 1, then the ring wrapped and 5 landed on
        // it. A late verdict for 1 must not clobber 5.
        val ring = RingBuffer<Int>(4)
        val slot = ring.add(1)
        listOf(2, 3, 4, 5).forEach(ring::add)
        assertFalse(ring.replace(slot, expected = 1, replacement = 100))
        // 5 wrapped onto that slot and is the newest entry; it must survive.
        assertEquals(5, ring.last())
        assertFalse(100 in ring.snapshot())
    }
}
