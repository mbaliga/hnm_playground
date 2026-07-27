package dev.hnm.workbench.ui.coachmarks

import dev.hnm.workbench.core.settings.OnboardingProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plain-logic tests for [CoachMarkEngine]/[CoachAnchorRegistry] — no Compose scene needed, since neither
 * type depends on composition to answer "what should be showing right now". [CoachAnchorRegistry.setPresent]
 * is exactly what [CoachAnchor] calls from `DisposableEffect`, so driving it directly here simulates a
 * target composing/decomposing (tab switches, sheets opening, routes popping) without needing to render
 * anything — see [dev.hnm.workbench.ui.PreviewRenderTest] / `CoachMarkRenderTest` for the render-level
 * integration coverage of the real wiring.
 */
class CoachMarkEngineTest {

    private val feel = CoachMarkSpec(CoachAnchorId.LIBRARY_TAB, "Feel one", "…")
    private val change = CoachMarkSpec(CoachAnchorId.EDITOR_INSPECTOR, "Change one", "…")
    private val ship = CoachMarkSpec(CoachAnchorId.EDITOR_SHIP, "Ship one", "…", clearOn = ClearOn.TARGET_ACTION)

    @Test
    fun noCoachIsActiveUntilItsTargetReportsLive() {
        val engine = CoachMarkEngine()
        engine.specs = listOf(feel)
        assertNull(engine.active, "nothing enqueued should show before any anchor registers itself live")

        engine.registry.setPresent(CoachAnchorId.LIBRARY_TAB, true)
        assertEquals(feel, engine.active)
    }

    @Test
    fun anUnreachableCoachStepsAsideForALaterReachableOneInsteadOfStarvingIt() {
        // This is the exact bug class the engine exists to avoid: the front of the queue (LIBRARY_TAB)
        // targets a tab the user hasn't visited yet, while a later entry (EDITOR_SHIP) is on the route
        // they're actually standing on right now. A naive "always show specs.first()" queue would show
        // nothing at all (or worse, block forever waiting for LIBRARY_TAB); ours must show EDITOR_SHIP.
        val engine = CoachMarkEngine()
        engine.specs = listOf(feel, ship)
        engine.registry.setPresent(CoachAnchorId.EDITOR_SHIP, true) // only the Editor route is reachable

        assertEquals(ship, engine.active, "an unreachable front-of-queue coach must not block a reachable one behind it")
        assertFalse(engine.isDismissed(CoachAnchorId.LIBRARY_TAB), "stepping aside must not be recorded as dismissal")
    }

    @Test
    fun aParkedCoachFiresLaterOnceItsTargetBecomesReachable() {
        val engine = CoachMarkEngine()
        engine.specs = listOf(feel, ship)
        engine.registry.setPresent(CoachAnchorId.EDITOR_SHIP, true)
        assertEquals(ship, engine.active)

        // The user now actually navigates to the Library tab: its anchor composes and reports live.
        engine.registry.setPresent(CoachAnchorId.LIBRARY_TAB, true)
        assertEquals(feel, engine.active, "the earlier queue entry should take over now that it's reachable")
    }

    @Test
    fun aCoachGoesQuietAgainWhenItsTargetStopsBeingReachable() {
        // Simulates a sheet opening over an anchor that's still composed underneath it, or a tab switch
        // away from it — either way CoachAnchor's DisposableEffect(onDispose) calls setPresent(false).
        val engine = CoachMarkEngine()
        engine.specs = listOf(feel)
        engine.registry.setPresent(CoachAnchorId.LIBRARY_TAB, true)
        assertEquals(feel, engine.active)

        engine.registry.setPresent(CoachAnchorId.LIBRARY_TAB, false)
        assertNull(engine.active)
        assertFalse(engine.isDismissed(CoachAnchorId.LIBRARY_TAB), "going unreachable again is not a dismissal")
    }

    @Test
    fun dismissClearsACoachForTheRestOfTheSessionEvenIfItBecomesReachableAgain() {
        val engine = CoachMarkEngine()
        engine.specs = listOf(feel)
        engine.registry.setPresent(CoachAnchorId.LIBRARY_TAB, true)

        engine.dismiss(CoachAnchorId.LIBRARY_TAB)
        assertNull(engine.active)
        assertTrue(engine.isDismissed(CoachAnchorId.LIBRARY_TAB))

        // Even if the user leaves and comes back to the same reachable target, a DISMISS-cleared coach
        // must stay gone (unlike simply going out-of-reach, which is not a dismissal).
        engine.registry.setPresent(CoachAnchorId.LIBRARY_TAB, false)
        engine.registry.setPresent(CoachAnchorId.LIBRARY_TAB, true)
        assertNull(engine.active)
    }

