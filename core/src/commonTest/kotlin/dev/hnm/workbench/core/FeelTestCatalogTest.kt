package dev.hnm.workbench.core

import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.library.BuiltInPatterns
import dev.hnm.workbench.core.library.FeelKind
import dev.hnm.workbench.core.library.FeelTestCatalog
import dev.hnm.workbench.core.library.RegistryIndex
import dev.hnm.workbench.core.widget.RecentlyFelt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FeelTestCatalogTest {

    private val entries = FeelTestCatalog.entries

    @Test
    fun listsTheGalleryInItsDisplayOrder() {
        val names = entries.map { it.name }
        assertEquals(BuiltInPatterns.ALL.map { it.name }, names.take(15))
        assertEquals(
            listOf("Strong buzz (400 ms)", "Confirm · variation 1", "Confirm · variation 2", "Confirm · variation 3", "Captured rhythm"),
            names.subList(15, 20),
        )
        assertEquals("Motion · Breath", names[20])
        assertEquals("Texture · Perlin · smooth", names[28])
        assertEquals("Texture · Perlin mid · fast scrub", names[41])
        assertEquals("Navigate · texture 1/5", names[42])
        assertEquals("Navigate · motion 5/5", names[51])
        assertEquals("Material · Ceramic", names.last())
        assertEquals(57, entries.size)
    }

    @Test
    fun countsEachKind() {
        val counts = entries.groupingBy { it.kind }.eachCount()
        assertEquals(15, counts[FeelKind.REFERENCE])
        assertEquals(1, counts[FeelKind.BUZZ])
        assertEquals(3, counts[FeelKind.VARIATION])
        assertEquals(1, counts[FeelKind.CAPTURED])
        assertEquals(8, counts[FeelKind.MOTION])
        assertEquals(14, counts[FeelKind.TEXTURE])
        assertEquals(10, counts[FeelKind.NAVIGATOR])
        assertEquals(5, counts[FeelKind.MATERIAL])
    }

    @Test
    fun idsAreUniqueStableSlugs() {
        val ids = entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "ids must be unique")
        assertTrue(ids.all { RecentlyFelt.isValidId(it) }, "every id must survive the recently-felt store")
        // Pinned: widgets and the recently-felt file refer to entries by these ids.
        listOf(
            "tap", "toggle-on", "strong-buzz-400-ms", "confirm-variation-1", "captured-rhythm", "motion-breath",
            "texture-fbm-smooth", "texture-perlin-mid-slow-scrub", "navigate-texture-1-5", "material-wood",
        ).forEach { id -> assertEquals(id, FeelTestCatalog.find(id)?.id, "missing pinned id $id") }
        assertNull(FeelTestCatalog.find("no-such-pattern"))
        assertNull(FeelTestCatalog.find("../tap"))
    }

    @Test
    fun builtInIdsMatchTheRegistry() {
        val registryIds = RegistryIndex.seed().entries.map { it.id }
        assertEquals(registryIds, entries.filter { it.kind == FeelKind.REFERENCE }.map { it.id })
    }

    @Test
    fun referenceEntriesAreTheBuiltInsThemselves() {
        entries.filter { it.kind == FeelKind.REFERENCE }.zip(BuiltInPatterns.ALL).forEach { (entry, builtIn) ->
            assertSame(builtIn, entry.pattern)
        }
    }

    @Test
    fun everyEntryHasADescriptionAndSomethingToFeel() {
        for (entry in entries) {
            assertTrue(entry.description.isNotBlank(), "${entry.id} has no description")
            val events = entry.pattern.tracks.filterIsInstance<HapticTrack>().flatMap { it.events }
            assertTrue(events.isNotEmpty(), "${entry.id} has no haptic events")
        }
    }

    @Test
    fun patternsAreBuiltOnceAndOnDemand() {
        val entry = FeelTestCatalog.find("navigate-motion-3-5")!!
        assertSame(entry.pattern, entry.pattern)
    }
}
