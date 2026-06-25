package dev.hnm.workbench.core.design

import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.Envelope
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticEvent
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.Primitive
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.core.library.BuiltInPatterns

/**
 * The result of an AI generation/edit: the new [pattern], a plain-language [explanation] of what was
 * made and why (this doubles as the app's living walkthrough — every action explains itself), and the
 * [source] that produced it ("on-device" or "cloud").
 */
data class GenerationResult(
    val pattern: HapticAudioPattern,
    val explanation: String,
    val source: String,
)

/**
 * The seam the UI talks to. Implementations turn a free-text prompt (optionally editing the [current]
 * pattern) into a [GenerationResult]. `suspend` so a cloud-backed implementation can do network I/O;
 * the on-device one returns immediately.
 *
 * This is the "relocated AI" the project's research doc argues for: AI operates on *parameters inside a
 * perceptual space*, not on raw signals or across the language gap alone — the deterministic mapping
 * from intent to IR is what actually synthesizes the felt signal.
 */
interface PatternGenerator {
    suspend fun generate(prompt: String, current: HapticAudioPattern?): GenerationResult
}

/**
 * Tries [cloud] first (richer free-text understanding) and falls back to [onDevice] on any failure or
 * when no cloud generator is wired. This is the "hybrid" path: the on-device generator always works
 * (offline, no key), and the cloud one is a strict upgrade when a network + key are available.
 */
class HybridPatternGenerator(
    private val onDevice: PatternGenerator = OnDevicePatternGenerator(),
    private val cloud: PatternGenerator? = null,
) : PatternGenerator {
    override suspend fun generate(prompt: String, current: HapticAudioPattern?): GenerationResult {
        if (cloud != null) {
            try {
                return cloud.generate(prompt, current)
            } catch (_: Throwable) {
                // Network/parse/availability failure — fall through to the always-works path.
            }
        }
        return onDevice.generate(prompt, current)
    }
}

/**
 * A deterministic, offline intent→pattern engine. It reads a small lexicon of perceptual words out of
 * the prompt and either (a) **edits** the current pattern when the prompt is an adjustment ("softer",
 * "longer", "sharper", "more"), or (b) **creates** a new pattern by routing to the best generator —
 * a named built-in, a motion primitive, a material strike, a texture field, or a from-scratch synthesis
 * of transients/buzz from the felt dimensions it detected.
 *
 * No model weights, no network — every mapping is an explicit, inspectable rule, which is exactly why
 * it can ship in the APK and run instantly. A learned model can later replace it behind [PatternGenerator].
 */
class OnDevicePatternGenerator : PatternGenerator {

    override suspend fun generate(prompt: String, current: HapticAudioPattern?): GenerationResult {
        val p = prompt.trim().lowercase()
        if (p.isEmpty()) {
            return GenerationResult(
                current ?: BuiltInPatterns.TAP,
                "Type what you want to feel — e.g. \"urgent alert\", \"gentle tick\", \"metal tap\", or an edit like \"make it softer\".",
                "on-device",
            )
        }

        // 1) Edit intent — adjust the existing pattern in place.
        if (current != null) {
            val edit = tryEdit(p, current)
            if (edit != null) return edit
        }

        // 2) Material strike — a concrete physical source wins over generic "tap/click" built-ins
        //    (so "metal tap" is a metal strike, not a bare tap).
        materialFor(p)?.let { preset ->
            return GenerationResult(
                ModalSynth.toPattern(preset.material, name = displayName(prompt)),
                "Struck a ${preset.displayName.lowercase()} object: one modal model drives both the contact sound and the felt ring-down. Edit it with \"shorter\" or \"brighter\".",
                "on-device",
            )
        }

        // 3) Texture field (scrub-a-surface) — also a concrete source, checked before built-ins.
        if (anyOf(p, "rough", "coarse", "grain", "grainy", "texture", "scrub", "sandpaper", "gritty", "fine", "smooth surface")) {
            val roughness = when {
                anyOf(p, "very rough", "coarse", "gritty", "sandpaper") -> 0.9
                anyOf(p, "rough", "grain", "grainy") -> 0.7
                anyOf(p, "fine") -> 0.4
                anyOf(p, "smooth") -> 0.2
                else -> 0.6
            }
            val type = when {
                anyOf(p, "cell", "worley", "bumpy", "pebble") -> TextureFieldType.WORLEY
                anyOf(p, "natural", "organic", "cloth", "fabric") -> TextureFieldType.FBM
                anyOf(p, "regular", "even") -> TextureFieldType.VALUE
                else -> TextureFieldType.PERLIN
            }
            val velocity = if (anyOf(p, "fast", "quick")) 1.6 else if (anyOf(p, "slow")) 0.6 else 1.0
            return GenerationResult(
                TextureFields.toPattern(TextureField(type = type, roughness = roughness), velocity = velocity).copy(name = displayName(prompt)),
                "Generated a ${type.displayName.lowercase()} texture field (roughness ${pct(roughness)}) scrubbed at ${vel(velocity)}. Roughness maps to spatial frequency; scrub speed sets the felt temporal frequency.",
                "on-device",
            )
        }

        // 4) Motion primitive by name.
        motionFor(p)?.let { mp ->
            return GenerationResult(
                MotionPrimitives.toPattern(mp).copy(name = displayName(prompt)),
                "Used the \"${mp.displayName}\" motion primitive — ${mp.description}. The motion curve is the haptic envelope.",
                "on-device",
            )
        }

        // 5) Named / semantic built-ins (effect words + generic tap/click catch-alls).
        builtinFor(p)?.let { (pat, why) ->
            return GenerationResult(pat.copy(name = displayName(prompt)), why, "on-device")
        }

        // 6) From-scratch synthesis from felt dimensions.
        return synthesize(p, displayName(prompt))
    }

