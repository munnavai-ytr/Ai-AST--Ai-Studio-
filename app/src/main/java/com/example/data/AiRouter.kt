package com.example.data

import com.example.data.gemini.GeminiApiClient
import com.example.data.groq.GroqApiClient

object AiRouter {
    val SIMPLE_KEYWORDS = listOf(
        "open",
        "close",
        "flashlight",
        "wifi",
        "খোলো",
        "বন্ধ",
        "অন",
        "অফ"
    )

    fun shouldRouteToGroq(userPrompt: String): Boolean {
        val trimmedPrompt = userPrompt.trim()
        val words = trimmedPrompt.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val isShortPrompt = words.size < 8

        val lowerPrompt = trimmedPrompt.lowercase()
        val hasSimpleKeyword = SIMPLE_KEYWORDS.any { lowerPrompt.contains(it) }

        return isShortPrompt || hasSimpleKeyword
    }

    suspend fun ask(userPrompt: String, isBengali: Boolean): Result<String> {
        return if (shouldRouteToGroq(userPrompt)) {
            val groqResult = GroqApiClient.askAssistant(userPrompt, isBengali)
            if (groqResult.isSuccess) {
                groqResult
            } else {
                // Automatic fallback to GeminiApiClient if Groq fails or API key is not set
                GeminiApiClient.askAssistant(userPrompt, isBengali)
            }
        } else {
            GeminiApiClient.askAssistant(userPrompt, isBengali)
        }
    }
}
