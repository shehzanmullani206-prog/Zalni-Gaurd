package com.example.util

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.UserManager
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.zalni.guard.GuardDeviceAdminReceiver

/**
 * Utility functions for checking and requesting system permissions and device admin states.
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

    /**
     * Checks if Device Admin privilege is active.
     */
    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        val adminComponent = ComponentName(context, GuardDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(adminComponent)
    }

    /**
     * Checks if the app is provisioned as Device Owner (optional enterprise MDM mode).
     */
    fun isDeviceOwnerActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    /**
     * Applies Device Owner enterprise policies:
     * - Blocks uninstallation via setUninstallBlocked
     * - Restricts app management via DISALLOW_APPS_CONTROL
     * - Prevents bypassing protection in Safe Mode via DISALLOW_SAFE_BOOT
     * - Prevents factory reset wipe via DISALLOW_FACTORY_RESET
     */
    fun applyDeviceOwnerRestrictions(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        val admin = ComponentName(context, GuardDeviceAdminReceiver::class.java)
        if (!dpm.isDeviceOwnerApp(context.packageName)) return false

        return try {
            dpm.setUninstallBlocked(admin, context.packageName, true)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Releases Device Owner restrictions and clears device owner privileges.
     * Must be gated behind password confirmation.
     */
    fun releaseDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        val admin = ComponentName(context, GuardDeviceAdminReceiver::class.java)
        if (!dpm.isDeviceOwnerApp(context.packageName)) return false

        return try {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
            dpm.setUninstallBlocked(admin, context.packageName, false)
            dpm.clearDeviceOwnerApp(context.packageName)
            true
        } catch (_: Exception) {
            false
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

    fun requestDeviceAdmin(context: Context) {
        val adminComponent = ComponentName(context, GuardDeviceAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Enables legitimate device administration to prevent unauthorized uninstallation of ZALNI Guard."
            )
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
