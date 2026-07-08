package dev.hnm.workbench.ui.splash

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SplashPreferencesTest {

    @Test
    fun showsForFirstThreeLaunches() {
        val prefs = SplashPreferences.inMemory()
        prefs.launchCount = 0
        assertTrue(prefs.shouldShowSplash())
        prefs.launchCount = 2
        assertTrue(prefs.shouldShowSplash())
    }

    @Test
    fun skipsByDefaultAfterThreeLaunches() {
        val prefs = SplashPreferences.inMemory()
        prefs.launchCount = 3
        assertFalse(prefs.shouldShowSplash())
        prefs.launchCount = 50
        assertFalse(prefs.shouldShowSplash())
    }

    @Test
    fun explicitOverrideWinsRegardlessOfLaunchCount() {
        val prefs = SplashPreferences.inMemory()
        prefs.launchCount = 0
        prefs.skipIntroOverride = true
        assertFalse(prefs.shouldShowSplash(), "explicit skip=true should suppress even on launch 1")

        prefs.launchCount = 50
        prefs.skipIntroOverride = false
        assertTrue(prefs.shouldShowSplash(), "explicit skip=false (always show) should win even after many launches")
    }
}
