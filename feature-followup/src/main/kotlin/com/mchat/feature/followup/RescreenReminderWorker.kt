package com.mchat.feature.followup

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local rescreening reminder worker.
 * PRIVACY GUARANTEE: Notification title and body NEVER contain patient names, IDs, scores, or PHI.
 */
@HiltWorker
class RescreenReminderWorker @AssistedInject constructor(
  @Assisted private val appContext: Context,
  @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

  companion object {
    const val CHANNEL_ID = "mchat_rescreen_reminders"
    const val KEY_NOTIFICATION_ID = "notification_id"
  }

  override suspend fun doWork(): Result {
    ensureChannel()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted = ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.POST_NOTIFICATIONS,
      ) == PackageManager.PERMISSION_GRANTED
      if (!granted) return Result.success()
    }

    val notificationId = inputData.getInt(KEY_NOTIFICATION_ID, 2400)
    val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_popup_reminder)
      .setContentTitle("M-CHAT-R/F Rescreening Reminder")
      .setContentText("A scheduled 24-month follow-up rescreening is due. Unlock the app to review.")
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .setAutoCancel(true)
      .build()

    NotificationManagerCompat.from(appContext).notify(notificationId, notification)
    return Result.success()
  }

  private fun ensureChannel() {
    val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (manager.getNotificationChannel(CHANNEL_ID) == null) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Clinical Rescreening Reminders",
        NotificationManager.IMPORTANCE_DEFAULT,
      ).apply {
        description = "Privacy-safe local reminders for scheduled M-CHAT-R/F rescreenings (no patient names)."
        lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
      }
      manager.createNotificationChannel(channel)
    }
  }
}

@Singleton
class RescreenReminderScheduler @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  /**
   * Schedules a privacy-safe local notification for [dueDateIso] (YYYY-MM-DD at 09:00 local time).
   * Strictly stores no patient name or clinical result in WorkManager inputData.
   */
  fun scheduleRescreenReminder(screeningId: String, dueDateIso: String) {
    val targetDate = runCatching { LocalDate.parse(dueDateIso) }.getOrNull() ?: return
    val targetTime = targetDate.atTime(9, 0)
    val now = LocalDateTime.now()
    val delayMillis = Duration.between(now, targetTime).toMillis().coerceAtLeast(5_000L)

    val request = OneTimeWorkRequestBuilder<RescreenReminderWorker>()
      .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
      .setInputData(workDataOf(RescreenReminderWorker.KEY_NOTIFICATION_ID to screeningId.hashCode()))
      .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
      "rescreen_reminder_${screeningId.hashCode()}",
      ExistingWorkPolicy.REPLACE,
      request,
    )
  }
}
