package com.mphone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mphone.ui.theme.AvatarColors

@Composable
fun ContactAvatar(
    name: String,
    modifier: Modifier = Modifier,
    photoUri: String? = null,
    colorIndex: Int = 0,
    size: Dp = 48.dp,
    showBorder: Boolean = true
) {
    val context = LocalContext.current
    val bgColor = AvatarColors.getOrElse(colorIndex % AvatarColors.size) { AvatarColors[0] }
    val initial = name.trim().take(1).uppercase().ifBlank { "?" }

    val gradientBorder = Brush.linearGradient(
        colors = listOf(bgColor.copy(alpha = 0.8f), Color.White.copy(alpha = 0.4f))
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (showBorder) Modifier.border(1.5.dp, gradientBorder, CircleShape)
                else Modifier
            )
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
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
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            Text(
                text = initial,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp
            )
        }
    }
}
