package com.mphone.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mphone.ui.theme.ElectricIndigo
import com.mphone.ui.theme.IncomingGreen
import com.mphone.ui.theme.MphoneTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SplashAnimations")

    // 1. Phone Ring Radar Wave Animation
    val ringPulse by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RingPulse"
    )

    // 2. Phone Ring Vibration Wobble Animation
    val phoneWobble by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(180, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PhoneWobble"
    )

    // 3. Flying Letter Flight Trajectory Animation
    val flyingLetterOffset by infiniteTransition.animateFloat(
        initialValue = -40f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FlyingLetter"
    )

    // Fade-in Animatable for Tagline
    val titleAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        titleAlpha.animateTo(1f, tween(800))
        delay(2000)
        onSplashFinished()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A) // Dark Slate Background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                // Animated Phone Ring & Flying Letter Icon Stack
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(200.dp)
                ) {
                    // Outer Ring 2
                    Box(
                        modifier = Modifier
                            .size(170.dp)
                            .scale(ringPulse)
                            .clip(CircleShape)
                            .border(1.5.dp, IncomingGreen.copy(alpha = 0.3f), CircleShape)
                    )

                    // Outer Ring 1
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .scale(ringPulse * 0.9f)
                            .clip(CircleShape)
                            .background(IncomingGreen.copy(alpha = 0.15f))
                    )

                    // Centered Phone Icon (Ringing Wobble)
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(IncomingGreen, Color(0xFF059669))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Ringing Phone",
                            tint = Color.White,
                            modifier = Modifier
                                .size(40.dp)
                                .rotate(phoneWobble)
                        )
                    }

                    // Flying Letter (Swooping Flying Envelope Icon)
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (flyingLetterOffset * 1.5f).roundToInt(),
                                    y = (-flyingLetterOffset * 1.2f).roundToInt()
                                )
                            }
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(ElectricIndigo, Color(0xFF4F46E5))
                                )
                            )
                            .align(Alignment.TopEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Flying Letter",
                            tint = Color.White,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(-25f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Tagline
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.alpha(titleAlpha.value)
                ) {
                    Text(
                        text = "Calls • Contacts • Messages",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 2.sp
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Modern Circular Loading Progress
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
            }
        }
    }
}

@Preview
@Composable
fun SplashScreenPreview() {
    MphoneTheme {
        SplashScreen(onSplashFinished = {})
    }
}
