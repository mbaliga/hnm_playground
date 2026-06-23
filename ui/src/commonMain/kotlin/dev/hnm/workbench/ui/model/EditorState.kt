package dev.hnm.workbench.ui.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.hnm.workbench.core.design.Variations
import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.dsp.PatternTiming
import dev.hnm.workbench.core.export.AhapExporter
import dev.hnm.workbench.core.export.KotlinVibrationEffectExporter
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticEvent
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.PatternSerialization
import dev.hnm.workbench.core.ir.Primitive
import dev.hnm.workbench.core.ir.PrimitiveType
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.core.library.BuiltInPatterns
import dev.hnm.workbench.core.playback.HapticCapabilities

/** The format shown in the export panel. */
enum class ExportKind { JSON, KOTLIN, AHAP }

/**
 * Compose state holder for the editor. Keeps the current [HapticAudioPattern] plus selection and the
 * target capability profile, and exposes pure edit operations that produce a new pattern (the IR is
 * immutable; edits replace it). All heavy lifting lives in `core`, so the UI stays a thin shell.
 */
class EditorState {
    var pattern by mutableStateOf<HapticAudioPattern>(BuiltInPatterns.CONFIRM)
        private set

    var capabilities by mutableStateOf(HapticCapabilities.LRA_FULL)
    var selectedEventIndex by mutableStateOf<Int?>(0)
    var exportKind by mutableStateOf(ExportKind.KOTLIN)
    private var variationSeed = 1

    val durationSeconds: Double get() = (PatternTiming.durationSeconds(pattern)).coerceAtLeast(0.001)

    /** The first haptic track is the one the editor manipulates directly. */
    val hapticTrack: HapticTrack?
        get() = pattern.tracks.filterIsInstance<HapticTrack>().firstOrNull()

    val hapticEvents: List<HapticEvent> get() = hapticTrack?.events ?: emptyList()

    val selectedEvent: HapticEvent?
        get() = selectedEventIndex?.let { hapticEvents.getOrNull(it) }

    fun select(index: Int?) {
        selectedEventIndex = index?.takeIf { it in hapticEvents.indices }
    }

    /** Replace the selected event with [transform] applied to it. No-op if nothing selected. */
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
            is Primitive -> e // primitives have no sharpness knob
        }
    }

    fun addPrimitive(type: PrimitiveType) {
        val track = hapticTrack ?: HapticTrack(id = "h1")
        val time = (hapticEvents.maxOfOrNull { it.time } ?: -0.1) + 0.15
        val events = track.events + Primitive(time = time, type = type, scale = 0.8)
        replaceHapticTrack(track.copy(events = events))
        select(events.lastIndex)
    }

    fun deleteSelected() {
        val idx = selectedEventIndex ?: return
        val track = hapticTrack ?: return
        if (idx !in track.events.indices) return
        replaceHapticTrack(track.copy(events = track.events.filterIndexed { i, _ -> i != idx }))
        select(null)
    }

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
    }

    private fun hapticEventsOf(p: HapticAudioPattern) =
        p.tracks.filterIsInstance<HapticTrack>().firstOrNull()?.events ?: emptyList()

    fun exportText(): String = when (exportKind) {
        ExportKind.JSON -> PatternSerialization.encode(pattern)
        ExportKind.KOTLIN -> KotlinVibrationEffectExporter.export(pattern, capabilities)
        ExportKind.AHAP -> AhapExporter.export(pattern)
    }

    val scheduleSummary: String
        get() = DefaultPatternRenderer().scheduleHaptics(pattern, capabilities)
            .joinToString("\n") { "  $it" }
            .ifEmpty { "  (no vibrator)" }

    private fun replaceHapticTrack(updated: HapticTrack) {
        val tracks = pattern.tracks.map { if (it.id == updated.id) updated else it }
        pattern = pattern.copy(tracks = if (tracks.any { it.id == updated.id }) tracks else tracks + updated)
    }
}
