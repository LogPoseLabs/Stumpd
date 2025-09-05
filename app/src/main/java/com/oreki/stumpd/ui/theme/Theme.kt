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

private val StumpdTeal = Color(0xFF22AFA0) // teal brand
private val StumpdTealDark = Color(0xFF158F82)
private val StumpdNavy = Color(0xFF0C2B3A) // ink/navy
private val StumpdNavyLight = Color(0xFF1F475A)
private val JokerOrange = Color(0xFFFF9800)
private val ErrorRed = Color(0xFFF44336)
private val White = Color(0xFFFFFFFF)

// Light scheme tuned for readability on white surfaces
private val LightColorScheme = lightColorScheme(
    primary = StumpdTeal,
    onPrimary = White,
    primaryContainer = StumpdTeal.copy(alpha = 0.15f),
    onPrimaryContainer = StumpdNavy,
    secondary = JokerOrange,
    onSecondary = Color.Black,
    secondaryContainer = JokerOrange.copy(alpha = 0.15f),
    onSecondaryContainer = StumpdNavy,

    tertiary = Color(0xFF2196F3),           // info/action blue
    onTertiary = White,
    tertiaryContainer = Color(0xFFBBDEFB),
    onTertiaryContainer = StumpdNavy,

    background = Color(0xFFFAFCFB),
    onBackground = StumpdNavy,

    surface = White,
    onSurface = StumpdNavy,
    surfaceVariant = Color(0xFFF2F5F4),
    onSurfaceVariant = StumpdNavyLight,

    outline = StumpdNavyLight.copy(alpha = 0.35f),
    error = ErrorRed,
    onError = White,
    errorContainer = ErrorRed.copy(alpha = 0.12f),
    onErrorContainer = ErrorRed
)

// Dark scheme for high contrast
private val DarkColorScheme = darkColorScheme(
    primary = StumpdTeal,
    onPrimary = Color.Black,
    primaryContainer = StumpdTealDark,
    onPrimaryContainer = White,
    secondary = JokerOrange,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF5A3B00),
    onSecondaryContainer = Color(0xFFFFE0B2),

    tertiary = Color(0xFF64B5F6),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF0D2A3A),
    onTertiaryContainer = Color(0xFFB3E5FC),

    background = Color(0xFF0E1719),
    onBackground = Color(0xFFE6F2F0),

    surface = Color(0xFF101A1C),
    onSurface = Color(0xFFE6F2F0),
    surfaceVariant = Color(0xFF142124),
    onSurfaceVariant = Color(0xFFB6D0CB),

    outline = Color(0xFF7FA5A0),
    error = ErrorRed,
    onError = Color.Black,
    errorContainer = Color(0xFF3B1211),
    onErrorContainer = Color(0xFFFFDAD6)
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


