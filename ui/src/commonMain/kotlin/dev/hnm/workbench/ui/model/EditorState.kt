package dev.hnm.workbench.ui.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.hnm.workbench.core.design.RhythmCapture
import dev.hnm.workbench.core.design.Tap
import dev.hnm.workbench.core.design.Variations
import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.dsp.PatternTiming
import dev.hnm.workbench.core.export.AhapExporter
import dev.hnm.workbench.core.export.KotlinVibrationEffectExporter
import dev.hnm.workbench.core.ir.AudioEvent
import dev.hnm.workbench.core.ir.AudioTrack
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.Envelope
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticEvent
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.OscEvent
import dev.hnm.workbench.core.ir.PatternSerialization
import dev.hnm.workbench.core.ir.Primitive
import dev.hnm.workbench.core.ir.PrimitiveType
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.core.ir.Waveform
import dev.hnm.workbench.core.library.BuiltInPatterns
import dev.hnm.workbench.core.library.PatternLibrary
import dev.hnm.workbench.core.playback.HapticCapabilities
import dev.hnm.workbench.core.playback.PatternPlayer

/** The format shown in the export panel. */
enum class ExportKind { JSON, KOTLIN, AHAP }

/**
 * Compose state holder for the editor. Keeps the current [HapticAudioPattern] plus selection, target
 * capability profile, pattern library, and rhythm capture state. All heavy lifting lives in `core`;
 * the UI is a thin shell. IR is immutable — edits replace the whole pattern.
 */
class EditorState {
    var pattern by mutableStateOf<HapticAudioPattern>(BuiltInPatterns.CONFIRM)
        private set

    var capabilities by mutableStateOf(HapticCapabilities.LRA_FULL)
    var selectedEventIndex by mutableStateOf<Int?>(0)
    var exportKind by mutableStateOf(ExportKind.KOTLIN)
    private var variationSeed = 1

    /**
     * How the current pattern gets felt. Defaults to [PatternPlayer.None] (desktop has no actuator);
     * the Android host injects a real player wired to the device's vibrator + speaker.
     */
    var player: PatternPlayer = PatternPlayer.None

    /** Whether playback is even possible in this host (drives the Play button's enabled state). */
    val canPlay: Boolean get() = player !== PatternPlayer.None

    /** Feel the current pattern on the device, if a real player is wired. */
    fun playCurrent() = player.play(pattern)

    // --- library -----------------------------------------------------------

    val library = PatternLibrary.withBuiltIns()

    fun saveToLibrary(name: String = pattern.name) {
        library.save(pattern.copy(name = name.ifBlank { pattern.name }))
    }

    fun loadFromLibrary(name: String) {
        val p = library.get(name) ?: return
        load(p)
    }

    fun removeFromLibrary(name: String) = library.remove(name)

    // --- rhythm capture ----------------------------------------------------

    /** Taps accumulated during a capture session (time in seconds from first tap). */
    val capturedTaps = mutableStateListOf<Tap>()
    private var captureStartMs: Long = -1L

    fun startOrTap(nowMs: Long) {
        if (captureStartMs < 0L) {
            captureStartMs = nowMs
            capturedTaps.clear()
        }
        val t = (nowMs - captureStartMs) / 1000.0
        capturedTaps.add(Tap(t, pressure = 1.0))
    }

    fun clearCapture() {
        capturedTaps.clear()
        captureStartMs = -1L
    }

    fun loadCapturedRhythm(name: String = "Captured") {
        if (capturedTaps.isEmpty()) return
        load(RhythmCapture.fromTaps(capturedTaps.toList(), name = name))
    }

    // --- timeline helpers --------------------------------------------------

    val durationSeconds: Double get() = PatternTiming.durationSeconds(pattern).coerceAtLeast(0.001)

    /** The first haptic track is the one the editor manipulates directly. */
    val hapticTrack: HapticTrack?
        get() = pattern.tracks.filterIsInstance<HapticTrack>().firstOrNull()

    val hapticEvents: List<HapticEvent> get() = hapticTrack?.events ?: emptyList()

    val audioTrack: AudioTrack?
        get() = pattern.tracks.filterIsInstance<AudioTrack>().firstOrNull()

    val audioEvents: List<AudioEvent> get() = audioTrack?.events ?: emptyList()

