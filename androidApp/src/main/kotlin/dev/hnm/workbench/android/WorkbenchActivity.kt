package dev.hnm.workbench.android

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.hnm.workbench.core.playback.HapticCapabilities
import dev.hnm.workbench.ui.WorkbenchWithSplash
import dev.hnm.workbench.ui.model.EditorState

/**
 * The main experience: hosts the shared Compose [WorkbenchApp] and wires its Play button to the real
 * actuator + speaker via [AndroidPatternPlayer]. The editor's target profile is initialized from what
 * this device actually reports, so the on-screen schedule matches what gets played. A "Gallery" button
 * jumps to the flat feel-test list ([MainActivity]).
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
