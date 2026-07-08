package dev.hnm.workbench.ui.onboarding

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingPreferencesTest {

    @Test
    fun defaultsToNotCompleted() {
        assertFalse(OnboardingPreferences.inMemory().completed)
    }

    @Test
    fun completedSticks() {
        val prefs = OnboardingPreferences.inMemory()
        prefs.completed = true
        assertTrue(prefs.completed)
    }
}
