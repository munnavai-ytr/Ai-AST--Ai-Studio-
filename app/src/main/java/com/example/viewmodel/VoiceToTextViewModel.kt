package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.gemini.GeminiApiClient
import com.example.model.AssistantStatus
import com.example.model.SpeechHistoryItem
import com.example.model.SpeechLanguage
import com.example.tts.AndroidTtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SpeechUiState(
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val soundLevel: Float = 0f, // 0.0f to 1.0f for reactive glow
    val selectedLanguage: SpeechLanguage = SpeechLanguage.BENGALI,
    val fullText: String = "",
    val partialText: String = "",
    val statusText: String = "ট্যাপ করুন এবং কথা বলুন",
    val errorMessage: String? = null,
    val isAvailable: Boolean = true,
    val historyList: List<SpeechHistoryItem> = emptyList(),

    // Gemini & Assistant state
    val assistantStatus: AssistantStatus = AssistantStatus.IDLE,
    val geminiResponse: String? = null,
    val isTtsSpeaking: Boolean = false,
    val autoSendToGemini: Boolean = true,

    // Background Wake-Word & Voice Profile State
    val isBackgroundServiceActive: Boolean = false,
    val serviceStatusMessage: String = "Background listener inactive",
    val voiceProfile: com.example.voice.VoiceProfile = com.example.voice.VoiceProfile(),
    val lastWakeMatch: com.example.voice.VoiceMatchResult? = null
)

class VoiceToTextViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext
    private val profileRepository = com.example.voice.VoiceProfileRepository.getInstance(application)

    private val _uiState = MutableStateFlow(SpeechUiState())
    val uiState: StateFlow<SpeechUiState> = _uiState.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private val ttsManager = AndroidTtsManager(application)

    init {
        checkAvailability()

        // Observe TTS speaking state
        viewModelScope.launch {
            ttsManager.isSpeaking.collect { isSpeaking ->
                _uiState.update { state ->
                    state.copy(
                        isTtsSpeaking = isSpeaking,
                        assistantStatus = if (isSpeaking) AssistantStatus.SPEAKING
                        else if (state.assistantStatus == AssistantStatus.SPEAKING) AssistantStatus.IDLE
                        else state.assistantStatus
                    )
                }
            }
        }

        // Observe Voice Profile
        viewModelScope.launch {
            profileRepository.currentProfile.collect { profile ->
                _uiState.update { it.copy(voiceProfile = profile) }
            }
        }

        // Observe Background Service Running state
        viewModelScope.launch {
            com.example.service.WakeWordStateManager.isServiceRunning.collect { running ->
                _uiState.update { it.copy(isBackgroundServiceActive = running) }
            }
        }

        // Observe Background Service Status text
        viewModelScope.launch {
            com.example.service.WakeWordStateManager.serviceStatusMessage.collect { status ->
                _uiState.update { it.copy(serviceStatusMessage = status) }
            }
        }

        // Observe Wake-Word Events (WakeWordTriggered or OtherVoiceIgnored)
        viewModelScope.launch {
            com.example.service.WakeWordStateManager.lastWakeEvent.collect { event ->
                when (event) {
                    is com.example.service.WakeWordEvent.Triggered -> {
                        _uiState.update {
                            it.copy(
                                lastWakeMatch = event.matchResult,
                                statusText = if (it.selectedLanguage == SpeechLanguage.BENGALI) {
                                    "'Hey Mimi' শনাক্ত হয়েছে! বলুন..."
                                } else {
                                    "'Hey Mimi' detected! Listening..."
                                }
                            )
                        }
                        // Automatically activate speech recognition for user's prompt
                        startListening()
                    }
                    is com.example.service.WakeWordEvent.OtherVoiceIgnored -> {
                        _uiState.update {
                            it.copy(
                                lastWakeMatch = com.example.voice.VoiceMatchResult(
                                    isMatch = false,
                                    similarityPercent = event.similarity,
                                    explanation = event.explanation
                                ),
                                statusText = if (it.selectedLanguage == SpeechLanguage.BENGALI) {
                                    "অন্য ব্যক্তির কণ্ঠস্বর উপেক্ষা করা হয়েছে (${event.similarity.toInt()}%)"
                                } else {
                                    "Ignored other person's voice (${event.similarity.toInt()}%)"
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    fun toggleBackgroundService() {
        if (_uiState.value.isBackgroundServiceActive) {
            com.example.service.WakeWordService.stop(context)
        } else {
            com.example.service.WakeWordService.start(context)
        }
    }

    private fun checkAvailability() {
        val available = SpeechRecognizer.isRecognitionAvailable(context)
        _uiState.update {
            it.copy(
                isAvailable = available,
                statusText = if (available) {
                    if (it.selectedLanguage == SpeechLanguage.BENGALI) "ট্যাপ করুন এবং কথা বলুন"
                    else "Tap to speak"
                } else {
                    "Speech recognition not available on this device"
                }
            )
        }
    }

    fun selectLanguage(language: SpeechLanguage) {
        if (_uiState.value.selectedLanguage == language) return

        if (_uiState.value.isListening) {
            stopListening()
        }
        stopSpeakingTts()

        _uiState.update {
            it.copy(
                selectedLanguage = language,
                statusText = if (language == SpeechLanguage.BENGALI) "ট্যাপ করুন এবং কথা বলুন"
                else "Tap to speak"
            )
        }
    }

    fun toggleListening() {
        if (_uiState.value.isListening) {
            stopListening()
        } else {
            startListening()
        }
    }

    fun startListening() {
        // Stop TTS speech if running
        stopSpeakingTts()

        viewModelScope.launch(Dispatchers.Main) {
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(createRecognitionListener())
                    }
                }

                val currentLang = _uiState.value.selectedLanguage
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLang.localeTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLang.localeTag)
                    putExtra(
                        RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE,
                        currentLang.localeTag
                    )
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra("android.speech.extra.DICTATION_MODE", true)
                }

                _uiState.update {
                    it.copy(
                        isListening = true,
                        isSpeaking = false,
                        soundLevel = 0.2f,
                        partialText = "",
                        errorMessage = null,
                        statusText = if (currentLang == SpeechLanguage.BENGALI) "শুনছি... বলুন" else "Listening... speak now"
                    )
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isListening = false,
                        soundLevel = 0f,
                        errorMessage = e.localizedMessage ?: "Failed to start speech recognition"
                    )
                }
            }
        }
    }

    fun stopListening() {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {
            }
            _uiState.update {
                it.copy(
                    isListening = false,
                    isSpeaking = false,
                    soundLevel = 0f,
                    statusText = if (it.selectedLanguage == SpeechLanguage.BENGALI) "ট্যাপ করুন এবং কথা বলুন"
                    else "Tap to speak"
                )
            }
        }
    }

    fun clearText() {
        stopSpeakingTts()
        _uiState.update {
            it.copy(
                fullText = "",
                partialText = "",
                geminiResponse = null,
                assistantStatus = AssistantStatus.IDLE,
                errorMessage = null
            )
        }
    }

    fun sendCurrentToGemini(textToSend: String? = null) {
        val targetText = textToSend ?: _uiState.value.fullText.trim()
        if (targetText.isBlank()) return

        stopSpeakingTts()

        val isBn = _uiState.value.selectedLanguage == SpeechLanguage.BENGALI

        _uiState.update {
            it.copy(
                assistantStatus = AssistantStatus.THINKING,
                statusText = if (isBn) "জেমিনি ভাবছে..." else "Gemini is thinking...",
                errorMessage = null
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val result = GeminiApiClient.askAssistant(targetText, isBengali = isBn)
            result.onSuccess { reply ->
                _uiState.update {
                    it.copy(
                        assistantStatus = AssistantStatus.IDLE,
                        geminiResponse = reply,
                        statusText = if (isBn) "সহকারী উত্তর তৈরি করেছে" else "Assistant responded"
                    )
                }
                // Speak out loud automatically using Android's Text-to-Speech (TTS)
                speakTts(reply)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        assistantStatus = AssistantStatus.ERROR,
                        errorMessage = err.localizedMessage ?: "Gemini API error",
                        statusText = if (isBn) "উত্তরে সমস্যা হয়েছে" else "Assistant error"
                    )
                }
            }
        }
    }

    fun speakTts(text: String? = null) {
        val textToSpeak = text ?: _uiState.value.geminiResponse ?: return
        if (textToSpeak.isBlank()) return

        val isBn = _uiState.value.selectedLanguage == SpeechLanguage.BENGALI
        ttsManager.speak(textToSpeak, isBengali = isBn)
    }

    fun stopSpeakingTts() {
        ttsManager.stop()
        _uiState.update {
            it.copy(
                isTtsSpeaking = false,
                assistantStatus = if (it.assistantStatus == AssistantStatus.SPEAKING) AssistantStatus.IDLE else it.assistantStatus
            )
        }
    }

    fun toggleAutoSend() {
        _uiState.update { it.copy(autoSendToGemini = !it.autoSendToGemini) }
    }

    fun appendText(newText: String) {
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return

        _uiState.update { state ->
            val updatedFull = if (state.fullText.isBlank()) {
                trimmed
            } else {
                "${state.fullText} $trimmed"
            }
            state.copy(
                fullText = updatedFull,
                partialText = ""
            )
        }
    }

    fun saveCurrentToHistory() {
        val currentText = _uiState.value.fullText.trim()
        if (currentText.isEmpty()) return

        val newItem = SpeechHistoryItem(
            text = currentText,
            language = _uiState.value.selectedLanguage
        )
        _uiState.update { state ->
            state.copy(
                historyList = listOf(newItem) + state.historyList.filterNot { it.text == currentText }
            )
        }
    }

    fun deleteHistoryItem(id: String) {
        _uiState.update { state ->
            state.copy(
                historyList = state.historyList.filterNot { it.id == id }
            )
        }
    }

    fun clearAllHistory() {
        _uiState.update { state ->
            state.copy(historyList = emptyList())
        }
    }

    fun restoreFromHistory(item: SpeechHistoryItem) {
        stopSpeakingTts()
        _uiState.update { state ->
            state.copy(
                fullText = item.text,
                selectedLanguage = item.language,
                geminiResponse = null
            )
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _uiState.update {
                    it.copy(
                        statusText = if (it.selectedLanguage == SpeechLanguage.BENGALI) "কথা বলুন..." else "Listening..."
                    )
                }
            }

            override fun onBeginningOfSpeech() {
                _uiState.update {
                    it.copy(
                        isSpeaking = true,
                        statusText = if (it.selectedLanguage == SpeechLanguage.BENGALI) "শব্দ শুনছি..." else "Detecting speech..."
                    )
                }
            }

            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.1f, 1.0f)
                _uiState.update { it.copy(soundLevel = normalized) }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _uiState.update {
                    it.copy(
                        isSpeaking = false,
                        soundLevel = 0.1f,
                        statusText = if (it.selectedLanguage == SpeechLanguage.BENGALI) "প্রক্রিয়াকরণ হচ্ছে..." else "Processing..."
                    )
                }
            }

            override fun onError(error: Int) {
                val isBn = _uiState.value.selectedLanguage == SpeechLanguage.BENGALI
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> if (isBn) "অডিও রেকর্ডিং সমস্যা" else "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> if (isBn) "ক্লায়েন্ট সমস্যা" else "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> if (isBn) "মাইক্রোফোনের অনুমতি প্রয়োজন" else "Microphone permission required"
                    SpeechRecognizer.ERROR_NETWORK -> if (isBn) "নেটওয়ার্ক সংযোগ চেক করুন" else "Network connection error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> if (isBn) "নেটওয়ার্ক সময়সীমা শেষ" else "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> if (isBn) "কথা বোঝা যায়নি, আবার বলুন" else "No match found, please speak again"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> if (isBn) "রিকগনাইজার ব্যস্ত" else "Recognition service busy"
                    SpeechRecognizer.ERROR_SERVER -> if (isBn) "সার্ভার সমস্যা" else "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> if (isBn) "কোনো কথা শোনা যায়নি" else "No speech detected"
                    else -> if (isBn) "সমস্যা হয়েছে ($error)" else "Recognition error ($error)"
                }

                _uiState.update {
                    it.copy(
                        isListening = false,
                        isSpeaking = false,
                        soundLevel = 0f,
                        statusText = if (isBn) "ট্যাপ করুন এবং আবার বলুন" else "Tap to speak again",
                        errorMessage = if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) message else null
                    )
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognized = matches?.firstOrNull() ?: ""

                if (recognized.isNotBlank()) {
                    appendText(recognized)
                    saveCurrentToHistory()

                    // Automatically trigger Gemini assistant if autoSend is enabled
                    if (_uiState.value.autoSendToGemini) {
                        sendCurrentToGemini(recognized)
                    }
                }

                _uiState.update {
                    it.copy(
                        isListening = false,
                        isSpeaking = false,
                        soundLevel = 0f,
                        partialText = "",
                        statusText = if (it.selectedLanguage == SpeechLanguage.BENGALI) "ট্যাপ করুন এবং বলুন" else "Tap to speak"
                    )
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedPartial = matches?.firstOrNull() ?: ""
                if (recognizedPartial.isNotBlank()) {
                    _uiState.update {
                        it.copy(partialText = recognizedPartial)
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}
        ttsManager.shutdown()
    }
}