    @Test
    fun clearOnDismissBlocksUntilExplicitlyTapped() {
        val engine = CoachMarkEngine()
        engine.specs = listOf(change) // default clearOn = DISMISS
        engine.registry.setPresent(CoachAnchorId.EDITOR_INSPECTOR, true)

        // Using the target (clearByAction) must NOT clear a DISMISS-mode coach.
        engine.clearByAction(CoachAnchorId.EDITOR_INSPECTOR)
        assertEquals(change, engine.active, "clearOn=DISMISS must ignore target-action clears and keep blocking")

        engine.dismiss(CoachAnchorId.EDITOR_INSPECTOR)
        assertNull(engine.active)
    }

    @Test
    fun clearOnTargetActionClearsWithoutAnExplicitDismissTap() {
        val engine = CoachMarkEngine()
        engine.specs = listOf(ship)
        engine.registry.setPresent(CoachAnchorId.EDITOR_SHIP, true)
        assertEquals(ship, engine.active)

        engine.clearByAction(CoachAnchorId.EDITOR_SHIP)
        assertNull(engine.active)
        assertTrue(engine.isDismissed(CoachAnchorId.EDITOR_SHIP))
    }

    @Test
    fun resetDismissedClearsSessionState() {
        val engine = CoachMarkEngine()
        engine.specs = listOf(feel)
        engine.registry.setPresent(CoachAnchorId.LIBRARY_TAB, true)
        engine.dismiss(CoachAnchorId.LIBRARY_TAB)
        assertTrue(engine.isDismissed(CoachAnchorId.LIBRARY_TAB))

        engine.resetDismissed()
        assertFalse(engine.isDismissed(CoachAnchorId.LIBRARY_TAB))
        assertEquals(feel, engine.active)
    }

    @Test
    fun registryReportsLastKnownBoundsSeparatelyFromLiveness() {
        val registry = CoachAnchorRegistry()
        assertNull(registry.boundsOf(CoachAnchorId.LIBRARY_TAB))
        assertFalse(registry.isLive(CoachAnchorId.LIBRARY_TAB))

        val bounds = androidx.compose.ui.geometry.Rect(0f, 0f, 10f, 10f)
        registry.setPresent(CoachAnchorId.LIBRARY_TAB, true, bounds)
        assertTrue(registry.isLive(CoachAnchorId.LIBRARY_TAB))
        assertEquals(bounds, registry.boundsOf(CoachAnchorId.LIBRARY_TAB))

        registry.setPresent(CoachAnchorId.LIBRARY_TAB, false)
        assertFalse(registry.isLive(CoachAnchorId.LIBRARY_TAB))
        assertNull(registry.boundsOf(CoachAnchorId.LIBRARY_TAB), "bounds should not survive going unreachable")
    }

    // ---- onboardingCoachMarkSpecs: pure derivation from OnboardingProgress ----

    @Test
    fun freshProgressQueuesAllThreeWiredRungsInLadderOrder() {
        val specs = onboardingCoachMarkSpecs(OnboardingProgress())
        assertEquals(
            listOf(CoachAnchorId.LIBRARY_TAB, CoachAnchorId.EDITOR_INSPECTOR, CoachAnchorId.EDITOR_SHIP),
            specs.map { it.id },
        )
    }

    @Test
    fun completedRungsDropOutOfTheQueue() {
        val specs = onboardingCoachMarkSpecs(OnboardingProgress(hasFeltOne = true, hasShippedOne = true))
        assertEquals(listOf(CoachAnchorId.EDITOR_INSPECTOR), specs.map { it.id })
    }

    @Test
    fun fullyDoneProgressQueuesNothing() {
        val specs = onboardingCoachMarkSpecs(
            OnboardingProgress(hasFeltOne = true, hasMadeOne = true, hasChangedOne = true, hasShippedOne = true),
        )
        assertTrue(specs.isEmpty())
    }
}
