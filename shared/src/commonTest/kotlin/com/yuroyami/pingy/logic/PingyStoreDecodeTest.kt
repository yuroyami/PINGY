package com.yuroyami.pingy.logic

import androidx.datastore.preferences.core.preferencesOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Reading the store, with the upgrade path included.
 *
 * The initialized marker arrived after the first release, so an older file has
 * panels and no marker. Reporting that as a first run made the next save wipe
 * the targets the user had saved.
 */
class PingyStoreDecodeTest {

    @Test
    fun an_empty_file_is_a_first_run() {
        assertIs<StoreLoad.NotInitialized>(decodeStore(preferencesOf()))
    }

    @Test
    fun a_legacy_file_without_the_marker_keeps_its_panels() {
        val prefs = preferencesOf(
            KEY_PANELS to """[{"ip":"1.1.1.1"},{"ip":"8.8.8.8"}]""",
            KEY_STYLE to "PINGLETTES",
            KEY_LAYOUT to "GRID",
        )
        val loaded = assertIs<StoreLoad.Loaded>(decodeStore(prefs))
        assertEquals(listOf("1.1.1.1", "8.8.8.8"), loaded.panels.map { it.ip })
        assertEquals("PINGLETTES", loaded.graphStyle)
        assertEquals("GRID", loaded.panelLayout)
    }

    @Test
    fun a_legacy_file_holding_only_a_layout_is_still_initialized() {
        val loaded = assertIs<StoreLoad.Loaded>(decodeStore(preferencesOf(KEY_LAYOUT to "PAGER")))
        assertEquals(0, loaded.panels.size)
        assertEquals("PAGER", loaded.panelLayout)
    }

    @Test
    fun the_marker_with_no_panels_is_an_emptied_cockpit_not_a_first_run() {
        val loaded = assertIs<StoreLoad.Loaded>(decodeStore(preferencesOf(KEY_INITIALIZED to true)))
        assertEquals(0, loaded.panels.size)
    }

    @Test
    fun an_oversized_panel_list_is_refused_before_it_is_parsed() {
        val huge = "[" + List(4_000) { """{"ip":"1.1.1.1"}""" }.joinToString(",") + "]"
        assertIs<StoreLoad.Failed>(decodeStore(preferencesOf(KEY_INITIALIZED to true, KEY_PANELS to huge)))
    }

    @Test
    fun a_full_cockpit_is_nowhere_near_the_bound() {
        val full = "[" + List(24) {
            """{"ip":"host$it.example.com","intervalMs":250,"packetSize":480,"roof":2000,"angleOfAttack":15.0,"timeframeMs":30000,"canvasHeightFraction":0.45,"style":"MOUNTAIN_SLOPES"}"""
        }.joinToString(",") + "]"
        val loaded = assertIs<StoreLoad.Loaded>(decodeStore(preferencesOf(KEY_INITIALIZED to true, KEY_PANELS to full)))
        assertEquals(24, loaded.panels.size)
    }

    @Test
    fun unreadable_records_are_dropped_and_counted() {
        val prefs = preferencesOf(
            KEY_INITIALIZED to true,
            KEY_PANELS to """[{"ip":"1.1.1.1"},{"ip":"   "},{"ip":"8.8.8.8"}]""",
        )
        val loaded = assertIs<StoreLoad.Loaded>(decodeStore(prefs))
        assertEquals(listOf("1.1.1.1", "8.8.8.8"), loaded.panels.map { it.ip })
        assertEquals(1, loaded.droppedRecords)
    }
}
