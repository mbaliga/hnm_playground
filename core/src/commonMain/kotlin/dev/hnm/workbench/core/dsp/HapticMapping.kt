package dev.hnm.workbench.core.dsp

import dev.hnm.workbench.core.ir.PrimitiveType
import kotlin.math.roundToInt

/**
 * The explicit, testable IR -> backend mappings from §6 & §8. Sharpness has no native Android knob on
 * most devices, so the intensity->amplitude / sharpness->primitive-or-frequency mapping lives here in
 * one place rather than being assumed at each call site.
 */
object HapticMapping {

    /** Android amplitude scale, 0..255. */
    fun intensityToAmplitude255(intensity: Double): Int =
        (intensity.coerceIn(0.0, 1.0) * 255.0).roundToInt().coerceIn(0, 255)

    /** Voice-coil carrier frequency (Hz) for a perceptual sharpness: dull thud -> crisp tick. */
    fun sharpnessToCarrierHz(sharpness: Double): Double {
        val s = sharpness.coerceIn(0.0, 1.0)
        return 80.0 + s * 220.0 // ~80 Hz dull .. ~300 Hz crisp
    }

    /**
     * Target frequency (Hz) for the wideband/PWLE envelope path. Same perceptual axis as
     * [sharpnessToCarrierHz] but expressed for actuators with a real frequency band so a "sharp"
     * continuous event actually rises in pitch rather than only getting a different carrier.
     */
    fun sharpnessToEnvelopeHz(sharpness: Double): Float = sharpnessToCarrierHz(sharpness).toFloat()

    /**
     * Composition-primitive selection for a transient when no frequency control exists: sharp -> TICK,
     * mid -> CLICK, dull -> THUD. Honoured only for primitives the device actually supports.
     */
    fun transientToPrimitive(sharpness: Double, supported: Set<PrimitiveType>): PrimitiveType? {
        val ordered = when {
            sharpness >= 0.66 -> listOf(PrimitiveType.TICK, PrimitiveType.CLICK, PrimitiveType.THUD)
            sharpness >= 0.33 -> listOf(PrimitiveType.CLICK, PrimitiveType.TICK, PrimitiveType.THUD)
            else -> listOf(PrimitiveType.THUD, PrimitiveType.LOW_TICK, PrimitiveType.CLICK)
        }
        return ordered.firstOrNull { it in supported }
    }

    /**
     * A primitive expressed as a transient's (intensity, sharpness): how the renderer synthesizes a
     * primitive the hardware doesn't support, and how the home-screen sketch draws one. [scale] is the
     * intensity; the sharpness is the primitive's characteristic crispness.
     */
    fun primitiveAsTransient(type: PrimitiveType, scale: Double): Pair<Double, Double> = when (type) {
        PrimitiveType.TICK -> scale to 0.95
        PrimitiveType.LOW_TICK -> scale to 0.6
        PrimitiveType.CLICK -> scale to 0.5
        PrimitiveType.THUD -> scale to 0.15
        PrimitiveType.SPIN -> scale to 0.4
        PrimitiveType.QUICK_RISE -> scale to 0.7
        PrimitiveType.SLOW_RISE -> scale to 0.5
        PrimitiveType.QUICK_FALL -> scale to 0.6
    }
}
