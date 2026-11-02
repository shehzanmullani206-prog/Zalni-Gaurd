package com.zalni.guard

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Helper to start and stop GuardForegroundService and manage periodic watchdog scheduling.
 */
object ServiceController {

    fun startService(context: Context) {
        val intent = Intent(context, GuardForegroundService::class.java)
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (_: Exception) {
            // Catches any background execution / foreground start restrictions safely
        }
        WatchdogManager.schedule(context)
    }

    fun stopService(context: Context) {
        val intent = Intent(context, GuardForegroundService::class.java)
        try {
            context.stopService(intent)
        } catch (_: Exception) {}
        WatchdogManager.cancel(context)
        WatchdogNotifier.cancelWatchdogNotification(context)
    }
}
