package com.example.voice

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VoiceProfileRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val profileAdapter = moshi.adapter(VoiceProfile::class.java)

    private val _currentProfile = MutableStateFlow(loadProfile())
    val currentProfile: StateFlow<VoiceProfile> = _currentProfile.asStateFlow()

    private val _currentSamples = MutableStateFlow<List<VoiceSampleFeatures>>(emptyList())
    val currentSamples: StateFlow<List<VoiceSampleFeatures>> = _currentSamples.asStateFlow()

    private fun loadProfile(): VoiceProfile {
        val json = prefs.getString(KEY_PROFILE, null) ?: return VoiceProfile()
        return try {
            profileAdapter.fromJson(json) ?: VoiceProfile()
        } catch (_: Exception) {
            VoiceProfile()
        }
    }

    fun saveProfile(profile: VoiceProfile) {
        val json = profileAdapter.toJson(profile)
        prefs.edit().putString(KEY_PROFILE, json).apply()
        _currentProfile.value = profile
    }

    fun addSample(features: VoiceSampleFeatures): List<VoiceSampleFeatures> {
        val updated = _currentSamples.value + features
        _currentSamples.value = updated

        if (updated.size >= 3) {
            // Automatically compile profile when 3 recordings are reached
            val avgPitch = updated.map { it.pitchHz }.average().toFloat()
            val avgCentroid = updated.map { it.spectralCentroid }.average().toFloat()
            val avgLow = updated.map { it.lowBandEnergyRatio }.average().toFloat()
            val avgMid = updated.map { it.midBandEnergyRatio }.average().toFloat()
            val avgHigh = updated.map { it.highBandEnergyRatio }.average().toFloat()
            val avgZcr = updated.map { it.zeroCrossingRate }.average().toFloat()

            val newProfile = VoiceProfile(
                isEnrolled = true,
                sampleCount = updated.size,
                samples = updated,
                averagePitchHz = avgPitch,
                averageCentroid = avgCentroid,
                averageLowRatio = avgLow,
                averageMidRatio = avgMid,
                averageHighRatio = avgHigh,
                averageZcr = avgZcr,
                enrolledAt = System.currentTimeMillis(),
                matchThresholdPercent = 60.0f
            )
            saveProfile(newProfile)
        }
        return updated
    }

    fun clearTemporarySamples() {
        _currentSamples.value = emptyList()
    }

    fun resetProfile() {
        prefs.edit().remove(KEY_PROFILE).apply()
        _currentSamples.value = emptyList()
        _currentProfile.value = VoiceProfile()
    }

    fun updateThreshold(thresholdPercent: Float) {
        val current = _currentProfile.value
        val updated = current.copy(matchThresholdPercent = thresholdPercent.coerceIn(40f, 90f))
        saveProfile(updated)
    }

    companion object {
        private const val PREFS_NAME = "mimi_voice_profile_prefs"
        private const val KEY_PROFILE = "voice_profile_json"

        @Volatile
        private var INSTANCE: VoiceProfileRepository? = null

        fun getInstance(context: Context): VoiceProfileRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VoiceProfileRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
