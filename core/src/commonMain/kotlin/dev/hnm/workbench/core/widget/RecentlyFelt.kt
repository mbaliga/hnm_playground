package dev.hnm.workbench.core.widget

import dev.hnm.workbench.core.library.FeelEntry

/** One pattern the user felt: a [FeelTestCatalog][dev.hnm.workbench.core.library.FeelTestCatalog] id and when. */
data class RecentFeel(val id: String, val feltAtEpochMillis: Long)

/**
 * The "recently felt" list: the last few catalogue patterns the user played, newest first, each once.
 * Only catalogue ids are kept — never a pattern from the editor, whose name can come from the user's own
 * prompt or an imported file — so nothing typed by the user can reach the home screen through it.
 * Stored as plain text, one `id<TAB>millis` line per entry; [decode] skips anything it doesn't
 * recognise, so a damaged file reads as fewer rows, never as a crash.
 */
object RecentlyFelt {
    const val MAX = 5

    private val ID = Regex("[a-z0-9]+(-[a-z0-9]+)*")

    fun isValidId(id: String): Boolean = id.length <= 80 && ID.matches(id)

    /** [id] moves to the front (or is added) with time [nowMillis]; the list keeps at most [max] entries. */
    fun record(current: List<RecentFeel>, id: String, nowMillis: Long, max: Int = MAX): List<RecentFeel> {
        require(isValidId(id)) { "not a catalogue id: $id" }
        return (listOf(RecentFeel(id, nowMillis)) + current.filterNot { it.id == id }).take(max)
    }

    fun encode(list: List<RecentFeel>): String =
        list.joinToString(separator = "") { "${it.id}\t${it.feltAtEpochMillis}\n" }

    fun decode(text: String, max: Int = MAX): List<RecentFeel> =
        text.lineSequence()
            .mapNotNull { line ->
                val parts = line.trim().split('\t')
                if (parts.size != 2) return@mapNotNull null
                val millis = parts[1].toLongOrNull() ?: return@mapNotNull null
                if (!isValidId(parts[0]) || millis < 0) null else RecentFeel(parts[0], millis)
            }
            .distinctBy { it.id }
            .take(max)
            .toList()

    /** Pairs each entry with its catalogue entry, dropping ids this version of the app doesn't know. */
    fun resolve(list: List<RecentFeel>, find: (String) -> FeelEntry?): List<Pair<RecentFeel, FeelEntry>> =
        list.mapNotNull { r -> find(r.id)?.let { r to it } }
}
