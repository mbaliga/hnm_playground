package dev.hnm.workbench.android.widget

import android.content.Context
import android.widget.RemoteViews
import dev.hnm.workbench.android.AndroidHaptics
import dev.hnm.workbench.android.R
import dev.hnm.workbench.core.ir.PrimitiveType
import dev.hnm.workbench.core.widget.HapticTier
import dev.hnm.workbench.core.widget.PhoneGlance
import dev.hnm.workbench.core.widget.PhoneGlances
import dev.hnm.workbench.core.widget.SizeBucket
import dev.hnm.workbench.core.widget.WidgetTemplate

/**
 * "This phone's haptics" (template `stat`): what the vibrator reports through the standard API, read
 * by the same probe the player uses: how many of the eight composition primitives it supports, its tier
 * in the player's own honest words, and (large) each primitive as a filled or hollow mark plus "yes" or
 * "no", with amplitude control and the predefined effects. No device model is shown.
 */
class PhoneHapticsWidget : HnmWidgetProvider<PhoneGlance>() {

    override val template = WidgetTemplate.STAT

    override fun load(context: Context): PhoneGlance {
        val vibrator = AndroidHaptics.vibrator(context)
        return PhoneGlances.of(AndroidHaptics.probe(vibrator), runCatching { AndroidHaptics.supportedEffects(vibrator) }.getOrDefault(emptyList()))
    }

    private fun value(context: Context, glance: PhoneGlance): String =
        if (glance.tier == HapticTier.NONE) {
            context.getString(R.string.widget_phone_value_none)
        } else {
            context.getString(R.string.widget_phone_value, glance.supportedPrimitives.size, glance.primitivesTotal)
        }

    private fun tier(context: Context, glance: PhoneGlance): String = context.getString(
        when (glance.tier) {
            HapticTier.PRIMITIVES -> R.string.widget_phone_tier_primitives
            HapticTier.AMPLITUDE -> R.string.widget_phone_tier_amplitude
            HapticTier.ON_OFF -> R.string.widget_phone_tier_on_off
            HapticTier.NONE -> R.string.widget_phone_tier_none
        },
    )

    private fun label(context: Context, glance: PhoneGlance): String =
        if (glance.tier == HapticTier.NONE) tier(context, glance) else context.getString(R.string.widget_phone_primitives)

    private fun footer(context: Context, glance: PhoneGlance): String = context.getString(
        R.string.widget_phone_caption,
        context.getString(if (glance.hasAmplitudeControl) R.string.widget_phone_amplitude_yes else R.string.widget_phone_amplitude_no),
        if (glance.supportedEffects.isEmpty()) {
            context.getString(R.string.widget_phone_effects_none)
        } else {
            context.getString(R.string.widget_phone_effects, glance.supportedEffects.joinToString(", "))
        },
    )

    private fun description(context: Context, glance: PhoneGlance): String = context.getString(
        R.string.widget_phone_cd,
        context.getString(R.string.widget_phone_value_label, value(context, glance), label(context, glance)),
        tier(context, glance),
        footer(context, glance),
    )

    /** The large layout also lists every primitive; TalkBack hears which ones are there and which aren't. */
    private fun largeDescription(context: Context, glance: PhoneGlance): String {
        val (yes, no) = glance.primitiveRows.partition { it.second }
        fun names(rows: List<Pair<PrimitiveType, Boolean>>) =
            rows.joinToString(", ") { it.first.name }.ifEmpty { context.getString(R.string.widget_none) }
        return WidgetText.sentences(
            context.getString(R.string.widget_phone_supported_cd, names(yes)),
            context.getString(R.string.widget_phone_unsupported_cd, names(no)),
            description(context, glance),
        )
    }

    override fun views(context: Context, appWidgetId: Int, data: PhoneGlance, bucket: SizeBucket): RemoteViews {
        val layout = when (bucket) {
            SizeBucket.SMALL -> R.layout.widget_stat_small
            SizeBucket.MEDIUM -> R.layout.widget_stat_medium
            else -> R.layout.widget_stat_large
        }
        return RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.stat_value, value(context, data))
            setTextViewText(R.id.stat_label, label(context, data))
            if (bucket != SizeBucket.SMALL) setTextViewText(R.id.stat_caption, tier(context, data))
            if (bucket == SizeBucket.LARGE) {
                data.primitiveRows.forEachIndexed { i, (primitive, supported) ->
                    val res = if (supported) R.string.widget_phone_primitive_yes else R.string.widget_phone_primitive_no
                    setTextViewText(DETAILS[i], context.getString(res, primitive.name))
                }
                setTextViewText(R.id.stat_footer, footer(context, data))
            }
            setContentDescription(
                android.R.id.background,
                if (bucket == SizeBucket.LARGE) largeDescription(context, data) else description(context, data),
            )
            setOnClickPendingIntent(android.R.id.background, WidgetIntents.openGallery(context, appWidgetId))
        }
    }

    override fun collapsed(context: Context, appWidgetId: Int, data: PhoneGlance): RemoteViews = summary(
        context,
        title = value(context, data),
        subtitle = label(context, data),
        description = description(context, data),
        tap = WidgetIntents.openGallery(context, appWidgetId),
    )

    private companion object {
        val DETAILS = intArrayOf(
            R.id.stat_detail0, R.id.stat_detail1, R.id.stat_detail2, R.id.stat_detail3,
            R.id.stat_detail4, R.id.stat_detail5, R.id.stat_detail6, R.id.stat_detail7,
        )
    }
}
