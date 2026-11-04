package dev.hnm.workbench.core.library

import dev.hnm.workbench.core.design.MaterialPreset
import dev.hnm.workbench.core.design.ModalSynth
import dev.hnm.workbench.core.design.MotionPrimitive
import dev.hnm.workbench.core.design.MotionPrimitives
import dev.hnm.workbench.core.design.ParameterNavigator
import dev.hnm.workbench.core.design.RhythmCapture
import dev.hnm.workbench.core.design.Tap
import dev.hnm.workbench.core.design.TextureField
import dev.hnm.workbench.core.design.TextureFieldType
import dev.hnm.workbench.core.design.TextureFields
import dev.hnm.workbench.core.design.Variations
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.Envelope
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticTrack
import kotlin.math.roundToInt

/** Which part of the feel-test gallery an entry comes from. */
enum class FeelKind { REFERENCE, BUZZ, VARIATION, CAPTURED, MOTION, TEXTURE, NAVIGATOR, MATERIAL }

/**
 * One pattern the on-device feel-test gallery offers: a stable [id] (a slug of [name]), the label the
 * gallery shows, and the pattern itself, built on first use so that looking an entry up (a widget pad,
 * a "recently felt" row) doesn't generate the other sixty.
 */
class FeelEntry internal constructor(
    val id: String,
    val name: String,
    val kind: FeelKind,
    val description: String,
    build: () -> HapticAudioPattern,
) {
    val pattern: HapticAudioPattern by lazy(build)

    override fun toString(): String = "FeelEntry($id)"
}

/**
 * The feel-test gallery's list as data: every pattern the Android player offers, in the order it shows
 * them, each with a stable id. The gallery screen and the home-screen widgets both read it, so a widget
 * can name, draw and replay exactly what the gallery plays. Everything here is built from `core`
 * (built-ins, variations, motion primitives, texture fields, the parameter navigator, materials), so
 * it is the same on every platform.
 */
object FeelTestCatalog {

    val entries: List<FeelEntry> by lazy { buildEntries() }

    private val byId: Map<String, FeelEntry> by lazy { entries.associateBy { it.id } }

    fun find(id: String): FeelEntry? = byId[id]

    /** The id scheme: lower-case, every run of other characters becomes one '-' (as [RegistryIndex.seed]). */
    fun slug(name: String): String = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    /** Short descriptions of the built-ins, in the words of their definitions in [BuiltInPatterns]. */
    private val REFERENCE_NOTES: Map<String, String> = mapOf(
        BuiltInPatterns.TAP.name to
            "A single clean transient: selection feedback, a button press, a minimal acknowledgement.",
        BuiltInPatterns.SELECTION.name to "A very soft, gentle tick: cursor movement, a list scroll step.",
        BuiltInPatterns.DOUBLE_TAP.name to "Two sharp taps in quick succession: a double tap or a toggle confirmation.",
        BuiltInPatterns.CONFIRM.name to
            "A sharp tick, then a stronger, softer click 80 ms later. The canonical \"action confirmed\".",
        BuiltInPatterns.SUCCESS.name to "A satisfying upward sweep: task complete, all good.",
        BuiltInPatterns.ERROR.name to "Two hard thumps in quick succession: an unmistakable \"something went wrong\".",
        BuiltInPatterns.WARNING.name to "Three ascending pulses: pay attention, but keep going.",
        BuiltInPatterns.NOTIFICATION.name to
            "A gentle incoming notification: a soft long buzz with a finishing tick. Non-urgent.",
        BuiltInPatterns.TOGGLE_ON.name to "Light to firm, like latching a switch: a slow rise, then a crisp close.",
        BuiltInPatterns.TOGGLE_OFF.name to "Firm to light, the reverse of Toggle ON: a quick fall before a softer close.",
        BuiltInPatterns.SWIPE.name to "A short directional buzz that decays, like a swipe completing.",
        BuiltInPatterns.SNAP.name to "A spring-back snap: the finger goes, and the thing stays. Modal dismiss, rubber-band.",
        BuiltInPatterns.HEARTBEAT.name to "Two pulses (lub-dub), then silence: alive, loading, a pulse.",
        BuiltInPatterns.TRIPLE_TICK.name to "Three quick ticks: sent, delivered, an ellipsis becoming a result.",
        BuiltInPatterns.SPIN_LOCK.name to "A spin-up buzz, then a soft landing: reorder, drag complete, lock.",
    )

