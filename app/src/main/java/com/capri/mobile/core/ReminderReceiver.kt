package com.capri.mobile.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.capri.mobile.R
import com.capri.mobile.ui.MainActivity

/** Shows Capri's reminder as a heads-up notification and removes it from the schedule. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ReminderScheduler.ACTION_FIRE) return
        val id = intent.getLongExtra("id", -1L)
        val text = intent.getStringExtra("text")?.takeIf { it.isNotBlank() } ?: return
        if (id >= 0) ReminderStore(context).remove(id)
        notify(context, id, text)
    }

    private fun notify(context: Context, id: Long, text: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.reminder_channel),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alarms and reminders Capri promised to bring up."
                    enableVibration(true)
                }
            )
        }
        val open = PendingIntent.getActivity(
            context,
            20,
            Intent(context, MainActivity::class.java)
                .setAction(IntentRouter.ACTION_SHOW_CHAT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        val n = builder
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setWhen(System.currentTimeMillis())
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setColor(context.getColor(R.color.pink))
            .build()
        try {
            nm.notify(notificationId(id), n)
        } catch (_: Exception) {
            // POST_NOTIFICATIONS denied; the reminder still fired, the app will show it on next open.
        }
    }

    private fun notificationId(id: Long): Int = 20_000 + ((id % 10_000).toInt())

    private companion object {
        const val CHANNEL = "capri_reminders"
    }
}
