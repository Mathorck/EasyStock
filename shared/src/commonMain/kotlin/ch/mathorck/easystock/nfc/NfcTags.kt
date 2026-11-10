package ch.mathorck.easystock.nfc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** What the phone can do with NFC. */
enum class NfcAvailability {
    /** Not known yet (before the platform answered). */
    Unknown,
    Enabled,

    /** The phone has NFC, but it is turned off in the settings. */
    Disabled,

    /** No NFC hardware (or no platform support, e.g. iOS for now). */
    Unavailable,
}

/** A tag held near the phone. */
sealed interface TagEvent {
    /** [tagId] is in the [tagIdOf] format. */
    data class Read(val tagId: String) : TagEvent

    /** A tag was detected but its ID could not be read. */
    data object Failed : TagEvent
}

/** Who gets a tag when several screens listen: the highest priority, then the most recent. */
enum class TagPriority {
    /** The Scan page: looks the tag up. */
    Lookup,

    /** A screen waiting for a tag to link it (link sheet). Takes the tag from [Lookup]. */
    Link,
}

/**
 * Routes the tags read by the platform reader to one listener (see [TagPriority]).
 * The platform reader runs only while [listening] is true and the app is in the foreground,
 * see [NfcReaderHost]. Main thread only.
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

    /** At least one screen waits for a tag: the reader should run. */
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
 * The reader runs as long as one listener is registered.
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
 * Runs the platform NFC reader while [tags] has listeners and the app is in the foreground,
 * and keeps [NfcTags.availability] up to date. Put it once at the root of the UI.
 */
@Composable
expect fun NfcReaderHost(tags: NfcTags = appNfcTags)

/** Opens the system NFC settings (to turn NFC on). */
@Composable
expect fun rememberOpenNfcSettings(): () -> Unit
