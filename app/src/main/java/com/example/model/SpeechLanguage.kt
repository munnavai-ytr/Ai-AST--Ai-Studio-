package com.example.model

enum class SpeechLanguage(
    val id: String,
    val localeTag: String,
    val labelNative: String,
    val labelEnglish: String,
    val promptPlaceholder: String
) {
    BENGALI(
        id = "bn",
        localeTag = "bn-BD",
        labelNative = "বাংলা",
        labelEnglish = "Bengali",
        promptPlaceholder = "এখানে আপনার মুখের কথা লেখা হবে... মাইক্রোফোন বাটনে ট্যাপ করে কথা বলুন।"
    ),
    ENGLISH(
        id = "en",
        localeTag = "en-US",
        labelNative = "English",
        labelEnglish = "English",
        promptPlaceholder = "Your spoken words will appear here... Tap the microphone button to start speaking."
    )
}
