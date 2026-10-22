package dev.hnm.workbench.core.companion

import dev.hnm.workbench.core.ir.*
import kotlin.random.Random

/** Immutable revisions travel as native patterns. No timed playback commands are synchronized. */
object PatternTransfer {
    const val DOCUMENT = "hafx.document"
    const val REVISION = "hafx.revision"
    const val PARENT = "hafx.parent"
    const val SOURCE = "hafx.source"
    const val PATTERN_PATH = "/hafx/v1/pattern/"
    const val RECEIPT_PATH = "/hafx/v1/receipt/"
    private val id = Regex("[a-f0-9]{32}")
    fun newId(): String = Random.nextBytes(16).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    fun validId(value: String?): Boolean = value != null && id.matches(value)

    fun revise(pattern: HapticAudioPattern, source: String = "studio"): HapticAudioPattern {
        PatternSafety.validate(pattern)
        val old = pattern.metadata
        val metadata = old.filterKeys { it !in setOf(DOCUMENT, REVISION, PARENT, SOURCE) }.toMutableMap()
        metadata[DOCUMENT] = old[DOCUMENT]?.takeIf(::validId) ?: newId()
        metadata[REVISION] = newId()
        old[REVISION]?.takeIf(::validId)?.let { metadata[PARENT] = it }
        metadata[SOURCE] = source
        return pattern.copy(metadata = metadata).also {
            PatternSafety.validate(it)
            require(PatternSerialization.encode(it, pretty=false).encodeToByteArray().size <= PatternSafety.MAX_BYTES) { "Saved effect exceeds 64 KiB" }
        }
    }

    fun encode(pattern: HapticAudioPattern): ByteArray {
        validateTransfer(pattern)
        return PatternSerialization.encode(pattern, pretty = false).encodeToByteArray().also {
            require(it.size <= PatternSafety.MAX_BYTES) { "Effect is too large to send (maximum 64 KiB)" }
        }
    }

    fun decode(bytes: ByteArray): HapticAudioPattern {
        require(bytes.size <= PatternSafety.MAX_BYTES) { "Effect is too large" }
        return PatternSerialization.decode(bytes.decodeToString()).also(::validateTransfer)
    }

    private fun validateTransfer(pattern: HapticAudioPattern) {
        PatternSafety.validate(pattern)
        require(validId(pattern.metadata[DOCUMENT]) && validId(pattern.metadata[REVISION])) { "Save a revision before sending" }
        require(pattern.metadata[PARENT] == null || validId(pattern.metadata[PARENT])) { "Invalid parent revision" }
        require(pattern.tracks.filterIsInstance<AudioTrack>().none { t -> t.events.any { it is SampleEvent } }) {
            "Sample audio needs its source assets. Send synthesized audio or haptics for now."
        }
    }
}
