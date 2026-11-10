package com.mphone.ui.phone

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mphone.service.MphoneInCallService
import com.mphone.ui.components.ContactAvatar
import com.mphone.ui.theme.ElectricIndigo
import com.mphone.ui.theme.ElectricViolet
import com.mphone.ui.theme.MissedRed
import com.mphone.ui.theme.MphoneTheme
import kotlinx.coroutines.delay

@Composable
fun OutgoingCallScreen(
    name: String,
    number: String,
    onCancelCall: () -> Unit,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier,
    photoUri: String? = null,
    onCallAnswered: () -> Unit = {}
) {
    val context = LocalContext.current

    // Fallback auto-transition if InCallService is not active
    LaunchedEffect(Unit) {
        if (MphoneInCallService.activeInstance == null) {
            delay(4000L)
            onCallAnswered()
        }
    }

    // Concentric Avatar Sonar Animations
    val infiniteTransition = rememberInfiniteTransition(label = "AvatarSonar")

    val ringScale1 by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Ring1"
    )

    val ringScale2 by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Ring2"
    )

    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RingAlpha"
    )

    // Dynamic vertical mesh gradient background
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F172A), // Deep Slate Navy
            Color(0xFF1E1B4B), // Dark Indigo Slate
            Color(0xFF020617)  // Obsidian Black
        )
    )

    Surface(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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

                // Dark Scrim Overlay for Legibility
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

            // Ambient glowing radial aura behind avatar
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .align(Alignment.Center)
                    .padding(bottom = 80.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ElectricIndigo.copy(alpha = 0.28f),
                                ElectricViolet.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Bar with Minimize Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                }

                // Center Caller Display & 2D Phone Radio Wave Animation
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    // Contact Avatar with Pulsing Rings
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(210.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .scale(ringScale2)
                                .border(
                                    width = 1.5.dp,
                                    color = ElectricIndigo.copy(alpha = ringAlpha * 0.5f),
                                    shape = CircleShape
                                )
                        )

                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .scale(ringScale1)
                                .background(
                                    color = ElectricIndigo.copy(alpha = ringAlpha * 0.3f),
                                    shape = CircleShape
                                )
                        )

                        ContactAvatar(
                            name = name.ifBlank { number },
                            photoUri = photoUri,
                            size = 110.dp
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Contact Name
                    Text(
                        text = name.ifBlank { number },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 26.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Phone Number
                    if (number.isNotBlank()) {
                        Text(
                            text = number,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.LightGray.copy(alpha = 0.8f),
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // 2D Phone + Radio Waves Animation (360 Spin & Shaking)
                    PhoneRadioWavesAnimation()
                }

                // Bottom Cancel / End Call Action Button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 36.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MissedRed,
                        shadowElevation = 12.dp,
                        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .clickable { onCancelCall() }
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
            }
        }
    }
}

/**
 * Modern 2D Smartphone Vector Graphic with 360° Rotation + Shaking and Radiating Radio/Signal Waves
 */
@Composable
private fun PhoneRadioWavesAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "2DPhoneRadioWaves")

    // Radio Arc Wave Pulsing
    val wave1Scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Wave1"
    )

    val wave2Scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Wave2"
    )

    val wave3Scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Wave3"
    )

    val waveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WaveAlpha"
    )

    // Continuous 360 Degree Rotation
    val phone360Rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Phone360Rotation"
    )

    // Rapid Shaking/Vibration Jitter Offset
    val phoneShakeOffset by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(90, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PhoneShakeOffset"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(240.dp, 120.dp)
    ) {
        // Radiating 2D Radio Wave Arcs Canvas (Left & Right)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val strokeWidth = 3.dp.toPx()

            val scales = listOf(wave1Scale, wave2Scale, wave3Scale)
            scales.forEachIndexed { index, scale ->
                val radius = 32.dp.toPx() * scale
                val alpha = (waveAlpha * (1f - (index * 0.28f))).coerceIn(0f, 1f)
                val waveColor = ElectricIndigo.copy(alpha = alpha)

                // Left Radio Signal Arcs ()))
                drawArc(
                    color = waveColor,
                    startAngle = 135f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(centerX - radius - 32.dp.toPx(), centerY - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Right Radio Signal Arcs (((
                drawArc(
                    color = waveColor,
                    startAngle = -45f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(centerX - radius + 32.dp.toPx(), centerY - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        // Sleek 2D Smartphone Frame with 360° Rotation and Shaking Offset
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF1E293B),
            border = BorderStroke(1.5.dp, ElectricIndigo),
            shadowElevation = 10.dp,
            modifier = Modifier
                .size(52.dp, 88.dp)
                .offset(x = phoneShakeOffset.dp, y = (phoneShakeOffset / 2f).dp)
                .rotate(phone360Rotation)
        ) {
            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF334155),
                                Color(0xFF0F172A)
                            )
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
            ) {
                // Top Camera Punch Hole
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(6.dp)
                        .background(Color.Black, CircleShape)
                )

                // Centered Calling Phone Icon
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    tint = ElectricIndigo,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(22.dp)
                )

                // Bottom Home Bar Line
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                        .width(20.dp)
                        .height(2.dp)
                        .background(Color.White.copy(alpha = 0.5f), CircleShape)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun OutgoingCallScreenPreview() {
    MphoneTheme {
        OutgoingCallScreen(
            name = "Sarah Jenkins",
            number = "+1 (555) 234-5678",
            onCancelCall = {},
            onMinimize = {}
        )
    }
}
