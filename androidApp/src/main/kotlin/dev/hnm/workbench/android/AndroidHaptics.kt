package dev.hnm.workbench.android

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.SystemClock
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
     * Fire a whole scheduled pattern. An all-primitive schedule composes into one atomic effect; any
     * other schedule (one-shots / waveforms, possibly several) is dispatched on [handler] at each
     * command's time — fixing the earlier bug where only the first command played.
     */
    fun play(
        vibrator: Vibrator,
        commands: List<HapticCommand>,
        handler: Handler,
        onError: (String) -> Unit,
    ) {
        if (commands.isEmpty()) {
            onError("Nothing scheduled — device reports no vibrator?")
            return
        }
        if (commands.all { it is PlayPrimitive }) {
            val effect = composePrimitives(commands.filterIsInstance<PlayPrimitive>()) ?: return
            vibrateSafely(vibrator, effect, onError)
            return
        }
        val base = SystemClock.uptimeMillis()
        for (command in commands) {
            val effect = effectFor(command) ?: continue
            handler.postAtTime({ vibrateSafely(vibrator, effect, onError) }, base + (command.atSeconds * 1000).toLong())
        }
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

    private fun effectFor(command: HapticCommand): VibrationEffect? = when (command) {
        is PlayPrimitive -> composePrimitives(listOf(command))
        is PlayWaveform -> VibrationEffect.createWaveform(command.timingsMs, command.amplitudes, command.repeat)
        is PlayOneShot -> VibrationEffect.createOneShot(
            command.durationMs.coerceAtLeast(1L),
            command.amplitude.coerceIn(1, 255),
        )
        else -> null
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