    // --- editing ---------------------------------------------------------------

    private fun tryEdit(p: String, current: HapticAudioPattern): GenerationResult? {
        val track = current.tracks.filterIsInstance<HapticTrack>().firstOrNull() ?: return null
        val notes = mutableListOf<String>()

        var intensityScale = 1.0
        var sharpnessDelta = 0.0
        var timeScale = 1.0
        var durationScale = 1.0
        var matched = false

        if (anyOf(p, "softer", "gentler", "weaker", "quieter", "lighter", "less intense", "calmer")) {
            intensityScale *= 0.7; matched = true; notes += "softened"
        }
        if (anyOf(p, "stronger", "harder", "more intense", "louder", "punchier", "bigger")) {
            intensityScale *= 1.35; matched = true; notes += "strengthened"
        }
        if (anyOf(p, "sharper", "crisper", "snappier", "tighter")) {
            sharpnessDelta += 0.25; matched = true; notes += "sharpened"
        }
        if (anyOf(p, "duller", "smoother", "softer edge", "rounder", "muffled")) {
            sharpnessDelta -= 0.25; matched = true; notes += "dulled"
        }
        if (anyOf(p, "longer", "sustained", "stretch")) {
            timeScale *= 1.4; durationScale *= 1.5; matched = true; notes += "lengthened"
        }
        if (anyOf(p, "shorter", "tighter", "compressed")) {
            timeScale *= 0.7; durationScale *= 0.7; matched = true; notes += "shortened"
        }
        if (anyOf(p, "faster", "quicker", "snappier")) {
            timeScale *= 0.6; matched = true; notes += "sped up"
        }
        if (anyOf(p, "slower")) {
            timeScale *= 1.6; matched = true; notes += "slowed down"
        }
        // Bare "more"/"less" as a catch-all intensity nudge if nothing else matched.
        if (!matched && anyOf(p, "more")) { intensityScale *= 1.3; matched = true; notes += "intensified" }
        if (!matched && anyOf(p, "less")) { intensityScale *= 0.75; matched = true; notes += "eased" }

        if (!matched) return null

        val edited = track.events.map { e ->
            scaleEvent(e, intensityScale, sharpnessDelta, timeScale, durationScale)
        }
        val newPattern = current.copy(
            tracks = current.tracks.map { if (it === track) track.copy(events = edited) else it },
        )
        return GenerationResult(
            newPattern,
            "Edited \"${current.name}\": ${notes.joinToString(", ")}. (Adjustments apply to every event; tweak individual events in the Inspector.)",
            "on-device",
        )
    }

