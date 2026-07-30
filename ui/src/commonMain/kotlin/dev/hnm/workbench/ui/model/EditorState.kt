package dev.hnm.workbench.ui.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.hnm.workbench.core.design.OptInPatternGenerator
import dev.hnm.workbench.core.design.PatternGenerator
import dev.hnm.workbench.core.design.RhythmCapture
import dev.hnm.workbench.core.design.Tap
import dev.hnm.workbench.core.design.Variations
import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.dsp.PatternTiming
import dev.hnm.workbench.core.export.AhapExporter
import dev.hnm.workbench.core.export.AhapImporter
import dev.hnm.workbench.core.export.ClackpadExporter
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
import dev.hnm.workbench.core.library.InMemoryLibraryRepository
import dev.hnm.workbench.core.library.LibraryRepository
import dev.hnm.workbench.core.library.PatternLibrary
import dev.hnm.workbench.core.library.RoleSeedPatterns
import dev.hnm.workbench.core.playback.HapticCapabilities
import dev.hnm.workbench.core.playback.PatternPlayer
import dev.hnm.workbench.core.settings.InMemorySettingsStore
import dev.hnm.workbench.core.settings.OnboardingProgress
import dev.hnm.workbench.core.settings.SettingsStore

/**
 * The format shown in the export panel. [CLACKPAD] is only ever entered via [EditorState.beginHandoff]
 * (a Clackpad-initiated session, `docs/CLACKPAD_CONTRACT.md` §1) — it's deliberately excluded from the
 * general format-chip picker ([dev.hnm.workbench.ui.components.ExportPanel]'s normal row), since without
 * a role a `clackpad-haptic` export is meaningless.
 */
enum class ExportKind { JSON, KOTLIN, AHAP, CLACKPAD }

/** Placeholder provenance for the in-editor Clackpad export *preview* only — see [EditorState.exportText]. */
private const val PREVIEW_SOURCE_VERSION = "0.0.0"
private const val PREVIEW_SOURCE_BUILD = "dev"

/**
 * Compose state holder for the editor. Keeps the current [HapticAudioPattern] plus selection, target
 * capability profile, pattern library, and rhythm capture state. All heavy lifting lives in `core`;
 * the UI is a thin shell. IR is immutable — edits replace the whole pattern.
 */
class EditorState(val settings: SettingsStore = InMemorySettingsStore()) {
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

    /**
     * Feel the current pattern on the device, if a real player is wired. Only counts as "felt one" for
     * [onboarding] when there's an actual actuator behind [player] — on a host with none (desktop's
     * [PatternPlayer.None]) nothing was actually felt, just previewed silently.
     */
    fun playCurrent() {
        player.play(pattern)
        if (canPlay) markFelt()
    }

    // --- onboarding / beginner ladder ---------------------------------------

    /**
     * A Compose-observable mirror of [settings]'s onboarding progress. [SettingsStore] itself lives in
     * `core` and isn't snapshot-state-backed, so the `mark...` calls below re-read it into this
     * `mutableStateOf` after every mutation — the same "bump after mutating a non-observable store"
     * pattern [libraryRepository]'s callers use via their own `epoch` counters.
     */
    var onboarding: OnboardingProgress by mutableStateOf(settings.onboarding)
        private set

    private fun markFelt() { settings.markFeltOne(); refreshOnboarding() }
    private fun markMade() { settings.markMadeOne(); refreshOnboarding() }
    private fun markChanged() { settings.markChangedOne(); refreshOnboarding() }

    /** Records that the user shipped a pattern (exported it, or sent it to a real integration like Clackpad). */
    fun markShipped() { settings.markShippedOne(); refreshOnboarding() }

    /** Instant-apply: clears the Feel/Make/Change/Ship checklist in [settings] (the Settings sheet's "reset" action). */
    fun resetOnboarding() { settings.resetOnboarding(); refreshOnboarding() }

    private fun refreshOnboarding() { onboarding = settings.onboarding }

    // --- AI assistant --------------------------------------------------------------------------

    /**
     * A Compose-observable mirror of [settings]'s opt-in flag — same "bump after mutating a
     * non-observable store" pattern [onboarding] uses. Defaults to `false`, matching
     * [SettingsStore.cloudAssistantEnabled]'s own default; [setCloudAssistantEnabled] is the only way
     * this app ever flips it to `true`. Purely for the Settings toggle to render correctly — [generator]
     * below reads [settings] directly (not this mirror), fresh on every generate call, so the gate is
     * enforced whether or not any Compose UI happens to be observing it.
     */
    private val cloudAssistantEnabledState = mutableStateOf(settings.cloudAssistantEnabled)
    val cloudAssistantEnabled: Boolean get() = cloudAssistantEnabledState.value

