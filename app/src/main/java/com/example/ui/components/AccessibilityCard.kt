package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.AssistantActionCommand
import com.example.service.AssistantActionResult
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RecordingPink
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccessibilityCard(
    isServiceConnected: Boolean,
    lastCommand: AssistantActionCommand?,
    lastResult: AssistantActionResult?,
    onOpenSettings: () -> Unit,
    onExecuteJson: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showTestingPanel by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isServiceConnected) Brush.linearGradient(listOf(NeonCyan, ElectricViolet))
                else Brush.linearGradient(listOf(DarkBorder, DarkBorder)),
                RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isServiceConnected) NeonCyan.copy(alpha = 0.2f)
                                else Color(0xFFFFB74D).copy(alpha = 0.2f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = if (isServiceConnected) NeonCyan else Color(0xFFFFB74D),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Accessibility UI Automation",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isServiceConnected) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isServiceConnected) NeonCyan else Color(0xFFFFB74D),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (isServiceConnected) "Service Connected (Ready)" else "Not Enabled in Settings",
                                color = if (isServiceConnected) NeonCyan else Color(0xFFFFB74D),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (!isServiceConnected) {
                    FilledTonalButton(
                        onClick = onOpenSettings,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = ElectricViolet.copy(alpha = 0.3f),
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("enable_accessibility_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Enable", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Mimi outputs ONLY JSON commands for AccessibilityService & Intents (opening specific settings, turning on flashlight, playing specific YT videos, and on-screen clicks).",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            // Last Executed Action Banner
            if (lastCommand != null || lastResult != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (lastResult?.success == true) NeonCyan.copy(alpha = 0.12f)
                            else RecordingPink.copy(alpha = 0.12f)
                        )
                        .border(
                            1.dp,
                            if (lastResult?.success == true) NeonCyan.copy(alpha = 0.3f)
                            else RecordingPink.copy(alpha = 0.3f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Last Command: ${lastCommand?.action?.uppercase() ?: "ACTION"}",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = if (lastResult?.success == true) "✓ SUCCESS" else "FAILED",
                                color = if (lastResult?.success == true) NeonCyan else RecordingPink,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }

                        if (!lastCommand?.rawJson.isNullOrBlank()) {
                            Text(
                                text = lastCommand?.rawJson ?: "",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        if (lastResult != null) {
                            Text(
                                text = lastResult.message,
                                color = if (lastResult.success) TextPrimary else RecordingPink,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Testing Action Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick Command Tests",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedButton(
                    onClick = { showTestingPanel = !showTestingPanel },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(if (showTestingPanel) "Hide" else "Show Tests", fontSize = 11.sp)
                }
            }

            AnimatedVisibility(visible = showTestingPanel) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Test 1: Flashlight ON
                        FilledTonalButton(
                            onClick = {
                                onExecuteJson("""{"action": "flashlight", "state": "on"}""")
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFFFFD54F).copy(alpha = 0.2f),
                                contentColor = Color(0xFFFFD54F)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("test_action_flashlight")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Flashlight On", fontSize = 11.sp)
                        }

                        // Test 2: Wi-Fi Settings
                        FilledTonalButton(
                            onClick = {
                                onExecuteJson("""{"action": "open_settings", "setting": "wifi"}""")
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = NeonCyan.copy(alpha = 0.15f),
                                contentColor = NeonCyan
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("test_action_settings")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Wi-Fi Settings", fontSize = 11.sp)
                        }

                        // Test 3: Play YouTube Video
                        FilledTonalButton(
                            onClick = {
                                onExecuteJson("""{"action": "play_youtube", "query": "relaxing lofi"}""")
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = ElectricViolet.copy(alpha = 0.2f),
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("test_play_youtube")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play YT Video", fontSize = 11.sp)
                        }

                        // Test 4: Open YouTube
                        FilledTonalButton(
                            onClick = {
                                onExecuteJson("""{"action": "open", "app": "youtube"}""")
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = NeonCyan.copy(alpha = 0.15f),
                                contentColor = NeonCyan
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("test_open_youtube")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open YouTube", fontSize = 11.sp)
                        }

                        // Test 5: Click Search
                        FilledTonalButton(
                            onClick = {
                                onExecuteJson("""{"action": "click", "text": "Search"}""")
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = ElectricViolet.copy(alpha = 0.2f),
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("test_click_search")
                        ) {
                            Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Click 'Search'", fontSize = 11.sp)
                        }

                        // Test 6: Home
                        FilledTonalButton(
                            onClick = {
                                onExecuteJson("""{"action": "home"}""")
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = 0.1f),
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("test_action_home")
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Home", fontSize = 11.sp)
                        }

                        // Test 7: Back
                        FilledTonalButton(
                            onClick = {
                                onExecuteJson("""{"action": "back"}""")
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = 0.1f),
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("test_action_back")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Back", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
