package com.iloapps.nomaddashboard.feature.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Draws the widgets again from the saved snapshot every 30 minutes, so "Updated 12:40", the daylight
 * left and the out-of-date state stay honest while the app is closed. It reads only the saved file:
 * it fetches nothing, so it costs almost no battery or data.
 */
internal class WidgetRefreshWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        NomadWidgets.updateAll(applicationContext)
        return Result.success()
    }
}

internal object WidgetRefreshScheduler {
    const val UNIQUE_WORK_NAME = "nomad-widget-refresh"

    /** 30 minutes: the 15 minute minimum would only cost battery, the widgets show minutes. */
    const val INTERVAL_MINUTES = 30L

    /** Schedules the periodic redraw unless it is already scheduled. */
    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Stops the periodic redraw once the last widget has been removed. */
    fun cancelIfNoWidgets(context: Context) {
        if (!NomadWidgets.hasPlacedWidgets(context)) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}

/**
 * Draws the widgets again when the clock, the time zone or the language changes: a traveller who crosses
 * a border should see the "Updated" time and the sunset time in the new zone straight away.
 */
class WidgetEnvironmentChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HandledActions) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                NomadWidgets.updateAll(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HandledActions = setOf(Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED)
    }
}
