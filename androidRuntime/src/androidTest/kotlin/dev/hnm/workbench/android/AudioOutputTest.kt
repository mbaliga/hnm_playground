package dev.hnm.workbench.android

import android.test.InstrumentationTestCase
import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.library.StarterEffects
import dev.hnm.workbench.core.playback.readAll

/** Runs against the real Android AudioTrack, not JVM Android stubs. */
@Suppress("DEPRECATION")
class AudioOutputTest : InstrumentationTestCase() {
    fun testPairedPresetsPrimeStaticAudioAndStartWithoutInitializationFailure() {
        val output=AudioPlayer()
        try {
            for(effect in listOf(StarterEffects.UNLOCK,StarterEffects.LOCK)) {
                val samples=DefaultPatternRenderer().renderAudio(effect,48000).readAll()
                assertTrue(samples.any { it != 0f })
                output.prepare(samples,48000)
                output.start()
                Thread.sleep(50)
                output.release()
            }
        } finally { output.release() }
    }
    fun testRapidReplacementAndEmptyPreparationReleaseThePreviousClip() {
        val output=AudioPlayer()
        try {
            val samples=DefaultPatternRenderer().renderAudio(StarterEffects.UNLOCK,48000).readAll()
            repeat(3) { output.prepare(samples,48000);output.start() }
            output.prepare(FloatArray(0),48000)
            output.start()
        } finally { output.release() }
    }
}
