package com.example.devicesecurity

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import java.util.UUID

/**
 * Manages local preferences for Owner-Controlled Device Security.
 */
object DeviceSecurityPreferences {

    private const val PREF_NAME = "device_security_prefs"
    private const val KEY_SECRET_CODE = "owner_secret_code"
    private const val KEY_SECURITY_ENABLED = "owner_security_enabled"
    private const val KEY_SECURITY_MONITORING_ENABLED = "owner_security_monitoring_enabled"
    private const val KEY_FCM_TOKEN = "owner_fcm_token"
    private const val KEY_DEVICE_ID = "owner_device_id"
    private const val DEFAULT_SECRET_CODE = "1234"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun isSecurityMonitoringEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SECURITY_MONITORING_ENABLED, false)
    }

    fun setSecurityMonitoringEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SECURITY_MONITORING_ENABLED, enabled).apply()
    }

    fun getSecretCode(context: Context): String {
        return getPrefs(context).getString(KEY_SECRET_CODE, DEFAULT_SECRET_CODE) ?: DEFAULT_SECRET_CODE
    }

    fun setSecretCode(context: Context, code: String) {
        getPrefs(context).edit().putString(KEY_SECRET_CODE, code.trim()).apply()
    }

    fun isSecurityEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SECURITY_ENABLED, true)
    }

    fun setSecurityEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SECURITY_ENABLED, enabled).apply()
    }

    fun getFcmToken(context: Context): String? {
        return getPrefs(context).getString(KEY_FCM_TOKEN, null)
    }

    fun setFcmToken(context: Context, token: String) {
        getPrefs(context).edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    fun getDeviceId(context: Context): String {
        val prefs = getPrefs(context)
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId.isNullOrBlank()) {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            deviceId = if (!androidId.isNullOrBlank()) androidId else UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }
}
