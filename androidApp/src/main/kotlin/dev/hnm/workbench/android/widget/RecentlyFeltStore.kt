package dev.hnm.workbench.android.widget

import android.content.Context
import dev.hnm.workbench.core.widget.RecentFeel
import dev.hnm.workbench.core.widget.RecentlyFelt
import java.io.File

/**
 * The "recently felt" list on disk: a few lines of catalogue ids and times under
 * [Context.getNoBackupFilesDir], so it never travels in a cloud backup. Writes go to a temporary file
 * that replaces the old one, and a missing or damaged file reads as an empty list.
 */
class RecentlyFeltStore(context: Context) {

    private val file = File(File(context.noBackupFilesDir, "widgets"), "recently-felt.txt")

    fun read(): List<RecentFeel> = synchronized(LOCK) {
        runCatching {
            if (!file.isFile || file.length() > MAX_BYTES) emptyList() else RecentlyFelt.decode(file.readText())
        }.getOrDefault(emptyList())
    }

    fun record(patternId: String, nowMillis: Long = System.currentTimeMillis()) {
        if (!RecentlyFelt.isValidId(patternId)) return
        synchronized(LOCK) {
            val next = RecentlyFelt.record(read(), patternId, nowMillis)
            val dir = file.parentFile ?: return
            if (!dir.isDirectory && !dir.mkdirs()) return
            val tmp = File(dir, file.name + ".tmp")
            tmp.writeText(RecentlyFelt.encode(next))
            if (!tmp.renameTo(file)) {
                file.writeText(RecentlyFelt.encode(next))
                tmp.delete()
            }
        }
    }

    private companion object {
        val LOCK = Any()
        const val MAX_BYTES = 16 * 1024L
    }
}
