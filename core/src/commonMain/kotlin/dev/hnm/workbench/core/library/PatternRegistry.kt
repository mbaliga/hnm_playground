package dev.hnm.workbench.core.library

import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.PatternSerialization
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * A shareable, versioned index of haptic patterns — the seed of a community publish channel. Where
 * [PatternLibrary] is the user's local save store, a registry is a *curated, attributed* catalog that
 * can be bundled in-repo today and fetched from a URL tomorrow (the entries are plain IR JSON, so no new
 * format is needed). The in-app gallery loads from one of these.
 */
@Serializable
data class RegistryEntry(
    val id: String,
    val name: String,
    val author: String = "built-in",
    val tags: List<String> = emptyList(),
    val description: String = "",
    val pattern: HapticAudioPattern,
)

@Serializable
data class RegistryIndex(
    val version: Int = 1,
    val title: String = "Haptics Workbench Registry",
    val entries: List<RegistryEntry> = emptyList(),
) {
    val size: Int get() = entries.size
    fun find(id: String): RegistryEntry? = entries.firstOrNull { it.id == id }
    fun withTag(tag: String): List<RegistryEntry> = entries.filter { tag in it.tags }
    val allTags: List<String> get() = entries.flatMap { it.tags }.distinct().sorted()

    companion object {
        // Reuse the IR's polymorphic codec so nested patterns round-trip losslessly.
        private val codec = PatternSerialization.json

        fun fromJson(text: String): RegistryIndex =
            codec.decodeFromString(serializer(), text)

        fun toJson(index: RegistryIndex): String =
            codec.encodeToString(serializer(), index)

        /**
         * The bundled starter registry: every built-in reference pattern, tagged so the gallery can
         * group them. Grows by appending [RegistryEntry] rows (a user-submitted pattern is just another
         * entry, contributable by PR).
         */
        fun seed(): RegistryIndex = RegistryIndex(
            title = "Built-in starter pack",
            entries = BuiltInPatterns.ALL.map { p ->
                RegistryEntry(
                    id = p.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'),
                    name = p.name,
                    author = "built-in",
                    tags = listOf("reference"),
                    description = "Built-in reference pattern.",
                    pattern = p,
                )
            },
        )

        fun seedJson(): String = toJson(seed())
    }
}

/** Convenience: turn a registry into a loadable [PatternLibrary]. */
fun RegistryIndex.toLibrary(): PatternLibrary =
    PatternLibrary().apply { entries.forEach { save(it.pattern) } }

private fun List<RegistryEntry>.patterns(): List<HapticAudioPattern> = map { it.pattern }

/** Encode just the patterns of a registry (e.g. to hand off to a player). */
fun RegistryIndex.encodePatterns(): String =
    PatternSerialization.json.encodeToString(
        ListSerializer(HapticAudioPattern.serializer()),
        entries.patterns(),
    )