    val selectedEvent: HapticEvent?
        get() = selectedEventIndex?.let { hapticEvents.getOrNull(it) }

    var selectedAudioEventIndex by mutableStateOf<Int?>(null)
    val selectedAudioEvent: AudioEvent?
        get() = selectedAudioEventIndex?.let { audioEvents.getOrNull(it) }

    fun select(index: Int?) {
        selectedEventIndex = index?.takeIf { it in hapticEvents.indices }
        selectedAudioEventIndex = null
    }

    fun selectAudio(index: Int?) {
        selectedAudioEventIndex = index?.takeIf { it in audioEvents.indices }
        selectedEventIndex = null
    }

    // --- haptic event edits ------------------------------------------------

    /** Replace the selected haptic event via [transform]. No-op if nothing selected. */
    fun updateSelected(transform: (HapticEvent) -> HapticEvent) {
        val idx = selectedEventIndex ?: return
        val track = hapticTrack ?: return
        val events = track.events.toMutableList()
        if (idx !in events.indices) return
        events[idx] = transform(events[idx])
        replaceHapticTrack(track.copy(events = events))
    }

    fun setSelectedIntensity(value: Double) = updateSelected { e ->
        when (e) {
            is Transient -> e.copy(intensity = value)
            is Continuous -> e.copy(intensity = value)
            is Primitive -> e.copy(scale = value)
        }
    }

    fun setSelectedSharpness(value: Double) = updateSelected { e ->
        when (e) {
            is Transient -> e.copy(sharpness = value)
            is Continuous -> e.copy(sharpness = value)
            is Primitive -> e
        }
    }

    fun setSelectedTime(value: Double) = updateSelected { e ->
        when (e) {
            is Transient -> e.copy(time = value)
            is Continuous -> e.copy(time = value)
            is Primitive -> e.copy(time = value)
        }
    }

    fun setSelectedDuration(value: Double) = updateSelected { e ->
        when (e) {
            is Continuous -> e.copy(duration = value.coerceAtLeast(0.01))
            else -> e
        }
    }

    fun setSelectedEnvelope(
        attack: Double? = null,
        decay: Double? = null,
        sustain: Double? = null,
        release: Double? = null,
    ) = updateSelected { e ->
        if (e !is Continuous) return@updateSelected e
        val env = e.envelope
        e.copy(
            envelope = env.copy(
                attack = attack ?: env.attack,
                decay = decay ?: env.decay,
                sustain = sustain ?: env.sustain,
                release = release ?: env.release,
            ),
        )
    }

    fun addPrimitive(type: PrimitiveType) {
        val track = hapticTrack ?: HapticTrack(id = "h1")
        val time = (hapticEvents.maxOfOrNull { it.time } ?: -0.1) + 0.15
        val events = track.events + Primitive(time = time, type = type, scale = 0.8)
        replaceHapticTrack(track.copy(events = events))
        select(events.lastIndex)
    }

    fun addTransient(time: Double = nextHapticTime(), intensity: Double = 0.8, sharpness: Double = 0.8) {
        val track = hapticTrack ?: HapticTrack(id = "h1")
        val events = track.events + Transient(time = time, intensity = intensity, sharpness = sharpness)
        replaceHapticTrack(track.copy(events = events.sortedBy { it.time }))
        select(events.sortedBy { it.time }.indexOfLast { it.time == time })
    }

    fun addContinuous(
        time: Double = nextHapticTime(),
        duration: Double = 0.2,
        intensity: Double = 0.7,
        sharpness: Double = 0.5,
    ) {
        val track = hapticTrack ?: HapticTrack(id = "h1")
        val ev = Continuous(time = time, duration = duration, intensity = intensity, sharpness = sharpness)
        val events = (track.events + ev).sortedBy { it.time }
        replaceHapticTrack(track.copy(events = events))
        select(events.indexOfFirst { it.time == time && it is Continuous })
    }

    fun deleteSelected() {
        val idx = selectedEventIndex ?: return
        val track = hapticTrack ?: return
        if (idx !in track.events.indices) return
        replaceHapticTrack(track.copy(events = track.events.filterIndexed { i, _ -> i != idx }))
        select(null)
    }

    // --- audio event edits -------------------------------------------------

