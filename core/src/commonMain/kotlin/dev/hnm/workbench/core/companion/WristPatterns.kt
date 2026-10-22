package dev.hnm.workbench.core.companion

import dev.hnm.workbench.core.ir.*

object WristPatterns {
    fun doublePulse(gapMs: Int = 240, strength: Double = 0.6): HapticAudioPattern = fromTaps(
        listOf(0L, gapMs.toLong().coerceIn(80, 600)), strength, "Double pulse",
    )

    fun fromTaps(tapsMs: List<Long>, strength: Double = 0.6, name: String = "My rhythm"): HapticAudioPattern {
        require(tapsMs.size in 1..6) { "Record between 1 and 6 taps" }
        require(tapsMs.zipWithNext().all { (a,b) -> b >= a }) { "Taps must be ordered" }
        require(tapsMs.last() - tapsMs.first() <= 4_000) { "Keep the rhythm within 4 seconds" }
        return HapticAudioPattern(name = name, tracks = listOf(HapticTrack("wrist", events = tapsMs.map {
            Transient((it - tapsMs.first()) / 1000.0, strength.coerceIn(0.1, 1.0), 0.6)
        })))
    }

    fun peakStrength(pattern: HapticAudioPattern): Double = pattern.tracks.filterIsInstance<HapticTrack>()
        .flatMap { it.events }.maxOfOrNull { when (it) {
            is Transient -> it.intensity; is Continuous -> it.intensity; is Primitive -> it.scale
        } } ?: 0.6

    /** Onset spacing can be edited safely for uncoupled haptics and synthesized audio. */
    fun canTune(pattern: HapticAudioPattern): Boolean = pattern.couplings.isEmpty() &&
        pattern.tracks.any { it is HapticTrack } && pattern.tracks.all { track ->
            track is HapticTrack || (track is AudioTrack && track.events.all { it is OscEvent })
        }

    fun hasHaptics(pattern: HapticAudioPattern): Boolean = pattern.tracks.filterIsInstance<HapticTrack>()
        .any { !it.muted && it.events.any { e -> when(e) { is Transient -> e.intensity>0; is Continuous -> e.intensity>0; is Primitive -> e.scale>0 } } } || pattern.couplings.any { it.mode == CouplingMode.AUDIO_DRIVES_HAPTICS }

    /** Make a simple original tone at each authored motor onset on the receiving surface. */
    fun addPairedSound(pattern: HapticAudioPattern): HapticAudioPattern {
        require(canTune(pattern) && !hasSound(pattern)) { "Use an uncoupled haptic effect without sound" }
        val onsets = pattern.tracks.filterIsInstance<HapticTrack>().filterNot { it.muted }
            .flatMap { it.events }.map { it.time }.distinct().sorted()
        require(onsets.isNotEmpty()) { "Add a pulse first" }
        var id="wrist-sound"
        while(pattern.tracks.any { it.id==id }) id+="-1"
        return pattern.copy(tracks=pattern.tracks+AudioTrack(id, name="Paired tone", events=onsets.map {
            OscEvent(time=it,duration=.025,waveform=Waveform.SINE,frequencyHz=700.0,gain=.12,
                envelope=Envelope(attack=.002,decay=.01,sustain=.2,release=.025))
        })).also(PatternSafety::validate)
    }

    fun soundLevel(pattern: HapticAudioPattern): Double = pattern.tracks.filterIsInstance<AudioTrack>()
        .filterNot { it.muted }.flatMap { it.events }.maxOfOrNull { when(it) { is OscEvent -> it.gain; is SampleEvent -> it.gain } } ?: 0.0

    fun withSoundLevel(pattern: HapticAudioPattern, gain: Double): HapticAudioPattern {
        require(gain.isFinite() && gain in 0.0..1.0)
        val peak=soundLevel(pattern)
        val factor=if(peak>0)gain/peak else 0.0
        return pattern.copy(tracks=pattern.tracks.map { t -> if(t is AudioTrack) t.copy(events=t.events.map {
            when(it) { is OscEvent -> it.copy(gain=(it.gain*factor).coerceIn(0.0,1.0)); is SampleEvent -> it.copy(gain=(it.gain*factor).coerceIn(0.0,1.0)) }
        }) else t }).also(PatternSafety::validate)
    }

    fun hasSound(pattern: HapticAudioPattern): Boolean = pattern.tracks.filterIsInstance<AudioTrack>()
        .any { !it.muted && it.events.any { e -> when(e) { is OscEvent -> e.gain > 0; is SampleEvent -> e.gain > 0 } } } ||
        pattern.couplings.any { it.mode == CouplingMode.HAPTICS_DRIVES_AUDIO }

    /** A preview projection: keep the authored document intact while muting its audio tracks. */
    fun hapticsOnly(pattern: HapticAudioPattern): HapticAudioPattern = pattern.copy(
        tracks = pattern.tracks.filterIsInstance<HapticTrack>(), couplings = emptyList(),
    )

    /** Keep both modalities' event onsets together; preserve tone pitch/decay and pulse dynamics. */
    fun tune(pattern: HapticAudioPattern, spacing: Double, strength: Double): HapticAudioPattern {
        require(canTune(pattern)) { "Use Studio to edit sampled audio or coupled effects" }
        require(spacing in 0.5..2.0 && strength in 0.1..1.0)
        val peak = peakStrength(pattern)
        val factor = if (peak > 0.0) strength / peak else 0.0
        return pattern.copy(tracks = pattern.tracks.map { track -> when(track) {
            is HapticTrack -> track.copy(events = track.events.map { e -> when(e) {
                is Transient -> e.copy(time=e.time*spacing, intensity=(e.intensity*factor).coerceIn(0.0,1.0))
                is Primitive -> e.copy(time=e.time*spacing, scale=(e.scale*factor).coerceIn(0.0,1.0))
                is Continuous -> e.copy(time=e.time*spacing, intensity=(e.intensity*factor).coerceIn(0.0,1.0))
            } }, curves = track.curves.map { c -> c.copy(points=c.points.map { it.copy(time=it.time*spacing) }) })
            is AudioTrack -> track.copy(events=track.events.map { e ->
                (e as OscEvent).copy(time=e.time*spacing)
            }, curves=track.curves.map { c -> c.copy(points=c.points.map { it.copy(time=it.time*spacing) }) })
        } }).also(PatternSafety::validate)
    }

}

/** Deterministic text navigation: crossings are detected even when frames skip past a match. */
class SearchCueIndex(val text: String, query: String) {
    val matches: List<Int> = if (query.isBlank()) emptyList() else buildList {
        var from = 0
        while (from < text.length) {
            val index = text.indexOf(query, from, ignoreCase = true)
            if (index < 0) break
            add(index)
            from = index + query.length
        }
    }
    fun crossed(from: Int, to: Int): Boolean = when {
        to > from -> matches.any { it > from && it <= to }
        to < from -> matches.any { it < from && it >= to }
        else -> false
    }
    fun next(position: Int): Int? = matches.firstOrNull { it > position } ?: matches.firstOrNull()
    fun previous(position: Int): Int? = matches.lastOrNull { it < position } ?: matches.lastOrNull()
    fun excerpt(position: Int): String = text.substring(position.coerceIn(0,text.length), (position+110).coerceIn(0,text.length))
}
