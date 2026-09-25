package com.example.data.groq

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GroqChatRequest(
    val model: String = "openai/gpt-oss-120b",
    val messages: List<GroqMessage>,
    val temperature: Float? = 0.1f,
    val response_format: GroqResponseFormat? = GroqResponseFormat("json_object")
)

@JsonClass(generateAdapter = true)
data class GroqResponseFormat(
    val type: String = "json_object"
)

@JsonClass(generateAdapter = true)
data class GroqMessage(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class GroqChatResponse(
    val choices: List<GroqChoice>? = null,
    val error: GroqErrorDetails? = null
)

@JsonClass(generateAdapter = true)
data class GroqChoice(
    val message: GroqMessage? = null,
    val finish_reason: String? = null
)

@JsonClass(generateAdapter = true)
data class GroqErrorDetails(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)

interface GroqApiService {
    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: GroqChatRequest
    ): GroqChatResponse
}

object GroqApiClient {
    private const val TAG = "GroqApiClient"
    private const val BASE_URL = "https://api.groq.com/openai/v1/"

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
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    val service: GroqApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GroqApiService::class.java)
    }

    suspend fun askAssistant(userPrompt: String, isBengali: Boolean): Result<String> {
        val apiKey = BuildConfig.GROQ_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GROQ_API_KEY") {
            return Result.failure(
                IllegalStateException("Groq API Key is not configured. Please add your key in AI Studio Secrets panel.")
            )
        }

        val systemInstructionText = """
            You are Mimi, a highly intelligent, expert, and deeply loyal AI friend and assistant. You are not a robotic AI. Act like a helpful, witty human friend. Keep your verbal responses extremely natural, concise, and friendly (in Bengali or English). Your primary job is to execute device commands. Always return a JSON object containing your conversational 'reply' AND the 'actions' to execute. Example: { "reply": "দোস্ত, ইউটিউব ওপেন করে দিচ্ছি!", "actions": [ {"type": "OPEN_APP", "target": "com.google.android.youtube"} ] }

            Available action types for the "actions" array:
            1. {"type": "OPEN_APP", "target": "com.google.android.youtube"} (or app name/package: "youtube", "chrome", "settings", "camera", "maps", "whatsapp", "calculator", "clock", "spotify", "gmail")
            2. {"type": "CLICK", "text": "Search"} or {"type": "CLICK", "targetId": "view_id"}
            3. {"type": "GLOBAL_ACTION", "action": "HOME"} (or "BACK", "RECENTS", "NOTIFICATIONS")
            4. {"type": "SCROLL", "direction": "FORWARD"} (or "BACKWARD")
            5. {"type": "OPEN_SETTINGS", "setting": "wifi"} (or "bluetooth", "display", "sound", "battery", "location", "accessibility", "apps")
            6. {"type": "FLASHLIGHT", "state": "on"} (or "off")
            7. {"type": "PLAY_YOUTUBE", "query": "song name"}

            If the user is just chatting or asking a general question, return actions as an empty array: [].
            Always output ONLY raw valid JSON conforming to this schema without markdown code blocks.
        """.trimIndent()

        val request = GroqChatRequest(
            model = "openai/gpt-oss-120b",
            messages = listOf(
                GroqMessage(role = "system", content = systemInstructionText),
                GroqMessage(role = "user", content = userPrompt)
            ),
            temperature = 0.1f,
            response_format = GroqResponseFormat(type = "json_object")
        )

        var attempts = 0
        val maxAttempts = 2

        while (attempts < maxAttempts) {
            attempts++
            try {
                val response = service.createChatCompletion(
                    authorization = "Bearer $apiKey",
                    request = request
                )
                val candidateText = response.choices?.firstOrNull()?.message?.content
                if (!candidateText.isNullOrBlank()) {
                    return Result.success(candidateText.trim())
                } else if (response.error?.message != null) {
                    val errMsg = response.error.message
                    Log.e(TAG, "Groq response error: $errMsg")
                    return Result.failure(Exception(errMsg))
                } else {
                    return Result.failure(Exception("Empty response received from Groq"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Groq API call attempt $attempts failed", e)

                val isRetryableHttp = e is HttpException && (e.code() in listOf(500, 502, 503))
                if (attempts < maxAttempts && isRetryableHttp) {
                    Log.w(TAG, "Retrying Groq API call after 800ms due to HTTP ${ (e as HttpException).code() }...")
                    delay(800)
                    continue
                }

                val userFriendlyMessage = getUserFriendlyErrorMessage(e, isBengali)
                return Result.failure(Exception(userFriendlyMessage))
            }
        }

        return Result.failure(
            Exception(
                if (isBengali) "সার্ভার এই মুহূর্তে ব্যস্ত, একটু পর আবার চেষ্টা করছি..."
                else "Server is busy, retrying..."
            )
        )
    }

    private fun getUserFriendlyErrorMessage(e: Exception, isBengali: Boolean): String {
        return when (e) {
            is HttpException -> {
                when (e.code()) {
                    503, 502, 500 -> if (isBengali) "সার্ভার এই মুহূর্তে ব্যস্ত, একটু পর আবার চেষ্টা করছি..." else "Server is busy, retrying..."
                    429 -> if (isBengali) "অনুরোধের সীমা শেষ, কিছুক্ষণ অপেক্ষা করুন" else "Rate limit reached, please wait"
                    401, 403 -> if (isBengali) "API key সঠিক নয়, দয়া করে সেটিংস চেক করুন" else "Invalid API key, please check settings"
                    else -> if (isBengali) "অনুরোধটি সম্পন্ন করা যায়নি (কোড: ${e.code()})" else "Request failed (code: ${e.code()})"
                }
            }
            is SocketTimeoutException, is UnknownHostException -> {
                if (isBengali) "ইন্টারনেট সংযোগ পরীক্ষা করুন" else "Please check your internet connection"
            }
            is IOException -> {
                if (isBengali) "ইন্টারনেট সংযোগ পরীক্ষা করুন" else "Please check your internet connection"
            }
            else -> {
                e.message ?: if (isBengali) "অপ্রত্যাশিত ত্রুটি ঘটেছে" else "An unexpected error occurred"
            }
        }
    }
}
