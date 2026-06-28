package dev.hnm.workbench.core.device

import dev.hnm.workbench.core.ir.PrimitiveType
import dev.hnm.workbench.core.playback.ActuatorType
import dev.hnm.workbench.core.playback.HapticCapabilities
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A concrete device's measured/known haptic capabilities — the row type of the device database.
 *
 * Why this exists: there is no public, crowd-sourced catalog of which phones expose which haptic
 * features (LRA vs ERM, amplitude control, composition primitives, resonant frequency, envelope/PWLE
 * support). Android only tells you at runtime; iOS exposes a single boolean. So we capture the runtime
 * probe into a portable, serializable profile that (a) seeds the target-device simulator and (b) can be
 * exported by users to grow a shared database.
 *
 * Field provenance mirrors the real APIs:
 *  - [hasAmplitudeControl]   ← Android `Vibrator.hasAmplitudeControl()` (API 26)
 *  - [supportedPrimitives]   ← `areAllPrimitivesSupported(...)` (API 30/31)
 *  - [supportedEffects]      ← `areEffectsSupported(...)` (API 30)
 *  - [resonantFrequencyHz]   ← `getResonantFrequency()` (API 34; NaN if unknown)
 *  - [qFactor]               ← `getQFactor()` (API 34)
 *  - [hasEnvelopeControl]    ← `areEnvelopeEffectsSupported()` (API 36)
 *  - [hasFrequencyControl]   ← PWLE/frequency-profile capable (`getFrequencyProfile()`, API 36)
 * iOS rows are filled from Core Haptics' coarse `capabilitiesForHardware()` plus known Taptic specs.
 */
@Serializable
data class DeviceProfile(
    val id: String,                       // stable slug, e.g. "google-pixel-8"
    val manufacturer: String,
    val model: String,
    @SerialName("marketName") val marketName: String = model,
    val platform: Platform,
    val os: String = "",                  // e.g. "Android 14" / "iOS 17"
    val actuatorType: ActuatorType,
    val hasAmplitudeControl: Boolean,
    val supportedPrimitives: Set<PrimitiveType> = emptySet(),
    val supportedEffects: List<String> = emptyList(), // CLICK/TICK/HEAVY_CLICK/DOUBLE_CLICK
    val hasFrequencyControl: Boolean = false,
    val hasEnvelopeControl: Boolean = false,
    val resonantFrequencyHz: Double? = null,
    val qFactor: Double? = null,
    val source: Source = Source.SEED,
    val notes: String = "",
) {
    /** Collapse this profile down to the renderer's capability model. */
    fun toCapabilities(): HapticCapabilities = HapticCapabilities(
        hasVibrator = actuatorType != ActuatorType.NONE,
        hasAmplitudeControl = hasAmplitudeControl,
        supportedPrimitives = supportedPrimitives,
        hasFrequencyControl = hasFrequencyControl,
        actuatorType = actuatorType,
    )

    /** One-line human summary for pickers and the device report. */
    fun summary(): String {
        val prim = if (supportedPrimitives.isEmpty()) "no primitives" else "${supportedPrimitives.size} primitives"
        val freq = resonantFrequencyHz?.let { "~${it.toInt()}Hz" } ?: "freq n/a"
        val amp = if (hasAmplitudeControl) "amp" else "on/off"
        val env = if (hasEnvelopeControl) " · envelope" else ""
        return "$actuatorType · $amp · $prim · $freq$env"
    }

    enum class Platform { ANDROID, IOS, CONTROLLER }

    /** Where the row came from — distinguishes hand-seeded reference rows from real user probes. */
    enum class Source { SEED, PROBED, COMMUNITY }
}
