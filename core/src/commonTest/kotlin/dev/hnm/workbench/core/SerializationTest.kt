package dev.hnm.workbench.core

import dev.hnm.workbench.core.ir.AudioTrack
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.Coupling
import dev.hnm.workbench.core.ir.CouplingMode
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.OscEvent
import dev.hnm.workbench.core.ir.PatternSerialization
import dev.hnm.workbench.core.ir.Primitive
import dev.hnm.workbench.core.ir.PrimitiveType
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.core.library.BuiltInPatterns
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SerializationTest {

    @Test
    fun confirmRoundTrips() {
        val json = PatternSerialization.encode(BuiltInPatterns.CONFIRM)
        val decoded = PatternSerialization.decode(json)
        assertEquals(BuiltInPatterns.CONFIRM, decoded)
    }

    @Test
    fun usesTypeDiscriminator() {
        val json = PatternSerialization.encode(BuiltInPatterns.CONFIRM)
        // Polymorphic tracks/events use the "type" discriminator from @SerialName.
        assertContains(json, "\"type\": \"haptic\"")
        assertContains(json, "\"type\": \"audio\"")
        assertContains(json, "\"type\": \"transient\"")
        assertContains(json, "\"type\": \"osc\"")
    }

    @Test
    fun nativeJsonMatchesBriefShape() {
        val json = PatternSerialization.encode(BuiltInPatterns.CONFIRM)
        assertContains(json, "\"name\": \"Confirm\"")
        assertContains(json, "\"frequencyHz\": 880.0")
        assertContains(json, "\"sharpness\": 0.9")
    }

    @Test
    fun fullPolymorphicHierarchyRoundTrips() {
        val pattern = BuiltInPatterns.CONFIRM.copy(
            tracks = listOf(
                HapticTrack(
                    id = "h",
                    events = listOf(
                        Transient(0.0, 0.5, 0.5),
                        Continuous(0.1, 0.3, 0.7, 0.4),
                        Primitive(0.5, PrimitiveType.SPIN, 0.9),
                    ),
                ),
                AudioTrack(id = "a", events = listOf(OscEvent(0.0, 0.1, dev.hnm.workbench.core.ir.Waveform.SAW, 220.0))),
            ),
            couplings = listOf(Coupling("c1", CouplingMode.AUDIO_DRIVES_HAPTICS, "a", "h")),
        )
        val decoded = PatternSerialization.decode(PatternSerialization.encode(pattern))
        assertEquals(pattern, decoded)
        assertTrue(decoded.couplings.single().mode == CouplingMode.AUDIO_DRIVES_HAPTICS)
    }
}
