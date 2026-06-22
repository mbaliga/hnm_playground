package dev.hnm.workbench.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
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
}
