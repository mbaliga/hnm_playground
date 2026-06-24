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
