package ch.mathorck.easystock.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.delay

/**
 * A [FocusRequester] that is focused, with the keyboard shown, as soon as the composable enters the composition.
 * Attach it with `Modifier.focusRequester(...)`. Dialogs need a short delay: the window is not attached on first frame.
 */
@Composable
fun rememberAutoFocusRequester(): FocusRequester {
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        repeat(FOCUS_ATTEMPTS) {
            delay(FOCUS_DELAY_MS)
            if (runCatching { requester.requestFocus() }.isSuccess) {
                keyboard?.show()
                return@LaunchedEffect
            }
        }
    }
    return requester
}

private const val FOCUS_ATTEMPTS = 5
private const val FOCUS_DELAY_MS = 100L
