package com.oreki.stumpd.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * All palettes a user can choose in Appearance settings.
 *
 * [PITCH_GREEN] keeps the app's original hand-tuned brand scheme unchanged.
 * The other curated palettes are generated from a representative seed color via
 * [schemeFromSeed] so every palette gets a complete, consistent Material3 [ColorScheme].
 * [DEVICE_DYNAMIC] wraps Android 12+ wallpaper-based dynamic color.
 * [CUSTOM] is generated from a user-picked seed color stored in preferences.
 */
enum class StumpdPaletteId(val displayName: String, val seed: Color?) {
    PITCH_GREEN("Pitch Green", Color(0xFF0D7C66)),
    OCEAN_BLUE("Ocean Blue", Color(0xFF0277BD)),
    SUNSET_AMBER("Sunset Amber", Color(0xFFFF7043)),
    ROYAL_PURPLE("Royal Purple", Color(0xFF7C4DFF)),
    CRIMSON_STADIUM("Crimson Stadium", Color(0xFFE63946)),
    MIDNIGHT_SLATE("Midnight Slate", Color(0xFF546E7A)),
    DEVICE_DYNAMIC("Match My Device", null),
    CUSTOM("Custom", null);

    companion object {
        fun fromStorageKey(key: String?): StumpdPaletteId =
            entries.firstOrNull { it.name == key } ?: PITCH_GREEN

        /** Palettes to show as fixed swatches in the picker grid, in display order. */
        fun selectable(): List<StumpdPaletteId> = listOf(
            PITCH_GREEN, OCEAN_BLUE, SUNSET_AMBER, ROYAL_PURPLE, CRIMSON_STADIUM, MIDNIGHT_SLATE,
        ) + (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) listOf(DEVICE_DYNAMIC) else emptyList())
    }
}

// Original brand colors - Cricket Theme (kept as-is for the default palette)
private val CricketGreen = Color(0xFF0D7C66)
private val CricketGreenLight = Color(0xFF41B3A2)
private val CricketGreenDark = Color(0xFF085F4F)
private val StadiumRed = Color(0xFFE63946)
private val TrophyGold = Color(0xFFFFB300)
private val NavyBlue = Color(0xFF0A2342)
private val NavyLight = Color(0xFF1F3A5F)
private val White = Color(0xFFFFFFFF)
private val OffWhite = Color(0xFFFAFAFA)

internal val PitchGreenLight = lightColorScheme(
    primary = CricketGreen,
    onPrimary = White,
    primaryContainer = Color(0xFFB8E6D5),
    onPrimaryContainer = Color(0xFF00261E),

    secondary = TrophyGold,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFFCFE9DE),
    onSecondaryContainer = Color(0xFF10352A),

    tertiary = Color(0xFF0277BD),
    onTertiary = White,
    tertiaryContainer = Color(0xFFB3E5FC),
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
    error = Color(0xFFD32F2F),
    onError = White,
    errorContainer = Color(0xFFFFCDD2),
    onErrorContainer = Color(0xFF5F0000)
)

internal val PitchGreenDark = darkColorScheme(
    primary = CricketGreenLight,
    onPrimary = Color(0xFF00261E),
    primaryContainer = CricketGreenDark,
    onPrimaryContainer = Color(0xFFB8E6D5),

    secondary = TrophyGold,
    onSecondary = Color(0xFF3E2723),
    secondaryContainer = Color(0xFF27423A),
    onSecondaryContainer = Color(0xFFBFE3D5),

    tertiary = Color(0xFF4FC3F7),
    onTertiary = Color(0xFF002F3F),
    tertiaryContainer = Color(0xFF004D61),
    onTertiaryContainer = Color(0xFFB3E5FC),

    background = Color(0xFF0E1415),
    onBackground = Color(0xFFE2F1ED),

    surface = Color(0xFF171D1E),
    onSurface = Color(0xFFE2F1ED),
    surfaceVariant = Color(0xFF1F2628),
    onSurfaceVariant = Color(0xFFBDCCC8),

    surfaceContainer = Color(0xFF1A2122),
    surfaceContainerHigh = Color(0xFF232B2C),
    surfaceContainerHighest = Color(0xFF2D3536),

    outline = Color(0xFF5A6B68),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF370000),
    errorContainer = Color(0xFF5F0000),
    onErrorContainer = Color(0xFFFFCDD2)
)

private fun Color.toHsv(): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    return hsv
}

internal fun hsvColor(hue: Float, saturation: Float, value: Float): Color {
    val normalizedHue = ((hue % 360f) + 360f) % 360f
    return Color(
        android.graphics.Color.HSVToColor(
            floatArrayOf(normalizedHue, saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f))
        )
    )
}

/**
 * Derives a complete, readable Material3 [ColorScheme] from a single seed color.
 * Used both for curated palettes (fixed seeds) and the user's custom color choice.
 * Deterministic: the same seed + dark flag always produces the same scheme.
 */
/**
 * Picks the text colour for a generated container.
 *
 * The seed ramp sets a *value* for each role, not a contrast ratio, so a mid-tone primary could
 * end up with white text at 3.6:1 — legible-ish, and below the 4.5:1 that 14sp button labels
 * need. [preferred] is the tinted colour the palette would like to use; it's kept when it clears
 * the bar and swapped for plain black or white (whichever wins) when it doesn't. Asserted across
 * every palette and mode by `ThemeMatrixTest`.
 */
