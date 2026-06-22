package dev.hnm.workbench.core.library

import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.PatternSerialization
import kotlinx.serialization.builtins.ListSerializer

/**
 * An in-memory, serializable collection of saved patterns (the library save/load of M4). Patterns are
 * keyed by name; persistence reuses the same native-JSON codec and `SerializersModule` as single
 * patterns, so the whole library round-trips losslessly.
 */
class PatternLibrary(
    private val patterns: MutableMap<String, HapticAudioPattern> = linkedMapOf(),
) {
    val names: List<String> get() = patterns.keys.toList()
    val size: Int get() = patterns.size

    fun save(pattern: HapticAudioPattern): PatternLibrary = apply { patterns[pattern.name] = pattern }
    fun get(name: String): HapticAudioPattern? = patterns[name]
    fun remove(name: String): HapticAudioPattern? = patterns.remove(name)
    fun all(): List<HapticAudioPattern> = patterns.values.toList()

    fun toJson(): String =
        PatternSerialization.json.encodeToString(ListSerializer(HapticAudioPattern.serializer()), all())

    companion object {
        fun fromJson(text: String): PatternLibrary {
            val list = PatternSerialization.json.decodeFromString(
                ListSerializer(HapticAudioPattern.serializer()), text,
            )
            return PatternLibrary(LinkedHashMap<String, HapticAudioPattern>().apply { list.forEach { put(it.name, it) } })
        }

        /** A starter library with the built-in reference patterns. */
        fun withBuiltIns(): PatternLibrary = PatternLibrary().save(BuiltInPatterns.CONFIRM)
    }
}
