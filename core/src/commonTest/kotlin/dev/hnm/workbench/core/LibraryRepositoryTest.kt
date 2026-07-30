package dev.hnm.workbench.core

import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.core.library.BuiltInPatterns
import dev.hnm.workbench.core.library.InMemoryLibraryRepository
import dev.hnm.workbench.core.library.PatternLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryRepositoryTest {

    private fun pattern(name: String) = HapticAudioPattern(
        name = name,
        tracks = listOf(HapticTrack(id = "h1", events = listOf(Transient(time = 0.0, intensity = 0.5, sharpness = 0.5)))),
    )

    @Test
    fun seedsFromBuiltInsAndKeepsPatternLibraryWorking() {
        val repo = InMemoryLibraryRepository()
        // PatternLibrary itself keeps working unmodified underneath the new surface.
        assertTrue(repo.library.get(BuiltInPatterns.CONFIRM.name) != null)
        assertEquals(BuiltInPatterns.ALL.size, repo.all().size)
        assertEquals(BuiltInPatterns.ALL.size, repo.library.size)
    }

    @Test
    fun favoritesStarAndUnstar() {
        val repo = InMemoryLibraryRepository()
        val name = BuiltInPatterns.CONFIRM.name
        assertFalse(repo.isFavorite(name))

        assertTrue(repo.toggleFavorite(name)) // star
        assertTrue(repo.isFavorite(name))
        assertTrue(repo.favorites().any { it.name == name })

        assertFalse(repo.toggleFavorite(name)) // unstar
        assertFalse(repo.isFavorite(name))
        assertTrue(repo.favorites().none { it.name == name })
    }

    @Test
    fun setFavoriteIsIdempotent() {
        val repo = InMemoryLibraryRepository()
        val name = BuiltInPatterns.TAP.name
        repo.setFavorite(name, true)
        repo.setFavorite(name, true)
        assertEquals(1, repo.favorites().count { it.name == name })
        repo.setFavorite(name, false)
        assertFalse(repo.isFavorite(name))
    }

    @Test
    fun recordPlayTracksRecentsMostRecentFirst() {
        val repo = InMemoryLibraryRepository(recentsLimit = 3)
        repo.recordPlay(BuiltInPatterns.TAP.name)
        repo.recordPlay(BuiltInPatterns.CONFIRM.name)
        repo.recordPlay(BuiltInPatterns.ERROR.name)

        assertEquals(
            listOf(BuiltInPatterns.ERROR.name, BuiltInPatterns.CONFIRM.name, BuiltInPatterns.TAP.name),
            repo.recents().map { it.name },
        )
    }

    @Test
    fun recentsAreCappedAndReplayingMovesToFront() {
        val repo = InMemoryLibraryRepository(recentsLimit = 2)
        repo.recordPlay(BuiltInPatterns.TAP.name)
        repo.recordPlay(BuiltInPatterns.CONFIRM.name)
        repo.recordPlay(BuiltInPatterns.ERROR.name) // TAP should drop off the cap
        assertEquals(2, repo.recents().size)
        assertTrue(repo.recents().none { it.name == BuiltInPatterns.TAP.name })

        repo.recordPlay(BuiltInPatterns.CONFIRM.name) // replaying moves it back to the front
        assertEquals(BuiltInPatterns.CONFIRM.name, repo.recents().first().name)
        assertEquals(2, repo.recents().size, "replaying an existing entry must not grow the list")
    }

    @Test
    fun recordPlayIgnoresPatternsNotInTheLibrary() {
        val repo = InMemoryLibraryRepository()
        repo.recordPlay("not a saved pattern")
        assertTrue(repo.recents().isEmpty())
    }

    @Test
    fun builtInsAreGroupedIntoTagsAndCollections() {
        val repo = InMemoryLibraryRepository()
        assertTrue("alerts" in repo.allTags())
        assertTrue(repo.byTag("alerts").any { it.name == BuiltInPatterns.ERROR.name })
        assertTrue(repo.byTag("alerts").any { it.name == BuiltInPatterns.WARNING.name })

        val collections = repo.collections()
        assertTrue(collections.containsKey("alerts"))
        assertEquals(repo.byTag("alerts").map { it.name }.toSet(), collections.getValue("alerts").map { it.name }.toSet())
    }

    @Test
    fun customTagsCanBeAddedAndRemoved() {
        val repo = InMemoryLibraryRepository()
        val name = BuiltInPatterns.SNAP.name
        repo.addTag(name, "favorites-of-the-week")
        assertTrue("favorites-of-the-week" in repo.tagsFor(name))
        assertTrue(repo.byTag("favorites-of-the-week").any { it.name == name })

        repo.removeTag(name, "favorites-of-the-week")
        assertFalse("favorites-of-the-week" in repo.tagsFor(name))
    }

    @Test
    fun saveAddsUserPatternsAlongsideBuiltIns() {
        val repo = InMemoryLibraryRepository()
        val mine = pattern("My Custom Buzz")
        repo.save(mine)
        assertEquals(mine, repo.get("My Custom Buzz"))
        assertEquals(BuiltInPatterns.ALL.size + 1, repo.all().size)
    }

    @Test
    fun removeClearsFavoriteRecentAndTagState() {
        val repo = InMemoryLibraryRepository()
        val name = BuiltInPatterns.SUCCESS.name
        repo.setFavorite(name, true)
        repo.recordPlay(name)
        repo.addTag(name, "custom")

        val removed = repo.remove(name)
        assertEquals(BuiltInPatterns.SUCCESS.name, removed?.name)
        assertNull(repo.get(name))
        assertFalse(repo.isFavorite(name))
        assertTrue(repo.recents().none { it.name == name })
        assertTrue(repo.tagsFor(name).isEmpty())
    }

    @Test
    fun canWrapAnExistingCustomPatternLibrary() {
        val custom = PatternLibrary().apply { save(pattern("Solo")) }
        val repo = InMemoryLibraryRepository(library = custom)
        assertEquals(1, repo.all().size)
        assertEquals("Solo", repo.all().first().name)
    }
}
