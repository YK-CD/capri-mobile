package com.capri.mobile.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.capri.mobile.core.ReminderScheduler

/** Brings the floating character back after a reboot, but only if the user left her switched on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        // Alarms do not survive a reboot, so re-arm Capri's reminders no matter how the overlay is set.
        try {
            ReminderScheduler.restore(context)
        } catch (_: Exception) {
            // Reminders stay in the DB and fire on the next restore/update.
        }

        val prefs = context.getSharedPreferences("capri", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("overlay_enabled", false)) return
        if (!Settings.canDrawOverlays(context)) return
        try {
            OverlayService.start(context)
        } catch (_: Exception) {
            // Boot restrictions differ by OEM; the user can still start her from the app.
        }
    }
}
