package com.mphone.ui.phone

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mphone.ui.components.ContactAvatar
import com.mphone.ui.theme.ElectricIndigo
import com.mphone.ui.theme.ElectricViolet
import com.mphone.ui.theme.IncomingGreen
import com.mphone.ui.theme.MissedRed
import com.mphone.ui.theme.MphoneTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun IncomingCallScreen(
    name: String,
    number: String,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier,
    photoUri: String? = null
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current

    // Pulsing Outer Aura Animation
    val infiniteTransition = rememberInfiniteTransition(label = "PulsingAura")
    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Scale1"
    )

    // Drag Offset State for Sweep Slider (-120dp to +120dp)
    val maxDragPx = with(density) { 120.dp.toPx() }
    val dragOffsetX = remember { Animatable(0f) }

    // Dynamic Vertical Background Gradient
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F172A), // Deep Slate
            Color(0xFF1E1B4B), // Dark Indigo Slate
            Color(0xFF020617)  // Obsidian Black
        )
    )

    Surface(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
        ) {
            // Ambient glowing radial aura behind profile card
            Box(
                modifier = Modifier
                    .size(360.dp)
                    .align(Alignment.Center)
                    .padding(bottom = 60.dp)
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
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
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

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF1E293B).copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(IncomingGreen)
                            )
                            Text(
                                text = "mPhone HD Call",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                // Full Screen Glassmorphic Caller Profile Card
                Surface(
                    shape = RoundedCornerShape(36.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Profile Picture / Avatar
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(bottom = 28.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(172.dp)
                                    .scale(pulseScale1)
                                    .border(2.dp, ElectricIndigo.copy(alpha = 0.45f), CircleShape)
                            )

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
                                    modifier = Modifier
                                        .size(156.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                )
                            } else {
                                ContactAvatar(
                                    name = name.ifBlank { number },
                                    photoUri = photoUri,
                                    size = 156.dp
                                )
                            }
                        }

                        // Caller Name
                        Text(
                            text = name.ifBlank { number },
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 30.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Phone Number
                        if (number.isNotBlank() && number != name) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF0F172A).copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                            ) {
                                Text(
                                    text = number,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.LightGray.copy(alpha = 0.9f),
                                    fontSize = 17.sp,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                // Modern Glassmorphic Sweep Slider with Action Indicator Logos
                Surface(
                    shape = RoundedCornerShape(40.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                        .padding(bottom = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Left & Right Target Action Indicator Logos
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Decline Target Logo (Left)
                            Surface(
                                shape = CircleShape,
                                color = MissedRed.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, MissedRed.copy(alpha = 0.6f)),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CallEnd,
                                        contentDescription = "Decline",
                                        tint = MissedRed,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Answer Target Logo (Right)
                            Surface(
                                shape = CircleShape,
                                color = IncomingGreen.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, IncomingGreen.copy(alpha = 0.6f)),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Answer",
                                        tint = IncomingGreen,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        // Draggable Slider Handle
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(dragOffsetX.value.roundToInt(), 0) }
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color.White, Color(0xFFE2E8F0))
                                    )
                                )
                                .draggable(
                                    orientation = Orientation.Horizontal,
                                    state = rememberDraggableState { delta ->
                                        scope.launch {
                                            val newOffset = (dragOffsetX.value + delta)
                                                .coerceIn(-maxDragPx, maxDragPx)
                                            dragOffsetX.snapTo(newOffset)
                                        }
                                    },
                                    onDragStopped = {
                                        if (dragOffsetX.value >= maxDragPx * 0.65f) {
                                            onAnswer()
                                        } else if (dragOffsetX.value <= -maxDragPx * 0.65f) {
                                            onDecline()
                                        } else {
                                            scope.launch {
                                                dragOffsetX.animateTo(
                                                    0f,
                                                    spring(stiffness = Spring.StiffnessMedium)
                                                )
                                            }
                                        }
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Slide Handle",
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun IncomingCallScreenPreview() {
    MphoneTheme {
        IncomingCallScreen(
            name = "Sarah Jenkins",
            number = "+1 (555) 234-5678",
            onAnswer = {},
            onDecline = {},
            onMinimize = {}
        )
    }
}
