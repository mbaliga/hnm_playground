package dev.hnm.workbench.core.companion

import dev.hnm.workbench.core.ir.*

/** Bounds apply before rendering, persistence or wire acceptance, not after allocation. */
object PatternSafety {
    const val MAX_SECONDS = 30.0
    const val MAX_BYTES = 65_536

    fun validate(pattern: HapticAudioPattern) {
        require(pattern.version == 1) { "Unsupported pattern version ${pattern.version}" }
        require(pattern.name.isNotBlank() && pattern.name.length <= 160) { "Use a name of 1–160 characters" }
        require(pattern.tracks.size <= 16) { "Too many tracks (maximum 16)" }
        require(pattern.tracks.map { it.id }.distinct().size == pattern.tracks.size) { "Track IDs must be unique" }
        require(pattern.metadata.size <= 32 && pattern.metadata.all { it.key.length <= 128 && it.value.length <= 2048 }) { "Metadata is too large" }
        fun number(v: Double, min: Double, max: Double, label: String) {
            require(v.isFinite() && v in min..max) { "$label must be between $min and $max" }
        }
        fun time(v: Double) = number(v, 0.0, MAX_SECONDS, "Time")
        fun level(v: Double) = number(v, 0.0, 1.0, "Level")
        fun envelope(e: Envelope) { time(e.attack); time(e.decay); time(e.release); level(e.sustain) }
        var count = 0
        pattern.tracks.forEach { track ->
            require(track.id.length in 1..128 && track.name.length <= 160) { "Invalid track name or ID" }
            val curves = when (track) {
                is HapticTrack -> {
                    count += track.events.size
                    track.events.forEach { e ->
                        time(e.time)
                        when(e) {
                            is Transient -> { level(e.intensity); level(e.sharpness) }
                            is Primitive -> level(e.scale)
                            is Continuous -> { time(e.duration); time(e.time + e.duration); level(e.intensity); level(e.sharpness); envelope(e.envelope) }
                        }
                    }
                    track.curves
                }
                is AudioTrack -> {
                    count += track.events.size
                    track.events.forEach { e ->
                        time(e.time)
                        when(e) {
                            is OscEvent -> {
                                time(e.duration); time(e.time + e.duration); level(e.gain); envelope(e.envelope)
                                number(e.frequencyHz, if (e.waveform == Waveform.NOISE) 0.0 else 1.0, 24_000.0, "Frequency")
                                e.filter?.let { number(it.cutoffHz, 1.0, 24_000.0, "Cutoff"); number(it.resonance, 0.0, 20.0, "Resonance") }
                            }
                            is SampleEvent -> { level(e.gain); number(e.pitchShiftSemitones, -48.0, 48.0, "Pitch"); envelope(e.envelope) }
                        }
                    }
                    track.curves
                }
            }
            require(curves.size <= 8) { "Too many curves" }
            curves.forEach { curve ->
                require(curve.points.size <= 256) { "Too many curve points" }
                require(curve.points.zipWithNext().all { (a,b) -> a.time <= b.time }) { "Curve times must be ordered" }
                val bounds=when(curve.parameter) {
                    CurveParam.HAPTIC_INTENSITY, CurveParam.HAPTIC_SHARPNESS, CurveParam.AUDIO_GAIN -> 0.0..1.0
                    CurveParam.AUDIO_PITCH -> -48.0..48.0
                    CurveParam.AUDIO_FILTER_CUTOFF -> 1.0..24_000.0
                }
                curve.points.forEach { time(it.time); number(it.value, bounds.start, bounds.endInclusive, "${curve.parameter} curve value") }
            }
        }
        require(duration(pattern) <= MAX_SECONDS && dev.hnm.workbench.core.dsp.PatternTiming.durationSeconds(pattern) <= MAX_SECONDS) { "Effect including release tails exceeds 30 seconds" }
        require(count <= 512) { "Too many events (maximum 512)" }
        require(pattern.couplings.size <= 16) { "Too many couplings" }
        pattern.couplings.forEach {
            require(pattern.tracks.any { t -> t.id == it.sourceTrackId } && pattern.tracks.any { t -> t.id == it.targetTrackId }) { "Coupling refers to a missing track" }
            number(it.params.gain, 0.0, 4.0, "Coupling gain")
            number(it.params.attackMs, 0.0, 30_000.0, "Attack")
            number(it.params.releaseMs, 0.0, 30_000.0, "Release")
        }
    }

    fun duration(pattern: HapticAudioPattern): Double = pattern.tracks.filterNot { it.muted }.maxOfOrNull { track ->
        when (track) {
            is HapticTrack -> track.events.maxOfOrNull { it.time + when(it) { is Continuous -> it.duration + it.envelope.release; else -> 0.35 } } ?: 0.0
            is AudioTrack -> track.events.maxOfOrNull { it.time + when(it) { is OscEvent -> it.duration + it.envelope.release; is SampleEvent -> 0.5 + it.envelope.release } } ?: 0.0
        }
    } ?: 0.0
}
