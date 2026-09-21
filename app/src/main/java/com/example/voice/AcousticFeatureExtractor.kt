package com.example.voice

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object AcousticFeatureExtractor {

    const val SAMPLE_RATE = 16000 // 16kHz
    private const val MIN_PITCH_HZ = 70f
    private const val MAX_PITCH_HZ = 400f
    private const val MIN_LAG = (SAMPLE_RATE / MAX_PITCH_HZ).toInt() // 40
    private const val MAX_LAG = (SAMPLE_RATE / MIN_PITCH_HZ).toInt() // 228

    /**
     * Compute RMS (Root Mean Square) energy of raw PCM 16-bit audio samples.
     */
    fun calculateRms(samples: ShortArray, offset: Int = 0, length: Int = samples.size): Float {
        if (length <= 0) return 0f
        var sumSquares = 0.0
        val end = (offset + length).coerceAtMost(samples.size)
        for (i in offset until end) {
            val s = samples[i].toDouble()
            sumSquares += s * s
        }
        return sqrt(sumSquares / length).toFloat()
    }

    /**
     * Extracts acoustic features from a spoken segment of PCM 16-bit 16kHz audio.
     */
    fun extractFeatures(samples: ShortArray): VoiceSampleFeatures? {
        if (samples.size < 1600) return null // Need at least 100ms of audio

        // Convert to normalized floats (-1.0 to 1.0)
        val floatSamples = FloatArray(samples.size) { samples[it] / 32768.0f }

        // Find active speech segment (exclude leading/trailing silence)
        val windowSize = 512
        var startIdx = 0
        var endIdx = floatSamples.size - 1

        val frameRms = mutableListOf<Float>()
        for (i in 0 until floatSamples.size - windowSize step (windowSize / 2)) {
            var sum = 0f
            for (j in 0 until windowSize) {
                val v = floatSamples[i + j]
                sum += v * v
            }
            frameRms.add(sqrt(sum / windowSize))
        }

        val maxRms = frameRms.maxOrNull() ?: 0f
        if (maxRms < 0.02f) {
            // Audio too quiet or silence
            return null
        }

        val threshold = maxRms * 0.25f
        for (i in frameRms.indices) {
            if (frameRms[i] >= threshold) {
                startIdx = i * (windowSize / 2)
                break
            }
        }
        for (i in frameRms.indices.reversed()) {
            if (frameRms[i] >= threshold) {
                endIdx = ((i + 1) * (windowSize / 2) + windowSize).coerceAtMost(floatSamples.size - 1)
                break
            }
        }

        val speechLength = endIdx - startIdx
        if (speechLength < 1600) return null // Less than 100ms speech

        // 1. Calculate Pitch (F0) using autocorrelation across voiced frames
        val pitchValues = mutableListOf<Float>()
        val pitchWindow = 1024
        val pitchHop = 512

        var cur = startIdx
        while (cur + pitchWindow <= endIdx) {
            val framePitch = estimatePitchAutocorrelation(floatSamples, cur, pitchWindow)
            if (framePitch in MIN_PITCH_HZ..MAX_PITCH_HZ) {
                pitchValues.add(framePitch)
            }
            cur += pitchHop
        }

        val avgPitch = if (pitchValues.isNotEmpty()) {
            // Use median or trimmed mean to reduce outlier influence
            pitchValues.sorted().let { sorted ->
                val trim = (sorted.size * 0.15f).toInt()
                val sub = sorted.subList(trim, sorted.size - trim)
                if (sub.isNotEmpty()) sub.average().toFloat() else sorted.average().toFloat()
            }
        } else {
            140.0f // Neutral default pitch if not explicitly extracted
        }

        // 2. Zero Crossing Rate (ZCR)
        var zeroCrossings = 0
        for (i in startIdx until endIdx - 1) {
            if ((floatSamples[i] >= 0 && floatSamples[i + 1] < 0) ||
                (floatSamples[i] < 0 && floatSamples[i + 1] >= 0)
            ) {
                zeroCrossings++
            }
        }
        val durationSeconds = speechLength.toFloat() / SAMPLE_RATE
        val zcr = zeroCrossings / durationSeconds

        // 3. Spectral Energy Bins (Low: 80-300Hz, Mid: 300-1200Hz, High: 1200-3500Hz)
        val (lowRatio, midRatio, highRatio, centroid) = computeSpectralBandsAndCentroid(
            floatSamples,
            startIdx,
            speechLength
        )

        return VoiceSampleFeatures(
            pitchHz = avgPitch,
            spectralCentroid = centroid,
            lowBandEnergyRatio = lowRatio,
            midBandEnergyRatio = midRatio,
            highBandEnergyRatio = highRatio,
            zeroCrossingRate = zcr
        )
    }

    /**
     * Autocorrelation pitch estimator for a given frame.
     */
    private fun estimatePitchAutocorrelation(samples: FloatArray, offset: Int, length: Int): Float {
        // Calculate energy of frame
        var energy0 = 0f
        for (i in 0 until length) {
            val s = samples[offset + i]
            energy0 += s * s
        }
        if (energy0 < 0.001f) return 0f

        var maxCorrelation = 0f
        var bestLag = 0

        for (lag in MIN_LAG..MAX_LAG) {
            var sum = 0f
            var lagEnergy = 0f
            for (i in 0 until length - lag) {
                val s1 = samples[offset + i]
                val s2 = samples[offset + i + lag]
                sum += s1 * s2
                lagEnergy += s2 * s2
            }

            val denom = sqrt(energy0 * lagEnergy)
            val normalizedCorrelation = if (denom > 1e-6f) sum / denom else 0f

            if (normalizedCorrelation > maxCorrelation) {
                maxCorrelation = normalizedCorrelation
                bestLag = lag
            }
        }

        // Peak threshold for voiced frames
        return if (maxCorrelation > 0.38f && bestLag > 0) {
            SAMPLE_RATE.toFloat() / bestLag
        } else {
            0f
        }
    }

    /**
     * Band-limited energy distribution and spectral centroid calculation using DFT filter banks.
     */
    private fun computeSpectralBandsAndCentroid(
        samples: FloatArray,
        offset: Int,
        length: Int
    ): SpectralFeatures {
        // Sample analysis up to 4096 samples around the center of speech
        val analysisLen = 2048.coerceAtMost(length)
        val center = offset + (length / 2) - (analysisLen / 2)
        val actualStart = center.coerceIn(offset, offset + length - analysisLen)

        // Evaluate frequencies from 100Hz to 3500Hz in steps of 50Hz (70 bins)
        var lowEnergy = 0f  // 80 - 300 Hz
        var midEnergy = 0f  // 300 - 1200 Hz
        var highEnergy = 0f // 1200 - 3500 Hz

        var weightedFreqSum = 0f
        var totalMagSum = 0f

        val stepHz = 50f
        var freq = 100f
        while (freq <= 3500f) {
            val omega = 2f * PI.toFloat() * freq / SAMPLE_RATE
            var real = 0f
            var imag = 0f

            // Windowed Goertzel/DFT sum
            for (n in 0 until analysisLen) {
                // Hamming window
                val window = 0.54f - 0.46f * cos(2f * PI.toFloat() * n / analysisLen)
                val s = samples[actualStart + n] * window
                real += s * cos(omega * n)
                imag -= s * sin(omega * n)
            }

            val mag = sqrt(real * real + imag * imag)

            weightedFreqSum += freq * mag
            totalMagSum += mag

            when {
                freq < 320f -> lowEnergy += mag
                freq in 320f..1200f -> midEnergy += mag
                else -> highEnergy += mag
            }

            freq += stepHz
        }

        val totalEnergy = (lowEnergy + midEnergy + highEnergy).coerceAtLeast(1e-5f)
        val lowRatio = lowEnergy / totalEnergy
        val midRatio = midEnergy / totalEnergy
        val highRatio = highEnergy / totalEnergy

        val centroid = if (totalMagSum > 1e-5f) weightedFreqSum / totalMagSum else 850f

        return SpectralFeatures(lowRatio, midRatio, highRatio, centroid)
    }

    private data class SpectralFeatures(
        val lowRatio: Float,
        val midRatio: Float,
        val highRatio: Float,
        val centroid: Float
    )
}
