package com.example.security

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.antitheft.SendAlertToOwner
import com.example.devicesecurity.DeviceSecurityPreferences
import com.google.firebase.auth.FirebaseAuth
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
        private const val NOTIFICATION_CHANNEL_ID = "app_install_transparency_channel"

        /**
         * Shows a visible transparency notification to the user about detected app installation.
         */
        private fun showInstallNotification(context: Context, appName: String, packageName: String) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "Mimi App Install Alerts",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Transparent notifications for newly installed apps"
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    (System.currentTimeMillis() % 10000).toInt(),
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val message = "Mimi Security detected a new app install: $appName"
                val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle("Mimi Security Alert")
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText("$message\nPackage: $packageName\nThis transparency notification ensures monitoring is visible."))
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()

                val notificationId = (System.currentTimeMillis() % 100000).toInt()
                notificationManager.notify(notificationId, notification)
            } catch (e: Exception) {
                Log.e(TAG, "Error displaying app install notification: ${e.message}", e)
            }
        }

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

                // Step 3: Show visible notification to the user for full transparency
                showInstallNotification(context, appName, packageName)

                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser == null) {
                    Log.d(TAG, "No authenticated owner signed in. Skipping Firestore app install log.")
                    return
                }

                val now = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val formattedDate = dateFormat.format(Date(now))
                val deviceId = DeviceSecurityPreferences.getDeviceId(context)
                val docId = DeviceSecurityPreferences.getSecurityDocumentId(context)

                val eventData = hashMapOf<String, Any>(
                    "deviceId" to deviceId,
                    "ownerUid" to currentUser.uid,
                    "ownerEmail" to (currentUser.email ?: ""),
                    "packageName" to packageName,
                    "appName" to appName,
                    "actionType" to actionType,
                    "installTime" to now,
                    "timestamp" to now,
                    "readableTime" to formattedDate
                )

                FirebaseFirestore.getInstance()
                    .collection(COLLECTION_INSTALLS)
                    .document("${docId}_${packageName}_$now")
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
