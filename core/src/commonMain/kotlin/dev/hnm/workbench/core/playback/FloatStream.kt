package dev.hnm.workbench.core.playback

/**
 * A pull-based stream of `Float` samples in [-1, 1], mono.
 *
 * This is the seam where a future Rust/cpal DSP core can be grafted *only where it pays off* — the
 * continuous-signal path (audio + voice-coil haptics). Consumers pull fixed-size blocks, so the same
 * interface serves both a real-time audio callback and an offline WAV render.
 */
interface FloatStream {
    /** Sample rate this stream was rendered at. */
    val sampleRate: Int

    /** Total number of samples if known up-front (offline render), or `null` for open-ended streams. */
    val totalSamples: Long?

    /**
     * Fill [out] starting at [offset] with up to [count] samples.
     * @return the number of samples written; a value `< count` (including 0) signals end-of-stream.
     */
    fun read(out: FloatArray, offset: Int = 0, count: Int = out.size - offset): Int

    /** Reset the stream to the beginning, if supported. */
    fun reset() {}
}

/** Read an entire finite [FloatStream] into one array. Convenience for offline rendering/exports. */
fun FloatStream.readAll(blockSize: Int = 4096): FloatArray {
    val total = totalSamples
    if (total != null) {
        val buf = FloatArray(total.toInt())
        var written = 0
        while (written < buf.size) {
            val n = read(buf, written, buf.size - written)
            if (n <= 0) break
            written += n
        }
        return if (written == buf.size) buf else buf.copyOf(written)
    }
    // Unknown length: grow dynamically.
    val chunks = ArrayList<FloatArray>()
    var totalRead = 0
    while (true) {
        val block = FloatArray(blockSize)
        val n = read(block, 0, blockSize)
        if (n <= 0) break
        chunks.add(if (n == blockSize) block else block.copyOf(n))
        totalRead += n
        if (n < blockSize) break
    }
    val out = FloatArray(totalRead)
    var pos = 0
    for (c in chunks) {
        c.copyInto(out, pos)
        pos += c.size
    }
    return out
}

/** Wrap an existing array as a finite, resettable [FloatStream]. */
class ArrayFloatStream(
    private val data: FloatArray,
    override val sampleRate: Int,
) : FloatStream {
    private var pos = 0
    override val totalSamples: Long get() = data.size.toLong()

    override fun read(out: FloatArray, offset: Int, count: Int): Int {
        val n = minOf(count, data.size - pos)
        if (n <= 0) return 0
        data.copyInto(out, offset, pos, pos + n)
        pos += n
        return n
    }

    override fun reset() {
        pos = 0
    }
}
