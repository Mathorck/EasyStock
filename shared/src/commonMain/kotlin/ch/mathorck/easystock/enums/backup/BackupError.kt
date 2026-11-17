package ch.mathorck.easystock.enums.backup

/** Why a backup can't be read. Shown to the user, never contains row data. */
enum class BackupError {
    /** Not an EasyStock backup (unknown file or QR code). */
    NOT_A_BACKUP,

    /** Made by a newer app version: update the app first. */
    NEWER_VERSION,

    /** Unreadable, truncated, modified or inconsistent. */
    DAMAGED,

    /** A password is needed to open it. */
    PASSWORD_REQUIRED,

    /** The password does not open it. */
    WRONG_PASSWORD,

    /** Too large for the device (or a forged size). */
    TOO_LARGE,

    /** Wi-Fi transfer: this phone is not on a Wi-Fi (or Ethernet) network. */
    NOT_ON_WIFI,

    /** Wi-Fi transfer: the other phone can't be reached (other network, transfer stopped or expired). */
    UNREACHABLE,

    /** Wi-Fi transfer: the other phone stopped answering. */
    TIMEOUT,

    /** Reading or writing a file failed (storage full, file moved, ...). */
    FAILED,
}
