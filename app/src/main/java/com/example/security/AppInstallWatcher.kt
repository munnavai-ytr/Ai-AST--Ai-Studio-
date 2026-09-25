package com.example.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.antitheft.SendAlertToOwner
import com.example.devicesecurity.DeviceSecurityPreferences
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BroadcastReceiver & monitor that watches for newly installed applications.
 * When a new app is installed, logs the package name and install time to the
 * "app_install_events" collection in Firestore for the owner's visibility.
 */
class AppInstallWatcher : BroadcastReceiver() {

    companion object {
        private const val TAG = "AppInstallWatcher"
        private const val COLLECTION_INSTALLS = "app_install_events"

        /**
         * Logs an install event to Firestore collection "app_install_events" and informs owner.
         */
        fun logAppInstallEvent(
            context: Context,
            packageName: String,
            actionType: String = "PACKAGE_ADDED"
        ) {
            try {
                val pm = context.packageManager
                val appName = try {
                    val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getApplicationInfo(packageName, 0)
                    }
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    packageName
                }

                val now = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val formattedDate = dateFormat.format(Date(now))
                val deviceId = DeviceSecurityPreferences.getDeviceId(context)

                val eventData = hashMapOf<String, Any>(
                    "deviceId" to deviceId,
                    "packageName" to packageName,
                    "appName" to appName,
                    "actionType" to actionType,
                    "installTime" to now,
                    "timestamp" to now,
                    "readableTime" to formattedDate
                )

                FirebaseFirestore.getInstance()
                    .collection(COLLECTION_INSTALLS)
                    .document("${deviceId}_${packageName}_$now")
                    .set(eventData, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(TAG, "Successfully logged app install event for $packageName ($appName)")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to log app install event to Firestore", e)
                    }

                // Send informational security alert to owner
                SendAlertToOwner.sendSecurityAlert(
                    context = context,
                    eventType = "app_installed",
                    message = "New application installed: $appName ($packageName)",
                    details = mapOf(
                        "packageName" to packageName,
                        "appName" to appName,
                        "action" to actionType
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in logAppInstallEvent", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return

        if (action == Intent.ACTION_PACKAGE_ADDED || action == Intent.ACTION_PACKAGE_REPLACED) {
            val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            val uri = intent.data
            val packageName = uri?.schemeSpecificPart ?: uri?.encodedSchemeSpecificPart ?: ""

            if (packageName.isNotBlank() && packageName != context.packageName) {
                val actionType = if (replacing) "PACKAGE_UPDATED" else "PACKAGE_ADDED"
                Log.i(TAG, "Detected package change: $actionType for $packageName")
                logAppInstallEvent(context, packageName, actionType)
            }
        }
    }
}
