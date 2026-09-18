package com.oreki.stumpd.ui.scoring

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.oreki.stumpd.ui.theme.StumpdMotion

/**
 * Answers a boundary or a wicket with a brief flourish.
 *
 * Rendered as a sibling *above* the scoring surface, never inside it: the live tab is a large,
 * non-skippable composable, and animating within it would recompose the whole scoreboard every
 * frame. Here the animation's invalidations are scoped to a composable that reads nothing else.
 *
 * Takes no touches and delays no input — scorers tap fast, and a flourish that swallows the next
 * ball would break the screen's actual job. There are no infinite transitions either; a match
 * runs for hours with the phone in hand.
 */
@Composable
fun CelebrationOverlay(
    effect: ScoringEffect?,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (effect == null) return

    val progress = remember(effect) { Animatable(0f) }
    LaunchedEffect(effect) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = if (effect == ScoringEffect.Wicket) StumpdMotion.QUICK_MS * 2
                else StumpdMotion.EXPRESSIVE_MS,
            ),
        )
        onFinished()
    }

    val accent = when (effect) {
        ScoringEffect.Four -> MaterialTheme.colorScheme.primary
        ScoringEffect.Six -> MaterialTheme.colorScheme.tertiary
        ScoringEffect.Wicket -> MaterialTheme.colorScheme.error
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val t = progress.value
        when (effect) {
            // A boundary expands outward from the score and fades: warm, upward, brief.
            ScoringEffect.Four, ScoringEffect.Six -> {
                val centre = Offset(size.width / 2f, size.height * 0.18f)
                val radius = size.minDimension * (0.18f + 0.45f * t)
                drawCircle(
                    color = accent.copy(alpha = (1f - t) * 0.45f),
                    radius = radius,
                    center = centre,
                    style = Stroke(width = size.minDimension * 0.012f * (1f - t) * 4f),
                )
                if (effect == ScoringEffect.Six) {
                    // A six gets a bloom behind the score as well as the ring.
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = (1f - t) * 0.28f), Color.Transparent),
                            center = centre,
                            radius = radius,
                        ),
                        radius = radius,
                        center = centre,
                    )
                }
            }
            // A wicket goes the other way: a cold flash over the whole surface, gone quickly.
            // The contrast with the boundary is what makes either read as deliberate.
            ScoringEffect.Wicket -> {
                drawRect(color = accent.copy(alpha = (1f - t) * 0.22f))
            }
        }
    }
}
