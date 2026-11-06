package com.capri.mobile.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Schedules Capri's reminders through AlarmManager, with a graceful inexact fallback on Android 12+. */
object ReminderScheduler {

    const val ACTION_FIRE = "com.capri.mobile.REMINDER_FIRE"

    fun schedule(context: Context, reminder: Reminder) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context, reminder)
        val now = System.currentTimeMillis()
        val at = if (reminder.at <= now + 1_000L) now + 5_000L else reminder.at
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: Exception) {
            // Last resort: nothing else we can do; the reminder stays in the DB for a later restore.
        }
    }

    fun cancel(context: Context, id: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            am.cancel(pending(context, Reminder(id, "", 0)))
        } catch (_: Exception) {
        }
    }

    fun cancelAll(context: Context) {
        ReminderStore(context).pending().forEach { cancel(context, it.id) }
    }

    /** Re-arms everything after a reboot or an app update. */
    fun restore(context: Context) {
        ReminderStore(context).pending().forEach { schedule(context, it) }
    }

    private fun pending(context: Context, reminder: Reminder): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra("id", reminder.id)
            putExtra("text", reminder.text)
        }
        return PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
