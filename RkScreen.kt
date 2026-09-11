package com.example

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.RkAccentGreen
import com.example.ui.theme.RkAccentOrange
import com.example.ui.theme.RkAccentPurple
import com.example.ui.theme.RkBlue
import com.example.ui.theme.RkCardNavy
import com.example.ui.theme.RkCyan
import com.example.ui.theme.RkCyanDark
import com.example.ui.theme.RkDarkNavy
import com.example.ui.theme.RkGlowCyan
import com.example.ui.theme.RkSurfaceNavy

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RkScreen(
    controller: RkController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val assistantState by controller.assistantState.collectAsStateWithLifecycle()
    val status by controller.status.collectAsStateWithLifecycle()
    val recognizedText by controller.recognizedText.collectAsStateWithLifecycle()
    val rmsLevel by controller.rmsLevel.collectAsStateWithLifecycle()
    val selectedLanguage by controller.selectedLanguage.collectAsStateWithLifecycle()
    val isTtsEnabled by controller.isTtsEnabled.collectAsStateWithLifecycle()
    val isWakeWordEnabled by controller.isWakeWordEnabled.collectAsStateWithLifecycle()
    val pendingAction by controller.pendingAction.collectAsStateWithLifecycle()
    val history by controller.history.collectAsStateWithLifecycle()

    var textInput by remember { mutableStateOf("") }
    var showLanguageMenu by remember { mutableStateOf("") }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showPermissionBanner by remember { mutableStateOf(!hasAudioPermission) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        showPermissionBanner = !isGranted
        if (isGranted) {
            controller.startListening()
        }
    }

    val quickCommands = remember {
        listOf(
            QuickCommand("ইউটিউব", "ভিডিও দেখুন", "RK, ইউটিউব খুলে দাও", "App", "bn"),
            QuickCommand("Camera", "ক্যামেরা অন", "RK, camera kholo", "Device", "bn"),
            QuickCommand("आज का मौसम", "Weather Query", "RK, आज मौसम कैसा है?", "Search", "hi"),
            QuickCommand("Search News", "Latest Updates", "RK, search latest technology news", "Search", "en"),
            QuickCommand("Facebook", "সামাজিক যোগাযোগ", "RK, ফেসবুক খোলো", "App", "bn"),
            QuickCommand("Instagram", "রিলস ও ছবি", "RK, ইনস্টাগ্রাম খোলো", "App", "bn"),
            QuickCommand("ভলিউম বাড়াও", "Volume Up", "RK, ভলিউম বাড়াও", "Device", "bn"),
            QuickCommand("ফোন ডায়াল", "Call Confirmation", "RK, 9876543210 এ কল করো", "Call", "bn"),
            QuickCommand("এসএমএস ড্রাফট", "SMS Confirmation", "RK, send message to 9876543210", "SMS", "en")
        )
    }

    Scaffold(
        modifier = modifier
            .background(RkDarkNavy)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        containerColor = RkDarkNavy,
        topBar = {
            // Futuristic RK Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(RkCyan, RkBlue))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "RK",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "RK",
                            color = RkCyan,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Personal AI Assistant",
                            color = Color.LightGray.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Controls: Language selector, Wake Word & TTS toggles
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Wake Word indicator chip
                    FilterChip(
                        selected = isWakeWordEnabled,
                        onClick = { controller.toggleWakeWord() },
                        label = {
                            Text(
                                text = "RK Word",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Hearing,
                                contentDescription = "Wake Word",
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RkCyan.copy(alpha = 0.2f),
                            selectedLabelColor = RkCyan,
                            selectedLeadingIconColor = RkCyan,
                            containerColor = RkSurfaceNavy,
                            labelColor = Color.Gray,
                            iconColor = Color.Gray
                        ),
                        border = BorderStroke(1.dp, if (isWakeWordEnabled) RkCyan.copy(alpha = 0.4f) else Color.Transparent)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // TTS Voice Mute Toggle
                    IconButton(
                        onClick = { controller.toggleTts() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(RkSurfaceNavy)
                            .testTag("tts_toggle")
                    ) {
                        Icon(
                            imageVector = if (isTtsEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = if (isTtsEnabled) "Mute Voice" else "Unmute Voice",
                            tint = if (isTtsEnabled) RkCyan else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Futuristic Input Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = RkSurfaceNavy,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, RkCyan.copy(alpha = 0.3f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("text_command_input"),
                        placeholder = {
                            Text(
                                text = "লিখুন বা বলুন: 'RK, ইউটিউব খোলো'...",
                                color = Color.Gray,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = RkCyan
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (textInput.isNotBlank()) {
                                controller.processUserInput(textInput)
                                textInput = ""
                                focusManager.clearFocus()
                            }
                        })
                    )

                    // Send Button
                    if (textInput.isNotBlank()) {
                        IconButton(
                            onClick = {
                                controller.processUserInput(textInput)
                                textInput = ""
                                focusManager.clearFocus()
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(RkCyan.copy(alpha = 0.2f))
                                .testTag("send_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send Command",
                                tint = RkCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Microphone Core Button
                    val isListening = assistantState == AssistantState.LISTENING
                    IconButton(
                        onClick = {
                            if (hasAudioPermission) {
                                if (isListening) {
                                    controller.stopListening()
                                } else {
                                    controller.startListening()
                                }
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (isListening) RkAccentGreen else RkCardNavy)
                            .testTag("mic_shortcut_button")
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                            contentDescription = if (isListening) "Stop Listening" else "Start Listening",
                            tint = if (isListening) Color.Black else RkCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val listState = rememberLazyListState()

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Permission Banner (if needed)
            item {
                AnimatedVisibility(visible = showPermissionBanner) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = RkCardNavy),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, RkAccentOrange)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MicOff,
                                contentDescription = "Permission Warning",
                                tint = RkAccentOrange,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "মাইক্রোফোন অনুমতি প্রয়োজন",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "RK কে ভয়েস কমান্ড দিতে মাইক্রোফোন চালু করুন।",
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                            }
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                colors = ButtonDefaults.buttonColors(containerColor = RkAccentOrange),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("অনুমতি দিন", fontSize = 11.sp, color = Color.Black)
                            }
                        }
                    }
                }
            }

            // Sensitive Action Confirmation Card (Calls & SMS)
            item {
                AnimatedVisibility(
                    visible = pendingAction != null,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    val action = pendingAction
                    if (action != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2333)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.5.dp, RkAccentOrange)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = "Security Confirmation",
                                        tint = RkAccentOrange,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = action.title,
                                        color = RkAccentOrange,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = action.description,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )

                                if (!action.targetData.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        color = RkDarkNavy,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, RkCyan.copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            text = action.targetData,
                                            color = RkCyan,
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { controller.cancelPendingAction() },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("cancel_action_button"),
                                        border = BorderStroke(1.dp, Color.Gray),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(16.dp), tint = Color.LightGray)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("বাতিল (Cancel)", color = Color.LightGray, fontSize = 12.sp)
                                    }

                                    Button(
                                        onClick = { controller.confirmPendingAction() },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("confirm_action_button"),
                                        colors = ButtonDefaults.buttonColors(containerColor = RkAccentGreen),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = "Confirm", modifier = Modifier.size(16.dp), tint = Color.Black)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("নিশ্চিত (Confirm)", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // AI Status & State Indicator
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = RkSurfaceNavy),
                    border = BorderStroke(
                        1.dp,
                        when (assistantState) {
                            AssistantState.LISTENING -> RkAccentGreen
                            AssistantState.THINKING -> RkAccentPurple
                            AssistantState.SPEAKING -> RkCyan
                            AssistantState.IDLE -> RkCyanDark.copy(alpha = 0.25f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // State badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            val stateColor = when (assistantState) {
                                AssistantState.LISTENING -> RkAccentGreen
                                AssistantState.THINKING -> RkAccentPurple
                                AssistantState.SPEAKING -> RkCyan
                                AssistantState.IDLE -> Color.Gray
                            }

                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(stateColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (assistantState) {
                                    AssistantState.LISTENING -> "Listening..."
                                    AssistantState.THINKING -> "Thinking..."
                                    AssistantState.SPEAKING -> "Speaking..."
                                    AssistantState.IDLE -> "RK • Ready"
                                },
                                color = stateColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = status,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Futuristic Arc Reactor & Orb Visualizer
            item {
                Spacer(modifier = Modifier.height(16.dp))
                RkArcReactor(
                    state = assistantState,
                    rmsLevel = rmsLevel,
                    onClick = {
                        if (hasAudioPermission) {
                            if (assistantState == AssistantState.LISTENING) {
                                controller.stopListening()
                            } else {
                                controller.startListening()
                            }
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Audio Waveform
            item {
                AudioWaveVisualizer(
                    isListening = assistantState == AssistantState.LISTENING,
                    rmsLevel = rmsLevel,
                    isSpeaking = assistantState == AssistantState.SPEAKING,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Text(
                    text = if (assistantState == AssistantState.LISTENING) "স্পর্শ করে বন্ধ করুন" else "কথা বলতে চাপুন বা বলুন 'RK'",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }

            // Quick Actions Title & Flow Chips
            item {
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Quick Actions",
                            tint = RkCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "দ্রুত নির্দেশনাসমূহ (Quick Actions)",
                            color = RkCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "বাংলা • हिन्दी • EN",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickCommands.forEach { cmd ->
                        QuickChip(
                            title = cmd.title,
                            subtitle = cmd.subtitle,
                            icon = when (cmd.category) {
                                "App" -> Icons.Default.PlayArrow
                                "Device" -> Icons.Default.CameraAlt
                                "Call" -> Icons.Default.Call
                                "SMS" -> Icons.Default.Message
                                else -> Icons.Default.Search
                            },
                            onClick = {
                                controller.processUserInput(cmd.commandPhrase)
                            }
                        )
                    }
                }
            }

            // Conversation History Section
            if (history.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "কথোপকথন (Conversation History)",
                            color = RkCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${history.size} turns",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(history, key = { it.id }) { item ->
                    HistoryItemCard(item = item)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun QuickChip(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = RkCardNavy,
        border = BorderStroke(1.dp, RkCyan.copy(alpha = 0.25f)),
        modifier = Modifier.padding(bottom = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = RkCyan,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = Color.Gray,
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
fun HistoryItemCard(item: RkHistoryItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = RkSurfaceNavy),
        border = BorderStroke(1.dp, RkCyanDark.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // User Command Bubble
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    color = RkCardNavy,
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 4.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
                    border = BorderStroke(1.dp, RkCyan.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = item.command,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // RK Response Bubble
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(RkCyan.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "RK",
                        color = RkCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = Color(0xFF131D31),
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
                    border = BorderStroke(1.dp, RkCyanDark.copy(alpha = 0.25f)),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = item.response,
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
