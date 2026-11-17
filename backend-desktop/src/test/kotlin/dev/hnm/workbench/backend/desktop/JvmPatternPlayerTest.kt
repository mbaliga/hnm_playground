package dev.hnm.workbench.backend.desktop

import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.library.BuiltInPatterns
import dev.hnm.workbench.core.playback.AudioBackend
import dev.hnm.workbench.core.playback.FloatStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmPatternPlayerTest {

    private class FakeBackend(private val failWith: Throwable? = null) : AudioBackend {
        var started: FloatStream? = null
        var rate = 0
        override fun start(stream: FloatStream, sampleRate: Int) {
            failWith?.let { throw it }
            started = stream
            rate = sampleRate
        }
        override fun stop() {}
    }

    @Test
    fun handsRenderedStreamToBackend() {
        val fake = FakeBackend()
        val pattern = BuiltInPatterns.CONFIRM
        JvmPatternPlayer(backend = fake, sampleRate = 22_050).play(pattern)

        val expected = DefaultPatternRenderer().renderAudio(pattern, 22_050)
        assertEquals(22_050, fake.rate)
        assertEquals(expected.totalSamples, fake.started?.totalSamples)
        assertTrue((fake.started?.totalSamples ?: 0L) > 0L)
    }

    @Test
    fun reportsBackendFailureInsteadOfThrowing() {
        val messages = mutableListOf<String>()
        val player = JvmPatternPlayer(
            backend = FakeBackend(IllegalStateException("no line")),
            onMessage = { messages += it },
        )
        player.play(BuiltInPatterns.CONFIRM)
        assertEquals(listOf("audio failed: no line"), messages)
    }
}
