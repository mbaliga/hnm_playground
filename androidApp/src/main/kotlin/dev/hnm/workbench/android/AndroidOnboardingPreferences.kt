package dev.hnm.workbench.android

import android.content.Context
import dev.hnm.workbench.ui.onboarding.OnboardingPreferences

/** SharedPreferences-backed [OnboardingPreferences] — the real, persisted implementation for the device. */
class AndroidOnboardingPreferences(context: Context) : OnboardingPreferences {
    private val prefs = context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)

    override var completed: Boolean
        get() = prefs.getBoolean(KEY_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_COMPLETED, value).apply()

    private companion object {
        const val KEY_COMPLETED = "completed"
    }
}
