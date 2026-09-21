package com.example.viewmodel

import android.app.Application
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.service.WakeWordService
import com.example.voice.AcousticFeatureExtractor
import com.example.voice.VoiceMatchResult
import com.example.voice.VoiceProfile
import com.example.voice.VoiceProfileRepository
import com.example.voice.VoiceSampleFeatures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class VoiceProfileSetupState(
    val currentStep: Int = 1, // 1, 2, 3, or 4 (Completed)
    val isRecordingSample: Boolean = false,
    val recordingProgress: Float = 0f, // 0f to 1.0f
    val liveRms: Float = 0f,
    val recordedSamples: List<VoiceSampleFeatures> = emptyList(),
    val activeProfile: VoiceProfile = VoiceProfile(),
    val isTestingVoice: Boolean = false,
    val testMatchResult: VoiceMatchResult? = null,
    val statusMessage: String = "বলুন: 'Hey Mimi'",
    val errorMessage: String? = null
)

class VoiceProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VoiceProfileRepository.getInstance(application)
    private val _state = MutableStateFlow(
        VoiceProfileSetupState(
            activeProfile = repository.currentProfile.value,
            currentStep = if (repository.currentProfile.value.isEnrolled) 4 else 1,
            recordedSamples = repository.currentProfile.value.samples
        )
    )
    val state: StateFlow<VoiceProfileSetupState> = _state.asStateFlow()

    private var recordingJob: Job? = null
    private var testingJob: Job? = null

    init {
        viewModelScope.launch {
            repository.currentProfile.collect { profile ->
                _state.update {
                    it.copy(
                        activeProfile = profile,
                        currentStep = if (profile.isEnrolled) 4 else it.currentStep,
                        recordedSamples = if (profile.isEnrolled) profile.samples else it.recordedSamples
                    )
                }
            }
        }
    }

    /**
     * Record one sample of the user saying "Hey Mimi" (2 seconds).
     */
    fun recordSample() {
        if (_state.value.isRecordingSample) return

        recordingJob?.cancel()
        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            val sampleRate = 16000
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val totalDurationMs = 2200L
            val totalSamples = (sampleRate * (totalDurationMs / 1000f)).toInt()
            val audioBuffer = ShortArray(totalSamples)
            var samplesReadTotal = 0

            _state.update {
                it.copy(
                    isRecordingSample = true,
                    recordingProgress = 0f,
                    statusMessage = "রেকর্ড হচ্ছে... বলুন 'Hey Mimi'",
                    errorMessage = null
                )
            }

            var audioRecord: AudioRecord? = null
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    _state.update {
                        it.copy(
                            isRecordingSample = false,
                            errorMessage = "মাইক্রোফোন চালু করা যায়নি।"
                        )
                    }
                    return@launch
                }

                audioRecord.startRecording()
                val chunk = ShortArray(1024)
                val startTime = System.currentTimeMillis()

                while (isActive && samplesReadTotal < totalSamples) {
                    val read = audioRecord.read(chunk, 0, chunk.size)
                    if (read > 0) {
                        val toCopy = read.coerceAtMost(totalSamples - samplesReadTotal)
                        System.arraycopy(chunk, 0, audioBuffer, samplesReadTotal, toCopy)
                        samplesReadTotal += toCopy

                        val rms = AcousticFeatureExtractor.calculateRms(chunk, 0, read)
                        val elapsed = System.currentTimeMillis() - startTime
                        val progress = (elapsed / totalDurationMs.toFloat()).coerceIn(0f, 1f)

                        _state.update {
                            it.copy(
                                recordingProgress = progress,
                                liveRms = (rms / 3000f).coerceIn(0.1f, 1f)
                            )
                        }
                    }
                }

                audioRecord.stop()
                audioRecord.release()
                audioRecord = null

                // Extract features
                val features = AcousticFeatureExtractor.extractFeatures(audioBuffer)
                if (features != null) {
                    val updatedList = repository.addSample(features)
                    val nextStep = if (updatedList.size >= 3) 4 else updatedList.size + 1

                    _state.update {
                        it.copy(
                            isRecordingSample = false,
                            recordingProgress = 1f,
                            currentStep = nextStep,
                            recordedSamples = updatedList,
                            statusMessage = if (nextStep == 4) {
                                "ভয়েস প্রোফাইল সফলভাবে তৈরি হয়েছে!"
                            } else {
                                "নমুনা ${updatedList.size}/3 সম্পন্ন। পরবর্তী ধাপে ট্যাপ করুন।"
                            }
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isRecordingSample = false,
                            statusMessage = "শব্দ স্পষ্ট ছিল না, অনুগ্রহ করে আবার বলুন",
                            errorMessage = "কোনো স্পষ্ট কণ্ঠস্বর শনাক্ত হয়নি। আবার চেষ্টা করুন।"
                        )
                    }
                }

            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isRecordingSample = false,
                        errorMessage = e.localizedMessage ?: "রেকর্ডিংয়ে ত্রুটি হয়েছে"
                    )
                }
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Test current voice profile against incoming speech.
     */
    fun testVoiceVerification() {
        if (_state.value.isTestingVoice) {
            testingJob?.cancel()
            _state.update { it.copy(isTestingVoice = false) }
            return
        }

        testingJob = viewModelScope.launch(Dispatchers.IO) {
            val sampleRate = 16000
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val totalDurationMs = 2500L
            val totalSamples = (sampleRate * (totalDurationMs / 1000f)).toInt()
            val audioBuffer = ShortArray(totalSamples)
            var samplesReadTotal = 0

            _state.update {
                it.copy(
                    isTestingVoice = true,
                    statusMessage = "যেকোনো কাউকে কথা বলতে বলুন...",
                    errorMessage = null,
                    testMatchResult = null
                )
            }

            var audioRecord: AudioRecord? = null
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                audioRecord.startRecording()
                val chunk = ShortArray(1024)

                while (isActive && samplesReadTotal < totalSamples) {
                    val read = audioRecord.read(chunk, 0, chunk.size)
                    if (read > 0) {
                        val toCopy = read.coerceAtMost(totalSamples - samplesReadTotal)
                        System.arraycopy(chunk, 0, audioBuffer, samplesReadTotal, toCopy)
                        samplesReadTotal += toCopy
                    }
                }

                audioRecord.stop()
                audioRecord.release()
                audioRecord = null

                val features = AcousticFeatureExtractor.extractFeatures(audioBuffer)
                if (features != null) {
                    val matchResult = _state.value.activeProfile.match(features)
                    _state.update {
                        it.copy(
                            isTestingVoice = false,
                            testMatchResult = matchResult,
                            statusMessage = if (matchResult.isMatch) {
                                "প্রমাণিত! আপনার কণ্ঠস্বর শনাক্ত হয়েছে (${matchResult.similarityPercent.roundToInt()}%)"
                            } else {
                                "অন্য ব্যক্তির কণ্ঠস্বর উপেক্ষা করা হয়েছে (${matchResult.similarityPercent.roundToInt()}%)"
                            }
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isTestingVoice = false,
                            statusMessage = "পর্যাপ্ত কণ্ঠস্বর শোনা যায়নি, আবার চেষ্টা করুন"
                        )
                    }
                }

            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isTestingVoice = false,
                        errorMessage = e.localizedMessage ?: "পরীক্ষায় ত্রুটি হয়েছে"
                    )
                }
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (_: Exception) {}
            }
        }
    }

    fun resetProfile() {
        recordingJob?.cancel()
        testingJob?.cancel()
        repository.resetProfile()
        _state.update {
            VoiceProfileSetupState(
                currentStep = 1,
                statusMessage = "বলুন: 'Hey Mimi'",
                activeProfile = VoiceProfile()
            )
        }
    }

    fun updateThreshold(threshold: Float) {
        repository.updateThreshold(threshold)
    }

    override fun onCleared() {
        super.onCleared()
        recordingJob?.cancel()
        testingJob?.cancel()
    }
}
