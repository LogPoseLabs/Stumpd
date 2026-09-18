package com.oreki.stumpd.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The app's motion vocabulary, so timings agree across screens instead of being invented per call
 * site. Everything here is deliberately short: this is feedback, not choreography.
 */
object StumpdMotion {
    /** A press, a tick, a colour change — anything that should feel instant but not abrupt. */
    const val QUICK_MS = 120

    /** The default: a card revealing, a number settling. */
    const val STANDARD_MS = 260

    /** Entrances and celebrations, where the eye needs time to follow the movement. */
    const val EXPRESSIVE_MS = 420

    /** Between staggered items. Small — the whole list should feel like one gesture. */
    const val STAGGER_STEP_MS = 40

    /**
     * How many items actually stagger. Beyond this they appear together, so item 200 doesn't wait
     * eight seconds for its turn. Nobody can tell, and the cost stays bounded.
     */
    const val STAGGER_MAX_STEPS = 7

    val enter: Easing = LinearOutSlowInEasing
    val standard: Easing = FastOutSlowInEasing

    /** For anything that should overshoot slightly — a score punching up, a chip landing. */
    fun <T> springy() = spring<T>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
}

/**
 * Tracks whether a list has already played its entrance, so rows don't re-animate every time they
 * scroll back into view.
 *
 * This is the mistake staggered lists usually make: the flag belongs to the *list*, not the row.
 * Held in [rememberSaveable] so returning from another screen doesn't replay it either.
 */
@Composable
fun rememberRevealState(): RevealState {
    var played by rememberSaveable { mutableStateOf(false) }
    val state = remember { RevealState(initiallyPlayed = played) { played = true } }

    // Freeze the list once the whole entrance window has elapsed — not when the first row
    // finishes, which would cut the remaining rows' animations short.
    if (!state.hasPlayed) {
        LaunchedEffect(Unit) {
            delay(
                (StumpdMotion.STAGGER_MAX_STEPS * StumpdMotion.STAGGER_STEP_MS +
                    StumpdMotion.STANDARD_MS).toLong()
            )
            state.freeze()
        }
    }
    return state
}

class RevealState internal constructor(
    initiallyPlayed: Boolean,
    private val markPlayed: () -> Unit,
) {
    /**
     * True once the entrance has run. Deliberately a plain field rather than Compose state:
     * flipping it must not recompose rows that are still animating.
     */
    var hasPlayed: Boolean = initiallyPlayed
        private set

    internal fun freeze() {
        if (!hasPlayed) {
            hasPlayed = true
            markPlayed()
        }
    }
}

/**
 * Fades and lifts an item into place, staggered by its position in the list.
 *
 * Only the first screenful staggers ([StumpdMotion.STAGGER_MAX_STEPS]); everything after appears
 * immediately. Pass the item's *stable id* as [key] so recycling a row doesn't restart its
 * animation, and share one [state] across the whole list.
 */
@Composable
fun Modifier.revealOnFirstPaint(
    index: Int,
    key: Any,
    state: RevealState,
): Modifier = composed(inspectorInfo = { name = "revealOnFirstPaint" }) {
    // Decided once, at this row's first composition: whether the list has already played can
    // change while this row is mid-animation, and re-reading it would snap the row to its end.
    val shouldReveal = remember(key) {
        !state.hasPlayed && index <= StumpdMotion.STAGGER_MAX_STEPS
    }
    if (!shouldReveal) return@composed this

    var revealed by remember(key) { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(
            durationMillis = StumpdMotion.STANDARD_MS,
            delayMillis = index * StumpdMotion.STAGGER_STEP_MS,
            easing = StumpdMotion.enter,
        ),
        label = "reveal",
    )

    LaunchedEffect(key) { revealed = true }

    this
        .alpha(progress)
        .graphicsLayer { translationY = (1f - progress) * 12.dp.toPx() }
}

/**
 * A number that rolls to its new value instead of snapping.
 *
 * Used for scores and stat tiles. Pair it with [ScoreLarge] / [StatValue] so the digits are
 * tabular — otherwise the text width changes mid-animation and the whole row twitches.
 *
 * Returns the value to draw, so the caller keeps control of styling:
 * `Text(animatedInt(runs).toString(), style = MaterialTheme.typography.…)`
 */
@Composable
fun animatedInt(
    target: Int,
    durationMillis: Int = StumpdMotion.STANDARD_MS,
): Int {
    val animated by animateFloatAsState(
        targetValue = target.toFloat(),
        animationSpec = tween(durationMillis = durationMillis, easing = StumpdMotion.standard),
        label = "animatedInt",
    )
    return animated.roundToInt()
}
