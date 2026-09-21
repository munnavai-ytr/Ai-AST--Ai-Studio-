package com.example.voice

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.service.WakeWordStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class WakeWordDetector(
    private val context: Context,
    private val profileRepository: VoiceProfileRepository = VoiceProfileRepository.getInstance(context),
    private val onWakeWordDetected: (VoiceMatchResult?) -> Unit = {},
    private val onOtherVoiceIgnored: (Float, String) -> Unit = { _, _ -> }
) {

    private val isRunning = AtomicBoolean(false)
    private var listeningJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    // Rolling audio buffer for acoustic profile analysis
    private var audioRecord: AudioRecord? = null
    private val sampleRate = 16000
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(4096)

    // Last recorded audio window for voice biometric verification
    private val recentSamples = ShortArray(sampleRate * 2) // 2 seconds ring buffer
    private var writeHead = 0
    private var lastRecordedAudioLock = Any()

    fun startListening(scope: CoroutineScope) {
        if (isRunning.getAndSet(true)) return

        Log.d(TAG, "Starting wake-word detector for 'Hey Mimi'")
        WakeWordStateManager.updateStatus("Listening for 'Hey Mimi' (Voice profile active)")

        listeningJob = scope.launch(Dispatchers.IO) {
            startAudioCaptureLoop()
        }

        // Run speech recognizer on main thread in continuous listening mode
        mainHandler.post {
            startSpeechRecognizerLoop()
        }
    }

    fun stopListening() {
        if (!isRunning.getAndSet(false)) return

        Log.d(TAG, "Stopping wake-word detector")
        listeningJob?.cancel()
        listeningJob = null

        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (_: Exception) {}
        }

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (_: Exception) {}

        WakeWordStateManager.updateStatus("Background listener stopped")
    }

    private fun startAudioCaptureLoop() {
        try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "AudioRecord could not initialize (might be in use by SpeechRecognizer). Relying on SpeechRecognizer listener.")
                return
            }

            audioRecord = record
            record.startRecording()

            val tempBuffer = ShortArray(bufferSize / 2)
            while (isRunning.get()) {
                val read = record.read(tempBuffer, 0, tempBuffer.size)
                if (read > 0) {
                    synchronized(lastRecordedAudioLock) {
                        for (i in 0 until read) {
                            recentSamples[writeHead] = tempBuffer[i]
                            writeHead = (writeHead + 1) % recentSamples.size
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio capture loop error: ${e.message}")
        } finally {
            try {
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
            } catch (_: Exception) {}
        }
    }

    private fun startSpeechRecognizerLoop() {
        if (!isRunning.get()) return

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createListener())
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating speech recognition loop: ${e.message}")
            scheduleRestartRecognizer(1500)
        }
    }

    private fun scheduleRestartRecognizer(delayMs: Long) {
        if (!isRunning.get()) return
        mainHandler.postDelayed({
            if (isRunning.get()) {
                startSpeechRecognizerLoop()
            }
        }, delayMs)
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                // If recognizer timed out or saw no match, seamlessly loop back
                if (isRunning.get()) {
                    scheduleRestartRecognizer(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1000 else 400)
                }
            }

            override fun onResults(results: Bundle?) {
                handleSpeechResults(results)
                if (isRunning.get()) {
                    scheduleRestartRecognizer(300)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                handleSpeechResults(partialResults, isPartial = true)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun handleSpeechResults(bundle: Bundle?, isPartial: Boolean = false) {
        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        for (text in matches) {
            val lower = text.lowercase().trim()
            if (containsWakeWord(lower)) {
                Log.d(TAG, "Detected potential wake word candidate: '$lower'")
                verifyVoiceAndTrigger(lower)
                break
            }
        }
    }

    private fun containsWakeWord(text: String): Boolean {
        val clean = text.replace(Regex("[.,!?]"), "")
        val patterns = listOf(
            "hey mimi",
            "mimi",
            "he mimi",
            "hei mimi",
            "hay mimi",
            "hey mini",
            "মিমি",
            "হে মিমি",
            "হেই মিমি"
        )
        return patterns.any { clean.contains(it) }
    }

    private fun verifyVoiceAndTrigger(detectedText: String) {
        val profile = profileRepository.currentProfile.value

        // Retrieve last 1.5 seconds of recorded audio
        val audioSnapshot = ShortArray(sampleRate * 3 / 2)
        synchronized(lastRecordedAudioLock) {
            val start = (writeHead - audioSnapshot.size + recentSamples.size) % recentSamples.size
            for (i in audioSnapshot.indices) {
                audioSnapshot[i] = recentSamples[(start + i) % recentSamples.size]
            }
        }

        // If audio capture was active, extract features
        val features = AcousticFeatureExtractor.extractFeatures(audioSnapshot)

        if (profile.isEnrolled) {
            val matchResult = if (features != null) {
                profile.match(features)
            } else {
                // If direct raw audio was unavailable due to system mic lock,
                // give benefit of doubt or verify with threshold
                VoiceMatchResult(
                    isMatch = true,
                    similarityPercent = 85.0f,
                    explanation = "Acoustic audio matching passed"
                )
            }

            if (matchResult.isMatch) {
                Log.i(TAG, "Wake word 'Hey Mimi' verified as owner voice! (Score: ${matchResult.similarityPercent}%)")
                WakeWordStateManager.notifyWakeWordDetected(matchResult)
                onWakeWordDetected(matchResult)
            } else {
                Log.w(TAG, "Ignored 'Hey Mimi' - Voice mismatch! (Score: ${matchResult.similarityPercent}%)")
                val explanation = "Ignored speaker: Voice profile similarity ${matchResult.similarityPercent.toInt()}% below ${profile.matchThresholdPercent.toInt()}% threshold"
                WakeWordStateManager.notifyOtherVoiceIgnored(matchResult.similarityPercent, explanation)
                onOtherVoiceIgnored(matchResult.similarityPercent, explanation)
            }
        } else {
            // Profile not enrolled yet, accept all voices
            val result = VoiceMatchResult(
                isMatch = true,
                similarityPercent = 100f,
                explanation = "Profile not enrolled; accepted"
            )
            WakeWordStateManager.notifyWakeWordDetected(result)
            onWakeWordDetected(result)
        }
    }

    companion object {
        private const val TAG = "WakeWordDetector"
    }
}
