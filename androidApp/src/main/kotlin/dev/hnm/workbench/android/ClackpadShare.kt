package dev.hnm.workbench.android

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import dev.hnm.workbench.core.export.ClackpadExporter
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.playback.HapticCapabilities
import java.io.File

/**
 * The Clackpad hand-off's *standalone* delivery path (`docs/CLACKPAD_CONTRACT.md` §2 delivery b): used
 * when the Workbench was opened on its own — from the library, not from Clackpad — so there's no calling
 * activity to `setResult()` back to (contrast [WorkbenchActivity]'s round-trip `shipToClackpad`, delivery
 * a). Writes the `clackpad-haptic` JSON to this app's own cache dir, wraps it via the app's `FileProvider`
 * (see `AndroidManifest.xml`'s `<provider>` and `res/xml/file_paths.xml`), and hands it to Clackpad
 * directly with `ACTION_SEND` — the standard Android "hand this artifact to another app" idiom, and the
 * one that (unlike the round trip) leaves Clackpad a durable file it can keep.
 */
object ClackpadShare {

    /**
     * @param role the Clackpad feedback role the user explicitly picked for this share. Never inferred —
     *   a standalone share has no launch intent to read a role from, so whatever UI calls this must have
     *   already asked (contract §3's file-level plan: "role is never inferred outside a handoff session —
     *   always explicit at ship time").
     */
    fun shareToClackpad(
        activity: Activity,
        pattern: HapticAudioPattern,
        role: String,
        capabilities: HapticCapabilities,
    ) {
        val (sourceVersion, sourceBuild) = WorkbenchHandoff.hostVersionInfo(activity)
        val json = ClackpadExporter.export(
            pattern = pattern,
            role = role,
            capabilities = capabilities,
            sourceVersion = sourceVersion,
            sourceBuild = sourceBuild,
        )

        val file = File(activity.cacheDir, "clackpad-haptic/${slug(pattern.name)}.clackpad.json").apply {
            parentFile?.mkdirs()
            writeText(json)
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)

        val direct = Intent(Intent.ACTION_SEND).apply {
            type = WorkbenchHandoff.HAPTIC_MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            setPackage(WorkbenchHandoff.PACKAGE) // go straight to Clackpad, no chooser
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { activity.startActivity(direct) }
            .onFailure {
                // Clackpad isn't installed (or no longer resolves `setPackage`): drop straight to a
                // normal chooser instead of a dead end, per the contract's own suggested fallback
                // ("drop setPackage and show a normal chooser, or toast") — try the chooser first, and
                // only toast if there's truly nothing on the device that can take this MIME type either.
                val chooser = Intent(Intent.ACTION_SEND).apply {
                    type = WorkbenchHandoff.HAPTIC_MIME
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { activity.startActivity(Intent.createChooser(chooser, "Send haptic pattern")) }
                    .onFailure {
                        Toast.makeText(activity, "Clackpad isn't installed.", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    /** Filesystem-safe stand-in for the pattern name, e.g. `"Heartbeat!"` -> `"heartbeat"`. */
    private fun slug(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "pattern" }
}
