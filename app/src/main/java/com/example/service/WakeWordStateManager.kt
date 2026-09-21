package com.example.service

import com.example.voice.VoiceMatchResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class WakeWordEvent {
    data class Triggered(
        val timestamp: Long = System.currentTimeMillis(),
        val matchResult: VoiceMatchResult? = null
    ) : WakeWordEvent()

    data class OtherVoiceIgnored(
        val similarity: Float,
        val explanation: String
    ) : WakeWordEvent()
}

object WakeWordStateManager {

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _serviceStatusMessage = MutableStateFlow("Background listener inactive")
    val serviceStatusMessage: StateFlow<String> = _serviceStatusMessage.asStateFlow()

    private val _lastWakeEvent = MutableSharedFlow<WakeWordEvent>(extraBufferCapacity = 5)
    val lastWakeEvent: SharedFlow<WakeWordEvent> = _lastWakeEvent.asSharedFlow()

    private val _lastVoiceMatch = MutableStateFlow<VoiceMatchResult?>(null)
    val lastVoiceMatch: StateFlow<VoiceMatchResult?> = _lastVoiceMatch.asStateFlow()

    fun setServiceRunning(running: Boolean, status: String = "") {
        _isServiceRunning.value = running
        if (status.isNotBlank()) {
            _serviceStatusMessage.value = status
        } else {
            _serviceStatusMessage.value = if (running) "Listening for 'Hey Mimi'..." else "Background listener inactive"
        }
    }

    fun updateStatus(status: String) {
        _serviceStatusMessage.value = status
    }

    fun notifyWakeWordDetected(matchResult: VoiceMatchResult?) {
        _lastVoiceMatch.value = matchResult
        _lastWakeEvent.tryEmit(WakeWordEvent.Triggered(matchResult = matchResult))
    }

    fun notifyOtherVoiceIgnored(similarity: Float, explanation: String) {
        val result = VoiceMatchResult(
            isMatch = false,
            similarityPercent = similarity,
            explanation = explanation
        )
        _lastVoiceMatch.value = result
        _lastWakeEvent.tryEmit(WakeWordEvent.OtherVoiceIgnored(similarity, explanation))
    }
}
