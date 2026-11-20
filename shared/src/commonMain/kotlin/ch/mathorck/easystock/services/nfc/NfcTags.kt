package ch.mathorck.easystock.services.nfc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import ch.mathorck.easystock.enums.nfc.NfcAvailability
import ch.mathorck.easystock.enums.nfc.TagPriority
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Routes the tags read by the platform reader to one listener (see [TagPriority]).
 * The platform reader runs whenever the app is in the foreground (see [NfcReaderHost]); a tag
 * read while no screen waits for one is dropped. Main thread only.
 */
class NfcTags(
    private val timeSource: TimeSource = TimeSource.Monotonic,
    /** The same tag read again within this delay is ignored (it stays in the field, or bounces). */
    private val repeatDelay: Duration = 1_500.milliseconds,
) {
    private class Listener(val priority: TagPriority, val onEvent: (TagEvent) -> Unit)

    private val listeners = mutableListOf<Listener>()
    private val _listening = MutableStateFlow(false)
    private val _availability = MutableStateFlow(NfcAvailability.Unknown)
    private var lastRead: Pair<String, TimeMark>? = null

    /** At least one screen waits for a tag. */
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    /** Set by the platform reader, rechecked when the app comes back to the foreground. */
    val availability: StateFlow<NfcAvailability> = _availability.asStateFlow()

    fun setAvailability(value: NfcAvailability) {
        _availability.value = value
    }

    /** Adds a listener. Call the returned function to remove it. */
    fun register(priority: TagPriority, onEvent: (TagEvent) -> Unit): () -> Unit {
        val listener = Listener(priority, onEvent)
        listeners += listener
        _listening.value = true
        return {
            listeners.remove(listener)
            _listening.value = listeners.isNotEmpty()
        }
    }

    /** Gives [event] to the current listener. Returns false when nobody took it (no listener, or a repeat). */
    fun dispatch(event: TagEvent): Boolean {
        // Dropped without being remembered: a screen opened just after still reads the same tag
        if (listeners.isEmpty()) return false
        if (event is TagEvent.Read) {
            val last = lastRead
            if (last != null && last.first == event.tagId && last.second.elapsedNow() < repeatDelay) return false
            lastRead = event.tagId to timeSource.markNow()
        }
        // maxBy keeps the first of equal ones: search from the end so the most recent wins
        val target = listeners.asReversed().maxByOrNull { it.priority.ordinal } ?: return false
        target.onEvent(event)
        return true
    }
}

/** The app-wide tag router, fed by the platform reader (and by the debug fake-tag receiver). */
val appNfcTags = NfcTags()

/**
 * Receives the tags read while this is in the composition and [enabled].
 */
@Composable
fun NfcTagListener(
    priority: TagPriority,
    enabled: Boolean = true,
    tags: NfcTags = appNfcTags,
    onEvent: (TagEvent) -> Unit,
) {
    val current = rememberUpdatedState(onEvent)
    DisposableEffect(tags, priority, enabled) {
        val unregister = if (enabled) tags.register(priority) { current.value(it) } else null
        onDispose { unregister?.invoke() }
    }
}

/**
 * Runs the platform NFC reader while the app is in the foreground, and keeps
 * [NfcTags.availability] up to date. Put it once at the root of the UI.
 */
@Composable
expect fun NfcReaderHost(tags: NfcTags = appNfcTags)

/** Opens the system NFC settings (to turn NFC on). */
@Composable
expect fun rememberOpenNfcSettings(): () -> Unit
