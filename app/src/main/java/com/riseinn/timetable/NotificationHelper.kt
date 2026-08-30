package com.riseinn.timetable

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.riseinn.timetable.data.TimetableRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.Calendar

object NotificationHelper {
    private const val CHANNEL_ID = "timetable_daily_notifications"
    private const val CHANNEL_NAME = "Daily Timetable"
    const val ACTION_SHOW_NOTIFICATION = "com.riseinn.timetable.ACTION_SHOW_NOTIFICATION"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily timetable schedule notifications"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showUpdateNotification(context: Context, batch: String) {
        createNotificationChannel(context)

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 
            1001, 
            mainIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Timetable Updated")
            .setContentText("The schedule for your batch has been updated.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify("update_$batch".hashCode(), builder.build())
    }

    fun scheduleDailyNotifications(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // The exact hours requested by user: 19:00, 20:00, 21:00, 22:00, 23:00, 00:00
        val targetHours = listOf(19, 20, 21, 22, 23, 0)

        for (hour in targetHours) {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                
                // If the time has already passed today, schedule for tomorrow
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_SHOW_NOTIFICATION
                putExtra("hour", hour)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                hour, // use hour as unique request code
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Can schedule exact alarm on Android 12+ if permission is granted, but for broader compatibility
            // and avoiding SecurityExceptions if SCHEDULE_EXACT_ALARM is denied, we use setExactAndAllowWhileIdle safely.
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                    } else {
                        // Fallback to inexact if permission denied
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == NotificationHelper.ACTION_SHOW_NOTIFICATION) {
            
            // Immediately reschedule all alarms so it loops forever
            NotificationHelper.scheduleDailyNotifications(context)

            runBlocking(Dispatchers.IO) {
                val savedBatch = TimetableRepository.getSavedBatch(context) ?: return@runBlocking
                val batchInfo = com.riseinn.timetable.data.LookupData.batches.find { it.name.equals(savedBatch, ignoreCase = true) } ?: return@runBlocking
                
                val rawEntries = com.riseinn.timetable.data.NewTimetableLogic.getCachedSchedule(context)
                
                val calendar = Calendar.getInstance()
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                
                // If it's evening, we display Tomorrow's schedule. If it's midnight (hour 0), we display Today's schedule.
                val isTomorrow = hour >= 18
                if (isTomorrow) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
                
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                val apiDayOfWeek = if (dayOfWeek == Calendar.SUNDAY) 7 else dayOfWeek - 1
                
                val dateSuffix = if (isTomorrow) "(Tomorrow)" else "(Today)"
                val dateStr = java.text.SimpleDateFormat("dd MMM $dateSuffix", java.util.Locale.getDefault()).format(calendar.time)

                val dailyCards = com.riseinn.timetable.data.NewTimetableLogic.getDailySchedule(rawEntries, batchInfo.uuid, apiDayOfWeek)

                val notificationText = if (dailyCards.isEmpty()) {
                    "Holiday! ☕"
                } else {
                    val firstRoom = dailyCards.firstOrNull()?.room
                    val scheduleStr = dailyCards.joinToString("\n") { card -> 
                        "${card.timeLabel.replace("\n", " ")}: ${card.subject}" 
                    }
                    
                    if (firstRoom != null && firstRoom != "TBD") {
                        "🏫 ${firstRoom.uppercase()}\n$scheduleStr"
                    } else {
                        scheduleStr
                    }
                }

                NotificationHelper.createNotificationChannel(context)

                // The intent that opens the app when clicking the notification
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, 
                    0, 
                    mainIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val builder = NotificationCompat.Builder(context, "timetable_daily_notifications")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("📅 $savedBatch Schedule $dateStr")
                    .setContentText(notificationText.replace("\n", " | ")) // single line summary
                    .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(savedBatch.hashCode(), builder.build())
            }
        }
    }
}
