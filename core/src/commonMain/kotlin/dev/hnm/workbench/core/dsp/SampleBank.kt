package dev.hnm.workbench.core.dsp

/** A loaded audio sample: mono float data plus its native sample rate. */
class LoadedSample(val data: FloatArray, val sampleRate: Int)

/** Resolves the `sampleId` on a [dev.hnm.workbench.core.ir.SampleEvent] to PCM data. */
fun interface SampleBank {
    fun resolve(sampleId: String): LoadedSample?

    companion object {
        val EMPTY = SampleBank { null }
        fun of(map: Map<String, LoadedSample>): SampleBank = SampleBank { map[it] }
    }
}
