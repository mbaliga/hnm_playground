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
}
