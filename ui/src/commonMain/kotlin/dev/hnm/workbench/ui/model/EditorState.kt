package dev.hnm.workbench.ui.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.hnm.workbench.core.design.HybridPatternGenerator
import dev.hnm.workbench.core.design.PatternGenerator
import dev.hnm.workbench.core.design.RhythmCapture
import dev.hnm.workbench.core.design.Tap
import dev.hnm.workbench.core.design.Variations
import dev.hnm.workbench.core.device.DeviceProfile
import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.dsp.PatternTiming
import dev.hnm.workbench.core.export.AhapExporter
import dev.hnm.workbench.core.export.AhapImporter
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
import dev.hnm.workbench.core.playback.ChromePlayer
import dev.hnm.workbench.core.playback.HapticCapabilities
import dev.hnm.workbench.core.playback.InterfaceFeelLevel
import dev.hnm.workbench.core.playback.PatternPlayer
import kotlinx.coroutines.delay

/** The format shown in the export panel. */
enum class ExportKind { JSON, KOTLIN, AHAP }

/**
 * Which editing surface the Editor shows (UX brief §5, onboarding beat 5). `VIBE` is the default and
 * hides technical tools (Edit-as-JSON) that most people never need; `TECHNICAL` reveals them. Chosen
 * once during onboarding, changeable any time — this is a display gate, not a different data model.
 */
enum class WorkspaceMode { VIBE, TECHNICAL }

/**
 * Compose state holder for the editor. Keeps the current [HapticAudioPattern] plus selection, target
 * capability profile, pattern library, and rhythm capture state. All heavy lifting lives in `core`;
 * the UI is a thin shell. IR is immutable — edits replace the whole pattern.
 */
class EditorState {
    var pattern by mutableStateOf<HapticAudioPattern>(BuiltInPatterns.CONFIRM)
        private set

    private val capabilitiesState = mutableStateOf(HapticCapabilities.LRA_FULL)

    /** The target device profile. Setting this also updates [chrome]'s capability gate. */
    var capabilities: HapticCapabilities
        get() = capabilitiesState.value
        set(value) {
            capabilitiesState.value = value
            chrome.hasAmplitudeControl = value.hasAmplitudeControl
        }

    /**
     * The real device currently being simulated, if the user picked one from the device database
     * (as opposed to one of the four abstract capability tiers). Shared across the Editor's
     * [dev.hnm.workbench.ui.components.CapabilityPanel] and the Device tab's hero card so both
     * reflect the same selection instead of keeping independent local UI state.
     */
    var selectedDevice by mutableStateOf<DeviceProfile?>(null)

    /** Simulate [device]'s real capabilities (resonant frequency, Q, primitives, etc). */
    fun selectDevice(device: DeviceProfile) {
        selectedDevice = device
        capabilities = device.toCapabilities()
    }

    /** Switch to one of the four abstract capability tiers, clearing any simulated real device. */
    fun selectCapabilityTier(tier: HapticCapabilities) {
        selectedDevice = null
        capabilities = tier
    }

    var selectedEventIndex by mutableStateOf<Int?>(0)
    var exportKind by mutableStateOf(ExportKind.KOTLIN)
    var workspaceMode by mutableStateOf(WorkspaceMode.VIBE)
    private var variationSeed = 1

    // --- undo / redo (Phase 4: Editor top bar) ------------------------------

    private val undoStack = mutableStateListOf<HapticAudioPattern>()
    private val redoStack = mutableStateListOf<HapticAudioPattern>()

    /** A pre-drag snapshot held while a coalesced edit (e.g. a slider drag) is in progress. */
    private var historyBaseline: HapticAudioPattern? = null

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    private fun pushHistory(entry: HapticAudioPattern) {
        undoStack.add(entry)
        if (undoStack.size > MAX_HISTORY) undoStack.removeAt(0)
        redoStack.clear()
    }

    /**
     * Sets [pattern], recording undo history. Discrete edits (add/delete/load/rename) checkpoint
     * immediately; [coalesce] = true folds a run of continuous changes (a slider drag) into a single
     * undo step — call [commitEdit] when the drag ends (e.g. `onValueChangeFinished`).
     */
    private fun setPattern(newPattern: HapticAudioPattern, coalesce: Boolean = false) {
        if (newPattern == pattern) return
        if (coalesce) {
            if (historyBaseline == null) historyBaseline = pattern
        } else {
            commitEdit()
            pushHistory(pattern)
        }
        pattern = newPattern
    }

