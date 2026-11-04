package dev.hnm.workbench.android

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.hnm.workbench.android.widget.WidgetIntents
import dev.hnm.workbench.core.library.FeelTestCatalog
import dev.hnm.workbench.core.playback.HapticCapabilities
import dev.hnm.workbench.ui.WorkbenchApp
import dev.hnm.workbench.ui.model.EditorState

/**
 * The main experience: hosts the shared Compose [WorkbenchApp] and wires its Play button to the real
 * actuator + speaker via [AndroidPatternPlayer]. The editor's target profile is initialized from what
 * this device actually reports, so the on-screen schedule matches what gets played. A "Gallery" button
 * jumps to the flat feel-test list ([MainActivity]). A home-screen widget can open it with a gallery
 * pattern loaded ([WidgetIntents.EXTRA_PATTERN_ID], through the non-exported
 * [WidgetIntents.OPEN_PATTERN_ALIAS]); an open workbench keeps its session and just loads it.
 */
class WorkbenchActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val audio = AudioPlayer()
    private lateinit var vibrator: Vibrator
    private var capabilities: HapticCapabilities = HapticCapabilities.LRA_FULL
    private lateinit var state: EditorState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vibrator = AndroidHaptics.vibrator(this)
        capabilities = AndroidHaptics.probe(vibrator)

        state = EditorState().apply {
            capabilities = this@WorkbenchActivity.capabilities
            player = AndroidPatternPlayer(
                vibrator = vibrator,
                capabilities = this@WorkbenchActivity.capabilities,
                handler = handler,
                audio = audio,
                onMessage = { toast(it) },
            )
        }

        setContent {
            WorkbenchApp(
                state = state,
                onOpenGallery = { startActivity(Intent(this, MainActivity::class.java)) },
            )
        }
        openRequestedPattern(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRequestedPattern(intent)
    }

    /**
     * Loads the gallery pattern a widget asked for. Only an intent that came through the app's own
     * non-exported alias counts, so another app starting this exported activity can't replace the
     * pattern being edited; anything else (or an unknown id) is ignored.
     */
    private fun openRequestedPattern(intent: Intent?) {
        if (intent?.component?.className != WidgetIntents.OPEN_PATTERN_ALIAS) return
        val id = intent.getStringExtra(WidgetIntents.EXTRA_PATTERN_ID) ?: return
        FeelTestCatalog.find(id)?.let { state.load(it.pattern) }
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
