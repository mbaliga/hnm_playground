package dev.hnm.workbench.core.library

import dev.hnm.workbench.core.ir.HapticAudioPattern

/**
 * The library *browsing* layer, built on top of [PatternLibrary]'s bare save/load map (M4). Where
 * [PatternLibrary] only knows "name -> pattern", a [LibraryRepository] adds the metadata a library panel
 * actually needs: which patterns are starred, which were played most recently, and how the built-ins
 * group into browsable tags/collections.
 *
 * [PatternLibrary] itself is untouched by this — a [LibraryRepository] wraps one rather than replacing
 * it, so every existing `PatternLibrary`/`RegistryIndex` caller (save/load JSON, [RegistryIndex.toLibrary])
 * keeps working exactly as before. This is the new surface later UI phases (favorites star, recents rail,
 * tag filter chips) should build against instead of talking to a bare map.
 */
interface LibraryRepository {

    /** The underlying save/load store — still the single source of truth for pattern content/JSON. */
    val library: PatternLibrary

    // --- browsing ---------------------------------------------------------------

    fun all(): List<HapticAudioPattern> = library.all()
    fun get(name: String): HapticAudioPattern? = library.get(name)

    /** Save [pattern] into [library]. Existing favorite/tag state for that name is left untouched. */
    fun save(pattern: HapticAudioPattern): HapticAudioPattern

    /** Remove a pattern by name, also clearing any favorite/recent/tag state kept for it. */
    fun remove(name: String): HapticAudioPattern?

    // --- favorites (star / unstar) -----------------------------------------------

    fun isFavorite(name: String): Boolean
    fun setFavorite(name: String, favorite: Boolean)

    /** Flip favorite state and return the new value. */
    fun toggleFavorite(name: String): Boolean

    /** Starred patterns, in the order they were favorited. */
    fun favorites(): List<HapticAudioPattern>

    // --- recents (record-on-play) ------------------------------------------------

    /** Record that [name] was just played — moves it to the front of [recents] (capped). */
    fun recordPlay(name: String)

    /** Most-recently-played patterns first, capped to the store's recents limit. */
    fun recents(): List<HapticAudioPattern>

    // --- tags / collections -------------------------------------------------------

    fun tagsFor(name: String): Set<String>
    fun addTag(name: String, tag: String)
    fun removeTag(name: String, tag: String)

    /** Every pattern carrying [tag], built-ins and user saves alike. */
    fun byTag(tag: String): List<HapticAudioPattern>

    /** Every tag currently in use, sorted for stable display. */
    fun allTags(): List<String>

    /** Named groupings — a "collection" is just every pattern sharing a tag, keyed by that tag. */
    fun collections(): Map<String, List<HapticAudioPattern>>
}

/**
 * In-memory default implementation. Wraps a [PatternLibrary] (defaulting to the built-in seed set) and
 * layers favorites/recents/tags on top in plain mutable collections. No persistence yet — a disk- or
 * DataStore-backed [LibraryRepository] is a drop-in later stage; nothing above this line assumes
 * in-memory storage.
 */
class InMemoryLibraryRepository(
    override val library: PatternLibrary = PatternLibrary.withBuiltIns(),
    private val recentsLimit: Int = 12,
) : LibraryRepository {

    private val favoriteNames = linkedSetOf<String>()
    private val recentNames = ArrayDeque<String>() // most-recent first
    private val tags: MutableMap<String, MutableSet<String>> = linkedMapOf()

    init {
        // Seed the built-ins into a few browsable collections so a fresh library panel has something
        // to group by before the user tags anything of their own.
        BUILT_IN_TAGS.forEach { (name, seedTags) ->
            if (library.get(name) != null) tags.getOrPut(name) { linkedSetOf() }.addAll(seedTags)
        }
    }

    override fun save(pattern: HapticAudioPattern): HapticAudioPattern {
        library.save(pattern)
        return pattern
    }

    override fun remove(name: String): HapticAudioPattern? {
        favoriteNames.remove(name)
        recentNames.remove(name)
        tags.remove(name)
        return library.remove(name)
    }

    // --- favorites ---------------------------------------------------------------

    override fun isFavorite(name: String): Boolean = name in favoriteNames

    override fun setFavorite(name: String, favorite: Boolean) {
        if (favorite) favoriteNames.add(name) else favoriteNames.remove(name)
    }

    override fun toggleFavorite(name: String): Boolean {
        val next = name !in favoriteNames
        setFavorite(name, next)
        return next
    }

    override fun favorites(): List<HapticAudioPattern> = favoriteNames.mapNotNull { library.get(it) }

    // --- recents -------------------------------------------------------------------

    override fun recordPlay(name: String) {
        if (library.get(name) == null) return // only track plays of patterns actually in the library
        recentNames.remove(name)
        recentNames.addFirst(name)
        while (recentNames.size > recentsLimit) recentNames.removeLast()
    }

    override fun recents(): List<HapticAudioPattern> = recentNames.mapNotNull { library.get(it) }

    // --- tags / collections -----------------------------------------------------------

    override fun tagsFor(name: String): Set<String> = tags[name]?.toSet() ?: emptySet()

    override fun addTag(name: String, tag: String) {
        tags.getOrPut(name) { linkedSetOf() }.add(tag)
    }

    override fun removeTag(name: String, tag: String) {
        tags[name]?.remove(tag)
    }

    override fun byTag(tag: String): List<HapticAudioPattern> =
        tags.filterValues { tag in it }.keys.mapNotNull { library.get(it) }

    override fun allTags(): List<String> = tags.values.flatten().distinct().sorted()

    override fun collections(): Map<String, List<HapticAudioPattern>> = allTags().associateWith { byTag(it) }

    companion object {
        /** A sensible default grouping of [BuiltInPatterns.ALL] into browsable collections. */
        private val BUILT_IN_TAGS: Map<String, Set<String>> = mapOf(
            BuiltInPatterns.TAP.name to setOf("feedback"),
            BuiltInPatterns.SELECTION.name to setOf("feedback"),
            BuiltInPatterns.DOUBLE_TAP.name to setOf("feedback"),
            BuiltInPatterns.TRIPLE_TICK.name to setOf("feedback"),
            BuiltInPatterns.CONFIRM.name to setOf("status"),
            BuiltInPatterns.SUCCESS.name to setOf("status"),
            BuiltInPatterns.ERROR.name to setOf("status", "alerts"),
            BuiltInPatterns.WARNING.name to setOf("status", "alerts"),
            BuiltInPatterns.NOTIFICATION.name to setOf("alerts"),
            BuiltInPatterns.TOGGLE_ON.name to setOf("toggles"),
            BuiltInPatterns.TOGGLE_OFF.name to setOf("toggles"),
            BuiltInPatterns.SWIPE.name to setOf("gestures"),
            BuiltInPatterns.SNAP.name to setOf("gestures"),
            BuiltInPatterns.HEARTBEAT.name to setOf("ambient"),
            BuiltInPatterns.SPIN_LOCK.name to setOf("gestures"),
        )
    }
}
