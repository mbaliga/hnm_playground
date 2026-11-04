package dev.hnm.workbench.android.widget

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import dev.hnm.workbench.android.R
import dev.hnm.workbench.core.library.FeelEntry
import dev.hnm.workbench.core.widget.EffectPads
import dev.hnm.workbench.core.widget.PatternSketch
import dev.hnm.workbench.core.widget.SizeBucket
import dev.hnm.workbench.core.widget.WidgetTemplate

/**
 * "Effect pads" (template `pads`): two, four or six built-in patterns as buttons, each with its shape as
 * a text sparkline. A tap opens the translucent [dev.hnm.workbench.android.FeelActivity], because a
 * background app's touch-feedback vibration is dropped on Android 12+.
 */
class EffectPadsWidget : HnmWidgetProvider<List<EffectPadsWidget.Pad>>() {

    class Pad(val entry: FeelEntry, val glyph: String)

    override val template = WidgetTemplate.PADS

    override fun load(context: Context): List<Pad> =
        EffectPads.pads(SizeBucket.LARGE).map { Pad(it, PatternSketch.of(it.pattern, columns = 8).sparkline()) }

    override fun views(context: Context, appWidgetId: Int, data: List<Pad>, bucket: SizeBucket): RemoteViews {
        val layout = when (bucket) {
            SizeBucket.SMALL -> R.layout.widget_pads_small
            SizeBucket.MEDIUM -> R.layout.widget_pads_medium
            else -> R.layout.widget_pads_large
        }
        val views = RemoteViews(context.packageName, layout)
        val slots = EffectPads.padCount(bucket)
        for (i in 0 until slots) {
            val pad = data.getOrNull(i)
            if (pad == null) {
                views.setViewVisibility(PADS[i], View.INVISIBLE)
                continue
            }
            views.setTextViewText(GLYPHS[i], pad.glyph)
            views.setTextViewText(LABELS[i], pad.entry.name)
            views.setContentDescription(PADS[i], context.getString(R.string.widget_pad_cd, pad.entry.name))
            views.setOnClickPendingIntent(PADS[i], WidgetIntents.feel(context, appWidgetId, i, pad.entry.id))
        }
        return views
    }

    override fun collapsed(context: Context, appWidgetId: Int, data: List<Pad>): RemoteViews = summary(
        context,
        title = context.getString(R.string.widget_pads_name),
        subtitle = context.getString(R.string.widget_open_gallery),
        description = context.getString(R.string.widget_open_gallery_cd),
        tap = WidgetIntents.openGallery(context, appWidgetId),
    )

    private companion object {
        val PADS = intArrayOf(R.id.pad0, R.id.pad1, R.id.pad2, R.id.pad3, R.id.pad4, R.id.pad5)
        val GLYPHS = intArrayOf(R.id.pad0_glyph, R.id.pad1_glyph, R.id.pad2_glyph, R.id.pad3_glyph, R.id.pad4_glyph, R.id.pad5_glyph)
        val LABELS = intArrayOf(R.id.pad0_label, R.id.pad1_label, R.id.pad2_label, R.id.pad3_label, R.id.pad4_label, R.id.pad5_label)
    }
}