    private fun buildEntries(): List<FeelEntry> = buildList {
        fun entry(name: String, kind: FeelKind, description: String, build: () -> HapticAudioPattern) {
            add(FeelEntry(slug(name), name, kind, description, build))
        }

        BuiltInPatterns.ALL.forEach { p ->
            entry(p.name, FeelKind.REFERENCE, REFERENCE_NOTES[p.name].orEmpty()) { p }
        }
        entry("Strong buzz (400 ms)", FeelKind.BUZZ, "A 400 ms full-strength buzz: the plainest check that the motor runs.") {
            strongBuzz()
        }
        val confirmFamily by lazy { Variations.family(BuiltInPatterns.CONFIRM, count = 3) }
        for (i in 0 until 3) {
            entry("Confirm · variation ${i + 1}", FeelKind.VARIATION, "Confirm, gently mutated (variation ${i + 1} of 3).") {
                confirmFamily[i]
            }
        }
        entry("Captured rhythm", FeelKind.CAPTURED, "Four tapped beats, captured as a rhythm.") { capturedRhythm() }
        MotionPrimitive.entries.forEach { p ->
            entry("Motion · ${p.displayName}", FeelKind.MOTION, sentence(p.description)) { MotionPrimitives.toPattern(p) }
        }
        val roughnesses = listOf(0.2 to "smooth", 0.5 to "mid", 0.85 to "rough")
        for (type in TextureFieldType.entries) {
            for ((r, rLabel) in roughnesses) {
                val pct = (r * 100).roundToInt()
                val about = "${type.displayName} texture at $pct% roughness: ${type.description}."
                entry("Texture · ${type.displayName} · $rLabel", FeelKind.TEXTURE, about) {
                    TextureFields.toPattern(TextureField(type = type, roughness = r))
                }
            }
        }
        val midPerlin = TextureField(type = TextureFieldType.PERLIN, roughness = 0.5)
        for ((v, vLabel) in listOf(0.5 to "slow", 1.5 to "fast")) {
            entry("Texture · Perlin mid · $vLabel scrub", FeelKind.TEXTURE, "Mid Perlin texture, scrubbed $vLabel.") {
                TextureFields.toPattern(midPerlin, velocity = v)
            }
        }
        val textureSteps by lazy {
            ParameterNavigator.textureFamilyPatterns(
                TextureField(type = TextureFieldType.PERLIN, roughness = 0.05),
                TextureField(type = TextureFieldType.PERLIN, roughness = 0.95),
                count = 5,
            )
        }
        for (i in 0 until 5) {
            entry("Navigate · texture ${i + 1}/5", FeelKind.NAVIGATOR, "Step ${i + 1} of 5 from a smooth to a rough texture.") {
                textureSteps[i]
            }
        }
        val motionSteps by lazy {
            ParameterNavigator.motionFamilyPatterns(MotionPrimitive.STIR, MotionPrimitive.SETTLE, count = 5)
        }
        for (i in 0 until 5) {
            entry("Navigate · motion ${i + 1}/5", FeelKind.NAVIGATOR, "Step ${i + 1} of 5 from Stir to Settle.") {
                motionSteps[i]
            }
        }
        MaterialPreset.entries.forEach { m ->
            val ringMs = (ModalSynth.ringSeconds(m.material) * 1000).roundToInt()
            entry(
                "Material · ${m.displayName}",
                FeelKind.MATERIAL,
                "A struck ${m.displayName.lowercase()} surface: an impact, then a ring-down of about $ringMs ms.",
            ) { ModalSynth.toPattern(m.material) }
        }
    }

    private fun sentence(phrase: String): String = phrase.replaceFirstChar { it.uppercaseChar() }

    private fun strongBuzz(): HapticAudioPattern =
        HapticAudioPattern(
            name = "Strong buzz",
            tracks = listOf(
                HapticTrack(
                    id = "h1",
                    events = listOf(
                        Continuous(
                            time = 0.0,
                            duration = 0.4,
                            intensity = 1.0,
                            sharpness = 0.5,
                            envelope = Envelope(attack = 0.02, sustain = 1.0, release = 0.05),
                        ),
                    ),
                ),
            ),
        )

    private fun capturedRhythm(): HapticAudioPattern =
        RhythmCapture.fromTaps(
            listOf(Tap(0.0, 0.9), Tap(0.12, 0.6), Tap(0.24, 0.6), Tap(0.5, 1.0)),
            name = "Captured rhythm",
        )
}
