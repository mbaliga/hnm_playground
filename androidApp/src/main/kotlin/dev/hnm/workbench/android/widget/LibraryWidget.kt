package dev.hnm.workbench.android.widget

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import dev.hnm.workbench.android.AndroidHaptics
import dev.hnm.workbench.android.R
import dev.hnm.workbench.core.library.FeelTestCatalog
import dev.hnm.workbench.core.widget.LibraryGlance
import dev.hnm.workbench.core.widget.LibraryGlances
import dev.hnm.workbench.core.widget.SizeBucket
import dev.hnm.workbench.core.widget.WidgetTemplate

/**
 * "Library at a glance" (template `list`): how many patterns the gallery offers, then how this phone
 * plays them (native primitives, predefined effects or an amplitude waveform, each counted by running
 * the renderer's own schedule against this phone's probe), then the gallery's sections by size.
 */
class LibraryWidget : HnmWidgetProvider<LibraryGlance>() {

    override val template = WidgetTemplate.LIST

    override fun load(context: Context): LibraryGlance =
        LibraryGlances.of(FeelTestCatalog.entries, AndroidHaptics.probe(AndroidHaptics.vibrator(context)))

    private fun header(context: Context, glance: LibraryGlance): String =
        context.resources.getQuantityString(R.plurals.widget_library_header, glance.total, glance.total)

    override fun views(context: Context, appWidgetId: Int, data: LibraryGlance, bucket: SizeBucket): RemoteViews {
        val layout = when (bucket) {
            SizeBucket.SMALL -> R.layout.widget_list_small
            SizeBucket.MEDIUM -> R.layout.widget_list_medium
            else -> R.layout.widget_list_large
        }
        val rows = data.byPath.map { (path, n) ->
            Triple(WidgetText.path(context, path), context.getString(R.string.widget_library_path_meta), n)
        } + data.byKind.map { (kind, n) ->
            Triple(WidgetText.kind(context, kind), context.getString(R.string.widget_library_kind_meta), n)
        }
        val gallery = WidgetIntents.openGallery(context, appWidgetId)
        val views = RemoteViews(context.packageName, layout)
        val header = header(context, data)
        views.setTextViewText(R.id.list_header, header)
        rows.take(WidgetTemplate.listRows(bucket)).forEachIndexed { i, (title, meta, count) ->
            views.setViewVisibility(ListRows.ROWS[i], View.VISIBLE)
            views.setTextViewText(ListRows.TITLES[i], title)
            views.setTextViewText(ListRows.METAS[i], meta)
            views.setTextViewText(ListRows.TRAILING[i], context.getString(R.string.widget_library_count, count))
            views.setContentDescription(ListRows.ROWS[i], context.getString(R.string.widget_library_row_cd, title, meta, count))
            views.setOnClickPendingIntent(ListRows.ROWS[i], gallery)
        }
        views.setContentDescription(android.R.id.background, context.getString(R.string.widget_library_cd, header))
        views.setOnClickPendingIntent(android.R.id.background, gallery)
        return views
    }

    override fun collapsed(context: Context, appWidgetId: Int, data: LibraryGlance): RemoteViews = summary(
        context,
        title = header(context, data),
        subtitle = context.getString(R.string.widget_open_gallery),
        description = context.getString(R.string.widget_library_cd, header(context, data)),
        tap = WidgetIntents.openGallery(context, appWidgetId),
    )
}
