package dev.hnm.workbench.ui

import dev.hnm.workbench.core.playback.PatternPlayer
import dev.hnm.workbench.core.settings.OnboardingTier
import dev.hnm.workbench.ui.model.EditorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [EditorState] is the seam between real UI actions (play, generate, edit, ship) and the beginner-ladder
 * fields on [dev.hnm.workbench.core.settings.SettingsStore] the starter checklist and coachmarks both
 * read from — these tests exercise that wiring directly, independent of Compose rendering.
 */
class EditorStateOnboardingTest {

    @Test
    fun freshEditorStateStartsAtTheNewTier() {
        val state = EditorState()
        assertEquals(OnboardingTier.NEW, state.onboarding.tier)
    }

    @Test
    fun playingWithNoActuatorWiredDoesNotCountAsFeltOne() {
        // Default EditorState() has PatternPlayer.None (desktop has no actuator) — nothing was actually
        // felt, so playCurrent() must stay a safe no-op for onboarding purposes too.
        val state = EditorState()
        state.playCurrent()
        assertFalse(state.onboarding.hasFeltOne)
    }

    @Test
    fun playingWithARealPlayerWiredMarksFeltOne() {
        var played = false
        val state = EditorState().apply { player = PatternPlayer { played = true } }

        state.playCurrent()

        assertTrue(played, "the real player must still actually be invoked")
        assertTrue(state.onboarding.hasFeltOne)
        assertEquals(OnboardingTier.FELT_ONE, state.onboarding.tier)
    }

    @Test
    fun playingFromLibraryAlsoMarksFeltOneViaTheSharedPlayCurrentPath() {
        val state = EditorState().apply { player = PatternPlayer { } }
        val name = state.library.names.first()

        state.playFromLibrary(name)

        assertTrue(state.onboarding.hasFeltOne)
    }

    @Test
    fun successfulGenerateMarksMadeOne() = kotlinx.coroutines.test.runTest {
        val state = EditorState()
        assertFalse(state.onboarding.hasMadeOne)

        state.generate("urgent alert")

        assertTrue(state.onboarding.hasMadeOne)
    }

    @Test
    fun blankPromptDoesNotMarkMadeOne() = kotlinx.coroutines.test.runTest {
        val state = EditorState()
        state.generate("   ")
        assertFalse(state.onboarding.hasMadeOne, "an empty prompt never reaches the generator, so nothing was actually made")
    }

    @Test
    fun editingTheSelectedEventMarksChangedOne() {
        val state = EditorState()
        state.select(0)
        assertFalse(state.onboarding.hasChangedOne)

        state.setSelectedIntensity(0.4)

        assertTrue(state.onboarding.hasChangedOne)
    }

    @Test
    fun editingWithNothingSelectedIsANoOpAndDoesNotMarkChangedOne() {
        val state = EditorState()
        state.select(null)

        state.setSelectedIntensity(0.4)

        assertFalse(state.onboarding.hasChangedOne)
    }

    @Test
    fun markShippedGoesThroughToSettingsAndIsIdempotent() {
        val state = EditorState()

        state.markShipped()
        assertTrue(state.onboarding.hasShippedOne)

        state.markShipped()
        assertEquals(OnboardingTier.SHIPPED_ONE, state.onboarding.tier)
    }

    @Test
    fun doingAllFourReachesTheShippedTierRegardlessOfOrder() {
        val state = EditorState().apply { player = PatternPlayer { } }
        state.select(0)

        state.markShipped()
        state.setSelectedIntensity(0.9)
        state.playCurrent()

        assertTrue(state.onboarding.hasShippedOne)
        assertTrue(state.onboarding.hasChangedOne)
        assertTrue(state.onboarding.hasFeltOne)
        assertEquals(OnboardingTier.SHIPPED_ONE, state.onboarding.tier)
    }
}
