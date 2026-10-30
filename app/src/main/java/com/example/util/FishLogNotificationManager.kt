package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.R
import java.util.concurrent.TimeUnit

object FishLogNotificationManager {
    const val CHANNEL_REMINDERS = "fishlog_reminders_channel"
    const val CHANNEL_MILESTONES = "fishlog_milestones_channel"

    private const val WORK_JOURNAL_REMINDER = "fishlog_periodic_journal_reminder"
    private const val WORK_SESSION_REMINDER = "fishlog_periodic_session_reminder"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                "Fishing & Journal Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Scheduled reminders to log catches and plan fishing sessions"
            }

            val milestoneChannel = NotificationChannel(
                CHANNEL_MILESTONES,
                "Personal Fishing Milestones",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Celebrations when you set a new personal record or catch milestone"
            }

            manager.createNotificationChannel(reminderChannel)
            manager.createNotificationChannel(milestoneChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun updateJournalReminderSchedule(context: Context, enabled: Boolean) {
        ensureChannels(context)
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<FishingReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(12, TimeUnit.HOURS)
                .setInputData(
                    workDataOf(
                        "title" to "FishLog Journal Check-In",
                        "body" to "Been on the water recently? Record your session conditions and catches while they're fresh."
                    )
                )
                .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_JOURNAL_REMINDER,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } else {
            workManager.cancelUniqueWork(WORK_JOURNAL_REMINDER)
        }
    }

    fun updateSessionReminderSchedule(context: Context, enabled: Boolean) {
        ensureChannels(context)
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<FishingReminderWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(2, TimeUnit.DAYS)
                .setInputData(
                    workDataOf(
                        "title" to "Prime Weekend Waters Await",
                        "body" to "Check barometric pressure and your saved spots in FishLog before heading out."
                    )
                )
                .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_SESSION_REMINDER,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } else {
            workManager.cancelUniqueWork(WORK_SESSION_REMINDER)
        }
    }

    fun sendMilestoneNotification(context: Context, title: String, message: String) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_MILESTONES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(
                (System.currentTimeMillis() % 100000).toInt(),
                notification
            )
        }
    }
}

class FishingReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!FishLogNotificationManager.hasNotificationPermission(applicationContext)) {
            return Result.success()
        }
        FishLogNotificationManager.ensureChannels(applicationContext)
        val title = inputData.getString("title") ?: "FishLog Reminder"
        val body = inputData.getString("body") ?: "Ready for your next cast? Open FishLog to track your session."

        val notification = NotificationCompat.Builder(applicationContext, FishLogNotificationManager.CHANNEL_REMINDERS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(id.hashCode(), notification)
        }
        return Result.success()
    }
}
