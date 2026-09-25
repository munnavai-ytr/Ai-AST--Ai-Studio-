package com.example.security

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.example.antitheft.SendAlertToOwner
import com.example.service.VoiceAssistantAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Monitors the status of VoiceAssistantAccessibilityService.
 * If the service is disabled (by user, system, or third-party), it logs an
 * informational "accessibility_disabled" security event to Firestore for the owner.
 */
class AccessibilityTamperWatcher(private val context: Context) {

    private var settingsObserver: ContentObserver? = null
    private var periodicJob: Job? = null
    private var lastKnownEnabledState: Boolean? = null

    companion object {
        private const val TAG = "AccessibilityTamper"
        private const val PERIODIC_CHECK_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
    }

    /**
     * Checks if VoiceAssistantAccessibilityService is currently enabled.
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            if (am == null || !am.isEnabled) {
                return false
            }

            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            val expectedPackage = context.packageName
            val expectedClass = VoiceAssistantAccessibilityService::class.java.name

            val isEnabledInList = enabledServices.any { serviceInfo ->
                val resolveInfo = serviceInfo.resolveInfo?.serviceInfo
                resolveInfo != null &&
                        resolveInfo.packageName == expectedPackage &&
                        (resolveInfo.name == expectedClass || resolveInfo.name.endsWith("VoiceAssistantAccessibilityService"))
            }

            if (isEnabledInList) return true

            // Fallback: check Settings.Secure ENABLED_ACCESSIBILITY_SERVICES string
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""

            enabledServicesSetting.contains(expectedPackage) &&
                    enabledServicesSetting.contains("VoiceAssistantAccessibilityService")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility status", e)
            false
        }
    }

    /**
     * Starts active monitoring using ContentObserver and periodic coroutine verification.
     */
    fun startWatching(scope: CoroutineScope) {
        stopWatching()

        val isCurrentlyEnabled = isAccessibilityServiceEnabled()
        lastKnownEnabledState = isCurrentlyEnabled
        Log.d(TAG, "Starting AccessibilityTamperWatcher. Initial state: enabled=$isCurrentlyEnabled")

        // 1. ContentObserver for instant setting change notifications
        try {
            val handler = Handler(Looper.getMainLooper())
            val observer = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    checkAndNotify()
                }
            }
            context.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                false,
                observer
            )
            settingsObserver = observer
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register accessibility ContentObserver", e)
        }

        // 2. Periodic polling loop (every 5 minutes) as fallback
        periodicJob = scope.launch {
            while (isActive) {
                delay(PERIODIC_CHECK_INTERVAL_MS)
                checkAndNotify()
            }
        }
    }

    /**
     * Evaluates whether service state changed to disabled, and sends alert to owner.
     */
    fun checkAndNotify() {
        val currentEnabled = isAccessibilityServiceEnabled()
        val previousState = lastKnownEnabledState

        Log.d(TAG, "Accessibility status check: current=$currentEnabled, previous=$previousState")

        if (previousState == true && !currentEnabled) {
            Log.w(TAG, "Accessibility service was disabled! Sending alert to owner...")
            SendAlertToOwner.sendSecurityAlert(
                context = context,
                eventType = "accessibility_disabled",
                message = "Voice Assistant Accessibility Service was turned off on this device.",
                details = mapOf(
                    "status" to "disabled",
                    "severity" to "INFO",
                    "source" to "AccessibilityTamperWatcher"
                )
            )
        }

        lastKnownEnabledState = currentEnabled
    }

    /**
     * Stops monitoring and unregisters observers.
     */
    fun stopWatching() {
        periodicJob?.cancel()
        periodicJob = null

        settingsObserver?.let {
            try {
                context.contentResolver.unregisterContentObserver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering ContentObserver", e)
            }
            settingsObserver = null
        }
    }
}
