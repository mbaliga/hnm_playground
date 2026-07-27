package dev.hnm.workbench.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import dev.hnm.workbench.core.settings.OnboardingProgress
import dev.hnm.workbench.ui.components.StarterChecklist
import dev.hnm.workbench.ui.components.WalkthroughCard
import dev.hnm.workbench.ui.model.EditorState
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Headless render checks (see [PreviewRenderTest] for the pattern) for the starter checklist + ladder. */
class StarterChecklistRenderTest {

    private fun renderToPng(
        name: String,
        width: Int = 380,
        height: Int = 420,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f), content = content)
        try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes ?: error("PNG encode returned null")
            File("build/preview").apply { mkdirs() }
            File("build/preview/$name.png").writeBytes(png)
            assertTrue(png.size > 2_000, "$name didn't compose (${png.size} bytes)")
        } finally {
            scene.close()
        }
    }

    @Test
    fun rendersChecklistAtEveryRungOfProgress() {
        val progressions = listOf(
            "new" to OnboardingProgress(),
            "felt" to OnboardingProgress(hasFeltOne = true),
            "felt-made" to OnboardingProgress(hasFeltOne = true, hasMadeOne = true),
            "felt-made-changed" to OnboardingProgress(hasFeltOne = true, hasMadeOne = true, hasChangedOne = true),
            "all-done" to OnboardingProgress(hasFeltOne = true, hasMadeOne = true, hasChangedOne = true, hasShippedOne = true),
            "shipped-only" to OnboardingProgress(hasShippedOne = true),
        )
        progressions.forEach { (label, progress) ->
            renderToPng("checklist-$label") { StarterChecklist(progress) }
        }
    }

    @Test
    fun rendersWalkthroughCardWithChecklistAndStepsTogether() {
        renderToPng("walkthrough-card-with-checklist", width = 420, height = 1200) {
            WalkthroughCard(EditorState())
        }
    }
}
