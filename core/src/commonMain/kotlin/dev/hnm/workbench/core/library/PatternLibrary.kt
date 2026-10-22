package dev.hnm.workbench.core.library

import dev.hnm.workbench.core.companion.PatternTransfer
import dev.hnm.workbench.core.companion.PatternSafety
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
    /** Called before committing a mutation. A failed durable write leaves the old library intact. */
    var persist: ((String) -> Unit)? = null
    var onChanged: (() -> Unit)? = null

    /** Refresh stock presets by stable identity; saved user documents are never replaced. */
    private fun currentPreset(pattern: HapticAudioPattern): HapticAudioPattern {
        val id = pattern.metadata[StarterEffects.ID] ?: return pattern
        if (pattern.metadata[PatternTransfer.DOCUMENT] != null) return pattern
        return StarterEffects.ALL.firstOrNull { it.name == pattern.name && it.metadata[StarterEffects.ID] == id } ?: pattern
    }

    private fun commit(updated: Map<String, HapticAudioPattern>) {
        require(updated.size <= 256) { "Library is full (maximum 256 effects)" }
        val normalized = updated.mapValues { currentPreset(it.value) }
        val json = PatternSerialization.json.encodeToString(ListSerializer(HapticAudioPattern.serializer()), normalized.values.toList())
        persist?.invoke(json)
        patterns.clear()
        patterns.putAll(normalized)
        onChanged?.invoke()
    }

    /** Bundled examples are immutable. User work has stable document IDs and immutable revisions. */
    fun saveUser(pattern: HapticAudioPattern, source: String = "studio"): HapticAudioPattern {
        PatternSafety.validate(pattern)
        val builtIns = BuiltInPatterns.ALL.map { it.name }.toSet()
        var name = pattern.name
        if (name in builtIns) name += " copy"
        val document = pattern.metadata[PatternTransfer.DOCUMENT]
        val existing = patterns[name]
        if (existing != null && (document == null || existing.metadata[PatternTransfer.DOCUMENT] != document)) {
            val base = name
            var suffix = 2
            while (name in patterns) name = "$base ${suffix++}"
        }
        val saved = PatternTransfer.revise(pattern.copy(name = name), source)
        save(saved)
        return saved
    }

    /** Explicit receive preserves local/concurrent edits and ignores duplicate deliveries. */
    fun receive(pattern: HapticAudioPattern): Boolean {
        PatternTransfer.encode(pattern)
        patterns.values.firstOrNull { it.metadata[PatternTransfer.REVISION] == pattern.metadata[PatternTransfer.REVISION] }?.let { existing ->
            require(existing.copy(name=pattern.name) == pattern) { "This revision differs from the saved copy. Save a new revision before importing." }
            return false
        }
        var name = pattern.name
        if (name in patterns || BuiltInPatterns.ALL.any { it.name == name }) {
            val base = "${pattern.name.take(120)} (received ${pattern.metadata[PatternTransfer.REVISION]!!.take(8)})"
            name = base
            var suffix = 2
            while (name in patterns) name = "$base ${suffix++}"
        }
        save(pattern.copy(name = name))
        return true
    }

    /** Validate the entire import before one durable commit; never partially import a backup. */
    fun importEffects(effects: List<HapticAudioPattern>): Int {
        require(effects.size <= 256) { "Too many effects in backup" }
        effects.forEach(PatternSafety::validate)
        val staged = PatternLibrary(LinkedHashMap(patterns))
        var added = 0
        effects.filter { it !in BuiltInPatterns.ALL }.forEach { effect ->
            val revision = effect.metadata[PatternTransfer.REVISION]
            if (revision != null) { if (staged.receive(effect)) added++ }
            else { staged.saveUser(effect, "import"); added++ }
        }
        commit(staged.patterns)
        return added
    }

    fun renameUser(oldName: String, newName: String, source: String, edited: HapticAudioPattern? = null): HapticAudioPattern {
        val existing = get(oldName) ?: error("Effect no longer exists")
        require(existing !in BuiltInPatterns.ALL) { "Save a copy of a starter before renaming" }
        val name = newName.trim()
        require(name == oldName || name !in patterns) { "That name is already in use" }
        require(BuiltInPatterns.ALL.none { it.name == name }) { "Choose a different name from a starter" }
        val current = edited ?: existing
        require(current.name == oldName && current.metadata[PatternTransfer.DOCUMENT] == existing.metadata[PatternTransfer.DOCUMENT]) { "Effect identity changed; reopen the saved effect" }
        val renamed = PatternTransfer.revise(current.copy(name = name), source)
        PatternSafety.validate(renamed)
        commit((patterns - oldName) + (name to renamed))
        return renamed
    }

    val names: List<String> get() = patterns.keys.toList()
    val size: Int get() = patterns.size

    fun save(pattern: HapticAudioPattern): PatternLibrary = apply { commit(patterns + (pattern.name to pattern)) }
    fun get(name: String): HapticAudioPattern? = patterns[name]?.let(::currentPreset)
    fun remove(name: String): HapticAudioPattern? {
        if (get(name) in BuiltInPatterns.ALL) return null
        val removed = patterns[name] ?: return null
        commit(patterns - name)
        return removed
    }
    fun all(): List<HapticAudioPattern> = patterns.values.map(::currentPreset)

    fun toJson(): String =
        PatternSerialization.json.encodeToString(ListSerializer(HapticAudioPattern.serializer()), all())

    companion object {
        fun fromJson(text: String): PatternLibrary {
            val list = PatternSerialization.json.decodeFromString(
                ListSerializer(HapticAudioPattern.serializer()), text,
            )
            require(list.map { it.name }.distinct().size == list.size) { "Duplicate effect names in library" }
            return PatternLibrary(LinkedHashMap<String, HapticAudioPattern>().apply { list.forEach { put(it.name, it) } })
        }

        /** A starter library with all built-in reference patterns. */
        fun withBuiltIns(): PatternLibrary = PatternLibrary().apply {
            BuiltInPatterns.ALL.forEach { save(it) }
        }
    }
}
