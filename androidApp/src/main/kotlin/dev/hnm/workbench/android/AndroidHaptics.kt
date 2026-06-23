package dev.hnm.workbench.android

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticEvent
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.Primitive
import dev.hnm.workbench.core.ir.PrimitiveType
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.core.playback.ActuatorType
import dev.hnm.workbench.core.playback.HapticCapabilities
import dev.hnm.workbench.core.playback.HapticCommand
import dev.hnm.workbench.core.playback.PlayOneShot
import dev.hnm.workbench.core.playback.PlayPrimitive
import dev.hnm.workbench.core.playback.PlayWaveform

/**
 * Bridges `core`'s device-agnostic schedule to the real Android `Vibrator`, choosing the best
 * available rendering for the actual hardware:
 *   1. composition primitives (richest — real LRA exposed through the standard API),
 *   2. predefined effects (`EFFECT_CLICK`/`TICK`/… — OEM-tuned, work without amplitude control),
 *   3. a stitched on/off waveform with a perceptible minimum pulse (last resort / sustained buzz).
 *
 * Detection is reported honestly: many phones (gaming phones especially) keep their rich "4D" haptics
 * behind a vendor SDK and only expose a plain on/off motor to AOSP, so "no primitives" does NOT mean
 * the hardware is a cheap ERM.
 */
object AndroidHaptics {

    /** ERM-style motors need ~tens of ms to spin up to a felt level; floor every short pulse to this. */
    private const val MIN_PULSE_MS = 70L

    private val PREDEFINED_EFFECTS = listOf(
        "CLICK" to VibrationEffect.EFFECT_CLICK,
        "TICK" to VibrationEffect.EFFECT_TICK,
        "HEAVY_CLICK" to VibrationEffect.EFFECT_HEAVY_CLICK,
        "DOUBLE_CLICK" to VibrationEffect.EFFECT_DOUBLE_CLICK,
    )

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
            supported.isNotEmpty() || hasAmplitude -> ActuatorType.LRA
            else -> ActuatorType.ERM // best-effort; see actuatorLabel() for the honest caveat
        }
        return HapticCapabilities(
            hasVibrator = hasVibrator,
            hasAmplitudeControl = hasAmplitude,
            supportedPrimitives = supported,
            hasFrequencyControl = false, // Android exposes no public PWLE/frequency API.
            actuatorType = actuator,
        )
    }

    /** Predefined effects the device explicitly reports as supported (UNKNOWN/NO are omitted). */
    fun supportedEffects(vibrator: Vibrator): List<String> {
        val support = vibrator.areEffectsSupported(*PREDEFINED_EFFECTS.map { it.second }.toIntArray())
        return PREDEFINED_EFFECTS.filterIndexed { i, _ ->
            support.getOrNull(i) == Vibrator.VIBRATION_EFFECT_SUPPORT_YES
        }.map { it.first }
    }

    /** An honest label that doesn't over-claim ERM when the API simply isn't exposing the LRA. */
    fun actuatorLabel(caps: HapticCapabilities): String = when {
        !caps.hasVibrator -> "no vibrator"
        caps.supportedPrimitives.isNotEmpty() -> "LRA — composition primitives"
        caps.hasAmplitudeControl -> "LRA — amplitude control"
        else -> "on/off via standard API (rich haptics likely behind a vendor SDK)"
    }

    /**
     * Play a whole pattern, picking the best rendering for the hardware. [commands] is the `core`
     * schedule; [pattern] is used when we can render individual events more faithfully (predefined
     * effects) than the collapsed schedule allows.
     */
    fun playPattern(
        vibrator: Vibrator,
        pattern: HapticAudioPattern,
        commands: List<HapticCommand>,
        handler: Handler,
        onError: (String) -> Unit,
    ) {
        if (commands.isEmpty()) {
            onError("Nothing scheduled — device reports no vibrator?")
            return
        }
        // 1) Native composition primitives.
        if (commands.all { it is PlayPrimitive }) {
            composePrimitives(commands.filterIsInstance<PlayPrimitive>())?.let { vibrateSafely(vibrator, it, onError) }
            return
        }
        // 2) Discrete taps with no primitive support -> OEM-tuned predefined effects, scheduled in time.
        val events = pattern.tracks.filterIsInstance<HapticTrack>().filterNot { it.muted }.flatMap { it.events }
        if (events.isNotEmpty() && events.all { it is Transient || it is Primitive }) {
            val base = SystemClock.uptimeMillis()
            events.sortedBy { it.time }.forEach { event ->
                val effect = predefinedFor(event)
                handler.postAtTime({ vibrateSafely(vibrator, effect, onError) }, base + (event.time * 1000).toLong())
            }
            return
        }
        // 3) Sustained / mixed content -> stitched waveform.
        buildWaveform(commands)?.let { vibrateSafely(vibrator, it, onError) }
            ?: onError("Could not build a vibration for this pattern.")
    }

    /**
     * An unmistakable buzz to confirm the actuator works at all, independent of any pattern.
     */
    fun selfTest(vibrator: Vibrator, handler: Handler, onError: (String) -> Unit) {
        vibrateSafely(vibrator, VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE), onError)
        handler.postDelayed({
            vibrateSafely(vibrator, VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK), onError)
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

    /** Describe the rendering this pattern will actually use on this device (shown under each row). */
    fun renderingSummary(pattern: HapticAudioPattern, commands: List<HapticCommand>): String {
        if (commands.isEmpty()) return "nothing (no vibrator)"
        if (commands.all { it is PlayPrimitive }) {
            return "primitives: " + commands.filterIsInstance<PlayPrimitive>().joinToString(", ") { it.type.name }
        }
        val events = pattern.tracks.filterIsInstance<HapticTrack>().filterNot { it.muted }.flatMap { it.events }
        if (events.isNotEmpty() && events.all { it is Transient || it is Primitive }) {
            return "predefined: " + events.sortedBy { it.time }.joinToString(", ") { effectNameFor(it) }
        }
        return "waveform · ${describe(commands)}"
    }

    private fun effectNameFor(event: HapticEvent): String = when (event) {
        is Transient -> when {
            event.sharpness >= 0.66 -> "TICK"
            event.sharpness >= 0.33 -> "CLICK"
            else -> "HEAVY_CLICK"
        }
        is Primitive -> "CLICK"
        is Continuous -> "BUZZ"
    }

    /** Map an event to an OEM-tuned predefined effect (used when composition primitives are absent). */
    private fun predefinedFor(event: HapticEvent): VibrationEffect = when (event) {
        is Transient -> VibrationEffect.createPredefined(
            when {
                event.sharpness >= 0.66 -> VibrationEffect.EFFECT_TICK
                event.sharpness >= 0.33 -> VibrationEffect.EFFECT_CLICK
                else -> VibrationEffect.EFFECT_HEAVY_CLICK
            },
        )
        is Primitive -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        is Continuous -> VibrationEffect.createOneShot(
            (event.duration * 1000).toLong().coerceAtLeast(MIN_PULSE_MS),
            VibrationEffect.DEFAULT_AMPLITUDE,
        )
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