    /** Ends an in-progress coalesced drag, turning it into one undo step. No-op if nothing pending. */
    fun commitEdit() {
        val baseline = historyBaseline ?: return
        historyBaseline = null
        if (baseline != pattern) pushHistory(baseline)
    }

    fun undo() {
        commitEdit()
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.add(pattern)
        pattern = previous
        clampSelectionToCurrentPattern()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.add(pattern)
        pattern = next
        clampSelectionToCurrentPattern()
    }

    /**
     * Keeps the current selection where it is across undo/redo when the index is still valid — e.g.
     * undoing a slider drag should keep the same event selected, not jump the inspector back to event 0.
     */
    private fun clampSelectionToCurrentPattern() {
        selectedEventIndex = selectedEventIndex?.takeIf { it in hapticEvents.indices }
        selectedAudioEventIndex = selectedAudioEventIndex?.takeIf { it in audioEvents.indices }
    }

    /** Renames the current pattern in place (Editor top-bar rename). Undoable like any other edit. */
    fun renamePattern(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed == pattern.name) return
        setPattern(pattern.copy(name = trimmed))
    }

    /**
     * How the current pattern gets felt. Defaults to [PatternPlayer.None] (desktop has no actuator);
     * the Android host injects a real player wired to the device's vibrator + speaker. A delegating
     * [PatternPlayer] (not a direct reference) so [chrome] keeps working after this is reassigned.
     */
    var player: PatternPlayer = PatternPlayer.None

    /** Whether playback is even possible in this host (drives the Play button's enabled state). */
    val canPlay: Boolean get() = player !== PatternPlayer.None

    /** Feel the current pattern on the device, if a real player is wired. */
    fun playCurrent() = player.play(pattern)

    // --- interface feel (UX brief §3.3) -------------------------------------

    /**
     * Gates and plays the app's own chrome.* feedback (tab switches, snaps, boundaries — never content).
     * Delegates through the live [player] so reassigning it (e.g. the Android host wiring a real
     * actuator after construction) doesn't require rebuilding this.
     */
    val chrome: ChromePlayer = ChromePlayer(
        player = PatternPlayer { p -> player.play(p) },
        hasAmplitudeControl = capabilities.hasAmplitudeControl,
    )

    var interfaceFeelLevel: InterfaceFeelLevel
        get() = chrome.level
        set(value) { chrome.level = value }

    /**
     * Play the current pattern with the chrome priority latch held for its approximate duration, so
     * ambient interface feedback never fires over real content. Desktop/no-actuator hosts still get the
     * latch (harmless — [chrome] would no-op there anyway since nothing calls it mid-playback).
     */
    suspend fun playCurrentLatched() {
        chrome.busy = true
        try {
            playCurrent()
            delay((durationSeconds * 1000).toLong().coerceAtLeast(1))
        } finally {
            chrome.busy = false
        }
    }

    // --- AI assistant ------------------------------------------------------

    /**
     * The intent→pattern engine. Defaults to the hybrid generator (on-device, offline). A host can
     * swap in a generator with a cloud backend wired for richer free-text understanding.
     */
    var generator: PatternGenerator = HybridPatternGenerator()

    /** The assistant's last plain-language explanation (what it made and why). Null until first use. */
    var assistantMessage by mutableStateOf<String?>(null)
        private set

    var isGenerating by mutableStateOf(false)
        private set

    /** The most recent prompt, kept so the UI text field survives recomposition. */
    var lastPrompt by mutableStateOf("")

    /**
     * Generate (or edit) from a natural-language prompt. Routes through [generator]: if the prompt
     * reads as an edit ("softer", "longer") it adjusts the current pattern; otherwise it creates one.
     * Auto-plays the result when a real actuator is wired so you immediately feel what you described.
     */
    suspend fun generate(prompt: String) {
        if (prompt.isBlank()) return
        isGenerating = true
        try {
            val result = generator.generate(prompt, pattern)
            load(result.pattern)
            assistantMessage = result.explanation
            lastPrompt = prompt
            if (canPlay) playCurrent()
        } catch (t: Throwable) {
            assistantMessage = "Couldn't generate that: ${t.message ?: "unknown error"}. Try simpler words like \"sharp tap\" or \"soft buzz\"."
        } finally {
            isGenerating = false
        }
    }

    // --- import ------------------------------------------------------------

    /** Result of the last AHAP import attempt (success summary or error). Null until first use. */
    var importMessage by mutableStateOf<String?>(null)
        private set

    /**
     * Import an Apple AHAP (Haptic and Audio Pattern) JSON document into the editor — the "borrow from
     * the ecosystem" path. Loads on success and reports a summary; never throws.
     */
    fun importAhap(text: String): Boolean {
        if (text.isBlank()) { importMessage = "Paste AHAP JSON first."; return false }
        val imported = AhapImporter.importOrNull(text)
        if (imported == null) {
            importMessage = "That doesn't look like valid AHAP JSON."
            return false
        }
        load(imported)
        importMessage = "Imported \"${imported.name}\" — ${hapticEvents.size} haptic events."
        return true
    }

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

    /**
     * Replace the selected haptic event via [transform]. No-op if nothing selected. Callers are all
     * slider-driven (intensity/sharpness/time/duration/envelope), so edits coalesce into one undo step
     * per drag — see [commitEdit].
     */
    fun updateSelected(transform: (HapticEvent) -> HapticEvent) {
        val idx = selectedEventIndex ?: return
        val track = hapticTrack ?: return
        val events = track.events.toMutableList()
        if (idx !in events.indices) return
        events[idx] = transform(events[idx])
        replaceHapticTrack(track.copy(events = events), coalesce = true)
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

    // A discrete pick (dropdown), not a drag — checkpoint immediately rather than coalescing.
    fun setSelectedAudioWaveform(value: Waveform) = updateSelectedAudio(coalesce = false) { ev ->
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

    private fun updateSelectedAudio(coalesce: Boolean = true, transform: (AudioEvent) -> AudioEvent) {
        val idx = selectedAudioEventIndex ?: return
        val track = audioTrack ?: return
        val events = track.events.toMutableList()
        if (idx !in events.indices) return
        events[idx] = transform(events[idx])
        replaceAudioTrack(track.copy(events = events), coalesce = coalesce)
    }

    // --- pattern-level operations ------------------------------------------

    fun mutate() {
        setPattern(Variations.mutate(pattern, amount = 0.2, seed = variationSeed++))
        select(0)
    }

    fun reset() {
        setPattern(BuiltInPatterns.CONFIRM)
        variationSeed = 1
        select(0)
    }

    /** Replace the whole pattern (e.g. when loading a motion primitive or library entry). */
    fun load(newPattern: HapticAudioPattern) {
        setPattern(newPattern)
        variationSeed = 1
        select(if (hapticEventsOf(newPattern).isNotEmpty()) 0 else null)
        selectedAudioEventIndex = null
    }

    // --- edit as JSON (Phase 5: technical workspace) ------------------------

    /** Error from the last "Edit as JSON" apply attempt; null once an apply succeeds. */
    var jsonEditError by mutableStateOf<String?>(null)
        private set

    /** Parses [text] as the pattern's JSON and loads it on success. Never throws. */
    fun applyPatternJson(text: String): Boolean {
        return try {
            load(PatternSerialization.decode(text))
            jsonEditError = null
            true
        } catch (t: Throwable) {
            jsonEditError = "Couldn't parse that JSON: ${t.message ?: "unknown error"}"
            false
        }
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

    private fun replaceHapticTrack(updated: HapticTrack, coalesce: Boolean = false) {
        val tracks = pattern.tracks.map { if (it.id == updated.id) updated else it }
        setPattern(
            pattern.copy(tracks = if (tracks.any { it.id == updated.id }) tracks else tracks + updated),
            coalesce = coalesce,
        )
    }

    private fun replaceAudioTrack(updated: AudioTrack, coalesce: Boolean = false) {
        val tracks = pattern.tracks.map { if (it.id == updated.id) updated else it }
        setPattern(
            pattern.copy(tracks = if (tracks.any { it.id == updated.id }) tracks else tracks + updated),
            coalesce = coalesce,
        )
    }

    private companion object {
        const val MAX_HISTORY = 50
    }
}