    private fun scaleEvent(
        e: HapticEvent,
        intensity: Double,
        sharpnessDelta: Double,
        timeScale: Double,
        durationScale: Double,
    ): HapticEvent = when (e) {
        is Transient -> e.copy(
            time = e.time * timeScale,
            intensity = (e.intensity * intensity).coerceIn(0.0, 1.0),
            sharpness = (e.sharpness + sharpnessDelta).coerceIn(0.0, 1.0),
        )
        is Continuous -> e.copy(
            time = e.time * timeScale,
            duration = (e.duration * durationScale).coerceAtLeast(0.02),
            intensity = (e.intensity * intensity).coerceIn(0.0, 1.0),
            sharpness = (e.sharpness + sharpnessDelta).coerceIn(0.0, 1.0),
        )
        is Primitive -> e.copy(
            time = e.time * timeScale,
            scale = (e.scale * intensity).coerceIn(0.0, 1.0),
        )
    }

    // --- creation routing ------------------------------------------------------

    private fun builtinFor(p: String): Pair<HapticAudioPattern, String>? = when {
        anyOf(p, "success", "complete", "done", "achievement") ->
            BuiltInPatterns.SUCCESS to "Read this as a success cue — a rising tick into a short swell, the shape of \"task complete.\""
        anyOf(p, "error", "fail", "wrong", "denied", "reject") ->
            BuiltInPatterns.ERROR to "Read this as an error — two hard, dull thumps that are unmistakably \"something went wrong.\""
        anyOf(p, "warn", "caution", "careful") ->
            BuiltInPatterns.WARNING to "Read this as a warning — three ascending pulses that say \"pay attention.\""
        anyOf(p, "urgent", "emergency", "critical") ->
            urgent() to "Built an urgent alert — three strong, sharp bursts in quick succession. Make it calmer with \"softer\" or \"slower.\""
        anyOf(p, "notification", "notify", "incoming", "message", "ping") ->
            BuiltInPatterns.NOTIFICATION to "Read this as a notification — a soft long buzz with a finishing tick, non-urgent."
        anyOf(p, "heartbeat", "pulse", "alive", "heart") ->
            BuiltInPatterns.HEARTBEAT to "Built a heartbeat — a lub-dub pair then silence."
        anyOf(p, "double tap", "double-tap", "double") ->
            BuiltInPatterns.DOUBLE_TAP to "Two sharp taps in quick succession."
        anyOf(p, "triple", "three tick") ->
            BuiltInPatterns.TRIPLE_TICK to "Three quick ticks — \"sent / delivered.\""
        anyOf(p, "swipe", "slide") ->
            BuiltInPatterns.SWIPE to "A directional buzz that decays — like a swipe completing."
        anyOf(p, "snap", "rubber band", "rubber-band") ->
            BuiltInPatterns.SNAP to "A spring-back snap — hard hit then quick decay."
        anyOf(p, "toggle on", "switch on", "turn on", "enable") ->
            BuiltInPatterns.TOGGLE_ON to "Toggle ON — a building rise that latches with a crisp close."
        anyOf(p, "toggle off", "switch off", "turn off", "disable") ->
            BuiltInPatterns.TOGGLE_OFF to "Toggle OFF — firm release into a softer close."
        anyOf(p, "confirm", "ok", "accept", "yes") ->
            BuiltInPatterns.CONFIRM to "Read this as a confirm — a sharp tick then a softer click."
        anyOf(p, "selection", "scroll", "cursor", "hover") ->
            BuiltInPatterns.SELECTION to "A very soft, crisp tick — cursor/scroll-step feedback."
        anyOf(p, "tap", "click", "press", "button") ->
            BuiltInPatterns.TAP to "A single clean tap — minimal acknowledgement."
        else -> null
    }

    private fun materialFor(p: String): MaterialPreset? = when {
        anyOf(p, "metal", "steel", "metallic", "clink", "bell") -> MaterialPreset.METAL
        anyOf(p, "wood", "wooden", "knock") -> MaterialPreset.WOOD
        anyOf(p, "glass", "crystal") -> MaterialPreset.GLASS
        anyOf(p, "rubber", "rubbery") -> MaterialPreset.RUBBER
        anyOf(p, "ceramic", "porcelain", "plate") -> MaterialPreset.CERAMIC
        else -> null
    }

    private fun motionFor(p: String): MotionPrimitive? = when {
        anyOf(p, "breath", "breathe", "breathing") -> MotionPrimitive.BREATH
        anyOf(p, "stir", "rolling", "churn") -> MotionPrimitive.STIR
        anyOf(p, "reform", "resolve") -> MotionPrimitive.REFORM
        anyOf(p, "reach", "extend") -> MotionPrimitive.REACH
        anyOf(p, "erupt", "burst", "explode") -> MotionPrimitive.ERUPT
        anyOf(p, "coalesce", "gather") -> MotionPrimitive.COALESCE
        anyOf(p, "give", "yield", "foam", "squish") -> MotionPrimitive.GIVE
        anyOf(p, "settle", "bounce", "land") -> MotionPrimitive.SETTLE
        else -> null
    }

