package com.example.data.gemini

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<ContentItem>,
    val systemInstruction: ContentItem? = null
)

@JsonClass(generateAdapter = true)
data class ContentItem(
    val parts: List<PartItem>,
    val role: String? = null
)

@JsonClass(generateAdapter = true)
data class PartItem(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<CandidateItem>? = null,
    val error: GeminiErrorDetails? = null
)

@JsonClass(generateAdapter = true)
data class CandidateItem(
    val content: ContentItem? = null,
    val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiErrorDetails(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)
