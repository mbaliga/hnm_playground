package dev.hnm.workbench.android

import android.content.Context
import dev.hnm.workbench.core.export.ClackpadExporter

/**
 * Constants for the Clackpad ⟷ Haptics + Audio Workbench hand-off, per the agreed
 * `docs/CLACKPAD_CONTRACT.md` ("Clackpad ⟷ Haptics + Audio Workbench Integration Contract").
 *
 * Clackpad (`mbaliga/Clackpad`) keeps its own, independently-maintained copy of this same object
 * (`com.clackpad.ime.WorkbenchHandoff`), with [PACKAGE]/[ACTIVITY_CLASS] swapped to point back at this
 * app. There is no shared Gradle module between the two repos, so the *literal string values* below are
 * the actual contract — the contract document is the source of truth if the two copies ever drift.
 *
 * [HAPTIC_MIME] and [VALID_ROLES] are not re-declared as fresh literals here: they're sourced straight
 * from [ClackpadExporter], the one place in *this* repo that already owns them (contract §2 resolution
 * 6 — hnm_playground owns the `clackpad-haptic` schema), so the launch-side check and the export-side
 * payload can never quietly drift apart from each other.
 */
object WorkbenchHandoff {

    /** Clackpad's own package name — both the required launch [EXTRA_SOURCE] (contract §1) and the
     *  explicit-component / `setPackage()` target for every Intent this app sends back to Clackpad. */
    const val PACKAGE: String = "com.clackpad.ime"

    /** Clackpad's settings/import activity — the standalone hand-off's `ACTION_SEND` target and the
     *  round-trip's implicit "whoever started us via [ACTION_DESIGN_HAPTIC]" return address. */
    const val ACTIVITY_CLASS: String = "com.clackpad.ime.SettingsActivity"

    /**
     * Namespaced under this app's own action prefix (contract §1). No version suffix by design: if this
     * launch contract ever needs a breaking change, it ships as a new action string
     * (`…DESIGN_HAPTIC_V2`), not an extra bolted onto this one.
     */
    const val ACTION_DESIGN_HAPTIC: String = "dev.hnm.workbench.action.DESIGN_HAPTIC"

    /** String extra: one of [VALID_ROLES] — Clackpad's own `HorizKeebService.vibrate(type)` vocabulary,
     *  verbatim. Required on the launch Intent. */
    const val EXTRA_ROLE: String = "dev.hnm.workbench.extra.ROLE"

    /** String extra: the launching app's own package name (i.e. literally `packageName` at the call
     *  site). Must equal [PACKAGE] for the launch to be honored as a real hand-off. */
    const val EXTRA_SOURCE: String = "dev.hnm.workbench.extra.SOURCE"

    /**
     * String extra carrying the `clackpad-haptic` JSON, set on the result [android.content.Intent] this
     * app returns via `setResult()`/`finish()` for a Clackpad-initiated session (contract §2 delivery a).
     * No filesystem, no `content://` URI — the payload is a few KB, well under the Binder transaction
     * ceiling, so it travels as a plain string extra.
     */
    const val EXTRA_RESULT_JSON: String = "dev.hnm.workbench.extra.RESULT_JSON"

    /** Deliberately distinct from `application/json`, which Clackpad's own full-settings-backup import
     *  already claims (contract §2) — see [ClackpadExporter.MIME_TYPE]. */
    val HAPTIC_MIME: String = ClackpadExporter.MIME_TYPE

    /** Clackpad's closed five-value feedback-role vocabulary — see [ClackpadExporter.VALID_ROLES]. */
    val VALID_ROLES: Set<String> = ClackpadExporter.VALID_ROLES

    /**
     * This app's own installed version name/code, read from [android.content.pm.PackageManager] rather
     * than a build-time constant (no `BuildConfig` field generation needs enabling for it) — feeds every
     * exported `clackpad-haptic` file's `source.version`/`source.build` provenance on both hand-off paths:
     * [WorkbenchActivity]'s round trip and [ClackpadShare]'s standalone send. Never throws; falls back to
     * an honestly-unknown `"0.0.0"`/`"0"` rather than crash a share/ship action over provenance metadata.
     */
    fun hostVersionInfo(context: Context): Pair<String, String> = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        (info.versionName ?: "0.0.0") to info.longVersionCode.toString()
    } catch (t: Throwable) {
        "0.0.0" to "0"
    }
}
