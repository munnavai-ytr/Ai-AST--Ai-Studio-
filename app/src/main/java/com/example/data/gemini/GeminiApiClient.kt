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

        val systemInstructionText = if (isBengali) {
            "You are a helpful, courteous, and knowledgeable system voice assistant. The user is talking to you through voice recognition. Provide a clear, natural, concise, and direct response in Bengali (বাংলা) so that it can be read out loud easily using Text-to-Speech. Avoid heavy markdown symbols like asterisks or complex tables."
        } else {
            "You are a helpful, courteous, and knowledgeable system voice assistant. The user is talking to you through voice recognition. Provide a clear, natural, concise, and direct response in conversational English so that it can be read out loud easily using Text-to-Speech. Avoid heavy markdown symbols like asterisks or complex tables."
        }

        val request = GenerateContentRequest(
            contents = listOf(
                ContentItem(
                    parts = listOf(PartItem(text = userPrompt)),
                    role = "user"
                )
            ),
            systemInstruction = ContentItem(
                parts = listOf(PartItem(text = systemInstructionText))
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
