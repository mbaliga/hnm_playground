package dev.hnm.workbench.core.export

import dev.hnm.workbench.core.dsp.PatternTiming
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticEvent
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.Primitive
import dev.hnm.workbench.core.ir.PrimitiveType
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.core.playback.HapticCapabilities
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * Exports a pattern to Clackpad's `clackpad-haptic` JSON — the Workbench → Clackpad hand-off from the
 * Clackpad ⟷ Haptics + Audio Workbench Integration Contract §2. One pattern per payload, scoped to
 * exactly one of Clackpad's own five feedback roles (`key`/`del`/`action`/`suggest`/`swipe` — Clackpad's
 * own vocabulary verbatim, not a Workbench invention).
 *
 * `render.timings`/`render.amplitudes` is the *whole* contract Clackpad's importer reads at play time;
 * everything above it in the file (`pattern`, and its `intensity`/`sharpness`/`voice` per event) is
 * provenance / re-editing-only, never interpreted by Clackpad (contract §5 resolution 4). The baked
 * render reuses [KotlinVibrationEffectExporter.buildAmplitudeTimeline] rather than re-deriving the
 * amplitude timeline, so the two exporters can never drift apart on what a pattern "sounds like" as a
 * plain waveform.
 */
object ClackpadExporter {

    /** Clackpad's own closed vocabulary for `HorizKeebService.vibrate(type)` — the file is meaningless without exactly one of these. */
    val VALID_ROLES: Set<String> = setOf("key", "del", "action", "suggest", "swipe")

    /** Deliberately distinct from `application/json`, which Clackpad's own settings-backup import already claims (contract §2). */
    const val MIME_TYPE: String = "application/vnd.clackpad.haptic+json"

    private const val FORMAT = "clackpad-haptic"
    private const val FORMAT_VERSION = 1
    private const val PATTERN_FORMAT = "workbench-pattern"
    private const val PATTERN_FORMAT_VERSION = 1

    // Nominal on-durations (ms) for IR event kinds that have no `duration` of their own — the file
    // format requires one (>= 10 ms per `units`) for every moment. These mirror the tick lengths
    // KotlinVibrationEffectExporter bakes into the same events' amplitude envelope (see its
    // `fillEnvelope`), so the provenance duration and the baked render stay perceptually consistent.
    private const val TRANSIENT_NOMINAL_MS = 15
    private const val PRIMITIVE_NOMINAL_MS = 20
    private const val MIN_DURATION_MS = 10

    private val UNITS: Map<String, String> = mapOf(
        "durationMs" to "integer ms — whole pattern, including the trailing silence",
        "role" to "one of key|del|action|suggest|swipe — Clackpad's own vibrate(type) vocabulary; the file is meaningless without exactly one of these",
        "pattern.events[].atMs" to "integer ms from pattern start, 0-based, ascending",
        "pattern.events[].duration" to "integer ms, minimum 10",
        "pattern.events[].intensity" to "0..1 — provenance only; Clackpad's importer does not read this field",
        "pattern.events[].sharpness" to "0..1 — provenance only; Clackpad has no live sharpness axis, this is never interpreted at play time, only baked into render",
        "pattern.events[].voice" to "click|thud|ring|swell|grain — timbre hint, safe to ignore",
        "render.timings" to "ms segments for Android VibrationEffect.createWaveform; index 0 is an off segment",
        "render.amplitudes" to "0..255, index-matched to render.timings, native VibrationEffect scale — Clackpad reads this directly with no conversion",
    )

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json { prettyPrint = true; prettyPrintIndent = "  " }

    /**
     * @param role one of [VALID_ROLES]; throws [IllegalArgumentException] otherwise.
     * @param capabilities accepted for parity with [KotlinVibrationEffectExporter.export] and future
     *   capability-aware rendering, but currently unused: Clackpad's playback path
     *   (`HorizKeebService.vibrate`) is amplitude+timing only (contract §5 resolution 4), so v1 always
     *   bakes the same plain waveform regardless of target capability.
     */
    fun export(
        pattern: HapticAudioPattern,
        role: String,
        capabilities: HapticCapabilities = HapticCapabilities.LRA_FULL,
        sourceVersion: String,
        sourceBuild: String,
    ): String {
        require(role in VALID_ROLES) {
            "Unknown Clackpad role \"$role\" — must be one of ${VALID_ROLES.sorted()}"
        }

        val hapticTracks = pattern.tracks.filterIsInstance<HapticTrack>()
        val events = hapticTracks.filterNot { it.muted }.flatMap { it.events }.sortedBy { it.time }
        val (timings, amplitudes) = KotlinVibrationEffectExporter.buildAmplitudeTimeline(events)
        val envelopeBaked = hapticTracks.any { it.curves.isNotEmpty() }

        val file = ClackpadHapticFile(
            format = FORMAT,
            version = FORMAT_VERSION,
            contract = ContractBlock(),
            name = pattern.name,
            role = role,
            source = SourceBlock(version = sourceVersion, build = sourceBuild),
            durationMs = (PatternTiming.durationSeconds(pattern) * 1000).roundToInt(),
            envelopeBaked = envelopeBaked,
            pattern = PatternBlock(
                format = PATTERN_FORMAT,
                version = PATTERN_FORMAT_VERSION,
                name = pattern.name,
                events = events.map { it.toMoment() },
            ),
            render = RenderBlock(timings = timings.toList(), amplitudes = amplitudes.toList()),
            units = UNITS,
        )
        return json.encodeToString(ClackpadHapticFile.serializer(), file)
    }

