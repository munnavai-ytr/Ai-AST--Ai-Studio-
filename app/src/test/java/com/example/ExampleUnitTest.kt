package com.example

import com.example.service.WakeWordStateManager
import com.example.voice.AcousticFeatureExtractor
import com.example.voice.VoiceProfile
import com.example.voice.VoiceSampleFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class ExampleUnitTest {

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testRmsCalculation() {
        val silentSamples = ShortArray(1600) { 0 }
        val silentRms = AcousticFeatureExtractor.calculateRms(silentSamples)
        assertEquals(0f, silentRms, 0.001f)

        val loudSamples = ShortArray(1000) { 10000 }
        val loudRms = AcousticFeatureExtractor.calculateRms(loudSamples)
        assertEquals(10000f, loudRms, 1.0f)
    }

    @Test
    fun testSyntheticTonePitchExtraction() {
        val sampleRate = 16000
        val targetFreq = 200.0 // 200 Hz
        val durationSamples = (sampleRate * 0.5).toInt() // 500 ms
        val samples = ShortArray(durationSamples) { i ->
            (sin(2.0 * PI * targetFreq * i / sampleRate) * 16000).toInt().toShort()
        }

        val features = AcousticFeatureExtractor.extractFeatures(samples)
        assertNotNull(features)
        // Check pitch is near 200 Hz
        assertEquals(200f, features!!.pitchHz, 25f)
    }

    @Test
    fun testVoiceProfileMatchesOwnerAndRejectsOtherVoices() {
        // Enrolled user with average pitch 190 Hz
        val sample1 = VoiceSampleFeatures(
            pitchHz = 190f,
            spectralCentroid = 950f,
            lowBandEnergyRatio = 0.45f,
            midBandEnergyRatio = 0.35f,
            highBandEnergyRatio = 0.20f,
            zeroCrossingRate = 45f
        )
        val sample2 = VoiceSampleFeatures(
            pitchHz = 185f,
            spectralCentroid = 920f,
            lowBandEnergyRatio = 0.44f,
            midBandEnergyRatio = 0.36f,
            highBandEnergyRatio = 0.20f,
            zeroCrossingRate = 42f
        )
        val sample3 = VoiceSampleFeatures(
            pitchHz = 195f,
            spectralCentroid = 980f,
            lowBandEnergyRatio = 0.46f,
            midBandEnergyRatio = 0.34f,
            highBandEnergyRatio = 0.20f,
            zeroCrossingRate = 47f
        )

        val profile = VoiceProfile(
            isEnrolled = true,
            sampleCount = 3,
            samples = listOf(sample1, sample2, sample3),
            averagePitchHz = 190f,
            averageCentroid = 950f,
            averageLowRatio = 0.45f,
            averageMidRatio = 0.35f,
            averageHighRatio = 0.20f,
            averageZcr = 45f,
            matchThresholdPercent = 60.0f
        )

        // Same user speaking "Hey Mimi" (pitch 188 Hz)
        val ownerCandidate = VoiceSampleFeatures(
            pitchHz = 188f,
            spectralCentroid = 960f,
            lowBandEnergyRatio = 0.45f,
            midBandEnergyRatio = 0.35f,
            highBandEnergyRatio = 0.20f,
            zeroCrossingRate = 44f
        )
        val ownerMatch = profile.match(ownerCandidate)
        assertTrue(ownerMatch.isMatch)
        assertTrue(ownerMatch.similarityPercent >= 80f)

        // Different person with deep voice (pitch 95 Hz, different timbre)
        val strangerCandidate = VoiceSampleFeatures(
            pitchHz = 95f,
            spectralCentroid = 450f,
            lowBandEnergyRatio = 0.70f,
            midBandEnergyRatio = 0.20f,
            highBandEnergyRatio = 0.10f,
            zeroCrossingRate = 18f
        )
        val strangerMatch = profile.match(strangerCandidate)
        assertFalse(strangerMatch.isMatch)
        assertTrue(strangerMatch.similarityPercent < 60f)
    }

    @Test
    fun testWakeWordStateManager() {
        WakeWordStateManager.setServiceRunning(true, "Listening for 'Hey Mimi'...")
        assertTrue(WakeWordStateManager.isServiceRunning.value)
        assertEquals("Listening for 'Hey Mimi'...", WakeWordStateManager.serviceStatusMessage.value)

        WakeWordStateManager.setServiceRunning(false)
        assertFalse(WakeWordStateManager.isServiceRunning.value)
    }
}
