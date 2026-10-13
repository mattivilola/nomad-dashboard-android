package com.iloapps.nomaddashboard.core.data.insights

import android.content.Context
import android.text.format.DateFormat
import com.iloapps.nomaddashboard.core.travel.daylight.ClockTimeFormatter
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpFormats
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Duration

/**
 * The device settings the heads-up rules write text with. Read on every evaluation, so a changed
 * locale, 12/24-hour preference or time zone shows up within a minute without restarting the process.
 */
interface InsightsEnvironment {
    fun formats(): HeadsUpFormats

    fun zone(): ZoneId
}

@Singleton
class AndroidInsightsEnvironment @Inject constructor(
    @ApplicationContext private val context: Context,
) : InsightsEnvironment {
    private data class Key(val locale: Locale, val is24Hour: Boolean)

    private var cached: Pair<Key, HeadsUpFormats>? = null

    @Synchronized
    override fun formats(): HeadsUpFormats {
        val key = Key(Locale.getDefault(), DateFormat.is24HourFormat(context))
        cached?.takeIf { it.first == key }?.let { return it.second }
        val clock = if (key.is24Hour) {
            ClockTimeFormatter.Hour24
        } else {
            val twelveHour = DateTimeFormatter.ofPattern("h:mm a", key.locale)
            ClockTimeFormatter { twelveHour.format(it) }
        }
        return HeadsUpFormats(locale = key.locale, clockTime = clock).also { cached = key to it }
    }

    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/** Emits once straight away and then on every interval, so time-relative insights stay current. */
interface InsightsTicker {
    fun ticks(): Flow<Unit>
}

class PeriodicInsightsTicker(private val interval: Duration) : InsightsTicker {
    @Inject
    constructor() : this(DefaultInterval)

    override fun ticks(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(interval.toMillis())
        }
    }

    companion object {
        /** Time-relative items re-evaluate every 60 s. */
        val DefaultInterval: Duration = Duration.ofSeconds(60)
    }
}
