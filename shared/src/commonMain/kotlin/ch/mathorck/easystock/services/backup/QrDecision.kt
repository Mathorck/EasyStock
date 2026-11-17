package ch.mathorck.easystock.services.backup

/** Whether a backup can be sent as a single QR code. */
sealed interface QrDecision {
    data class Fits(val text: String) : QrDecision

    /** Too large, or has photos (never in a QR code): offer the Wi-Fi transfer or a file. */
    data object TooLarge : QrDecision
}
