package com.zalni.guard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

enum class DeviceMaker(val displayName: String) {
    XIAOMI("Xiaomi / Redmi / Poco"),
    TRANSSION("Tecno / Infinix / itel"),
    OPPO("Oppo / Realme / OnePlus"),
    GENERIC("Android Device")
}

data class SetupStep(
    val id: String,
    val title: String,
    val description: String,
    val intents: List<Intent>
)

/**
 * Detects device manufacturer and provides tailored setup guidance and intent launchers
 * for aggressive task killers on Transsion, Xiaomi, and Oppo devices.
 */
object MakerSetupHelper {

    fun detectMaker(): DeviceMaker {
        val mfg = (Build.MANUFACTURER ?: "").lowercase()
        val brand = (Build.BRAND ?: "").lowercase()

        return when {
            mfg.contains("xiaomi") || mfg.contains("redmi") || mfg.contains("poco") ||
            brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") -> DeviceMaker.XIAOMI

            mfg.contains("tecno") || mfg.contains("infinix") || mfg.contains("itel") || mfg.contains("transsion") ||
            brand.contains("tecno") || brand.contains("infinix") || brand.contains("itel") || brand.contains("transsion") -> DeviceMaker.TRANSSION

            mfg.contains("oppo") || mfg.contains("realme") || mfg.contains("oneplus") ||
            brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus") -> DeviceMaker.OPPO

            else -> DeviceMaker.GENERIC
        }
    }

    fun getStepsForMaker(context: Context, maker: DeviceMaker): List<SetupStep> {
        val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val steps = mutableListOf<SetupStep>()

        // Step 1: Autostart ON
        val autostartIntents = when (maker) {
            DeviceMaker.XIAOMI -> listOf(
                Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
                appDetailsIntent
            )
            DeviceMaker.TRANSSION -> listOf(
                Intent().setClassName("com.transsion.phonemanager", "com.transsion.phonemanager.autostart.AutoStartManageActivity"),
                Intent().setClassName("com.transsion.phonemanager", "com.transsion.phonemanager.MainActivity"),
                appDetailsIntent
            )
            DeviceMaker.OPPO -> listOf(
                Intent().setClassName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                Intent().setClassName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                Intent().setClassName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                appDetailsIntent
            )
            DeviceMaker.GENERIC -> listOf(
                appDetailsIntent
            )
        }.map { it.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK } }

        steps.add(
            SetupStep(
                id = "step_autostart",
                title = "1. Enable Auto-Start",
                description = "Allow ZALNI Guard to launch automatically in the background when the phone boots or memory recovers.",
                intents = autostartIntents
            )
        )

        // Step 2: Battery set to "No restrictions"
        val batteryIntents = when (maker) {
            DeviceMaker.XIAOMI -> listOf(
                Intent().setClassName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") },
                appDetailsIntent
            )
            DeviceMaker.TRANSSION -> listOf(
                Intent().setClassName("com.transsion.phonemanager", "com.transsion.phonemanager.battery.BatterySettingsActivity"),
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") },
                appDetailsIntent
            )
            DeviceMaker.OPPO -> listOf(
                Intent().setClassName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"),
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") },
                appDetailsIntent
            )
            DeviceMaker.GENERIC -> listOf(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") },
                appDetailsIntent
            )
        }.map { it.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK } }

        steps.add(
            SetupStep(
                id = "step_battery",
                title = "2. Set Battery to 'No Restrictions'",
                description = "Prevent the system's aggressive task killer from terminating the protection service.",
                intents = batteryIntents
            )
        )

        // Step 3: Lock App in Recents
        steps.add(
            SetupStep(
                id = "step_recents_lock",
                title = "3. Lock App in Recents Carousel",
                description = "Open the phone's Recent Apps screen, long-press or pull down on ZALNI Guard, and tap the Lock icon so it won't be cleared when pressing 'Clear All'.",
                intents = listOf(appDetailsIntent)
            )
        )

        // Step 4: Allow Restricted Settings (Android 13+ only)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            steps.add(
                SetupStep(
                    id = "step_restricted_settings",
                    title = "4. Allow Restricted Settings",
                    description = "On Android 13+, open App Info, tap the 3 dots in the top-right corner, and select 'Allow restricted settings' to grant accessibility permission.",
                    intents = listOf(appDetailsIntent)
                )
            )
        }

        return steps
    }

    /**
     * Tries each candidate intent in order, checking resolveActivity and catching all exceptions.
     */
    fun openStepIntent(context: Context, step: SetupStep) {
        val pm = context.packageManager
        for (candidate in step.intents) {
            try {
                if (candidate.resolveActivity(pm) != null) {
                    context.startActivity(candidate)
                    return
                }
            } catch (_: Exception) {}
        }
        // Fallback to application details settings
        try {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallback)
        } catch (_: Exception) {}
    }
}