    fun addOscEvent(
        time: Double = nextAudioTime(),
        duration: Double = 0.1,
        frequencyHz: Double = 440.0,
        gain: Double = 0.7,
        waveform: Waveform = Waveform.SINE,
    ) {
        val track = audioTrack ?: AudioTrack(id = "a1")
        val ev = OscEvent(time = time, duration = duration, waveform = waveform, frequencyHz = frequencyHz, gain = gain)
        val events = (track.events + ev).sortedBy { it.time }
        replaceAudioTrack(track.copy(events = events))
        selectAudio(events.indexOfFirst { it.time == time && it is OscEvent })
    }

    fun setSelectedAudioGain(value: Double) = updateSelectedAudio { ev ->
        when (ev) {
            is OscEvent -> ev.copy(gain = value)
            else -> ev
        }
    }

    fun setSelectedAudioFrequency(value: Double) = updateSelectedAudio { ev ->
        when (ev) {
            is OscEvent -> ev.copy(frequencyHz = value)
            else -> ev
        }
    }

    fun setSelectedAudioDuration(value: Double) = updateSelectedAudio { ev ->
        when (ev) {
            is OscEvent -> ev.copy(duration = value.coerceAtLeast(0.01))
            else -> ev
        }
    }

    fun setSelectedAudioWaveform(value: Waveform) = updateSelectedAudio { ev ->
        when (ev) {
            is OscEvent -> ev.copy(waveform = value)
            else -> ev
        }
    }

    fun deleteSelectedAudio() {
        val idx = selectedAudioEventIndex ?: return
        val track = audioTrack ?: return
        if (idx !in track.events.indices) return
        replaceAudioTrack(track.copy(events = track.events.filterIndexed { i, _ -> i != idx }))
        selectAudio(null)
    }

    private fun updateSelectedAudio(transform: (AudioEvent) -> AudioEvent) {
        val idx = selectedAudioEventIndex ?: return
        val track = audioTrack ?: return
        val events = track.events.toMutableList()
        if (idx !in events.indices) return
        events[idx] = transform(events[idx])
        replaceAudioTrack(track.copy(events = events))
    }

    // --- pattern-level operations ------------------------------------------

    fun mutate() {
        pattern = Variations.mutate(pattern, amount = 0.2, seed = variationSeed++)
        select(0)
    }

    fun reset() {
        pattern = BuiltInPatterns.CONFIRM
        variationSeed = 1
        select(0)
    }

    /** Replace the whole pattern (e.g. when loading a motion primitive or library entry). */
    fun load(newPattern: HapticAudioPattern) {
        pattern = newPattern
        variationSeed = 1
        select(if (hapticEventsOf(newPattern).isNotEmpty()) 0 else null)
        selectedAudioEventIndex = null
    }

    fun exportText(): String = when (exportKind) {
        ExportKind.JSON -> PatternSerialization.encode(pattern)
        ExportKind.KOTLIN -> KotlinVibrationEffectExporter.export(pattern, capabilities)
        ExportKind.AHAP -> AhapExporter.export(pattern)
    }

    val scheduleSummary: String
        get() = DefaultPatternRenderer().scheduleHaptics(pattern, capabilities)
            .joinToString("\n") { "  $it" }
            .ifEmpty { "  (no vibrator)" }

    // --- private helpers ---------------------------------------------------

    private fun nextHapticTime() = (hapticEvents.maxOfOrNull { it.time } ?: -0.1) + 0.15
    private fun nextAudioTime() = (audioEvents.maxOfOrNull { it.time } ?: -0.05) + 0.1

    private fun hapticEventsOf(p: HapticAudioPattern) =
        p.tracks.filterIsInstance<HapticTrack>().firstOrNull()?.events ?: emptyList()

    private fun replaceHapticTrack(updated: HapticTrack) {
        val tracks = pattern.tracks.map { if (it.id == updated.id) updated else it }
        pattern = pattern.copy(
            tracks = if (tracks.any { it.id == updated.id }) tracks else tracks + updated,
        )
    }

    private fun replaceAudioTrack(updated: AudioTrack) {
        val tracks = pattern.tracks.map { if (it.id == updated.id) updated else it }
        pattern = pattern.copy(
            tracks = if (tracks.any { it.id == updated.id }) tracks else tracks + updated,
        )
    }
}
