package dev.hnm.workbench.core.ir

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Time is seconds, relative to pattern start. All level/scale values are normalized 0..1 unless noted. */
typealias Seconds = Double

/**
 * The keystone of the whole tool: a backend-agnostic effect definition.
 *
 * One pattern is authored once and rendered to whatever playback backend exists on the current
 * platform (Android Vibrator, a controller voice-coil, or pure audio). The model borrows Apple's
 * Core Haptics shape — events on a timeline, each carrying intensity + sharpness plus animatable
 * breakpoint curves — because it is the most expressive cross-platform-ish haptic abstraction and
 * already has an interchange format (AHAP).
 */
@Serializable
data class HapticAudioPattern(
    val version: Int = 1,
    val name: String,
    val tracks: List<Track>,
    val couplings: List<Coupling> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
sealed interface Track {
    val id: String
    val name: String
    val muted: Boolean
}

@Serializable
@SerialName("haptic")
data class HapticTrack(
    override val id: String,
    override val name: String = "Haptics",
    override val muted: Boolean = false,
    val events: List<HapticEvent> = emptyList(),
    val curves: List<ParameterCurve> = emptyList(),
) : Track

@Serializable
@SerialName("audio")
data class AudioTrack(
    override val id: String,
    override val name: String = "Audio",
    override val muted: Boolean = false,
    val events: List<AudioEvent> = emptyList(),
    val curves: List<ParameterCurve> = emptyList(),
) : Track

// ---------------------------------------------------------------------------
// Haptic events
// ---------------------------------------------------------------------------

@Serializable
sealed interface HapticEvent {
    val time: Seconds
}

/** Instantaneous tap/click. */
@Serializable
@SerialName("transient")
data class Transient(
    override val time: Seconds,
    val intensity: Double, // 0..1
    val sharpness: Double, // 0..1 (perceptual: dull thud -> crisp tick)
) : HapticEvent

/** Sustained buzz with envelope; intensity/sharpness can be animated by curves. */
@Serializable
@SerialName("continuous")
data class Continuous(
    override val time: Seconds,
    val duration: Seconds,
    val intensity: Double,
    val sharpness: Double,
    val envelope: Envelope = Envelope(),
) : HapticEvent

/** Device-tuned Android primitive. Renders natively on Android; emulated elsewhere. */
@Serializable
@SerialName("primitive")
data class Primitive(
    override val time: Seconds,
    // Serialized as "primitiveType" so it doesn't collide with the polymorphic "type" discriminator.
    @SerialName("primitiveType") val type: PrimitiveType,
    val scale: Double = 1.0, // 0..1
) : HapticEvent

@Serializable
enum class PrimitiveType { CLICK, TICK, LOW_TICK, THUD, SPIN, QUICK_RISE, SLOW_RISE, QUICK_FALL }

@Serializable
data class Envelope(
    val attack: Seconds = 0.0,
    val decay: Seconds = 0.0,
    val sustain: Double = 1.0, // level 0..1
    val release: Seconds = 0.0,
)

// ---------------------------------------------------------------------------
// Audio events
// ---------------------------------------------------------------------------

@Serializable
sealed interface AudioEvent {
    val time: Seconds
}

@Serializable
@SerialName("osc")
data class OscEvent(
    override val time: Seconds,
    val duration: Seconds,
    val waveform: Waveform,
    val frequencyHz: Double,
    val gain: Double = 0.8,
    val envelope: Envelope = Envelope(attack = 0.005, release = 0.05),
    val filter: Filter? = null,
) : AudioEvent

@Serializable
@SerialName("sample")
data class SampleEvent(
    override val time: Seconds,
    val sampleId: String, // reference into the sample bank
    val gain: Double = 1.0,
    val pitchShiftSemitones: Double = 0.0,
    val envelope: Envelope = Envelope(),
) : AudioEvent

@Serializable
enum class Waveform { SINE, SQUARE, SAW, TRIANGLE, NOISE }

@Serializable
data class Filter(val type: FilterType, val cutoffHz: Double, val resonance: Double = 0.7)

@Serializable
enum class FilterType { LOWPASS, HIGHPASS, BANDPASS }

// ---------------------------------------------------------------------------
// Parameter curves ("live knobs, over time")
// ---------------------------------------------------------------------------

@Serializable
data class ParameterCurve(
    val parameter: CurveParam,
    val points: List<ControlPoint>,
    val interpolation: Interpolation = Interpolation.LINEAR,
)

@Serializable
enum class CurveParam {
    HAPTIC_INTENSITY, HAPTIC_SHARPNESS,
    AUDIO_GAIN, AUDIO_PITCH, AUDIO_FILTER_CUTOFF,
}

@Serializable
data class ControlPoint(val time: Seconds, val value: Double)

@Serializable
enum class Interpolation { STEP, LINEAR, SMOOTH } // SMOOTH = cubic

// ---------------------------------------------------------------------------
// Coupling (audio <-> haptics relationships)
// ---------------------------------------------------------------------------

@Serializable
data class Coupling(
    val id: String,
    val mode: CouplingMode, // INDEPENDENT needs no entry; only the cross-drives are stored
    val sourceTrackId: String,
    val targetTrackId: String,
    val params: CouplingParams = CouplingParams(),
)

@Serializable
enum class CouplingMode { AUDIO_DRIVES_HAPTICS, HAPTICS_DRIVES_AUDIO }

@Serializable
data class CouplingParams(
    val gain: Double = 1.0,
    val attackMs: Double = 5.0, // envelope-follower attack
    val releaseMs: Double = 50.0, // envelope-follower release
    val sharpnessFromBrightness: Boolean = true, // map audio spectral brightness -> haptic sharpness
)
