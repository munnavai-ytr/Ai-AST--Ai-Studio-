package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.SpeechLanguage
import com.example.ui.components.AccessibilityCard
import com.example.ui.components.GeminiResponseCard
import com.example.ui.components.GlowingMicButton
import com.example.ui.components.HistorySheet
import com.example.ui.components.SoundWaveVisualizer
import com.example.ui.components.TranscriptCard
import com.example.ui.screens.VoiceProfileSetupScreen
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
import com.example.viewmodel.VoiceToTextViewModel

@Composable
fun VoiceToTextScreen(
    viewModel: VoiceToTextViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showProfileSetupScreen by remember { mutableStateOf(false) }
    var permissionDeniedExplanation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.checkAccessibilityStatus()
    }

    if (showProfileSetupScreen) {
        VoiceProfileSetupScreen(
            onNavigateBack = { showProfileSetupScreen = false }
        )
        return
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            permissionDeniedExplanation = false
            viewModel.startListening()
        } else {
            permissionDeniedExplanation = true
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.toggleBackgroundService()
    }

    fun handleBackgroundServiceToggle() {
        val micPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        if (micPermission != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (notifPermission != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        viewModel.toggleBackgroundService()
    }

    fun handleMicPress() {
        if (uiState.isListening) {
            viewModel.stopListening()
            return
        }

        val permission = Manifest.permission.RECORD_AUDIO
        val check = ContextCompat.checkSelfPermission(context, permission)
        if (check == PackageManager.PERMISSION_GRANTED) {
            viewModel.startListening()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val isBengali = uiState.selectedLanguage == SpeechLanguage.BENGALI

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBackground
    ) { innerPadding ->
        val screenScrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding()
                )
                .verticalScroll(screenScrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // TOP BAR: App Title, Language Toggle, and History
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Brand with subtle neon gradient
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(listOf(NeonCyan, ElectricViolet))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = DarkBackground,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = if (isBengali) "এআই ভয়েস সহকারী" else "AI Voice Assistant",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = if (isBengali) "জেমিনি এআই ও টিটিএস সংহত" else "Gemini AI & TTS Powered",
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }
                }

                // Right actions: Voice Profile & History Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { showProfileSetupScreen = true },
                        modifier = Modifier.testTag("voice_profile_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (uiState.voiceProfile.isEnrolled) {
                                    Badge(
                                        containerColor = SuccessGreen,
                                        contentColor = DarkBackground
                                    ) {
                                        Text("✓")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Voice Profile",
                                tint = if (uiState.voiceProfile.isEnrolled) SuccessGreen else NeonCyan
                            )
                        }
                    }

                    // History Icon
                    IconButton(
                        onClick = { showHistorySheet = true },
                        modifier = Modifier.testTag("history_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (uiState.historyList.isNotEmpty()) {
                                    Badge(
                                        containerColor = NeonCyan,
                                        contentColor = DarkBackground
                                    ) {
                                        Text(uiState.historyList.size.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "History",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // LANGUAGE TOGGLE SWITCH (Bengali / English)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkBorder, RoundedCornerShape(24.dp))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isBnSelected = uiState.selectedLanguage == SpeechLanguage.BENGALI
                val bnBgModifier = if (isBnSelected) {
                    Modifier.background(Brush.linearGradient(listOf(NeonCyan, ElectricViolet)))
                } else {
                    Modifier.background(Color.Transparent)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .then(bnBgModifier)
                        .clickable { viewModel.selectLanguage(SpeechLanguage.BENGALI) }
                        .padding(horizontal = 20.dp, vertical = 7.dp)
                        .testTag("language_toggle_bn"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "বাংলা",
                        color = if (isBnSelected) DarkBackground else TextSecondary,
                        fontWeight = if (isBnSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                val isEnSelected = uiState.selectedLanguage == SpeechLanguage.ENGLISH
                val enBgModifier = if (isEnSelected) {
                    Modifier.background(Brush.linearGradient(listOf(NeonCyan, ElectricViolet)))
                } else {
                    Modifier.background(Color.Transparent)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .then(enBgModifier)
                        .clickable { viewModel.selectLanguage(SpeechLanguage.ENGLISH) }
                        .padding(horizontal = 20.dp, vertical = 7.dp)
                        .testTag("language_toggle_en"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "English",
                        color = if (isEnSelected) DarkBackground else TextSecondary,
                        fontWeight = if (isEnSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // HEY MIMI BACKGROUND SERVICE CARD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (uiState.isBackgroundServiceActive) Brush.linearGradient(listOf(NeonCyan, ElectricViolet))
                        else Brush.linearGradient(listOf(DarkBorder, DarkBorder)),
                        RoundedCornerShape(16.dp)
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = DarkCardBg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (uiState.isBackgroundServiceActive) NeonCyan.copy(alpha = 0.2f)
                                        else DarkSurfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Hearing,
                                    contentDescription = null,
                                    tint = if (uiState.isBackgroundServiceActive) NeonCyan else TextTertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Hey Mimi ব্যাকগ্রাউন্ড ডিটেকশন",
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (uiState.isBackgroundServiceActive) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(SuccessGreen.copy(alpha = 0.2f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "ACTIVE",
                                                color = SuccessGreen,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (uiState.voiceProfile.isEnrolled) {
                                        "বায়োমেট্রিক প্রোফাইল সক্রিয় (অন্যের কণ্ঠ অগ্রাহ্য)"
                                    } else {
                                        "ভয়েস প্রোফাইল তৈরি নেই (সেটআপ করুন)"
                                    },
                                    color = if (uiState.voiceProfile.isEnrolled) SuccessGreen else RecordingPink,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Switch(
                            checked = uiState.isBackgroundServiceActive,
                            onCheckedChange = { handleBackgroundServiceToggle() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DarkBackground,
                                checkedTrackColor = NeonCyan,
                                uncheckedThumbColor = TextTertiary,
                                uncheckedTrackColor = DarkSurfaceVariant
                            ),
                            modifier = Modifier.testTag("wake_word_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Floating UI overlay toggle / test launcher
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "ফ্লোটিং উইন্ডো (System Overlay)",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Text(
                            text = if (Settings.canDrawOverlays(context)) "ওভারলে খুলুন ›" else "অনুমতি দিন ›",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    if (Settings.canDrawOverlays(context)) {
                                        com.example.service.FloatingUIService.start(context)
                                    } else {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        ).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("btn_toggle_floating_overlay")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.serviceStatusMessage,
                            color = TextTertiary,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = if (uiState.voiceProfile.isEnrolled) "প্রোফাইল পরীক্ষা ›" else "৩ বার রেকর্ড করুন ›",
                            color = NeonCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showProfileSetupScreen = true }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("open_voice_profile_setup")
                        )
                    }

                    // Recent voice match event banner
                    uiState.lastWakeMatch?.let { match ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (match.isMatch) SuccessGreen.copy(alpha = 0.12f)
                                    else RecordingPink.copy(alpha = 0.12f)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (match.isMatch) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (match.isMatch) SuccessGreen else RecordingPink,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = match.explanation,
                                color = if (match.isMatch) SuccessGreen else RecordingPink,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // SECURITY MONITORING CARD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (uiState.isSecurityMonitoringActive) Brush.linearGradient(listOf(NeonCyan, ElectricViolet))
                        else Brush.linearGradient(listOf(DarkBorder, DarkBorder)),
                        RoundedCornerShape(16.dp)
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (uiState.isSecurityMonitoringActive) NeonCyan.copy(alpha = 0.2f)
                                        else DarkSurfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = if (uiState.isSecurityMonitoringActive) NeonCyan else TextTertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Security Monitoring",
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (uiState.isSecurityMonitoringActive) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(SuccessGreen.copy(alpha = 0.2f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "ACTIVE",
                                                color = SuccessGreen,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (uiState.isSecurityMonitoringActive) {
                                        "অ্যাক্সেসিবিলিটি ট্যাম্পার ও অ্যাপ ইন্সটল পর্যবেক্ষণ সক্রিয়"
                                    } else {
                                        "সন্দেহজনক কার্যকলাপ পর্যবেক্ষণ বন্ধ রয়েছে"
                                    },
                                    color = if (uiState.isSecurityMonitoringActive) SuccessGreen else TextTertiary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Switch(
                            checked = uiState.isSecurityMonitoringActive,
                            onCheckedChange = { viewModel.toggleSecurityMonitoring() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DarkBackground,
                                checkedTrackColor = NeonCyan,
                                uncheckedThumbColor = TextTertiary,
                                uncheckedTrackColor = DarkSurfaceVariant
                            ),
                            modifier = Modifier.testTag("security_monitoring_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (uiState.isSecurityMonitoringActive) {
                            "স্বচ্ছতা নোটিফিকেশন সহ ব্যাকগ্রাউন্ডে চলছে (Firestore এ ইভেন্ট লগ হবে)"
                        } else {
                            "মালিকের সুরক্ষার জন্য সক্রিয় করুন"
                        },
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // CENTER SECTION: Centerpiece Glowing Microphone Button
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Permission warning if user previously denied
                AnimatedVisibility(
                    visible = permissionDeniedExplanation,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(RecordingPink.copy(alpha = 0.15f))
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = RecordingPink,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (isBengali) "ভয়েস শুনতে মাইক্রোফোনের অনুমতি প্রয়োজন।" else "Microphone permission is required to listen.",
                                color = RecordingPink,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RecordingPink),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (isBengali) "অনুমতি দিন" else "Grant", fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Centerpiece Glowing Microphone Button
                GlowingMicButton(
                    isListening = uiState.isListening,
                    isSpeaking = uiState.isSpeaking,
                    soundLevel = uiState.soundLevel,
                    onClick = { handleMicPress() },
                    buttonSize = 92.dp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Sound Wave Equalizer Bars
                SoundWaveVisualizer(
                    isListening = uiState.isListening,
                    isSpeaking = uiState.isSpeaking,
                    soundLevel = uiState.soundLevel,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Status text pill with glowing dot
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkCardBg)
                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (uiState.isSpeaking || uiState.isTtsSpeaking) RecordingPink
                                else if (uiState.isListening) NeonCyan
                                else TextTertiary
                            )
                    )

                    Text(
                        text = uiState.statusText,
                        color = if (uiState.isListening) NeonCyan else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Push 1 Recognized Voice Transcript Display
            TranscriptCard(
                fullText = uiState.fullText,
                partialText = uiState.partialText,
                language = uiState.selectedLanguage,
                isListening = uiState.isListening,
                onClear = { viewModel.clearText() },
                onAskGemini = { viewModel.sendCurrentToGemini() },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Gemini Response Card with Text-to-Speech Controls
            GeminiResponseCard(
                response = uiState.geminiResponse,
                status = uiState.assistantStatus,
                isTtsSpeaking = uiState.isTtsSpeaking,
                language = uiState.selectedLanguage,
                onSpeak = { viewModel.speakTts() },
                onStopSpeak = { viewModel.stopSpeakingTts() },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Accessibility UI Automation Card
            AccessibilityCard(
                isServiceConnected = uiState.isAccessibilityConnected,
                lastCommand = uiState.lastActionCommand,
                lastResult = uiState.lastActionResult,
                onOpenSettings = { viewModel.openAccessibilitySettings() },
                onExecuteJson = { json -> viewModel.executeGeminiJsonCommand(json) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showHistorySheet) {
        HistorySheet(
            historyList = uiState.historyList,
            onSelect = { item -> viewModel.restoreFromHistory(item) },
            onDelete = { id -> viewModel.deleteHistoryItem(id) },
            onClearAll = { viewModel.clearAllHistory() },
            onDismiss = { showHistorySheet = false },
            isBengali = isBengali
        )
    }
}
