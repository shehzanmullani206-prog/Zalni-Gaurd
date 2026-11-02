package com.zalni.guard

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Device Admin Receiver to provide legitimate Android anti-uninstall protection.
 */
class GuardDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Warning: Disabling Device Admin removes ZALNI Guard anti-uninstall protection."
    }
}
