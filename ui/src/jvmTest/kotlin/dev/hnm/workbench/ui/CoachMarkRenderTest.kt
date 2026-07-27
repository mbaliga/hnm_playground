package dev.hnm.workbench.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import dev.hnm.workbench.core.settings.InMemorySettingsStore
import dev.hnm.workbench.core.settings.OnboardingProgress
import dev.hnm.workbench.core.settings.WorkbenchSettings
import dev.hnm.workbench.ui.coachmarks.CoachAnchorId
import dev.hnm.workbench.ui.coachmarks.CoachMarkEngine
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.nav.Sheet
import dev.hnm.workbench.ui.nav.Tab
import dev.hnm.workbench.ui.nav.WorkbenchNavState
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Render-level integration coverage for the coachmark wiring in [WorkbenchApp]: [CoachMarkEngine] and
 * [dev.hnm.workbench.ui.coachmarks.CoachAnchorRegistry] are hoisted the same way [EditorState]/
 * [WorkbenchNavState] already are (see [PreviewRenderTest]), so a test can render the real nav shell and
 * then assert on what the engine actually resolved as reachable — not just that composition didn't crash.
 */
class CoachMarkRenderTest {

    /**
     * Renders once and runs [check] *before* the scene is closed. This matters: [ImageComposeScene.close]
     * disposes the composition, which fires every live [dev.hnm.workbench.ui.coachmarks.CoachAnchor]'s
     * `DisposableEffect.onDispose` — so a registry/engine read taken *after* closing would see everything
     * as unregistered regardless of what was actually reachable while the scene was alive.
     */
    private fun render(
        state: EditorState,
        nav: WorkbenchNavState,
        coachMarks: CoachMarkEngine,
        name: String,
        check: () -> Unit,
    ) {
        val scene = ImageComposeScene(width = 1180, height = 900, density = Density(1f)) {
            WorkbenchApp(state = state, nav = nav, coachMarks = coachMarks)
        }
        try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes ?: error("PNG encode returned null")
            File("build/preview").apply { mkdirs() }
            File("build/preview/$name.png").writeBytes(png)
            check()
        } finally {
            scene.close()
        }
    }

    @Test
    fun freshUserSeesTheLibraryTabCoachFromAnyTabInTheShell() {
        val state = EditorState()
        val nav = WorkbenchNavState(initialTab = Tab.MAKE)
        val coachMarks = CoachMarkEngine()

        render(state, nav, coachMarks, "coachmark-library-from-make-tab") {
            assertEquals(CoachAnchorId.LIBRARY_TAB, coachMarks.active?.id, "the Library tab button is reachable from every tab, not just when Library is selected")
        }
    }

    @Test
    fun unreachableFrontOfQueueStepsAsideForTheReachableEditorCoachInsteadOfShowingNothing() {
        // The exact bug class this engine exists to avoid: LIBRARY_TAB is first in the queue (nothing
        // done yet) but the user is standing in the Editor route, where the Library tab bar isn't even
        // composed. A naive "always render specs.first()" implementation would show nothing (or a
        // coachmark pointing at a control that isn't on screen); ours must fall through to EDITOR_INSPECTOR.
        val state = EditorState()
        val nav = WorkbenchNavState(initialTab = Tab.MAKE)
        nav.openEditor()
        val coachMarks = CoachMarkEngine()

        render(state, nav, coachMarks, "coachmark-editor-inspector-while-library-unreached") {
            assertEquals(CoachAnchorId.EDITOR_INSPECTOR, coachMarks.active?.id)
        }
    }

    @Test
    fun openingASheetOverTheTabShellBlocksTheStillComposedLibraryCoach() {
        // BottomTabBar (and its LIBRARY_TAB anchor) stays composed underneath an open sheet — SheetHost
        // draws content() first, the sheet on top of it — so composition presence alone can't tell a
        // covered anchor from a visible one. LocalCoachMarksBlocked is the explicit signal that does.
        val state = EditorState()
        val nav = WorkbenchNavState(initialTab = Tab.MAKE)
        nav.openSheet(Sheet.SETTINGS)
        val coachMarks = CoachMarkEngine()

        render(state, nav, coachMarks, "coachmark-blocked-by-sheet") {
            assertNull(coachMarks.active, "a sheet covering the only reachable anchor must block its coachmark, not just its own content")
        }
    }

    @Test
    fun dismissingTheSheetUnblocksTheCoachAgain() {
        val state = EditorState()
        val nav = WorkbenchNavState(initialTab = Tab.MAKE)
        nav.openSheet(Sheet.SETTINGS)
        val coachMarks = CoachMarkEngine()
        render(state, nav, coachMarks, "coachmark-blocked-by-sheet-before") {
            assertNull(coachMarks.active)
        }

        nav.dismissSheet()
        render(state, nav, coachMarks, "coachmark-unblocked-after-sheet-dismissed") {
            assertEquals(CoachAnchorId.LIBRARY_TAB, coachMarks.active?.id)
        }
    }

    @Test
    fun aCompletedRungIsNeverQueuedEvenWhenItsSurfaceIsReachable() {
        val store = InMemorySettingsStore(initial = WorkbenchSettings(onboarding = OnboardingProgress(hasFeltOne = true)))
        val state = EditorState(settings = store)
        val nav = WorkbenchNavState(initialTab = Tab.LIBRARY)
        val coachMarks = CoachMarkEngine()

        render(state, nav, coachMarks, "coachmark-felt-one-already-done") {
            // hasFeltOne is already true, so LIBRARY_TAB must not be in the queue at all — and nothing
            // else (EDITOR_INSPECTOR / EDITOR_SHIP) is reachable from the tab shell.
            assertNull(coachMarks.active)
        }
    }

    @Test
    fun fullyCompletedOnboardingShowsNoCoachAnywhere() {
        val store = InMemorySettingsStore(
            initial = WorkbenchSettings(
                onboarding = OnboardingProgress(
                    hasFeltOne = true,
                    hasMadeOne = true,
                    hasChangedOne = true,
                    hasShippedOne = true,
                ),
            ),
        )
        val state = EditorState(settings = store)
        val nav = WorkbenchNavState(initialTab = Tab.MAKE)
        nav.openEditor()
        val coachMarks = CoachMarkEngine()

        render(state, nav, coachMarks, "coachmark-fully-done") {
            assertNull(coachMarks.active)
        }
    }
}
