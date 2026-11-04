package dev.hnm.workbench.core.widget

import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.Primitive
import dev.hnm.workbench.core.ir.PrimitiveType
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.core.library.FeelEntry
import dev.hnm.workbench.core.library.FeelKind
import dev.hnm.workbench.core.playback.HapticCapabilities
import dev.hnm.workbench.core.playback.HapticCommand
import dev.hnm.workbench.core.playback.PatternRenderer
import dev.hnm.workbench.core.playback.PlayPrimitive

/**
 * How the Android player renders a pattern on a given device, best first: native composition
 * primitives, OEM-tuned predefined effects for purely discrete taps, a stitched amplitude waveform for
 * anything sustained, or nothing when the device reports no vibrator.
 */
enum class RenderPath { PRIMITIVES, PREDEFINED, WAVEFORM, NOTHING }

object RenderPaths {
    /** The player's choice for [pattern], given the schedule [commands] the renderer produced for it. */
    fun of(pattern: HapticAudioPattern, commands: List<HapticCommand>): RenderPath {
        if (commands.isEmpty()) return RenderPath.NOTHING
        if (commands.all { it is PlayPrimitive }) return RenderPath.PRIMITIVES
        val events = pattern.tracks.filterIsInstance<HapticTrack>().filterNot { it.muted }.flatMap { it.events }
        return if (events.isNotEmpty() && events.all { it is Transient || it is Primitive }) {
            RenderPath.PREDEFINED
        } else {
            RenderPath.WAVEFORM
        }
    }
}

/** The library at a glance: how many patterns there are to feel, by kind and by how this device plays them. */
data class LibraryGlance(
    val total: Int,
    val byKind: List<Pair<FeelKind, Int>>,
    val byPath: List<Pair<RenderPath, Int>>,
)

object LibraryGlances {
    /** Counts in a fixed order (gallery order for kinds, best-first for paths); empty groups are left out. */
    fun of(
        entries: List<FeelEntry>,
        capabilities: HapticCapabilities,
        renderer: PatternRenderer = DefaultPatternRenderer(),
    ): LibraryGlance {
        val kinds = entries.groupingBy { it.kind }.eachCount()
        val paths = entries.groupingBy { RenderPaths.of(it.pattern, renderer.scheduleHaptics(it.pattern, capabilities)) }
            .eachCount()
        return LibraryGlance(
            total = entries.size,
            byKind = FeelKind.entries.mapNotNull { k -> kinds[k]?.let { k to it } },
            byPath = RenderPath.entries.mapNotNull { p -> paths[p]?.let { p to it } },
        )
    }
}

/**
 * What this phone's vibrator reports, in the player's own honest terms: composition primitives, then
 * amplitude control, then plain on/off through the standard API (many phones keep richer haptics behind
 * a vendor SDK, so on/off is not a verdict on the hardware), or no vibrator at all.
 */
enum class HapticTier { PRIMITIVES, AMPLITUDE, ON_OFF, NONE }

data class PhoneGlance(
    val tier: HapticTier,
    val supportedPrimitives: Set<PrimitiveType>,
    val hasAmplitudeControl: Boolean,
    val supportedEffects: List<String>,
) {
    val primitivesTotal: Int get() = PrimitiveType.entries.size

    /** Every primitive in a fixed order, each with whether this device reports it. */
    val primitiveRows: List<Pair<PrimitiveType, Boolean>>
        get() = PrimitiveType.entries.map { it to (it in supportedPrimitives) }
}

object PhoneGlances {
    fun tierOf(caps: HapticCapabilities): HapticTier = when {
        !caps.hasVibrator -> HapticTier.NONE
        caps.supportedPrimitives.isNotEmpty() -> HapticTier.PRIMITIVES
        caps.hasAmplitudeControl -> HapticTier.AMPLITUDE
        else -> HapticTier.ON_OFF
    }

    fun of(caps: HapticCapabilities, supportedEffects: List<String>): PhoneGlance = PhoneGlance(
        tier = tierOf(caps),
        supportedPrimitives = if (caps.hasVibrator) caps.supportedPrimitives else emptySet(),
        hasAmplitudeControl = caps.hasVibrator && caps.hasAmplitudeControl,
        supportedEffects = if (caps.hasVibrator) supportedEffects else emptyList(),
    )
}
