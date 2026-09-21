package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RecordingPink

@Composable
fun SoundWaveVisualizer(
    isListening: Boolean,
    isSpeaking: Boolean,
    soundLevel: Float, // 0.0f to 1.0f
    modifier: Modifier = Modifier
) {
    val barCount = 13
    val infiniteTransition = rememberInfiniteTransition(label = "soundwave")

    val pulse1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse1"
    )

    val pulse2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, delayMillis = 100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse2"
    )

    val pulse3 by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(520, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse3"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val activeColor = if (isSpeaking) RecordingPink else NeonCyan

        for (i in 0 until barCount) {
            val distFromCenter = kotlin.math.abs(i - (barCount / 2)) / (barCount / 2f)
            val baseMultiplier = 1f - (distFromCenter * 0.45f)

            val animatedFactor = when (i % 3) {
                0 -> pulse1
                1 -> pulse2
                else -> pulse3
            }

            val targetHeightDp = if (isListening) {
                val dynamicLevel = (soundLevel * 1.5f).coerceIn(0.1f, 1.0f)
                val h = (8.dp + 32.dp * animatedFactor * dynamicLevel * baseMultiplier)
                h.coerceIn(6.dp, 40.dp)
            } else {
                5.dp
            }

            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(targetHeightDp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        if (isListening) {
                            if (i % 2 == 0) activeColor else ElectricViolet
                        } else {
                            NeonCyan.copy(alpha = 0.25f)
                        }
                    )
            )
        }
    }
}