private fun readableOn(container: Color, preferred: Color): Color {
    fun ratio(a: Color, b: Color): Float {
        val la = a.luminance() + 0.05f
        val lb = b.luminance() + 0.05f
        return if (la > lb) la / lb else lb / la
    }
    if (ratio(preferred, container) >= 4.5f) return preferred
    return if (ratio(Color.White, container) >= ratio(Color.Black, container)) Color.White
    else Color.Black
}

fun schemeFromSeed(seed: Color, dark: Boolean): ColorScheme {
    val hsv = seed.toHsv()
    val hue = hsv[0]
    val sat = hsv[1].coerceIn(0.35f, 0.85f)
    val secondaryHue = hue + 40f
    val tertiaryHue = hue - 40f

    return if (!dark) {
        lightColorScheme(
            primary = hsvColor(hue, sat, 0.55f),
            onPrimary = readableOn(hsvColor(hue, sat, 0.55f), Color.White),
            primaryContainer = hsvColor(hue, sat * 0.55f, 0.90f),
            onPrimaryContainer = hsvColor(hue, sat, 0.20f),

            secondary = hsvColor(secondaryHue, sat * 0.8f, 0.50f),
            onSecondary = readableOn(hsvColor(secondaryHue, sat * 0.8f, 0.50f), Color.White),
            secondaryContainer = hsvColor(secondaryHue, sat * 0.5f, 0.90f),
            onSecondaryContainer = hsvColor(secondaryHue, sat, 0.20f),

            tertiary = hsvColor(tertiaryHue, sat * 0.8f, 0.50f),
            onTertiary = readableOn(hsvColor(tertiaryHue, sat * 0.8f, 0.50f), Color.White),
            tertiaryContainer = hsvColor(tertiaryHue, sat * 0.5f, 0.90f),
            onTertiaryContainer = hsvColor(tertiaryHue, sat, 0.20f),

            background = hsvColor(hue, 0.04f, 0.99f),
            onBackground = hsvColor(hue, 0.30f, 0.15f),

            surface = hsvColor(hue, 0.02f, 0.995f),
            onSurface = hsvColor(hue, 0.30f, 0.15f),
            surfaceVariant = hsvColor(hue, 0.06f, 0.94f),
            onSurfaceVariant = hsvColor(hue, 0.20f, 0.32f),

            surfaceContainer = hsvColor(hue, 0.04f, 0.97f),
            surfaceContainerHigh = hsvColor(hue, 0.05f, 0.93f),
            surfaceContainerHighest = hsvColor(hue, 0.06f, 0.90f),

            outline = hsvColor(hue, 0.15f, 0.45f),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
        )
    } else {
        darkColorScheme(
            primary = hsvColor(hue, sat * 0.7f, 0.75f),
            onPrimary = readableOn(hsvColor(hue, sat * 0.7f, 0.75f), hsvColor(hue, sat, 0.15f)),
            primaryContainer = hsvColor(hue, sat, 0.28f),
            onPrimaryContainer = hsvColor(hue, sat * 0.4f, 0.90f),

            secondary = hsvColor(secondaryHue, sat * 0.6f, 0.72f),
            onSecondary = readableOn(
                hsvColor(secondaryHue, sat * 0.6f, 0.72f),
                hsvColor(secondaryHue, sat, 0.15f),
            ),
            secondaryContainer = hsvColor(secondaryHue, sat * 0.8f, 0.30f),
            onSecondaryContainer = hsvColor(secondaryHue, sat * 0.3f, 0.90f),

            tertiary = hsvColor(tertiaryHue, sat * 0.6f, 0.72f),
            onTertiary = readableOn(
                hsvColor(tertiaryHue, sat * 0.6f, 0.72f),
                hsvColor(tertiaryHue, sat, 0.15f),
            ),
            tertiaryContainer = hsvColor(tertiaryHue, sat * 0.8f, 0.30f),
            onTertiaryContainer = hsvColor(tertiaryHue, sat * 0.3f, 0.90f),

            background = hsvColor(hue, 0.12f, 0.08f),
            onBackground = hsvColor(hue, 0.08f, 0.92f),

            surface = hsvColor(hue, 0.10f, 0.10f),
            onSurface = hsvColor(hue, 0.08f, 0.92f),
            surfaceVariant = hsvColor(hue, 0.12f, 0.16f),
            onSurfaceVariant = hsvColor(hue, 0.10f, 0.78f),

            surfaceContainer = hsvColor(hue, 0.11f, 0.13f),
            surfaceContainerHigh = hsvColor(hue, 0.12f, 0.17f),
            surfaceContainerHighest = hsvColor(hue, 0.13f, 0.21f),

            outline = hsvColor(hue, 0.10f, 0.45f),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
        )
    }
}

/** Resolves the actual [ColorScheme] to render for a given palette selection. */
fun colorSchemeFor(
    paletteId: StumpdPaletteId,
    dark: Boolean,
    customSeedColorArgb: Int?,
    context: Context,
): ColorScheme = when (paletteId) {
    StumpdPaletteId.PITCH_GREEN -> if (dark) PitchGreenDark else PitchGreenLight
    StumpdPaletteId.DEVICE_DYNAMIC -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) PitchGreenDark else PitchGreenLight
    }
    StumpdPaletteId.CUSTOM -> schemeFromSeed(
        customSeedColorArgb?.let { Color(it) } ?: (StumpdPaletteId.PITCH_GREEN.seed ?: CricketGreen),
        dark,
    )
    else -> schemeFromSeed(paletteId.seed ?: CricketGreen, dark)
}
