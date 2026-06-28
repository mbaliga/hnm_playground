package dev.hnm.workbench.core.playback

import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.PrimitiveType
import dev.hnm.workbench.core.ir.Seconds

/**
 * The critical seam (§2). The core exposes two *kinds* of output so a Rust core can later be grafted
 * only where it pays off (the DSP), without touching the IR, sequencer, UI, or Android:
 *
 *  - Continuous signal (audio + voice-coil haptics) -> a pull-based [FloatStream]. Rust-graftable.
 *  - Discrete commands (event-scheduled hardware, i.e. Android Vibrator) -> a list of timed
 *    [HapticCommand]s that the OS schedules itself. Stays Kotlin; it's a native API.
 */
interface PatternRenderer {
    /** Continuous audio signal, pull-based for streaming. The Rust-graftable path. */
    fun renderAudio(pattern: HapticAudioPattern, sampleRate: Int): FloatStream

    /** Continuous haptic waveform for voice-coil actuators (DualSense). Also Rust-graftable. */
    fun renderHapticWaveform(pattern: HapticAudioPattern, sampleRate: Int): FloatStream

    /** Discrete commands for event-scheduled haptic hardware (Android). Stays Kotlin. */
    fun scheduleHaptics(pattern: HapticAudioPattern, caps: HapticCapabilities): List<HapticCommand>
}

interface AudioBackend {
    fun start(stream: FloatStream, sampleRate: Int)
    fun stop()
}

interface HapticBackend {
    val capabilities: HapticCapabilities
    fun play(pattern: HapticAudioPattern, renderer: PatternRenderer, clock: TransportClock)
    fun stop()
}

// ---------------------------------------------------------------------------
// Capabilities
// ---------------------------------------------------------------------------

data class HapticCapabilities(
    val hasVibrator: Boolean,
    val hasAmplitudeControl: Boolean,
    val supportedPrimitives: Set<PrimitiveType>,
    val hasFrequencyControl: Boolean, // PWLE / envelope-capable hardware only
    val actuatorType: ActuatorType,
) {
    companion object {
        /** A device with no actuator at all (most desktops): audio-only / export-only. */
        val NONE = HapticCapabilities(
            hasVibrator = false,
            hasAmplitudeControl = false,
            supportedPrimitives = emptySet(),
            hasFrequencyControl = false,
            actuatorType = ActuatorType.NONE,
        )

        /** A modern phone LRA: amplitude control + full primitive set, but no true frequency knob. */
        val LRA_FULL = HapticCapabilities(
            hasVibrator = true,
            hasAmplitudeControl = true,
            supportedPrimitives = PrimitiveType.entries.toSet(),
            hasFrequencyControl = false,
            actuatorType = ActuatorType.LRA,
        )

        /** A cheap ERM motor: on/off only. Exercises the degrade path. */
        val ERM_BASIC = HapticCapabilities(
            hasVibrator = true,
            hasAmplitudeControl = false,
            supportedPrimitives = emptySet(),
            hasFrequencyControl = false,
            actuatorType = ActuatorType.ERM,
        )

        /** Wideband / PWLE hardware with real frequency control. */
        val WIDEBAND = HapticCapabilities(
            hasVibrator = true,
            hasAmplitudeControl = true,
            supportedPrimitives = PrimitiveType.entries.toSet(),
            hasFrequencyControl = true,
            actuatorType = ActuatorType.WIDEBAND,
        )
    }
}

enum class ActuatorType { ERM, LRA, WIDEBAND, CONTROLLER_RUMBLE, CONTROLLER_VOICECOIL, NONE }

// ---------------------------------------------------------------------------
// Discrete haptic commands (the Android / event-scheduled path)
// ---------------------------------------------------------------------------

/**
 * A timed instruction for event-scheduled hardware. The backend converts these into the relevant
 * native call (`VibrationEffect.Composition`, `createWaveform`, `createOneShot`) and lets the OS
 * schedule them. Amplitudes use Android's 0..255 convention so the Android backend is a thin mapping.
 */
sealed interface HapticCommand {
    /** Absolute time from pattern start at which this command should fire. */
    val atSeconds: Seconds
}

/** Native Android composition primitive — best fidelity where supported. */
data class PlayPrimitive(
    override val atSeconds: Seconds,
    val type: PrimitiveType,
    val scale: Float, // 0..1
) : HapticCommand

/** A pre-sampled amplitude waveform: `VibrationEffect.createWaveform(timingsMs, amplitudes, repeat)`. */
data class PlayWaveform(
    override val atSeconds: Seconds,
    val timingsMs: LongArray,
    val amplitudes: IntArray, // 0..255 per step
    val repeat: Int = -1,
) : HapticCommand {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlayWaveform) return false
        return atSeconds == other.atSeconds &&
            timingsMs.contentEquals(other.timingsMs) &&
            amplitudes.contentEquals(other.amplitudes) &&
            repeat == other.repeat
    }

    override fun hashCode(): Int {
        var result = atSeconds.hashCode()
        result = 31 * result + timingsMs.contentHashCode()
        result = 31 * result + amplitudes.contentHashCode()
        result = 31 * result + repeat
        return result
    }
}

/** A single on/off (or fixed-amplitude) buzz: `VibrationEffect.createOneShot(durationMs, amplitude)`. */
data class PlayOneShot(
    override val atSeconds: Seconds,
    val durationMs: Long,
    val amplitude: Int, // 0..255; use 255 for "default" on no-amplitude-control hardware
) : HapticCommand

/**
 * A control point of an amplitude+frequency envelope for wideband / PWLE-capable hardware.
 * @param timeMs offset from the envelope start.
 * @param amplitude 0..1 perceived strength.
 * @param frequencyHz target vibration frequency (the actuator is driven toward this within its band).
 */
data class EnvelopePoint(val timeMs: Long, val amplitude: Float, val frequencyHz: Float)

/**
 * A smooth amplitude+frequency envelope — the richest path, for actuators that expose real frequency
 * control (Android 16 `WaveformEnvelopeBuilder`, voice-coils like DualSense). Backends without envelope
 * support fall back to a sampled [PlayWaveform] (amplitude only). This is what lets one authored
 * Continuous event resynthesize to "HD" haptics on capable hardware instead of a flat buzz.
 */
data class PlayEnvelope(
    override val atSeconds: Seconds,
    val points: List<EnvelopePoint>,
) : HapticCommand
