package dev.hnm.workbench.android

import android.content.Context
import android.util.AtomicFile
import dev.hnm.workbench.core.companion.PatternSafety
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.PatternSerialization
import dev.hnm.workbench.core.library.PatternLibrary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** One process-local owner; activity and sync service mutate on the main dispatcher. */
class AndroidWorkspaceStore internal constructor(context: Context) {
    private val directory = File(context.filesDir, "hafx").apply { mkdirs() }
    private val libraryFile = AtomicFile(File(directory, "library.json"))
    private val draftFile = AtomicFile(File(directory, "draft.json"))
    private val changes = MutableStateFlow(0)
    val version = changes.asStateFlow()
    val notice = MutableStateFlow<String?>(null)
    private var libraryReadFailed = false
    val library: PatternLibrary = runCatching {
        if (libraryFile.baseFile.exists() || File(libraryFile.baseFile.path + ".bak").exists()) {
            val text = readBounded(libraryFile, 4 * 1024 * 1024)
            PatternLibrary.fromJson(text).also { lib ->
                require(lib.size <= 256) { "Library has too many effects" }
                lib.all().forEach(PatternSafety::validate)
            }
        } else PatternLibrary.withBuiltIns()
    }.getOrElse {
        libraryReadFailed = true
        notice.value = "Your saved library could not be read. It has been preserved; new saves are disabled. ${it.message}"
        PatternLibrary.withBuiltIns()
    }.apply {
        persist = { text ->
            check(!libraryReadFailed) { "Saved library needs recovery; its file has not been overwritten" }
            require(text.encodeToByteArray().size <= 4 * 1024 * 1024) { "Library is full; export or remove effects first" }
            write(libraryFile, text)
        }
        onChanged = { changes.value++ }
    }

    val hasRecoveryBackup get() = libraryReadFailed || directory.listFiles()?.any { it.name.startsWith("library-recovery-") } == true
    fun recoveryBackup(): ByteArray {
        val file=if(libraryReadFailed) libraryFile.baseFile else directory.listFiles()
            ?.filter { it.name.startsWith("library-recovery-") }?.maxByOrNull { it.lastModified() }
            ?: error("No recovery file is available")
        return file.inputStream().use { it.readBounded(8 * 1024 * 1024) }.also { require(it.size <= 8 * 1024 * 1024) { "Recovery file exceeds the export limit; the original remains archived on this device" } }
    }
    val needsRecovery get() = libraryReadFailed
    fun recoverLibrary() {
        check(libraryReadFailed) { "Library is readable" }
        val archived = File(directory, "library-recovery-${System.currentTimeMillis()}.json")
        libraryFile.openRead().use { input -> archived.outputStream().use { output -> input.copyTo(output); output.fd.sync() } }
        check(archived.length() > 0) { "Could not archive the unreadable library" }
        libraryReadFailed = false
        try {
            library.save(library.all().first())
            notice.value = "Fresh library ready. Original retained as ${archived.name}. Import a backup to restore effects."
        } catch (t: Throwable) { libraryReadFailed = true; throw t }
    }

    fun loadDraft(): HapticAudioPattern? = runCatching {
        if (!draftFile.baseFile.exists() && !File(draftFile.baseFile.path + ".bak").exists()) null
        else PatternSerialization.decode(readBounded(draftFile, PatternSafety.MAX_BYTES)).also(PatternSafety::validate)
    }.getOrElse { notice.value = "Draft could not be restored: ${it.message}"; null }

    fun saveDraft(pattern: HapticAudioPattern) {
        PatternSafety.validate(pattern)
        val text = PatternSerialization.encode(pattern, pretty = false)
        require(text.encodeToByteArray().size <= PatternSafety.MAX_BYTES) { "Draft exceeds 64 KiB" }
        write(draftFile, text)
    }

    private fun readBounded(file: AtomicFile, limit: Int): String = file.openRead().use {
        val bytes = it.readBounded(limit)
        require(bytes.size <= limit) { "Stored file exceeds size limit" }
        bytes.decodeToString()
    }

    private fun write(file: AtomicFile, text: String) {
        val stream = file.startWrite()
        try { stream.write(text.encodeToByteArray()); file.finishWrite(stream) }
        catch (t: Throwable) { file.failWrite(stream); throw t }
    }

    companion object {
        @Volatile private var instance: AndroidWorkspaceStore? = null
        fun get(context: Context): AndroidWorkspaceStore = instance ?: synchronized(this) {
            instance ?: AndroidWorkspaceStore(context.applicationContext).also { instance = it }
        }
    }
}
