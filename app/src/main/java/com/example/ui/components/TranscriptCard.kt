package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SpeechLanguage
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RecordingPink
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import kotlinx.coroutines.delay

@Composable
fun TranscriptCard(
    fullText: String,
    partialText: String,
    language: SpeechLanguage,
    isListening: Boolean,
    onClear: () -> Unit,
    onAskGemini: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var isCopied by remember { mutableStateOf(false) }

    LaunchedEffect(fullText, partialText) {
        if (scrollState.canScrollForward) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(2000)
            isCopied = false
        }
    }

    val hasContent = fullText.isNotBlank() || partialText.isNotBlank()
    val wordCount = remember(fullText) {
        if (fullText.isBlank()) 0 else fullText.trim().split(Regex("\\s+")).size
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = if (isListening) {
                        listOf(NeonCyan.copy(alpha = 0.5f), ElectricViolet.copy(alpha = 0.3f))
                    } else {
                        listOf(DarkBorder, DarkBorder.copy(alpha = 0.4f))
                    }
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .testTag("transcript_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkCardBg
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header row with Badge & Counters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Language & Status Tag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeonCyan.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = language.labelNative,
                            color = NeonCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (isListening) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(RecordingPink.copy(alpha = 0.18f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = RecordingPink,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (language == SpeechLanguage.BENGALI) "রেকর্ড হচ্ছে" else "REC",
                                    color = RecordingPink,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Stats: Word count
                if (hasContent) {
                    Text(
                        text = if (language == SpeechLanguage.BENGALI) "$wordCount টি শব্দ" else "$wordCount words",
                        color = TextTertiary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Text Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 70.dp, max = 130.dp)
                    .verticalScroll(scrollState)
            ) {
                if (!hasContent) {
                    Text(
                        text = language.promptPlaceholder,
                        color = TextSecondary.copy(alpha = 0.6f),
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    SelectionContainer {
                        val annotatedText = buildAnnotatedString {
                            withStyle(
                                style = SpanStyle(
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            ) {
                                append(fullText)
                            }
                            if (partialText.isNotBlank()) {
                                if (fullText.isNotBlank()) append(" ")
                                withStyle(
                                    style = SpanStyle(
                                        color = NeonCyan,
                                        fontSize = 16.sp,
                                        fontStyle = FontStyle.Italic,
                                        fontWeight = FontWeight.Medium
                                    )
                                ) {
                                    append(partialText)
                                }
                            }
                        }

                        Text(
                            text = annotatedText,
                            lineHeight = 24.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("transcript_display")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Row (Clear, Share, Copy, Ask Gemini)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasContent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Clear
                        IconButton(
                            onClick = onClear,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear text",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Share
                        IconButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, fullText)
                                }
                                context.startActivity(
                                    Intent.createChooser(
                                        shareIntent,
                                        if (language == SpeechLanguage.BENGALI) "টেক্সট শেয়ার করুন" else "Share speech text"
                                    )
                                )
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("share_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share text",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Copy
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Transcribed Voice", fullText)
                                clipboard.setPrimaryClip(clip)
                                isCopied = true
                                Toast.makeText(
                                    context,
                                    if (language == SpeechLanguage.BENGALI) "কপি করা হয়েছে" else "Copied",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("copy_button")
                        ) {
                            Icon(
                                imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Copy text",
                                tint = if (isCopied) SuccessGreen else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Send to Gemini manual button
                    FilledTonalButton(
                        onClick = onAskGemini,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = ElectricViolet.copy(alpha = 0.2f),
                            contentColor = ElectricViolet
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("ask_gemini_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Ask Gemini",
                            tint = NeonCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (language == SpeechLanguage.BENGALI) "জেমিনিকে জিজ্ঞেস করুন" else "Ask Gemini",
                            color = NeonCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
