package com.zalni.guard

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.example.util.PermissionUtils

/**
 * Device Admin Receiver to provide legitimate Android anti-uninstall protection.
 */
class GuardDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        PermissionUtils.applyDeviceOwnerRestrictions(context)
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        if (PermissionUtils.isDeviceOwnerActive(context)) {
            PermissionUtils.applyDeviceOwnerRestrictions(context)
        }
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Warning: Disabling Device Admin removes ZALNI Guard anti-uninstall protection."
    }
}
