package dev.hnm.workbench.core.widget

/** A size in density-independent pixels, the unit launchers report widget sizes in. */
data class DpSize(val width: Int, val height: Int) {
    fun fits(widthDp: Float, heightDp: Float): Boolean = width <= widthDp && height <= heightDp
}

/**
 * The layout a home-screen widget draws at a given size. Each template shows more as it grows;
 * below the template's smallest layout it collapses to one tappable summary instead of squeezing
 * its controls.
 */
enum class SizeBucket { COLLAPSED, SMALL, MEDIUM, LARGE }

/**
 * The layout patterns this app's widgets are drawn with (the widget strategy's templates), each with
 * the smallest size its small, medium and large layouts need. The sizes are what the layouts were laid
 * out against: 48 dp touch targets, 12 sp secondary text and the widget's inner padding. They follow the
 * platform's cell formula (n cells ≈ 70n − 30 dp), so 2×1, 2×2, 4×2, 3×3 and 4×3 each land on a layout.
 */
enum class WidgetTemplate(val small: DpSize, val medium: DpSize, val large: DpSize) {
    /** Two to six buttons, each an action: 2 pads stacked, a 2×2 grid, a 3×2 grid with a header. */
    PADS(DpSize(110, 110), DpSize(250, 110), DpSize(250, 180)),

    /** One item read at a glance: label and title; then body and a button; then an image and meta. */
    CARD(DpSize(110, 40), DpSize(250, 110), DpSize(250, 180)),

    /** A list of up to five rows: one, three, then five rows of 48 dp under a header. */
    LIST(DpSize(110, 110), DpSize(180, 180), DpSize(180, 276)),

    /** One value with a label; then a caption; then detail lines. */
    STAT(DpSize(110, 40), DpSize(180, 110), DpSize(250, 180)),

    /** One picture the app draws (alone when small); then a title and legend; then the numbers behind it. */
    IMAGE(DpSize(110, 40), DpSize(180, 110), DpSize(250, 180)),
    ;

    fun sizeOf(bucket: SizeBucket): DpSize = when (bucket) {
        SizeBucket.COLLAPSED -> COLLAPSED_SIZE
        SizeBucket.SMALL -> small
        SizeBucket.MEDIUM -> medium
        SizeBucket.LARGE -> large
    }

    /**
     * The largest layout that fits [widthDp] × [heightDp] — the same choice a launcher makes from a
     * size-mapped widget on Android 12+ — or [SizeBucket.COLLAPSED] when even the small one doesn't fit.
     */
    fun bucketFor(widthDp: Float, heightDp: Float): SizeBucket = when {
        large.fits(widthDp, heightDp) -> SizeBucket.LARGE
        medium.fits(widthDp, heightDp) -> SizeBucket.MEDIUM
        small.fits(widthDp, heightDp) -> SizeBucket.SMALL
        else -> SizeBucket.COLLAPSED
    }

    companion object {
        /** The collapsed summary fits any cell. */
        val COLLAPSED_SIZE = DpSize(40, 40)

        /** Rows a [LIST] layout holds at each size. */
        fun listRows(bucket: SizeBucket): Int = when (bucket) {
            SizeBucket.COLLAPSED -> 0
            SizeBucket.SMALL -> 1
            SizeBucket.MEDIUM -> 3
            SizeBucket.LARGE -> 5
        }
    }
}
