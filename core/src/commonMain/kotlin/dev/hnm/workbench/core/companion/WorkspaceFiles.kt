package dev.hnm.workbench.core.companion

import dev.hnm.workbench.core.ir.*
import dev.hnm.workbench.core.library.PatternLibrary

/** Native effects and library backups carry every synthesized layer and reference. */
object WorkspaceFiles {
    const val MAX_BACKUP_BYTES = 4 * 1024 * 1024
    fun decode(bytes: ByteArray): List<HapticAudioPattern> {
        require(bytes.size <= MAX_BACKUP_BYTES) { "File exceeds 4 MiB" }
        val text = bytes.decodeToString(throwOnInvalidSequence = true).trim()
        val effects = if (text.startsWith("[")) PatternLibrary.fromJson(text).all()
            else listOf(PatternSerialization.decode(text))
        require(effects.size <= 256) { "Backup has too many effects" }
        effects.forEach { effect ->
            PatternSafety.validate(effect)
            require(PatternSerialization.encode(effect, pretty=false).encodeToByteArray().size <= PatternSafety.MAX_BYTES) { "Effect exceeds 64 KiB" }
            require(effect.tracks.filterIsInstance<AudioTrack>().none { t -> t.events.any { it is SampleEvent } }) {
                "Sample audio files are not included. Import synthesized effects instead."
            }
        }
        return effects
    }
}
