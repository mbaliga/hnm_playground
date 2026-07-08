package dev.hnm.workbench.ui.splash

/**
 * Persisted splash behavior (UX brief §4.2/§6.5): shown every launch for the first three, then skipped
 * by default — but always replayable, and the "skip after first run" default itself is a toggle. A tiny
 * platform seam, same shape as [dev.hnm.workbench.core.playback.PatternPlayer]: desktop/tests get
 * [inMemory] (nothing persists across process restarts, which is correct there — there's no "first run"
 * concept without a real install), the Android host injects a SharedPreferences-backed implementation.
 */
interface SplashPreferences {
    /** Launches seen so far. Incremented once per cold start by the host, before the splash decision. */
    var launchCount: Int

    /** Explicit user choice from Settings; null means "use the launch-count default." */
    var skipIntroOverride: Boolean?

    companion object {
        fun inMemory(): SplashPreferences = object : SplashPreferences {
            override var launchCount: Int = 0
            override var skipIntroOverride: Boolean? = null
        }
    }
}

/** Default ON after the first three launches, unless the user has explicitly overridden it. */
fun SplashPreferences.shouldShowSplash(): Boolean =
    skipIntroOverride?.let { skip -> !skip } ?: (launchCount < 3)
