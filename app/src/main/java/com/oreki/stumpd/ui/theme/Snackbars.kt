package com.oreki.stumpd.ui.theme

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Status messages, shown as themed snackbars instead of system toasts.
 *
 * A `Toast` is drawn by the system, so nothing about it can be themed — it stays the platform's
 * grey pill with the platform's type, which is the one part of the app no amount of re-skinning
 * could reach. Roughly a third of the app's emoji lived inside toast strings for exactly that
 * reason: they were the only way to signal success or failure.
 *
 * Deliberately *not* used on the live scoring screen: toasts there sit over empty space below the
 * keypad and consume no touches, while a snackbar would cover the undo button and swallow taps on
 * itself. Nor inside dialogs, where a snackbar would render behind the dialog's scrim.
 */
class StumpdMessenger internal constructor(
    private val hostState: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    fun show(message: String, long: Boolean = false) {
        scope.launch {
            // Replace rather than queue. These report what just happened, so a backlog would
            // keep announcing things the user has already moved past — and `showSnackbar`
            // suspends until the current one is gone, so without this a burst of five messages
            // would take fifteen seconds to drain.
            hostState.currentSnackbarData?.dismiss()
            hostState.showSnackbar(
                message = message,
                duration = if (long) SnackbarDuration.Long else SnackbarDuration.Short,
                withDismissAction = long,
            )
        }
    }
}

/** Remembers a [StumpdMessenger] for a Scaffold's [SnackbarHostState]. */
@Composable
fun rememberMessenger(hostState: SnackbarHostState): StumpdMessenger {
    val scope = rememberCoroutineScope()
    return remember(hostState, scope) { StumpdMessenger(hostState, scope) }
}
