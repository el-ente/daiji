package io.github.elente.daiji.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay

/**
 * Gives a screen's focusable elements stable keys and puts focus back on the last one when the screen comes
 * back, so Back and Home land where the user left. Keys live in the ViewModel because screens are disposed.
 */
class FocusMemory(private val onFocused: (String) -> Unit) {
    private val requesters = HashMap<String, FocusRequester>()

    /** Android focuses the first element on its own when the window opens; that is not the user's choice. */
    private var restored = false

    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    fun tag(key: String): Modifier = Modifier
        .focusRequester(requester(key))
        .onFocusChanged { if (it.isFocused && restored) onFocused(key) }

    /** Lazy rows and grids attach their items a frame or two after the screen appears, hence the retries. */
    suspend fun restore(vararg keys: String?) {
        for (key in keys.filterNotNull()) {
            repeat(RESTORE_ATTEMPTS) {
                if (runCatching { requester(key).requestFocus() }.isSuccess) {
                    restored = true
                    return
                }
                delay(RESTORE_DELAY_MS)
            }
        }
        restored = true
    }

    private companion object {
        const val RESTORE_ATTEMPTS = 6
        const val RESTORE_DELAY_MS = 32L
    }
}

@Composable
fun rememberFocusMemory(onFocused: (String) -> Unit): FocusMemory = remember { FocusMemory(onFocused) }
