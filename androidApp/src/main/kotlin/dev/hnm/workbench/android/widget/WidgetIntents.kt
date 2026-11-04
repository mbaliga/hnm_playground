package dev.hnm.workbench.android.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.hnm.workbench.android.FeelActivity
import dev.hnm.workbench.android.MainActivity
import dev.hnm.workbench.android.WorkbenchActivity

/**
 * Every tap a widget offers, as an immutable PendingIntent to one of the app's own activities (explicit
 * components, no URL schemes). Request codes are unique per widget id and slot, so two widgets, or two
 * pads on one widget, never share or overwrite each other's intent.
 *
 * Whatever a widget opens, the workbench stays the root of the app's task: the workbench is started with
 * the launcher's own intent shape (MAIN + LAUNCHER), so a later tap on the app icon finds that task and
 * brings it forward instead of stacking a second workbench on top, and the gallery always opens on top of
 * a workbench rather than becoming the task's root.
 */
object WidgetIntents {

    /** A [dev.hnm.workbench.core.library.FeelTestCatalog] id; the receiving activity validates it. */
    const val EXTRA_PATTERN_ID = "dev.hnm.workbench.android.extra.PATTERN_ID"

    /**
     * The non-exported alias of [WorkbenchActivity] (see the manifest) that widget "open this pattern"
     * taps go through. The workbench loads a pattern only when it was started through this alias, so no
     * other app can replace the pattern being edited by starting the exported launcher activity.
     */
    const val OPEN_PATTERN_ALIAS = "dev.hnm.workbench.android.WidgetOpenPattern"

    /** Plays a catalogue pattern from the translucent [FeelActivity]. */
    const val ACTION_FEEL = "dev.hnm.workbench.android.action.FEEL"

    private const val SLOTS = 32
    const val SLOT_OPEN = 24
    const val SLOT_FEEL_MAIN = 25
    const val SLOT_OPEN_PATTERN = 26
    const val SLOT_GALLERY = 27

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    /** Brings the workbench forward in its task: clears the gallery above it and reuses the instance. */
    private const val TO_WORKBENCH =
        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

    private fun requestCode(appWidgetId: Int, slot: Int): Int {
        require(slot in 0 until SLOTS) { "slot $slot out of range" }
        return appWidgetId * SLOTS + slot
    }

    /** The launcher's intent shape; `filterEquals` ignores extras and flags, so the task root still matches it. */
    private fun launcherShaped(): Intent =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    /** Feel a pattern: slots 0..15 are pads and rows, [SLOT_FEEL_MAIN] a card's own button. */
    fun feel(context: Context, appWidgetId: Int, slot: Int, patternId: String): PendingIntent {
        val intent = Intent(context, FeelActivity::class.java)
            .setAction(ACTION_FEEL)
            .putExtra(EXTRA_PATTERN_ID, patternId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, requestCode(appWidgetId, slot), intent, FLAGS)
    }

    /** Opens the workbench with a catalogue pattern loaded into the editor, through [OPEN_PATTERN_ALIAS]. */
    fun openPattern(context: Context, appWidgetId: Int, patternId: String): PendingIntent {
        val intent = launcherShaped()
            .setClassName(context, OPEN_PATTERN_ALIAS)
            .putExtra(EXTRA_PATTERN_ID, patternId)
            .addFlags(TO_WORKBENCH)
        return PendingIntent.getActivity(context, requestCode(appWidgetId, SLOT_OPEN_PATTERN), intent, FLAGS)
    }

    /**
     * The feel-test gallery, which also carries the device diagnostics and the vibration self-test. It is
     * started on top of the workbench, as the workbench's "Gallery" button does: the first intent brings
     * the app's task forward exactly as the launcher icon would (or starts it with the workbench as its
     * root), and the second reuses a gallery already on top or adds one.
     */
    fun openGallery(context: Context, appWidgetId: Int): PendingIntent {
        val root = launcherShaped()
            .setClass(context, WorkbenchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val gallery = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivities(context, requestCode(appWidgetId, SLOT_GALLERY), arrayOf(root, gallery), FLAGS)
    }

    fun openWorkbench(context: Context, appWidgetId: Int): PendingIntent {
        val intent = launcherShaped()
            .setClass(context, WorkbenchActivity::class.java)
            .addFlags(TO_WORKBENCH)
        return PendingIntent.getActivity(context, requestCode(appWidgetId, SLOT_OPEN), intent, FLAGS)
    }
}
