package com.example.devicesecurity

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * DeviceAdminReceiver to support remote lock capabilities authorized by the device owner.
 */
class SecurityDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, SecurityDeviceAdminReceiver::class.java)
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }
}
