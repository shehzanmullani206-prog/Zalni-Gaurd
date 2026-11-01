package com.example.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat

/**
 * Utility functions for checking and requesting system permissions for ZALNI Guard.
 */
object PermissionUtils {

    private const val ACCESSIBILITY_SERVICE_CLASS = "com.zalni.guard.GuardAccessibilityService"

    /**
     * Detects whether the accessibility service is active by reading
     * Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES without directly referencing the Phase 3 class.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)

        val targetWithPackage1 = "${context.packageName}/$ACCESSIBILITY_SERVICE_CLASS"
        val targetWithPackage2 = "com.zalni.guard/$ACCESSIBILITY_SERVICE_CLASS"

        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(targetWithPackage1, ignoreCase = true) ||
                componentName.equals(targetWithPackage2, ignoreCase = true) ||
                componentName.contains(ACCESSIBILITY_SERVICE_CLASS, ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if the app is exempt from battery optimizations.
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Checks notification permission (granted on Android 12 and below, checks POST_NOTIFICATIONS on 13+).
     */
    fun isNotificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun requestBatteryOptimizationExemption(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallback)
        }
    }
}
