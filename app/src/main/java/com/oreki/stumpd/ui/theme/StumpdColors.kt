package com.oreki.stumpd.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The semantic colours Material's scheme doesn't carry but cricket needs.
 *
 * A scorecard is full of judgements — a wicket is bad, a milestone is good, an economy rate is one
 * or the other — and `colorScheme` offers only `error` for all of it. Screens were reaching for
 * hard-coded greens and ambers instead, which broke in dark mode and clashed with 7 of the 8
 * palettes.
 *
 * These are fixed pairs rather than palette-derived, for the same reason this app pins `error`:
 * "good" has to stay green whichever seed colour the user picked, or it stops meaning anything.
 * They are chosen to hold up against every scheme's `surface`.
 */
@Immutable
data class StumpdColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

private val LightStumpdColors = StumpdColors(
    success = Color(0xFF1B6B3A),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFA7F0BA),
    onSuccessContainer = Color(0xFF00210E),
    warning = Color(0xFF8A5000),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFDDB3),
    onWarningContainer = Color(0xFF2B1700),
)

private val DarkStumpdColors = StumpdColors(
    success = Color(0xFF8CD3A3),
    onSuccess = Color(0xFF003919),
    successContainer = Color(0xFF00522A),
    onSuccessContainer = Color(0xFFA7F0BA),
    warning = Color(0xFFFFB95C),
    onWarning = Color(0xFF492900),
    warningContainer = Color(0xFF683C00),
    onWarningContainer = Color(0xFFFFDDB3),
)

internal fun stumpdColorsFor(darkTheme: Boolean): StumpdColors =
    if (darkTheme) DarkStumpdColors else LightStumpdColors

/**
 * Provided by [StumpdTheme]. Defaults to the light set so a stray preview can't crash, but every
 * real screen gets the set matching the user's dark-mode choice — not the system's, which is the
 * mistake the old `successContainerAdaptive()` helper made.
 */
val LocalStumpdColors = staticCompositionLocalOf { LightStumpdColors }

/** `MaterialTheme.stumpd.success` reads alongside `MaterialTheme.colorScheme.primary`. */
val MaterialTheme.stumpd: StumpdColors
    @Composable @ReadOnlyComposable get() = LocalStumpdColors.current
