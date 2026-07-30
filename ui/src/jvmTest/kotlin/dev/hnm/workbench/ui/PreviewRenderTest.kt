package dev.hnm.workbench.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import dev.hnm.workbench.core.design.MotionPrimitive
import dev.hnm.workbench.core.design.MotionPrimitives
import dev.hnm.workbench.core.design.TextureField
import dev.hnm.workbench.core.design.TextureFieldType
import dev.hnm.workbench.core.design.TextureFields
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.nav.Sheet
import dev.hnm.workbench.ui.nav.Tab
import dev.hnm.workbench.ui.nav.WorkbenchNavState
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Headless render check: draws the full editor off-screen with [ImageComposeScene] (no display
 * needed) and writes a PNG. This verifies the Compose tree actually composes and paints — and the
 * PNG doubles as a screenshot artifact under `ui/build/preview/`.
 */
class PreviewRenderTest {

    @Test
    fun rendersEditorToPng() {
        val width = 1180
        val height = 820
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            WorkbenchApp()
        }
        try {
            val image = scene.render()
            val png = image.encodeToData(EncodedImageFormat.PNG)?.bytes
                ?: error("PNG encode returned null")

            val outDir = File("build/preview").apply { mkdirs() }
            File(outDir, "workbench.png").writeBytes(png)

            // A blank/failed render would be tiny; a real composed UI is comfortably larger.
            assertTrue(png.size > 5_000, "rendered PNG too small (${png.size} bytes) — UI likely didn't compose")
            assertTrue(image.width == width && image.height == height)
        } finally {
            scene.close()
        }
    }

    @Test
    fun rendersEditorWithLoadedMotionPrimitive() {
        // Exercises the Stage-1 path: a motion primitive loaded into the editor renders end-to-end.
        val state = EditorState().apply { load(MotionPrimitives.toPattern(MotionPrimitive.SETTLE)) }
        val scene = ImageComposeScene(width = 1180, height = 820, density = Density(1f)) {
            WorkbenchApp(state)
        }
        try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
                ?: error("PNG encode returned null")
            File("build/preview").apply { mkdirs() }
            File("build/preview/workbench-settle.png").writeBytes(png)
            assertTrue(png.size > 5_000)
        } finally {
            scene.close()
        }
    }

    @Test
    fun rendersEditorWithLoadedTextureField() {
        // Exercises the Stage-2 path: a procedural texture field scrubbed into the editor renders end-to-end.
        val field = TextureField(type = TextureFieldType.FBM, roughness = 0.7)
        val state = EditorState().apply { load(TextureFields.toPattern(field)) }
        val scene = ImageComposeScene(width = 1180, height = 820, density = Density(1f)) {
            WorkbenchApp(state)
        }
        try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
                ?: error("PNG encode returned null")
            File("build/preview").apply { mkdirs() }
            File("build/preview/workbench-texture.png").writeBytes(png)
            assertTrue(png.size > 5_000)
        } finally {
            scene.close()
        }
    }

    @Test
    fun rendersEditorWithNavigatorFamily() {
        // Exercises the Stage-3 path: a member of an interpolated motion family loaded end-to-end.
        val family = dev.hnm.workbench.core.design.ParameterNavigator.motionFamilyPatterns(
            MotionPrimitive.STIR, MotionPrimitive.SETTLE, count = 5,
        )
        val state = EditorState().apply { load(family[2]) }
        val scene = ImageComposeScene(width = 1180, height = 900, density = Density(1f)) {
            WorkbenchApp(state)
        }
        try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
                ?: error("PNG encode returned null")
            File("build/preview").apply { mkdirs() }
            File("build/preview/workbench-navigator.png").writeBytes(png)
            assertTrue(png.size > 5_000)
        } finally {
            scene.close()
        }
    }

    @Test
    fun rendersNarrowPhoneLayout() {
        // Exercises the responsive single-column path at phone width (< 720dp): walkthrough + assistant
        // + stacked panels must compose without the desktop two-column layout.
        val scene = ImageComposeScene(width = 400, height = 1600, density = Density(1f)) {
            WorkbenchApp()
        }
        try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
                ?: error("PNG encode returned null")
            File("build/preview").apply { mkdirs() }
            File("build/preview/workbench-phone.png").writeBytes(png)
            assertTrue(png.size > 5_000, "narrow layout didn't compose (${png.size} bytes)")
        } finally {
            scene.close()
        }
    }

    @Test
    fun rendersAssistantGeneratedPattern() = kotlinx.coroutines.test.runTest {
        // Exercises the AI path end-to-end: generate from a prompt, load it, and render the editor.
        val state = EditorState()
        state.generate("urgent alert")
        assertTrue(state.assistantMessage != null, "assistant should have explained its work")
        val scene = ImageComposeScene(width = 1180, height = 900, density = Density(1f)) {
            WorkbenchApp(state)
        }
        try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
                ?: error("PNG encode returned null")
            File("build/preview").apply { mkdirs() }
            File("build/preview/workbench-assistant.png").writeBytes(png)
            assertTrue(png.size > 5_000)
        } finally {
            scene.close()
        }
    }

    @Test
    fun rendersSplashFrame() {
        // Draw a deterministic mid-animation frame of each procedural splash motif.
        dev.hnm.workbench.core.design.SplashMotifs.all().forEach { scene ->
            val s = ImageComposeScene(width = 600, height = 900, density = Density(1f)) {
                dev.hnm.workbench.ui.components.SplashScreen(scene = scene, fixedTimeSec = 0.7)
            }
            try {
                val png = s.render().encodeToData(EncodedImageFormat.PNG)?.bytes
                    ?: error("PNG encode returned null")
                File("build/preview").apply { mkdirs() }
                File("build/preview/splash-${scene.visual.name.lowercase()}.png").writeBytes(png)
                assertTrue(png.size > 5_000, "${scene.visual} splash didn't compose (${png.size} bytes)")
            } finally {
                s.close()
            }
        }
    }

    @Test
    fun rendersEditorWithLoadedMaterial() {
        // Exercises the Stage-4 path: a struck material (sound + haptics from one modal model) loaded.
        val state = EditorState().apply {
            load(dev.hnm.workbench.core.design.ModalSynth.toPattern(dev.hnm.workbench.core.design.MaterialPreset.METAL.material))
        }
        val scene = ImageComposeScene(width = 1180, height = 900, density = Density(1f)) {
            WorkbenchApp(state)
        }
        try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
                ?: error("PNG encode returned null")
            File("build/preview").apply { mkdirs() }
            File("build/preview/workbench-material.png").writeBytes(png)
            assertTrue(png.size > 5_000)
        } finally {
            scene.close()
        }
    }

    // ---- nav shell: tabs, the pushed editor route, and sheets ----

    @Test
    fun rendersEachTab() {
        // Exercises the bottom-tab shell end-to-end: Library (row list), Make (assistant + palettes),
        // and Device (capability picker) must each compose on their own, without an editor pushed.
        Tab.entries.forEach { tab ->
            val state = EditorState()
            val nav = WorkbenchNavState(initialTab = tab)
            val scene = ImageComposeScene(width = 1180, height = 900, density = Density(1f)) {
                WorkbenchApp(state = state, nav = nav)
            }
            try {
                val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
                    ?: error("PNG encode returned null")
                File("build/preview").apply { mkdirs() }
                File("build/preview/workbench-tab-${tab.name.lowercase()}.png").writeBytes(png)
                assertTrue(png.size > 5_000, "$tab tab didn't compose (${png.size} bytes)")
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun rendersEditorRoutePushedFromLibraryChevron() {
        // Simulates the library row's chevron: load a saved pattern, then push the full-screen editor —
        // the same two calls LibraryListPanel's chevron makes — and confirm the deep-edit surface renders.
        val state = EditorState()
        val name = state.library.names.first()
        state.loadFromLibrary(name)
        val nav = WorkbenchNavState(initialTab = Tab.LIBRARY)
        nav.openEditor(name)
        val scene = ImageComposeScene(width = 1180, height = 900, density = Density(1f)) {
            WorkbenchApp(state = state, nav = nav)
        }
        try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
                ?: error("PNG encode returned null")
            File("build/preview").apply { mkdirs() }
            File("build/preview/workbench-editor-route.png").writeBytes(png)
            assertTrue(png.size > 5_000, "editor route didn't compose (${png.size} bytes)")
        } finally {
            scene.close()
        }
    }

    @Test
    fun rendersEditorRouteNarrow() {
        // The editor route's narrow (phone-width) body, pinned transport slab included.
        val state = EditorState()
        val nav = WorkbenchNavState()
        nav.openEditor()
        val scene = ImageComposeScene(width = 400, height = 1600, density = Density(1f)) {
            WorkbenchApp(state = state, nav = nav)
        }
        try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
                ?: error("PNG encode returned null")
            File("build/preview").apply { mkdirs() }
            File("build/preview/workbench-editor-route-narrow.png").writeBytes(png)
            assertTrue(png.size > 5_000, "narrow editor route didn't compose (${png.size} bytes)")
        } finally {
            scene.close()
        }
    }

    @Test
    fun rendersEverySheet() {
        // Every Sheet route (real panel or placeholder) must render over both the tab shell and the
        // pushed editor without crashing — the whole point of routes existing ahead of their content.
        Sheet.entries.forEach { sheet ->
            listOf(false, true).forEach { overEditor ->
                val state = EditorState()
                val nav = WorkbenchNavState()
                if (overEditor) nav.openEditor()
                nav.openSheet(sheet)
                val scene = ImageComposeScene(width = 1180, height = 900, density = Density(1f)) {
                    WorkbenchApp(state = state, nav = nav)
                }
                try {
                    val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
                        ?: error("PNG encode returned null")
                    val suffix = if (overEditor) "editor" else "tabs"
                    File("build/preview").apply { mkdirs() }
                    File("build/preview/workbench-sheet-${sheet.name.lowercase()}-$suffix.png").writeBytes(png)
                    assertTrue(png.size > 5_000, "$sheet sheet (over $suffix) didn't compose (${png.size} bytes)")
                } finally {
                    scene.close()
                }
            }
        }
    }

    @Test
    fun galleryActionDefaultsToLibraryTabNavigationWhenHostDoesNotOverrideIt() {
        // No onOpenGallery host override supplied (the default WorkbenchApp() call every host except
        // Android's WorkbenchActivity makes): the keypad's Gallery key must be wired into our own nav —
        // Library tab, editor popped — rather than staying the dead no-op stub it used to be.
        val nav = WorkbenchNavState(initialTab = Tab.MAKE)
        nav.openEditor("Confirm")
        val action = resolveGalleryAction(nav, onOpenGallery = null)

        action()

        assertTrue(nav.tab == Tab.LIBRARY, "expected the Library tab, got ${nav.tab}")
        assertTrue(nav.screen == null, "expected the pushed editor to be popped, got ${nav.screen}")
    }

    @Test
    fun galleryActionUsesHostOverrideWhenProvided() {
        // Android's WorkbenchActivity passes onOpenGallery to jump to its own gallery Activity instead —
        // resolveGalleryAction must defer to that override and leave nav untouched.
        val nav = WorkbenchNavState(initialTab = Tab.MAKE)
        var hostInvoked = false
        val action = resolveGalleryAction(nav) { hostInvoked = true }

        action()

        assertTrue(hostInvoked, "expected the host override to run")
        assertTrue(nav.tab == Tab.MAKE, "host override should not also drive nav")
    }
}
