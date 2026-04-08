package com.iloapps.nomaddashboard.feature.timetracking.runtime

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.iloapps.nomaddashboard.MainActivity
import com.iloapps.nomaddashboard.core.data.timetracking.TimeTrackingRepository
import com.iloapps.nomaddashboard.core.model.TimeTrackingRecord
import com.iloapps.nomaddashboard.core.model.isUnallocated
import dagger.hilt.android.AndroidEntryPoint
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TimeTrackingForegroundService : Service() {
    @Inject
    lateinit var timeTrackingRepository: TimeTrackingRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var commandHandler: TimeTrackingServiceCommandHandler

    private var isForeground = false
    private var updateJob: Job? = null
    private var activeCollectionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = NotificationManagerCompat.from(this)
        commandHandler = TimeTrackingServiceCommandHandler(timeTrackingRepository)
        createNotificationChannel()
        activeCollectionJob = serviceScope.launch {
            timeTrackingRepository.activeEntry.collect { session ->
                if (session == null) {
                    stopForegroundService()
                } else if (isForeground) {
                    publishNotification(session)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForegroundStarted()
        serviceScope.launch {
            when (commandHandler.handle(intent?.action)) {
                TimeTrackingServiceCommand.RefreshNotification -> {
                    val active = timeTrackingRepository.currentActiveEntry()
                    if (active == null) {
                        stopForegroundService()
                    } else {
                        publishNotification(active)
                    }
                }

                TimeTrackingServiceCommand.StopService -> stopForegroundService()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        updateJob?.cancel()
        activeCollectionJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureForegroundStarted() {
        if (isForeground) return
        val notification = TimeTrackingNotificationFormatter.placeholder(this)
        startForegroundCompat(notification)
        isForeground = true
    }

    @SuppressLint("MissingPermission")
    private fun publishNotification(session: TimeTrackingRecord) {
        if (hasNotificationPermission(this).not()) {
            stopForegroundService()
            return
        }

        val notification = TimeTrackingNotificationFormatter.notification(
            context = this,
            session = session,
            now = Instant.now(),
        )

        if (isForeground.not()) {
            startForegroundCompat(notification)
            isForeground = true
        } else {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }

        updateJob?.cancel()
        updateJob = serviceScope.launch {
            while (true) {
                delay(1_000)
                if (hasNotificationPermission(this@TimeTrackingForegroundService).not()) {
                    stopForegroundService()
                    break
                }
                notificationManager.notify(
                    NOTIFICATION_ID,
                    TimeTrackingNotificationFormatter.notification(
                        context = this@TimeTrackingForegroundService,
                        session = session,
                        now = Instant.now(),
                    ),
                )
            }
        }
    }

    private fun stopForegroundService() {
        updateJob?.cancel()
        updateJob = null
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            notificationManager.cancel(NOTIFICATION_ID)
            isForeground = false
        }
        stopSelf()
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Time Tracking",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Persistent notification for active project time tracking."
            },
        )
    }

    companion object {
        private const val ACTION_START_OR_RESUME = "com.iloapps.nomaddashboard.action.START_OR_RESUME_TIME_TRACKING"
        internal const val ACTION_STOP_TRACKING = "com.iloapps.nomaddashboard.action.STOP_TIME_TRACKING"
        internal const val CHANNEL_ID = "time_tracking"
        internal const val NOTIFICATION_ID = 4001

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TimeTrackingForegroundService::class.java).setAction(ACTION_START_OR_RESUME),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, TimeTrackingForegroundService::class.java).setAction(ACTION_STOP_TRACKING),
            )
        }

        fun hasNotificationPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED

        internal fun contentIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        internal fun stopIntent(context: Context): PendingIntent =
            PendingIntent.getService(
                context,
                1,
                Intent(context, TimeTrackingForegroundService::class.java).setAction(ACTION_STOP_TRACKING),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}

internal object TimeTrackingNotificationFormatter {
    fun placeholder(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, TimeTrackingForegroundService.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Resuming capture")
            .setContentText("Loading the active unallocated timer.")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(TimeTrackingForegroundService.contentIntent(context))
            .build()

    fun notification(
        context: Context,
        session: TimeTrackingRecord,
        now: Instant,
    ): android.app.Notification =
        NotificationCompat.Builder(context, TimeTrackingForegroundService.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title(session))
            .setContentText(body(session, now))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(TimeTrackingForegroundService.contentIntent(context))
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                TimeTrackingForegroundService.stopIntent(context),
            )
            .build()

    internal fun title(session: TimeTrackingRecord): String =
        if (session.entry.isUnallocated()) "Unallocated timer running" else "Tracking ${session.project.name}"

    internal fun body(
        session: TimeTrackingRecord,
        now: Instant,
    ): String {
        val startedAt = DateTimeFormatter.ofPattern("HH:mm")
            .format(session.entry.startAt.atZone(ZoneId.systemDefault()))
        val elapsed = Duration.between(session.entry.startAt, now)
        return "Started $startedAt · ${formatDuration(elapsed)} elapsed"
    }

    private fun formatDuration(duration: Duration): String {
        val totalSeconds = duration.seconds.coerceAtLeast(0)
        val hours = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }
}

internal enum class TimeTrackingServiceCommand {
    RefreshNotification,
    StopService,
}

internal class TimeTrackingServiceCommandHandler(
    private val repository: TimeTrackingRepository,
) {
    suspend fun handle(action: String?): TimeTrackingServiceCommand =
        when (action) {
            TimeTrackingForegroundService.ACTION_STOP_TRACKING -> {
                repository.stopTracking()
                TimeTrackingServiceCommand.StopService
            }

            else -> TimeTrackingServiceCommand.RefreshNotification
        }
}
