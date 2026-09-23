package com.example.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Handles parsing and executing JSON commands received from Gemini:
 * - e.g. {"action": "open", "app": "youtube"}
 * - e.g. {"action": "click", "text": "Search"}
 * - e.g. {"action": "home"}
 * - e.g. {"action": "back"}
 */
object AssistantActionManager {

    private const val TAG = "AssistantActionManager"

    // Pattern to detect JSON objects containing "action"
    private val JSON_ACTION_PATTERN = Pattern.compile(
        "\\{[^{}]*\"action\"\\s*:\\s*\"[^\"]+\"[^{}]*\\}",
        Pattern.DOTALL
    )

    /**
     * Parses the modern Gemini response containing:
     * { "reply": "...", "actions": [ {"type": "OPEN_APP", "target": "..."} ] }
     */
    fun parseMimiResponse(rawText: String): MimiGeminiResponse {
        if (rawText.isBlank()) return MimiGeminiResponse(reply = "")

        val cleanJson = extractCleanJson(rawText)
        if (cleanJson != null) {
            val replyMatch = Regex("\"reply\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"", RegexOption.DOT_MATCHES_ALL).find(cleanJson)
            val reply = replyMatch?.groupValues?.get(1)?.replace("\\\"", "\"")?.replace("\\n", "\n") ?: ""

            val actionsList = mutableListOf<AssistantActionCommand>()

            // Extract actions array items
            val actionsArrayMatch = Regex("\"actions\"\\s*:\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL).find(cleanJson)
            if (actionsArrayMatch != null) {
                val arrayContent = actionsArrayMatch.groupValues[1]
                val objPattern = Regex("\\{(.*?)\\}", RegexOption.DOT_MATCHES_ALL)
                objPattern.findAll(arrayContent).forEach { match ->
                    val actionJson = match.value
                    val parsed = parseSingleActionRegex(actionJson)
                    if (parsed != null) {
                        actionsList.add(parsed)
                    }
                }
            } else {
                // Fallback to single action format {"action": "..."}
                val singleAction = parseJsonObject(cleanJson)
                if (singleAction != null) {
                    actionsList.add(singleAction)
                }
            }

            return MimiGeminiResponse(
                reply = reply.ifBlank { cleanConversationalReply(rawText) },
                actions = actionsList,
                rawJson = cleanJson
            )
        }

        // Fallback for conversational plain text or partial match
        return MimiGeminiResponse(
            reply = cleanConversationalReply(rawText),
            actions = emptyList(),
            rawJson = rawText
        )
    }

    private fun extractCleanJson(rawText: String): String? {
        val trimmed = rawText.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed
        }
        if (trimmed.contains("```")) {
            val codeBlockPattern = Pattern.compile("```(?:json)?\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL)
            val matcher = codeBlockPattern.matcher(trimmed)
            if (matcher.find()) {
                return matcher.group(1)?.trim()
            }
        }
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1)
        }
        return null
    }

