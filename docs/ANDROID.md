# Android backend reference (M1)

This is the copy-ready glue for `:backend-android`. It is documented here (rather than committed as a
module) because the build image has no Android SDK — but it is deliberately thin: `core` already
produces the `HapticCommand` list and the audio `FloatStream`, so the backend is a near-mechanical
mapping onto the `Vibrator` API. `minSdk 26`, with capability-gated paths above it.

## CapabilityProbe

Fills `HapticCapabilities` so the UI can surface what the device can do and drive graceful
degradation. The ERM-vs-LRA split is a heuristic (Android doesn't expose it) — label it as such in
the UI.

```kotlin
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dev.hnm.workbench.core.ir.PrimitiveType
import dev.hnm.workbench.core.playback.ActuatorType
import dev.hnm.workbench.core.playback.HapticCapabilities

object CapabilityProbe {

    fun probe(context: Context): HapticCapabilities {
        val vibrator = vibrator(context)
        val hasVibrator = vibrator?.hasVibrator() == true
        val hasAmplitude = hasVibrator && vibrator!!.hasAmplitudeControl()

        val supported: Set<PrimitiveType> =
            if (hasVibrator && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                primitiveMap.filter { (_, androidId) ->
                    vibrator!!.areAllPrimitivesSupported(androidId)
                }.keys
            } else emptySet()

        // PWLE / envelope (true frequency control) only on the newest hardware.
        val hasFrequency = hasVibrator && Build.VERSION.SDK_INT >= 36 /* VibrationEffect.WaveformEnvelopeBuilder era */

        // Heuristic: amplitude control + primitive support ≈ LRA; neither ≈ ERM.
        val actuator = when {
            !hasVibrator -> ActuatorType.NONE
            hasFrequency -> ActuatorType.WIDEBAND
            hasAmplitude && supported.isNotEmpty() -> ActuatorType.LRA
            else -> ActuatorType.ERM
        }

        return HapticCapabilities(hasVibrator, hasAmplitude, supported, hasFrequency, actuator)
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    val primitiveMap: Map<PrimitiveType, Int> = buildMap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            put(PrimitiveType.CLICK, VibrationEffect.Composition.PRIMITIVE_CLICK)
            put(PrimitiveType.TICK, VibrationEffect.Composition.PRIMITIVE_TICK)
            put(PrimitiveType.LOW_TICK, VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
            put(PrimitiveType.THUD, VibrationEffect.Composition.PRIMITIVE_THUD)
            put(PrimitiveType.SPIN, VibrationEffect.Composition.PRIMITIVE_SPIN)
            put(PrimitiveType.QUICK_RISE, VibrationEffect.Composition.PRIMITIVE_QUICK_RISE)
            put(PrimitiveType.SLOW_RISE, VibrationEffect.Composition.PRIMITIVE_SLOW_RISE)
            put(PrimitiveType.QUICK_FALL, VibrationEffect.Composition.PRIMITIVE_QUICK_FALL)
        }
    }
}
```

## AndroidHapticBackend

Consumes `PatternRenderer.scheduleHaptics(...)` — the discrete-command path — and lets the OS
schedule each command. Commands carry absolute times; the shared `TransportClock` plus `latencyComp`
keep them aligned with audio (§6).

```kotlin
import android.os.VibrationEffect
import android.os.Vibrator
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.playback.*

class AndroidHapticBackend(
    private val vibrator: Vibrator,
    override val capabilities: HapticCapabilities,
    private val latencyComp: Double = 0.0, // seconds; tune per device so haptics meet the audio
) : HapticBackend {

    override fun play(pattern: HapticAudioPattern, renderer: PatternRenderer, clock: TransportClock) {
        val commands = renderer.scheduleHaptics(pattern, capabilities)
        for (cmd in commands) {
            clock.scheduleAt(cmd.atSeconds, latencyComp) { fire(cmd) }
        }
    }

    private fun fire(cmd: HapticCommand) {
        val effect = when (cmd) {
            is PlayPrimitive -> capabilities.primitiveId(cmd.type)?.let { id ->
                VibrationEffect.startComposition().addPrimitive(id, cmd.scale, 0).compose()
            }
            is PlayWaveform -> VibrationEffect.createWaveform(cmd.timingsMs, cmd.amplitudes, cmd.repeat)
            is PlayOneShot -> VibrationEffect.createOneShot(cmd.durationMs, cmd.amplitude)
        }
        effect?.let { vibrator.vibrate(it) }
    }

    override fun stop() = vibrator.cancel()

    // Map the IR PrimitiveType to the Android constant via CapabilityProbe.primitiveMap.
    private fun HapticCapabilities.primitiveId(type: dev.hnm.workbench.core.ir.PrimitiveType): Int? =
        CapabilityProbe.primitiveMap[type]
}
```

## Degrade rules (already enforced by `core`)

`scheduleHaptics` honours the capability flags, so the backend above doesn't special-case hardware:

- **No amplitude control** → `Continuous` events come back as a single on/off `PlayOneShot`.
- **No primitive support** → primitives/transients come back synthesized as `PlayOneShot`s.
- **No frequency control** → sharpness is dropped from the discrete signal but still used for
  primitive *selection*.

These paths are covered by `ScheduleHapticsTest` in `core`, so the ERM degrade path is validated
without a device.

## M2 audio on Android

`renderAudio(...)` returns the same `FloatStream` the desktop backend consumes; the Android audio
backend writes it to an `AudioTrack` in `MODE_STREAM`. Share the one `TransportClock` with
`AndroidHapticBackend` so sound and haptics stay coincident.
```
