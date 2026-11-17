package ch.mathorck.easystock.enums.nfc

/** Who gets a tag when several screens listen: the highest priority, then the most recent. */
enum class TagPriority {
    /** The Scan page: looks the tag up. */
    Lookup,

    /** A screen waiting for a tag to link it (link sheet). Takes the tag from [Lookup]. */
    Link,
}
