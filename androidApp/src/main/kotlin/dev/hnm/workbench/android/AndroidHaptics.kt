package dev.hnm.workbench.android

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.VibrationAttributes
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

    /** ERM motors need ~tens of ms to spin up to a felt level; floor every short pulse to this. */
    private const val MIN_PULSE_MS = 70L

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

    /**
     * Fire a whole scheduled pattern as a single [VibrationEffect]. An all-primitive schedule composes
     * atomically (best fidelity on an LRA). Anything else — one-shots/waveforms, the common case on
     * ERM hardware with no amplitude control — is stitched into one on/off waveform with a perceptible
     * minimum pulse width, so short transients actually move the motor instead of being too brief to
     * feel. (Earlier this only fired the first command, and 20 ms pulses were imperceptible on an ERM.)
     */
    fun play(
        vibrator: Vibrator,
        commands: List<HapticCommand>,
        onError: (String) -> Unit,
    ) {
        if (commands.isEmpty()) {
            onError("Nothing scheduled — device reports no vibrator?")
            return
        }
        val effect = if (commands.all { it is PlayPrimitive }) {
            composePrimitives(commands.filterIsInstance<PlayPrimitive>())
        } else {
            buildWaveform(commands)
        }
        if (effect == null) {
            onError("Could not build a vibration for this pattern.")
            return
        }
        vibrateSafely(vibrator, effect, onError)
    }

    /**
     * A deliberately unmistakable buzz to confirm the actuator works at all, independent of any
     * pattern: a 600 ms full-amplitude one-shot, then a single CLICK primitive. If you can't feel
     * THIS, the issue is the device/OS (haptics setting, ring mode), not the pattern.
     */
    fun selfTest(vibrator: Vibrator, handler: Handler, onError: (String) -> Unit) {
        vibrateSafely(vibrator, VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE), onError)
        handler.postDelayed({
            val click = runCatching {
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 0)
                    .compose()
            }.getOrNull()
            if (click != null) vibrateSafely(vibrator, click, onError)
        }, 900)
    }

    /** Short human-readable summary of a schedule, shown under each pattern so the path is visible. */
    fun describe(commands: List<HapticCommand>): String {
        if (commands.isEmpty()) return "no haptic commands"
        return commands.joinToString("  ·  ") { c ->
            val ms = (c.atSeconds * 1000).toInt()
            when (c) {
                is PlayPrimitive -> "${c.type}@${ms}ms×${(c.scale * 100).toInt()}%"
                is PlayWaveform -> "waveform@${ms}ms(${c.timingsMs.size} steps)"
                is PlayOneShot -> "oneshot@${ms}ms(${c.durationMs}ms,a=${c.amplitude})"
                else -> c.toString()
            }
        }
    }

    /**
     * Stitch a schedule into one waveform timeline. Each event becomes an "on" segment (one-shots and
     * primitives get at least [MIN_PULSE_MS] so an ERM is actually felt; waveforms keep their own
     * envelope), with "off" gaps between events. On no-amplitude hardware the amplitudes collapse to
     * on/off automatically.
     */
    private fun buildWaveform(commands: List<HapticCommand>): VibrationEffect? {
        data class Seg(val startMs: Long, val durMs: Long, val amp: Int)

        val segments = mutableListOf<Seg>()
        for (command in commands) {
            val startMs = (command.atSeconds * 1000).toLong().coerceAtLeast(0L)
            when (command) {
                is PlayOneShot ->
                    segments += Seg(startMs, maxOf(command.durationMs, MIN_PULSE_MS), command.amplitude.coerceIn(1, 255))
                is PlayPrimitive ->
                    segments += Seg(startMs, MIN_PULSE_MS, (command.scale * 255).toInt().coerceIn(1, 255))
                is PlayWaveform -> {
                    var local = 0L
                    for (i in command.timingsMs.indices) {
                        val amp = command.amplitudes.getOrElse(i) { 0 }
                        if (amp > 0) segments += Seg(startMs + local, command.timingsMs[i].coerceAtLeast(1L), amp.coerceIn(1, 255))
                        local += command.timingsMs[i]
                    }
                }
                else -> {}
            }
        }
        if (segments.isEmpty()) return null
        segments.sortBy { it.startMs }

        val timings = mutableListOf<Long>()
        val amplitudes = mutableListOf<Int>()
        var cursor = 0L
        for (seg in segments) {
            val start = maxOf(seg.startMs, cursor)
            if (start > cursor) {
                timings += (start - cursor)
                amplitudes += 0
            }
            timings += seg.durMs
            amplitudes += seg.amp
            cursor = start + seg.durMs
        }
        return VibrationEffect.createWaveform(timings.toLongArray(), amplitudes.toIntArray(), -1)
    }

    private fun composePrimitives(primitives: List<PlayPrimitive>): VibrationEffect? {
        if (primitives.isEmpty()) return null
        val composition = VibrationEffect.startComposition()
        var prevAt = 0.0
        primitives.forEach { cmd ->
            val delayMs = ((cmd.atSeconds - prevAt) * 1000).toInt().coerceAtLeast(0)
            composition.addPrimitive(primitiveId(cmd.type), cmd.scale.coerceIn(0f, 1f), delayMs)
            prevAt = cmd.atSeconds
        }
        return composition.compose()
    }

    private fun vibrateSafely(vibrator: Vibrator, effect: VibrationEffect, onError: (String) -> Unit) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // USAGE_TOUCH keeps haptics from being suppressed by ring/media routing on some OEMs.
                val attrs = VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_TOUCH).build()
                vibrator.vibrate(effect, attrs)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect)
            }
        } catch (t: Throwable) {
            onError("vibrate failed: ${t.message}")
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
