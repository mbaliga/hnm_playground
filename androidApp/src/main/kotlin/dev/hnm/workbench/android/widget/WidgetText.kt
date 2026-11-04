package dev.hnm.workbench.android.widget

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import dev.hnm.workbench.android.R
import dev.hnm.workbench.core.library.FeelKind
import dev.hnm.workbench.core.widget.PatternSketch
import dev.hnm.workbench.core.widget.RenderPath
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The words and numbers widgets print, from string resources and in the phone's locale. */
internal object WidgetText {

    fun number(value: Double): String = String.format(Locale.getDefault(), "%.2f", value)

    fun duration(context: Context, seconds: Double): String =
        context.getString(R.string.widget_duration_seconds, number(seconds))

    fun events(context: Context, count: Int): String =
        context.resources.getQuantityString(R.plurals.widget_events, count, count)

    fun path(context: Context, path: RenderPath): String = context.getString(
        when (path) {
            RenderPath.PRIMITIVES -> R.string.widget_path_primitives
            RenderPath.PREDEFINED -> R.string.widget_path_predefined
            RenderPath.WAVEFORM -> R.string.widget_path_waveform
            RenderPath.NOTHING -> R.string.widget_path_nothing
        },
    )

    fun kind(context: Context, kind: FeelKind): String = context.getString(
        when (kind) {
            FeelKind.REFERENCE -> R.string.widget_kind_reference
            FeelKind.BUZZ -> R.string.widget_kind_buzz
            FeelKind.VARIATION -> R.string.widget_kind_variation
            FeelKind.CAPTURED -> R.string.widget_kind_captured
            FeelKind.MOTION -> R.string.widget_kind_motion
            FeelKind.TEXTURE -> R.string.widget_kind_texture
            FeelKind.NAVIGATOR -> R.string.widget_kind_navigator
            FeelKind.MATERIAL -> R.string.widget_kind_material
        },
    )

    /** A short local date, e.g. "Wed 4 Nov" or "Wed, Nov 4", for the day a widget's content is for. */
    fun day(epochDay: Long): String {
        val locale = Locale.getDefault()
        val pattern = DateFormat.getBestDateTimePattern(locale, "EEEdMMM")
        return DateTimeFormatter.ofPattern(pattern, locale).format(LocalDate.ofEpochDay(epochDay))
    }

    /**
     * A date and clock time, e.g. "4 Nov, 14:02" (the year only when it isn't this one). Always with the
     * date: nothing redraws these widgets at midnight, so a bare time from yesterday would pass for today.
     */
    fun at(context: Context, millis: Long): String = DateUtils.formatDateTime(
        context,
        millis,
        DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH,
    )

    /** Joins spoken parts into sentences, ending each that has no closing punctuation with a full stop. */
    fun sentences(vararg parts: CharSequence?): String =
        parts.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
            .joinToString(" ") { if (it.last() in ".!?…") it else "$it." }

    fun sharpness(context: Context, sketch: PatternSketch): String {
        val lo = sketch.sharpnessMin ?: return "–"
        val hi = sketch.sharpnessMax ?: lo
        return if (hi - lo < 0.005) number(lo) else context.getString(R.string.widget_shape_sharpness_range, number(lo), number(hi))
    }

    /** The facts behind a shape: events, length, peak intensity and the sharpness range. */
    fun facts(context: Context, sketch: PatternSketch): String = context.getString(
        R.string.widget_shape_facts,
        events(context, sketch.eventCount),
        duration(context, sketch.durationSeconds),
        number(sketch.peakIntensity),
        sharpness(context, sketch),
    )

    /** Alt text for a drawn shape, carrying the numbers it shows. */
    fun shapeFacts(context: Context, name: String, sketch: PatternSketch): String = context.getString(
        R.string.widget_shape_facts_cd,
        name,
        events(context, sketch.eventCount),
        duration(context, sketch.durationSeconds),
        number(sketch.peakIntensity),
        sharpness(context, sketch),
    )

    /** [shapeFacts] plus what a tap on the shape does: open it in the workbench. */
    fun shapeDescription(context: Context, name: String, sketch: PatternSketch): String =
        sentences(shapeFacts(context, name, sketch), context.getString(R.string.widget_tap_open_workbench))
}
