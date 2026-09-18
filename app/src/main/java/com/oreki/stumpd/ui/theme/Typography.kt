package com.oreki.stumpd.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val StumpdTypography = Typography(
    // Display styles - for hero sections
    displayLarge = TextStyle(
        fontSize = 57.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 64.sp,
        letterSpacing = (-1.5).sp
    ),
    displayMedium = TextStyle(
        fontSize = 45.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 52.sp,
        letterSpacing = (-1.2).sp
    ),
    displaySmall = TextStyle(
        fontSize = 36.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 44.sp,
        letterSpacing = (-0.9).sp
    ),
    
    // Headline styles - for card titles and section headers
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 40.sp,
        letterSpacing = (-0.7).sp
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp
    ),
    
    // Title styles - for card titles and important text
    titleLarge = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    
    // Body styles - for regular content
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    
    // Label styles - for buttons and labels
    labelLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * Figures that don't move.
 *
 * Proportional digits are different widths, so a score ticking 6 → 7 → 18 shifts the text either
 * side of it, and a column of run totals never lines up. `tnum` (tabular numerals) fixes the
 * advance width; `lnum` (lining figures) keeps them all cap-height. Roboto ships both, so this
 * needs no font file.
 *
 * Apply to numbers only — the big score, the runs/balls/SR columns, stat tiles. Prose set in
 * tabular figures looks subtly wrong, which is why this isn't folded into the base typography.
 */
private const val TabularFigures = "tnum, lnum"

/** The match score, at the top of the live screen and the scorecard. */
val ScoreLarge = TextStyle(
    fontSize = 40.sp,
    fontWeight = FontWeight.Bold,
    lineHeight = 44.sp,
    // Large type needs negative tracking or it reads as loose; this is most of what makes a
    // scoreboard look like a scoreboard.
    letterSpacing = (-1.5).sp,
    fontFeatureSettings = TabularFigures,
)

/** An innings total or a headline stat — smaller than the live score, same treatment. */
val ScoreMedium = TextStyle(
    fontSize = 22.sp,
    fontWeight = FontWeight.Bold,
    lineHeight = 28.sp,
    letterSpacing = (-0.5).sp,
    fontFeatureSettings = TabularFigures,
)

/** Figures inside tables and stat rows: runs, balls, wickets, averages. */
val StatValue = TextStyle(
    fontSize = 14.sp,
    fontWeight = FontWeight.Medium,
    lineHeight = 20.sp,
    fontFeatureSettings = TabularFigures,
)

/** Uppercase micro-labels above figures ("RUN RATE", "ECON"). Wide tracking, small size. */
val MicroLabel = TextStyle(
    fontSize = 11.sp,
    fontWeight = FontWeight.Medium,
    lineHeight = 16.sp,
    letterSpacing = 1.sp,
)