    private fun parseSingleActionRegex(jsonString: String): AssistantActionCommand? {
        val typeMatch = Regex("\"(?:type|action)\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(jsonString) ?: return null
        val type = typeMatch.groupValues[1].trim()

        val target = Regex("\"target\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(jsonString)?.groupValues?.get(1)?.trim()
        val app = Regex("\"app\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(jsonString)?.groupValues?.get(1)?.trim() ?: target
        val text = Regex("\"text\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(jsonString)?.groupValues?.get(1)?.trim() ?: target
        val targetId = Regex("\"targetId\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(jsonString)?.groupValues?.get(1)?.trim()
        val setting = Regex("\"setting\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(jsonString)?.groupValues?.get(1)?.trim() ?: target
        val query = Regex("\"(?:query|video|search)\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(jsonString)?.groupValues?.get(1)?.trim() ?: target
        val state = Regex("\"(?:state|status|mode)\"\\s*:\\s*\"?([^\",}]+)\"?", RegexOption.IGNORE_CASE).find(jsonString)?.groupValues?.get(1)?.trim()

        val action = when (type.uppercase()) {
            "OPEN_APP", "OPEN", "LAUNCH" -> "open"
            "CLICK", "TAP", "PRESS" -> "click"
            "GLOBAL_ACTION", "GESTURE" -> {
                val actionVal = Regex("\"action\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(jsonString)?.groupValues?.get(1)?.trim()
                actionVal?.lowercase() ?: target?.lowercase() ?: "home"
            }
            "HOME" -> "home"
            "BACK" -> "back"
            "RECENTS" -> "recents"
            "NOTIFICATIONS" -> "notifications"
            "SCROLL" -> {
                val dir = Regex("\"direction\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(jsonString)?.groupValues?.get(1)?.trim()?.uppercase()
                if (dir == "BACKWARD" || dir == "UP") "scroll_backward" else "scroll_forward"
            }
            "OPEN_SETTINGS", "SETTINGS" -> "open_settings"
            "FLASHLIGHT", "TORCH" -> "flashlight"
            "PLAY_YOUTUBE", "YOUTUBE" -> "play_youtube"
            else -> type.lowercase()
        }

        return AssistantActionCommand(
            action = action,
            app = if (action == "open") app else null,
            text = if (action == "click") text else null,
            targetId = targetId,
            setting = if (action == "open_settings") setting else null,
            query = if (action == "play_youtube") query else null,
            state = state,
            rawJson = jsonString.trim()
        )
    }

    private fun cleanConversationalReply(text: String): String {
        return text.replace(Regex("```(?:json)?.*?```", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("\\{.*?\\}", RegexOption.DOT_MATCHES_ALL), "")
            .trim()
    }

    /**
     * Parses a structured JSON command from Gemini's response string.
     * Can extract JSON even if embedded within conversational text or markdown code blocks.
     */
    fun parseCommand(rawText: String): AssistantActionCommand? {
        val mimiResponse = parseMimiResponse(rawText)
        return mimiResponse.actions.firstOrNull()
    }

    private fun parseJsonObject(jsonString: String): AssistantActionCommand? {
        val actionMatch = Regex("\"action\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(jsonString) ?: return null
        val action = actionMatch.groupValues[1].trim()

        val app = Regex("\"app\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(jsonString)?.groupValues?.get(1)?.trim()
        val text = Regex("\"text\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(jsonString)?.groupValues?.get(1)?.trim()
        val targetId = Regex("\"targetId\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(jsonString)?.groupValues?.get(1)?.trim()
        val setting = Regex("\"setting\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(jsonString)?.groupValues?.get(1)?.trim()
        val query = Regex("\"(?:query|video|search)\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(jsonString)?.groupValues?.get(1)?.trim()
        val state = Regex("\"(?:state|status|mode)\"\\s*:\\s*\"?([^\",}]+)\"?", RegexOption.IGNORE_CASE).find(jsonString)?.groupValues?.get(1)?.trim()

        return AssistantActionCommand(
            action = action,
            app = app,
            text = text,
            targetId = targetId,
            setting = setting,
            query = query,
            state = state,
            rawJson = jsonString.trim()
        )
    }

    /**
     * Executes the parsed Gemini command using AccessibilityService or Intent.
     */
    fun executeCommand(context: Context, command: AssistantActionCommand): AssistantActionResult {
        val service = VoiceAssistantAccessibilityService.getInstance()

        // 1. If AccessibilityService is connected, execute directly via service
        if (service != null && VoiceAssistantAccessibilityService.isConnected()) {
            return service.executeCommand(command)
        }

        // 2. Direct intent/hardware executions can run via Context even if AccessibilityService is not yet turned on
        val actionType = command.action.lowercase().trim()
        when (actionType) {
            "open_settings", "settings" -> {
                val targetSetting = command.setting ?: command.text ?: command.app ?: ""
                return VoiceAssistantAccessibilityService.openSpecificSettings(context, targetSetting)
            }
            "flashlight", "torch" -> {
                val state = command.state?.lowercase()?.trim()
                val turnOn = state != "off" && state != "false" && state != "disable" && state != "stop"
                return VoiceAssistantAccessibilityService.setFlashlight(context, turnOn)
            }
            "play_youtube", "youtube_play", "youtube" -> {
                val query = command.query ?: command.text ?: command.app ?: ""
                return VoiceAssistantAccessibilityService.playYouTubeVideo(context, query)
            }
            "open", "launch", "start" -> {
                val appName = command.app ?: command.text ?: ""
                val cleanApp = appName.lowercase().trim()
                if (cleanApp == "settings" && !command.setting.isNullOrBlank()) {
                    return VoiceAssistantAccessibilityService.openSpecificSettings(context, command.setting)
                }
                if ((cleanApp == "youtube" || cleanApp.contains("youtube")) && !command.query.isNullOrBlank()) {
                    return VoiceAssistantAccessibilityService.playYouTubeVideo(context, command.query)
                }
                return openAppViaContext(context, appName)
            }
        }

        // For "click", "back", "home", accessibility service is strictly required
        return AssistantActionResult(
            success = false,
            message = "Accessibility Service is not enabled. Please enable it in Settings to allow on-screen clicks and navigation.",
            actionType = command.action
        )
    }

    /**
     * Fallback to launch applications directly from Context when AccessibilityService is not yet active.
     */
    private fun openAppViaContext(context: Context, appName: String): AssistantActionResult {
        val pm = context.packageManager
        val clean = appName.lowercase().trim()

        val aliases = mapOf(
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "browser" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "gmail" to "com.google.android.gm",
            "settings" to "com.android.settings",
            "camera" to "com.google.android.GoogleCamera",
            "play store" to "com.android.vending",
            "whatsapp" to "com.whatsapp",
            "calculator" to "com.google.android.calculator",
            "clock" to "com.google.android.deskclock"
        )

        val targetPackage = aliases[clean] ?: clean
        val launchIntent = pm.getLaunchIntentForPackage(targetPackage)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(launchIntent)
                return AssistantActionResult(
                    success = true,
                    message = "Opened application '$appName'",
                    actionType = "open"
                )
            } catch (e: Exception) {
                return AssistantActionResult(
                    success = false,
                    message = "Failed to launch '$appName': ${e.message}",
                    actionType = "open"
                )
            }
        }

        // Search launcher intent
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = pm.queryIntentActivities(mainIntent, 0)
            for (appInfo in apps) {
                val label = appInfo.loadLabel(pm).toString().lowercase()
                if (label.contains(clean) || clean.contains(label)) {
                    val intent = pm.getLaunchIntentForPackage(appInfo.activityInfo.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
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
            message = "Application '$appName' not found",
            actionType = "open"
        )
    }

    /**
     * Checks if the Accessibility Service is enabled in Android system settings.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val expectedPackage = context.packageName

        for (service in enabledServices) {
            val resolveInfo = service.resolveInfo ?: continue
            val serviceInfo = resolveInfo.serviceInfo ?: continue
            if (serviceInfo.packageName == expectedPackage &&
                serviceInfo.name.contains("VoiceAssistantAccessibilityService")) {
                return true
            }
        }
        return false
    }

    /**
     * Navigates the user directly to the Android Accessibility settings page.
     */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
