package dev.hnm.workbench.core

import dev.hnm.workbench.core.design.ABPair
import dev.hnm.workbench.core.design.RhythmCapture
import dev.hnm.workbench.core.design.Tap
import dev.hnm.workbench.core.design.Variations
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.core.library.BuiltInPatterns
import dev.hnm.workbench.core.library.PatternLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesignTest {

    @Test
    fun mutateIsDeterministicAndStaysInRange() {
        val a = Variations.mutate(BuiltInPatterns.CONFIRM, amount = 0.2, seed = 7)
        val b = Variations.mutate(BuiltInPatterns.CONFIRM, amount = 0.2, seed = 7)
        assertEquals(a, b, "same seed must reproduce the same variation")
        assertNotEquals(BuiltInPatterns.CONFIRM.tracks, a.tracks, "mutation should change something")

        val haptic = a.tracks.filterIsInstance<HapticTrack>().single()
        for (e in haptic.events.filterIsInstance<Transient>()) {
            assertTrue(e.intensity in 0.0..1.0)
            assertTrue(e.sharpness in 0.0..1.0)
            assertTrue(e.time >= 0.0)
        }
    }

    @Test
    fun familyProducesDistinctVariations() {
        val family = Variations.family(BuiltInPatterns.CONFIRM, count = 4)
        assertEquals(4, family.size)
        assertEquals(4, family.map { it.name }.toSet().size)
    }

    @Test
    fun rhythmCaptureNormalizesToZeroAndMapsPressure() {
        val taps = listOf(Tap(1.00, 0.5), Tap(1.10, 0.9), Tap(1.80, 1.0))
        val pattern = RhythmCapture.fromTaps(taps)
        val events = (pattern.tracks.single() as HapticTrack).events.filterIsInstance<Transient>()
        assertEquals(3, events.size)
        assertEquals(0.0, events[0].time, 1e-9) // first tap normalized to t=0
        assertEquals(0.10, events[1].time, 1e-9)
        assertEquals(0.80, events[2].time, 1e-9)
        assertEquals(0.5, events[0].intensity, 1e-9) // pressure -> intensity
        // The quick 0.10 s tap should feel sharper than the slow 0.70 s one.
        assertTrue(events[1].sharpness > events[2].sharpness)
    }

    @Test
    fun libraryRoundTripsAndManagesEntries() {
        val lib = PatternLibrary.withBuiltIns()
        assertEquals(BuiltInPatterns.ALL.size, lib.size, "withBuiltIns() should contain all built-in patterns")

        // mutate() appends " (var N)" so this is a *new* entry, growing the library by 1.
        val mutated = Variations.mutate(BuiltInPatterns.CONFIRM, seed = 1)
        lib.save(mutated)
        assertEquals(BuiltInPatterns.ALL.size + 1, lib.size)
        assertTrue(lib.names.contains(mutated.name))

        val restored = PatternLibrary.fromJson(lib.toJson())
        assertEquals(lib.names.toSet(), restored.names.toSet())
        assertEquals(BuiltInPatterns.CONFIRM, restored.get("Confirm"), "original Confirm should round-trip unchanged")

        restored.remove("Confirm")
        assertNull(restored.get("Confirm"))
        assertEquals(BuiltInPatterns.ALL.size, restored.size) // back to all built-ins minus Confirm
    }

    @Test
    fun abPairDiffersBetweenVariants() {
        val ab = ABPair.fromMutation(BuiltInPatterns.CONFIRM, amount = 0.25, seed = 3)
        assertEquals(BuiltInPatterns.CONFIRM, ab.a)
        assertNotEquals(ab.a.tracks, ab.b.tracks)
    }
}
