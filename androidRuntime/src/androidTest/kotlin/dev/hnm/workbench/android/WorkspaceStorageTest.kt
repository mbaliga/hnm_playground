package dev.hnm.workbench.android

import android.content.Context
import android.content.ContextWrapper
import android.test.InstrumentationTestCase
import dev.hnm.workbench.core.library.StarterEffects
import java.io.File

@Suppress("DEPRECATION")
class WorkspaceStorageTest : InstrumentationTestCase() {
    fun testOfflineLibraryDraftAndCorruptFileRecovery() {
        val directory=File(instrumentation.targetContext.cacheDir,"workspace-test-${System.nanoTime()}").apply { mkdirs() }
        val isolated=object: ContextWrapper(instrumentation.targetContext) {
            override fun getFilesDir(): File = directory
            override fun getApplicationContext(): Context = this
        }
        try {
            val first=AndroidWorkspaceStore(isolated)
            val saved=first.library.saveUser(StarterEffects.UNLOCK,"watch")
            first.saveDraft(saved)
            val reopened=AndroidWorkspaceStore(isolated)
            assertEquals(saved,reopened.library.get(saved.name))
            assertEquals(saved,reopened.loadDraft())
            val libraryFile=File(directory,"hafx/library.json")
            libraryFile.writeText("unreadable-library")
            val damaged=AndroidWorkspaceStore(isolated)
            assertTrue(damaged.needsRecovery)
            var rejected=false
            try { damaged.library.saveUser(StarterEffects.LOCK) } catch (_: Exception) { rejected=true }
            assertTrue(rejected)
            assertEquals("unreadable-library",libraryFile.readText())
            damaged.recoverLibrary()
            assertFalse(damaged.needsRecovery)
            assertEquals("unreadable-library",damaged.recoveryBackup().decodeToString())
            assertEquals(saved,damaged.loadDraft())
            val restored=damaged.library.saveUser(StarterEffects.LOCK)
            assertEquals(restored,AndroidWorkspaceStore(isolated).library.get(restored.name))
        } finally { directory.deleteRecursively() }
    }
}
