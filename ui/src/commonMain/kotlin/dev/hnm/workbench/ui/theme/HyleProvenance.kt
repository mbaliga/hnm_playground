package dev.hnm.workbench.ui.theme

/**
 * Ported from `mbaliga/Hyle-Design-System`'s `hyle/src/main/java/dev/aarso/hyle/Hyle.kt` — the
 * "single canonical where-did-this-come-from idiom": **warm radium = on-device, cold cyan = from
 * elsewhere**. Semantics and values are kept identical to the source (see [HyleTokens] for provenance
 * on why this is a vendored copy, not a live dependency).
 *
 * Colour-blind-safe by construction (WCAG 1.4.1): [Provenance.hue] is never the sole signal — every
 * source also carries a distinct [Glyph] silhouette, so it reads under red-green *and* blue-yellow
 * colour-blindness and in greyscale (the two hues also differ in luminance). Rule carried over from the
 * source: never encode provenance by hue alone — always pair the hue with the glyph.
 */

/** ARGB colour as a plain Long (0xAARRGGBB) — matches the source repo's token format. */
typealias Argb = Long

/** How a surface should be rendered: inert-until-touched, or self-emitting on a breathing pulse. */
sealed interface Finish {
    /** Reflective: of-here, inert until touched. No emission — the default for ordinary chrome. */
    data object Reflective : Finish

    /** Radiant: watched / from-elsewhere. Emits its own light at [tint], breathing per [pulse]. */
    data class Radiant(val tint: Argb, val pulse: Pulse = Pulse.WATCHED) : Finish
}

/**
 * "Heartbeat, not weather" — the motion rule. Ambient emission breathes on a slow, regular,
 * low-amplitude cycle that *means* "alive / connected / watched," never aperiodic churn.
 */
data class Pulse(
    val periodMs: Int,
    val minAlphaPct: Int,
    val maxAlphaPct: Int,
) {
    init {
        require(periodMs > 0) { "periodMs must be > 0" }
        require(minAlphaPct in 0..maxAlphaPct && maxAlphaPct <= 100) { "alpha must satisfy 0 <= min <= max <= 100" }
    }

    companion object {
        /** A calm ~2.4s breath, like a connected-status light. */
        val WATCHED = Pulse(periodMs = 2400, minAlphaPct = 42, maxAlphaPct = 78)

        /** Fully still — the default when there is nothing to say. */
        val STILL = Pulse(periodMs = 1, minAlphaPct = 100, maxAlphaPct = 100)
    }
}

/** The two provenance hues. Deliberately not a friendly success-green — a watched object isn't "all good." */
object RadiantHues {
    /** Pale, uncanny yellow-green — reads *other*. On-device provenance. */
    const val RADIUM: Argb = HyleTokens.Color.ProvenanceNative

    /** Cold, clinical cyan — reads *monitored*. Cloud/from-elsewhere provenance. */
    const val COLD_CYAN: Argb = HyleTokens.Color.ProvenanceCloud
}

/** The redundant non-colour channel that keeps [Provenance] colour-blind-safe. */
enum class Glyph { FILLED_DISC, HOLLOW_RING }

/** Where a piece of UI-visible output came from — always paired hue + glyph, never hue alone. */
sealed interface Provenance {
    val hue: Argb
    val glyph: Glyph
    val finish: Finish

    /** Of-here: computed on this device (e.g. the on-device assistant engine). Warm radium + filled disc. */
    data object OnDevice : Provenance {
        override val hue: Argb = RadiantHues.RADIUM
        override val glyph: Glyph = Glyph.FILLED_DISC
        override val finish: Finish = Finish.Radiant(hue, Pulse.WATCHED)
    }

    /** From-elsewhere: a watched cloud provider. Cold cyan + hollow ring. */
    data object Cloud : Provenance {
        override val hue: Argb = RadiantHues.COLD_CYAN
        override val glyph: Glyph = Glyph.HOLLOW_RING
        override val finish: Finish = Finish.Radiant(hue, Pulse.WATCHED)
    }

    companion object {
        val all: List<Provenance> = listOf(OnDevice, Cloud)
    }
}
