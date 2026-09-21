package com.example.voice

import com.squareup.moshi.JsonClass
import kotlin.math.abs
import kotlin.math.max

@JsonClass(generateAdapter = true)
data class VoiceSampleFeatures(
    val pitchHz: Float,
    val spectralCentroid: Float,
    val lowBandEnergyRatio: Float,
    val midBandEnergyRatio: Float,
    val highBandEnergyRatio: Float,
    val zeroCrossingRate: Float,
    val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class VoiceProfile(
    val isEnrolled: Boolean = false,
    val sampleCount: Int = 0,
    val samples: List<VoiceSampleFeatures> = emptyList(),
    val averagePitchHz: Float = 0f,
    val averageCentroid: Float = 0f,
    val averageLowRatio: Float = 0f,
    val averageMidRatio: Float = 0f,
    val averageHighRatio: Float = 0f,
    val averageZcr: Float = 0f,
    val enrolledAt: Long = 0L,
    val matchThresholdPercent: Float = 60.0f // Required similarity to accept
) {
    fun match(features: VoiceSampleFeatures): VoiceMatchResult {
        if (!isEnrolled || averagePitchHz <= 0f) {
            return VoiceMatchResult(
                isMatch = true,
                similarityPercent = 100f,
                explanation = "Profile not enrolled; accepting all voices"
            )
        }

        // 1. Pitch comparison (weight: 45%)
        val pitchDiff = abs(features.pitchHz - averagePitchHz)
        val pitchScore = max(0f, 100f - (pitchDiff / averagePitchHz) * 160f)

        // 2. Spectral Centroid comparison (weight: 25%)
        val centroidDiff = abs(features.spectralCentroid - averageCentroid)
        val centroidScore = max(0f, 100f - (centroidDiff / max(100f, averageCentroid)) * 140f)

        // 3. Timbre / Energy ratios comparison (weight: 20%)
        val lowDiff = abs(features.lowBandEnergyRatio - averageLowRatio)
        val midDiff = abs(features.midBandEnergyRatio - averageMidRatio)
        val timbreScore = max(0f, 100f - (lowDiff + midDiff) * 150f)

        // 4. Zero Crossing Rate comparison (weight: 10%)
        val zcrDiff = abs(features.zeroCrossingRate - averageZcr)
        val zcrScore = max(0f, 100f - (zcrDiff / max(10f, averageZcr)) * 100f)

        val totalSimilarity = (pitchScore * 0.45f) +
                (centroidScore * 0.25f) +
                (timbreScore * 0.20f) +
                (zcrScore * 0.10f)

        val isMatch = totalSimilarity >= matchThresholdPercent

        val explanation = if (isMatch) {
            "Voice verified as owner (Match: ${totalSimilarity.toInt()}%)"
        } else {
            "Voice profile mismatch (Similarity: ${totalSimilarity.toInt()}% < ${matchThresholdPercent.toInt()}% threshold)"
        }

        return VoiceMatchResult(
            isMatch = isMatch,
            similarityPercent = totalSimilarity.coerceIn(0f, 100f),
            pitchHz = features.pitchHz,
            expectedPitchHz = averagePitchHz,
            explanation = explanation
        )
    }
}

data class VoiceMatchResult(
    val isMatch: Boolean,
    val similarityPercent: Float,
    val pitchHz: Float = 0f,
    val expectedPitchHz: Float = 0f,
    val explanation: String
)
