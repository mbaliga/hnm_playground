package dev.hnm.workbench.android

import androidx.activity.result.contract.ActivityResultContracts
import dev.hnm.workbench.core.companion.WorkspaceFiles
import java.io.File
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import dev.hnm.workbench.core.device.DeviceDatabase
import dev.hnm.workbench.core.library.BuiltInPatterns
import dev.hnm.workbench.ui.AppShell
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.WorkbenchTheme
import kotlinx.coroutines.*

class WorkbenchActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val draftHandler = Handler(Looper.getMainLooper())
    private lateinit var state: EditorState
    private lateinit var player: AndroidPatternPlayer
    private lateinit var store: AndroidWorkspaceStore
    private lateinit var sync: PatternSync

    private val pendingExport get() = File(cacheDir, "hafx-pending-export")
    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val effects = withContext(Dispatchers.IO) {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBounded(WorkspaceFiles.MAX_BACKUP_BYTES) } ?: error("File unavailable")
                    WorkspaceFiles.decode(bytes)
                }
                val added = store.library.importEffects(effects)
                state.workspaceMessage = "Imported $added effects. Find them in Yours. Your draft is unchanged."
            }.onFailure { state.workspaceMessage = "Import failed: ${it.message}" }
        }
    }
    private val openSearchText = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) scope.launch {
            runCatching {
                val text=withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBounded(8192).decodeToString(throwOnInvalidSequence=true) } ?: error("File unavailable")
                }
                require(text.isNotBlank() && text.length <= 2000) { "Choose a short UTF-8 text file, up to 2,000 characters" }
                state.load(state.pattern.copy(metadata=state.pattern.metadata + ("hafx.search.text" to text)))
                state.workspaceMessage="Text attached. Save and send this effect; open Search lab on the watch. Text travels with the effect."
            }.onFailure { state.workspaceMessage="Could not attach text: ${it.message}" }
        }
    }
    private val createDocument = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri, "wt")?.use { out -> pendingExport.inputStream().use { it.copyTo(out) } } ?: error("File unavailable")
                }
                state.workspaceMessage = "File saved"
            }.onFailure { state.workspaceMessage = "Export failed: ${it.message}" }
            pendingExport.delete()
        } else pendingExport.delete()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AndroidWorkspaceStore.get(this)
        sync = PatternSync(this)
        val vibrator = AndroidHaptics.vibrator(this)
        val capabilities = AndroidHaptics.probe(vibrator)
        player = AndroidPatternPlayer(vibrator, applicationContext, capabilities)
        state = EditorState(store.library, store.loadDraft() ?: BuiltInPatterns.CONFIRM) { pattern ->
            draftHandler.removeCallbacksAndMessages(null)
            draftHandler.postDelayed({
                runCatching { store.saveDraft(pattern) }.onFailure { state.workspaceMessage = "Draft not saved: ${it.message}" }
            }, 250)
        }.apply {
            this.capabilities = capabilities
            playbackCapabilities = capabilities
            playbackDeviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
            this.player = this@WorkbenchActivity.player
            workspaceMessage = store.notice.value
            recoveryAvailable = store.needsRecovery
            recoveryBackupAvailable = store.hasRecoveryBackup
            exportRecoveryBackup = {
                runCatching { store.recoveryBackup() }
                    .onSuccess { exportFile?.invoke("hafx-library-recovery.json", "application/json", it) }
                    .onFailure { workspaceMessage="Could not export recovery file: ${it.message}" }
            }
            recoverLibrary = {
                runCatching { store.recoverLibrary() }
                    .onSuccess { recoveryAvailable = false; recoveryBackupAvailable = store.hasRecoveryBackup; workspaceMessage = store.notice.value }
                    .onFailure { workspaceMessage = "Recovery failed: ${it.message}" }
            }
            attachSearchText = { openSearchText.launch(arrayOf("text/plain")) }
            importFile = { openDocument.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }
            exportFile = { name, mime, bytes ->
                runCatching {
                    pendingExport.writeBytes(bytes)
                    createDocument.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE); type=mime; putExtra(Intent.EXTRA_TITLE, name)
                    })
                }.onFailure { workspaceMessage = "Could not export: ${it.message}" }
            }
            shareExport = { text ->
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
                }, "Share HAfx export"))
            }
            sendToCompanion = { pattern -> scope.launch {
                runCatching { sync.send(pattern) }.onFailure { workspaceMessage = "Send failed: ${it.message}" }
            } }
        }
        val preferences = getSharedPreferences("hafx_workspace", MODE_PRIVATE)
        runCatching { state.workspaceMode = dev.hnm.workbench.ui.model.WorkspaceMode.valueOf(preferences.getString("mode", "VIBE")!!) }
        runCatching { state.interfaceFeelLevel = dev.hnm.workbench.core.playback.InterfaceFeelLevel.valueOf(preferences.getString("feel", "SUBTLE")!!) }
        scope.launch { store.version.collect { state.libraryVersion = it } }
        state.refreshCompanion = { scope.launch { sync.reconcile() } }
        scope.launch { sync.connection.collect { state.companionConnection = it } }
        scope.launch { sync.status.collect { state.companionStatus = it } }
        setContent {
            WorkbenchTheme {
                AppShell(
                    state = state,
                    onSelfTest = { state.audition(dev.hnm.workbench.core.companion.WristPatterns.doublePulse()) },
                    onCaptureDeviceReport = { DeviceDatabase(listOf(AndroidHaptics.probeProfile(vibrator))).toJson() },
                    reducedMotion = isSystemReducedMotion(this),
                )
            }
        }
    }

    override fun onResume() { super.onResume(); if (::sync.isInitialized) scope.launch { sync.reconcile() } }
    override fun onStop() {
        if (::player.isInitialized) player.stop()
        draftHandler.removeCallbacksAndMessages(null)
        if (::state.isInitialized) runCatching { store.saveDraft(state.pattern) }
            .onFailure { state.workspaceMessage = "Draft not saved: ${it.message}" }
        if (::state.isInitialized) getSharedPreferences("hafx_workspace", MODE_PRIVATE).edit()
            .putString("mode", state.workspaceMode.name).putString("feel", state.interfaceFeelLevel.name).apply()
        super.onStop()
    }
    override fun onDestroy() { draftHandler.removeCallbacksAndMessages(null); player.close(); sync.close(); scope.cancel(); super.onDestroy() }
}
