package dev.hnm.workbench.ui.onboarding

/**
 * Persisted onboarding completion (UX brief §6, Phase 6): the six-beat walkthrough shown once, after
 * the splash, before the first real use. Same tiny platform seam as
 * [dev.hnm.workbench.ui.splash.SplashPreferences]: desktop/tests get [inMemory], the Android host
 * injects a SharedPreferences-backed implementation.
 */
interface OnboardingPreferences {
    /** Whether the walkthrough has already been shown (and shouldn't be shown again automatically). */
    var completed: Boolean

    companion object {
        fun inMemory(): OnboardingPreferences = object : OnboardingPreferences {
            override var completed: Boolean = false
        }
    }
}
