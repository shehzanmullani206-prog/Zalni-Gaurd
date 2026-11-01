package com.zalni.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.BlockListRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Automatically restores GuardForegroundService upon device boot or app update
 * if the master protection toggle was enabled.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repository = BlockListRepository(context)
                    val isEnabled = repository.isProtectionEnabledFlow.first()
                    if (isEnabled) {
                        ServiceController.startService(context)
                    }
                } catch (_: Exception) {
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