    /** Instant-apply: the Settings sheet's cloud-assistant toggle. No separate "apply" step. */
    fun setCloudAssistantEnabled(enabled: Boolean) {
        settings.setCloudAssistantEnabled(enabled)
        cloudAssistantEnabledState.value = settings.cloudAssistantEnabled
    }

    /**
     * The intent→pattern engine the Assistant panel drives (see [generate]). [OptInPatternGenerator] is
     * the strict opt-in gate: the on-device generator always runs; a cloud fallback only ever runs when
     * [settings].[SettingsStore.cloudAssistantEnabled] is `true` at the moment of the call — full stop
     * otherwise, not even attempted. A host wanting a real cloud backend wires it in as this generator's
     * `cloud` parameter (e.g. `generator = OptInPatternGenerator(settings, cloud = MyCloudGenerator())`);
     * nothing does that today, so the cloud path is inert even when the toggle is on.
     */
    var generator: PatternGenerator = OptInPatternGenerator(settings = settings)

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
            markMade()
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

    // --- Clackpad hand-off ---------------------------------------------------------------------

    /**
     * Non-null exactly when this editor session was opened by Clackpad
     * (`docs/CLACKPAD_CONTRACT.md` §1) for one of its five feedback roles — see [beginHandoff]. Read by
     * [dev.hnm.workbench.ui.components.ExportPanel] to switch its primary action from the general
     * format-chip menu to a single "Send to Clackpad" button.
     */
    var handoffRole by mutableStateOf<String?>(null)
        private set

    /**
     * Enter a Clackpad hand-off session for [role]: pre-seeds a role-appropriate starting pattern
     * (instead of the default [BuiltInPatterns.CONFIRM]), marks [handoffRole], and switches
     * [exportKind] to [ExportKind.CLACKPAD] so the export preview already shows the shape that will
     * actually be shipped. The host (e.g. Android's `WorkbenchActivity`) calls this once, from the
     * launch intent's validated role extra, before the editor is first shown.
     */
    fun beginHandoff(role: String) {
        handoffRole = role
        load(RoleSeedPatterns.seedFor(role))
        exportKind = ExportKind.CLACKPAD
    }

    /** The `clackpad-haptic` JSON for the current pattern under [role] — see [ClackpadExporter]. */
    fun exportClackpadJson(role: String, sourceVersion: String, sourceBuild: String): String =
        ClackpadExporter.export(pattern, role, capabilities, sourceVersion, sourceBuild)

    // --- library -----------------------------------------------------------

    val library = PatternLibrary.withBuiltIns()

    /**
     * The library-browsing layer (favorites/recents/tags) over [library] — same underlying store
     * ([LibraryRepository.library] is this exact [library] instance, not a copy. The library UI
     * ([dev.hnm.workbench.ui.components.LibraryPanel], [dev.hnm.workbench.ui.components.LibraryListPanel])
     * talks to this, not the bare [library], so a star/recent/tag survives a save/remove round trip.
     */
    val libraryRepository: LibraryRepository = InMemoryLibraryRepository(library = library)

    fun saveToLibrary(name: String = pattern.name) {
        libraryRepository.save(pattern.copy(name = name.ifBlank { pattern.name }))
    }

    fun loadFromLibrary(name: String) {
        val p = libraryRepository.get(name) ?: return
        load(p)
    }

    fun removeFromLibrary(name: String) = libraryRepository.remove(name)

    /**
     * Load [name] from the library, feel it on the actuator, and record it as a recent play — the
     * library UI's "tap a row" affordance ([dev.hnm.workbench.ui.components.LibraryListPanel]/
     * [dev.hnm.workbench.ui.components.LibraryPanel]). A no-op play on a host with no actuator, same as
     * every other Play affordance in this app.
     */
    fun playFromLibrary(name: String) {
        loadFromLibrary(name)
        libraryRepository.recordPlay(name)
        playCurrent()
    }

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
        markChanged()
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
        // Preview only — a placeholder source/build, since the real provenance (the host app's actual
        // version/build) is only known at ship time and is supplied directly to exportClackpadJson() by
        // whichever host wires up the "Send to Clackpad" button (see WorkbenchApp's onShipToClackpad).
        ExportKind.CLACKPAD -> handoffRole?.let {
            exportClackpadJson(it, sourceVersion = PREVIEW_SOURCE_VERSION, sourceBuild = PREVIEW_SOURCE_BUILD)
        } ?: PatternSerialization.encode(pattern)
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
