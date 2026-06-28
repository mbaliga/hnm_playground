package dev.hnm.workbench.core

import dev.hnm.workbench.core.export.AhapExporter
import dev.hnm.workbench.core.export.AhapImporter
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.core.library.BuiltInPatterns
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AhapImporterTest {

    @Test
    fun importsTransientWithIntensityAndSharpness() {
        val ahap = """
            {
              "Version": 1.0,
              "Pattern": [
                { "Event": { "Time": 0.0, "EventType": "HapticTransient",
                  "EventParameters": [
                    { "ParameterID": "HapticIntensity", "ParameterValue": 0.8 },
                    { "ParameterID": "HapticSharpness", "ParameterValue": 0.3 } ] } }
              ]
            }
        """.trimIndent()
        val pattern = AhapImporter.import(ahap, "t")
        val events = (pattern.tracks.first() as HapticTrack).events
        assertEquals(1, events.size)
        val t = events.first() as Transient
        assertEquals(0.8, t.intensity, 1e-6)
        assertEquals(0.3, t.sharpness, 1e-6)
    }

    @Test
    fun importsContinuousWithDuration() {
        val ahap = """
            { "Version": 1, "Pattern": [
              { "Event": { "Time": 0.5, "EventType": "HapticContinuous", "EventDuration": 1.2,
                "EventParameters": [ { "ParameterID": "HapticIntensity", "ParameterValue": 0.6 } ] } } ] }
        """.trimIndent()
        val c = (AhapImporter.import(ahap).tracks.first() as HapticTrack).events.first() as Continuous
        assertEquals(0.5, c.time, 1e-6)
        assertEquals(1.2, c.duration, 1e-6)
        assertEquals(0.6, c.intensity, 1e-6)
    }

    @Test
    fun ignoresAudioEventsAndUnknownKeys() {
        val ahap = """
            { "Version": 1, "Metadata": { "Project": "Mixed" }, "Pattern": [
              { "Event": { "Time": 0, "EventType": "AudioCustom", "EventWaveformPath": "boom.wav" } },
              { "Event": { "Time": 0.1, "EventType": "HapticTransient", "SomeUnknown": 42,
                "EventParameters": [ { "ParameterID": "HapticIntensity", "ParameterValue": 1.0 } ] } } ] }
        """.trimIndent()
        val pattern = AhapImporter.import(ahap)
        assertEquals("Mixed", pattern.name)
        assertEquals(1, (pattern.tracks.first() as HapticTrack).events.size) // audio dropped
    }

    @Test
    fun roundTripsExportThenImport() {
        // Export a built-in to AHAP, re-import, and the transient/continuous events should survive.
        val original = BuiltInPatterns.CONFIRM
        val ahap = AhapExporter.export(original)
        val reimported = AhapImporter.import(ahap, original.name)
        val origCount = (original.tracks.first() as HapticTrack).events.size
        val backCount = (reimported.tracks.first() as HapticTrack).events.size
        assertEquals(origCount, backCount)
    }

    @Test
    fun malformedThrowsButOrNullIsSafe() {
        assertFailsWith<AhapImporter.AhapParseException> { AhapImporter.import("not json {{{") }
        assertNull(AhapImporter.importOrNull("not json {{{"))
        // Missing Pattern array is also a parse error.
        assertFailsWith<AhapImporter.AhapParseException> { AhapImporter.import("""{ "Version": 1 }""") }
    }

    @Test
    fun importsParameterCurve() {
        val ahap = """
            { "Version": 1, "Pattern": [
              { "Event": { "Time": 0, "EventType": "HapticContinuous", "EventDuration": 1.0,
                "EventParameters": [ { "ParameterID": "HapticIntensity", "ParameterValue": 1.0 } ] } },
              { "ParameterCurve": { "ParameterID": "HapticIntensityControl", "Time": 0.0,
                "ParameterCurveControlPoints": [
                  { "Time": 0.0, "ParameterValue": 0.0 },
                  { "Time": 1.0, "ParameterValue": 1.0 } ] } } ] }
        """.trimIndent()
        val track = AhapImporter.import(ahap).tracks.first() as HapticTrack
        assertEquals(1, track.curves.size)
        assertTrue(track.curves.first().points.size == 2)
    }
}
