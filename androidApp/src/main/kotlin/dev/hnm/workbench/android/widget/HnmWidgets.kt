package dev.hnm.workbench.android.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The app side of its home-screen widgets: one background thread for widget work, a [refresh] that
 * pushes a redraw to the providers whose data changed, and the one scheduled redraw (the pattern of
 * the day at local midnight). Nothing polls: `updatePeriodMillis` is 0 on every widget.
 */
object HnmWidgets {

    /** Sent by the app to its own providers (explicitly) to redraw every instance. */
    const val ACTION_REFRESH = "dev.hnm.workbench.android.widget.action.REFRESH"

    val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "hnm-widgets").apply { isDaemon = true }
    }

    val ALL: List<Class<out AppWidgetProvider>> = listOf(
        EffectPadsWidget::class.java,
        PatternOfDayWidget::class.java,
        PatternShapeWidget::class.java,
        RecentlyFeltWidget::class.java,
        PhoneHapticsWidget::class.java,
        LibraryWidget::class.java,
    )

    /** Redraws every placed instance of [providers]; a no-op for providers with none on a home screen. */
    fun refresh(context: Context, providers: List<Class<out AppWidgetProvider>> = ALL) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app) ?: return
        for (provider in providers) {
            val ids = manager.getAppWidgetIds(ComponentName(app, provider))
            if (ids.isEmpty()) continue
            app.sendBroadcast(
                Intent(app, provider)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }
    }

    /**
     * Record that the user felt catalogue pattern [patternId] (from the gallery, a pad or a row) and
     * redraw the widgets that show it. Off the main thread; the file is a few lines.
     */
    fun recordFelt(context: Context, patternId: String) {
        val app = context.applicationContext
        executor.execute {
            runCatching { RecentlyFeltStore(app).record(patternId) }
            refresh(app, listOf(RecentlyFeltWidget::class.java, PatternShapeWidget::class.java))
        }
    }

    /** Today's local day number (days since 1970-01-01 in the phone's time zone). */
    fun localEpochDay(nowMillis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toEpochDay()

    /** The next local midnight, plus a few seconds so the redraw lands on the new day. */
    fun nextLocalMidnightMillis(nowMillis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().plusDays(1)
            .atStartOfDay(zone).plusSeconds(5).toInstant().toEpochMilli()

    /**
     * One inexact, non-waking alarm at the next local midnight that redraws the pattern of the day. It
     * needs no permission; the system may deliver it late (Doze, battery managers), which is why the
     * widget prints the date it is for.
     */
    fun scheduleNextDay(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { alarms.set(AlarmManager.RTC, nextLocalMidnightMillis(), dayTick(context)) }
    }

    fun cancelNextDay(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(dayTick(context))
    }

    private fun dayTick(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, PatternOfDayWidget::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