    private fun HapticEvent.toMoment(): PatternMoment = when (this) {
        is Transient -> PatternMoment(
            atMs = msOf(time),
            intensity = intensity,
            sharpness = sharpness,
            duration = TRANSIENT_NOMINAL_MS,
            voice = voice ?: transientVoice(sharpness),
        )
        is Continuous -> PatternMoment(
            atMs = msOf(time),
            intensity = intensity,
            sharpness = sharpness,
            duration = msOf(duration).coerceAtLeast(MIN_DURATION_MS),
            voice = voice ?: "swell",
        )
        is Primitive -> PatternMoment(
            atMs = msOf(time),
            intensity = scale,
            sharpness = primitiveSharpness(type),
            duration = PRIMITIVE_NOMINAL_MS,
            voice = voice ?: primitiveVoice(type),
        )
    }

    private fun msOf(seconds: Double): Int = (seconds * 1000).roundToInt()

    /** Deterministic transient -> voice mapping (contract §2), reproducible by a human or a test. */
    private fun transientVoice(sharpness: Double): String = if (sharpness >= 0.6) "click" else "thud"

    private fun primitiveVoice(type: PrimitiveType): String = when (type) {
        PrimitiveType.CLICK, PrimitiveType.TICK -> "click"
        PrimitiveType.LOW_TICK, PrimitiveType.THUD -> "thud"
        PrimitiveType.SPIN -> "grain"
        PrimitiveType.QUICK_RISE, PrimitiveType.SLOW_RISE, PrimitiveType.QUICK_FALL -> "swell"
    }

    /** Nominal perceptual sharpness for a primitive, which carries no sharpness of its own — provenance only. */
    private fun primitiveSharpness(type: PrimitiveType): Double = when (type) {
        PrimitiveType.TICK -> 0.95
        PrimitiveType.LOW_TICK -> 0.6
        PrimitiveType.CLICK -> 0.5
        PrimitiveType.THUD -> 0.15
        else -> 0.5
    }
}

@Serializable
data class ClackpadHapticFile(
    val format: String,
    val version: Int,
    val contract: ContractBlock,
    val name: String,
    val role: String,
    val source: SourceBlock,
    val durationMs: Int,
    val envelopeBaked: Boolean,
    val pattern: PatternBlock,
    val render: RenderBlock,
    val units: Map<String, String>,
)

@Serializable
data class ContractBlock(
    val status: String = "agreed",
    val agreedWith: String = "com.clackpad.ime — Clackpad/Workbench integration, 2026-07-27",
    val spec: String = "docs/CLACKPAD_CONTRACT.md",
    val note: String = "Shape agreed between Haptics + Audio Workbench and Clackpad (com.clackpad.ime) " +
        "on 2026-07-27. render.timings/render.amplitudes is the whole contract Clackpad needs to play " +
        "this; everything above it is optional provenance.",
)

@Serializable
data class SourceBlock(
    val app: String = "Haptics + Audio Workbench",
    val version: String,
    val build: String,
)

@Serializable
data class PatternBlock(
    val format: String,
    val version: Int,
    val name: String,
    val events: List<PatternMoment>,
)

@Serializable
data class PatternMoment(
    val atMs: Int,
    val intensity: Double,
    val sharpness: Double,
    val duration: Int,
    val voice: String,
)

@Serializable
data class RenderBlock(
    val note: String = "Lossy fallback. Play this if you ignore everything above — amplitude and " +
        "timing only, no sharpness or timbre.",
    val api: String = "android.os.VibrationEffect.createWaveform(timings, amplitudes, -1)",
    val timings: List<Long>,
    val amplitudes: List<Int>,
)
