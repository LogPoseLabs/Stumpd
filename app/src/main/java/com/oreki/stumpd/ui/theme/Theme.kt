package com.oreki.stumpd.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Brand Colors - Cricket Theme
private val CricketGreen = Color(0xFF0D7C66) // Deep cricket field green
private val CricketGreenLight = Color(0xFF41B3A2) // Lighter green
private val CricketGreenDark = Color(0xFF085F4F) // Darker green
private val StadiumRed = Color(0xFFE63946) // Vibrant red for action
private val BallRed = Color(0xFFD32F2F) // Cricket ball red
private val TrophyGold = Color(0xFFFFB300) // Golden trophy
private val NavyBlue = Color(0xFF0A2342) // Deep navy
private val NavyLight = Color(0xFF1F3A5F) // Light navy
private val White = Color(0xFFFFFFFF)
private val OffWhite = Color(0xFFFAFAFA)

// Light scheme - Fresh cricket field aesthetic
private val LightColorScheme = lightColorScheme(
    primary = CricketGreen,
    onPrimary = White,
    primaryContainer = Color(0xFFB8E6D5), // Light mint green
    onPrimaryContainer = Color(0xFF00261E), // Dark green
    
    secondary = TrophyGold,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFFFFE082), // Light gold
    onSecondaryContainer = Color(0xFF3E2723), // Dark brown

    tertiary = Color(0xFF0277BD), // Action blue
    onTertiary = White,
    tertiaryContainer = Color(0xFFB3E5FC), // Light blue
    onTertiaryContainer = NavyBlue,

    background = OffWhite,
    onBackground = NavyBlue,

    surface = White,
    onSurface = NavyBlue,
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = NavyLight,
    
    surfaceContainer = Color(0xFFF7F9F8),
    surfaceContainerHigh = Color(0xFFEEF1F0),
    surfaceContainerHighest = Color(0xFFE8EBEa),

    outline = NavyLight.copy(alpha = 0.3f),
    error = StadiumRed,
    onError = White,
    errorContainer = Color(0xFFFFCDD2), // Light red
    onErrorContainer = Color(0xFF5F0000) // Dark red
)

// Dark scheme - Night stadium aesthetic
private val DarkColorScheme = darkColorScheme(
    primary = CricketGreenLight,
    onPrimary = Color(0xFF00261E),
    primaryContainer = CricketGreenDark,
    onPrimaryContainer = Color(0xFFB8E6D5),
    
    secondary = TrophyGold,
    onSecondary = Color(0xFF3E2723),
    secondaryContainer = Color(0xFF5D4037), // Dark amber
    onSecondaryContainer = Color(0xFFFFD54F), // Light gold

    tertiary = Color(0xFF4FC3F7), // Bright blue
    onTertiary = Color(0xFF002F3F),
    tertiaryContainer = Color(0xFF004D61), // Dark blue
    onTertiaryContainer = Color(0xFFB3E5FC),

    background = Color(0xFF0E1415), // Very dark green-gray
    onBackground = Color(0xFFE2F1ED),

    surface = Color(0xFF171D1E), // Dark surface
    onSurface = Color(0xFFE2F1ED),
    surfaceVariant = Color(0xFF1F2628),
    onSurfaceVariant = Color(0xFFBDCCC8),
    
    surfaceContainer = Color(0xFF1A2122),
    surfaceContainerHigh = Color(0xFF232B2C),
    surfaceContainerHighest = Color(0xFF2D3536),

    outline = Color(0xFF5A6B68),
    error = Color(0xFFFF6B6B), // Softer red for dark mode
    onError = Color(0xFF370000),
    errorContainer = Color(0xFF5F0000),
    onErrorContainer = Color(0xFFFFCDD2)
)

@Composable
fun StumpdTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
// Use dynamic colors on Android 12+, but fall back to brand
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme =
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (darkTheme) DarkColorScheme else LightColorScheme
        }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = StumpdTypography,
        shapes = StumpdShapes,
        content = content
    )
    val shapes = Shapes(
        small = MaterialTheme.shapes.small.copy(all = CornerSize(14.dp)),
        medium = MaterialTheme.shapes.medium.copy(all = CornerSize(14.dp)),
        large = MaterialTheme.shapes.large.copy(all = CornerSize(14.dp)),
    )
}


