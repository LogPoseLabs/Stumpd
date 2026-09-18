package com.oreki.stumpd.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** How firmly to answer an action. */
enum class Feedback { Tick, Confirm, Reject }

/**
 * Physical feedback for scoring, honouring the user's `vibrationFeedback` setting.
 *
 * The setting has existed in `MatchSettings` since before this was written and was read by
 * nothing; scoring a six produced no feedback at all, while a wide raised a Toast. A tick per run
 * and a firmer answer for a boundary or a wicket is most of what makes the screen feel responsive.
 */
class Haptics(private val view: View, private val enabled: Boolean) {
    fun perform(feedback: Feedback) {
        if (!enabled) return
        view.performHapticFeedback(constantFor(feedback))
    }

    private fun constantFor(feedback: Feedback): Int = when (feedback) {
        // CONFIRM and REJECT only exist from API 30; below that the closest equivalents are the
        // key-press and long-press constants, which every device has had since forever.
        Feedback.Confirm ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.VIRTUAL_KEY
        Feedback.Reject ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
            else HapticFeedbackConstants.LONG_PRESS
        Feedback.Tick -> HapticFeedbackConstants.VIRTUAL_KEY
    }
}

@Composable
fun rememberHaptics(enabled: Boolean): Haptics {
    val view = LocalView.current
    return remember(view, enabled) { Haptics(view, enabled) }
}
