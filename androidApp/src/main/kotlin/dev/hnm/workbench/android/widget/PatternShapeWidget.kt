package dev.hnm.workbench.android.widget

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import dev.hnm.workbench.android.R
import dev.hnm.workbench.core.library.FeelEntry
import dev.hnm.workbench.core.library.FeelTestCatalog
import dev.hnm.workbench.core.widget.PatternSketch
import dev.hnm.workbench.core.widget.RecentFeel
import dev.hnm.workbench.core.widget.RecentlyFelt
import dev.hnm.workbench.core.widget.SizeBucket
import dev.hnm.workbench.core.widget.WidgetTemplate

/**
 * "Pattern shape" (template `image`): the intensity and sharpness of the pattern last felt, drawn by the
 * app as one picture whose alt text carries its numbers. A tap opens that pattern in the workbench.
 * Before anything has been felt it says so instead of drawing a stand-in.
 */
class PatternShapeWidget : HnmWidgetProvider<PatternShapeWidget.Data?>() {

    class Data(val felt: RecentFeel, val entry: FeelEntry, val sketch: PatternSketch, val shape: Bitmap)

    override val template = WidgetTemplate.IMAGE

    override fun load(context: Context): Data? {
        val (felt, entry) = RecentlyFelt.resolve(RecentlyFeltStore(context).read(), FeelTestCatalog::find)
            .firstOrNull() ?: return null
        val sketch = PatternSketch.of(entry.pattern)
        return Data(felt, entry, sketch, ShapeBitmap.forWidget(context, sketch))
    }

    override fun views(context: Context, appWidgetId: Int, data: Data?, bucket: SizeBucket): RemoteViews {
        val layout = when (bucket) {
            SizeBucket.SMALL -> R.layout.widget_image_small
            SizeBucket.MEDIUM -> R.layout.widget_image_medium
            else -> R.layout.widget_image_large
        }
        val views = RemoteViews(context.packageName, layout)
        if (data == null) {
            views.setViewVisibility(R.id.image_view, View.GONE)
            views.setViewVisibility(R.id.image_empty, View.VISIBLE)
            // The full sentence doesn't fit the small layout's one line or two; say it short there.
            if (bucket == SizeBucket.SMALL) views.setTextViewText(R.id.image_empty, context.getString(R.string.widget_shape_empty_short))
            if (bucket != SizeBucket.SMALL) views.setViewVisibility(R.id.image_legend, View.GONE)
            if (bucket == SizeBucket.LARGE) views.setViewVisibility(R.id.image_facts, View.GONE)
            views.setContentDescription(android.R.id.background, emptyDescription(context))
            views.setOnClickPendingIntent(android.R.id.background, WidgetIntents.openGallery(context, appWidgetId))
            return views
        }
        val name = data.entry.name
        val facts = WidgetText.shapeFacts(context, name, data.sketch)
        views.setImageViewBitmap(R.id.image_view, data.shape)
        views.setContentDescription(R.id.image_view, facts)
        if (bucket != SizeBucket.SMALL) {
            views.setTextViewText(R.id.image_title, context.getString(R.string.widget_shape_title, name))
        }
        var felt: String? = null
        if (bucket == SizeBucket.LARGE) {
            felt = context.getString(R.string.widget_shape_felt_at, WidgetText.at(context, data.felt.feltAtEpochMillis))
            views.setTextViewText(R.id.image_facts, context.getString(R.string.widget_two_parts, WidgetText.facts(context, data.sketch), felt))
        }
        // The widget is one tap target, so TalkBack reads only this: the shape's numbers, when, and the tap.
        val description = WidgetText.sentences(
            context.getString(R.string.widget_shape_label),
            facts,
            felt,
            context.getString(R.string.widget_tap_open_workbench),
        )
        views.setContentDescription(android.R.id.background, description)
        views.setOnClickPendingIntent(android.R.id.background, WidgetIntents.openPattern(context, appWidgetId, data.entry.id))
        return views
    }

    override fun collapsed(context: Context, appWidgetId: Int, data: Data?): RemoteViews =
        if (data == null) {
            summary(
                context,
                title = context.getString(R.string.widget_shape_name),
                subtitle = context.getString(R.string.widget_shape_empty_short),
                description = emptyDescription(context),
                tap = WidgetIntents.openGallery(context, appWidgetId),
            )
        } else {
            summary(
                context,
                title = data.entry.name,
                subtitle = context.getString(R.string.widget_shape_label),
                description = WidgetText.sentences(
                    context.getString(R.string.widget_shape_label),
                    WidgetText.shapeDescription(context, data.entry.name, data.sketch),
                ),
                tap = WidgetIntents.openPattern(context, appWidgetId, data.entry.id),
            )
        }

    private fun emptyDescription(context: Context): String = WidgetText.sentences(
        context.getString(R.string.widget_shape_empty),
        context.getString(R.string.widget_open_gallery),
    )
}
