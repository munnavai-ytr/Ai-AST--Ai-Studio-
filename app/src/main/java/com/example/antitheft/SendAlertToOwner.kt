package com.example.antitheft

import android.content.Context
import android.util.Log
import com.example.devicesecurity.DeviceSecurityPreferences
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sends security alerts and telemetry events to Cloud Firestore for the owner's dashboard.
 */
object SendAlertToOwner {

    private const val TAG = "SendAlertToOwner"
    private const val COLLECTION_ALERTS = "device_alerts"
    private const val COLLECTION_STATUS = "device_status"

    /**
     * Logs a security alert event to Firestore so the owner can review suspicious events.
     */
    fun sendSecurityAlert(
        context: Context,
        eventType: String,
        message: String,
        details: Map<String, Any> = emptyMap()
    ) {
        try {
            val deviceId = DeviceSecurityPreferences.getDeviceId(context)
            val now = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val formattedDate = dateFormat.format(Date(now))

            val alertData = hashMapOf<String, Any>(
                "deviceId" to deviceId,
                "eventType" to eventType,
                "message" to message,
                "timestamp" to now,
                "readableTime" to formattedDate
            )
            alertData.putAll(details)

            val firestore = FirebaseFirestore.getInstance()

            // 1. Add to historical device_alerts collection
            firestore.collection(COLLECTION_ALERTS)
                .add(alertData)
                .addOnSuccessListener {
                    Log.d(TAG, "Security alert recorded in Firestore: $eventType")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to send security alert to Firestore", e)
                }

            // 2. Update lastSecurityAlert on device_status document
            val statusUpdate = hashMapOf<String, Any>(
                "deviceId" to deviceId,
                "lastSecurityAlert" to eventType,
                "lastAlertMessage" to message,
                "lastAlertTimestamp" to now,
                "lastAlertTimeReadable" to formattedDate
            )
            firestore.collection(COLLECTION_STATUS)
                .document(deviceId)
                .set(statusUpdate, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "Updated last alert on device_status")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in sendSecurityAlert", e)
        }
    }
}
