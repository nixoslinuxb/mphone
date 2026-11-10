package com.mphone.ui.phone

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mphone.data.CallAudioRecorder
import com.mphone.data.TelecomCallManager
import com.mphone.model.Contact
import com.mphone.ui.components.ContactAvatar
import com.mphone.ui.theme.ElectricIndigo
import com.mphone.ui.theme.ElectricViolet
import com.mphone.ui.theme.IncomingGreen
import com.mphone.ui.theme.MissedRed
import com.mphone.ui.theme.MphoneTheme
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun ActiveCallScreen(
    name: String,
    number: String,
    onEndCall: (durationStr: String) -> Unit,
    contacts: List<Contact> = emptyList(),
    onPerformCall: (name: String, number: String) -> Unit = { _, _ -> },
    onMinimize: () -> Unit = {},
    photoUri: String? = null
) {
    val context = LocalContext.current
    var secondsElapsed by remember { mutableIntStateOf(0) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }
    var isHold by remember { mutableStateOf(false) }

    var isKeypadOpen by remember { mutableStateOf(false) }
    var enteredKeypadDigits by remember { mutableStateOf("") }

    var secondParticipant by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showAddParticipantDialog by remember { mutableStateOf(false) }

    val callRecorder = remember { CallAudioRecorder() }
    var isRecordingCall by remember { mutableStateOf(false) }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            TelecomCallManager.toggleSpeakerphone(context, true)
            isSpeakerOn = true
            val file = callRecorder.startRecording(context, name, number)
            isRecordingCall = callRecorder.isRecording
            if (file != null) {
                Toast.makeText(context, "Voice recording started (Speaker enabled for clear voice)", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Could not start voice recording", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Microphone permission required for call recording", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            secondsElapsed++
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (callRecorder.isRecording) {
                val savedFile = callRecorder.stopRecording()
                if (savedFile != null) {
                    Toast.makeText(context, "Recording saved: ${savedFile.name}", Toast.LENGTH_LONG).show()
                }
            }
            TelecomCallManager.toggleSpeakerphone(context, false)
            TelecomCallManager.toggleMicrophoneMute(context, false)
        }
    }

    val minutes = secondsElapsed / 60
    val secs = secondsElapsed % 60
    val durationText = String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)

    // Dynamic background mesh gradient
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F172A), // Deep Slate Navy
            Color(0xFF1E1B4B), // Dark Indigo Slate
            Color(0xFF020617)  // Obsidian Black
        )
    )

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = Color.Black
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Full-screen Profile Background Photo (if available)
            if (!photoUri.isNullOrBlank()) {
                val imageRequest = remember(photoUri) {
                    ImageRequest.Builder(context)
                        .data(photoUri)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Dark Scrim Overlay for Contrast & Legibility
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.55f),
                                    Color(0xFF0F172A).copy(alpha = 0.85f),
                                    Color(0xFF020617).copy(alpha = 0.95f)
                                )
                            )
                        )
                )
            } else {
                // Default Dark Vertical Mesh Gradient Background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundBrush)
                )
            }

            // Ambient glowing radial aura behind caller avatar
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                (if (isHold) Color(0xFFF59E0B) else ElectricIndigo).copy(alpha = 0.28f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Header Row
                ActiveCallTopHeader(
                    onMinimize = onMinimize,
                    isRecording = isRecordingCall
                )

                // Main Caller Display Section
                CallerInformationSection(
                    name = name,
                    number = number,
                    photoUri = photoUri,
                    secondParticipant = secondParticipant,
                    isHold = isHold,
                    durationText = durationText,
                    isMuted = isMuted
                )

                // Bottom Call Controls Area
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    // 3x2 Modern Control Grid (Logos Only, No Text)
                    CallControlsGrid(
                        isMuted = isMuted,
                        onMuteToggle = {
                            val newMute = !isMuted
                            isMuted = newMute
                            TelecomCallManager.toggleMicrophoneMute(context, newMute)
                        },
                        isSpeakerOn = isSpeakerOn,
                        onSpeakerToggle = {
                            val newSpeaker = !isSpeakerOn
                            isSpeakerOn = newSpeaker
                            TelecomCallManager.toggleSpeakerphone(context, newSpeaker)
                        },
                        isKeypadOpen = isKeypadOpen,
                        onKeypadToggle = { isKeypadOpen = !isKeypadOpen },
                        onAddCallClick = { showAddParticipantDialog = true },
                        isRecording = isRecordingCall,
                        onRecordToggle = {
                            if (isRecordingCall) {
                                val savedFile = callRecorder.stopRecording()
                                isRecordingCall = false
                                if (savedFile != null) {
                                    Toast.makeText(context, "Voice recording saved: ${savedFile.name}", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasPermission) {
                                    val file = callRecorder.startRecording(context, name.ifBlank { number })
                                    isRecordingCall = callRecorder.isRecording
                                    if (file != null) {
                                        Toast.makeText(context, "Voice recording started", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        isHold = isHold,
                        onHoldToggle = { isHold = !isHold }
                    )

                    // Prominent Glowing End Call Action Button
                    EndCallButton(
                        onClick = {
                            val finalDuration = "${minutes}m ${secs}s"
                            if (callRecorder.isRecording) {
                                val savedFile = callRecorder.stopRecording()
                                if (savedFile != null) {
                                    Toast.makeText(context, "Recording saved: ${savedFile.name}", Toast.LENGTH_SHORT).show()
                                }
                            }
                            TelecomCallManager.toggleSpeakerphone(context, false)
                            TelecomCallManager.toggleMicrophoneMute(context, false)
                            onEndCall(finalDuration)
                        }
                    )
                }
            }

            // Interactive Modern DTMF Keypad Overlay
            AnimatedVisibility(
                visible = isKeypadOpen,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                DtmfKeypadOverlay(
                    digits = enteredKeypadDigits,
                    onDigitClick = { digit ->
                        enteredKeypadDigits += digit
                        TelecomCallManager.sendDtmfTone(digit)
                    },
                    onBackspace = {
                        if (enteredKeypadDigits.isNotEmpty()) {
                            enteredKeypadDigits = enteredKeypadDigits.dropLast(1)
                        }
                    },
                    onClose = { isKeypadOpen = false }
                )
            }
        }

        // Add Participant Dialog
        if (showAddParticipantDialog) {
            AddParticipantDialog(
                contacts = contacts,
                onDismiss = { showAddParticipantDialog = false },
                onAddParticipant = { pName, pNumber ->
                    onPerformCall(pName, pNumber)
                    secondParticipant = Pair(pName, pNumber)
                    showAddParticipantDialog = false
                    Toast.makeText(context, "$pName added to conference call", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
private fun ActiveCallTopHeader(
    onMinimize: () -> Unit,
    isRecording: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RecPulseHeader")
    val recPulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RecScaleHeader"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Glassmorphic Minimize Icon Button
        IconButton(
            onClick = onMinimize,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E293B).copy(alpha = 0.8f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Minimize Call",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        // HD Voice / Security Badge
        Surface(
            shape = CircleShape,
            color = Color(0xFF1E293B).copy(alpha = 0.6f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.HighQuality,
                    contentDescription = null,
                    tint = IncomingGreen,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "HD Voice",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Animated Recording Status Pill
        if (isRecording) {
            Surface(
                shape = CircleShape,
                color = MissedRed.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, MissedRed.copy(alpha = 0.5f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .scale(recPulseScale)
                            .background(MissedRed, CircleShape)
                    )
                    Text(
                        text = "REC",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MissedRed
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.width(48.dp))
        }
    }
}

@Composable
private fun CallerInformationSection(
    name: String,
    number: String,
    photoUri: String?,
    secondParticipant: Pair<String, String>?,
    isHold: Boolean,
    durationText: String,
    isMuted: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "AvatarPulse")
    val avatarRingScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AvatarRing"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 12.dp)
    ) {
        // Avatar Ring with Dynamic Pulsing Halo
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(bottom = 20.dp)
        ) {
            if (!isHold) {
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .scale(avatarRingScale)
                        .border(
                            width = 2.dp,
                            color = ElectricIndigo.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy((-20).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ContactAvatar(
                    name = name.ifBlank { number },
                    photoUri = photoUri,
                    size = 100.dp
                )
                if (secondParticipant != null) {
                    ContactAvatar(
                        name = secondParticipant.first.ifBlank { secondParticipant.second },
                        size = 100.dp
                    )
                }
            }
        }

        val displayName = if (secondParticipant != null) {
            "${name.ifBlank { number }} & ${secondParticipant.first.ifBlank { secondParticipant.second }}"
        } else {
            name.ifBlank { number }
        }

        // Caller Name
        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontSize = 26.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Phone Number / Conference Tag
        if (secondParticipant != null) {
            Surface(
                shape = CircleShape,
                color = ElectricIndigo.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, ElectricIndigo.copy(alpha = 0.5f))
            ) {
                Text(
                    text = "3-Way Conference Call",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }
        } else if (number.isNotBlank()) {
            Text(
                text = number,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.LightGray.copy(alpha = 0.8f),
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Call Timer & Status Indicator Badge
        Surface(
            shape = CircleShape,
            color = if (isHold) Color(0xFFF59E0B).copy(alpha = 0.2f) else Color(0xFF1E293B).copy(alpha = 0.8f),
            border = BorderStroke(
                1.dp,
                if (isHold) Color(0xFFF59E0B).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.15f)
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                if (isHold) {
                    Text(
                        text = "ON HOLD",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF59E0B)
                    )
                } else {
                    // Animated Sound Wave Equalizer Bars
                    if (!isMuted) {
                        AnimatedEqualizerBars()
                    }

                    Text(
                        text = durationText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ElectricIndigo,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedEqualizerBars() {
    val infiniteTransition = rememberInfiniteTransition(label = "EqAnim")

    val bar1Height by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Bar1"
    )

    val bar2Height by infiniteTransition.animateFloat(
        initialValue = 16f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Bar2"
    )

    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Bar3"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.height(18.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(bar1Height.dp)
                .background(ElectricIndigo, RoundedCornerShape(2.dp))
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(bar2Height.dp)
                .background(ElectricIndigo, RoundedCornerShape(2.dp))
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(bar3Height.dp)
                .background(ElectricIndigo, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun CallControlsGrid(
    isMuted: Boolean,
    onMuteToggle: () -> Unit,
    isSpeakerOn: Boolean,
    onSpeakerToggle: () -> Unit,
    isKeypadOpen: Boolean,
    onKeypadToggle: () -> Unit,
    onAddCallClick: () -> Unit,
    isRecording: Boolean,
    onRecordToggle: () -> Unit,
    isHold: Boolean,
    onHoldToggle: () -> Unit
) {
    // Modern 3x2 Control Grid Layout (Logos Only)
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Row 1: Mute | Keypad | Speaker
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CallControlButton(
                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (isMuted) "Muted" else "Mute",
                isActive = isMuted,
                activeColor = MissedRed,
                onClick = onMuteToggle
            )

            CallControlButton(
                icon = Icons.Default.Dialpad,
                contentDescription = if (isKeypadOpen) "Hide Pad" else "Keypad",
                isActive = isKeypadOpen,
                activeColor = ElectricIndigo,
                onClick = onKeypadToggle
            )

            CallControlButton(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = if (isSpeakerOn) "Speaker On" else "Speaker",
                isActive = isSpeakerOn,
                activeColor = ElectricIndigo,
                onClick = onSpeakerToggle
            )
        }

        // Row 2: Add Call | Record | Hold
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CallControlButton(
                icon = Icons.Default.PersonAdd,
                contentDescription = "Add Call",
                isActive = false,
                onClick = onAddCallClick
            )

            CallControlButton(
                icon = Icons.Default.GraphicEq,
                contentDescription = if (isRecording) "Recording" else "Record",
                isActive = isRecording,
                activeColor = MissedRed,
                onClick = onRecordToggle
            )

            CallControlButton(
                icon = if (isHold) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = if (isHold) "Resume" else "Hold",
                isActive = isHold,
                activeColor = Color(0xFFF59E0B),
                onClick = onHoldToggle
            )
        }
    }
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    activeColor: Color = ElectricIndigo,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isActive) activeColor else Color(0xFF1E293B).copy(alpha = 0.75f),
        animationSpec = tween(250),
        label = "BtnBgColor"
    )

    val contentColor by animateColorAsState(
        targetValue = Color.White,
        animationSpec = tween(250),
        label = "BtnContentColor"
    )

    Surface(
        shape = CircleShape,
        color = backgroundColor,
        border = BorderStroke(
            1.dp,
            if (isActive) activeColor.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.12f)
        ),
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .clickable { onClick() }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun EndCallButton(
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MissedRed,
        shadowElevation = 12.dp,
        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.3f)),
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .clickable { onClick() }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.Default.CallEnd,
                contentDescription = "End Call",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
private fun DtmfKeypadOverlay(
    digits: String,
    onDigitClick: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = Color(0xFF0F172A),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DTMF Keypad",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Keypad",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Entered Digits Display Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = digits.ifBlank { "Tap numbers..." },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (digits.isBlank()) Color.Gray else ElectricIndigo,
                    fontSize = 22.sp
                )

                if (digits.isNotEmpty()) {
                    IconButton(onClick = onBackspace) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Backspace",
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // DTMF Grid
            val keypadRows = listOf(
                listOf('1' to "", '2' to "ABC", '3' to "DEF"),
                listOf('4' to "GHI", '5' to "JKL", '6' to "MNO"),
                listOf('7' to "PQRS", '8' to "TUV", '9' to "WXYZ"),
                listOf('*' to "", '0' to "+", '#' to "")
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                keypadRows.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEach { (digit, subtext) ->
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .clickable { onDigitClick(digit) }
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = digit.toString(),
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    if (subtext.isNotEmpty()) {
                                        Text(
                                            text = subtext,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.LightGray.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ActiveCallScreenPreview() {
    MphoneTheme {
        ActiveCallScreen(
            name = "Sarah Jenkins",
            number = "+1 (555) 234-5678",
            onEndCall = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DtmfKeypadOverlayPreview() {
    MphoneTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0F17))
        ) {
            DtmfKeypadOverlay(
                digits = "1234#",
                onDigitClick = {},
                onBackspace = {},
                onClose = {}
            )
        }
    }
}
