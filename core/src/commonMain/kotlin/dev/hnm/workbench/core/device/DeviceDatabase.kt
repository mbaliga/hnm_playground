package dev.hnm.workbench.core.device

import dev.hnm.workbench.core.ir.PrimitiveType
import dev.hnm.workbench.core.playback.ActuatorType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A small, growable catalog of real-device haptic capabilities. The seed rows below are the reference
 * set the target-device simulator draws from; users can probe their own phone (Android) and append the
 * resulting [DeviceProfile] to grow the catalog. Persistence is plain JSON so a community index can be
 * fetched/merged later.
 *
 * Honesty note: per-model **resonant frequencies are approximate/typical** (most phone LRAs sit ~150–180
 * Hz; published per-model figures are rare), and primitive/amplitude support reflects what the AOSP API
 * exposes — many gaming phones keep richer "4D" haptics behind a vendor SDK, so a row can understate real
 * hardware. Each row carries a [DeviceProfile.source] and [DeviceProfile.notes] so this stays auditable.
 */
class DeviceDatabase(rows: List<DeviceProfile> = SEED) {

    private val byId = LinkedHashMap<String, DeviceProfile>().apply { rows.forEach { put(it.id, it) } }

    val all: List<DeviceProfile> get() = byId.values.toList()
    val size: Int get() = byId.size

    fun get(id: String): DeviceProfile? = byId[id]

    fun byPlatform(platform: DeviceProfile.Platform): List<DeviceProfile> =
        all.filter { it.platform == platform }

    /** Add or replace a row (e.g. a freshly probed device). Returns this for chaining. */
    fun upsert(profile: DeviceProfile): DeviceDatabase = apply { byId[profile.id] = profile }

    fun toJson(): String = json.encodeToString(ListSerializer(DeviceProfile.serializer()), all)

    companion object {
        private val json = Json { prettyPrint = true; prettyPrintIndent = "  "; encodeDefaults = false }

        fun fromJson(text: String): DeviceDatabase =
            DeviceDatabase(json.decodeFromString(ListSerializer(DeviceProfile.serializer()), text))

        // Full modern Android composition-primitive set (API 31).
        private val FULL_PRIMITIVES = PrimitiveType.entries.toSet()
        private val ANDROID_EFFECTS = listOf("CLICK", "TICK", "HEAVY_CLICK", "DOUBLE_CLICK")

        /** Reference rows spanning the capability tiers users actually ship against. */
        val SEED: List<DeviceProfile> = listOf(
            DeviceProfile(
                id = "google-pixel-8",
                manufacturer = "Google",
                model = "Pixel 8",
                marketName = "Google Pixel 8",
                platform = DeviceProfile.Platform.ANDROID,
                os = "Android 14",
                actuatorType = ActuatorType.LRA,
                hasAmplitudeControl = true,
                supportedPrimitives = FULL_PRIMITIVES,
                supportedEffects = ANDROID_EFFECTS,
                hasFrequencyControl = false,
                hasEnvelopeControl = false,
                resonantFrequencyHz = 150.0,
                qFactor = 14.0,
                notes = "Tuned LRA, full composition primitives. Resonance approximate.",
            ),
            DeviceProfile(
                id = "google-pixel-6",
                manufacturer = "Google",
                model = "Pixel 6",
                marketName = "Google Pixel 6",
                platform = DeviceProfile.Platform.ANDROID,
                os = "Android 13",
                actuatorType = ActuatorType.LRA,
                hasAmplitudeControl = true,
                supportedPrimitives = FULL_PRIMITIVES,
                supportedEffects = ANDROID_EFFECTS,
                resonantFrequencyHz = 150.0,
                notes = "Strong reference LRA. Resonance approximate.",
            ),
            DeviceProfile(
                id = "samsung-galaxy-s23",
                manufacturer = "Samsung",
                model = "SM-S911",
                marketName = "Samsung Galaxy S23",
                platform = DeviceProfile.Platform.ANDROID,
                os = "Android 14",
                actuatorType = ActuatorType.LRA,
                hasAmplitudeControl = true,
                supportedPrimitives = FULL_PRIMITIVES,
                supportedEffects = ANDROID_EFFECTS,
                resonantFrequencyHz = 180.0,
                notes = "LRA with composition primitives. Resonance approximate.",
            ),
            DeviceProfile(
                id = "android-budget-erm",
                manufacturer = "Generic",
                model = "Budget ERM phone",
                marketName = "Budget Android (ERM)",
                platform = DeviceProfile.Platform.ANDROID,
                os = "Android 12",
                actuatorType = ActuatorType.ERM,
                hasAmplitudeControl = false,
                supportedPrimitives = emptySet(),
                supportedEffects = emptyList(),
                notes = "Rotational ERM: on/off only, no amplitude, no primitives. The degrade target.",
            ),
            DeviceProfile(
                id = "android-16-wideband-ref",
                manufacturer = "Generic",
                model = "Wideband reference",
                marketName = "Wideband LRA (Android 16)",
                platform = DeviceProfile.Platform.ANDROID,
                os = "Android 16",
                actuatorType = ActuatorType.WIDEBAND,
                hasAmplitudeControl = true,
                supportedPrimitives = FULL_PRIMITIVES,
                supportedEffects = ANDROID_EFFECTS,
                hasFrequencyControl = true,
                hasEnvelopeControl = true,
                resonantFrequencyHz = 160.0,
                qFactor = 6.0,
                notes = "Broad-bandwidth LRA exposing envelope/PWLE APIs (areEnvelopeEffectsSupported).",
            ),
            DeviceProfile(
                id = "apple-iphone-15",
                manufacturer = "Apple",
                model = "iPhone15,4",
                marketName = "Apple iPhone 15",
                platform = DeviceProfile.Platform.IOS,
                os = "iOS 17",
                actuatorType = ActuatorType.LRA,
                hasAmplitudeControl = true,           // Core Haptics intensity 0..1
                supportedPrimitives = emptySet(),     // composition primitives are Android-only
                supportedEffects = emptyList(),
                hasFrequencyControl = true,            // Core Haptics sharpness ≈ frequency content
                hasEnvelopeControl = true,             // dynamic parameters / parameter curves
                resonantFrequencyHz = 150.0,
                notes = "Taptic Engine LRA via Core Haptics; intensity+sharpness, parameter curves. Uniform across iPhones.",
            ),
            DeviceProfile(
                id = "sony-dualsense",
                manufacturer = "Sony",
                model = "DualSense",
                marketName = "PS5 DualSense",
                platform = DeviceProfile.Platform.CONTROLLER,
                os = "PS5",
                actuatorType = ActuatorType.CONTROLLER_VOICECOIL,
                hasAmplitudeControl = true,
                supportedPrimitives = emptySet(),
                hasFrequencyControl = true,            // voice-coil: full amplitude+frequency waveform
                hasEnvelopeControl = true,
                notes = "Voice-coil actuators (audio-driven) + adaptive triggers. Wideband waveform path.",
            ),
            DeviceProfile(
                id = "desktop-none",
                manufacturer = "Generic",
                model = "Desktop",
                marketName = "Desktop (no actuator)",
                platform = DeviceProfile.Platform.ANDROID,
                os = "—",
                actuatorType = ActuatorType.NONE,
                hasAmplitudeControl = false,
                notes = "No vibrator: audio-only / export-only preview.",
            ),
        )

        fun seeded(): DeviceDatabase = DeviceDatabase(SEED)
    }
}
