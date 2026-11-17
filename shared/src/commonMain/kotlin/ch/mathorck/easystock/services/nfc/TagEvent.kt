package ch.mathorck.easystock.services.nfc

/** A tag held near the phone. */
sealed interface TagEvent {
    /** [tagId] is in the [tagIdOf] format. */
    data class Read(val tagId: String) : TagEvent

    /** A tag was detected but its ID could not be read. */
    data object Failed : TagEvent
}
