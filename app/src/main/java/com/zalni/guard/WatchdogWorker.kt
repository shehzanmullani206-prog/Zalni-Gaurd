package com.zalni.guard

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.BlockListRepository
import com.example.util.PermissionUtils
import kotlinx.coroutines.flow.first

/**
 * 15-minute periodic watchdog worker to monitor accessibility service health.
 * Runs in background without starting any foreground services.
 */
class WatchdogWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        try {
            val repository = BlockListRepository(applicationContext)
            val isProtectionEnabled = repository.isProtectionEnabledFlow.first()

            if (isProtectionEnabled) {
                val isAccessibilityActive =
                    PermissionUtils.isAccessibilityServiceEnabled(applicationContext)

                if (!isAccessibilityActive) {
                    // Alert user to tap and re-enable accessibility service
                    WatchdogNotifier.postWatchdogNotification(applicationContext)
                } else {
                    WatchdogNotifier.cancelWatchdogNotification(applicationContext)
                }
            }
        } catch (_: Exception) {
            return Result.retry()
        }

        return Result.success()
    }
}
