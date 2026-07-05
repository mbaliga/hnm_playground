package dev.hnm.workbench.android

import android.app.Application
import dev.aarso.crashrecovery.CrashRecovery

/**
 * Installs crash capture as early as possible, so a device-only launch crash (which CI never
 * sees — CI runs unit tests, never launches the app) is diagnosable on the next launch
 * instead of silently bricking the install. See [WorkbenchActivity.onCreate].
 */
class WorkbenchApplication : Application() {
    override fun onCreate() {
        CrashRecovery.install(this, appLabel = "Haptics Workbench")
        super.onCreate()
    }
}
