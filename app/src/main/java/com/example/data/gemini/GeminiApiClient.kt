package com.example.data.gemini

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface GeminiApiService {
    // Using recommended gemini-3.5-flash
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiApiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    suspend fun askAssistant(userPrompt: String, isBengali: Boolean): Result<String> {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return Result.failure(
                IllegalStateException("Gemini API Key is not configured. Please add your key in AI Studio Secrets panel.")
            )
        }

        val systemInstructionText = """
            You are Mimi. Do not output conversational text. Output ONLY JSON commands formatted for my AccessibilityService to execute (like opening specific settings, turning on flashlight, or playing a specific YT video).

            Format all output strictly as a single JSON object. Do not enclose in markdown ticks, and do not include any conversational sentences or greetings.

            Command Formats:
            1. Opening specific device settings:
               {"action": "open_settings", "setting": "wifi"}
               Available settings include: "wifi", "bluetooth", "display", "sound", "battery", "location", "accessibility", "apps", "airplane_mode", "date", "storage"

            2. Controlling flashlight / torch:
               {"action": "flashlight", "state": "on"}
               {"action": "flashlight", "state": "off"}

            3. Playing a specific YouTube video:
               {"action": "play_youtube", "query": "never gonna give you up"}

            4. Opening an application:
               {"action": "open", "app": "youtube"}
               (e.g., "youtube", "chrome", "settings", "camera", "maps", "gmail", "whatsapp", "calculator", "clock", "spotify")

            5. Clicking on-screen elements or text:
               {"action": "click", "text": "Search"}

            6. Global navigation gestures:
               {"action": "home"}
               {"action": "back"}
               {"action": "recents"}
               {"action": "notifications"}
               {"action": "scroll_forward"}
               {"action": "scroll_backward"}
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                ContentItem(
                    parts = listOf(PartItem(text = userPrompt)),
                    role = "user"
                )
            ),
            systemInstruction = ContentItem(
                parts = listOf(PartItem(text = systemInstructionText))
            ),
            generationConfig = GenerationConfigItem(
                responseMimeType = "application/json",
                temperature = 0.1f
            )
        )

        return try {
            val response = service.generateContent(apiKey = apiKey, request = request)
            val candidateText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!candidateText.isNullOrBlank()) {
                Result.success(candidateText.trim())
            } else if (response.error?.message != null) {
                Result.failure(Exception(response.error.message))
            } else {
                Result.failure(Exception("Empty response received from Gemini"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
