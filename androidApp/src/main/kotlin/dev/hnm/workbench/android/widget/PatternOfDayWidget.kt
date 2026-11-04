package dev.hnm.workbench.android.widget

import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import dev.hnm.workbench.android.AndroidHaptics
import dev.hnm.workbench.android.R
import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.library.FeelEntry
import dev.hnm.workbench.core.widget.PatternOfTheDay
import dev.hnm.workbench.core.widget.PatternSketch
import dev.hnm.workbench.core.widget.RenderPath
import dev.hnm.workbench.core.widget.RenderPaths
import dev.hnm.workbench.core.widget.SizeBucket
import dev.hnm.workbench.core.widget.WidgetTemplate

/**
 * "Pattern of the day" (template `card`): one library pattern per local day, its description, how this
 * phone will play it, its shape, and buttons to feel it or open it in the workbench. It redraws at local
 * midnight (an inexact alarm) and prints the date it is for at every size, so a late redraw is visible
 * as such.
 */
class PatternOfDayWidget : HnmWidgetProvider<PatternOfDayWidget.Data>() {

    class Data(
        val entry: FeelEntry,
        val date: String,
        val sketch: PatternSketch,
        val path: RenderPath,
        val shape: Bitmap,
    )

    override val template = WidgetTemplate.CARD

    override fun load(context: Context): Data {
        val day = HnmWidgets.localEpochDay()
        val entry = checkNotNull(PatternOfTheDay.pick(day)) { "the catalogue has no pattern of the day" }
        val capabilities = AndroidHaptics.probe(AndroidHaptics.vibrator(context))
        val path = RenderPaths.of(entry.pattern, DefaultPatternRenderer().scheduleHaptics(entry.pattern, capabilities))
        val sketch = PatternSketch.of(entry.pattern)
        return Data(entry, WidgetText.day(day), sketch, path, ShapeBitmap.forWidget(context, sketch))
    }

    override fun afterRender(context: Context) = HnmWidgets.scheduleNextDay(context)

    override fun onDisabled(context: Context) {
        HnmWidgets.cancelNextDay(context)
        super.onDisabled(context)
    }

    override fun views(context: Context, appWidgetId: Int, data: Data, bucket: SizeBucket): RemoteViews {
        val name = data.entry.name
        val feel = WidgetIntents.feel(context, appWidgetId, WidgetIntents.SLOT_FEEL_MAIN, data.entry.id)
        if (bucket == SizeBucket.SMALL) {
            return RemoteViews(context.packageName, R.layout.widget_card_small).apply {
                // The dated label doesn't fit this width; the date alone does, and it is what shows a late redraw.
                setTextViewText(R.id.card_label, data.date)
                setTextViewText(R.id.card_title, name)
                setContentDescription(android.R.id.background, context.getString(R.string.widget_daily_small_cd, data.date, name))
                setOnClickPendingIntent(android.R.id.background, feel)
            }
        }
        val layout = if (bucket == SizeBucket.MEDIUM) R.layout.widget_card_medium else R.layout.widget_card_large
        val large = bucket == SizeBucket.LARGE
        val facts = if (large) WidgetText.shapeFacts(context, name, data.sketch) else null
        val meta = if (large) {
            context.getString(
                R.string.widget_daily_meta,
                WidgetText.path(context, data.path),
                WidgetText.events(context, data.sketch.eventCount),
                WidgetText.duration(context, data.sketch.durationSeconds),
            )
        } else {
            null
        }
        // The card is one tap target, so TalkBack reads only its description: it carries what the card shows.
        val description = WidgetText.sentences(
            context.getString(R.string.widget_daily_card_cd, data.date, name),
            data.entry.description,
            facts,
            meta,
            context.getString(R.string.widget_tap_open_workbench),
        )
        return RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.card_label, context.getString(R.string.widget_daily_label_dated, data.date))
            setTextViewText(R.id.card_title, name)
            setTextViewText(R.id.card_body, data.entry.description)
            setContentDescription(android.R.id.background, description)
            setOnClickPendingIntent(android.R.id.background, WidgetIntents.openPattern(context, appWidgetId, data.entry.id))
            setContentDescription(R.id.card_feel, context.getString(R.string.widget_daily_feel_cd, name))
            setOnClickPendingIntent(R.id.card_feel, feel)
            if (large) {
                setImageViewBitmap(R.id.card_image, data.shape)
                setContentDescription(R.id.card_image, facts.orEmpty())
                setTextViewText(R.id.card_meta, meta.orEmpty())
                setContentDescription(R.id.card_open, context.getString(R.string.widget_daily_open_cd, name))
                setOnClickPendingIntent(R.id.card_open, WidgetIntents.openPattern(context, appWidgetId, data.entry.id))
            }
        }
    }

    override fun collapsed(context: Context, appWidgetId: Int, data: Data): RemoteViews = summary(
        context,
        title = data.entry.name,
        // The date it is for, not the bare label, so a late midnight redraw shows at this size too.
        subtitle = data.date,
        description = context.getString(R.string.widget_daily_small_cd, data.date, data.entry.name),
        tap = WidgetIntents.feel(context, appWidgetId, WidgetIntents.SLOT_FEEL_MAIN, data.entry.id),
    )
}
