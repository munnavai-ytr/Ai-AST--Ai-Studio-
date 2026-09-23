package com.example.tts

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Native Android Text-To-Speech (TTS) engine manager.
 * - Initialized with TextToSpeech.OnInitListener.
 * - Sets default language to Bengali (Locale("bn", "BD")) with fallback to system default / Locale.US.
 * - Supports speak(text, isBengali) using TextToSpeech.QUEUE_FLUSH.
 * - Lifecycle shutdown() method safely releases TTS resources to prevent memory leaks.
 */
class AndroidTtsManager(
    context: Context,
    private val onInitComplete: (Boolean) -> Unit = {}
) : TextToSpeech.OnInitListener {

    private val appContext: Context = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        tts = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            setupLanguage(isBengali = true)
            setupUtteranceListener()
            Log.d(TAG, "Android TTS initialized successfully")
            onInitComplete(true)
        } else {
            isInitialized = false
            Log.e(TAG, "Android TTS initialization failed with status: $status")
            onInitComplete(false)
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
                Log.w(TAG, "TTS utterance error: $errorCode for id: $utteranceId")
            }
        })
    }

    private fun setupLanguage(isBengali: Boolean): Boolean {
        val targetLocale = if (isBengali) {
            Locale.forLanguageTag("bn-BD")
        } else {
            Locale.US
        }

        var result = tts?.setLanguage(targetLocale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Locale $targetLocale not supported or missing data, falling back to Bengali (India)")
            result = tts?.setLanguage(Locale.forLanguageTag("bn-IN"))
        }

        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Bengali not supported or missing data, falling back to system default")
            val defaultLocale = Locale.getDefault()
            result = tts?.setLanguage(defaultLocale)
        }

        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "System default not supported, falling back to Locale.US")
            tts?.setLanguage(Locale.US)
        }

        return result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    /**
     * Speaks the conversational reply using TextToSpeech.QUEUE_FLUSH.
     * Allows Mimi to talk back naturally to the user while background actions execute simultaneously.
     */
    fun speak(text: String, isBengali: Boolean = true) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return

        if (!isInitialized || tts == null) {
            Log.w(TAG, "TTS engine not ready yet, skipping utterance: $cleanText")
            return
        }

        setupLanguage(isBengali)

        val utteranceId = "mimi_utterance_${System.currentTimeMillis()}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } else {
            @Suppress("DEPRECATION")
            val params = HashMap<String, String>().apply {
                put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            @Suppress("DEPRECATION")
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params)
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS: ${e.message}")
        }
        _isSpeaking.value = false
    }

    /**
     * Shuts down the Text-To-Speech engine to release native audio and service resources,
     * preventing any memory leaks when the service or activity is destroyed.
     */
    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS: ${e.message}")
        }
        isInitialized = false
        _isSpeaking.value = false
    }

    companion object {
        private const val TAG = "AndroidTtsManager"
    }
}
