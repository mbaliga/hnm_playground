package dev.hnm.workbench.core

import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.ir.PatternSerialization
import dev.hnm.workbench.core.library.BuiltInPatterns
import dev.hnm.workbench.core.library.ChromeSemantic
import dev.hnm.workbench.core.playback.ChromePlayer
import dev.hnm.workbench.core.playback.HapticCapabilities
import dev.hnm.workbench.core.playback.InterfaceFeelLevel
import dev.hnm.workbench.core.playback.PatternPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromePatternsTest {

    private val renderer = DefaultPatternRenderer()

    @Test
    fun everyChromeSemanticRoundTripsThroughJson() {
        for (semantic in ChromeSemantic.entries) {
            val json = PatternSerialization.encode(semantic.pattern)
            val decoded = PatternSerialization.decode(json)
            assertEquals(semantic.pattern, decoded, "${semantic.name} did not round-trip")
        }
    }

    @Test
    fun chromeIsExcludedFromAllButPresentInChrome() {
        assertTrue(BuiltInPatterns.CHROME.isNotEmpty())
        assertEquals(ChromeSemantic.entries.size, BuiltInPatterns.CHROME.size)
        // ALL (the user-facing gallery source) must not contain any chrome.* pattern.
        val allNames = BuiltInPatterns.ALL.map { it.name }.toSet()
        BuiltInPatterns.CHROME.forEach { chrome ->
            assertFalse(chrome.name in allNames, "${chrome.name} leaked into BuiltInPatterns.ALL")
        }
    }

    @Test
    fun chromeConfirmReusesConfirmVerbatim() {
        val confirmEvents = (BuiltInPatterns.CONFIRM.tracks.first() as dev.hnm.workbench.core.ir.HapticTrack).events
        val chromeConfirmEvents = (BuiltInPatterns.CHROME_CONFIRM.tracks.first() as dev.hnm.workbench.core.ir.HapticTrack).events
        assertEquals(confirmEvents, chromeConfirmEvents)
        assertEquals("chrome.confirm", BuiltInPatterns.CHROME_CONFIRM.name)
    }

    @Test
    fun everyChromePatternScheduleIsNonEmptyOnLra() {
        for (semantic in ChromeSemantic.entries) {
            val cmds = renderer.scheduleHaptics(semantic.pattern, HapticCapabilities.LRA_FULL)
            assertTrue(cmds.isNotEmpty(), "${semantic.name} scheduled nothing on a full LRA")
        }
    }

    // ---- ChromePlayer: priority latch + capability gate + level gate ----

    private class RecordingPlayer : PatternPlayer {
        var lastPlayed: dev.hnm.workbench.core.ir.HapticAudioPattern? = null
        var playCount = 0
        override fun play(pattern: dev.hnm.workbench.core.ir.HapticAudioPattern) {
            lastPlayed = pattern
            playCount++
        }
    }

    @Test
    fun playsThroughWhenIdleAndCapable() {
        val recorder = RecordingPlayer()
        val chrome = ChromePlayer(recorder, hasAmplitudeControl = true, level = InterfaceFeelLevel.FULL)
        chrome.play(ChromeSemantic.TAP)
        assertEquals(1, recorder.playCount)
        assertEquals(ChromeSemantic.TAP.pattern, recorder.lastPlayed)
    }

    @Test
    fun busyLatchSuppressesChrome() {
        val recorder = RecordingPlayer()
        val chrome = ChromePlayer(recorder, hasAmplitudeControl = true, level = InterfaceFeelLevel.FULL)
        chrome.busy = true
        chrome.play(ChromeSemantic.NAVIGATE)
        assertEquals(0, recorder.playCount, "chrome must not fire while a real pattern/slider owns the actuator")
        assertFalse(chrome.wouldPlay())

        chrome.busy = false
        chrome.play(ChromeSemantic.NAVIGATE)
        assertEquals(1, recorder.playCount, "chrome should resume once the latch clears")
    }

    @Test
    fun noAmplitudeControlSuppressesChrome() {
        val recorder = RecordingPlayer()
        val chrome = ChromePlayer(recorder, hasAmplitudeControl = false, level = InterfaceFeelLevel.FULL)
        chrome.play(ChromeSemantic.LAND)
        assertEquals(0, recorder.playCount, "silence beats mush below amplitude-control hardware")
    }

    @Test
    fun offLevelSuppressesChrome() {
        val recorder = RecordingPlayer()
        val chrome = ChromePlayer(recorder, hasAmplitudeControl = true, level = InterfaceFeelLevel.OFF)
        chrome.play(ChromeSemantic.CONFIRM)
        assertEquals(0, recorder.playCount)
    }

    @Test
    fun subtleLevelScalesIntensityDown() {
        val recorder = RecordingPlayer()
        val chrome = ChromePlayer(recorder, hasAmplitudeControl = true, level = InterfaceFeelLevel.SUBTLE)
        chrome.play(ChromeSemantic.TAP)
        val played = recorder.lastPlayed!!
        val originalIntensity = (ChromeSemantic.TAP.pattern.tracks.first() as dev.hnm.workbench.core.ir.HapticTrack)
            .events.first().let { (it as dev.hnm.workbench.core.ir.Transient).intensity }
        val scaledIntensity = (played.tracks.first() as dev.hnm.workbench.core.ir.HapticTrack)
            .events.first().let { (it as dev.hnm.workbench.core.ir.Transient).intensity }
        assertTrue(scaledIntensity < originalIntensity, "SUBTLE must scale intensity down from FULL")
        assertEquals(originalIntensity * InterfaceFeelLevel.SUBTLE.intensityScale, scaledIntensity, 1e-9)
    }

    @Test
    fun fullLevelDoesNotMutatePattern() {
        val recorder = RecordingPlayer()
        val chrome = ChromePlayer(recorder, hasAmplitudeControl = true, level = InterfaceFeelLevel.FULL)
        chrome.play(ChromeSemantic.DETENT)
        assertEquals(ChromeSemantic.DETENT.pattern, recorder.lastPlayed)
    }
}
