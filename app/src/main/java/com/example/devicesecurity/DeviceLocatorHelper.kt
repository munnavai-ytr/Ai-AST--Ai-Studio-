package com.example.devicesecurity

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helper class for Owner-Controlled Device Security actions:
 * - Real-time FusedLocation location retrieval (similar to Google Find My Device)
 * - Remote device locking via DevicePolicyManager
 * - Updating device status and location in Cloud Firestore
 * - Displaying prominent transparent notifications on every action
 */
object DeviceLocatorHelper {

    private const val TAG = "DeviceLocatorHelper"
    private const val CHANNEL_ID = "owner_device_security_channel"
    private const val CHANNEL_NAME = "Find My Device Security Alerts"
    private const val NOTIFICATION_ID_LOCATE = 9001
    private const val NOTIFICATION_ID_LOCK = 9002

    /**
     * Retrieves the device location using FusedLocationProviderClient and updates Firestore.
     * Shows a visible notification informing the user that location was requested by the owner.
     */
    fun findMyDeviceLocation(context: Context) {
        // Always show visible notification for transparency
        showSecurityNotification(
            context = context,
            notificationId = NOTIFICATION_ID_LOCATE,
            title = "Device Location Requested",
            message = "Your device location was requested remotely by the owner."
        )

        val hasFine = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            Log.w(TAG, "Location permission not granted. Cannot fetch coordinates.")
            updateStatusInFirestore(
                context = context,
                status = "PERMISSION_DENIED",
                lastAction = "LOCATE",
                extraData = mapOf("error" to "Location permissions not granted on device")
            )
            return
        }

        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()

            // Request current high accuracy location
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        Log.d(TAG, "Fetched current location: ${location.latitude}, ${location.longitude}")
                        saveLocationToFirestore(context, location)
                    } else {
                        // Fallback to last known location
                        fusedClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                            if (lastLoc != null) {
                                Log.d(TAG, "Fetched last known location: ${lastLoc.latitude}, ${lastLoc.longitude}")
                                saveLocationToFirestore(context, lastLoc)
                            } else {
                                Log.w(TAG, "No location available.")
                                updateStatusInFirestore(
                                    context = context,
                                    status = "LOCATION_UNAVAILABLE",
                                    lastAction = "LOCATE",
                                    extraData = mapOf("error" to "Device GPS returned null location")
                                )
                            }
                        }.addOnFailureListener { e ->
                            Log.e(TAG, "Failed to get last known location", e)
                            updateStatusInFirestore(
                                context = context,
                                status = "LOCATION_ERROR",
                                lastAction = "LOCATE",
                                extraData = mapOf("error" to (e.message ?: "Unknown error"))
                            )
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get current location", e)
                    updateStatusInFirestore(
                        context = context,
                        status = "LOCATION_ERROR",
                        lastAction = "LOCATE",
                        extraData = mapOf("error" to (e.message ?: "Unknown error"))
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during location lookup", e)
        }
    }

    /**
     * Remotely locks the device using DevicePolicyManager if device admin is active.
     * Shows a visible notification informing that remote lock was initiated.
     */
    fun lockDeviceRemotely(context: Context) {
        showSecurityNotification(
            context = context,
            notificationId = NOTIFICATION_ID_LOCK,
            title = "Device Locked Remotely",
            message = "This device was locked remotely by the owner."
        )

        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val adminComponent = SecurityDeviceAdminReceiver.getComponentName(context)

            if (dpm != null && dpm.isAdminActive(adminComponent)) {
                Log.d(TAG, "Executing remote lockNow()")
                dpm.lockNow()
                updateStatusInFirestore(
                    context = context,
                    status = "LOCKED",
                    lastAction = "LOCK",
                    extraData = mapOf("lockSuccess" to true)
                )
            } else {
                Log.w(TAG, "Device Admin is not active. Unable to call lockNow().")
                updateStatusInFirestore(
                    context = context,
                    status = "LOCK_FAILED_NO_ADMIN",
                    lastAction = "LOCK",
                    extraData = mapOf("error" to "Device Admin permission is not activated on device")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during remote device lock", e)
            updateStatusInFirestore(
                context = context,
                status = "LOCK_ERROR",
                lastAction = "LOCK",
                extraData = mapOf("error" to (e.message ?: "Lock exception"))
            )
        }
    }

    /**
     * Saves location coordinates, battery percentage, and timestamps to Firestore.
     */
    private fun saveLocationToFirestore(context: Context, location: Location) {
        try {
            val deviceId = DeviceSecurityPreferences.getDeviceId(context)
            val docId = DeviceSecurityPreferences.getSecurityDocumentId(context)
            val ownerUid = DeviceSecurityPreferences.getOwnerUid()
            val now = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val formattedDate = dateFormat.format(Date(now))
            val (batteryLevel, isCharging) = getBatteryInfo(context)

            val locationData = hashMapOf(
                "deviceId" to deviceId,
                "ownerUid" to (ownerUid ?: ""),
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "accuracy" to location.accuracy,
                "altitude" to location.altitude,
                "speed" to location.speed,
                "bearing" to location.bearing,
                "timestamp" to now,
                "readableTime" to formattedDate,
                "batteryLevel" to batteryLevel,
                "isCharging" to isCharging,
                "status" to "LOCATED",
                "lastAction" to "LOCATE",
                "mapsUrl" to "https://maps.google.com/?q=${location.latitude},${location.longitude}"
            )

            FirebaseFirestore.getInstance()
                .collection("device_status")
                .document(docId)
                .set(locationData, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "Location successfully updated in Firestore for doc: $docId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to write location to Firestore", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing location to Firestore", e)
        }
    }

    /**
     * Updates device status in Firestore with custom status string and action name.
     */
    private fun updateStatusInFirestore(
        context: Context,
        status: String,
        lastAction: String,
        extraData: Map<String, Any> = emptyMap()
    ) {
        try {
            val deviceId = DeviceSecurityPreferences.getDeviceId(context)
            val docId = DeviceSecurityPreferences.getSecurityDocumentId(context)
            val ownerUid = DeviceSecurityPreferences.getOwnerUid()
            val now = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val formattedDate = dateFormat.format(Date(now))
            val (batteryLevel, isCharging) = getBatteryInfo(context)

            val statusData = hashMapOf<String, Any>(
                "deviceId" to deviceId,
                "ownerUid" to (ownerUid ?: ""),
                "status" to status,
                "lastAction" to lastAction,
                "timestamp" to now,
                "readableTime" to formattedDate,
                "batteryLevel" to batteryLevel,
                "isCharging" to isCharging
            )
            statusData.putAll(extraData)

            FirebaseFirestore.getInstance()
                .collection("device_status")
                .document(docId)
                .set(statusData, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "Status updated in Firestore: $status")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to update status in Firestore", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating status in Firestore", e)
        }
    }

    /**
     * Gets current battery level percentage and charging state.
     */
    private fun getBatteryInfo(context: Context): Pair<Int, Boolean> {
        return try {
            val batteryIntent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val batteryPct = if (level >= 0 && scale > 0) {
                ((level / scale.toFloat()) * 100).toInt()
            } else {
                -1
            }
            Pair(batteryPct, isCharging)
        } catch (e: Exception) {
            Pair(-1, false)
        }
    }

    /**
     * Displays a visible notification for transparency when security features are used.
     */
    private fun showSecurityNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows notifications when Find My Device security actions are requested by the owner."
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }
}
