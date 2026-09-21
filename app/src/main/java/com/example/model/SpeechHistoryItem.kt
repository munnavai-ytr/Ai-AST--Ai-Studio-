package com.example.model

data class SpeechHistoryItem(
    val id: String = System.currentTimeMillis().toString(),
    val text: String,
    val language: SpeechLanguage,
    val timestamp: Long = System.currentTimeMillis()
)
