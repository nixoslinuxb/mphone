package com.mphone.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Modern Liquid Glass Electric Palette
val ElectricIndigo = Color(0xFF6366F1)
val ElectricViolet = Color(0xFF8B5CF6)
val ElectricTeal = Color(0xFF10B981)
val ElectricRose = Color(0xFFF43F5E)
val ElectricCyan = Color(0xFF06B6D4)

// Liquid Glass Dark Surface System
val DarkBackground = Color(0xFF070B14)
val DarkSurface = Color(0xFF0F172A)
val DarkSurfaceContainer = Color(0xFF1E293B)
val DarkSurfaceContainerHigh = Color(0xFF334155)

// Liquid Specular Highlight Brush
val GlassBorderBrush = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.35f),
        Color.White.copy(alpha = 0.08f),
        Color.White.copy(alpha = 0.20f)
    )
)

val LiquidGlowBrush = Brush.radialGradient(
    colors = listOf(
        ElectricIndigo.copy(alpha = 0.40f),
        ElectricViolet.copy(alpha = 0.15f),
        Color.Transparent
    )
)

// Status Colors
val IncomingGreen = Color(0xFF10B981)
val OutgoingBlue = Color(0xFF3B82F6)
val MissedRed = Color(0xFFF43F5E)

// Avatar Color Palette
val AvatarColors = listOf(
    Color(0xFF6366F1), // Indigo
    Color(0xFFEC4899), // Pink
    Color(0xFF10B981), // Emerald
    Color(0xFFF59E0B), // Amber
    Color(0xFF8B5CF6), // Purple
    Color(0xFF06B6D4)  // Cyan
)
