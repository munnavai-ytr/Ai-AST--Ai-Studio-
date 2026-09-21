package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AssistantStatus
import com.example.model.SpeechLanguage
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RecordingPink
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun GeminiResponseCard(
    response: String?,
    status: AssistantStatus,
    isTtsSpeaking: Boolean,
    language: SpeechLanguage,
    onSpeak: () -> Unit,
    onStopSpeak: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isBengali = language == SpeechLanguage.BENGALI

    if (status == AssistantStatus.THINKING) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(listOf(ElectricViolet, NeonCyan)),
                    shape = RoundedCornerShape(20.dp)
                )
                .testTag("gemini_loading_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = NeonCyan,
                    strokeWidth = 2.5.dp
                )
                Column {
                    Text(
                        text = if (isBengali) "জেমিনি এআই সহকারী ভাবছে..." else "Gemini Assistant is thinking...",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isBengali) "আপনার ভয়েস নির্দেশ বিশ্লেষণ করা হচ্ছে" else "Analyzing your voice query",
                        color = TextTertiary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    } else if (!response.isNullOrBlank()) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        if (isTtsSpeaking) listOf(NeonCyan, RecordingPink)
                        else listOf(ElectricViolet.copy(alpha = 0.6f), DarkBorder)
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .testTag("gemini_response_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.linearGradient(listOf(ElectricViolet, NeonCyan))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = DarkCardBg,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = if (isBengali) "জেমিনি সহকারী" else "Gemini Assistant",
                            color = NeonCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // TTS audio status pill
                    if (isTtsSpeaking) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(RecordingPink.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(RecordingPink)
                                )
                                Text(
                                    text = if (isBengali) "বলছে..." else "Speaking...",
                                    color = RecordingPink,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable response text
                val textScrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .verticalScroll(textScrollState)
                ) {
                    SelectionContainer {
                        Text(
                            text = response,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            modifier = Modifier.testTag("gemini_response_text")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action buttons: Speak/Stop, Copy, Share
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Copy
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Gemini Response", response)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(
                                context,
                                if (isBengali) "উত্তর কপি করা হয়েছে" else "Copied to clipboard",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy response",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Share
                    IconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, response)
                            }
                            context.startActivity(
                                Intent.createChooser(
                                    shareIntent,
                                    if (isBengali) "উত্তর শেয়ার করুন" else "Share Gemini response"
                                )
                            )
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share response",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // TTS Speak/Stop button
                    if (isTtsSpeaking) {
                        FilledTonalButton(
                            onClick = onStopSpeak,
                            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                containerColor = RecordingPink.copy(alpha = 0.2f),
                                contentColor = RecordingPink
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("stop_tts_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.StopCircle,
                                contentDescription = "Stop TTS",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isBengali) "থামান" else "Stop",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onSpeak,
                            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                containerColor = NeonCyan.copy(alpha = 0.15f),
                                contentColor = NeonCyan
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("speak_tts_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Read aloud",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isBengali) "পড়ুন" else "Read Aloud",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
