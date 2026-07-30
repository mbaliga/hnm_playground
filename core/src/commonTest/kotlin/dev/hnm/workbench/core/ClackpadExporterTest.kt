package dev.hnm.workbench.core

import dev.hnm.workbench.core.export.ClackpadExporter
import dev.hnm.workbench.core.export.ClackpadHapticFile
import dev.hnm.workbench.core.export.KotlinVibrationEffectExporter
import dev.hnm.workbench.core.ir.ControlPoint
import dev.hnm.workbench.core.ir.CurveParam
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.ParameterCurve
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.core.library.BuiltInPatterns
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClackpadExporterTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(text: String): ClackpadHapticFile = json.decodeFromString(ClackpadHapticFile.serializer(), text)

    @Test
    fun rejectsRolesOutsideClackpadsFiveValueVocabulary() {
        assertFailsWith<IllegalArgumentException> {
            ClackpadExporter.export(BuiltInPatterns.TAP, role = "return", sourceVersion = "0.1.0", sourceBuild = "1")
        }
    }

    @Test
    fun acceptsEveryValidRole() {
        assertEquals(setOf("key", "del", "action", "suggest", "swipe"), ClackpadExporter.VALID_ROLES)
        for (role in ClackpadExporter.VALID_ROLES) {
            val out = ClackpadExporter.export(BuiltInPatterns.TAP, role = role, sourceVersion = "0.1.0", sourceBuild = "1")
            assertEquals(role, decode(out).role)
        }
    }

    @Test
    fun mimeTypeIsDistinctFromPlainJson() {
        // Deliberately distinct from "application/json", which Clackpad's own settings backup already claims.
        assertEquals("application/vnd.clackpad.haptic+json", ClackpadExporter.MIME_TYPE)
    }

    @Test
    fun emitsFormatVersionAndAgreedContractBlock() {
        val file = decode(ClackpadExporter.export(BuiltInPatterns.CONFIRM, role = "action", sourceVersion = "0.14.0", sourceBuild = "15"))
        assertEquals("clackpad-haptic", file.format)
        assertEquals(1, file.version)
        assertEquals("agreed", file.contract.status)
        assertEquals("docs/CLACKPAD_CONTRACT.md", file.contract.spec)
        assertEquals("Confirm", file.name)
        assertEquals("Haptics + Audio Workbench", file.source.app)
        assertEquals("0.14.0", file.source.version)
        assertEquals("15", file.source.build)
    }

    @Test
    fun patternBlockCarriesFormatAndOrderedEvents() {
        val file = decode(ClackpadExporter.export(BuiltInPatterns.CONFIRM, role = "action", sourceVersion = "1", sourceBuild = "1"))
        assertEquals("workbench-pattern", file.pattern.format)
        assertEquals(1, file.pattern.version)
        assertEquals(2, file.pattern.events.size) // CONFIRM's haptic track has two transients
        assertTrue(file.pattern.events.zipWithNext().all { (a, b) -> a.atMs <= b.atMs }, "events must be ascending by atMs")
        assertEquals(0, file.pattern.events.first().atMs)
        assertEquals(80, file.pattern.events[1].atMs) // second CONFIRM transient is at 0.08s
    }

    @Test
    fun everyMomentGetsAtLeastTheMinimumDuration() {
        val file = decode(ClackpadExporter.export(BuiltInPatterns.CONFIRM, role = "key", sourceVersion = "1", sourceBuild = "1"))
        assertTrue(file.pattern.events.all { it.duration >= 10 })
    }

    @Test
    fun voiceDerivesFromSharpnessWhenNotAuthored() {
        // CONFIRM: sharp opening transient (0.9) then a duller closing one (0.5).
        val file = decode(ClackpadExporter.export(BuiltInPatterns.CONFIRM, role = "action", sourceVersion = "1", sourceBuild = "1"))
        assertEquals("click", file.pattern.events[0].voice) // sharpness 0.9 >= 0.6
        assertEquals("thud", file.pattern.events[1].voice) // sharpness 0.5 < 0.6
    }

    @Test
    fun voiceReproducesTheContractsThudSanityCheck() {
        // Both HEARTBEAT transients have sharpness well under 0.6 -> both must derive to "thud", exactly
        // the sanity check called out in the contract for a sharpness: 0.06-class transient.
        val file = decode(ClackpadExporter.export(BuiltInPatterns.HEARTBEAT, role = "action", sourceVersion = "1", sourceBuild = "1"))
        assertTrue(file.pattern.events.isNotEmpty())
        assertTrue(file.pattern.events.all { it.voice == "thud" })
    }

    @Test
    fun explicitIrVoiceWinsOverTheDerivedGuess() {
        val pattern = HapticAudioPattern(
            name = "Bell",
            tracks = listOf(
                HapticTrack(id = "h1", events = listOf(Transient(time = 0.0, intensity = 0.7, sharpness = 0.9, voice = "ring"))),
            ),
        )
        val file = decode(ClackpadExporter.export(pattern, role = "suggest", sourceVersion = "1", sourceBuild = "1"))
        // sharpness 0.9 would derive to "click", but the authored voice must win.
        assertEquals("ring", file.pattern.events.single().voice)
    }

    @Test
    fun renderReusesKotlinVibrationEffectExportersAmplitudeTimeline() {
        val events = (BuiltInPatterns.CONFIRM.tracks.first() as HapticTrack).events
        val (expectedTimings, expectedAmps) = KotlinVibrationEffectExporter.buildAmplitudeTimeline(events)

        val file = decode(ClackpadExporter.export(BuiltInPatterns.CONFIRM, role = "key", sourceVersion = "1", sourceBuild = "1"))
        assertEquals(expectedTimings.toList(), file.render.timings)
        assertEquals(expectedAmps.toList(), file.render.amplitudes)
        assertTrue(file.render.amplitudes.all { it in 0..255 })
        assertEquals("android.os.VibrationEffect.createWaveform(timings, amplitudes, -1)", file.render.api)
    }

    @Test
    fun envelopeBakedReflectsWhetherAnyHapticTrackHasCurves() {
        val withoutCurves = decode(ClackpadExporter.export(BuiltInPatterns.TAP, role = "key", sourceVersion = "1", sourceBuild = "1"))
        assertFalse(withoutCurves.envelopeBaked)

        val withCurves = BuiltInPatterns.TAP.copy(
            tracks = listOf(
                HapticTrack(
                    id = "h1",
                    events = listOf(Transient(time = 0.0, intensity = 0.5, sharpness = 0.5)),
                    curves = listOf(
                        ParameterCurve(
                            parameter = CurveParam.HAPTIC_INTENSITY,
                            points = listOf(ControlPoint(0.0, 0.0), ControlPoint(0.1, 1.0)),
                        ),
                    ),
                ),
            ),
        )
        val file = decode(ClackpadExporter.export(withCurves, role = "key", sourceVersion = "1", sourceBuild = "1"))
        assertTrue(file.envelopeBaked)
    }

    @Test
    fun durationMsCoversTheWholePatternIncludingTails() {
        val file = decode(ClackpadExporter.export(BuiltInPatterns.CONFIRM, role = "key", sourceVersion = "1", sourceBuild = "1"))
        // The last event starts at 80ms, but the whole-pattern duration (PatternTiming.durationSeconds)
        // also accounts for envelope/audio tails, so it must run strictly longer than that raw atMs.
        assertTrue(file.durationMs > 80, "durationMs=${file.durationMs}")
    }

    @Test
    fun mutedHapticTracksAreExcluded() {
        val pattern = HapticAudioPattern(
            name = "Muted",
            tracks = listOf(
                HapticTrack(id = "h1", muted = true, events = listOf(Transient(time = 0.0, intensity = 1.0, sharpness = 1.0))),
            ),
        )
        val file = decode(ClackpadExporter.export(pattern, role = "key", sourceVersion = "1", sourceBuild = "1"))
        assertTrue(file.pattern.events.isEmpty())
        assertTrue(file.render.timings.isEmpty())
    }

    @Test
    fun unitsBlockDocumentsTheProvenanceOnlyFields() {
        val out = ClackpadExporter.export(BuiltInPatterns.TAP, role = "key", sourceVersion = "1", sourceBuild = "1")
        assertContains(out, "\"pattern.events[].sharpness\"")
        val file = decode(out)
        assertEquals(
            "0..1 — provenance only; Clackpad's importer does not read this field",
            file.units["pattern.events[].intensity"],
        )
    }
}
