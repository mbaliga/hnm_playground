package dev.hnm.workbench.android.widget

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import dev.hnm.workbench.android.R
import dev.hnm.workbench.core.library.FeelEntry
import dev.hnm.workbench.core.library.FeelTestCatalog
import dev.hnm.workbench.core.widget.RecentFeel
import dev.hnm.workbench.core.widget.RecentlyFelt
import dev.hnm.workbench.core.widget.SizeBucket
import dev.hnm.workbench.core.widget.WidgetTemplate

/**
 * "Recently felt" (template `list`): the last one, three or five catalogue patterns felt from the gallery
 * or a widget, newest first, each with the date and time it was felt (absolute, so it stays true
 * without a redraw). A row plays its pattern again. No counts or streaks: it is a shortcut, not a record.
 */
class RecentlyFeltWidget : HnmWidgetProvider<List<Pair<RecentFeel, FeelEntry>>>() {

    override val template = WidgetTemplate.LIST

    override fun load(context: Context): List<Pair<RecentFeel, FeelEntry>> =
        RecentlyFelt.resolve(RecentlyFeltStore(context).read(), FeelTestCatalog::find)

    override fun views(
        context: Context,
        appWidgetId: Int,
        data: List<Pair<RecentFeel, FeelEntry>>,
        bucket: SizeBucket,
    ): RemoteViews {
        val layout = when (bucket) {
            SizeBucket.SMALL -> R.layout.widget_list_small
            SizeBucket.MEDIUM -> R.layout.widget_list_medium
            else -> R.layout.widget_list_large
        }
        val views = RemoteViews(context.packageName, layout)
        views.setTextViewText(R.id.list_header, context.getString(R.string.widget_recent_header))
        views.setOnClickPendingIntent(android.R.id.background, WidgetIntents.openGallery(context, appWidgetId))
        if (data.isEmpty()) {
            val empty = context.getString(R.string.widget_recent_empty)
            views.setTextViewText(R.id.list_empty, empty)
            views.setViewVisibility(R.id.list_empty, View.VISIBLE)
            // The whole widget is one tap target here, so its description carries the empty message.
            views.setContentDescription(android.R.id.background, emptyDescription(context))
            return views
        }
        views.setContentDescription(android.R.id.background, context.getString(R.string.widget_open_gallery_cd))
        data.take(WidgetTemplate.listRows(bucket)).forEachIndexed { i, (felt, entry) ->
            val row = ListRows.ROWS[i]
            views.setViewVisibility(row, View.VISIBLE)
            views.setTextViewText(ListRows.TITLES[i], entry.name)
            views.setTextViewText(
                ListRows.METAS[i],
                context.getString(R.string.widget_recent_row_meta, WidgetText.kind(context, entry.kind), WidgetText.at(context, felt.feltAtEpochMillis)),
            )
            views.setTextViewText(ListRows.TRAILING[i], context.getString(R.string.widget_recent_play))
            views.setContentDescription(row, rowDescription(context, felt, entry))
            views.setOnClickPendingIntent(row, WidgetIntents.feel(context, appWidgetId, i, entry.id))
        }
        return views
    }

    private fun emptyDescription(context: Context): String = WidgetText.sentences(
        context.getString(R.string.widget_recent_empty),
        context.getString(R.string.widget_open_gallery),
    )

    /** What a row shows, for TalkBack: its name, gallery section and when it was felt. */
    private fun rowDescription(context: Context, felt: RecentFeel, entry: FeelEntry): String = context.getString(
        R.string.widget_recent_row_cd,
        entry.name,
        WidgetText.kind(context, entry.kind),
        WidgetText.at(context, felt.feltAtEpochMillis),
    )

    override fun collapsed(context: Context, appWidgetId: Int, data: List<Pair<RecentFeel, FeelEntry>>): RemoteViews {
        val latest = data.firstOrNull()
        return if (latest == null) {
            summary(
                context,
                title = context.getString(R.string.widget_recent_name),
                subtitle = context.getString(R.string.widget_recent_empty),
                description = emptyDescription(context),
                tap = WidgetIntents.openGallery(context, appWidgetId),
            )
        } else {
            summary(
                context,
                title = latest.second.name,
                subtitle = context.getString(R.string.widget_recent_name),
                description = rowDescription(context, latest.first, latest.second),
                tap = WidgetIntents.feel(context, appWidgetId, 0, latest.second.id),
            )
        }
    }
}

/** The view ids of the list template's five fixed rows. */
internal object ListRows {
    val ROWS = intArrayOf(R.id.row0, R.id.row1, R.id.row2, R.id.row3, R.id.row4)
    val TITLES = intArrayOf(R.id.row0_title, R.id.row1_title, R.id.row2_title, R.id.row3_title, R.id.row4_title)
    val METAS = intArrayOf(R.id.row0_meta, R.id.row1_meta, R.id.row2_meta, R.id.row3_meta, R.id.row4_meta)
    val TRAILING = intArrayOf(R.id.row0_trailing, R.id.row1_trailing, R.id.row2_trailing, R.id.row3_trailing, R.id.row4_trailing)
}
