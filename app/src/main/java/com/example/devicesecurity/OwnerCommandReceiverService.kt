package com.example.devicesecurity

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FirebaseMessagingService that receives remote owner commands for device security.
 * Validates the owner's secret code before executing any actions (Locate, Lock).
 */
class OwnerCommandReceiverService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "OwnerCommandReceiver"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Token received for device: $token")
        DeviceSecurityPreferences.setFcmToken(applicationContext, token)

        // Sync FCM token to Firestore device_status document
        try {
            val deviceId = DeviceSecurityPreferences.getDeviceId(applicationContext)
            val docId = DeviceSecurityPreferences.getSecurityDocumentId(applicationContext)
            val ownerUid = DeviceSecurityPreferences.getOwnerUid()
            val data = hashMapOf(
                "deviceId" to deviceId,
                "ownerUid" to (ownerUid ?: ""),
                "fcmToken" to token,
                "tokenUpdatedAt" to System.currentTimeMillis()
            )
            FirebaseFirestore.getInstance()
                .collection("device_status")
                .document(docId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "FCM Token registered in Firestore for doc $docId")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync token to Firestore", e)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Message received from: ${remoteMessage.from}")

        // Check if device security module is enabled by the owner
        if (!DeviceSecurityPreferences.isSecurityEnabled(applicationContext)) {
            Log.w(TAG, "Device security is disabled by user settings. Ignoring command.")
            return
        }

        val data = remoteMessage.data
        if (data.isEmpty()) {
            Log.d(TAG, "Empty data payload received.")
            return
        }

        val action = data["action"] ?: data["command"] ?: ""
        val receivedSecretCode = data["secret_code"] ?: data["secretCode"] ?: data["pin"] ?: ""
        val ownerSecretCode = DeviceSecurityPreferences.getSecretCode(applicationContext)

        // Security check: verify secret code set by device owner
        if (receivedSecretCode.isBlank() || receivedSecretCode != ownerSecretCode) {
            Log.w(TAG, "Security verification failed! Provided code does not match owner secret code.")
            return
        }

        Log.i(TAG, "Owner authentication verified. Executing command: $action")

        when (action.uppercase()) {
            "LOCATE", "LOCATE_DEVICE", "FIND_MY_DEVICE", "GET_LOCATION" -> {
                Log.d(TAG, "Triggering findMyDeviceLocation()")
                DeviceLocatorHelper.findMyDeviceLocation(applicationContext)
            }
            "LOCK", "LOCK_DEVICE", "REMOTE_LOCK" -> {
                Log.d(TAG, "Triggering lockDeviceRemotely()")
                DeviceLocatorHelper.lockDeviceRemotely(applicationContext)
            }
            else -> {
                Log.w(TAG, "Unrecognized owner command: $action")
            }
        }
    }
}
