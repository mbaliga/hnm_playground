package dev.hnm.workbench.android

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.hnm.workbench.core.device.DeviceDatabase
import dev.hnm.workbench.core.playback.HapticCapabilities
import dev.hnm.workbench.ui.WorkbenchWithSplash
import dev.hnm.workbench.ui.model.EditorState

/**
 * The single-activity app (UX brief §3.1 D1): hosts the shared Compose app shell (Feel/Make/Device tabs
 * + the Editor route), wiring Play, the interface-feel chrome, self-test, and the device-report capture
 * to the real actuator + speaker via [AndroidPatternPlayer]/[AndroidHaptics]. The editor's target profile
 * is initialized from what this device actually reports, so the on-screen schedule matches what gets
 * played. [onOpenGallery] still reaches the legacy native-Views gallery ([MainActivity]) — kept as a
 * safety-hatch hook per the brief, not surfaced as a button in the new tabbed UI (the Feel tab replaces
 * its purpose).
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

        // A different procedural splash motif each launch; its visual, sound and haptics come from one
        // pattern and it plays on the real actuator wired above, then reveals the workbench.
        val splashSeed = (android.os.SystemClock.elapsedRealtime() / 500L).toInt()

        setContent {
            WorkbenchWithSplash(
                state = state,
                seed = splashSeed,
                onOpenGallery = { startActivity(Intent(this, MainActivity::class.java)) },
                onSelfTest = { AndroidHaptics.selfTest(vibrator, handler) { toast(it) } },
                onCaptureDeviceReport = {
                    DeviceDatabase(listOf(AndroidHaptics.probeProfile(vibrator))).toJson()
                },
            )
        }
    }

    override fun onDestroy() {
        audio.release()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
