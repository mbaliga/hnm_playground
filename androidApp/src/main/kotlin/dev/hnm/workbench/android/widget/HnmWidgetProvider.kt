package dev.hnm.workbench.android.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import dev.hnm.workbench.android.R
import dev.hnm.workbench.core.widget.SizeBucket
import dev.hnm.workbench.core.widget.WidgetTemplate

/**
 * Shared plumbing for the app's widgets. Data is loaded once per update on [HnmWidgets.executor]
 * (inside `goAsync`, so the receiver stays alive), then each instance gets one size-mapped
 * [RemoteViews]: the launcher picks the collapsed, small, medium or large layout for the size it
 * shows without waking the app. A failure draws the error state in words instead of a blank tile.
 */
abstract class HnmWidgetProvider<T> : AppWidgetProvider() {

    protected abstract val template: WidgetTemplate

    /** Reads the app's data for this widget. Runs on a background thread. */
    protected abstract fun load(context: Context): T

    /** The layout for one size; [bucket] is never [SizeBucket.COLLAPSED]. */
    protected abstract fun views(context: Context, appWidgetId: Int, data: T, bucket: SizeBucket): RemoteViews

    /** The one-tap summary drawn below the template's smallest layout. */
    protected abstract fun collapsed(context: Context, appWidgetId: Int, data: T): RemoteViews

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        renderAsync(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            HnmWidgets.ACTION_REFRESH,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            -> {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids = manager.getAppWidgetIds(ComponentName(context, javaClass))
                if (ids.isNotEmpty()) renderAsync(context, ids)
            }
            else -> super.onReceive(context, intent)
        }
    }

    private fun renderAsync(context: Context, appWidgetIds: IntArray) {
        val app = context.applicationContext
        val pending = goAsync()
        HnmWidgets.executor.execute {
            try {
                val manager = AppWidgetManager.getInstance(app)
                val data = runCatching { load(app) }
                    .onFailure { Log.w(TAG, "${javaClass.simpleName}: could not read widget data", it) }
                for (id in appWidgetIds) {
                    val views = data.mapCatching { sized(app, id, it) }
                        .onFailure { Log.w(TAG, "${javaClass.simpleName}: could not draw widget", it) }
                        .getOrElse { error(app, id) }
                    // An update can still be refused (e.g. too large); fall back to the error state.
                    runCatching { manager.updateAppWidget(id, views) }
                        .onFailure {
                            Log.w(TAG, "${javaClass.simpleName}: launcher refused the update", it)
                            runCatching { manager.updateAppWidget(id, error(app, id)) }
                        }
                }
                runCatching { afterRender(app) }
            } finally {
                pending.finish()
            }
        }
    }

    /** Hook for scheduling the next boundary redraw after an update. */
    protected open fun afterRender(context: Context) = Unit

    private fun sized(context: Context, appWidgetId: Int, data: T): RemoteViews =
        RemoteViews(
            SizeBucket.entries.associate { bucket ->
                val size = template.sizeOf(bucket)
                SizeF(size.width.toFloat(), size.height.toFloat()) to
                    if (bucket == SizeBucket.COLLAPSED) collapsed(context, appWidgetId, data)
                    else views(context, appWidgetId, data, bucket)
            },
        )

    private fun error(context: Context, appWidgetId: Int): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_collapsed).apply {
            setTextViewText(R.id.collapsed_title, context.getString(R.string.widget_error_title))
            setTextViewText(R.id.collapsed_subtitle, context.getString(R.string.widget_error_subtitle))
            setContentDescription(android.R.id.background, context.getString(R.string.widget_open_app_cd))
            setOnClickPendingIntent(android.R.id.background, WidgetIntents.openWorkbench(context, appWidgetId))
        }

    /** The collapsed layout with a title, a subtitle and one tap target covering the whole widget. */
    protected fun summary(
        context: Context,
        title: CharSequence,
        subtitle: CharSequence,
        description: CharSequence,
        tap: android.app.PendingIntent,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_collapsed).apply {
        setTextViewText(R.id.collapsed_title, title)
        setTextViewText(R.id.collapsed_subtitle, subtitle)
        setContentDescription(android.R.id.background, description)
        setOnClickPendingIntent(android.R.id.background, tap)
    }

    private companion object {
        const val TAG = "HnmWidgets"
    }
}
