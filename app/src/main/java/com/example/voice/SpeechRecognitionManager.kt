package com.example.voice

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared singleton manager that arbitrates access to Android's SpeechRecognizer.
 * Since only one SpeechRecognizer can be active simultaneously, this manager ensures:
 * 1. Mutual exclusion between Foreground Manual Mic (VoiceToTextViewModel) and
 *    Background WakeWordService ("Hey Mimi" detector).
 * 2. Pause/Resume coordination so the background wake word service pauses and destroys
 *    its recognizer whenever manual speech recognition begins, and resumes after it finishes.
 */
object SpeechRecognitionManager {

    private const val TAG = "SpeechRecognitionMgr"

    enum class ActiveOwner {
        NONE,
        MANUAL_MIC,
        WAKE_WORD_BACKGROUND
    }

    private val isManualMicActive = AtomicBoolean(false)
    private val isWakeWordPausedForManual = AtomicBoolean(false)

    private val _currentOwner = MutableStateFlow(ActiveOwner.NONE)
    val currentOwner: StateFlow<ActiveOwner> = _currentOwner.asStateFlow()

    // Callback hook invoked to command WakeWordService/Detector to pause its recognizer
    @Volatile
    private var wakeWordPauseCallback: (() -> Unit)? = null

    // Callback hook invoked to command WakeWordService/Detector to resume its recognizer
    @Volatile
    private var wakeWordResumeCallback: (() -> Unit)? = null

    /**
     * Registers control callbacks from WakeWordService / WakeWordDetector.
     */
    fun registerWakeWordHooks(onPause: () -> Unit, onResume: () -> Unit) {
        wakeWordPauseCallback = onPause
        wakeWordResumeCallback = onResume
    }

    fun unregisterWakeWordHooks() {
        wakeWordPauseCallback = null
        wakeWordResumeCallback = null
    }

    /**
     * Called before starting manual foreground speech recognition.
     * Pauses the wake word detector (destroying its SpeechRecognizer) to free the mic.
     */
    fun requestManualRecognitionStart(): Boolean {
        synchronized(this) {
            Log.d(TAG, "Requesting manual mic recognition. Pausing background wake word detector...")
            isManualMicActive.set(true)
            _currentOwner.value = ActiveOwner.MANUAL_MIC

            try {
                wakeWordPauseCallback?.invoke()
                isWakeWordPausedForManual.set(true)
            } catch (e: Exception) {
                Log.e(TAG, "Error invoking wake word pause callback: ${e.message}")
            }
            return true
        }
    }

    /**
     * Called when manual foreground recognition finishes (results, error, or manual stop).
     * Resumes the background wake word detector if it was previously paused.
     */
    fun onManualRecognitionFinished() {
        synchronized(this) {
            if (!isManualMicActive.getAndSet(false)) return
            Log.d(TAG, "Manual mic recognition finished. Resuming background wake word detector...")

            if (_currentOwner.value == ActiveOwner.MANUAL_MIC) {
                _currentOwner.value = ActiveOwner.NONE
            }

            if (isWakeWordPausedForManual.getAndSet(false)) {
                try {
                    wakeWordResumeCallback?.invoke()
                    _currentOwner.value = ActiveOwner.WAKE_WORD_BACKGROUND
                } catch (e: Exception) {
                    Log.e(TAG, "Error invoking wake word resume callback: ${e.message}")
                }
            }
        }
    }

    /**
     * Checks whether manual microphone is currently running.
     */
    fun isManualActive(): Boolean = isManualMicActive.get()

    /**
     * Notifies that the background wake word detector acquired the mic.
     */
    fun notifyWakeWordListeningStarted() {
        if (!isManualMicActive.get()) {
            _currentOwner.value = ActiveOwner.WAKE_WORD_BACKGROUND
        }
    }

    /**
     * Notifies that the background wake word detector released the mic.
     */
    fun notifyWakeWordListeningStopped() {
        if (_currentOwner.value == ActiveOwner.WAKE_WORD_BACKGROUND) {
            _currentOwner.value = ActiveOwner.NONE
        }
    }
}
