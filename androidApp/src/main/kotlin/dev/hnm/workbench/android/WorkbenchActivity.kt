package dev.hnm.workbench.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.hnm.workbench.core.playback.HapticCapabilities
import dev.hnm.workbench.ui.WorkbenchApp
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.nav.WorkbenchNavState

/**
 * The main experience: hosts the shared Compose [WorkbenchApp] and wires its Play button to the real
 * actuator + speaker via [AndroidPatternPlayer]. The editor's target profile is initialized from what
 * this device actually reports, so the on-screen schedule matches what gets played. A "Gallery" button
 * jumps to the flat feel-test list ([MainActivity]).
 *
 * Also the receiving end of the Clackpad ⟷ Workbench hand-off (`docs/CLACKPAD_CONTRACT.md`, both
 * directions):
 *  - **Launch** (§1): [WorkbenchActivity] is already `exported=true` with a `MAIN`/`LAUNCHER`
 *    intent-filter, so Clackpad's *explicit-component* [WorkbenchHandoff.ACTION_DESIGN_HAPTIC] intent is
 *    delivered here without any extra `<intent-filter>` — explicit-component intents bypass intent-filter
 *    matching entirely. [onCreate] reads the action/extras itself, in [incomingHandoffRole].
 *  - **Ship** (§2 delivery a): when this session *was* such a hand-off, the Export panel's "Send to
 *    Clackpad" button (wired below via `onShipToClackpad`) calls [shipToClackpad], which returns the
 *    built `clackpad-haptic` JSON as a plain string extra via `setResult()`/`finish()` — no filesystem,
 *    no `content://` URI, mirroring Clackpad's own `REQ_*`/`onActivityResult` idiom.
 */
class WorkbenchActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val audio = AudioPlayer()
    private lateinit var vibrator: Vibrator
    private var capabilities: HapticCapabilities = HapticCapabilities.LRA_FULL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vibrator = AndroidHaptics.vibrator(this)
        capabilities = AndroidHaptics.probe(vibrator)

        val state = EditorState().apply {
            capabilities = this@WorkbenchActivity.capabilities
            player = AndroidPatternPlayer(
                vibrator = vibrator,
                capabilities = this@WorkbenchActivity.capabilities,
                handler = handler,
                audio = audio,
                onMessage = { toast(it) },
            )
        }

        // A plain WorkbenchNavState() constructed here (not `remember`ed inside the composable) so a
        // valid hand-off can push the Editor route *before* the first frame — the same "build state in
        // onCreate, hand it to setContent" shape `state` above already uses. Compose observes both via
        // their own mutableStateOf-backed fields regardless of when they were constructed.
        val nav = WorkbenchNavState()
        val handoffRole = incomingHandoffRole(intent)
        if (handoffRole != null) {
            state.beginHandoff(handoffRole)
            nav.openEditor(state.pattern.name)
        }

        val (sourceVersion, sourceBuild) = WorkbenchHandoff.hostVersionInfo(this)

        setContent {
            WorkbenchApp(
                state = state,
                onOpenGallery = { startActivity(Intent(this, MainActivity::class.java)) },
                nav = nav,
                onShipToClackpad = { json -> shipToClackpad(json) },
                sourceVersion = sourceVersion,
                sourceBuild = sourceBuild,
            )
        }
    }

    override fun onDestroy() {
        audio.release()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /**
     * Validates an incoming launch against contract §1's exact rule: the action must be
     * [WorkbenchHandoff.ACTION_DESIGN_HAPTIC], [WorkbenchHandoff.EXTRA_ROLE] must be one of the five
     * closed values, and [WorkbenchHandoff.EXTRA_SOURCE] must be exactly Clackpad's own package name.
     * Never throws — a missing/unknown role, a wrong or absent source, or any other launch (including
     * the ordinary home-screen `MAIN`/`LAUNCHER` one) all resolve to `null` here, so the caller falls
     * back to a normal, non-hand-off open rather than a crash or a dead screen.
     */
    private fun incomingHandoffRole(intent: Intent): String? {
        if (intent.action != WorkbenchHandoff.ACTION_DESIGN_HAPTIC) return null
        if (intent.getStringExtra(WorkbenchHandoff.EXTRA_SOURCE) != WorkbenchHandoff.PACKAGE) return null
        return intent.getStringExtra(WorkbenchHandoff.EXTRA_ROLE)?.takeIf { it in WorkbenchHandoff.VALID_ROLES }
    }

    /**
     * The round-trip result for a Clackpad-initiated session (contract §2 delivery a). [json] is already
     * the fully-built `clackpad-haptic` payload (built in `core` by [dev.hnm.workbench.core.export.ClackpadExporter],
     * via [dev.hnm.workbench.ui.model.EditorState.exportClackpadJson]) — this method only has to hand it
     * back and close. If the user instead backs out without shipping, this is never called, so
     * `onDestroy` runs with the default `RESULT_CANCELED` — exactly the "no `setResult` call" case the
     * contract describes, and exactly what Clackpad's own `onActivityResult` already no-ops on.
     */
    private fun shipToClackpad(json: String) {
        setResult(Activity.RESULT_OK, Intent().putExtra(WorkbenchHandoff.EXTRA_RESULT_JSON, json))
        finish()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