    // --- from-scratch synthesis ------------------------------------------------

    private fun synthesize(p: String, name: String): GenerationResult {
        val intensity = when {
            anyOf(p, "strong", "intense", "hard", "powerful", "heavy", "big") -> 1.0
            anyOf(p, "soft", "gentle", "light", "subtle", "delicate", "faint") -> 0.4
            else -> 0.75
        }
        val sharpness = when {
            anyOf(p, "sharp", "crisp", "snappy", "tick", "click", "precise") -> 0.9
            anyOf(p, "dull", "soft", "muffled", "round", "thud") -> 0.25
            else -> 0.6
        }
        val sustained = anyOf(p, "buzz", "long", "sustained", "hum", "vibrate", "rumble", "continuous")
        val count = when {
            anyOf(p, "triple", "three", "3x") -> 3
            anyOf(p, "double", "two", "twice", "2x") -> 2
            anyOf(p, "repeated", "series", "burst", "rapid", "multiple", "many") -> 4
            else -> 1
        }
        val gap = if (anyOf(p, "fast", "rapid", "quick")) 0.07 else if (anyOf(p, "slow")) 0.22 else 0.12

        if (sustained) {
            val duration = if (anyOf(p, "long")) 0.5 else 0.3
            val pattern = HapticAudioPattern(
                name = name,
                tracks = listOf(
                    HapticTrack(
                        id = "h1",
                        events = listOf(
                            Continuous(
                                time = 0.0, duration = duration, intensity = intensity, sharpness = sharpness,
                                envelope = Envelope(attack = 0.04, sustain = 0.85, release = 0.08),
                            ),
                        ),
                    ),
                ),
            )
            return GenerationResult(
                pattern,
                "Synthesized a ${describe(intensity, sharpness)} buzz (${ms(duration)} ms). I didn't recognise a specific object/effect, so I built it from the feel-words I found. Refine with \"sharper\", \"longer\", \"softer\".",
                "on-device",
            )
        }

        val events = (0 until count).map { i ->
            Transient(time = i * gap, intensity = intensity, sharpness = sharpness)
        }
        val pattern = HapticAudioPattern(name = name, tracks = listOf(HapticTrack(id = "h1", events = events)))
        val countWord = if (count == 1) "a single" else "$count"
        return GenerationResult(
            pattern,
            "Synthesized $countWord ${describe(intensity, sharpness)} tap${if (count > 1) "s" else ""}${if (count > 1) " spaced ${ms(gap)} ms apart" else ""}. Built from the feel-words I found; refine with \"sharper\", \"more\", \"slower\".",
            "on-device",
        )
    }

    private fun urgent(): HapticAudioPattern = HapticAudioPattern(
        name = "Urgent alert",
        tracks = listOf(
            HapticTrack(
                id = "h1",
                events = listOf(
                    Transient(0.0, 1.0, 0.9),
                    Transient(0.09, 1.0, 0.9),
                    Transient(0.18, 1.0, 0.9),
                ),
            ),
        ),
    )

    // --- helpers ---------------------------------------------------------------

    private fun anyOf(haystack: String, vararg needles: String) = needles.any { haystack.contains(it) }
    private fun pct(v: Double) = "${(v * 100).toInt()}%"
    private fun ms(s: Double) = "${(s * 1000).toInt()}"
    private fun vel(v: Double) = if (v >= 1.4) "fast" else if (v <= 0.7) "slow" else "moderate speed"
    private fun describe(intensity: Double, sharpness: Double): String {
        val i = if (intensity >= 0.85) "strong" else if (intensity <= 0.5) "gentle" else "medium"
        val s = if (sharpness >= 0.8) "sharp" else if (sharpness <= 0.35) "dull" else "moderate"
        return "$i, $s"
    }

    /** A short, human title from the prompt (first few words, capitalized). */
    private fun displayName(prompt: String): String {
        val words = prompt.trim().split(Regex("\\s+")).take(4).joinToString(" ")
        return words.replaceFirstChar { it.uppercase() }.take(40).ifBlank { "Generated" }
    }
}
