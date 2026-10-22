package dev.hnm.workbench.wear

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.*
import dev.hnm.workbench.android.*
import dev.hnm.workbench.core.companion.*
import dev.hnm.workbench.core.ir.*
import kotlinx.coroutines.*

class WristState(context: Context) {
    val store = AndroidWorkspaceStore.get(context)
    val capabilities = AndroidHaptics.probe(AndroidHaptics.vibrator(context))
    val player = AndroidPatternPlayer(AndroidHaptics.vibrator(context), context.applicationContext, capabilities)
    val sync = PatternSync(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var draftJob: Job? = null
    private var captureJob: Job? = null
    var pattern by mutableStateOf(store.loadDraft() ?: WristPatterns.doublePulse())
        private set
    private var tuneOriginal = pattern
    var spacing by mutableStateOf(1f)
        private set
    var strength by mutableStateOf(WristPatterns.peakStrength(pattern).toFloat().coerceIn(.1f,1f))
        private set
    var message by mutableStateOf(store.notice.value ?: "Made and played on this watch")
    var recording by mutableStateOf(false)
        private set
    val taps = mutableStateListOf<Long>()
    var query by mutableStateOf("haptic")
    var position by mutableIntStateOf(0)
        private set
    var cues by mutableStateOf(true)
    private var lastCue = Long.MIN_VALUE
    val searchText get() = pattern.metadata["hafx.search.text"]?.takeIf { it.isNotBlank() }?.take(2000) ?: SEARCH_TEXT
    val searchIndex get() = SearchCueIndex(searchText, query)
    val canTune get() = WristPatterns.canTune(pattern)
    val hasSound get() = WristPatterns.hasSound(pattern)
    val auditionLabel get() = when {
        hasSound && soundOn && WristPatterns.hasHaptics(pattern) -> "Hear & feel"
        hasSound && soundOn -> "Hear"
        WristPatterns.hasHaptics(pattern) -> "Feel"
        else -> "Preview"
    }
    private val preferences = context.getSharedPreferences("hafx_preview", Context.MODE_PRIVATE)
    var soundOn by mutableStateOf(preferences.getBoolean("sound", true))
        private set
    fun toggleSound() {
        player.stop()
        soundOn = !soundOn
        preferences.edit().putBoolean("sound", soundOn).apply()
    }
    fun audition(effect: HapticAudioPattern = pattern) {
        if(effect.couplings.any { it.mode == CouplingMode.AUDIO_DRIVES_HAPTICS }) {
            message="Audio-driven haptics need Studio waveform export. Use an effect with explicit haptic pulses here."
            return
        }
        player.play(if(soundOn) effect else WristPatterns.hapticsOnly(effect))
    }
    fun searchCue(): HapticAudioPattern = WristPatterns.hapticsOnly(pattern).let {
        if(it.tracks.filterIsInstance<HapticTrack>().any { t -> !t.muted && t.events.isNotEmpty() } && pattern.couplings.isEmpty() && PatternSafety.duration(it)<=1.5) it
        else WristPatterns.fromTaps(listOf(0L), name="Search match")
    }
    var comparison by mutableStateOf<HapticAudioPattern?>(null)
        private set

    fun open(effect: HapticAudioPattern) {
        player.stop()
        pattern = effect
        position = 0
        tuneOriginal = effect
        comparison = effect
        spacing = 1f
        strength = WristPatterns.peakStrength(effect).toFloat().coerceIn(.1f,1f)
        draftChanged()
    }
    fun addSound() { runCatching { open(WristPatterns.addPairedSound(pattern)) }.onFailure { message=it.message.orEmpty() } }
    fun setSoundLevel(level: Float) {
        runCatching {
            val original=WristPatterns.withSoundLevel(tuneOriginal,level.toDouble())
            original to WristPatterns.tune(original,spacing.toDouble(),strength.toDouble())
        }.onSuccess { (original,adjusted) -> tuneOriginal=original;pattern=adjusted;draftChanged() }
            .onFailure { message=it.message.orEmpty() }
    }
    fun makePulse() { open(WristPatterns.doublePulse()); message = "Adjust, feel, then keep your version" }
    fun adjust(spacing: Float = this.spacing, strength: Float = this.strength) {
        runCatching { WristPatterns.tune(tuneOriginal, spacing.toDouble(), strength.toDouble()) }
            .onSuccess { pattern = it; this.spacing = spacing; this.strength = strength; draftChanged() }
            .onFailure { message = it.message ?: "Could not adjust effect" }
    }
    private fun draftChanged() {
        draftJob?.cancel()
        draftJob = scope.launch { delay(250); flushDraft() }
    }
    fun flushDraft() {
        runCatching { store.saveDraft(pattern) }.onFailure { message = "Draft not saved: ${it.message}" }
    }
    fun keep(): Boolean = runCatching {
        pattern = store.library.saveUser(pattern, "watch")
        tuneOriginal = pattern
        spacing = 1f
        flushDraft()
        message = "Saved on this watch"
        true
    }.getOrElse { message = "Not saved: ${it.message}"; false }
    var editName by mutableStateOf("")
    val isSavedUser get() = pattern !in dev.hnm.workbench.core.library.BuiltInPatterns.ALL && store.library.get(pattern.name) != null
    fun rename(): Boolean = runCatching {
        open(store.library.renameUser(pattern.name, editName, "watch", pattern))
        message = "Name saved"; true
    }.getOrElse { message = "Could not rename: ${it.message}"; false }
    fun delete(): Boolean = runCatching {
        check(isSavedUser) { "Only saved personal effects can be deleted" }
        store.library.remove(pattern.name)
        open(WristPatterns.doublePulse()); message = "Effect deleted. Your new draft is ready."; true
    }.getOrElse { message = "Could not delete: ${it.message}"; false }
    fun recover(): Boolean = runCatching {
        store.recoverLibrary(); message = store.notice.value.orEmpty(); true
    }.getOrElse { message = "Could not recover: ${it.message}"; false }
    fun send() {
        if (!keep()) return
        val revision = pattern
        scope.launch { runCatching { sync.send(revision) }.onFailure { message = "Send failed: ${it.message}" } }
    }
    fun record() {
        player.stop(); taps.clear(); recording = true
        message = "Tap up to 6 times"
        captureJob?.cancel()
        captureJob = scope.launch { delay(4000); finishRecording() }
    }
    fun tap() {
        if (!recording) return
        taps.add(SystemClock.elapsedRealtime())
        if (taps.size >= 6) finishRecording()
    }
    fun finishRecording() {
        if (!recording) return
        recording = false; captureJob?.cancel()
        if (taps.isEmpty()) { message = "No taps recorded. Try again."; return }
        open(WristPatterns.fromTaps(taps.toList()))
        message = "${taps.size} taps captured. Feel it before saving."
    }
    fun cancelCapture() { captureJob?.cancel(); recording = false }
    fun scrub(next: Int) {
        val target = next.coerceIn(0,searchText.lastIndex)
        val crossed = searchIndex.crossed(position,target)
        position = target
        if (cues && crossed) cue()
    }
    fun nextMatch() { searchIndex.next(position)?.let { position=it; if(cues)cue() } }
    fun previousMatch() { searchIndex.previous(position)?.let { position=it; if(cues)cue() } }
    private fun cue() {
        val now = SystemClock.elapsedRealtime()
        if (lastCue != Long.MIN_VALUE && now-lastCue < 350) return
        lastCue = now
        // Search cues must remain short, even if the user's document is long.
        player.play(searchCue())
    }
    fun pause() { cancelCapture(); player.stop(); draftJob?.cancel(); flushDraft() }
    fun refreshSync() { scope.launch { sync.reconcile() } }
    fun close() { pause(); player.close(); sync.close(); scope.cancel() }

    companion object {
        const val SEARCH_TEXT = "A haptic cue can mark a search match without requiring another visual marker. " +
            "This local document is a navigation playground. Turn the bezel or use the position controls to move through it. " +
            "A useful haptic pattern is brief and distinguishable from surrounding feedback. " +
            "Long patterns may hide the next match, so this lab uses a short cue when your saved effect is too long. " +
            "You can switch cues off to compare the same navigation without vibration. " +
            "Personal haptic effects should be created where they are felt. " +
            "Finding information quickly is different from understanding it; both deserve careful evaluation."
    }
}
