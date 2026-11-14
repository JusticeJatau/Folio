package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = ForestEmerald,
    onPrimary = Color.White,
    primaryContainer = SoftSage,
    onPrimaryContainer = DeepPine,
    secondary = Terracotta,
    onSecondary = Color.White,
    secondaryContainer = SoftClay,
    onSecondaryContainer = Color(0xFF5D210B),
    tertiary = OchreGold,
    onTertiary = Color.White,
    tertiaryContainer = SoftAmber,
    onTertiaryContainer = Color(0xFF473004),
    background = IvoryPaper,
    onBackground = InkPrimary,
    surface = CrispCard,
    onSurface = InkPrimary,
    surfaceVariant = WarmSurface,
    onSurfaceVariant = InkSecondary,
    outline = PaperBorder,
    outlineVariant = Color(0xFFECE7DC),
    error = Color(0xFFB3261E),
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = MintSage,
    onPrimary = Color(0xFF0A2E21),
    primaryContainer = DeepSageContainer,
    onPrimaryContainer = Color(0xFFB3F0D7),
    secondary = WarmPeach,
    onSecondary = Color(0xFF421708),
    secondaryContainer = DeepClayContainer,
    onSecondaryContainer = Color(0xFFFFD6C6),
    tertiary = Color(0xFFE5BA66),
    onTertiary = Color(0xFF3A2803),
    tertiaryContainer = Color(0xFF4B370B),
    onTertiaryContainer = Color(0xFFFFE4AF),
    background = NightPaper,
    onBackground = ParchmentText,
    surface = NightCard,
    onSurface = ParchmentText,
    surfaceVariant = NightSurface,
    onSurfaceVariant = ParchmentSecondary,
    outline = NightBorder,
    outlineVariant = Color(0xFF2B2E28),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

val FolioShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun FolioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FolioTypography,
        shapes = FolioShapes,
        content = content
    )
}
