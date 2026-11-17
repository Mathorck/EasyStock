package ch.mathorck.easystock.enums.nfc

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
