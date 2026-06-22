package dev.hnm.workbench.android

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dev.hnm.workbench.core.ir.PrimitiveType
import dev.hnm.workbench.core.playback.ActuatorType
import dev.hnm.workbench.core.playback.HapticCapabilities
import dev.hnm.workbench.core.playback.HapticCommand
import dev.hnm.workbench.core.playback.PlayOneShot
import dev.hnm.workbench.core.playback.PlayPrimitive
import dev.hnm.workbench.core.playback.PlayWaveform

/**
 * Bridges `core`'s device-agnostic [HapticCommand] schedule to the real Android `Vibrator`. The
 * mapping mirrors what `KotlinVibrationEffectExporter` emits, so what you feel here matches the
 * exported code. Probing reports the actuator's real capabilities so `core` can degrade gracefully.
 */
object AndroidHaptics {

    fun vibrator(context: Context): Vibrator {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        return manager.defaultVibrator
    }

    fun probe(vibrator: Vibrator): HapticCapabilities {
        val hasVibrator = vibrator.hasVibrator()
        val hasAmplitude = vibrator.hasAmplitudeControl()
        val supported = PrimitiveType.entries
            .filter { vibrator.areAllPrimitivesSupported(primitiveId(it)) }
            .toSet()
        val actuator = when {
            !hasVibrator -> ActuatorType.NONE
            supported.isNotEmpty() -> ActuatorType.LRA
            hasAmplitude -> ActuatorType.LRA
            else -> ActuatorType.ERM
        }
        return HapticCapabilities(
            hasVibrator = hasVibrator,
            hasAmplitudeControl = hasAmplitude,
            supportedPrimitives = supported,
            hasFrequencyControl = false, // Android exposes no public PWLE/frequency API.
            actuatorType = actuator,
        )
    }

    /** Build a single [VibrationEffect] for the whole scheduled pattern, or null if there's nothing. */
    fun toEffect(commands: List<HapticCommand>): VibrationEffect? {
        if (commands.isEmpty()) return null

        // All-primitive schedules compose into one atomic effect (the LRA path).
        if (commands.all { it is PlayPrimitive }) {
            val composition = VibrationEffect.startComposition()
            var prevAt = 0.0
            commands.filterIsInstance<PlayPrimitive>().forEach { cmd ->
                val delayMs = ((cmd.atSeconds - prevAt) * 1000).toInt().coerceAtLeast(0)
                composition.addPrimitive(primitiveId(cmd.type), cmd.scale.coerceIn(0f, 1f), delayMs)
                prevAt = cmd.atSeconds
            }
            return composition.compose()
        }

        // Degraded paths (ERM etc.) — play the first concrete command.
        return when (val cmd = commands.first()) {
            is PlayWaveform -> VibrationEffect.createWaveform(cmd.timingsMs, cmd.amplitudes, cmd.repeat)
            is PlayOneShot -> VibrationEffect.createOneShot(
                cmd.durationMs.coerceAtLeast(1L),
                cmd.amplitude.coerceIn(1, 255),
            )
            else -> null
        }
    }

    fun primitiveId(type: PrimitiveType): Int = when (type) {
        PrimitiveType.CLICK -> VibrationEffect.Composition.PRIMITIVE_CLICK
        PrimitiveType.TICK -> VibrationEffect.Composition.PRIMITIVE_TICK
        PrimitiveType.LOW_TICK -> VibrationEffect.Composition.PRIMITIVE_LOW_TICK
        PrimitiveType.THUD -> VibrationEffect.Composition.PRIMITIVE_THUD
        PrimitiveType.SPIN -> VibrationEffect.Composition.PRIMITIVE_SPIN
        PrimitiveType.QUICK_RISE -> VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
        PrimitiveType.SLOW_RISE -> VibrationEffect.Composition.PRIMITIVE_SLOW_RISE
        PrimitiveType.QUICK_FALL -> VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
    }
}
