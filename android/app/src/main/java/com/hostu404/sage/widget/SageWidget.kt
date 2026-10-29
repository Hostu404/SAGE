package com.hostu404.sage.widget

import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.hostu404.sage.MainActivity
import com.hostu404.sage.R
import com.hostu404.sage.data.Forecast
import com.hostu404.sage.data.ForecastStore
import com.hostu404.sage.data.Prefs
import com.hostu404.sage.data.WhereYouAre
import com.hostu404.sage.data.Words
import com.hostu404.sage.ui.ON_THREAT
import com.hostu404.sage.ui.Sage
import com.hostu404.sage.ui.THREAT_COLORS
import com.hostu404.sage.ui.chanceColor
import com.hostu404.sage.ui.chanceOf
import com.hostu404.sage.ui.nextMonth
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/**
 * The home-screen widget: the global threat level, and where you are (or
 * the first country you follow). Drawn from the forecast already on the
 * phone, then a background job fetches a newer one when there's a network.
 * Android asks it to update every few hours (sage_widget_info.xml).
 */
class SageWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val done = goAsync()
        thread {
            try {
                runCatching { render(context, manager, ids) }
                runCatching { RefreshJob.schedule(context) }
            } finally {
                done.finish()
            }
        }
    }

    /** Before Android 12 a resized widget has to pick its own layout. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        if (Build.VERSION.SDK_INT >= 31) return
        val done = goAsync()
        thread {
            try {
                runCatching { render(context, manager, intArrayOf(id)) }
            } finally {
                done.finish()
            }
        }
    }

    /** The last SAGE widget was removed: nothing left to refresh for. */
    override fun onDisabled(context: Context) {
        context.getSystemService(JobScheduler::class.java)?.cancel(RefreshJob.ID)
    }

    companion object {
        /**
         * Redraws every SAGE widget from the forecast on the phone. Background
         * thread only. A widget that can't be drawn keeps what it showed.
         */
        fun updateAll(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids = manager.getAppWidgetIds(ComponentName(context, SageWidget::class.java))
                if (ids.isNotEmpty()) render(context, manager, ids)
            }
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val prefs = Prefs(context)
            val f = ForecastStore(context).load()
            val here = prefs.here ?: WhereYouAre.fromPhone(context)
            if (Build.VERSION.SDK_INT >= 31) {
                // The launcher picks whichever fits, and switches when the widget is resized.
                manager.updateAppWidget(ids, RemoteViews(mapOf(
                    SizeF(110f, 80f) to views(context, f, here, prefs.followed, compact = true),
                    SizeF(110f, FULL_HEIGHT) to views(context, f, here, prefs.followed),
                )))
            } else for (id in ids) {
                // Its height in portrait; 0 if the launcher doesn't say.
                val height = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                manager.updateAppWidget(id, views(context, f, here, prefs.followed, compact = height in 1 until FULL_HEIGHT.toInt()))
            }
        }

        /** How tall the widget must be, in dp, for the full layout; shorter ones get the small one. */
        const val FULL_HEIGHT = 180f

        /** The widget's contents for this forecast; `compact` for a short widget. */
        fun views(context: Context, f: Forecast, here: String?, followed: Set<Int>, compact: Boolean = false): RemoteViews {
            val v = RemoteViews(context.packageName, if (compact) R.layout.widget_sage_small else R.layout.widget_sage)
            if (!compact) v.setTextViewText(R.id.widget_date, Words.day(f.generated).uppercase())
            f.world?.let { w ->
                v.setTextViewText(R.id.widget_level, w.level.uppercase())
                v.setTextColor(R.id.widget_level, ON_THREAT[w.rank].toArgb())
                v.setInt(R.id.widget_level_bg, "setColorFilter", THREAT_COLORS[w.rank].toArgb())
                if (!compact) v.setTextViewText(R.id.widget_world,
                    "${w.expectedFighting.roundToInt()} countries expected to see deadly fighting in ${w.month?.let(Words::month) ?: nextMonth(f)}")
            }
            val place = here?.let { f.placeForIso2(it) } ?: followed.mapNotNull { f.place(it) }.minByOrNull { it.name }
            if (place != null) {
                val c = place.country
                val p = chanceOf(c, "fighting")
                v.setTextViewText(R.id.widget_place, place.name)
                v.setTextViewText(R.id.widget_place_pct, p?.let(Words::pctShort) ?: "–")
                v.setTextColor(R.id.widget_place_pct, (p?.let(::chanceColor) ?: Sage.faint).toArgb())
                if (c?.alert?.on == true) v.setViewVisibility(R.id.widget_alert, View.VISIBLE)
                if (!compact) v.setTextViewText(R.id.widget_place_line,
                    if (c != null) "Deadly fighting in ${nextMonth(f)} · ${c.status.lowercase()} last month" else "Relations only")
            } else {
                v.setTextViewText(R.id.widget_place, here?.let(WhereYouAre::name) ?: "Star a country in the app")
                v.setTextViewText(R.id.widget_place_pct, "")
                if (!compact) v.setTextViewText(R.id.widget_place_line, if (here != null) "No organised violence recorded since 1989" else "It shows here")
            }
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            v.setOnClickPendingIntent(R.id.widget_root, open)
            return v
        }
    }
}

/** Fetches a newer forecast for the widget once there's a network, then redraws it. */
class RefreshJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        thread {
            // Whatever goes wrong waits for the next try: never crash the app in the background.
            runCatching {
                ForecastStore(this).refresh()
                WhereYouAre.fromConnection()?.let { Prefs(this).here = it }
            }
            SageWidget.updateAll(this)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        const val ID = 7301

        fun schedule(context: Context) {
            val job = JobInfo.Builder(ID, ComponentName(context, RefreshJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build()
            context.getSystemService(JobScheduler::class.java)?.schedule(job)
        }
    }
}
