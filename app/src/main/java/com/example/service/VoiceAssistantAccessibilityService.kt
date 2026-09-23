package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import java.net.URLEncoder

/**
 * Data structure representing a structured action command received from Gemini.
 * Supports both legacy flat format and modern actions array format.
 */
data class AssistantActionCommand(
    val action: String,
    val app: String? = null,
    val text: String? = null,
    val targetId: String? = null,
    val setting: String? = null,
    val query: String? = null,
    val state: String? = null,
    val rawJson: String? = null
)

/**
 * Encapsulates full parsed response from Gemini:
 * A conversational human-like 'reply' and a list of 'actions' to execute.
 */
data class MimiGeminiResponse(
    val reply: String,
    val actions: List<AssistantActionCommand> = emptyList(),
    val rawJson: String? = null
)

/**
 * Result of executing an action.
 */
data class AssistantActionResult(
    val success: Boolean,
    val message: String,
    val actionType: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Android AccessibilityService that executes hands-free UI automation commands from Gemini:
 * - Uses Intent to launch requested applications.
 * - Uses AccessibilityNodeInfo to find and click UI elements on the screen.
 */
class VoiceAssistantAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "VoiceAssistantAccessibilityService connected")
        instanceRef = WeakReference(this)
        _isServiceConnected.value = true

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        info.notificationTimeout = 100
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Accessibility events received; available for context tracking
    }

    override fun onInterrupt() {
        Log.w(TAG, "VoiceAssistantAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "VoiceAssistantAccessibilityService destroyed")
        if (instanceRef?.get() == this) {
            instanceRef = null
        }
        _isServiceConnected.value = false
    }

    /**
     * Executes an assistant action command parsed from Gemini JSON output.
     */
    fun executeCommand(command: AssistantActionCommand): AssistantActionResult {
        Log.i(TAG, "Executing command: action='${command.action}', app='${command.app}', text='${command.text}'")
        val result = when (command.action.lowercase().trim()) {
            "open_settings", "settings" -> {
                val targetSetting = command.setting ?: command.text ?: command.app ?: ""
                openSpecificSettings(this, targetSetting)
            }
            "flashlight", "torch" -> {
                val state = command.state?.lowercase()?.trim()
                val turnOn = state != "off" && state != "false" && state != "disable" && state != "stop"
                setFlashlight(this, turnOn)
            }
            "play_youtube", "youtube_play", "youtube" -> {
                val query = command.query ?: command.text ?: command.app ?: ""
                playYouTubeVideo(this, query)
            }
            "open", "launch", "start" -> {
                val appName = command.app ?: command.text ?: ""
                val cleanApp = appName.lowercase().trim()
                if (cleanApp == "settings" && !command.setting.isNullOrBlank()) {
                    openSpecificSettings(this, command.setting)
                } else if ((cleanApp == "youtube" || cleanApp.contains("youtube")) && !command.query.isNullOrBlank()) {
                    playYouTubeVideo(this, command.query)
                } else if (appName.isBlank()) {
                    AssistantActionResult(false, "No app name specified in command", command.action)
                } else {
                    openApp(appName)
                }
            }
            "click", "tap", "press" -> {
                val targetText = command.text ?: command.app ?: ""
                if (targetText.isBlank() && command.targetId.isNullOrBlank()) {
                    AssistantActionResult(false, "No target text or ID specified to click", command.action)
                } else {
                    clickElement(targetText, command.targetId)
                }
            }
            "home" -> {
                val performed = performGlobalAction(GLOBAL_ACTION_HOME)
                AssistantActionResult(performed, if (performed) "Navigated to Home screen" else "Failed to go home", "home")
            }
            "back" -> {
                val performed = performGlobalAction(GLOBAL_ACTION_BACK)
                AssistantActionResult(performed, if (performed) "Navigated Back" else "Failed to go back", "back")
            }
            "recents", "recent_apps" -> {
                val performed = performGlobalAction(GLOBAL_ACTION_RECENTS)
                AssistantActionResult(performed, if (performed) "Opened Recent Apps" else "Failed to open recent apps", "recents")
            }
            "notifications" -> {
                val performed = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                AssistantActionResult(performed, if (performed) "Opened Notifications" else "Failed to open notifications", "notifications")
            }
            "scroll_forward", "scroll_down" -> {
                scrollScreen(forward = true)
            }
            "scroll_backward", "scroll_up" -> {
                scrollScreen(forward = false)
            }
            else -> {
                AssistantActionResult(false, "Unknown action: '${command.action}'", command.action)
            }
        }

        _lastActionResult.value = result
        return result
    }

    /**
     * Uses Intent to open an application based on its common name or package.
     */
    fun openApp(targetApp: String): AssistantActionResult {
        val cleanName = targetApp.lowercase().trim()
        val pm = packageManager

        // 1. Resolve package name from known aliases or by searching installed apps
        val resolvedPackage = resolvePackageName(cleanName, pm)

        if (resolvedPackage != null) {
            val launchIntent = pm.getLaunchIntentForPackage(resolvedPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                try {
                    startActivity(launchIntent)
                    return AssistantActionResult(
                        success = true,
                        message = "Opened application '$targetApp'",
                        actionType = "open"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start activity for $resolvedPackage", e)
                    return AssistantActionResult(
                        success = false,
                        message = "Could not open '$targetApp': ${e.localizedMessage}",
                        actionType = "open"
                    )
                }
            }
        }

        // 2. Fallback: Search launcher activities matching the label
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = pm.queryIntentActivities(mainIntent, 0)
            for (appInfo in apps) {
                val label = appInfo.loadLabel(pm).toString().lowercase()
                if (label.contains(cleanName) || cleanName.contains(label)) {
                    val launch = pm.getLaunchIntentForPackage(appInfo.activityInfo.packageName)
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launch)
                        return AssistantActionResult(
                            success = true,
                            message = "Opened '${appInfo.loadLabel(pm)}'",
                            actionType = "open"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying launcher apps: ${e.message}")
        }

        return AssistantActionResult(
            success = false,
            message = "Application '$targetApp' not found on device",
            actionType = "open"
        )
    }

    /**
     * Uses AccessibilityNodeInfo to find and click UI elements on the screen.
     */
    fun clickElement(targetText: String, targetId: String? = null): AssistantActionResult {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            return AssistantActionResult(
                success = false,
                message = "Cannot access active window screen. Please verify accessibility permission.",
                actionType = "click"
            )
        }

        // 1. If target view ID is provided, try finding by view ID first
        if (!targetId.isNullOrBlank()) {
            val nodesById = rootNode.findAccessibilityNodeInfosByViewId(targetId)
            if (!nodesById.isNullOrEmpty()) {
                for (node in nodesById) {
                    if (performClickOnNodeOrAncestor(node)) {
                        return AssistantActionResult(
                            success = true,
                            message = "Clicked element with ID '$targetId'",
                            actionType = "click"
                        )
                    }
                }
            }
        }

        // 2. Try findAccessibilityNodeInfosByText
        if (targetText.isNotBlank()) {
            val nodesByText = rootNode.findAccessibilityNodeInfosByText(targetText)
            if (!nodesByText.isNullOrEmpty()) {
                for (node in nodesByText) {
                    if (performClickOnNodeOrAncestor(node)) {
                        return AssistantActionResult(
                            success = true,
                            message = "Clicked '$targetText'",
                            actionType = "click"
                        )
                    }
                }
            }

            // 3. Fallback: Deep traversal matching text, content description, or hint text (case-insensitive)
            val matchingNode = findNodeFuzzy(rootNode, targetText.lowercase().trim())
            if (matchingNode != null) {
                if (performClickOnNodeOrAncestor(matchingNode)) {
                    return AssistantActionResult(
                        success = true,
                        message = "Found and clicked '$targetText'",
                        actionType = "click"
                    )
                }
            }
        }

        return AssistantActionResult(
            success = false,
            message = "Could not find clickable element for '$targetText' on screen",
            actionType = "click"
        )
    }

    private fun scrollScreen(forward: Boolean): AssistantActionResult {
        val root = rootInActiveWindow ?: return AssistantActionResult(false, "No active window", "scroll")
        val scrollable = findScrollableNode(root)
        if (scrollable != null) {
            val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            val success = scrollable.performAction(action)
            return AssistantActionResult(
                success = success,
                message = if (success) "Scrolled screen" else "Failed to scroll screen",
                actionType = "scroll"
            )
        }
        return AssistantActionResult(false, "No scrollable container found on screen", "scroll")
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollableNode(child)
            if (found != null) return found
        }
        return null
    }

    /**
     * Traverses the node or its parents to find a clickable element and calls ACTION_CLICK.
     */
    private fun performClickOnNodeOrAncestor(startNode: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = startNode
        while (current != null) {
            if (current.isClickable) {
                val clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    return true
                }
            }
            current = current.parent
        }
        // Fallback: try performing ACTION_CLICK directly on the original node
        return startNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /**
     * Fuzzy recursive tree search for node matching text or content description.
     */
    private fun findNodeFuzzy(node: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        val hintText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            node.hintText?.toString()?.lowercase() ?: ""
        } else {
            ""
        }

        if (text.contains(query) || contentDesc.contains(query) || hintText.contains(query)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeFuzzy(child, query)
            if (found != null) {
                return found
            }
        }
        return null
    }

    /**
     * Resolves target names into package IDs (handles common aliases).
     */
    private fun resolvePackageName(name: String, pm: PackageManager): String? {
        // Direct package name check
        try {
            pm.getPackageInfo(name, 0)
            return name
        } catch (_: Exception) {}

        // Known popular application package maps
        val aliases = mapOf(
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "google chrome" to "com.android.chrome",
            "browser" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "gmail" to "com.google.android.gm",
            "email" to "com.google.android.gm",
            "settings" to "com.android.settings",
            "camera" to "com.google.android.GoogleCamera",
            "play store" to "com.android.vending",
            "google play" to "com.android.vending",
            "whatsapp" to "com.whatsapp",
            "calculator" to "com.google.android.calculator",
            "clock" to "com.google.android.deskclock",
            "alarm" to "com.google.android.deskclock",
            "messages" to "com.google.android.apps.messaging",
            "phone" to "com.google.android.dialer",
            "dialer" to "com.google.android.dialer",
            "photos" to "com.google.android.apps.photos",
            "calendar" to "com.google.android.calendar",
            "spotify" to "com.spotify.music"
        )

        val mapped = aliases[name]
        if (mapped != null) {
            try {
                pm.getPackageInfo(mapped, 0)
                return mapped
            } catch (_: Exception) {}
        }

        return null
    }

    companion object {
        private const val TAG = "AssistantAccessibility"

        private var instanceRef: WeakReference<VoiceAssistantAccessibilityService>? = null

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        private val _lastActionResult = MutableStateFlow<AssistantActionResult?>(null)
        val lastActionResult: StateFlow<AssistantActionResult?> = _lastActionResult.asStateFlow()

        /**
         * Returns the active instance of the AccessibilityService if connected.
         */
        fun getInstance(): VoiceAssistantAccessibilityService? = instanceRef?.get()

        /**
         * Checks if the Accessibility Service is actively connected.
         */
        fun isConnected(): Boolean = _isServiceConnected.value && instanceRef?.get() != null

        fun openSpecificSettings(context: Context, settingName: String): AssistantActionResult {
            val clean = settingName.lowercase().trim()
            val action = when {
                clean.contains("wifi") || clean.contains("wi-fi") || clean.contains("network") || clean.contains("internet") -> {
                    Settings.ACTION_WIFI_SETTINGS
                }
                clean.contains("bluetooth") || clean.contains("bt") -> {
                    Settings.ACTION_BLUETOOTH_SETTINGS
                }
                clean.contains("display") || clean.contains("brightness") || clean.contains("screen") -> {
                    Settings.ACTION_DISPLAY_SETTINGS
                }
                clean.contains("sound") || clean.contains("volume") || clean.contains("audio") -> {
                    Settings.ACTION_SOUND_SETTINGS
                }
                clean.contains("battery") || clean.contains("power") -> {
                    Settings.ACTION_BATTERY_SAVER_SETTINGS
                }
                clean.contains("location") || clean.contains("gps") -> {
                    Settings.ACTION_LOCATION_SOURCE_SETTINGS
                }
                clean.contains("accessibility") -> {
                    Settings.ACTION_ACCESSIBILITY_SETTINGS
                }
                clean.contains("app") -> {
                    Settings.ACTION_APPLICATION_SETTINGS
                }
                clean.contains("airplane") -> {
                    Settings.ACTION_AIRPLANE_MODE_SETTINGS
                }
                clean.contains("date") || clean.contains("time") -> {
                    Settings.ACTION_DATE_SETTINGS
                }
                clean.contains("storage") -> {
                    Settings.ACTION_INTERNAL_STORAGE_SETTINGS
                }
                else -> {
                    Settings.ACTION_SETTINGS
                }
            }
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                context.startActivity(intent)
                AssistantActionResult(
                    success = true,
                    message = "Opened ${if (clean.isBlank()) "Settings" else "$settingName settings"}",
                    actionType = "open_settings"
                )
            } catch (e: Exception) {
                AssistantActionResult(
                    success = false,
                    message = "Could not open $settingName settings: ${e.localizedMessage}",
                    actionType = "open_settings"
                )
            }
        }

        fun playYouTubeVideo(context: Context, query: String): AssistantActionResult {
            val cleanQuery = query.trim()
            if (cleanQuery.isBlank()) {
                val service = getInstance()
                return service?.openApp("youtube") ?: AssistantActionResult(false, "No query for YouTube", "play_youtube")
            }
            return try {
                val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
                val appUri = Uri.parse("vnd.youtube:results?search_query=$encoded")
                val appIntent = Intent(Intent.ACTION_VIEW, appUri).apply {
                    setPackage("com.google.android.youtube")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (appIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(appIntent)
                } else {
                    val webUri = Uri.parse("https://www.youtube.com/results?search_query=$encoded")
                    val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(webIntent)
                }
                AssistantActionResult(
                    success = true,
                    message = "Playing '$cleanQuery' on YouTube",
                    actionType = "play_youtube"
                )
            } catch (e: Exception) {
                AssistantActionResult(
                    success = false,
                    message = "Failed to launch YouTube: ${e.localizedMessage}",
                    actionType = "play_youtube"
                )
            }
        }

        fun setFlashlight(context: Context, turnOn: Boolean): AssistantActionResult {
            return try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                val cameraId = cameraManager?.cameraIdList?.firstOrNull()
                if (cameraManager != null && cameraId != null) {
                    cameraManager.setTorchMode(cameraId, turnOn)
                    AssistantActionResult(
                        success = true,
                        message = if (turnOn) "Flashlight turned on" else "Flashlight turned off",
                        actionType = "flashlight"
                    )
                } else {
                    AssistantActionResult(
                        success = false,
                        message = "No flashlight hardware available",
                        actionType = "flashlight"
                    )
                }
            } catch (e: Exception) {
                AssistantActionResult(
                    success = false,
                    message = "Flashlight control error: ${e.localizedMessage}",
                    actionType = "flashlight"
                )
            }
        }
    }
}
