package com.zalni.guard

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * Constants and package classification helpers for Settings & Anti-Tamper Guard.
 * Watched packages, danger keywords (English and Hindi), and launcher package detection.
 */
object SettingsGuardConstants {

    /**
     * Settings and device manager packages watched for tampering attempts.
     */
    val SETTINGS_PACKAGES: Set<String> = setOf(
        "com.android.settings",
        "com.google.android.permissioncontroller",
        "com.android.systemui",
        "com.miui.securitycenter",
        "com.coloros.safecenter",
        "com.oplus.phonemanager",
        "com.oppo.safe",
        "com.coloros.oppoguardelf",
        "com.transsion.phonemaster",
        "com.transsion.phonemanager"
    )

    /**
     * Package installer packages where detecting the app label alone triggers protection.
     */
    val INSTALLER_PACKAGES: Set<String> = setOf(
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.miui.packageinstaller"
    )

    /**
     * All statically defined watched packages.
     */
    val ALL_WATCHED_PACKAGES: Set<String> = SETTINGS_PACKAGES + INSTALLER_PACKAGES

    /**
     * Danger keywords in English and Hindi for settings and app-info screens.
     */
    val DANGER_KEYWORDS: List<String> = listOf(
        // English
        "force stop",
        "uninstall",
        "clear data",
        "clear storage",
        "manage space",
        "deactivate",
        "turn off",
        "stop",
        "disable",
        "app info",
        // Hindi equivalents
        "फ़ोर्स स्टॉप",
        "ज़बरदस्ती रोकें",
        "बलपूर्वक रोकें",
        "रोकें",
        "रोकना",
        "अनइंस्टॉल",
        "हटाएं",
        "हटाएँ",
        "डेटा साफ़ करें",
        "डेटा साफ़ करे",
        "डेटा हटाएं",
        "डेटा हटाएँ",
        "डेटा मिटाएं",
        "स्टोरेज साफ़ करें",
        "स्टोरेज साफ़ करे",
        "स्टोरेज हटाएं",
        "स्टोरेज हटाएँ",
        "स्पेस प्रबंधित करें",
        "जगह प्रबंधित करें",
        "स्थान प्रबंधित करें",
        "निष्क्रिय करें",
        "अक्षम करें",
        "अक्षम",
        "डिएक्टिवेट",
        "बंद करें",
        "टर्न ऑफ",
        "स्टॉप",
        "एप जानकारी",
        "जानकारी"
    )

    /**
     * Danger keywords for launcher packages (uninstall, app info, delete, and Hindi equivalents).
     */
    val LAUNCHER_DANGER_KEYWORDS: List<String> = listOf(
        "uninstall",
        "app info",
        "delete",
        "अनइंस्टॉल",
        "हटाएं",
        "हटाएँ",
        "डिलीट",
        "एप जानकारी",
        "जानकारी"
    )

    /**
     * Returns true if the package is a package installer package.
     */
    fun isInstallerPackage(packageName: String): Boolean {
        return INSTALLER_PACKAGES.contains(packageName)
    }

    /**
     * Returns true if the package is a settings package.
     */
    fun isSettingsPackage(packageName: String): Boolean {
        return SETTINGS_PACKAGES.contains(packageName)
    }

    /**
     * Resolves all home launcher packages installed on the device.
     */
    fun getLauncherPackages(context: Context): Set<String> {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveList = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(
                    homeIntent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(homeIntent, 0)
            }
        } catch (_: Exception) {
            emptyList()
        }
        return resolveList.mapNotNull { it.activityInfo?.packageName }
            .filter { it.isNotBlank() && it != "android" }
            .toSet()
    }
}
