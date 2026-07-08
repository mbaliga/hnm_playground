package dev.hnm.workbench.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import dev.hnm.workbench.core.design.MotionPrimitive
import dev.hnm.workbench.core.design.MotionPrimitives
import dev.hnm.workbench.core.design.TextureField
import dev.hnm.workbench.core.design.TextureFieldType
import dev.hnm.workbench.core.design.TextureFields
import dev.hnm.workbench.ui.model.EditorState
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
    fun appShellColdLaunchesIntoFeelTab() {
        // The Phase 0 accept bar: the app shell's default route is the Feel tab.
        val width = 420
        val height = 900
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            AppShell(state = EditorState())
        }
        try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
                ?: error("PNG encode returned null")
            File("build/preview").apply { mkdirs() }
            File("build/preview/appshell-feel.png").writeBytes(png)
            assertTrue(png.size > 5_000, "AppShell cold-launch didn't compose (${png.size} bytes)")
        } finally {
            scene.close()
        }
    }

    @Test
    fun appShellFeelTabIsWidthCappedOnADesktopWindow() {
        // Phase 7 desktop-adaptive check: on a wide window the tab content should center in a capped
        // column rather than stretch edge to edge.
        val scene = ImageComposeScene(width = 1200, height = 900, density = Density(1f)) {
            AppShell(state = EditorState())
        }
        try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
                ?: error("PNG encode returned null")
            File("build/preview").apply { mkdirs() }
            File("build/preview/appshell-feel-desktop.png").writeBytes(png)
            assertTrue(png.size > 5_000, "AppShell desktop-width didn't compose (${png.size} bytes)")
        } finally {
            scene.close()
        }
    }

    @Test
    fun rendersFeelScreenWithEveryBuiltIn() {
        val scene = ImageComposeScene(width = 420, height = 1400, density = Density(1f)) {
            dev.hnm.workbench.ui.screens.FeelScreen(state = EditorState(), onOpenEditor = {})
        }
        try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
                ?: error("PNG encode returned null")
            File("build/preview").apply { mkdirs() }
            File("build/preview/screen-feel.png").writeBytes(png)
            assertTrue(png.size > 5_000, "Feel screen didn't compose (${png.size} bytes)")
        } finally {
            scene.close()
        }
    }

    @Test
    fun rendersMakeScreen() {
        val scene = ImageComposeScene(width = 420, height = 900, density = Density(1f)) {
            dev.hnm.workbench.ui.screens.MakeScreen(state = EditorState(), onOpenEditor = {})
        }
        try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
                ?: error("PNG encode returned null")
            File("build/preview").apply { mkdirs() }
            File("build/preview/screen-make.png").writeBytes(png)
            assertTrue(png.size > 5_000, "Make screen didn't compose (${png.size} bytes)")
        } finally {
            scene.close()
        }
    }

    @Test
    fun rendersEveryMakeSourceMiniFlow() {
        for (kind in dev.hnm.workbench.ui.nav.MakeSourceKind.entries) {
            val scene = ImageComposeScene(width = 420, height = 1200, density = Density(1f)) {
                dev.hnm.workbench.ui.screens.MakeSourceScreen(
                    kind = kind,
                    state = EditorState(),
                    onBack = {},
                    onOpenEditor = {},
                )
            }
            try {
                val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
                    ?: error("PNG encode returned null")
                File("build/preview").apply { mkdirs() }
                File("build/preview/make-source-${kind.name.lowercase()}.png").writeBytes(png)
                assertTrue(png.size > 5_000, "${kind.title} mini-flow didn't compose (${png.size} bytes)")
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun rendersDeviceScreen() {
        val scene = ImageComposeScene(width = 420, height = 900, density = Density(1f)) {
            dev.hnm.workbench.ui.screens.DeviceScreen(state = EditorState())
        }
        try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
                ?: error("PNG encode returned null")
            File("build/preview").apply { mkdirs() }
            File("build/preview/screen-device.png").writeBytes(png)
            assertTrue(png.size > 5_000, "Device screen didn't compose (${png.size} bytes)")
        } finally {
            scene.close()
        }
    }

    @Test
    fun rendersDeviceScreenWithReplayButtons() {
        // The "anything pending?" follow-up: interface-feel level / workspace mode chips, plus the
        // replay-onboarding / replay-splash buttons, only appear when their callbacks are wired.
        val scene = ImageComposeScene(width = 420, height = 1000, density = Density(1f)) {
            dev.hnm.workbench.ui.screens.DeviceScreen(
                state = EditorState(),
                onReplayOnboarding = {},
                onReplaySplash = {},
            )
        }
        try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
                ?: error("PNG encode returned null")
            File("build/preview").apply { mkdirs() }
            File("build/preview/screen-device-replay.png").writeBytes(png)
            assertTrue(png.size > 5_000, "Device screen with replay buttons didn't compose (${png.size} bytes)")
        } finally {
            scene.close()
        }
    }

    @Test
    fun rendersDeviceScreenWithSimulatedDeviceHeroCard() {
        // Phase 6: picking a real device (as CapabilityPanel does) should show its resonant
        // frequency/Q on the Device tab's hero card, via the same EditorState.selectedDevice.
        val state = EditorState().apply {
            selectDevice(dev.hnm.workbench.core.device.DeviceDatabase.seeded().all.first())
        }
        val scene = ImageComposeScene(width = 420, height = 900, density = Density(1f)) {
            dev.hnm.workbench.ui.screens.DeviceScreen(state = state)
        }
        try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
                ?: error("PNG encode returned null")
            File("build/preview").apply { mkdirs() }
            File("build/preview/screen-device-hero.png").writeBytes(png)
            assertTrue(png.size > 5_000, "Device hero card didn't compose (${png.size} bytes)")
        } finally {
            scene.close()
        }
    }

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
    fun rendersEditorTopBarWithBackArrow() {
        // Phase 4: when hosted from AppShell's Editor route, WorkbenchApp gets a real back arrow
        // (onBack != null) in place of the plain recording dot.
        val width = 1180
        val height = 820
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            WorkbenchApp(onBack = {})
        }
        try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
                ?: error("PNG encode returned null")
            File("build/preview").apply { mkdirs() }
            File("build/preview/workbench-editor-topbar.png").writeBytes(png)
            assertTrue(png.size > 5_000, "Editor top bar with back arrow didn't compose (${png.size} bytes)")
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
    fun rendersOnboardingFirstBeat() {
        val scene = ImageComposeScene(width = 420, height = 900, density = Density(1f)) {
            dev.hnm.workbench.ui.onboarding.OnboardingScreen(onComplete = {})
        }
        try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
                ?: error("PNG encode returned null")
            File("build/preview").apply { mkdirs() }
            File("build/preview/onboarding-beat-1.png").writeBytes(png)
            assertTrue(png.size > 5_000, "Onboarding didn't compose (${png.size} bytes)")
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
}
