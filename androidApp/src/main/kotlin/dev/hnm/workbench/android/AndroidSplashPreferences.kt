package dev.hnm.workbench.android

import android.content.Context
import android.provider.Settings
import dev.hnm.workbench.ui.splash.SplashPreferences

/** SharedPreferences-backed [SplashPreferences] — the real, persisted implementation for the device. */
class AndroidSplashPreferences(context: Context) : SplashPreferences {
    private val prefs = context.getSharedPreferences("splash_prefs", Context.MODE_PRIVATE)

    override var launchCount: Int
        get() = prefs.getInt(KEY_LAUNCH_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_LAUNCH_COUNT, value).apply()

    override var skipIntroOverride: Boolean?
        get() = if (prefs.contains(KEY_SKIP_OVERRIDE)) prefs.getBoolean(KEY_SKIP_OVERRIDE, false) else null
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove(KEY_SKIP_OVERRIDE) else editor.putBoolean(KEY_SKIP_OVERRIDE, value)
            editor.apply()
        }

    /** The seed of the most recently played splash — for the About screen's "replay last splash" hook. */
    var lastSeed: Int
        get() = prefs.getInt(KEY_LAST_SEED, 0)
        set(value) = prefs.edit().putInt(KEY_LAST_SEED, value).apply()

    private companion object {
        const val KEY_LAUNCH_COUNT = "launch_count"
        const val KEY_SKIP_OVERRIDE = "skip_intro_override"
        const val KEY_LAST_SEED = "last_splash_seed"
    }
}

/**
 * Best-effort reduced-motion signal: Android has no single cross-OEM "prefers reduced motion" API, but
 * the system-wide animator duration scale being zero (Settings → Accessibility → Remove animations, or
 * a developer-options toggle) is the closest reliable proxy and is what most reduced-motion-aware apps
 * key off in practice.
 */
fun isSystemReducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
