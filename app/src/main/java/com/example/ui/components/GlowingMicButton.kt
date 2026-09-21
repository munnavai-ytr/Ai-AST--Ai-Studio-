package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RecordingGlow
import com.example.ui.theme.RecordingPink

@Composable
fun GlowingMicButton(
    isListening: Boolean,
    isSpeaking: Boolean,
    soundLevel: Float, // 0.0 to 1.0
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 96.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_glow")

    // Breathing pulse for idle
    val idlePulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_pulse"
    )

    val idleGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_glow_alpha"
    )

    // Fast ripple for active listening
    val wave1Progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1"
    )

    val wave2Progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, delayMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2"
    )

    // Dynamic scale responding to audio level
    val audioScale = remember { Animatable(1f) }
    LaunchedEffect(soundLevel, isListening) {
        if (isListening) {
            val target = 1f + (soundLevel * 0.25f).coerceIn(0f, 0.35f)
            audioScale.animateTo(target, animationSpec = tween(80))
        } else {
            audioScale.animateTo(1f, animationSpec = tween(250))
        }
    }

    val primaryGlowColor = if (isSpeaking) RecordingPink else if (isListening) NeonCyan else NeonCyan
    val secondaryGlowColor = if (isSpeaking) RecordingGlow else if (isListening) CyanGlow else CyanGlow

    Box(
        modifier = modifier
            .size(buttonSize * 2.2f)
            .testTag("mic_button_container"),
        contentAlignment = Alignment.Center
    ) {
        // Outer soundwave canvas
        Canvas(modifier = Modifier.size(buttonSize * 2.1f)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (buttonSize.toPx() / 2f)

            if (isListening) {
                // Wave 1
                val r1 = baseRadius + (size.width / 2f - baseRadius) * wave1Progress
                val a1 = (1f - wave1Progress) * 0.7f
                drawCircle(
                    color = primaryGlowColor.copy(alpha = a1),
                    radius = r1,
                    center = center,
                    style = Stroke(width = (4f * (1f - wave1Progress) + 1f).coerceAtLeast(1f))
                )

                // Wave 2
                val r2 = baseRadius + (size.width / 2f - baseRadius) * wave2Progress
                val a2 = (1f - wave2Progress) * 0.7f
                drawCircle(
                    color = secondaryGlowColor.copy(alpha = a2),
                    radius = r2,
                    center = center,
                    style = Stroke(width = (4f * (1f - wave2Progress) + 1f).coerceAtLeast(1f))
                )

                // Audio reactive inner aura
                val audioAuraRadius = baseRadius * (1f + soundLevel * 0.45f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryGlowColor.copy(alpha = 0.45f + soundLevel * 0.35f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = audioAuraRadius
                    ),
                    radius = audioAuraRadius,
                    center = center
                )
            } else {
                // Idle glowing halo
                val haloRadius = baseRadius * idlePulseScale
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NeonCyan.copy(alpha = idleGlowAlpha * 0.5f),
                            ElectricViolet.copy(alpha = idleGlowAlpha * 0.2f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = haloRadius * 1.3f
                    ),
                    radius = haloRadius * 1.3f,
                    center = center
                )
            }
        }

        // Main circular button
        val interactionSource = remember { MutableInteractionSource() }

        Box(
            modifier = Modifier
                .size(buttonSize)
                .scale(if (isListening) audioScale.value else 1f)
                .shadow(
                    elevation = if (isListening) 24.dp else 12.dp,
                    shape = CircleShape,
                    ambientColor = primaryGlowColor,
                    spotColor = primaryGlowColor
                )
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = if (isSpeaking) {
                            listOf(Color(0xFF8A0027), Color(0xFFFF2A6D))
                        } else if (isListening) {
                            listOf(Color(0xFF003D4D), Color(0xFF00B4D8), Color(0xFF00E5FF))
                        } else {
                            listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF0D1B2A))
                        }
                    )
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, color = primaryGlowColor),
                    onClick = onClick
                )
                .testTag("mic_button"),
            contentAlignment = Alignment.Center
        ) {
            // Neon border ring
            Canvas(modifier = Modifier.size(buttonSize)) {
                val strokeWidth = if (isListening) 3.5.dp.toPx() else 2.dp.toPx()
                val borderColor = if (isSpeaking) {
                    RecordingPink
                } else if (isListening) {
                    NeonCyan
                } else {
                    NeonCyan.copy(alpha = 0.5f)
                }
                drawCircle(
                    color = borderColor,
                    radius = (size.width / 2f) - (strokeWidth / 2f),
                    style = Stroke(width = strokeWidth)
                )
            }

            // Icon
            Icon(
                imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isListening) "Stop listening" else "Start listening",
                tint = if (isSpeaking) Color.White else if (isListening) Color.White else NeonCyan,
                modifier = Modifier.size(buttonSize * 0.44f)
            )
        }
    }
}
