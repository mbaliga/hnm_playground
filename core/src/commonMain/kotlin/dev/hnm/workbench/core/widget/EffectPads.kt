package dev.hnm.workbench.core.widget

import dev.hnm.workbench.core.library.FeelEntry
import dev.hnm.workbench.core.library.FeelKind
import dev.hnm.workbench.core.library.FeelTestCatalog
import kotlin.random.Random

/**
 * The "Effect pads" widget: built-in reference patterns as buttons, ordered so that every size starts
 * with a contrast worth feeling (a crisp single tap against a dull double pulse), then adds the
 * everyday feedback vocabulary. Only built-ins: anything else waits for persisted favourites.
 */
object EffectPads {

    val DEFAULT_IDS: List<String> = listOf("tap", "heartbeat", "confirm", "success", "snap", "notification")

    fun padCount(bucket: SizeBucket): Int = when (bucket) {
        SizeBucket.COLLAPSED -> 0
        SizeBucket.SMALL -> 2
        SizeBucket.MEDIUM -> 4
        SizeBucket.LARGE -> 6
    }

    fun pads(bucket: SizeBucket, find: (String) -> FeelEntry? = FeelTestCatalog::find): List<FeelEntry> =
        DEFAULT_IDS.mapNotNull(find).filter { it.kind == FeelKind.REFERENCE }.take(padCount(bucket))
}

/**
 * The "Pattern of the day" widget's choice: one catalogue entry per local calendar day, cycling through
 * a fixed shuffle of the reference, motion, texture and material entries so every one comes round once
 * per cycle before any repeats. The caller supplies the local day number (days since 1970-01-01), so
 * the day changes at the user's midnight.
 */
object PatternOfTheDay {

    val KINDS: Set<FeelKind> = setOf(FeelKind.REFERENCE, FeelKind.MOTION, FeelKind.TEXTURE, FeelKind.MATERIAL)

    private const val SHUFFLE_SEED = 20261104

    fun cycle(entries: List<FeelEntry> = FeelTestCatalog.entries): List<FeelEntry> =
        entries.filter { it.kind in KINDS }.shuffled(Random(SHUFFLE_SEED))

    fun pick(epochDay: Long, entries: List<FeelEntry> = FeelTestCatalog.entries): FeelEntry? {
        val order = cycle(entries)
        if (order.isEmpty()) return null
        return order[floorMod(epochDay, order.size)]
    }

    private fun floorMod(x: Long, m: Int): Int = (((x % m) + m) % m).toInt()
}
