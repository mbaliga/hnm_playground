package dev.hnm.workbench.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import dev.hnm.workbench.core.library.BuiltInPatterns
import dev.hnm.workbench.ui.components.FeelCard
import dev.hnm.workbench.ui.components.LibraryListPanel
import dev.hnm.workbench.ui.components.LibraryPanel
import dev.hnm.workbench.ui.components.TimelineDotMatrixThumbnail
import dev.hnm.workbench.ui.components.TimelineRenderMode
import dev.hnm.workbench.ui.components.TimelineView
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.nav.Tab
import dev.hnm.workbench.ui.nav.WorkbenchNavState
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Headless render checks (see [PreviewRenderTest] for the pattern) for the new library/discovery UI,
 * [FeelCard], and the LED dot-matrix timeline mode — plus a couple of plain-logic checks on the
 * [EditorState] wiring none of those composables would otherwise exercise on their own.
 */
class LibraryDiscoveryRenderTest {

    private fun renderToPng(
        name: String,
        width: Int = 500,
        height: Int = 500,
        minBytes: Int = 2_000,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f), content = content)
        try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
                ?: error("PNG encode returned null")
            File("build/preview").apply { mkdirs() }
            File("build/preview/$name.png").writeBytes(png)
            assertTrue(png.size > minBytes, "$name didn't compose (${png.size} bytes)")
        } finally {
            scene.close()
        }
    }

    @Test
    fun rendersFeelCardStandalone() {
        renderToPng("feelcard-confirm", width = 200, height = 260) {
            FeelCard(
                pattern = BuiltInPatterns.CONFIRM,
                favorite = true,
                onTap = {},
                onToggleFavorite = {},
            )
        }
    }

    @Test
    fun rendersFeelCardForEveryBuiltIn() {
        // Every built-in must produce a FeelCard without crashing — including all-Primitive patterns
        // (SWIPE, TOGGLE_ON/OFF, SPIN_LOCK), which exercise FeelStats' nominal-sharpness fallback.
        BuiltInPatterns.ALL.forEach { pattern ->
            renderToPng("feelcard-${pattern.name.lowercase().replace(' ', '-')}", width = 200, height = 260) {
                FeelCard(pattern = pattern, favorite = false, onTap = {}, onToggleFavorite = {})
            }
        }
    }

    @Test
    fun rendersTimelineDotMatrixThumbnailStandalone() {
        // A small, mostly-dark canvas legitimately compresses to a tiny PNG — lower the size floor
        // rather than inflate the canvas just to satisfy the "didn't compose" heuristic.
        renderToPng("dotmatrix-thumbnail", width = 140, height = 80, minBytes = 500) {
            TimelineDotMatrixThumbnail(pattern = BuiltInPatterns.HEARTBEAT)
        }
    }

    @Test
    fun rendersTimelineInDotMatrixMode() {
        // Exercises TimelineView's LED branch end-to-end (the WAVEFORM branch is already covered by
        // every existing PreviewRenderTest that renders the editor).
        val state = EditorState().apply { load(BuiltInPatterns.SUCCESS) }
        renderToPng("timeline-dotmatrix", width = 700, height = 260) {
            TimelineView(state, initialMode = TimelineRenderMode.DOT_MATRIX)
        }
    }

    @Test
    fun rendersLibraryTabHomeBlockWithStarredAndRecentRails() {
        // Seed a favorite and a couple of recents so LibraryListPanel's FeelCard rails actually have
        // content, not just the empty-library-tab path already covered by PreviewRenderTest.rendersEachTab.
        val state = EditorState()
        state.libraryRepository.setFavorite(BuiltInPatterns.CONFIRM.name, true)
        state.libraryRepository.recordPlay(BuiltInPatterns.TAP.name)
        state.libraryRepository.recordPlay(BuiltInPatterns.ERROR.name)
        val nav = WorkbenchNavState(initialTab = Tab.LIBRARY)
        renderToPng("workbench-library-rails", width = 1180, height = 1400) {
            WorkbenchApp(state = state, nav = nav)
        }
    }

    @Test
    fun rendersCompactLibraryPanelStandalone() {
        val state = EditorState()
        renderToPng("library-panel-compact", width = 900, height = 240) {
            LibraryPanel(state = state, onOpenEditor = {})
        }
    }

    @Test
    fun rendersLibraryListPanelStandalone() {
        val state = EditorState()
        renderToPng("library-list-panel", width = 900, height = 1200) {
            LibraryListPanel(state = state, onOpenEditor = {})
        }
    }

    // ---- plain-logic checks on the EditorState wiring ----

    @Test
    fun playFromLibraryLoadsAndRecordsARecent() {
        val state = EditorState()
        val name = BuiltInPatterns.WARNING.name
        assertTrue(state.libraryRepository.recents().none { it.name == name })

        state.playFromLibrary(name)

        assertEquals(name, state.pattern.name)
        assertEquals(name, state.libraryRepository.recents().first().name)
    }

    @Test
    fun removeFromLibraryGoesThroughTheRepositoryAndClearsFavoriteState() {
        val state = EditorState()
        val name = BuiltInPatterns.NOTIFICATION.name
        state.libraryRepository.setFavorite(name, true)
        assertTrue(state.libraryRepository.isFavorite(name))

        state.removeFromLibrary(name)

        assertFalse(state.libraryRepository.isFavorite(name))
        assertEquals(null, state.library.get(name))
    }

    @Test
    fun saveToLibraryIsVisibleThroughTheRepositoryToo() {
        val state = EditorState()
        state.load(BuiltInPatterns.TAP.copy(name = "My Saved Buzz"))

        state.saveToLibrary()

        assertTrue(state.libraryRepository.all().any { it.name == "My Saved Buzz" })
    }
}
