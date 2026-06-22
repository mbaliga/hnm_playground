package dev.hnm.workbench.desktop

import dev.hnm.workbench.core.playback.AudioBackend
import dev.hnm.workbench.core.playback.FloatStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/**
 * A JVM desktop audio backend over `javax.sound.sampled` (§9: "start simple"). It pulls from the
 * [FloatStream] in small blocks and writes 16-bit PCM to a [SourceDataLine]. The low-buffer pull loop
 * is the seam where a future Rust/cpal path would slot in for tighter latency (§8).
 */
class JvmAudioBackend(private val bufferFrames: Int = 1024) : AudioBackend {

    private var line: SourceDataLine? = null
    private var worker: Thread? = null
    @Volatile private var running = false

    override fun start(stream: FloatStream, sampleRate: Int) {
        stop()
        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false) // 16-bit, mono, little-endian
        val info = DataLine.Info(SourceDataLine::class.java, format)
        val sdl = AudioSystem.getLine(info) as SourceDataLine
        sdl.open(format, bufferFrames * 2 * 4)
        sdl.start()
        line = sdl
        running = true
        worker = thread(name = "jvm-audio", isDaemon = true) {
            val floats = FloatArray(bufferFrames)
            val bytes = ByteArray(bufferFrames * 2)
            while (running) {
                val n = stream.read(floats, 0, bufferFrames)
                if (n <= 0) break
                for (i in 0 until n) {
                    val v = (floats[i].coerceIn(-1f, 1f) * 32767f).roundToInt()
                    bytes[i * 2] = (v and 0xFF).toByte()
                    bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                }
                sdl.write(bytes, 0, n * 2)
            }
            sdl.drain()
        }
    }

    override fun stop() {
        running = false
        worker?.join(500)
        worker = null
        line?.let {
            it.stop()
            it.close()
        }
        line = null
    }
}
