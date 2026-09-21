package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RecordingPink
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.viewmodel.VoiceProfileViewModel
import kotlin.math.roundToInt

@Composable
fun VoiceProfileSetupScreen(
    onNavigateBack: () -> Unit,
    viewModel: VoiceProfileViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.testTag("setup_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "ভয়েস প্রোফাইল সেটআপ",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Voice Profile Enrollment (Hey Mimi)",
                    color = TextTertiary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Instructions Hero Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Brush.linearGradient(listOf(NeonCyan.copy(alpha = 0.4f), ElectricViolet.copy(alpha = 0.2f))), RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(NeonCyan, ElectricViolet))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = DarkBackground,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "অন্যের কণ্ঠস্বর উপেক্ষা করুন",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "আপনার কণ্ঠস্বর ৩ বার রেকর্ড করে প্রোফাইল তৈরি করুন। ফলে অন্য কেউ 'Hey Mimi' বললেও অ্যাপ সক্রিয় হবে না।",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3-Step Progress Indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (step in 1..3) {
                val isDone = state.recordedSamples.size >= step
                val isCurrent = state.currentStep == step && !isDone

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isDone -> SuccessGreen
                                    isCurrent -> NeonCyan
                                    else -> DarkSurfaceVariant
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = if (isCurrent) CyanGlow else DarkBorder,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDone) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = DarkBackground,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text(
                                text = "$step",
                                color = if (isCurrent) DarkBackground else TextTertiary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "নমুনা $step/3",
                        color = if (isCurrent) NeonCyan else TextTertiary,
                        fontSize = 11.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Central Recording Action Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (state.isRecordingSample) RecordingPink else DarkBorder,
                    shape = RoundedCornerShape(20.dp)
                ),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = state.statusMessage,
                    color = if (state.isRecordingSample) RecordingPink else TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Big Mic Button or Checkmark
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .background(
                            if (state.isRecordingSample) {
                                Brush.radialGradient(listOf(RecordingPink, DarkSurfaceVariant))
                            } else if (state.activeProfile.isEnrolled) {
                                Brush.radialGradient(listOf(SuccessGreen, DarkSurfaceVariant))
                            } else {
                                Brush.linearGradient(listOf(NeonCyan, ElectricViolet))
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.isRecordingSample) {
                        CircularProgressIndicator(
                            progress = { state.recordingProgress },
                            modifier = Modifier.size(86.dp),
                            color = RecordingPink,
                            strokeWidth = 4.dp
                        )
                    }

                    Icon(
                        imageVector = if (state.activeProfile.isEnrolled && !state.isRecordingSample) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.Mic
                        },
                        contentDescription = "Record Voice",
                        tint = DarkBackground,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (state.isRecordingSample) {
                    LinearProgressIndicator(
                        progress = { state.recordingProgress },
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = RecordingPink,
                        trackColor = DarkSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "কথা রেকর্ড হচ্ছে... (২ সেকেন্ড)",
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                } else if (!state.activeProfile.isEnrolled) {
                    Button(
                        onClick = { viewModel.recordSample() },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("record_sample_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = DarkBackground,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "নমুনা ${state.recordedSamples.size + 1} রেকর্ড করুন",
                            color = DarkBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    Text(
                        text = "আপনার প্রোফাইল সক্রিয় আছে",
                        color = SuccessGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Error message display
                state.errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = error,
                        color = RecordingPink,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Active Profile Metrics & Testing
        if (state.activeProfile.isEnrolled) {
            Spacer(modifier = Modifier.height(20.dp))

            // Profile Specs Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkBorder, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ভয়েস প্রোফাইল প্যারামিটার",
                            color = NeonCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "৩টি নমুনা সংকলিত",
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricItem(
                            label = "গড় পিচ (F0)",
                            value = "${state.activeProfile.averagePitchHz.roundToInt()} Hz"
                        )
                        MetricItem(
                            label = "স্পেকট্রাল রেজোন্যান্স",
                            value = "${state.activeProfile.averageCentroid.roundToInt()} Hz"
                        )
                        MetricItem(
                            label = "ভয়েসিং অনুপাত",
                            value = "${(state.activeProfile.averageLowRatio * 100).roundToInt()}%"
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Threshold slider
                    Text(
                        text = "নিরাপত্তা থ্রেশহোল্ড: ${state.activeProfile.matchThresholdPercent.roundToInt()}% মিল আবশ্যক",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Slider(
                        value = state.activeProfile.matchThresholdPercent,
                        onValueChange = { viewModel.updateThreshold(it) },
                        valueRange = 45f..85f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan,
                            inactiveTrackColor = DarkSurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Test Voice Verification Live Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Brush.linearGradient(listOf(ElectricViolet, DarkBorder)), RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "কণ্ঠস্বর যাচাই পরীক্ষা (Voice Match Test)",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "আপনি অথবা অন্য কোনো ব্যক্তি কথা বলে পরীক্ষা করুন",
                        color = TextTertiary,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.testVoiceVerification() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isTestingVoice) RecordingPink else ElectricViolet
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("test_voice_button")
                    ) {
                        Text(
                            text = if (state.isTestingVoice) "শুনছি... (কথা বলুন)" else "কণ্ঠস্বর পরীক্ষা করুন",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Test result box
                    state.testMatchResult?.let { result ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (result.isMatch) SuccessGreen.copy(alpha = 0.15f)
                                    else RecordingPink.copy(alpha = 0.15f)
                                )
                                .border(
                                    1.dp,
                                    if (result.isMatch) SuccessGreen else RecordingPink,
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (result.isMatch) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (result.isMatch) SuccessGreen else RecordingPink,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (result.isMatch) "অনুমোদিত মালিকের কণ্ঠস্বর" else "অন্য ব্যক্তির কণ্ঠস্বর - বাতিল",
                                        color = if (result.isMatch) SuccessGreen else RecordingPink,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "মিল: ${result.similarityPercent.roundToInt()}% (পিচ: ${result.pitchHz.roundToInt()} Hz, প্রত্যাশিত: ${result.expectedPitchHz.roundToInt()} Hz)",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Reset profile button
            OutlinedButton(
                onClick = { viewModel.resetProfile() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RecordingPink),
                border = androidx.compose.foundation.BorderStroke(1.dp, RecordingPink),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reset_profile_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "নতুন করে ভয়েস প্রোফাইল তৈরি করুন", fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = TextTertiary, fontSize = 10.sp)
    }
}
